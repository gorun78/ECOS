package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.event.TopicLagSource;
import com.chinacreator.gzcm.sysman.security.MinimumClearance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code GET /api/v1/system/events/topics}（详设-00 §3.3 L2127 {@code listEventTopics}）契约门禁
 * —— #142/B4-1 端点缺口第 2 项。
 *
 * <p>钉死五件事：
 * <ol>
 *   <li>清单<b>来自 {@code KafkaTopics} 反射单源</b>，且与 {@code KafkaTopicParityTest.COMPOSE_MIRROR}
 *       （compose kafka-init 11 条镜像）<b>逐条对等</b>——防止「端点里另抄一份」的第二套真值；</li>
 *   <li>契约四字段（name / dltTopic / lag / lastProducedAt）齐备，{@code dltTopic} 严格 {@code <topic>.DLT}；</li>
 *   <li><b>指标缺源时 {@code lag} 必须为 {@code null}，禁为 0</b>（0 在告警面等于「无积压」＝假绿），
 *       且顶层 {@code lagSource}/{@code lastProducedAtSource} 明示 {@code NOT_WIRED}；</li>
 *   <li>反射面退化 ⇒ <b>503 + {@code ECOS-SYS-503}</b>，且不返回 {@code topics} 键冒充「无 topic」
 *       （该分支由覆写接缝真实触发过，不是读代码相信它会走）；</li>
 *   <li>档位 = 契约原文的 <b>L1</b>（显式覆盖 {@code /api/v1/system/} 路径默认 L3），
 *       且 {@code /api/v1/system/events/topics} 双路径映射在位。</li>
 * </ol>
 */
class EventTopicsEndpointTest {

    /**
     * compose 侧 11 条镜像：反射取 {@code KafkaTopicParityTest} 的私有常量，保持单源不分叉。
     *
     * <p>用 {@code Class.forName} 而非直接引用——该类是包级可见（{@code com.chinacreator.gzcm.gateway}），
     * 跨包编译期引用不到；反射既免改可见性，也让「镜像漂移」在运行期即失败。</p>
     */
    @SuppressWarnings("unchecked")
    private static List<String> composeMirror() throws Exception {
        Class<?> parity = Class.forName("com.chinacreator.gzcm.gateway.KafkaTopicParityTest");
        Field f = parity.getDeclaredField("COMPOSE_MIRROR");
        f.setAccessible(true);
        return (List<String>) f.get(null);
    }

    private static SystemEventTopicsV1Controller withoutLagSource() {
        return new SystemEventTopicsV1Controller(Optional.empty());
    }

    @Test
    @DisplayName("清单与 compose 11 条逐条对等（BUSINESS 类反射单源，禁内联第二套真值）")
    void businessTopicsMatchComposeMirror() throws Exception {
        ApiResponse<Map<String, Object>> resp = withoutLagSource().listEventTopics();
        assertEquals(0, resp.getCode(), "取单源成功必须 code=0，实际 errorCode=" + resp.getErrorCode());
        Map<String, Object> data = resp.getData();
        assertNotNull(data);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topics = (List<Map<String, Object>>) data.get("topics");
        Set<String> business = new TreeSet<>();
        for (Map<String, Object> t : topics) {
            if ("BUSINESS".equals(t.get("category"))) business.add(String.valueOf(t.get("name")));
        }
        assertEquals(new TreeSet<>(composeMirror()), business,
                "端点 BUSINESS topic 清单必须与 KafkaTopicParityTest.COMPOSE_MIRROR 完全一致");
        assertEquals(topics.size(), data.get("total"),
                "total 必须等于实际枚举出的条数（含 OPERATIONAL 类，禁只数业务条数造成 total 与 items 不符）");
        assertEquals(composeMirror().size() + 1, topics.size(),
                "清单 = compose 11 条业务 + 1 条 OPERATIONAL 探活");
        assertNotNull(resp.getTraceId(), "诊断面成功响应也必须带可追踪 traceId");
    }

