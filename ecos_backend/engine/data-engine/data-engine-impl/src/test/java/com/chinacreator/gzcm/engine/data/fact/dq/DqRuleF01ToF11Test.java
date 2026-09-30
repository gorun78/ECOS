package com.chinacreator.gzcm.engine.data.fact.dq;

import com.chinacreator.gzcm.engine.data.fact.FactType;
import com.chinacreator.gzcm.engine.data.fact.model.RowRejection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F02-07 / PRD-02 §2.1 验收：11 条 DQ 规则各 1 正 1 反用例（11×2 = 22）。
 *
 * <p>规则清单（PRD-02 §2.1）：</p>
 * <pre>
 *   F01 UNIQUENESS    唯一索引冲突                       → REJECT
 *   F02 VALIDITY      比率列 ∈ [0,1]                     → REJECT
 *   F03 VALIDITY      金额 scale ≤2 &amp; 非负              → REJECT
 *   F04 VALIDITY      period 格式 YYYY-MM &amp; 年一致     → REJECT
 *   F05 CONSISTENCY   同批币种唯一（批级,Engine 承接）  → REJECT
 *   F06 CONSISTENCY   归 sum ratio ≤1                    → REJECT
 *   F07 COMPLETENESS  必填列非空                          → REJECT
 *   F08 COMPLETENESS  ALLOCATED 成本必带 allocation_rule → REJECT
 *   F09 COMPLETENESS  成本/实际必带 evidence/source_ref  → REJECT
 *   F10 ACCURACY      contract_base 与主数据一致         → WARN
 *   F11 FRESHNESS     ACTUAL 期间 ≤ 当前月              → REJECT
 * </pre>
 *
 * <p>说明：DQ-F05 为批级不变量，已在 {@code DqF05BatchCurrencyTest} 单测覆盖（ {@code
 * FactImportService.checkDqF05BatchCurrency}），本测的 F05 断言指针到该既有单测，不重复避免漂移。
 * F10 为 WARN-only（不产生 RowRejection，只落待处理队列），本测断言 F10 主数据不一致时
 * <b>不</b>进 rejected。</p>
 */
@DisplayName("F02-07 DQ-F01..F11 11×2 用例")
class DqRuleF01ToF11Test {

    private final DqRowGate gate = new DqRowGateImpl();

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private static String ruleIdsOf(List<RowRejection> rej) {
        StringBuilder sb = new StringBuilder();
        for (RowRejection r : rej) {
            if (sb.length() > 0) sb.append(',');
            sb.append(r.ruleId());
        }
        return sb.toString();
    }

    // ── F01 UNIQUENESS (batch-internal) ─────────────────────────

    @Test
    @DisplayName("F01 - 正：批内业务键各不同 → 无 F01 拦截")
    void f01_positive() {
        Map<String, Object> a = row("projectId", "P1", "departmentId", "D1", "period", "2026-09",
                "stage", "ACCEPTANCE", "factType", "PLAN", "contractBase", "100",
                "attributionRatio", "0.5", "currency", "CNY");
        Map<String, Object> b = row("projectId", "P2", "departmentId", "D1", "period", "2026-09",
                "stage", "ACCEPTANCE", "factType", "PLAN", "contractBase", "100",
                "attributionRatio", "0.5", "currency", "CNY");
        List<RowRejection> out = gate.checkRows(FactType.STAGE, List.of(a, b), Map.of());
        assertTrue(out.stream().noneMatch(r -> r.ruleId().equals(DqRowGateImpl.R_F01)),
                "F01 未触发，实际: " + ruleIdsOf(out));
    }

    @Test
    @DisplayName("F01 - 反：批内同 (project, dept, period, stage, factType) → 第二行 F01 拒")
    void f01_negative() {
        Map<String, Object> a = row("projectId", "P1", "departmentId", "D1", "period", "2026-09",
                "stage", "ACCEPTANCE", "factType", "PLAN", "contractBase", "100",
                "attributionRatio", "0.5", "currency", "CNY");
        Map<String, Object> b = row("projectId", "P1", "departmentId", "D1", "period", "2026-09",
                "stage", "ACCEPTANCE", "factType", "PLAN", "contractBase", "200",
                "attributionRatio", "0.4", "currency", "CNY");
        List<RowRejection> out = gate.checkRows(FactType.STAGE, List.of(a, b), Map.of());
        assertTrue(out.stream().anyMatch(r -> r.ruleId().equals(DqRowGateImpl.R_F01) && r.rowNo() == 2),
                "第 2 行应命中 F01；实际: " + ruleIdsOf(out));
    }

