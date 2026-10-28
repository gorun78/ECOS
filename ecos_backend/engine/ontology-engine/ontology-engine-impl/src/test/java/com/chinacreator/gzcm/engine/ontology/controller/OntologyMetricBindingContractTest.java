package com.chinacreator.gzcm.engine.ontology.controller;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.ontology.dto.MetricBindingDTO;
import com.chinacreator.gzcm.engine.ontology.service.CaliberService;
import com.chinacreator.gzcm.sysman.security.MinimumClearance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #146u —— 差距台账附录A 第 35 条：{@code POST /api/v1/ontology/metrics/{code}/binding}
 * （{@code bindMetricCaliber}）契约路径。
 *
 * <h3>本批补的是什么</h3>
 * <p>原文记「偏差（挂 {@code /calibers} 下而非 {@code metrics} 下）」。契约行要求
 * 路径 {@code /api/v1/ontology/metrics/{code}/binding}、面级 {@code L2}、体
 * {@code caliberId+formulaVersion}、失败 {@code ECOS-ONTO-040}。本类钉住新建别名的
 * 注解面 + 委派面 + 三条失败腿映射 + 双体键回退，且旧面 {@code CaliberController#bindMetric}
 * 未被触碰（旧面用例另见 {@code service/MetricCaliberBindingTest}）。</p>
 *
 * <h3>能变红</h3>
 * <ul>
 *   <li>摘掉 {@code @MinimumClearance(2)}（落 {@code /api/v1/} 路径默认 L1，低于契约 L2）；</li>
 *   <li>把 {@code caliberId} 误传给 {@code findByCode}（第 2 案 {@code verify} 失败）；</li>
 *   <li>失败腿改回 {@code ApiResponse.error} 200 信封（三案均断言抛携带型异常）；</li>
 *   <li>摘掉版本存在性护栏（第 6 案会由 success 而非 400 收场）；</li>
 *   <li>把快照冻结折成 400（第 7 案断言 409）。</li>
 * </ul>
 */
class OntologyMetricBindingContractTest {

    private static final String METRIC = "M_ACCEPTANCE_AMT";
    private static final String CALIBER_ID = "cal-uuid-0001";
    private static final String CALIBER_CODE = "CAL_ACCEPTANCE";
    private static final String VERSION = "2";

    // ── 1. 注解面：路径 + 面级 L2 ─────────────────────────────

    @Test
    @Timeout(120)
    @DisplayName("类级 /api/v1/ontology/metrics + 方法级 /{code}/binding + @MinimumClearance(2)")
    void endpointCarriesContractPathAndClearance() throws Exception {
        RequestMapping classMapping = OntologyMetricBindingController.class.getAnnotation(RequestMapping.class);
        assertNotNull(classMapping, "缺类级 @RequestMapping");
        assertEquals(List.of("/api/v1/ontology/metrics"), List.of(classMapping.value()),
                "契约路径段必须落在 /api/v1/ontology/metrics（旧面 /calibers/metrics 保持不动）");

        Method method = OntologyMetricBindingController.class.getMethod("bindMetricCaliber",
                String.class, MetricBindingDTO.class);
        PostMapping post = method.getAnnotation(PostMapping.class);
        assertNotNull(post, "缺 @PostMapping");
        assertEquals(List.of("/{code}/binding"), List.of(post.value()));

        MinimumClearance clearance = method.getAnnotation(MinimumClearance.class);
        assertNotNull(clearance, "缺 @MinimumClearance ⇒ 落 /api/v1/ 默认 L1，低于契约 L2");
        assertEquals(2, clearance.level(), "契约面为 L2");

        PathVariable pathVariable = method.getParameters()[0].getAnnotation(PathVariable.class);
        assertNotNull(pathVariable, "指标编码须为路径变量");
        assertEquals("code", pathVariable.value(), "契约占位符是 {code}");
    }

    // ── 2. 契约体键 caliberId：直通委派 ──────────────────────

    @Test
    @Timeout(120)
    @DisplayName("体给 caliberId ⇒ 直通 bindMetricCaliber，回执含 resolvedBy=caliberId")
    void caliberIdIsDelegatedDirectly() {
        CaliberService service = mock(CaliberService.class);
        when(service.listVersionsByCaliberId(CALIBER_ID, null))
                .thenReturn(List.of(Map.of("version_no", VERSION)));
        Map<String, Object> serviceResult = new LinkedHashMap<>();
        serviceResult.put("metricCode", METRIC);
        serviceResult.put("caliber_id", CALIBER_ID);
        serviceResult.put("formula_version", VERSION);
        serviceResult.put("caliber_snapshot", "{\"caliber_id\":\"" + CALIBER_ID + "\"}");
        when(service.bindMetricCaliber(METRIC, CALIBER_ID, VERSION)).thenReturn(serviceResult);

        OntologyMetricBindingController controller = new OntologyMetricBindingController(service);
        MetricBindingDTO dto = new MetricBindingDTO();
        dto.setCaliberId(CALIBER_ID);
        dto.setFormulaVersion(VERSION);

        ApiResponse<Map<String, Object>> resp = controller.bindMetricCaliber(METRIC, dto);

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode());
        Map<String, Object> receipt = resp.getData();
        assertEquals(METRIC, receipt.get("metricCode"));
        assertEquals(CALIBER_ID, receipt.get("caliberId"));
        assertEquals(VERSION, receipt.get("formulaVersion"));
        assertEquals("caliberId", receipt.get("resolvedBy"));
        assertEquals("{\"caliber_id\":\"" + CALIBER_ID + "\"}", receipt.get("caliber_snapshot"),
                "服务返回的原样回执不得被改写");
        verify(service).bindMetricCaliber(METRIC, CALIBER_ID, VERSION);
        verify(service, never()).findByCode(anyString());
    }

    // ── 3. 旧体键 caliberCode 回退 ───────────────────────────

    @Test
    @Timeout(120)
    @DisplayName("省略 caliberId、只给 caliberCode ⇒ findByCode 折成 id 再委派，resolvedBy=caliberCode")
    void caliberCodeIsAcceptedAsFallback() {
        CaliberService service = mock(CaliberService.class);
        when(service.findByCode(CALIBER_CODE)).thenReturn(java.util.Optional.of(Map.of("id", CALIBER_ID)));
        when(service.listVersionsByCaliberId(CALIBER_ID, null))
                .thenReturn(List.of(Map.of("version_no", VERSION)));
        when(service.bindMetricCaliber(eq(METRIC), eq(CALIBER_ID), eq(VERSION)))
                .thenReturn(new LinkedHashMap<>(Map.of("metricCode", METRIC)));

        OntologyMetricBindingController controller = new OntologyMetricBindingController(service);
        MetricBindingDTO dto = new MetricBindingDTO();
        dto.setCaliberCode(CALIBER_CODE);
        dto.setFormulaVersion(VERSION);

        ApiResponse<Map<String, Object>> resp = controller.bindMetricCaliber(METRIC, dto);

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode());
        assertEquals("caliberCode", resp.getData().get("resolvedBy"));
        assertEquals(CALIBER_ID, resp.getData().get("caliberId"));
        verify(service).findByCode(CALIBER_CODE);
        verify(service).bindMetricCaliber(METRIC, CALIBER_ID, VERSION);
    }

    // ── 4. 入参拒绝腿：一律 400 + ECOS-ONTO-040，且不触达写服务 ──

    @Test
    @Timeout(120)
    @DisplayName("体缺失/双键皆空/口径不存在/版本号缺失/版本行不存在 ⇒ 400＋ECOS-ONTO-040，且不调绑定")
    void invalidInputIsRejectedWithoutWriting() {
        CaliberService service = mock(CaliberService.class);
        when(service.findByCode("CAL_NO_SUCH")).thenReturn(java.util.Optional.empty());
        when(service.listVersionsByCaliberId(CALIBER_ID, null))
                .thenReturn(List.of(Map.of("version_no", VERSION)));
        OntologyMetricBindingController controller = new OntologyMetricBindingController(service);

        assertBadRequest(() -> controller.bindMetricCaliber(METRIC, null), "体缺失");

        MetricBindingDTO emptyKeys = new MetricBindingDTO();
        emptyKeys.setFormulaVersion(VERSION);
        assertBadRequest(() -> controller.bindMetricCaliber(METRIC, emptyKeys), "双键皆空");

        MetricBindingDTO missingCaliber = new MetricBindingDTO();
        missingCaliber.setCaliberCode("CAL_NO_SUCH");
        missingCaliber.setFormulaVersion(VERSION);
        assertBadRequest(() -> controller.bindMetricCaliber(METRIC, missingCaliber), "口径不存在");

        MetricBindingDTO missingVersion = new MetricBindingDTO();
        missingVersion.setCaliberId(CALIBER_ID);
        assertBadRequest(() -> controller.bindMetricCaliber(METRIC, missingVersion), "版本号缺失");

        MetricBindingDTO unknownVersion = new MetricBindingDTO();
        unknownVersion.setCaliberId(CALIBER_ID);
        unknownVersion.setFormulaVersion("99");
        assertBadRequest(() -> controller.bindMetricCaliber(METRIC, unknownVersion), "版本行不存在");

        verify(service, never()).bindMetricCaliber(any(), any(), any());
    }

    // ── 5. 服务异常三分映射：409 / 400 / 500 ─────────────────

    @Test
    @Timeout(120)
    @DisplayName("SnapshotFrozen⇒409＋041；CaliberMissing/NotApproved⇒400＋040；其余⇒500＋042")
    void serviceFailuresMapToContractStatusLines() {
        MetricBindingDTO dto = new MetricBindingDTO();
        dto.setCaliberId(CALIBER_ID);
        dto.setFormulaVersion(VERSION);

        CaliberService frozen = mock(CaliberService.class);
        when(frozen.listVersionsByCaliberId(CALIBER_ID, null))
                .thenReturn(List.of(Map.of("version_no", VERSION)));
        when(frozen.bindMetricCaliber(METRIC, CALIBER_ID, VERSION))
                .thenThrow(new CaliberService.SnapshotFrozenException("snapshot frozen"));
        DataBridgeException e409 = assertThrows(DataBridgeException.class,
                () -> new OntologyMetricBindingController(frozen).bindMetricCaliber(METRIC, dto));
        assertEquals(409, e409.getHttpStatus());
        assertEquals(409, e409.getErrorCode());
        assertTrue(e409.getMessage().startsWith("ECOS-ONTO-041:"), e409.getMessage());
        assertEquals(e409.getHttpStatus(), DataBridgeException.resolveHttpStatus(e409.getErrorCode()));

        CaliberService missing = mock(CaliberService.class);
        when(missing.listVersionsByCaliberId(CALIBER_ID, null))
                .thenReturn(List.of(Map.of("version_no", VERSION)));
        when(missing.bindMetricCaliber(METRIC, CALIBER_ID, VERSION))
                .thenThrow(new CaliberService.CaliberMissingException("caliber missing"));
        DataBridgeException e400 = assertThrows(DataBridgeException.class,
                () -> new OntologyMetricBindingController(missing).bindMetricCaliber(METRIC, dto));
        assertEquals(400, e400.getHttpStatus());
        assertTrue(e400.getMessage().startsWith("ECOS-ONTO-040:"), e400.getMessage());

        CaliberService notApproved = mock(CaliberService.class);
        when(notApproved.listVersionsByCaliberId(CALIBER_ID, null))
                .thenReturn(List.of(Map.of("version_no", VERSION)));
        when(notApproved.bindMetricCaliber(METRIC, CALIBER_ID, VERSION))
                .thenThrow(new CaliberService.CaliberNotApprovedException("version not approved"));
        DataBridgeException e400b = assertThrows(DataBridgeException.class,
                () -> new OntologyMetricBindingController(notApproved).bindMetricCaliber(METRIC, dto));
        assertEquals(400, e400b.getHttpStatus());
        assertTrue(e400b.getMessage().startsWith("ECOS-ONTO-040:"), e400b.getMessage());

        CaliberService broken = mock(CaliberService.class);
        when(broken.listVersionsByCaliberId(CALIBER_ID, null))
                .thenReturn(List.of(Map.of("version_no", VERSION)));
        when(broken.bindMetricCaliber(METRIC, CALIBER_ID, VERSION))
                .thenThrow(new IllegalStateException("db down"));
        DataBridgeException e500 = assertThrows(DataBridgeException.class,
                () -> new OntologyMetricBindingController(broken).bindMetricCaliber(METRIC, dto));
        assertEquals(500, e500.getHttpStatus());
        assertTrue(e500.getMessage().startsWith("ECOS-ONTO-042:"), e500.getMessage());
        assertEquals(e500.getHttpStatus(), DataBridgeException.resolveHttpStatus(e500.getErrorCode()));
    }

    // ── helpers ─────────────────────────────────────────────

    private interface Call {
        Object run() throws Exception;
    }

    private static void assertBadRequest(Call call, String leg) {
        DataBridgeException thrown = assertThrows(DataBridgeException.class, call::run,
                leg + " 必须抛携带型 400，不得返回 200 信封");
        assertEquals(400, thrown.getHttpStatus(), leg + " 状态行不符");
        assertEquals(400, thrown.getErrorCode(), leg + " 携带码须与状态行同号");
        assertTrue(thrown.getMessage().startsWith("ECOS-ONTO-040:"),
                leg + " 消息首段须为 ECOS-ONTO-040，实际：" + thrown.getMessage());
        assertEquals(thrown.getHttpStatus(), DataBridgeException.resolveHttpStatus(thrown.getErrorCode()),
                leg + " 状态行必须由 resolveHttpStatus 单源推出");
    }
}
