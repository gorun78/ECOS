package com.chinacreator.gzcm.engine.ontology.service.PublishGate;

import com.chinacreator.gzcm.engine.ontology.dto.GateResult;
import com.chinacreator.gzcm.engine.ontology.service.CaliberService;
import com.chinacreator.gzcm.engine.ontology.service.DatNetLineageGuard;
import com.chinacreator.gzcm.engine.ontology.service.PublishGateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * F03-02 / F03-03 V2 单位门禁（W75/M0）。
 *
 * <p>以文档 10 项指标单位为口径断言 {@link com.chinacreator.gzcm.engine.ontology.service.MetricUnitDerivationGuard}：</p>
 * <ul>
 *   <li>{@code M_FC_PROFIT}（AMOUNT，算术均为 AMOUNT 叶子）→ PASS；</li>
 *   <li>构造表达式 {@code M_REALIZATION_RATE - M_COST_SALARY}（RATE - AMOUNT）→ V2_UNIT / ECOS-ONTO-021；</li>
 *   <li>结果为 RATE 的指标声明 {@code additive=1} → V2_UNIT / ECOS-ONTO-022。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PublishGateV2Test {

    @Mock
    private CaliberService caliberService;

    private PublishGateService service() {
        return new PublishGateService(caliberService,
                (DatNetLineageGuard.LineageColumnFetcher) tableRef -> List.of());
    }

    private void stubCaliberOk(String code) {
        doNothing().when(caliberService).assertMetricCaliberApprovedForPublish(code);
    }

    @Test
    void amountArithmeticOnAmountLeavesPasses() {
        // M_FC_PROFIT 是单叶子 AMOUNT 指标（纯叶子 → 推导为 AMOUNT，合法）
        stubCaliberOk("M_FC_PROFIT");
        GateResult r = service().validate(row(metric(
                "M_FC_PROFIT", "M_FC_PROFIT", null, null // 无 additive 声明
        )));
        assertFalse(hasViolation(r, "V2_UNIT", "ECOS-ONTO-021"), "AMOUNT 指标不应产生单位冲突： " + r.getViolations());
        assertFalse(hasViolation(r, "V2_UNIT", "ECOS-ONTO-022"), "AMOUNT 指标不应产生可加性冲突： " + r.getViolations());
    }

    @Test
    void amountArithmeticExpressionPasses() {
        // ACCEPTANCE - SALARY = AMOUNT - AMOUNT = AMOUNT（合法）
        stubCaliberOk("M_COMPOSED");
        GateResult r = service().validate(row(metric(
                "M_COMPOSED", "M_ACCEPTANCE_AMT - M_COST_SALARY", null, null
        )));
        assertFalse(hasViolation(r, "V2_UNIT", "ECOS-ONTO-021"),
                "AMOUNT - AMOUNT = AMOUNT 应合法： " + r.getViolations());
    }

    @Test
    void rateMinusAmountIsUnitConflict() {
        // 单位冲突：RATE - AMOUNT → ECOS-ONTO-021
        stubCaliberOk("M_CONTRIVED");
        GateResult r = service().validate(row(metric(
                "M_CONTRIVED", "M_REALIZATION_RATE - M_COST_SALARY", null, null
        )));
        assertTrue(hasViolation(r, "V2_UNIT", "ECOS-ONTO-021"),
                "RATE - AMOUNT 应产生单位冲突 ECOS-ONTO-021，实际 " + r.getViolations());
    }

    @Test
    void nonAdditiveResultDeclaredAdditiveIsConflict() {
        // 结果为 RATE 的指标声明 additive=1 → ECOS-ONTO-022
        stubCaliberOk("M_RATE_DECL_ADD");
        GateResult r = service().validate(row(metric(
                "M_RATE_DECL_ADD", "M_FC_REVENUE / M_ACCEPTANCE_AMT", "1", null // additive=1，结果 RATE
        )));
        assertTrue(hasViolation(r, "V2_UNIT", "ECOS-ONTO-022"),
                "RATE 结果声明 additive=1 应产生可加性冲突 ECOS-ONTO-022，实际 " + r.getViolations());
    }

    @Test
    void monthMinusAmountIsUnitConflict() {
        // M_COLLECTION_CYCLE(MONTH) - M_COST_SALARY(AMOUNT) → 单位冲突 ECOS-ONTO-021
        stubCaliberOk("M_MONTH_CONTRIVED");
        GateResult r = service().validate(row(metric(
                "M_MONTH_CONTRIVED", "M_COLLECTION_CYCLE - M_COST_SALARY", null, null
        )));
        assertTrue(hasViolation(r, "V2_UNIT", "ECOS-ONTO-021"),
                "MONTH - AMOUNT 应产生单位冲突 ECOS-ONTO-021，实际 " + r.getViolations());
    }

    // ── 辅助 ─────────────────────────────────────────────────────

    private static Map<String, Object> metric(String code, String expression, Object additive, Object extra) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("code", code);
        m.put("caliber_id", "cb-" + code);
        if (expression != null) m.put("expression", expression);
        if (additive != null) m.put("additive", additive);
        return m;
    }

    private static Map<String, Object> row(Map<String, Object> metric) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("metrics", List.of(metric));
        payload.put("changeType", "CREATE_METRIC");
        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", "proposal-v2");
        row.put("payload", payload);
        return row;
    }

    private static boolean hasViolation(GateResult r, String gate, String code) {
        return r.getViolations().stream()
                .anyMatch(v -> gate.equals(v.getGate()) && code.equals(v.getCode()));
    }
}
