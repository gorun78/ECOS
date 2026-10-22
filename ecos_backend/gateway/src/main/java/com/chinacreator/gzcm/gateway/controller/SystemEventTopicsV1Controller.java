package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.SystemErrorCode;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.event.TopicLagSource;
import com.chinacreator.gzcm.sysman.security.MinimumClearance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * 详细设计说明书-从零实施版 §3.3 L2127 {@code listEventTopics}
 * ——{@code GET /api/v1/system/events/topics}：暴露 {@link KafkaTopics} 清单与 lag。
 *
 * <p><b>本端点补齐的差距</b>（差距文档 §「端点缺口 5 处」第 2 项，L795-798）：
 * 该端点此前全仓零载体（{@code grep -rn "events/topics"} 只命中设计文档），
 * 运维/诊断面看不到事件总线 topic 与积压，§3.3.2「DLQ 命名 {@code <topic>.DLT} 并暴露 lag 指标」
 * 的「暴露」半边整体缺位。</p>
 *
 * <p><b>topic 单源纪律（不漏项、也防假绿）</b>：清单<b>不内联</b>，由 {@link KafkaTopics}
 * 的 {@code public static final String} 字段<b>反射</b>得到——与既有
 * {@code KafkaTopicParityTest} 同口径。若哪天有人新增/删除常量，本端点的输出随之变化，
 * 不会出现「端点里另抄一份 11 条、代码改了端点没改」的第二套真值。
 * 反射取值失败（类被裁剪、字段不可见）按 {@code ECOS-SYS-503} fail-closed，
 * <b>绝不返回空清单冒充「无 topic」</b>（§4.3.3 同类禁令：禁渲染空列表冒充「无数据」）。</p>
 *
 * <p><b>{@code lag} 的取值口径（诚实降级）</b>：lag 来自 {@link TopicLagSource}
 * （runtime-event 的 {@code KafkaAdminTopicLagSource}，仅 {@code ecos.event.kafka.enabled=true}
 * 时装配）。该开关默认关闭 ⇒ 容器内无此 Bean（{@code Optional} 为空）⇒ 每条 topic 的 {@code lag} 呈现为
 * {@code null}，并由顶层 {@code lagSource="NOT_WIRED"} + {@code metricNotes} 显式声明
 * 「未接指标源」。<b>不填 0</b>：0 在告警面等于「无积压」，是差距文档 D-15/D-16 那类
 * 「恒 0 命中恒 PASS」假绿在运行期的翻版。</p>
 *
 * <p><b>{@code lastProducedAt} 的取值口径</b>：当前<b>无任何来源</b>可给该值——AdminClient 只给
 * offset（{@code ListOffsetsResult}），不含时间戳；要拿「最后投递时间」必须读记录本身，
 * 而读记录属消费面（禁在诊断端点里消费业务 topic）。故一律 {@code null} +
 * {@code lastProducedAtSource="NOT_WIRED"}，作为收口项登记（详见差距文档 B4-1 订正注）。</p>
 *
 * <p><b>档位 = L1（设计原文），显式覆盖路径默认 L3</b>：{@code /api/v1/system/} 前缀在
 * {@code ClearanceInterceptor.PATH_RULES} 里默认 L3，而 §3.3 L2127 对本端点写的是 L1。
 * 用方法级 {@link MinimumClearance}{@code (level=1)} 表达该差异——该方法级优先于路径级
 * （{@code resolveRequiredLevel} 的既定顺序），因此这是<b>按设计原文取档</b>，不是自行放松：
 * 事件拓扑属平台内部结构，不含租户数据，且端点仍必须过认证
 * （本路径不在 {@code AnonymousEndpointRegistry} 的 ANONYMOUS/CLEARANCE_EXEMPT 名录内）。</p>
 *
 * <p><b>三滤波器</b>：{@code VersionPrefixRewriteFilter} 无需登记（Controller 直接映射
 * {@code /api/system} 与 {@code /api/v1/system} 双路径，与 {@code RuntimeAlertV1Controller}
 * 的 KEEP 约定一致）；permitAll 不落（需认证）；准入档位由方法注解表达，见上。</p>
 *
 * <p>验收：{@code mvn -o test -pl gateway -Dtest='EventTopicsEndpointTest'}
 * （{@code ecos_backend/} 下执行）。</p>
 */
@RestController
@RequestMapping({"/api/system/events", "/api/v1/system/events"})
public class SystemEventTopicsV1Controller {

