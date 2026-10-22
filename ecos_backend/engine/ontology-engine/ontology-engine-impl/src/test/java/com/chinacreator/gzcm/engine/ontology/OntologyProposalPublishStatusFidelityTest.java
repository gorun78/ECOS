package com.chinacreator.gzcm.engine.ontology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.controller.OntologyProposalController;
import com.chinacreator.gzcm.engine.ontology.dto.GateResult;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyProposalPublishVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyProposalSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyProposalVO;
import com.chinacreator.gzcm.engine.ontology.service.OntologyProposalService;
import com.chinacreator.gzcm.engine.ontology.service.OntologyService;
import com.chinacreator.gzcm.engine.ontology.service.OntologyVersionService;
import com.chinacreator.gzcm.engine.ontology.service.PublishGateService;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * #142/B4-3e-37 行为门《OntologyProposalPublishStatusFidelityTest》—
 * 本体提案执行/发布闭环的 <b>HTTP 状态行保真</b>（D-B4-59 末腿）。
 *
 * <p>缺陷本体：拒绝态只改信封 {@code code} 而把状态行留在 200，等于「改码不改行」——
 * 调用方按 HTTP 判定成功与否时会把 409/404/400/503 读成成功。本门把六枚腿钉成
 * 「状态行＝预期值 ＋ 状态行与信封同号」，并各带一条反向语义（成功腿必须显式 200、
 * 门禁通过腿不得产出响应）。</p>
 *
 * <p>与既有门的分工：{@code ProposalLifecycleTest}／{@code ProposalPendingDirectExecuteRejectedTest}
 * 钉状态机语义，{@code PublishGateV3Test} 钉门禁映射；本门钉 <b>端点出口的状态行</b>，
 * 即「三态（通过／拒绝／依赖不可用）在 HTTP 面上可分辨」这一验收口径。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("#142/B4-3e-37：本体提案端点 HTTP 状态行保真（D-B4-59 末腿）")
class OntologyProposalPublishStatusFidelityTest {

    @Mock
    private OntologyProposalService proposalService;
    @Mock
    private OntologyVersionService versionService;
    @Mock
    private OntologyService ontologyService;
    @Mock
    private PublishGateService publishGate;

    @InjectMocks
    private OntologyProposalController controller;