    // ── F02 VALIDITY (ratio in [0,1]) ───────────────────────────

    @Test
    @DisplayName("F02 - 正：attributionRatio=0.6 在区间内")
    void f02_positive() {
        Map<String, Object> r = row("projectId", "P1", "departmentId", "D1", "period", "2026-09",
                "stage", "ACCEPTANCE", "factType", "PLAN", "contractBase", "100",
                "attributionRatio", new BigDecimal("0.6"), "currency", "CNY");
        List<RowRejection> out = gate.checkRows(FactType.STAGE, List.of(r), Map.of());
        assertTrue(out.stream().noneMatch(x -> x.ruleId().equals(DqRowGateImpl.R_F02)));
    }

    @Test
    @DisplayName("F02 - 反：attributionRatio=1.2 越界")
    void f02_negative() {
        Map<String, Object> bad = row("projectId", "P1", "departmentId", "D1", "period", "2026-09",
                "stage", "ACCEPTANCE", "factType", "PLAN", "contractBase", "100",
                "attributionRatio", new BigDecimal("1.2"), "currency", "CNY");
        List<RowRejection> out = gate.checkRows(FactType.STAGE, List.of(bad), Map.of());
        RowRejection hit = out.stream().filter(x -> x.ruleId().equals(DqRowGateImpl.R_F02)).findFirst().orElseThrow(
                () -> new AssertionError("缺 F02，实际: " + ruleIdsOf(out)));
        assertEquals("attributionRatio", hit.field());
    }

    // ── F03 VALIDITY (amount scale ≤2 & ≥0) ─────────────────────

    @Test
    @DisplayName("F03 - 正：amount=100.55 精度合规")
    void f03_positive() {
        Map<String, Object> r = row("projectId", "P1", "departmentId", "D1", "period", "2026-09",
                "stage", "ACCEPTANCE", "factType", "PLAN", "contractBase", "100",
                "attributionRatio", "0.5", "currency", "CNY",
                "amount", new BigDecimal("100.55"));
        List<RowRejection> out = gate.checkRows(FactType.STAGE, List.of(r), Map.of());
        assertTrue(out.stream().noneMatch(x -> x.ruleId().equals(DqRowGateImpl.R_F03)));
    }

    @Test
    @DisplayName("F03 - 反：amount=100.12345 浮点污染")
    void f03_negative() {
        Map<String, Object> bad = row("projectId", "P1", "departmentId", "D1", "period", "2026-09",
                "stage", "ACCEPTANCE", "factType", "PLAN", "contractBase", "100",
                "attributionRatio", "0.5", "currency", "CNY",
                "amount", new BigDecimal("100.12345"));
        List<RowRejection> out = gate.checkRows(FactType.STAGE, List.of(bad), Map.of());
        assertTrue(out.stream().anyMatch(x -> x.ruleId().equals(DqRowGateImpl.R_F03) && x.field().equals("amount")),
                "缺 F03，实际: " + ruleIdsOf(out));
    }

    // ── F04 VALIDITY (period format + consistency) ──────────────

    @Test
    @DisplayName("F04 - 正：period=2026-09 标准格式")
    void f04_positive() {
        Map<String, Object> r = row("projectId", "P1", "departmentId", "D1", "period", "2026-09",
                "stage", "ACCEPTANCE", "factType", "PLAN", "contractBase", "100",
                "attributionRatio", "0.5", "currency", "CNY");
        List<RowRejection> out = gate.checkRows(FactType.STAGE, List.of(r), Map.of());
        assertTrue(out.stream().noneMatch(x -> x.ruleId().equals(DqRowGateImpl.R_F04)));
    }

