package com.chinacreator.gzcm.engine.data.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * F02-07（详细设计-02）DQ 发布前门禁 11 规则 验收测试。
 *
 * <p>覆盖设计验收用例：</p>
 * <ul>
 *   <li>{@code DqRuleF01ToF11Test} — 11 条规则（DQ-F01~F11）各一个违规行 → 断言命中的 ruleId + 定位字段；若干合规行 → 断言全通过（null）</li>
 *   <li>{@code PendingQueueActionableTest} — rejected 行含 ruleId + 定位字段 + 修复入口（suggestion）</li>
 *   <li>{@code NoSilentDefaultFillArchTest} — fact 写入路径源码禁 {@code putIfAbsent(.*, 0)} 类"补默认以通过"填充</li>
 * </ul>
 *
 * <p>策略：直接对这 11 条内置参数化规则做纯函数断言（反射 {@code runDqRules}，DB-free），
 * 规避持久化依赖；rejected 语义用 {@code importBusinessFacts}（mock JdbcTemplate，全 rejected
 * 批次不触库）验证。</p>
 */
class DqRuleF01ToF11Test {

    private static Method runDqRules;

    @BeforeAll
    static void setUp() throws Exception {
        runDqRules = BusinessFactService.class.getDeclaredMethod("runDqRules", String.class, Map.class);
        runDqRules.setAccessible(true);
    }

    private String[] dq(String factType, Map<String, Object> row) throws Exception {
        return (String[]) runDqRules.invoke(new BusinessFactService(mock(JdbcTemplate.class)), factType, row);
    }