    private static Map<String, Object> row(String id, String status) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", id);
        r.put("status", status);
        r.put("domain_code", "default");
        r.put("proposal_type", "CREATE_ENTITY");
        r.put("author", "alice");
        return r;
    }

    private static GateResult passedGate() {
        GateResult g = new GateResult();
        g.setPassed(true);
        return g;
    }

    private static GateResult dependencyGate() {
        GateResult g = new GateResult();
        g.setPassed(false);
        g.getDependencyErrors().add(
                new GateResult.DependencyError("datanet", GateResult.CODE_DEPENDENCY_UNAVAILABLE));
        return g;
    }

    private static GateResult violationGate() {
        GateResult g = new GateResult();
        g.setPassed(false);
        g.getViolations().add(new GateResult.GateViolation(
                GateResult.GATE_V1_CALIBER, GateResult.CODE_CALIBER_METRIC_MISSING, "no approved caliber"));
        return g;
    }

    private static void assertSameLine(ResponseEntity<ApiResponse<OntologyProposalVO>> entity, int expected,
                                       String label) {
        assertNotNull(entity, label + "：端点必须产出响应");
        assertEquals(expected, entity.getStatusCode().value(),
                label + "：HTTP 状态行应为 " + expected + "，实际 " + entity.getStatusCode());
        ApiResponse<OntologyProposalVO> body = entity.getBody();
        assertNotNull(body, label + "：必须带信封体");
        assertEquals(expected, body.getCode(),
                label + "：状态行与信封 code 必须同号（状态行 " + entity.getStatusCode()
                        + "／信封 " + body.getCode() + "）");
    }

    @Test
    @DisplayName("提案不存在腿：404 状态行＋信封同号，且不进门禁")
    void notFoundLegCarries404() {
        when(proposalService.queryForMap(anyString(), any()))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertSameLine(controller.executeProposal("missing"), 404, "ONT-001 未找到腿");
        verify(publishGate, never()).validate(any());
    }

    @Test
    @DisplayName("PENDING 直达腿：409 状态行（不得折成 400/200），且不落库")
    void pendingBypassLegCarries409() {
        when(proposalService.queryForMap(anyString(), any())).thenReturn(row("p1", "PENDING"));

        assertSameLine(controller.executeProposal("p1"), 409, "ECOS-ONTO-012 绕审批腿");
        verify(versionService, never()).createVersion(anyString(), any());
    }

    @Test
    @DisplayName("DRAFT 等非法状态腿：400 状态行＋信封同号")
    void illegalStatusLegCarries400() {
        when(proposalService.queryForMap(anyString(), any())).thenReturn(row("p1", "DRAFT"));

        assertSameLine(controller.executeProposal("p1"), 400, "ECOS-ONTO-012 非法状态腿");
        verify(versionService, never()).createVersion(anyString(), any());
    }

    @Test
    @DisplayName("门禁依赖不可用腿：503 状态行（不可用≠无权限，禁折 403/200），且 fail-closed 不落库")
    void gateDependencyLegCarries503AndDoesNotProceed() {
        when(proposalService.queryForMap(anyString(), any())).thenReturn(row("p1", "VERIFIED"));
        when(publishGate.validate(any())).thenReturn(dependencyGate());

        ResponseEntity<ApiResponse<OntologyProposalVO>> entity = controller.executeProposal("p1");
        assertSameLine(entity, 503, "ECOS-ONTO-050 依赖不可用腿");
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, entity.getStatusCode(),
                "依赖不可用必须是 SERVICE_UNAVAILABLE 而非折叠成 FORBIDDEN");
        assertNotNull(entity.getBody().getMessage());
        assertTrue(entity.getBody().getMessage().contains("ECOS-ONTO-050"),
                "不可用腿应携带 ECOS-ONTO-050，实际 " + entity.getBody().getMessage());
        verify(versionService, never()).createVersion(anyString(), any());
    }

    @Test
    @DisplayName("门禁违反项腿：400 状态行＋拒绝响应，且 fail-closed 不落库")
    void gateViolationLegCarries400AndDoesNotProceed() {
        when(proposalService.queryForMap(anyString(), any())).thenReturn(row("p1", "APPROVED"));
        when(publishGate.validate(any())).thenReturn(violationGate());

        assertSameLine(controller.executeProposal("p1"), 400, "ECOS-ONTO-publish-gate-failed 违反腿");
        verify(versionService, never()).createVersion(anyString(), any());
    }

    @Test
    @DisplayName("反向腿：门禁全通过不产出拒绝响应，成功态状态行显式 200（CODE_SUCCESS=0 不得折成 500）")
    void passedGateYieldsExplicit200() {
        when(proposalService.queryForMap(anyString(), any()))
                .thenReturn(row("p1", "VERIFIED"), row("p1", "EXECUTED"));
        when(publishGate.validate(any())).thenReturn(passedGate());
        Map<String, Object> version = new HashMap<>();
        version.put("id", "v1");
        version.put("versionNo", 1);
        when(versionService.createVersion(anyString(), any())).thenReturn(version);

        ResponseEntity<ApiResponse<OntologyProposalVO>> entity = controller.executeProposal("p1");
        assertNotNull(entity, "成功腿必须产出响应");
        assertEquals(200, entity.getStatusCode().value(),
                "成功态状态行应为显式 200，实际 " + entity.getStatusCode());
        ApiResponse<OntologyProposalVO> body = entity.getBody();
        assertNotNull(body, "成功态必须带信封体");
        assertEquals(ApiResponse.CODE_SUCCESS, body.getCode(),
                "成功信封 code 应保持 CODE_SUCCESS=0，不因折叠而漂移");
        verify(versionService, times(1)).createVersion(anyString(), any());
    }

    @Test
    @DisplayName("反向腿：/approve-and-publish 门禁不可用腿同样落 503 状态行")
    void approveAndPublishGateDependencyLegCarries503() {
        Map<String, Object> pending = row("p1", "PENDING");
        when(proposalService.findProposalById("p1")).thenReturn(pending);
        when(publishGate.validate(any())).thenReturn(dependencyGate());
        // 审批人显式带入：不依赖 UserContext 是否可解析，确保走到门禁腿而非 ONT-008 400 腿
        OntologyProposalSaveDTO body = new OntologyProposalSaveDTO();
        body.setReviewer("bob");

        ResponseEntity<ApiResponse<OntologyProposalPublishVO>> entity =
                controller.approveAndPublish("p1", body);

        assertNotNull(entity, "approve-and-publish 不可用腿必须产出响应");
        assertEquals(503, entity.getStatusCode().value(),
                "approve-and-publish 的依赖不可用状态行应为 503，实际 " + entity.getStatusCode()
                        + "（若为 400 说明没走到门禁腿）");
        assertNotNull(entity.getBody(), "不可用腿必须带信封体");
        assertEquals(503, entity.getBody().getCode(), "状态行与信封 code 必须同号");
        verify(versionService, never()).createVersion(anyString(), any());
    }
}
