package com.chinacreator.gzcm.engine.ontology.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.controller.CaliberController;
import com.chinacreator.gzcm.engine.ontology.dao.CaliberDao;
import com.chinacreator.gzcm.engine.ontology.dto.CaliberFormulaValidationVO;
import com.chinacreator.gzcm.engine.ontology.dto.CaliberVersionSaveDTO;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 09 册 H-2 收口 — 口径公式四关校验的<b>写入咽喉 + 审批咽喉</b>。
 *
 * <p>此前四关只挂在 {@code POST /calibers/validate} 独立端点上，升版 / 审批两条真实写路径可原样绕过，
 * 等于校验形同虚设；本类钉死「拒绝即零副作用」。夹具经<b>真实</b> {@link CaliberFormulaDslService}
 * （mock {@link CaliberDao} 供给指标注册表），判定源与预检端点同源，禁另造一套断言口径。</p>
 *
 * <p>覆盖：</p>
 * <ol>
 *   <li>V1 语法（悬空 {@code '='}、悬空二元算符、越白名单函数 {@code POW}）→ {@code ECOS-ONTO-070} 不落库；</li>
 *   <li>V2 引用不闭合 → {@code 071}；V3 量纲不自洽（金额+比率直加）→ {@code 072}；
 *       V4 自引用 / 经注册表展开的互引用成环 → {@code 073}；</li>
 *   <li>合法公式 → 放行且 DAO 写恰一次；</li>
 *   <li>空 formula（无公式口径既有形态）→ 跳过校验保持既有语义；</li>
 *   <li>审批咽喉：库内坏公式 → 拒绝且不写状态；</li>
 *   <li>端点面：两处咽喉的拒绝映射为 400 + ECOS-ONTO-07x（不得被 catch-all 兜成 500）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CaliberFormulaPersistenceGateTest {

    private static final String CID = "cal-uuid-1";
    private static final String V = "1";

    /** PRD-03 登记的指标码（expression 为 null：本夹具只判提交公式自身，互引用环另用带表达式的夹具）。 */
    private static final List<String> PRD03_METRICS = List.of(
            "M_ACCEPTANCE_AMT", "M_COLLECTION_AMT", "M_SUBCONTRACT_AMT", "M_FC_REVENUE",
            "M_FC_PROFIT", "M_COST_SALARY", "M_COST_MGMT", "M_COST_MKT",
            "M_REALIZATION_RATE", "M_COLLECTION_CYCLE");

    @Mock
    private CaliberDao caliberDao;

    private CaliberService service;

    @BeforeEach
    void setUp() {
        stubRegistry(PRD03_METRICS.stream().map(c -> metric(c, null)).toList());
        service = new CaliberService(caliberDao, new CaliberFormulaDslService(caliberDao));
        when(caliberDao.findVersionById(anyString())).thenReturn(versionRow("DRAFT"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ────────────────────────────────────────────────────────────
    // 写入咽喉（insertVersion ⇔ POST /api/v1/ontology/calibers/{code}/versions）
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("写入咽喉 V1 语法关：悬空赋值 / 悬空算符 / 越白名单函数 → 070 且 DAO 写零调用")
    void insertVersion_rejectsSyntaxViolations_withNoDaoWrite() {
        for (String bad : List.of(
                "revenue = ",                                // '=' 之后无 expr
                "a = b +",                                   // 二元算符悬空
                "M_FC_REVENUE = POW(M_ACCEPTANCE_AMT, 2)")) { // POW 越 SUM/ROUND/MIN/MAX/IF 白名单
            CaliberService.CaliberFormulaRejectedException ex = assertThrows(
                    CaliberService.CaliberFormulaRejectedException.class,
                    () -> service.insertVersion(CID, V, bad, 1, "MONTH", null),
                    "非法语法应被写入咽喉拒绝: " + bad);
            assertEquals("ECOS-ONTO-070", ex.getCode(), "V1 语法违例应携 ECOS-ONTO-070：" + bad);
            assertEquals("DSL_V1_SYNTAX", ex.getViolations().get(0).getGate());
        }
        // 拒绝即零副作用：三条坏公式一条都没落库
        verify(caliberDao, never()).insertVersion(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("写入咽喉 V2 引用关：未注册且非事实字段的符号 → 071 且不落库")
    void insertVersion_rejectsUnresolvedReference() {
        CaliberService.CaliberFormulaRejectedException ex = assertThrows(
                CaliberService.CaliberFormulaRejectedException.class,
                () -> service.insertVersion(CID, V, "M_FC_REVENUE = mystery_metric", 1, "MONTH", null));
        assertEquals("ECOS-ONTO-071", ex.getCode());
        assertTrue(ex.getMessage().contains("mystery_metric"), "消息应含违规符号: " + ex.getMessage());
        verify(caliberDao, never()).insertVersion(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("写入咽喉 V3 量纲关：金额 + 比率直加 → 072 且不落库")
    void insertVersion_rejectsDimensionConflict() {
        CaliberService.CaliberFormulaRejectedException ex = assertThrows(
                CaliberService.CaliberFormulaRejectedException.class,
                () -> service.insertVersion(CID, V,
                        "TOTAL_X = M_ACCEPTANCE_AMT + M_REALIZATION_RATE", 1, "MONTH", null));
        assertEquals("ECOS-ONTO-072", ex.getCode());
        assertTrue(ex.getMessage().contains("AMOUNT") && ex.getMessage().contains("RATE"),
                "消息应含两侧量纲: " + ex.getMessage());
        verify(caliberDao, never()).insertVersion(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("写入咽喉 V4 无环关：自引用与经注册表展开的互引用都判环 → 073 且不落库")
    void insertVersion_rejectsCyclicDependency() {
        CaliberService.CaliberFormulaRejectedException selfRef = assertThrows(
                CaliberService.CaliberFormulaRejectedException.class,
                () -> service.insertVersion(CID, V, "M_FC_PROFIT = M_FC_PROFIT + 1", 1, "MONTH", null));
        assertEquals("ECOS-ONTO-073", selfRef.getCode());
        assertTrue(selfRef.getMessage().contains("M_FC_PROFIT -> M_FC_PROFIT"),
                "消息应打印环路径: " + selfRef.getMessage());

        // 互引用：提交 M_FC_REVENUE = M_FC_PROFIT + 1，注册表里 M_FC_PROFIT 又回指 M_FC_REVENUE
        stubRegistry(List.of(metric("M_FC_REVENUE", null),
                metric("M_FC_PROFIT", "M_FC_PROFIT = M_FC_REVENUE + 1")));
        CaliberService.CaliberFormulaRejectedException mutual = assertThrows(
                CaliberService.CaliberFormulaRejectedException.class,
                () -> service.insertVersion(CID, V, "M_FC_REVENUE = M_FC_PROFIT + 1", 1, "MONTH", null));
        assertEquals("ECOS-ONTO-073", mutual.getCode());
        assertTrue(mutual.getMessage().contains("M_FC_REVENUE -> M_FC_PROFIT -> M_FC_REVENUE"),
                "消息应打印互引用环路径: " + mutual.getMessage());

        verify(caliberDao, never()).insertVersion(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("写入咽喉正向：PRD-09 §1.2 合法公式放行，DAO insertVersion 恰一次")
    void insertVersion_passesValidFormula() {
        Map<String, Object> inserted = service.insertVersion(CID, V,
                "stage_amount = contract_base * attribution_ratio * realization_rate", 1, "MONTH", null);

        assertEquals("DRAFT", inserted.get("status"));
        verify(caliberDao, times(1)).insertVersion(anyString(), eq(CID), eq(V),
                eq("stage_amount = contract_base * attribution_ratio * realization_rate"),
                eq(1), eq("MONTH"), eq("DRAFT"));
    }

    @Test
    @DisplayName("写入咽喉空 formula 裁定：null / 全空白 = 无公式口径既有形态 → 跳过校验照常落库")
    void insertVersion_blankFormula_keepsLegacySemantics() {
        service.insertVersion(CID, V, null, 1, "MONTH", null);
        service.insertVersion(CID, "2", "   ", 1, "MONTH", null);

        verify(caliberDao, times(1)).insertVersion(anyString(), eq(CID), eq(V),
                eq((String) null), eq(1), eq("MONTH"), eq("DRAFT"));
        verify(caliberDao, times(1)).insertVersion(anyString(), eq(CID), eq("2"),
                eq("   "), eq(1), eq("MONTH"), eq("DRAFT"));
    }

    // ────────────────────────────────────────────────────────────
    // 审批咽喉（archiveVersion→APPROVED ⇔ POST /{code}/versions/{versionNo}/approve）
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("审批咽喉：库内已有坏公式（四关生效前写入）→ 拒绝审批，状态 UPDATE 零调用")
    void approve_existingBadFormulaInDb_isRejectedWithNoStatusWrite() {
        when(caliberDao.findVersionStatus(CID, V)).thenReturn("DRAFT");
        when(caliberDao.lookupVersionFormula(CID, V)).thenReturn(
                "TOTAL_X = M_ACCEPTANCE_AMT + M_REALIZATION_RATE");

        CaliberService.CaliberFormulaRejectedException ex = assertThrows(
                CaliberService.CaliberFormulaRejectedException.class,
                () -> service.archiveVersion(CID, V, "APPROVED", "admin"));
        assertEquals("ECOS-ONTO-072", ex.getCode());

        verify(caliberDao, never()).archiveVersion(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("审批咽喉正向：库内公式过四关 → 置 APPROVED 一次")
    void approve_validFormula_promotesVersion() {
        when(caliberDao.findVersionStatus(CID, V)).thenReturn("REVIEWING");
        when(caliberDao.lookupVersionFormula(CID, V)).thenReturn(
                "M_FC_PROFIT = M_FC_REVENUE - (M_COST_SALARY + M_COST_MGMT + M_COST_MKT)");
        when(caliberDao.archiveVersion(CID, V, "APPROVED", "admin", "REVIEWING")).thenReturn(1);

        assertEquals(1, service.archiveVersion(CID, V, "APPROVED", "admin"));
        verify(caliberDao, times(1)).archiveVersion(CID, V, "APPROVED", "admin", "REVIEWING");
    }

    @Test
    @DisplayName("审批咽喉边界：库内空 formula 不拦审批；非 APPROVED 目标（→REVIEWING）不触发校验、不读公式")
    void approve_blankFormulaAndNonApproveTarget_areNotGated() {
        when(caliberDao.findVersionStatus(CID, V)).thenReturn("DRAFT");
        when(caliberDao.lookupVersionFormula(CID, V)).thenReturn(null);
        when(caliberDao.archiveVersion(CID, V, "APPROVED", "admin", "DRAFT")).thenReturn(1);
        assertEquals(1, service.archiveVersion(CID, V, "APPROVED", "admin"));

        when(caliberDao.findVersionStatus(CID, "2")).thenReturn("DRAFT");
        when(caliberDao.archiveVersion(CID, "2", "REVIEWING", "admin", "DRAFT")).thenReturn(1);
        assertEquals(1, service.archiveVersion(CID, "2", "REVIEWING", "admin"));

        verify(caliberDao, never()).lookupVersionFormula(CID, "2");
    }

    // ────────────────────────────────────────────────────────────
    // 端点面：拒绝语义映射为 400（不得被 catch-all 兜成 500 ECOS-ONTO-042）
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("端点面：升版四关不过 → 400 + ECOS-ONTO-070 + 逐条违反项；空 formula → code=0")
    void addVersionEndpoint_mapsRejectionTo400() {
        CaliberService svc = Mockito.mock(CaliberService.class);
        CaliberController controller = new CaliberController(
                svc, Mockito.mock(CaliberFormulaDslService.class));
        when(svc.findByCode("CAL_X")).thenReturn(Optional.of(caliberRow(CID)));
        when(svc.insertVersion(eq(CID), eq("1"), any(), any(), any(), any()))
                .thenThrow(rejection("insertVersion", "DSL_V1_SYNTAX", "ECOS-ONTO-070",
                        "DSL_V1 syntax: expected factor after '=' — unexpected '<eof>'"
                                + " (line 1, column 11, offset 10)"));

        ApiResponse<Map<String, Object>> rejected = controller.addVersion("CAL_X", versionDto("revenue = "));
        assertEquals(400, rejected.getCode(), "四关不过应 400，不得被 catch-all 兜成 500");
        assertEquals("ECOS-ONTO-070", rejected.getErrorCode());
        assertTrue(rejected.getMessage().contains("DSL_V1_SYNTAX"),
                "400 消息应含逐条违反项: " + rejected.getMessage());

        when(svc.insertVersion(eq(CID), eq("1"), any(), any(), any(), any()))
                .thenReturn(versionRow("DRAFT"));
        assertEquals(0, controller.addVersion("CAL_X", versionDto(null)).getCode(),
                "空 formula 保持既有语义（放行）");
    }

    @Test
    @DisplayName("端点面：审批四关不过 → 400 + ECOS-ONTO-073（含环路径），且不置 APPROVED")
    void approveEndpoint_mapsRejectionTo400() {
        UserContext ctx = new UserContext();
        ctx.setUsername("admin");
        UserContext.setCurrent(ctx);

        CaliberService svc = Mockito.mock(CaliberService.class);
        CaliberController controller = new CaliberController(
                svc, Mockito.mock(CaliberFormulaDslService.class));
        when(svc.findByCode("CAL_X")).thenReturn(Optional.of(caliberRow(CID)));
        when(svc.archiveVersion(CID, V, "APPROVED", "admin")).thenThrow(
                rejection("approve", "DSL_V4_CYCLE", "ECOS-ONTO-073",
                        "DSL_V4 dependency graph is cyclic (topological order impossible):"
                                + " M_FC_PROFIT -> M_FC_PROFIT"));

        ApiResponse<Map<String, Object>> rejected = controller.approve("CAL_X", V);
        assertEquals(400, rejected.getCode());
        assertEquals("ECOS-ONTO-073", rejected.getErrorCode());
        assertTrue(rejected.getMessage().contains("cyclic"), "400 消息应含环路径: " + rejected.getMessage());
    }

    // ────────────────────────────────────────────────────────────
    // 夹具
    // ────────────────────────────────────────────────────────────

    private void stubRegistry(List<Map<String, Object>> rows) {
        when(caliberDao.listMetricDefinitionExpressions()).thenReturn(rows);
    }

    private static Map<String, Object> metric(String code, String expression) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("expression", expression); // 允许 null（指标存在但未带公式）
        return row;
    }

    private static Map<String, Object> caliberRow(String id) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("code", "CAL_X");
        row.put("status", "ACTIVE");
        return row;
    }

    private static Map<String, Object> versionRow(String status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("caliber_id", CID);
        row.put("version_no", V);
        row.put("status", status);
        return row;
    }

    private static CaliberVersionSaveDTO versionDto(String formula) {
        CaliberVersionSaveDTO dto = new CaliberVersionSaveDTO();
        dto.setVersionNo("1");
        dto.setFormula(formula);
        dto.setAdditive(1);
        dto.setPeriodGranularity("MONTH");
        return dto;
    }

    private static CaliberService.CaliberFormulaRejectedException rejection(
            String stage, String gate, String code, String message) {
        return new CaliberService.CaliberFormulaRejectedException(stage, "formula",
                List.of(new CaliberFormulaValidationVO.Violation(gate, code, message, "x", 1, 1, 0)));
    }
}
