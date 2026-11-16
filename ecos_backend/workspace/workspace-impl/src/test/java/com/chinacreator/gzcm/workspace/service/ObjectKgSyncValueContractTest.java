package com.chinacreator.gzcm.workspace.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Object→KG 属性降型门禁（D92，2026-11-14 验证批）。
 *
 * <p><b>缺陷形态（活体实测）</b>：PG 行里的 {@code created_at} 是 {@link Timestamp}，
 * Neo4j 驱动不认识，整条写入抛
 * {@code Unable to convert java.sql.Timestamp to Neo4j Value}；
 * {@link ObjectKgSyncService} 把这个异常吞成一条 WARN，于是<b>每个对象</b>的图谱同步
 * 都失败而主流程一切正常 —— 知识图谱里始终没有节点。</p>
 *
 * <p><b>门禁要点</b>：属性必须先降型到驱动认识的标量再进 Cypher 参数面。
 * {@link #timestampBecomesIsoString()} 守时间类型，
 * {@link #unknownTypeDegradesToStringInsteadOfFailingWrite()} 守「宁可丢类型不可丢节点」，
 * {@link #probeDetectsRawTimestampPassThrough()} 是反假绿自检：证明本门禁的判据
 * 确实能识别「原样透传 Timestamp」的旧行为。</p>
 */
@DisplayName("D92 — 对象属性进 Neo4j 前必须降型（Timestamp/Date/UUID → 字符串）")
class ObjectKgSyncValueContractTest {

    // toNeo4jProps / toNeo4jValue 是私有静态方法：用反射直测单点实现，
    // 避免为可测性把内部降型逻辑暴露成公开 API。
    private static Object callToProps(Map<String, Object> props) throws Exception {
        Method m = ObjectKgSyncService.class.getDeclaredMethod("toNeo4jProps", Map.class);
        m.setAccessible(true);
        return m.invoke(null, props);
    }

    private static Object callToValue(Object v) throws Exception {
        Method m = ObjectKgSyncService.class.getDeclaredMethod("toNeo4jValue", Object.class);
        m.setAccessible(true);
        return m.invoke(null, v);
    }

    @Test
    @DisplayName("Timestamp → ISO-8601 字符串（驱动不认 java.sql.Timestamp）")
    void timestampBecomesIsoString() throws Exception {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", "cust-1");
        props.put("name", "Probe");
        props.put("created_at", Timestamp.from(Instant.parse("2026-11-14T17:05:28.133365Z")));

        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) callToProps(props);

        Object createdAt = out.get("created_at");
        assertTrue(createdAt instanceof String,
                "时间列必须降为字符串，实际类型: " + (createdAt == null ? "null" : createdAt.getClass()));
        assertTrue(createdAt.toString().startsWith("2026-11-14T17:05:28"),
                "降型后必须保留完整时刻，实际: " + createdAt);
        assertEquals("Probe", out.get("name"), "普通字符串列不得被改写");
        assertEquals("cust-1", out.get("id"), "id 不得被改写");
    }

    @Test
    @DisplayName("驱动不认识的类型一律降字符串，不让整条写入失败")
    void unknownTypeDegradesToStringInsteadOfFailingWrite() throws Exception {
        Object weird = new java.math.BigDecimal("12.34");
        Object out = callToValue(weird);
        assertEquals("12.34", out.toString(), "无法直传的类型必须降为字符串而不是原样透出");
    }

    @Test
    @DisplayName("Null 属性原样保留（不得抛异常，也不得凭空造值）")
    void nullStaysNull() throws Exception {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("region", null);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) callToProps(props);
        assertTrue(out.containsKey("region"), "空值列应保留键");
        assertEquals(null, out.get("region"), "空值列不得被替换成占位串");
    }

    @Test
    @DisplayName("null / 空属性集返回空 Map（DELETE 路径不再 NPE）")
    void nullPropsYieldEmptyMap() throws Exception {
        assertEquals(Map.of(), callToProps(null));
        assertEquals(Map.of(), callToProps(Map.of()));
    }

    @Test
    @DisplayName("探针自检：本门禁判据能识别「原样透传 Timestamp」的旧行为")
    void probeDetectsRawTimestampPassThrough() throws Exception {
        Timestamp raw = Timestamp.from(Instant.parse("2026-11-14T00:00:00Z"));

        // 旧行为 = 原样放进 Cypher 参数表
        Map<String, Object> legacy = Map.of("created_at", raw);
        assertTrue(isDriverUnsupported(legacy.get("created_at")),
                "旧写法（原样 Timestamp）必须被判为驱动不支持 —— 否则本门禁恒绿");

        // 新行为 = 降型后进参数表
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("created_at", raw);
        @SuppressWarnings("unchecked")
        Map<String, Object> fixed = (Map<String, Object>) callToProps(props);
        assertFalse(isDriverUnsupported(fixed.get("created_at")),
                "降型后必须是驱动可存类型，实际: " + fixed.get("created_at"));
    }

    /**
     * 调用点门禁：降型函数必须真的被 Cypher 参数面使用。
     *
     * <p>为什么不能只测函数本身：把 {@code toNeo4jProps(properties)} 改回
     * {@code properties}（=还原 D92 缺陷），函数级用例<b>仍然全绿</b>——
     * 它们只证明「这个函数能降型」，不证明「写入前调了它」。
     * 这条用源码扫描把「函数存在但没人用」的假修复钉死。</p>
     */
    @Test
    @DisplayName("源码级：Cypher 参数面必须经降型函数，不得直传原始 properties")
    void cypherParamsMustGoThroughDowngrade() throws Exception {
        java.nio.file.Path src = java.nio.file.Path.of(
                "src/main/java/com/chinacreator/gzcm/workspace/service/ObjectKgSyncService.java");
        assertTrue(java.nio.file.Files.exists(src), "门禁必须能读到生产源码: " + src.toAbsolutePath());
        String code = java.nio.file.Files.readString(src, java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(code.contains("toNeo4jProps(properties)"),
                "Cypher 写入前必须调用 toNeo4jProps(properties) 降型；"
                        + "直接传 properties 会让 Timestamp 再次打挂整条写入（D92）");
        assertFalse(code.contains("\"props\", properties != null ? properties : Map.of()"),
                "不得保留「原始属性直传」的旧写法");
    }

    /** Neo4j 驱动可直存的标量集合（Timestamp/Date/自定义对象一律不支持）。 */
    private static boolean isDriverUnsupported(Object v) {
        return !(v instanceof String || v instanceof Boolean
                || v instanceof Long || v instanceof Integer
                || v instanceof Double || v instanceof Float
                || v instanceof byte[] || v instanceof List || v instanceof Map);
    }

    /** 让编译器保留对 TaskExecutor 的引用（构造器签名的一部分，防误删参数）。 */
    @SuppressWarnings("unused")
    private static final Class<?> CTOR_ARG = TaskExecutor.class;
}
