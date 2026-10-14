package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEngineCatalog;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEngineCatalog.EngineEntry;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEngineCatalog.EngineKey;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DIKC 四层只读探测器（详细设计-10 §4.1 组件落位 {@code readiness/EngineProbeClient(只读 3s)}；
 * F10-08 算法树「并行四层探测 D/I/K/C，每层超时 3 秒」的实位）。
 *
 * <p><b>探测目标单源 = {@link WAgentEngineCatalog}</b>（R-43「Readiness 探测的目标端点校验一律引本类单源，
 * 禁散落 URL 字面量」）：层位 → EngineKey → 该 Engine 在位前缀族里取 {@code /api/v1/engine/} 形态的
 * 引擎健康前缀（该族没有时才回落脚首前缀），再拼 {@code health} 段。本类因此<b>不出现任何完整 URL 字面量</b>，
 * 落点实测：D {@code /api/v1/engine/data/health}、I {@code /api/v1/engine/ontology/health}、
 * K {@code /api/v1/knowledge/health}、C {@code /api/v1/engine/cognitive/health}。</p>
 *
 * <p><b>解包口径对齐 {@code workspace/knowledge/service/KnowledgeHealthAggregator}</b>（同仓既有 health 探针，
 * 不另立协议）：{@code ApiResponse} 包裹时取 {@code data} 段的 {@code {status, components}}；
 * 直返形态时 {@code status} 在顶层。{@code "UP".equalsIgnoreCase(status)} 才算可达。</p>
 *
 * <p><b>fail-closed 是探测器的唯一方向</b>（F10-08 硬规则②「探测异常 ⇒ UNKNOWN，禁 fail-open」）：
 * 连接异常/读超时/非 2xx/空体/状态非 UP/前缀不在目录 ⇒ 该层 {@code reachable=false} + 机器可读原因，
 * <b>本类对调用方不抛异常</b>。把"没探到"折叠成"探到了且没问题"就是假真理，
 * 门禁 {@code ReadinessProbeTest#probeErrorYieldsUnknownNotPass} 钉的就是这一条。</p>
 *
 * <p><b>底座纪律</b>：RestTemplate 一律注入 {@code AiRestClientConfig#wagentEngineProbeRest}
 * （基址经 runtime-access 两态寻址，超时在本工厂集中下发），本类不自建客户端、不写死端口（X-35）。</p>
 *
 * <p><b>并行度与线程池归属（裁决 D-B2④-18，2026-10-15 由 gateway 门禁抓出后订正）</b>：
 * 一次评估内 4 层并发出向，聚合预算 = 单层预算 + 收尾余量（默认 3000ms + 250ms）。
 * 本类<b>不自建线程池</b>——门禁 {@code SelfScheduleArchTest} / {@code ModuleDependencyArchTest
 * #businessMustNotSelfSchedule}（F05-15 X-36 / 铁律 §2.5-3）实测把我第一版
 * {@code Executors.newFixedThreadPool} 判成违例并要求"存量基线只减不增"：登记进
 * {@code self-schedule-baseline.txt} 等于把门禁吃回去，故按规则原文给的另条出路走 —
 * 注入底座统一 Executor Bean（{@code runtime/llm-gateway} 的 {@code llmGatewaySchedulerExecutor}，
 * core=4/max=32/SynchronousQueue/CallerRunsPolicy，池由底座 config 工厂集中构造、已在基线内）。
 * 该池饱和时按 {@code CallerRunsPolicy} 落回调用线程＝自然退化为顺序探测，慢但不失真。
 * <b>底座 Bean 缺席</b>（如宿主未装配 llm-gateway）⇒ 本类顺序内联探测并只 WARN 一次，
 * 到点未探的层如实记 {@code aggregator-budget-exceeded}（禁无限等、禁默认放行）。
 * 定级器拿半集探测结论就会算出半个级，因此<b>降级只许慢、不许缺键</b>。</p>
 */
@Component
public class EngineProbeClient {

    private static final Logger log = LoggerFactory.getLogger(EngineProbeClient.class);

    /** F10-08 原文「每层超时 3 秒」。 */
    public static final long LAYER_BUDGET_MS = 3000L;

    /** 聚合收尾余量：调度抖动不计入引擎预算，但到点必收（禁无限等）。 */
    public static final long FANOUT_MARGIN_MS = 250L;

    /** 引擎健康端点的相对段（前缀由目录给出，禁在此写完整 URL）。 */
    private static final String HEALTH_SEGMENT = "health";

    /** 底座池缺席 ⇒ 顺序降级，只 WARN 一次（禁每次评估刷日志）。 */
    private static final AtomicBoolean SEQUENTIAL_WARNED = new AtomicBoolean(false);

    private final RestTemplate restTemplate;

    /** 底座统一 Executor（可空＝缺席，空则走顺序降级路径；本类不拥有其生命周期）。 */
    private final ExecutorService fanout;

    public EngineProbeClient(@Qualifier("wagentEngineProbeRest") RestTemplate restTemplate,
                             @Qualifier("llmGatewaySchedulerExecutor")
                             ObjectProvider<ExecutorService> fanoutProvider) {
        this.restTemplate = restTemplate;
        // getIfAvailable()：底座 Bean 未装配时返回 null 而非抛 NoSuchBeanDefinitionException——
        // "探测能力降级"不许升级成"整个服务起不来"（口径同 AiRestClientConfig 基址延迟解析）
        this.fanout = fanoutProvider.getIfAvailable();
    }

    /** 一次评估的四层探测结论（DIKC 恒有键；层缺席即"该层没探"，也是结论）。 */
    public Map<ReadinessLayer, LayerProbe> probeAll() {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LAYER_BUDGET_MS + FANOUT_MARGIN_MS);
        Map<ReadinessLayer, LayerProbe> out = new EnumMap<>(ReadinessLayer.class);
        if (fanout == null) {
            probeSequentially(deadline, out);
            return Collections.unmodifiableMap(out);
        }
        Map<ReadinessLayer, Future<LayerProbe>> pending = new EnumMap<>(ReadinessLayer.class);
        for (ReadinessLayer layer : ReadinessLayer.values()) {
            pending.put(layer, fanout.submit(() -> probe(layer)));
        }
        for (ReadinessLayer layer : ReadinessLayer.values()) {
            out.put(layer, await(layer, pending.get(layer), deadline));
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * 底座 Executor 缺席时的降级路径：同线程逐层探，仍受聚合截止点约束。
     *
     * <p>语义与并发路径逐字对齐——四键齐、单层永不抛、到点未探记
     * {@code aggregator-budget-exceeded}。差别只有耗时（最坏 4×3s 而非 3s）。</p>
     */
    private void probeSequentially(long deadlineNanos, Map<ReadinessLayer, LayerProbe> out) {
        if (SEQUENTIAL_WARNED.compareAndSet(false, true)) {
            log.warn("[F10-08] 底座统一 Executor（llmGatewaySchedulerExecutor）未装配 ⇒ 四层探测退化为顺序执行，"
                    + "单次评估最坏耗时由 {}ms 升到 {}ms（结论口径不变，禁按并发路径假设时延）",
                    LAYER_BUDGET_MS + FANOUT_MARGIN_MS, ReadinessLayer.values().length * LAYER_BUDGET_MS);
        }
        for (ReadinessLayer layer : ReadinessLayer.values()) {
            if (System.nanoTime() >= deadlineNanos) {
                // 截止点已过才轮到该层：属"没探"，禁拿旧结论或默认放行顶替
                out.put(layer, LayerProbe.down(layer, null, "aggregator-budget-exceeded"));
                continue;
            }
            out.put(layer, inlineProbe(layer));
        }
    }

    /** 单层内联探测（并发路径由 {@link #await} 折叠任务异常，本路径须自己折叠）。 */
    private LayerProbe inlineProbe(ReadinessLayer layer) {
        try {
            return probe(layer);
        } catch (RuntimeException unexpected) {
            // 与并发路径同字面：probe() 自称永不抛，真抛出来就是底座级异常，仍按不可达处理
            log.warn("[F10-08] 层探测任务异常终止(顺序路径): layer={} cause={}", layer, String.valueOf(unexpected));
            return LayerProbe.down(layer, null, "probe-task-failed");
        }
    }

    /**
     * 单层探测（永不抛出）。
     *
     * <p><b>本类没有 {@code @PreDestroy}</b>（D-B2④-18 的后半）：注入的池归底座所有，
     * 探测器关闭它会把 LLM 调度一起停掉；线程生命周期由底座 Bean 自己管。</p>
     */
    public LayerProbe probe(ReadinessLayer layer) {
        String path;
        try {
            path = healthPath(layer);
        } catch (IllegalStateException unresolved) {
            // 目录里没有该 Engine 的在位前缀 ⇒ 连"该往哪探"都不成立，记不可达（禁猜端口）
            return LayerProbe.down(layer, null, "prefix-unresolvable:" + unresolved.getMessage());
        }
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(path, HttpMethod.GET, HttpEntity.EMPTY, Map.class);
            return interpret(layer, path, resp.getBody());
        } catch (IllegalStateException baseUnresolved) {
            // 基址解析失败（route-manifest 未登记 owner=gateway 且无覆盖项）：属"没探"，不是"探过了"
            log.warn("[F10-08] 层探测基址不可解析: layer={} err={}", layer, baseUnresolved.getMessage());
            return LayerProbe.down(layer, path, "base-url-unresolvable");
        } catch (ResourceAccessException ioOrTimeout) {
            log.warn("[F10-08] 层探测超时/连接失败: layer={} path={} err={}",
                    layer, path, ioOrTimeout.getMessage());
            return LayerProbe.down(layer, path, "unreachable:timeout-or-connect");
        } catch (HttpStatusCodeException httpErr) {
            // 401/403 = 该 health 端点未登记匿名面（如 /api/v1/engine/ontology/health）；
            // 404/5xx = 端点或引擎不在位。一律不可达，禁按"能连上就算过"处理。
            log.warn("[F10-08] 层探测回非 2xx: layer={} path={} status={}",
                    layer, path, httpErr.getRawStatusCode());
            return LayerProbe.down(layer, path, "http-" + httpErr.getRawStatusCode());
        } catch (RestClientException malformed) {
            log.warn("[F10-08] 层探测响应不可解析: layer={} path={} err={}",
                    layer, path, malformed.getMessage());
            return LayerProbe.down(layer, path, "unparsable-body");
        }
    }

    /** 层位 → Engine 目录键（附件四层 ↔ ECOS 六引擎中的四只，F10-02 单源）。 */
    static EngineKey engineKeyOf(ReadinessLayer layer) {
        return switch (layer) {
            case D -> EngineKey.DATA;
            case I -> EngineKey.ONTOLOGY;
            case K -> EngineKey.KNOWLEDGE;
            case C -> EngineKey.COGNITIVE;
        };
    }

    /**
     * 层位健康端点路径（目录单源推导，供测试与 {@code layer_summary_json} 留痕）。
     *
     * @throws IllegalStateException 该 Engine 在目录里没有可用前缀
     */
    public static String healthPath(ReadinessLayer layer) {
        EngineEntry entry = WAgentEngineCatalog.entry(engineKeyOf(layer))
                .orElseThrow(() -> new IllegalStateException("engine catalog 无 " + engineKeyOf(layer)));
        List<String> prefixes = entry.livePathPrefixes();
        if (prefixes.isEmpty()) {
            throw new IllegalStateException("Engine " + entry.key() + " 无在位 REST 前缀");
        }
        String preferred = prefixes.stream()
                .filter(p -> p.startsWith("/api/v1/engine/"))
                .findFirst()
                .orElse(prefixes.get(0));
        String path = preferred + HEALTH_SEGMENT;
        if (!entry.ownsPath(path)) {
            throw new IllegalStateException("推导路径 " + path + " 不在 Engine " + entry.key() + " 前缀族内");
        }
        return path;
    }

    // ── 内部 ─────────────────────────────────────────

    private static LayerProbe await(ReadinessLayer layer, Future<LayerProbe> future, long deadlineNanos) {
        try {
            long remainNanos = Math.max(1L, deadlineNanos - System.nanoTime());
            return future.get(remainNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException to) {
            future.cancel(true);
            return LayerProbe.down(layer, null, "fanout-budget-exceeded");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return LayerProbe.down(layer, null, "interrupted");
        } catch (ExecutionException ee) {
            // probe() 自身不抛；此处只可能是/Error 级异常，仍按不可达处理（禁半集定级）
            log.warn("[F10-08] 层探测任务异常终止: layer={} cause={}", layer, String.valueOf(ee.getCause()));
            return LayerProbe.down(layer, null, "probe-task-failed");
        }
    }

    /** 健康体解包：{@code data{status,components}} 或顶层 {@code status} 两种既有形态。 */
    @SuppressWarnings("rawtypes")
    private static LayerProbe interpret(ReadinessLayer layer, String path, Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return LayerProbe.down(layer, path, "remote-empty");
        }
        Object dataNode = body.get("data");
        Map<?, ?> source = dataNode instanceof Map<?, ?> data ? data : body;
        Object status = source.get("status");
        boolean up = status != null && "UP".equalsIgnoreCase(String.valueOf(status));
        Map<String, Object> components = new LinkedHashMap<>();
        Object comps = source.get("components");
        if (comps instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null) {
                    components.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        }
        if (up) {
            return new LayerProbe(layer, path, true, "UP", null, components);
        }
        return new LayerProbe(layer, path, false, status == null ? "missing-status" : String.valueOf(status),
                "remote-status-not-up", components);
    }

    /**
     * 单层探测结论。
     *
     * @param reachable  该层是否给出"引擎在位且健康"的肯定结论（唯一可进入度量取值的前置）
     * @param status     远端状态字面（UP/down/missing-status/http-*）
     * @param reason     不可达原因（机器可读，落 {@code measured_json} 与缺项清单）
     * @param components 远端 components 原样透传（禁解析后改写数值）
     */
    public record LayerProbe(ReadinessLayer layer, String path, boolean reachable, String status,
                             String reason, Map<String, Object> components) {

        public LayerProbe {
            components = components == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(components));
        }

        static LayerProbe down(ReadinessLayer layer, String path, String reason) {
            return new LayerProbe(layer, path, false, "down", reason, Map.of());
        }
    }
}
