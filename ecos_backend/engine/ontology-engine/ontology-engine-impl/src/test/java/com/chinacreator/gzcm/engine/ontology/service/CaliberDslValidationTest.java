package com.chinacreator.gzcm.engine.ontology.service;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.controller.CaliberController;
import com.chinacreator.gzcm.engine.ontology.dto.CaliberFormulaValidateDTO;
import com.chinacreator.gzcm.engine.ontology.dto.CaliberFormulaValidationVO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * H-2（详设-09 v1.2 §十一 / PRD-09 v1.2 §1.2·§1.5）— 口径公式 DSL 四关校验单测。
 *
 * <p>覆盖（PRD-09 §1.5 判据 3「DSL 四类违规夹具全部 400 且定位到符号」）：</p>
 * <ul>
 *   <li>V1 语法：残缺括号 → {@code DSL_V1_SYNTAX / ECOS-ONTO-070}；</li>
 *   <li>V1 函数白名单：{@code AVG} 越界 → 拒绝并定位 {@code 'AVG'}；</li>
 *   <li>V2 引用闭合：未注册符号 → {@code DSL_V2_REFERENCE / ECOS-ONTO-071} 含符号与位置；</li>
 *   <li>V3 量纲：金额+比率直加 → {@code DSL_V3_DIMENSION / ECOS-ONTO-072} 定位到算符；</li>
 *   <li>V4 成环：公式图环 → {@code DSL_V4_CYCLE / ECOS-ONTO-073} 打印环路径；</li>
 *   <li>正向：PRD-09 §1.2 三条示例公式（环节额/收入/利润）全部通过；</li>
 *   <li>端点面：{@code POST /api/v1/ontology/calibers/validate} 违反 → 400、通过 → 0。</li>
 * </ul>
 *
 * <p>指标注册表面经 {@link CaliberDslValidator.MetricSymbols} 内存装配
 * （生产态由 {@code CaliberFormulaDslService} 从 ecos_metric_definition 加载，R-4 唯一语义入口）。</p>
 */
class CaliberDslValidationTest {

    private final CaliberDslValidator validator = new CaliberDslValidator();

    private static final Map<String, String> PRD03_REGISTRY = buildPrd03Registry();

    /**
     * PRD-03 登记的 10 个指标码（expression 为 null：本夹具只测提交公式自身，
     * 注册表关联公式面经 DTO.relatedFormulas 显式提供）。
     * Map.of/Map.copyOf 不接受 null 值，故用 HashMap。
     */
    private static Map<String, String> buildPrd03Registry() {
        Map<String, String> m = new java.util.HashMap<>();
        for (String code : List.of("M_ACCEPTANCE_AMT", "M_COLLECTION_AMT", "M_SUBCONTRACT_AMT",
                "M_FC_REVENUE", "M_FC_PROFIT", "M_COST_SALARY", "M_COST_MGMT", "M_COST_MKT",
                "M_REALIZATION_RATE", "M_COLLECTION_CYCLE")) {
            m.put(code, null);
        }
        return m;
    }

    private final CaliberDslValidator.MetricSymbols registry =
            new CaliberDslValidator.MetricSymbols(PRD03_REGISTRY);

    private CaliberFormulaValidateDTO dto(String formula) {
        CaliberFormulaValidateDTO d = new CaliberFormulaValidateDTO();
        d.setFormula(formula);
        return d;
    }

    private boolean hasViolation(CaliberFormulaValidationVO vo, String gate, String code) {
        return vo.getViolations().stream()
                .anyMatch(v -> gate.equals(v.getGate()) && code.equals(v.getCode()));
    }

    private CaliberFormulaValidationVO.Violation firstOf(CaliberFormulaValidationVO vo, String gate) {
        return vo.getViolations().stream()
                .filter(v -> gate.equals(v.getGate()))
                .findFirst().orElseThrow(() -> new AssertionError("no violation of gate " + gate));
    }