    private static final Logger log = LoggerFactory.getLogger(SystemEventTopicsV1Controller.class);

    /**
     * §4.3.3 之外的系统类错误码：设计 §3.3 L2127 对 listEventTopics 只点 {@code ECOS-SYS-503}。
     * <p>#142/B4-3e-27（差距 D-B4-65 余项「族码副本」）：面值改取 §3.2.7 族常量单源
     * {@link SystemErrorCode#ECOS_SYS_503}，本类不再自带 {@code "ECOS-SYS-"} 字面量副本
     * （该形态由 {@code NoMachineCodeFactoryFaceRatchetTest} 在 main 面零容忍钉住）。
     * 常量值不变 ⇒ 应答面逐字不变，既有断言
     * {@code EventTopicsEndpointTest} 取的也是本常量引用而非串。</p>
     */
    public static final String ERROR_SYS_UNAVAILABLE = SystemErrorCode.ECOS_SYS_503;

    /** 指标源缺席时的取值标记（{@code lagSource} 字段）。 */
    public static final String LAG_SOURCE_NOT_WIRED = "NOT_WIRED";

    /** DLQ 命名约定 —— 与 {@code DltConsumer.baseTopic()} 的 {@code .DLT} 后缀严格同形。 */
    static final String DLT_SUFFIX = ".DLT";

    /**
     * 运维/探活类 topic 名录（不进 {@link KafkaTopics} 业务契约常量）。
     *
     * <p>登记理由：{@code KafkaEventBusServiceImpl.PROBE_TOPIC = "ecos.health.probe"}
     * 是<b>私有常量</b>（该类的 broker 探活靠它发一条 {@code "probe"} 消息），不在
     * {@code KafkaTopics} 的 11 条之内，也不在 compose kafka-init 的 11 条之内。
     * 若本端点只反射 {@code KafkaTopics}，运维在诊断页看不到这个真实存在的 topic，
     * 属于「清单口径与实际投递面不一致」。因此在此<b>显式登记</b>为 OPERATIONAL 类，
     * 并标 {@code declaredIn="KafkaEventBusServiceImpl.PROBE_TOPIC"} 指回单源出处
     * （禁把它塞进 {@code KafkaTopics} —— 那是业务聚合事件契约面，探活不属该契约）。</p>
     */
    private static final List<OperationalTopic> OPERATIONAL_TOPICS = List.of(
            new OperationalTopic("ecos.health.probe", "KafkaEventBusServiceImpl.PROBE_TOPIC"));

    /** 运维类 topic 登记项（名称 + 单源出处，出处为文本而非反射目标，避免跨模块编译依赖）。 */
    record OperationalTopic(String name, String declaredIn) {
    }

    private final Optional<TopicLagSource> lagSource;

    /**
     * {@link Optional} 注入而非 {@code ObjectProvider}：与 runtime-event 既定口径一致
     * （该模块 AGENTS.md 的 Producer 样例即 {@code private final Optional<EventBusService> eventBus}），
     * 且默认档位（Kafka 开关关闭）容器内无 {@code TopicLagSource} Bean 时启动不失败。
     */
    public SystemEventTopicsV1Controller(Optional<TopicLagSource> lagSource) {
        this.lagSource = lagSource;
    }

    @GetMapping("/topics")
    @MinimumClearance(level = 1)
    public ApiResponse<Map<String, Object>> listEventTopics() {
        try {
            Map<String, Long> lagByTopic = readLagSnapshot();

            List<Map<String, Object>> topics = new ArrayList<>();
            for (String name : businessTopicNames()) {
                topics.add(entry(name, "BUSINESS", "KafkaTopics", lagByTopic));
            }
            for (OperationalTopic op : OPERATIONAL_TOPICS) {
                topics.add(entry(op.name(), "OPERATIONAL", op.declaredIn(), lagByTopic));
            }

            TopicLagSource source = lagSource.orElse(null);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("topics", topics);
            out.put("total", topics.size());
            out.put("dltSuffix", DLT_SUFFIX);
            out.put("lagSource", source == null ? LAG_SOURCE_NOT_WIRED : source.sourceId());
            out.put("lastProducedAtSource", LAG_SOURCE_NOT_WIRED);
            out.put("metricNotes", List.of(
                    "lag=null 表示指标源未装配或该 topic 无已提交位点，不等于 0（无积压）",
                    "lastProducedAt 无可用来源：AdminClient 仅暴露 offset，不含记录时间戳",
                    "dltTopic 按 DltConsumer 的 <topic>.DLT 命名约定推导，不代表该 DLT 已在 broker 建立"));
            return trace(ApiResponse.success(out));
        } catch (Exception ex) {
            log.error("listEventTopics 取 topic 单源失败（fail-closed，不返回空清单）: {}", ex.getMessage());
            return trace(ApiResponse.<Map<String, Object>>error(503, ERROR_SYS_UNAVAILABLE,
                    "事件 topic 清单不可用，请稍后重试"));
        }
    }

