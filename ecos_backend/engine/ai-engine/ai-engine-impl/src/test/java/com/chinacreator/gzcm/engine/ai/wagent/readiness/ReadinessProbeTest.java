package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessCheckStatus;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessLayer;
import com.chinacreator.gzcm.engine.ai.wagent.readiness.ReadinessCheckCode.Judgement;
import com.chinacreator.gzcm.engine.ai.wagent.readiness.ThresholdSet.ThresholdValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F10-08 探测面门禁（详细设计-10 §4.1 {@code EngineProbeClient(只读 3s)} + 硬规则②）。
 *
 * <p><b>钉死的是方向，不是数值</b>：探测出任何异常（超时/连接失败/非 2xx/空体/状态非 UP/
 * 任务级异常）只能得出「该层没探到」，绝不能得出「该层没问题」。把"未取到证据"折叠成
 * "取到了且通过"就是假绿——这正是差距分析把 F10-08 判"未实装"时要防的第一风险。</p>
 *
 * <p>全 mock：{@link RestTemplate} 桩化，不起真引擎、不连真库；探测端点由
 * {@link WAgentEngineCatalog} 单源推导（{@link #healthPathsComeFromEngineCatalog()}）。</p>
 */
class ReadinessProbeTest {

    private RestTemplate restTemplate;
    private EngineProbeClient probeClient;

    /**
     * 夹具池：冒充底座的 {@code llmGatewaySchedulerExecutor} 注入位。
     *
     * <p>刻意用 {@link ThreadPoolExecutor} 构造器而非 {@code Executors.new*}——后者是
     * {@code SelfScheduleArchTest} 规则 C 的检测面，测试源同样在 {@code engine.ai..} 包范围内，
     * 图里省事会把门禁吃回去。</p>
     */
    private ThreadPoolExecutor fanoutFixture;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        fanoutFixture = new ThreadPoolExecutor(ReadinessLayer.values().length,
                ReadinessLayer.values().length, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        probeClient = new EngineProbeClient(restTemplate, providerReturning(fanoutFixture));
    }

    @AfterEach
    void tearDown() {
        fanoutFixture.shutdownNow();
    }

    /** 底座 Executor 注入桩（在位）。 */
    @SuppressWarnings("unchecked")
    private static ObjectProvider<ExecutorService> providerReturning(ExecutorService executor) {
        ObjectProvider<ExecutorService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(executor);
        return provider;
    }

    /** 底座 Executor 缺席桩（llm-gateway 未装配的宿主形态）。 */
    @SuppressWarnings("unchecked")
    private static ObjectProvider<ExecutorService> providerAbsent() {
        ObjectProvider<ExecutorService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    // ══════════════════ 硬规则②：异常 ⇒ 不可达，禁折成 pass ══════════════════

    @Test
    @DisplayName("F10-08 硬规则②：超时/连接失败 ⇒ reachable=false + 机器可读原因（禁 fail-open）")
    void probeErrorYieldsUnknownNotPass() {
        stubThrow(new ResourceAccessException("connect timed out"));
        EngineProbeClient.LayerProbe d = probeClient.probe(ReadinessLayer.D);
        assertFalse(d.reachable(), "超时绝不能算可达");
        assertEquals("unreachable:timeout-or-connect", d.reason());
        assertEquals("down", d.status());
        assertTrue(d.components().isEmpty(), "不可达时不得带出任何组件结论");

        // 四层同时无响应：probeAll 仍须给出四键且全不可达（禁漏键当"没这层"、禁半集定级）
        Map<ReadinessLayer, EngineProbeClient.LayerProbe> all = probeClient.probeAll();
        assertEquals(ReadinessLayer.values().length, all.size(), "DIKC 恒有键");
        for (ReadinessLayer layer : ReadinessLayer.values()) {
            assertFalse(all.get(layer).reachable(), layer + " 超时后不得可达");
        }
    }

    @Test
    @DisplayName("探测任务级未受检异常（不在收口清单内）也折成不可达，不外溢到评估链")
    void unexpectedTaskFailureStillUnreachable() {
        stubThrow(new NullPointerException("底座被人为破坏"));
        Map<ReadinessLayer, EngineProbeClient.LayerProbe> all = probeClient.probeAll();
        for (ReadinessLayer layer : ReadinessLayer.values()) {
            EngineProbeClient.LayerProbe probe = all.get(layer);
            assertFalse(probe.reachable(), layer + " 任务异常后不得可达");
            assertEquals("probe-task-failed", probe.reason(), "异常终止要有可复盘原因位");
        }
    }

    @Test
    @DisplayName("底座 Executor 缺席 ⇒ 顺序降级：结论口径与并发路径逐字一致（四键齐、异常折成不可达、不外溢）")
    void sequentialDegradationKeepsSameConclusions() {
        stubThrow(new ResourceAccessException("connect timed out"));
        Map<ReadinessLayer, EngineProbeClient.LayerProbe> all =
                new EngineProbeClient(restTemplate, providerAbsent()).probeAll();

        assertEquals(ReadinessLayer.values().length, all.size(), "降级路径同样必须回四键（禁漏键当没这层）");
        for (ReadinessLayer layer : ReadinessLayer.values()) {
            EngineProbeClient.LayerProbe probe = all.get(layer);
            assertFalse(probe.reachable(), layer + " 降级路径不得放行");
            assertEquals("unreachable:timeout-or-connect", probe.reason(),
                    "降级只改耗时不改结论口径（同一异常须给同一原因字面）");
        }

        // 顺序路径没有 Future 兜异常，probe 若真抛出必须由 inlineProbe 自己折叠成 probe-task-failed
        stubThrow(new NullPointerException("底座被人为破坏"));
        Map<ReadinessLayer, EngineProbeClient.LayerProbe> broken =
                new EngineProbeClient(restTemplate, providerAbsent()).probeAll();
        for (ReadinessLayer layer : ReadinessLayer.values()) {
            assertFalse(broken.get(layer).reachable(), layer + " 顺序路径异常后不得可达");
            assertEquals("probe-task-failed", broken.get(layer).reason(),
                    "与并发路径同字面，否则降级会改变下游缺口判定");
        }
    }

    @Test
    @DisplayName("非 2xx（403=健康面未登记匿名面的真实形态）⇒ 不可达 http-403")
    void non2xxYieldsUnreachable() {
        stubThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));
        EngineProbeClient.LayerProbe i = probeClient.probe(ReadinessLayer.I);
        assertFalse(i.reachable());
        assertEquals("http-403", i.reason());
    }

    @Test
    @DisplayName("200 但 status 非 UP ⇒ 不可达并透传远端状态（'连上了'不等于'健康'）")
    void reachableButNotUpIsUnreachable() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "DOWN");
        stubReturn(new ResponseEntity<>(body, HttpStatus.OK));

        EngineProbeClient.LayerProbe c = probeClient.probe(ReadinessLayer.C);
        assertFalse(c.reachable());
        assertEquals("DOWN", c.status());
        assertEquals("remote-status-not-up", c.reason());
    }

    @Test
    @DisplayName("空体 / 空 Map ⇒ 不可达 remote-empty（禁把空响应读成通过）")
    void emptyBodyYieldsUnreachable() {
        stubReturn(new ResponseEntity<>(Map.of(), HttpStatus.OK));
        EngineProbeClient.LayerProbe k = probeClient.probe(ReadinessLayer.K);
        assertFalse(k.reachable());
        assertEquals("remote-empty", k.reason());

        stubReturn(new ResponseEntity<>((Map<String, Object>) null, HttpStatus.OK));
        EngineProbeClient.LayerProbe d = probeClient.probe(ReadinessLayer.D);
        assertFalse(d.reachable());
        assertEquals("remote-empty", d.reason());
    }

    @Test
    @DisplayName("UP 才算可达；ApiResponse 包裹形态取 data 段（对齐仓内既有 health 探针）")
    void upStatusIsReachable() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "up"); // 大小写不敏感（既有聚合器口径）
        data.put("components", Map.of("db", "UP"));
        stubReturn(new ResponseEntity<>(Map.of("code", 200, "data", data), HttpStatus.OK));

        EngineProbeClient.LayerProbe d = probeClient.probe(ReadinessLayer.D);
        assertTrue(d.reachable());
        assertEquals("UP", d.status());
        assertNull(d.reason(), "可达项不留原因位");
        assertEquals("UP", d.components().get("db"));
    }

    // ══════════════════ 探测目标单源（R-43）══════════════════

    @Test
    @DisplayName("四层健康路径由 WAgentEngineCatalog 单源推导；单层预算 = 设计原文 3s")
    void healthPathsComeFromEngineCatalog() {
        assertEquals("/api/v1/engine/data/health", EngineProbeClient.healthPath(ReadinessLayer.D));
        assertEquals("/api/v1/engine/ontology/health", EngineProbeClient.healthPath(ReadinessLayer.I));
        // K 层：KB 属主健康面实测在位 = /api/v1/engine/knowledge/health（KbEngineHealthController
        // 挂 /api/v1/engine/knowledge，非 /api/v1/knowledge——#146b 目录前缀族订正，
        // 与 data/ontology/cognitive 三层的 /api/v1/engine/{族}/health 同形态）
        assertEquals("/api/v1/engine/knowledge/health", EngineProbeClient.healthPath(ReadinessLayer.K));
        assertEquals("/api/v1/engine/cognitive/health", EngineProbeClient.healthPath(ReadinessLayer.C));
        assertEquals(3000L, EngineProbeClient.LAYER_BUDGET_MS,
                "F10-08 原文「每层超时 3 秒」，禁另立数值");
    }

    // ══════════════════ 判档语义逐条对齐 V265 seed ══════════════════

    @Test
    @DisplayName("V265 seed 判档语义 8 条逐项锁死（含两界、自纠正、档位强度差）")
    void judgementSemanticsFollowSeedRows() {
        // ① data.check_coverage：seed「< 本值 ⇒ fail」，域 (0,1]
        assertEquals(ReadinessCheckStatus.FAIL, judge(ReadinessCheckCode.DATA_COVERAGE,
                MetricValue.number(new BigDecimal("0.65")),
                set("default", "data.check_coverage", "0.7")).status());
        assertEquals(ReadinessCheckStatus.PASS, judge(ReadinessCheckCode.DATA_COVERAGE,
                MetricValue.number(new BigDecimal("0.70")),
                set("default", "data.check_coverage", "0.7")).status());
        assertEquals(ReadinessCheckStatus.UNKNOWN, judge(ReadinessCheckCode.DATA_COVERAGE,
                MetricValue.number(new BigDecimal("0.9")),
                set("default", "data.check_coverage", "1.5")).status(),
                "阈值越出 (0,1] ⇒ UNKNOWN（禁算 pass）");

        // ② data.staleness_days：seed「> 本值 ⇒ warn，> 本值×2 ⇒ fail」，等于界不越界
        assertEquals(ReadinessCheckStatus.PASS, judge(ReadinessCheckCode.DATA_STALENESS,
                MetricValue.number(new BigDecimal("7")),
                set("default", "data.staleness_days", "7")).status());
        assertEquals(ReadinessCheckStatus.WARN, judge(ReadinessCheckCode.DATA_STALENESS,
                MetricValue.number(new BigDecimal("8")),
                set("default", "data.staleness_days", "7")).status());
        assertEquals(ReadinessCheckStatus.FAIL, judge(ReadinessCheckCode.DATA_STALENESS,
                MetricValue.number(new BigDecimal("15")),
                set("default", "data.staleness_days", "7")).status());

        // ③ onto.caliber_defined：seed「true=必须已定义，否则该层 fail」；配置侧 false = 演示档显式关闭
        assertEquals(ReadinessCheckStatus.FAIL, judge(ReadinessCheckCode.ONTO_CALIBER_DEFINED,
                MetricValue.bool(false), set("default", "onto.caliber_defined", "true")).status());
        assertEquals(ReadinessCheckStatus.PASS, judge(ReadinessCheckCode.ONTO_CALIBER_DEFINED,
                MetricValue.bool(true), set("default", "onto.caliber_defined", "true")).status());
        assertEquals(ReadinessCheckStatus.PASS, judge(ReadinessCheckCode.ONTO_CALIBER_DEFINED,
                MetricValue.bool(false), set("default", "onto.caliber_defined", "false")).status());

        // ④ onto.conflict_count：seed 两 clause 在 max=0/count=1 处重叠 ⇒ 取更具体者（裁决 D-B2④-6）
        assertEquals(ReadinessCheckStatus.PASS, judge(ReadinessCheckCode.ONTO_CONFLICT_COUNT,
                MetricValue.number(BigDecimal.ZERO),
                set("default", "onto.conflict_count_max", "0")).status());
        assertEquals(ReadinessCheckStatus.WARN, judge(ReadinessCheckCode.ONTO_CONFLICT_COUNT,
                MetricValue.number(BigDecimal.ONE),
                set("default", "onto.conflict_count_max", "0")).status());
        assertEquals(ReadinessCheckStatus.FAIL, judge(ReadinessCheckCode.ONTO_CONFLICT_COUNT,
                MetricValue.number(new BigDecimal("2")),
                set("default", "onto.conflict_count_max", "0")).status());
        assertEquals(ReadinessCheckStatus.UNKNOWN, judge(ReadinessCheckCode.ONTO_CONFLICT_COUNT,
                MetricValue.number(new BigDecimal("-1")),
                set("default", "onto.conflict_count_max", "0")).status(),
                "度量自身越界 ⇒ UNKNOWN（禁把负数当'零冲突'放行）");

        // ⑤ know.sample_size：seed「< min ⇒ warn 挂 DG-D1；< trend_floor ⇒ 只给方向」+ floor 自纠正
        ThresholdSet sample = set("default",
                "know.sample_size_min", "30", "know.sample_size_trend_floor", "10");
        assertEquals(ReadinessCheckStatus.FAIL, judge(ReadinessCheckCode.KNOW_SAMPLE_SIZE,
                MetricValue.number(new BigDecimal("5")), sample).status());
        assertEquals(ReadinessCheckStatus.WARN, judge(ReadinessCheckCode.KNOW_SAMPLE_SIZE,
                MetricValue.number(new BigDecimal("20")), sample).status());
        assertEquals(ReadinessCheckStatus.PASS, judge(ReadinessCheckCode.KNOW_SAMPLE_SIZE,
                MetricValue.number(new BigDecimal("40")), sample).status());
        Judgement selfCorrected = judge(ReadinessCheckCode.KNOW_SAMPLE_SIZE,
                MetricValue.number(new BigDecimal("9")),
                set("default", "know.sample_size_min", "30", "know.sample_size_trend_floor", "99"));
        assertEquals(ReadinessCheckStatus.FAIL, selfCorrected.status(),
                "floor>min 时按 seed 自纠正为 min/3=10 ⇒ 9 低于趋势下限 ⇒ fail");
        assertTrue(selfCorrected.reason().contains("self-corrected"),
                "自纠正必须留痕（禁静默改写配置值）: " + selfCorrected.reason());

        // ⑥ know.applicability：seed 只给「< 本值 ⇒ 视为缺历史画像先验，挂 DG-K1」⇒ warn 不阻断（D-B2④-7）
        assertEquals(ReadinessCheckStatus.WARN, judge(ReadinessCheckCode.KNOW_APPLICABILITY,
                MetricValue.number(new BigDecimal("0.5")),
                set("default", "know.applicability_min", "0.6")).status());

        // ⑦ cog.cm_ready：seed「未就绪 ⇒ 该层 blocker ⇒ 总级 D」
        assertEquals(ReadinessCheckStatus.BLOCKER, judge(ReadinessCheckCode.COG_CM_READY,
                MetricValue.bool(false), set("default", "cog.cm_ready", "true")).status());

        // ⑧ cog.backtest_ready：同一条 seed 两档强度不同 ⇒ 强度来自命中档位，不写死在代码
        assertEquals(ReadinessCheckStatus.WARN, judge(ReadinessCheckCode.COG_BACKTEST_READY,
                MetricValue.bool(false), set("default", "cog.backtest_ready", "true")).status());
        assertEquals(ReadinessCheckStatus.BLOCKER, judge(ReadinessCheckCode.COG_BACKTEST_READY,
                MetricValue.bool(false), set("forecast", "cog.backtest_ready", "true")).status());
    }

    @Test
    @DisplayName("三类'判不了'一律 UNKNOWN：度量缺席 / 形态不符 / 阈值未命中（8 项全过）")
    void unparsableInputsAreUnknown() {
        for (ReadinessCheckCode code : ReadinessCheckCode.values()) {
            // 度量 ABSENT（七项未接线度量的现形态）⇒ UNKNOWN，禁默认放行
            assertEquals(ReadinessCheckStatus.UNKNOWN,
                    code.judge(MetricValue.absent("metric-source-not-wired:" + code.checkCode()),
                            set("default", fragmentsWith(code, "1"))).status(),
                    code.checkCode() + " 在度量缺席时不得出 pass");
            // 阈值集合为空 ⇒ UNKNOWN（sysman 未 seed 或未装配的真实形态）
            assertEquals(ReadinessCheckStatus.UNKNOWN,
                    code.judge(MetricValue.number(BigDecimal.ONE), ThresholdSet.unset("default")).status(),
                    code.checkCode() + " 在无阈值时不得出 pass");
            assertEquals(ReadinessCheckStatus.UNKNOWN,
                    code.judge(MetricValue.bool(true), ThresholdSet.unset("default")).status(),
                    code.checkCode() + " 在无阈值时不得出 pass（布尔形态）");
        }
        // 形态不符：布尔度量交给数值项 / 数值度量交给布尔项
        assertEquals(ReadinessCheckStatus.UNKNOWN, judge(ReadinessCheckCode.DATA_COVERAGE,
                MetricValue.bool(true), set("default", "data.check_coverage", "0.7")).status());
        assertEquals(ReadinessCheckStatus.UNKNOWN, judge(ReadinessCheckCode.COG_CM_READY,
                MetricValue.number(BigDecimal.ONE), set("default", "cog.cm_ready", "true")).status());
        // null 度量 ⇒ UNKNOWN 且不抛（判档器不许把 null 当 false）
        assertEquals(ReadinessCheckStatus.UNKNOWN,
                ReadinessCheckCode.COG_CM_READY.judge(null, set("default", "cog.cm_ready", "true")).status());
    }

    @Test
    @DisplayName("片段部分缺席 ⇒ hasAll=false 且 judge() 必抛：这就是调用方前置闸存在的原因（禁删该闸）")
    void partialThresholdSetMustBeGatedByCaller() {
        ThresholdSet partial = set("default", "know.sample_size_min", "30");
        List<String> declared = ReadinessCheckCode.KNOW_SAMPLE_SIZE.thresholdFragments();
        assertFalse(partial.hasAll(declared), "半截阈值必须判为不可判档");
        assertEquals(List.of("know.sample_size_trend_floor"), partial.missing(declared));
        assertThrows(NoSuchElementException.class,
                () -> ReadinessCheckCode.KNOW_SAMPLE_SIZE.judge(MetricValue.number(new BigDecimal("20")), partial),
                "半截阈值直喂 judge() 必抛（orElseThrow 实测形态）⇒ 生产路径的 hasAll 前置闸不可删");
    }

    // ── 夹具 ─────────────────────────────────────────

    private Judgement judge(ReadinessCheckCode code, MetricValue metric, ThresholdSet thresholds) {
        assertTrue(thresholds.hasAll(code.thresholdFragments()),
                "夹具必须先过 hasAll 闸（与生产路径同形）: " + code.checkCode());
        return code.judge(metric, thresholds);
    }

    private static ThresholdSet set(String profile, String... fragmentValuePairs) {
        Map<String, ThresholdValue> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < fragmentValuePairs.length; i += 2) {
            String fragment = fragmentValuePairs[i];
            map.put(fragment, new ThresholdValue(fragment,
                    ThresholdPolicy.keyOf(profile, fragment), fragmentValuePairs[i + 1], profile));
        }
        return new ThresholdSet(profile, map);
    }

    private static String[] fragmentsWith(ReadinessCheckCode code, String raw) {
        String[] args = new String[code.thresholdFragments().size() * 2];
        for (int i = 0; i < code.thresholdFragments().size(); i++) {
            args[i * 2] = code.thresholdFragments().get(i);
            args[i * 2 + 1] = raw;
        }
        return args;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubReturn(ResponseEntity<?> body) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class))).thenReturn((ResponseEntity) body);
    }

    @SuppressWarnings("unchecked")
    private void stubThrow(Throwable error) {
        doThrow(error).when(restTemplate).exchange(anyString(), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class));
    }
}