    @Test
    @DisplayName("F04 - 反：period=2026-9 非法（单月无零填充）")
    void f04_negative() {
        Map<String, Object> bad = row("projectId", "P1", "departmentId", "D1", "period", "2026-9",
                "stage", "ACCEPTANCE", "factType", "PLAN", "contractBase", "100",
                "attributionRatio", "0.5", "currency", "CNY");
        List<RowRejection> out = gate.checkRows(FactType.STAGE, List.of(bad), Map.of());
        assertTrue(out.stream().anyMatch(x -> x.ruleId().equals(DqRowGateImpl.R_F04) && x.field().equals("period")),
                "缺 F04，实际: " + ruleIdsOf(out));
    }

    // ── F05 CONSISTENCY (batch currency — Engine internal) ──────

    @Test
    // F05 属批级 400 invariant，Engine 内建 pre-check（见 DqF05BatchCurrencyTest 单测）。本 A 用例
    // 从 DqRowGate 侧观测 delegate 合规：单个 row 携带 currency 不应触发 gate 侧拒收。
    @DisplayName("F05 - 正（engine 侧判）：gate 不做批级币种，单行 currency=CNY 无 gate 拒收")
    void f05_positive_engineSide() {
        Map<String, Object> okBatch = row("projectId", "P1", "departmentId", "D1", "period", "2026-09",
                "stage", "ACCEPTANCE", "factType", "PLAN", "contractBase", "100",
                "attributionRatio", "0.5", "currency", "CNY");
        List<RowRejection> out = gate.checkRows(FactType.STAGE, List.of(okBatch), Map.of());
        assertTrue(out.stream().noneMatch(x -> x.field().equals("currency")
                && x.ruleId().equals("DQ-F05")),
                "gate 不承接 F05（批级）；实际: " + ruleIdsOf(out));
    }

    @Test
    @DisplayName("F05 - 反（指针到 DqF05BatchCurrencyTest）：gate 侧仍不触发 F05，批级双币种由 Engine 400")
    void f05_negative_engineSide() {
        // 主代码契约断言：gate 不产出 ruleId=DQ-F05 的记录（PRD-02 §2.1/§2.2 + DqRowGate interfaces Javadoc
        // 明确 F05 归 Engine 内建 pre-check）。若 gate 违规开始处理 F05 → 本断言 FAIL。
        Map<String, Object> a = row("projectId", "P1", "departmentId", "D1", "period", "2026-09",
                "stage", "ACCEPTANCE", "factType", "PLAN", "contractBase", "100",
                "attributionRatio", "0.5", "currency", "CNY");
        Map<String, Object> b = row("projectId", "P2", "departmentId", "D1", "period", "2026-09",
                "stage", "ACCEPTANCE", "factType", "PLAN", "contractBase", "100",
                "attributionRatio", "0.5", "currency", "USD");
        List<RowRejection> out = gate.checkRows(FactType.STAGE, List.of(a, b), Map.of());
        assertTrue(out.stream().noneMatch(x -> x.ruleId().equals("DQ-F05")),
                "F05 归 Engine batch-level，gate 不得产出 F05；实际: " + ruleIdsOf(out));
    }

    // ── F06 CONSISTENCY (Σ ratio ≤ 1) ───────────────────────────

    @Test
    @DisplayName("F06 - 正：同 (P,C) 归 sum ratio = 0.5 + 0.3 = 0.8 ≤ 1")
    void f06_positive() {
        Map<String, Object> a = row("projectId", "P1", "contractId", "C1", "attributionType", "PRIMARY",
                "attributionRatio", new BigDecimal("0.5"));
        Map<String, Object> b = row("projectId", "P1", "contractId", "C1", "attributionType", "SPLIT",
                "attributionRatio", new BigDecimal("0.3"));
        List<RowRejection> out = gate.checkRows(FactType.ATTRIBUTION, List.of(a, b), Map.of());
        assertTrue(out.stream().noneMatch(x -> x.ruleId().equals(DqRowGateImpl.R_F06)));
    }

    @Test
    @DisplayName("F06 - 反：同 (P,C) 归 sum ratio = 0.6 + 0.6 = 1.2 > 1")
    void f06_negative() {
        Map<String, Object> a = row("projectId", "P1", "contractId", "C1", "attributionType", "PRIMARY",
                "attributionRatio", new BigDecimal("0.6"));
        Map<String, Object> b = row("projectId", "P1", "contractId", "C1", "attributionType", "SPLIT",
                "attributionRatio", new BigDecimal("0.6"));
        List<RowRejection> out = gate.checkRows(FactType.ATTRIBUTION, List.of(a, b), Map.of());
        long hits = out.stream().filter(x -> x.ruleId().equals(DqRowGateImpl.R_F06)).count();
        assertTrue(hits >= 1, "缺 F06，实际: " + ruleIdsOf(out));
    }