    // ────────────────────────────────────────────────────────────
    // 点名用例：V2 未知符号 + V3 量纲冲突（均定位到符号）
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("H-2：未注册符号与金额+比率直加分别命中 V2/V3，400 消息定位到符号位置")
    void rejectsUnknownSymbolAndDimensionMismatch() {
        // V2：mystery_metric 既不在指标注册表（ecos_metric_definition 语义面）也不是业务事实字段
        CaliberFormulaValidationVO unknown = validator.validate(
                dto("M_FC_PROFIT = M_FC_REVENUE - mystery_metric"), registry);
        assertFalse(unknown.isValid(), "未知符号应被拒绝");
        assertTrue(hasViolation(unknown, "DSL_V2_REFERENCE", "ECOS-ONTO-071"),
                "应命中 DSL_V2_REFERENCE/ECOS-ONTO-071，实际 " + unknown.getViolations());
        CaliberFormulaValidationVO.Violation uv = firstOf(unknown, "DSL_V2_REFERENCE");
        assertTrue(uv.getMessage().contains("mystery_metric"), "消息应含违规符号名: " + uv.getMessage());
        assertTrue(uv.getMessage().contains("line 1") && uv.getMessage().contains("column"),
                "消息应含行列定位: " + uv.getMessage());
        assertNotNull(uv.getSymbol());
        assertNotNull(uv.getColumn());

        // V3：M_ACCEPTANCE_AMT(AMOUNT) + M_REALIZATION_RATE(RATE) = 金额+比率直加 → 拒
        CaliberFormulaValidationVO dim = validator.validate(
                dto("TOTAL_X = M_ACCEPTANCE_AMT + M_REALIZATION_RATE"), registry);
        assertFalse(dim.isValid(), "金额+比率直加应被拒绝");
        assertTrue(hasViolation(dim, "DSL_V3_DIMENSION", "ECOS-ONTO-072"),
                "应命中 DSL_V3_DIMENSION/ECOS-ONTO-072，实际 " + dim.getViolations());
        CaliberFormulaValidationVO.Violation dv = firstOf(dim, "DSL_V3_DIMENSION");
        assertTrue(dv.getMessage().contains("AMOUNT"), "消息应含左量纲: " + dv.getMessage());
        assertTrue(dv.getMessage().contains("RATE"), "消息应含右量纲: " + dv.getMessage());
        assertTrue(dv.getMessage().contains("column") && dv.getMessage().contains("line 1"),
                "量纲冲突应定位到算符位置: " + dv.getMessage());
        assertEquals("+", dv.getSymbol(), "违规符号应为算符 '+'");
    }

    // ────────────────────────────────────────────────────────────
    // V1 负向：语法残缺 + 白名单外函数
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("V1 语法关：括号不闭合 → ECOS-ONTO-070 且定位到符号")
    void rejectsSyntaxErrorWithSymbolPosition() {
        CaliberFormulaValidationVO vo = validator.validate(
                dto("M_FC_PROFIT = M_FC_REVENUE - (M_COST_SALARY + M_COST_MGMT"), registry);
        assertFalse(vo.isValid());
        assertTrue(hasViolation(vo, "DSL_V1_SYNTAX", "ECOS-ONTO-070"),
                "残缺括号应命中 DSL_V1_SYNTAX/ECOS-ONTO-070，实际 " + vo.getViolations());
        CaliberFormulaValidationVO.Violation v = firstOf(vo, "DSL_V1_SYNTAX");
        assertTrue(v.getMessage().contains("<eof>"), "应定位到实际出错 token: " + v.getMessage());
        assertTrue(v.getMessage().contains("line 1"), "应含行列定位: " + v.getMessage());
        assertEquals(1, v.getLine());
    }

    @Test
    @DisplayName("V1 函数白名单关：AVG 不在 SUM/ROUND/MIN/MAX/IF 白名单 → 拒绝并定位 'AVG'")
    void rejectsFunctionOutsideWhitelist() {
        CaliberFormulaValidationVO vo = validator.validate(
                dto("M_FC_REVENUE = AVG(M_ACCEPTANCE_AMT)"), registry);
        assertFalse(vo.isValid());
        assertTrue(hasViolation(vo, "DSL_V1_SYNTAX", "ECOS-ONTO-070"),
                "白名单外函数应作为 V1 语法拒绝，实际 " + vo.getViolations());
        CaliberFormulaValidationVO.Violation v = firstOf(vo, "DSL_V1_SYNTAX");
        assertTrue(v.getMessage().contains("AVG"), "消息应含 'AVG': " + v.getMessage());
        assertTrue(v.getMessage().contains("whitelist"), "消息应说明白名单约束: " + v.getMessage());
        assertTrue(v.getMessage().contains("column"), "消息应含定位: " + v.getMessage());
    }

    // ────────────────────────────────────────────────────────────
    // V4 负向：循环依赖打印环路径
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("V4 无环关：A→B→A 公式图成环 → ECOS-ONTO-073 并打印环路径")
    void rejectsCyclicDependencyAndPrintsCyclePath() {
        CaliberFormulaValidateDTO d = dto("M_FC_PROFIT = M_FC_REVENUE + 1");
        d.setRelatedFormulas(Map.of("M_FC_REVENUE", "M_FC_REVENUE = M_FC_PROFIT + 1"));
        CaliberFormulaValidationVO vo = validator.validate(d, registry);
        assertFalse(vo.isValid());
        assertTrue(hasViolation(vo, "DSL_V4_CYCLE", "ECOS-ONTO-073"),
                "成环应命中 DSL_V4_CYCLE/ECOS-ONTO-073，实际 " + vo.getViolations());
        CaliberFormulaValidationVO.Violation v = firstOf(vo, "DSL_V4_CYCLE");
        assertTrue(v.getMessage().contains("M_FC_PROFIT -> M_FC_REVENUE -> M_FC_PROFIT"),
                "消息应打印环路径，实际: " + v.getMessage());
    }