    // 合规基线（各 factType 必填字段齐备 + 值合法）
    private static Map<String, Object> validStage() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("project_id", "p-1");
        r.put("department_id", "d-1");
        r.put("period", "2025-07");
        r.put("stage", "DELIVERY");
        r.put("fact_type", "REVENUE");
        return r;
    }

    private static Map<String, Object> validResource() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("project_id", "p-1");
        r.put("department_id", "d-1");
        r.put("period", "2025-07");
        r.put("staff_ref_hash", "a".repeat(64));
        r.put("fact_type", "WORK");
        return r;
    }

    private static Map<String, Object> validCost() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("department_id", "d-1");
        r.put("period", "2025-07");
        r.put("fact_type", "COST");
        return r;
    }

    @Test
    @DisplayName("合规 stage 行 → 全通过（11 规则无命中）")
    @SuppressWarnings("unchecked")
    void stage_validRow_passes() throws Exception {
        assertNull(dq("stage", validStage()), "合规行应全通过（返回 null）");
    }

    @Test
    @DisplayName("DQ-F01 必填项非空 — 缺 project_id → 命中 F01 且定位字段")
    @SuppressWarnings("unchecked")
    void f01_missingRequired() throws Exception {
        Map<String, Object> r = validStage();
        r.remove("project_id");
        String[] e = dq("stage", r);
        assertNotNull(e);
        assertEquals("DQ-F01", e[1]);
        assertTrue(e[0].contains("project_id"), "应定位到缺的必填字段; 实际 " + e[0]);
    }

    @Test
    @DisplayName("DQ-F02 期/月格式 — period=2025-7（非 yyyy-MM）→ F02")
    void f02_badPeriodShape() throws Exception {
        Map<String, Object> r = validStage();
        r.put("period", "2025-7");
        String[] e = dq("stage", r);
        assertNotNull(e);
        assertEquals("DQ-F02", e[1], "非 yyyy-MM 期格式应命中 F02; 实际 " + e[1]);
    }

    @Test
    @DisplayName("DQ-F03 金额精度 — amount=1.234（>2 位小数）→ F03")
    void f03_amountPrecision() throws Exception {
        Map<String, Object> r = validStage();
        r.put("amount", "1.234");
        String[] e = dq("stage", r);
        assertNotNull(e);
        assertEquals("DQ-F03", e[1], "精度越界应命中 F03; 实际 " + e[1]);
    }

    @Test
    @DisplayName("DQ-F04 比率越界 — attribution_ratio=1.5 → F04")
    void f04_ratioOutOfRange() throws Exception {
        Map<String, Object> r = validStage();
        r.put("attribution_ratio", "1.5");
        String[] e = dq("stage", r);
        assertNotNull(e);
        assertEquals("DQ-F04", e[1], "[0,1] 越界应命中 F04; 实际 " + e[1]);
    }

    @Test
    @DisplayName("DQ-F05 金额行需 3char currency — 有 amount 无 currency → F05")
    void f05_amountWithoutCurrency() throws Exception {
        Map<String, Object> r = validStage();
        r.put("amount", "100.00");
        String[] e = dq("stage", r);
        assertNotNull(e);
        assertEquals("DQ-F05", e[1], "金额行缺 3char currency 应命中 F05; 实际 " + e[1]);
    }

    @Test
    @DisplayName("DQ-F06 引用字段长度 — source_ref 256 字符 → F06")
    void f06_refTooLong() throws Exception {
        Map<String, Object> r = validStage();
        r.put("source_ref", "a".repeat(256));
        String[] e = dq("stage", r);
        assertNotNull(e);
        assertEquals("DQ-F06", e[1], "引用字段 >255 应命中 F06; 实际 " + e[1]);
    }

    @Test
    @DisplayName("DQ-F07 键长 ≤36 — project_id 37 字符 → F07")
    void f07_keyTooLong() throws Exception {
        Map<String, Object> r = validStage();
        r.put("project_id", "a".repeat(37));
        String[] e = dq("stage", r);
        assertNotNull(e);
        assertEquals("DQ-F07", e[1], "键长 >36 应命中 F07; 实际 " + e[1]);
    }

    @Test
    @DisplayName("DQ-F08 期间值域 — period=2025-13（月 13）→ F08")
    void f08_periodValueRange() throws Exception {
        Map<String, Object> r = validStage();
        r.put("period", "2025-13");
        String[] e = dq("stage", r);
        assertNotNull(e);
        assertEquals("DQ-F08", e[1], "月/年非法应命中 F08; 实际 " + e[1]);
    }

    @Test
    @DisplayName("DQ-F09 资源表工号哈希 — staff_ref_hash 非 64 hex → F09")
    void f09_staffHashBad() throws Exception {
        Map<String, Object> r = validResource();
        r.put("staff_ref_hash", "not-a-hash");
        String[] e = dq("resource", r);
        assertNotNull(e);
        assertEquals("DQ-F09", e[1], "非 SHA-256 hex(64) 应命中 F09; 实际 " + e[1]);
    }

    @Test
    @DisplayName("DQ-F10 fact_type 值域 — fact_type=bad type! → F10")
    void f10_factTypeBad() throws Exception {
        Map<String, Object> r = validStage();
        r.put("fact_type", "bad type!");
        String[] e = dq("stage", r);
        assertNotNull(e);
        assertEquals("DQ-F10", e[1], "fact_type 非短大写码应命中 F10; 实际 " + e[1]);
    }

    @Test
    @DisplayName("DQ-F11 cost is_pool 二值 — is_pool=2 → F11")
    void f11_isPoolBad() throws Exception {
        Map<String, Object> r = validCost();
        r.put("is_pool", "2");
        String[] e = dq("cost", r);
        assertNotNull(e);
        assertEquals("DQ-F11", e[1], "is_pool 非 0/1 应命中 F11; 实际 " + e[1]);
    }

    // ==================== PendingQueueActionableTest ====================

    @Test
    @DisplayName("PendingQueueActionableTest — 违规行经 importBusinessFacts → rejected 含 ruleId+定位字段+修复入口；accepted=0（不触库）")
    void pendingQueue_actionablePendingRow() {
        BusinessFactService svc = new BusinessFactService(mock(JdbcTemplate.class));
        Map<String, Object> bad = validStage();
        bad.put("period", "2025-13"); // 触发 F08
        BusinessFactService.ImportResult res =
                svc.importBusinessFacts("stage", "bq-pending", List.of(bad));
        assertEquals(0, res.accepted(), "违规行不应落库（accepted=0）");
        assertEquals(1, res.rejected().size(), "应有 1 条待处理行");
        BusinessFactService.RejectedRow row = res.rejected().get(0);
        assertNotNull(row.ruleId());
        assertEquals("DQ-F08", row.ruleId(), "待处理行应带规则 ID");
        assertNotNull(row.field());
        assertTrue(row.field().contains("period"), "应带定位字段; 实际 " + row.field());
        assertNotNull(row.suggestion());
        assertTrue(!row.suggestion().isBlank(), "应带修复入口（suggestion 非空）");
        assertTrue(row.rowNo() >= 1, "应带行号定位");
    }

    // ==================== NoSilentDefaultFillArchTest ====================

    @Test
    @DisplayName("NoSilentDefaultFillArchTest — fact 写入路径源码禁 putIfAbsent(.*, 0) 类补默认填充")
    void noSilentDefaultFill() throws Exception {
        Path src = Path.of("src/main/java/com/chinacreator/gzcm/engine/data/service/BusinessFactService.java");
        assertTrue(Files.exists(src), "应能定位 BusinessFactService 源文件");
        String code = Files.readString(src);
        // 红线：禁"补默认值/补 0 以通过校验"
        assertTrue(!code.contains("putIfAbsent(null, 0)") && !code.contains("putIfAbsent(null, 0L)"),
                "禁 putIfAbsent(null, 0) 类补默认填充");
        assertTrue(!code.contains("coalesceZero") && !code.contains("fillWithZero"),
                "禁以 0 填充越界/缺失值");
    }
}