    // ── F07 COMPLETENESS (required field) ───────────────────────

    @Test
    @DisplayName("F07 - 正：STAGE 必填列全齐")
    void f07_positive() {
        Map<String, Object> r = row("projectId", "P1", "departmentId", "D1", "period", "2026-09",
                "stage", "ACCEPTANCE", "factType", "PLAN", "contractBase", "100",
                "attributionRatio", "0.5", "currency", "CNY");
        List<RowRejection> out = gate.checkRows(FactType.STAGE, List.of(r), Map.of());
        assertTrue(out.stream().noneMatch(x -> x.ruleId().equals(DqRowGateImpl.R_F07)));
    }

    @Test
    @DisplayName("F07 - 反：STAGE 缺 currency")
    void f07_negative() {
        Map<String, Object> bad = row("projectId", "P1", "departmentId", "D1", "period", "2026-09",
                "stage", "ACCEPTANCE", "factType", "PLAN", "contractBase", "100",
                "attributionRatio", "0.5");
        List<RowRejection> out = gate.checkRows(FactType.STAGE, List.of(bad), Map.of());
        RowRejection hit = out.stream().filter(x -> x.ruleId().equals(DqRowGateImpl.R_F07)).findFirst().orElseThrow(
                () -> new AssertionError("缺 F07，实际: " + ruleIdsOf(out)));
        assertEquals("currency", hit.field());
    }

    // ── F08 COMPLETENESS (ALLOCATED cost must have allocationRuleRef) ──

    @Test
    @DisplayName("F08 - 正：COST 行 directOrAllocated=DIRECT（不要求 allocationRuleRef）")
    void f08_positive() {
        Map<String, Object> r = row("departmentId", "D1", "period", "2026-09",
                "costCategory", "SALARY", "directOrAllocated", "DIRECT",
                "amount", "100", "currency", "CNY");
        List<RowRejection> out = gate.checkRows(FactType.COST, List.of(r), Map.of());
        assertTrue(out.stream().noneMatch(x -> x.ruleId().equals(DqRowGateImpl.R_F08)));
    }

    @Test
    @DisplayName("F08 - 反：COST 行 ALLOCATED 无 allocationRuleRef")
    void f08_negative() {
        Map<String, Object> bad = row("departmentId", "D1", "period", "2026-09",
                "costCategory", "SALARY", "directOrAllocated", "ALLOCATED",
                "amount", "100", "currency", "CNY");
        List<RowRejection> out = gate.checkRows(FactType.COST, List.of(bad), Map.of());
        RowRejection hit = out.stream().filter(x -> x.ruleId().equals(DqRowGateImpl.R_F08)).findFirst().orElseThrow(
                () -> new AssertionError("缺 F08，实际: " + ruleIdsOf(out)));
        assertEquals("allocationRuleRef", hit.field());
    }

    // ── F09 COMPLETENESS (cost/actual must carry evidence or source ref) ──

    @Test
    @DisplayName("F09 - 正：COST 行带 evidenceRef")
    void f09_positive() {
        Map<String, Object> r = row("departmentId", "D1", "period", "2026-09",
                "costCategory", "SALARY", "directOrAllocated", "DIRECT",
                "amount", "100", "currency", "CNY", "evidenceRef", "EV-001");
        List<RowRejection> out = gate.checkRows(FactType.COST, List.of(r), Map.of());
        assertTrue(out.stream().noneMatch(x -> x.ruleId().equals(DqRowGateImpl.R_F09)));
    }

    @Test
    @DisplayName("F09 - 反：COST 行无 evidence/source ref")
    void f09_negative() {
        Map<String, Object> bad = row("departmentId", "D1", "period", "2026-09",
                "costCategory", "SALARY", "directOrAllocated", "DIRECT",
                "amount", "100", "currency", "CNY");
        List<RowRejection> out = gate.checkRows(FactType.COST, List.of(bad), Map.of());
        assertTrue(out.stream().anyMatch(x -> x.ruleId().equals(DqRowGateImpl.R_F09)
                && x.field().equals("evidenceRef")), "缺 F09，实际: " + ruleIdsOf(out));
    }