    @Test
    @DisplayName("探活 topic 作为 OPERATIONAL 类在名录内，并指回单源出处")
    void operationalProbeTopicIsListedWithItsDeclarationSite() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topics =
                (List<Map<String, Object>>) withoutLagSource().listEventTopics().getData().get("topics");
        Map<String, Object> probe = topics.stream()
                .filter(t -> "ecos.health.probe".equals(t.get("name")))
                .findFirst().orElse(null);
        assertNotNull(probe, "KafkaEventBusServiceImpl.PROBE_TOPIC 真实投递的 topic 必须可见，否则清单口径与实际投递面不一致");
        assertEquals("OPERATIONAL", probe.get("category"));
        assertEquals("KafkaEventBusServiceImpl.PROBE_TOPIC", probe.get("declaredIn"));
    }

    @Test
    @DisplayName("契约四字段齐备 + dltTopic 严格 <topic>.DLT")
    void contractFieldsAndDltNaming() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topics =
                (List<Map<String, Object>>) withoutLagSource().listEventTopics().getData().get("topics");
        assertFalse(topics.isEmpty());
        for (Map<String, Object> t : topics) {
            assertTrue(t.containsKey("name"), "缺 name");
            assertTrue(t.containsKey("dltTopic"), "缺 dltTopic");
            assertTrue(t.containsKey("lag"), "缺 lag 键（值可为 null，键必须在位）");
            assertTrue(t.containsKey("lastProducedAt"), "缺 lastProducedAt 键");
            assertEquals(t.get("name") + ".DLT", t.get("dltTopic"),
                    "DLQ 命名必须与 DltConsumer.baseTopic() 的 .DLT 后缀同形");
        }
    }

    @Test
    @DisplayName("指标未接：lag=null 且禁为 0；顶层声明 NOT_WIRED + metricNotes")
    void lagIsNullNotZeroWhenSourceAbsent() {
        Map<String, Object> data = withoutLagSource().listEventTopics().getData();
        assertEquals(SystemEventTopicsV1Controller.LAG_SOURCE_NOT_WIRED, data.get("lagSource"));
        assertEquals("NOT_WIRED", data.get("lastProducedAtSource"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topics = (List<Map<String, Object>>) data.get("topics");
        for (Map<String, Object> t : topics) {
            assertNull(t.get("lag"), "无指标源时 lag 必须为 null（填 0＝冒充无积压，假绿）");
            assertNull(t.get("lastProducedAt"), "AdminClient 不暴露记录时间戳，禁造 lastProducedAt");
        }
        @SuppressWarnings("unchecked")
        List<String> notes = (List<String>) data.get("metricNotes");
        assertNotNull(notes);
        assertTrue(notes.stream().anyMatch(s -> s.contains("不等于 0")),
                "响应必须自带「null≠0」的解释，防止消费方误读");
    }

    @Test
    @DisplayName("接入 lag 源时逐条取真实值，源名取自 sourceId")
    void lagValuesArePassedThroughWhenSourceWired() {
        TopicLagSource stub = () -> Map.of("ecos.audit", 7L, "ecos.ontology", 42L);
        Map<String, Object> data = new SystemEventTopicsV1Controller(Optional.of(stub))
                .listEventTopics().getData();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topics = (List<Map<String, Object>>) data.get("topics");
        assertEquals(7L, byName(topics, "ecos.audit").get("lag"));
        assertEquals(42L, byName(topics, "ecos.ontology").get("lag"));
        // 快照里没有的 topic 仍是 null（缺数据≠0），不被真实值「顺带补全」
        assertNull(byName(topics, "ecos.knowledge").get("lag"));
        assertEquals("UNKNOWN", data.get("lagSource"), "sourceId 默认值必须如实呈现，不冒充 KAFKA_ADMIN");
    }

    @Test
    @DisplayName("lag 源抛异常 ⇒ 只降级指标（lag 全 null），清单仍出（不因诊断面抖动 503）")
    void lagSourceFailureDegradesToNullLag() {
        TopicLagSource broken = () -> {
            throw new IllegalStateException("broker unreachable");
        };
        ApiResponse<Map<String, Object>> resp =
                new SystemEventTopicsV1Controller(Optional.of(broken)).listEventTopics();
        assertEquals(0, resp.getCode());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topics =
                (List<Map<String, Object>>) resp.getData().get("topics");
        assertFalse(topics.isEmpty(), "指标面失败不得清空清单");
        assertTrue(topics.stream().allMatch(t -> t.get("lag") == null));
    }

    @Test
    @DisplayName("topic 单源退化 ⇒ 503 + ECOS-SYS-503，且不返回 topics 冒充「无 topic」（分支真实触发）")
    void degenerateTopicSourceFailsClosed() {
        SystemEventTopicsV1Controller broken = new SystemEventTopicsV1Controller(Optional.empty()) {
            @Override
            List<String> businessTopicNames() throws Exception {
                throw new IllegalStateException("反射面退化（夹具）");
            }
        };
        ApiResponse<Map<String, Object>> resp = broken.listEventTopics();
        assertEquals(503, resp.getCode());
        assertEquals(SystemEventTopicsV1Controller.ERROR_SYS_UNAVAILABLE, resp.getErrorCode());
        assertNull(resp.getData(), "DENY 面禁带 data，空 topics 同样属冒充");
        assertNotNull(resp.getTraceId());
    }

    @Test
    @DisplayName("退化夹具：无 String 常量的类 ⇒ 反射结果空即抛，禁被解释为「无 topic」")
    void emptyReflectionSurfaceThrowsInsteadOfEmptyList() {
        assertThrows(IllegalStateException.class,
                () -> SystemEventTopicsV1Controller.declaredBusinessTopics(Object.class));
    }

    @Test
    @DisplayName("档位与路径：方法级 L1（覆盖 /api/v1/system/ 默认 L3）+ 双路径映射在位")
    void clearanceLevelAndDualPathMapping() throws Exception {
        Method m = SystemEventTopicsV1Controller.class.getMethod("listEventTopics");
        MinimumClearance mc = m.getAnnotation(MinimumClearance.class);
        assertNotNull(mc, "listEventTopics 必须显式带 @MinimumClearance，否则落路径默认 L3 与设计原文 L1 不符");
        assertEquals(1, mc.level());
        assertNotNull(m.getAnnotation(GetMapping.class), "/topics 必须是 GET（契约 listEventTopics）");

        RequestMapping rm = SystemEventTopicsV1Controller.class.getAnnotation(RequestMapping.class);
        assertNotNull(rm);
        List<String> paths = List.of(rm.value());
        assertTrue(paths.contains("/api/v1/system/events") && paths.contains("/api/system/events"),
                "双路径必须各写一遍（三滤波器约定），实际=" + paths);
    }

    private static Map<String, Object> byName(List<Map<String, Object>> topics, String name) {
        return topics.stream().filter(t -> name.equals(t.get("name"))).findFirst()
                .orElseThrow(() -> new AssertionError("清单缺 topic: " + name));
    }
}