    @Test
    @DisplayName("V4 自引用：x = x + 1 也是环")
    void rejectsSelfReferenceAsCycle() {
        CaliberFormulaValidationVO vo = validator.validate(
                dto("M_FC_PROFIT = M_FC_PROFIT + 1"), registry);
        assertFalse(vo.isValid());
        assertTrue(hasViolation(vo, "DSL_V4_CYCLE", "ECOS-ONTO-073"),
                "自引用应判环，实际 " + vo.getViolations());
        assertTrue(firstOf(vo, "DSL_V4_CYCLE").getMessage().contains("M_FC_PROFIT -> M_FC_PROFIT"));
    }

    // ────────────────────────────────────────────────────────────
    // 正向：PRD-09 §1.2 三条示例公式（环节额 / 收入 / 利润）
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("正向：PRD-09 §1.2 三条示例公式全部通过四关")
    void acceptsPrd09SampleFormulas() {
        // 1 环节额 = 合同基数 × 归属比例 × 实现率（金额×比率×比率 = 金额）
        CaliberFormulaValidationVO stage = validator.validate(
                dto("stage_amount = contract_base * attribution_ratio * realization_rate"), registry);
        assertTrue(stage.isValid(), "环节额公式应通过四关，实际 " + stage.getViolations());
        assertEquals("stage_amount", stage.getTarget());

        // 2 收入：验收/回款 − 分包（SUM + 限定符形态）
        CaliberFormulaValidationVO revenue = validator.validate(
                dto("M_FC_REVENUE = SUM(M_ACCEPTANCE_AMT[ACCEPTANCE]) + M_COLLECTION_AMT"
                        + " - M_SUBCONTRACT_AMT[SUBCONTRACT]"), registry);
        assertTrue(revenue.isValid(), "收入公式应通过四关，实际 " + revenue.getViolations());

        // 3 利润 = 收入 − (薪酬 + 管理 + 市场)（PRD-03 §1.3 登记口径）
        CaliberFormulaValidationVO profit = validator.validate(
                dto("M_FC_PROFIT = M_FC_REVENUE - (M_COST_SALARY + M_COST_MGMT + M_COST_MKT)"), registry);
        assertTrue(profit.isValid(), "利润公式应通过四关，实际 " + profit.getViolations());
    }

    // ────────────────────────────────────────────────────────────
    // 端点面：POST /api/v1/ontology/calibers/validate 的 400/0 映射
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("端点面：validate 违反四关 → 400（错误码透传）；通过 → code=0")
    void validateEndpointMapsViolationTo400AndPassToSuccess() {
        CaliberFormulaDslService dslService = Mockito.mock(CaliberFormulaDslService.class);
        CaliberController controller = new CaliberController(
                Mockito.mock(CaliberService.class), dslService);

        CaliberFormulaValidationVO bad = new CaliberFormulaValidationVO();
        bad.setValid(false);
        bad.getViolations().add(new CaliberFormulaValidationVO.Violation(
                "DSL_V2_REFERENCE", "ECOS-ONTO-071",
                "DSL_V2 reference: symbol 'ghost' (line 1, column 22, offset 21) does not resolve",
                "ghost", 1, 22, 21));
        when(dslService.validate(any())).thenReturn(bad);
        ApiResponse<CaliberFormulaValidationVO> rejected = controller.validateFormula(dto("x = ghost"));
        assertEquals(400, rejected.getCode(), "违反四关应返回 400");
        assertEquals("ECOS-ONTO-071", rejected.getErrorCode());
        assertTrue(rejected.getMessage().contains("ghost"), "400 消息应含违规符号定位");

        CaliberFormulaValidationVO good = new CaliberFormulaValidationVO();
        good.setValid(true);
        when(dslService.validate(any())).thenReturn(good);
        ApiResponse<CaliberFormulaValidationVO> passed = controller.validateFormula(
                dto("stage_amount = contract_base * attribution_ratio * realization_rate"));
        assertEquals(0, passed.getCode());
        assertNotNull(passed.getData());
        assertTrue(passed.getData().isValid());
    }

    @Test
    @DisplayName("装配面：CaliberFormulaDslService 从指标注册表 DAO 快照构建符号源（R-4）")
    void serviceLoadsSymbolsFromMetricRegistrySnapshot() {
        com.chinacreator.gzcm.engine.ontology.dao.CaliberDao dao =
                Mockito.mock(com.chinacreator.gzcm.engine.ontology.dao.CaliberDao.class);
        when(dao.listMetricDefinitionExpressions()).thenReturn(List.of(
                Map.of("code", "M_FC_REVENUE", "expression", "M_FC_REVENUE = M_ACCEPTANCE_AMT")));
        CaliberFormulaDslService service = new CaliberFormulaDslService(dao);
        CaliberDslValidator.MetricSymbols symbols = service.loadMetricSymbols();
        assertTrue(symbols.knownMetric("M_FC_REVENUE"));
        assertEquals("M_FC_REVENUE = M_ACCEPTANCE_AMT", symbols.expressionOf("M_FC_REVENUE"));
        assertEquals(1, symbols.size());
    }
}