    /**
     * 出口统一带 traceId：MDC 有则用真链路 id，没有（异步/无 filter 面）则现造一个。
     *
     * <p>取该口径而非直接 {@code withTraceIdFromMdc()}（后者 MDC 缺省时保持 null）的理由：
     * 本端点是诊断面，{@code ECOS-SYS-503} 分支若连可追踪 id 都给不出，运维无法据此查日志；
     * 与 {@code ImpersonateController.resolveTraceId()}「保证 428/501 必带可追踪 id」同形。
     * 这是<b>关联 id</b>而非业务数据，不构成造假。</p>
     */
    private static ApiResponse<Map<String, Object>> trace(ApiResponse<Map<String, Object>> resp) {
        resp.withTraceIdFromMdc();
        if (resp.getTraceId() == null) {
            resp.withTraceId("t-" + java.util.UUID.randomUUID());
        }
        return resp;
    }

    /** 单 topic 投影：契约四字段（name/dltTopic/lag/lastProducedAt）+ 溯源两字段。 */
    private Map<String, Object> entry(String name, String category, String declaredIn,
                                      Map<String, Long> lagByTopic) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("dltTopic", name + DLT_SUFFIX);
        m.put("lag", lagByTopic.get(name));
        m.put("lastProducedAt", null);
        m.put("category", category);
        m.put("declaredIn", declaredIn);
        return m;
    }

    /** lag 快照读取：Bean 缺席或抛异常 ⇒ 空快照（各条 {@code lag} 因此为 null）。 */
    private Map<String, Long> readLagSnapshot() {
        TopicLagSource source = lagSource.orElse(null);
        if (source == null) return Map.of();
        Map<String, Long> raw;
        try {
            raw = source.snapshotLag();
        } catch (Exception e) {
            log.warn("TopicLagSource 取快照失败，本请求按「指标未取到」处理（不补 0）: {}", e.getMessage());
            return Map.of();
        }
        if (raw == null || raw.isEmpty()) return Map.of();
        Map<String, Long> out = new LinkedHashMap<>();
        raw.forEach((topic, lag) -> {
            if (topic != null && lag != null) out.put(topic, lag);
        });
        return out;
    }

    /**
     * topic 单源枚举接缝（包可见）：默认反射 {@link KafkaTopics}。
     *
     * <p>存在的理由是「fail-closed 分支必须能被真实触发过」——若 503 分支只能靠读代码相信它会走，
     * 那条断言就是空的（差距文档 D-15/D-16 记录的「恒 PASS 假绿」正是这类）。
     * 单测 {@code EventTopicsEndpointTest} 用子类覆写本方法抛异常，实测 503 + {@code ECOS-SYS-503}
     * 且响应体不含 {@code topics} 键；生产路径仍走真实反射，行为不变。</p>
     */
    List<String> businessTopicNames() throws Exception {
        return declaredBusinessTopics(KafkaTopics.class);
    }

    /** 反射取 {@link KafkaTopics} 的全部 {@code public static final String} 常量值（字典序）。 */
    static List<String> declaredBusinessTopics() throws IllegalAccessException {
        return declaredBusinessTopics(KafkaTopics.class);
    }

    /**
     * 同上，但单源类可指定（供门禁做「退化夹具」断言）。
     *
     * <p>空集＝异常：一个都没有说明反射面被裁剪或类被换，必须走 503 而非「无 topic」。</p>
     */
    static List<String> declaredBusinessTopics(Class<?> source) throws IllegalAccessException {
        Set<String> names = new TreeSet<>();
        for (Field f : source.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (!Modifier.isPublic(mod) || !Modifier.isStatic(mod) || !Modifier.isFinal(mod)) continue;
            if (!String.class.equals(f.getType())) continue;
            Object v = f.get(null);
            if (v instanceof String s && !s.isBlank()) names.add(s);
        }
        if (names.isEmpty()) {
            throw new IllegalStateException(source.getSimpleName()
                    + " 反射结果为空（反射面退化，禁当作「无 topic」）");
        }
        return new ArrayList<>(names);
    }
}
