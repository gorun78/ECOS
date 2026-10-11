package com.chinacreator.gzcm.engine.data.fact.masterdata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * C224（架构设计说明书 §十一冻结测试，详设-09 v1.2 §十一 H-6 交付⑤）—
 * 主数据完备性检查器纯逻辑面：mock 只读 mapper 行集，验证
 * <b>每笔明细都能解析出合同基数与归属行</b>；并逐项验证 MD-C1/C2/C3 与
 * blockedCount（DQ BLOCK 趋势面）、MD-EMPTY 空检验集显式上报。
 */
@ExtendWith(MockitoExtension.class)
class MasterDataCompletenessTest {

    @Mock private MasterDataCompletenessMapper mapper;

    private static Map<String, Object> detail(String id, String projectId, String period,
                                              Object contractBase) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", id);
        m.put("projectId", projectId);
        m.put("period", period);
        m.put("departmentId", "d-1");
        if (contractBase != null) {
            m.put("contractBase", contractBase);
        }
        m.put("dqStatus", "PASSED");
        return m;
    }

    private static Map<String, Object> attribution(String id, String projectId,
                                                   String effectiveFrom, String effectiveTo,
                                                   String ratio) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", id);
        m.put("projectId", projectId);
        m.put("contractId", "c-1");
        m.put("departmentId", "d-1");
        m.put("effectiveFrom", effectiveFrom);
        if (effectiveTo != null) {
            m.put("effectiveTo", effectiveTo);
        }
        m.put("attributionRatio", new BigDecimal(ratio));
        return m;
    }

    private MasterDataCompletenessChecker checker() {
        return new MasterDataCompletenessChecker(mapper);
    }

    @Test
    @DisplayName("C224：完备明细（合同基数+归属覆盖窗命中）→ pass=true，零发现")
    void everyDetailResolvesContractAndAttribution() {
        when(mapper.selectCompletenessDetails(isNull(), isNull(), anyInt()))
                .thenReturn(List.of(
                        detail("s-1", "p-1", "2025-03", new BigDecimal("100000.00")),
                        detail("s-2", "p-2", "2025-04", new BigDecimal("50000.00"))));
        when(mapper.countRejectedDetails(isNull(), isNull())).thenReturn(0);
        when(mapper.selectActiveAttributions(isNull(), anyInt()))
                .thenReturn(List.of(
                        attribution("a-1", "p-1", "2025-01", "2025-12", "0.6000"),
                        attribution("a-2", "p-1", "2025-02", null, "0.4000"),
                        attribution("a-3", "p-2", "2025-04", "2025-06", "1.0000")));

        MasterDataCompletenessChecker.Report report = checker().check(null, null);

        assertTrue(report.isPass(), "完备数据集应 pass，实际: " + report);
        assertEquals(2, report.getTotalDetails());
        assertEquals(0, report.getIssues().size());
    }

    @Test
    @DisplayName("MD-C1：contract_base 为空 → 逐笔上报，pass=false")
    void missingContractBaseReported() {
        when(mapper.selectCompletenessDetails(eq("p-1"), eq("2025-03"), anyInt()))
                .thenReturn(List.of(detail("s-1", "p-1", "2025-03", null)));
        when(mapper.countRejectedDetails(eq("p-1"), eq("2025-03"))).thenReturn(0);
        when(mapper.selectActiveAttributions(eq("p-1"), anyInt()))
                .thenReturn(List.of(attribution("a-1", "p-1", "2025-01", null, "1.0000")));

        MasterDataCompletenessChecker.Report report = checker().check("p-1", "2025-03");

        assertFalse(report.isPass());
        assertEquals(1, report.getIssues().size());
        assertEquals(MasterDataCompletenessChecker.RULE_CONTRACT_BASE_MISSING,
                report.getIssues().get(0).ruleCode);
        assertEquals("s-1", report.getIssues().get(0).detailId);
    }

    @Test
    @DisplayName("MD-C2：period 落在归属覆盖窗外 → 归属缺失上报（effective_to 开区间命中）")
    void attributionWindowMustCoverPeriod() {
        when(mapper.selectCompletenessDetails(isNull(), isNull(), anyInt()))
                .thenReturn(List.of(
                        detail("s-1", "p-1", "2026-01", BigDecimal.ONE),   // 窗外 → MD-C2
                        detail("s-2", "p-2", "2025-05", BigDecimal.ONE))); // 开区间 → 命中
        when(mapper.countRejectedDetails(isNull(), isNull())).thenReturn(0);
        when(mapper.selectActiveAttributions(isNull(), anyInt()))
                .thenReturn(List.of(
                        attribution("a-1", "p-1", "2025-01", "2025-12", "1.0000"),
                        attribution("a-2", "p-2", "2025-01", null, "0.5000")));

        MasterDataCompletenessChecker.Report report = checker().check(null, null);

        assertFalse(report.isPass());
        assertEquals(1, report.getIssues().size());
        assertEquals(MasterDataCompletenessChecker.RULE_ATTRIBUTION_MISSING,
                report.getIssues().get(0).ruleCode);
        assertEquals("s-1", report.getIssues().get(0).detailId);
    }

    @Test
    @DisplayName("MD-C3：同 (projectId, period) Σratio>1 → 项目级仅报一次")
    void attributionSumExceededReportedOncePerProject() {
        when(mapper.selectCompletenessDetails(isNull(), isNull(), anyInt()))
                .thenReturn(List.of(
                        detail("s-1", "p-1", "2025-03", BigDecimal.ONE),
                        detail("s-2", "p-1", "2025-03", BigDecimal.ONE))); // 同项目同期第二笔
        when(mapper.countRejectedDetails(isNull(), isNull())).thenReturn(0);
        when(mapper.selectActiveAttributions(isNull(), anyInt()))
                .thenReturn(List.of(
                        attribution("a-1", "p-1", "2025-01", null, "0.7000"),
                        attribution("a-2", "p-1", "2025-02", null, "0.5000")));

        MasterDataCompletenessChecker.Report report = checker().check(null, null);

        assertFalse(report.isPass());
        List<MasterDataCompletenessChecker.Issue> mdC3 = report.getIssues().stream()
                .filter(i -> MasterDataCompletenessChecker.RULE_ATTRIBUTION_SUM_EXCEEDED
                        .equals(i.ruleCode))
                .toList();
        assertEquals(1, mdC3.size(), "Σ>1 须按 (projectId, period) 只报一次，实际: " + report);
    }

    @Test
    @DisplayName("blockedCount：REJECTED 行数经独立计数进入 DQ BLOCK 趋势面，不入检验集")
    void rejectedRowsCountedAsBlockedOnly() {
        when(mapper.selectCompletenessDetails(isNull(), isNull(), anyInt()))
                .thenReturn(List.of(detail("s-1", "p-1", "2025-03", BigDecimal.ONE)));
        when(mapper.countRejectedDetails(isNull(), isNull())).thenReturn(7);
        when(mapper.selectActiveAttributions(isNull(), anyInt()))
                .thenReturn(List.of(attribution("a-1", "p-1", "2025-01", null, "1.0000")));

        MasterDataCompletenessChecker.Report report = checker().check(null, null);

        assertTrue(report.isPass());
        assertEquals(7, report.getBlockedCount());
    }

    @Test
    @DisplayName("MD-EMPTY：0 笔可检明细 → pass=false 显式上报（禁静默假成功，X-44/X-50）")
    void emptyCheckSetIsExplicitFailure() {
        when(mapper.selectCompletenessDetails(isNull(), isNull(), anyInt()))
                .thenReturn(List.of());
        when(mapper.countRejectedDetails(isNull(), isNull())).thenReturn(0);
        when(mapper.selectActiveAttributions(isNull(), anyInt())).thenReturn(List.of());

        MasterDataCompletenessChecker.Report report = checker().check(null, null);

        assertFalse(report.isPass(), "空检验集不得输出 pass=true 的假成功");
        assertEquals(MasterDataCompletenessChecker.RULE_EMPTY_CHECK_SET,
                report.getIssues().get(0).ruleCode);
    }

    @Test
    @DisplayName("入参空白串按 null 处理；limit 越界钳制到 [1, MAX_SCAN_LIMIT]")
    void paramsNormalized() {
        when(mapper.selectCompletenessDetails(isNull(), isNull(),
                eq(MasterDataCompletenessChecker.MAX_SCAN_LIMIT))).thenReturn(List.of());
        when(mapper.countRejectedDetails(isNull(), isNull())).thenReturn(0);
        when(mapper.selectActiveAttributions(isNull(),
                eq(MasterDataCompletenessChecker.MAX_SCAN_LIMIT))).thenReturn(List.of());

        // 空白串 → null；limit=999999 → 钳制 MAX_SCAN_LIMIT（stub 若不相符则 Mockito 严格桩报错）
        MasterDataCompletenessChecker.Report report = checker().check("  ", " ", 999999);

        assertFalse(report.isPass()); // MD-EMPTY（0 笔）
    }
}