    // ── F10 ACCURACY (master compare — WARN only, not rejected) ──

    @Test
    @DisplayName("F10 - 正：contractBase 与主数据一致（无 gate 拒收）")
    void f10_positive() {
        Map<String, Object> r = row("contractId", "C1", "contractBase", new BigDecimal("100"),
                "projectId", "P1", "attributionRatio", new BigDecimal("0.5"));
        Map<String, Object> ctx = Map.of(
                "contractMasterBase", Map.of("C1", new BigDecimal("100")));
        List<RowRejection> out = gate.checkRows(FactType.ATTRIBUTION, List.of(r), ctx);
        assertTrue(out.stream().noneMatch(x -> x.ruleId().equals(DqRowGateImpl.R_F10)),
                "F10 是 WARN only，不应出现在 rejected；实际: " + ruleIdsOf(out));
    }

    @Test
    @DisplayName("F10 - 反（语义）：contractBase 与主数据不一致 → gate <b>不</b>拒收（进待处理队列通道）")
    void f10_negative_warnOnly() {
        // 主数据 = 100，行内 = 200，主数据比对不一致 → 只 WARN，仍不 reject（PRD-02 §2.1 处置列）
        Map<String, Object> r = row("contractId", "C1", "contractBase", new BigDecimal("200"),
                "projectId", "P1", "attributionRatio", new BigDecimal("0.5"));
        Map<String, Object> ctx = Map.of(
                "contractMasterBase", Map.of("C1", new BigDecimal("100")));
        List<RowRejection> out = gate.checkRows(FactType.ATTRIBUTION, List.of(r), ctx);
        assertTrue(out.stream().noneMatch(x -> x.ruleId().equals(DqRowGateImpl.R_F10)),
                "F10 应为 WARN（不落 rejected）；实际: " + ruleIdsOf(out));
    }

    // ── F11 FRESHNESS (ACTUAL period ≤ current month) ────────────

    @Test
    @DisplayName("F11 - 正：ACTUAL 期间 = 当前月（≤ 当前月）")
    void f11_positive() {
        String nowMonth = YearMonth.now().toString();
        Map<String, Object> r = row("projectId", "P1", "departmentId", "D1", "period", nowMonth,
                "stage", "ACCEPTANCE", "factType", "ACTUAL", "contractBase", "100",
                "attributionRatio", "0.5", "currency", "CNY", "evidenceRef", "EV-1");
        List<RowRejection> out = gate.checkRows(FactType.STAGE, List.of(r), Map.of());
        assertTrue(out.stream().noneMatch(x -> x.ruleId().equals(DqRowGateImpl.R_F11)));
    }

    @Test
    @DisplayName("F11 - 反：ACTUAL 期间 = 当前月 + 1（未来实际值）")
    void f11_negative() {
        String future = YearMonth.now().plusMonths(1).toString();
        Map<String, Object> bad = row("projectId", "P1", "departmentId", "D1", "period", future,
                "stage", "ACCEPTANCE", "factType", "ACTUAL", "contractBase", "100",
                "attributionRatio", "0.5", "currency", "CNY", "evidenceRef", "EV-1");
        List<RowRejection> out = gate.checkRows(FactType.STAGE, List.of(bad), Map.of());
        RowRejection hit = out.stream().filter(x -> x.ruleId().equals(DqRowGateImpl.R_F11)).findFirst().orElseThrow(
                () -> new AssertionError("缺 F11，实际: " + ruleIdsOf(out)));
        assertEquals("period", hit.field());
    }

    // ── 汇总 / 恶意输入不崩溃 ─────────────────────────────────

    @Test
    @DisplayName("护栏：null / 空 rows 不打崩（业务上 no-op 应该上游 400 兜底）")
    void nullSafeguards() {
        List<RowRejection> out1 = gate.checkRows(null, new ArrayList<>(), Map.of());
        assertTrue(out1.isEmpty());
        List<RowRejection> out2 = gate.checkRows(FactType.STAGE, null, Map.of());
        assertTrue(out2.isEmpty());
        List<RowRejection> out3 = gate.checkRows(FactType.STAGE, List.of(), null);
        assertTrue(out3.isEmpty());
    }
}
