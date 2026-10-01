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
import com.chinacreator.gzcm.engine.ontology.dto.OntologyProposalSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyProposalVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyProposalVerifyVO;
import com.chinacreator.gzcm.engine.ontology.service.OntologyProposalService;
import com.chinacreator.gzcm.engine.ontology.service.OntologyService;
import com.chinacreator.gzcm.engine.ontology.service.OntologyVersionService;
import com.chinacreator.gzcm.engine.ontology.service.PublishGateService;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * W74 / C58 《ProposalLifecycleTest》— 提案状态机表驱动。
 *
 * <p>F03-08 状态机：{@code DRAFT → PENDING → (APPROVED | REJECTED) → VERIFIED → EXECUTED}。
 * 关键回归点：
 * <ul>
 *   <li>PENDING 不再可直执行（原 bug）→ DRAFT / PENDING execute 命中 ECOS-ONTO-012；</li>
 *   <li>verify 成功写大写 {@code VERIFIED}（旧代码误写小写 "verified"）；</li>
 *   <li>VERIFIED execute 走 {@code executeAndPublish}（versionService.createVersion 被调用）。</li>
 * </ul>
 * 全 mock，不依赖真实 PG / Neo4j。{@code PublishGateService.validate} 恒返回空通过结果。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("W74：提案状态机生命周期（DRAFT→PENDING→APPROVED→VERIFIED→EXECUTED + PENDING 禁直执）")
class ProposalLifecycleTest {

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

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "draft_submit_to_pending",
            "pending_approve_to_approved",
            "approved_verify_to_verified",
            "verified_execute_to_executed",
            "draft_execute_rejected",
            "pending_execute_rejected"
    })
    @DisplayName("状态机场景")
    void lifecycle(String scenario) {
        switch (scenario) {
            case "draft_submit_to_pending" -> {
                when(proposalService.queryForMap(anyString(), any()))
                        .thenReturn(row("p1", "DRAFT", "alice"), row("p1", "PENDING", "alice"));
                ApiResponse<OntologyProposalVO> resp = controller.submitProposal("p1");
                assertTrue(resp.isSuccess(), "DRAFT→submit 应成功, code=" + resp.getCode());
                assertEquals("PENDING", resp.getData().getStatus());
            }
            case "pending_approve_to_approved" -> {
                when(proposalService.queryForMap(anyString(), any()))
                        .thenReturn(row("p1", "PENDING", "alice"), row("p1", "APPROVED", "alice"));
                OntologyProposalSaveDTO body = new OntologyProposalSaveDTO();
                body.setReviewer("bob"); // reviewer ≠ author(alice)
                ApiResponse<OntologyProposalVO> resp = controller.approveProposal("p1", body);
                assertTrue(resp.isSuccess(), "PENDING→approve 应成功, code=" + resp.getCode()
                        + " msg=" + resp.getMessage());
                assertEquals("APPROVED", resp.getData().getStatus());
            }
            case "approved_verify_to_verified" -> {
                Map<String, Object> approved = row("p1", "APPROVED", "alice");
                approved.put("proposal_type", "CREATE_ENTITY");
                Map<String, Object> payload = new HashMap<>();
                payload.put("name", "Foo");
                payload.put("code", "FOO");
                approved.put("payload", payload);
                Map<String, Object> verified = row("p1", "VERIFIED", "alice");
                when(proposalService.queryForMap(anyString(), any()))
                        .thenReturn(approved, verified);
                ApiResponse<OntologyProposalVerifyVO> resp = controller.verifyProposal("p1");
                assertTrue(resp.isSuccess(), "APPROVED→verify 应成功, code=" + resp.getCode());
                assertTrue(Boolean.TRUE.equals(resp.getData().getValid()), "payload 完整 → valid=true");
                assertEquals("VERIFIED", String.valueOf(resp.getData().getProposal().get("status")),
                        "verify 成功后必须写大写 VERIFIED");
            }
            case "verified_execute_to_executed" -> {
                when(proposalService.queryForMap(anyString(), any()))
                        .thenReturn(row("p1", "VERIFIED", "alice"), row("p1", "EXECUTED", "alice"));
                when(publishGate.validate(any())).thenReturn(passedGate());
                Map<String, Object> version = new HashMap<>();
                version.put("id", "v1");
                version.put("versionNo", 1);
                when(versionService.createVersion(anyString(), any())).thenReturn(version);

                ApiResponse<OntologyProposalVO> resp = controller.executeProposal("p1");
                assertTrue(resp.isSuccess(), "VERIFIED→execute 应成功, code=" + resp.getCode()
                        + " msg=" + resp.getMessage());
                assertEquals("EXECUTED", resp.getData().getStatus());
                verify(versionService, times(1)).createVersion(anyString(), any());
            }
            case "draft_execute_rejected" -> {
                when(proposalService.queryForMap(anyString(), any())).thenReturn(row("p1", "DRAFT", "alice"));
                ApiResponse<?> resp = controller.executeProposal("p1");
                assertEquals(400, resp.getCode(), "DRAFT execute → 400");
                assertTrue(resp.getMessage().contains("ECOS-ONTO-012"),
                        "应携带 ECOS-ONTO-012, 实际=" + resp.getMessage());
                verify(versionService, never()).createVersion(anyString(), any());
            }
            case "pending_execute_rejected" -> {
                when(proposalService.queryForMap(anyString(), any()))
                        .thenReturn(row("p1", "PENDING", "alice"));
                ApiResponse<?> resp = controller.executeProposal("p1");
                assertEquals(409, resp.getCode(), "PENDING execute → 409 (绕过审批)");
                assertTrue(resp.getMessage().contains("ECOS-ONTO-012"),
                        "应携带 ECOS-ONTO-012, 实际=" + resp.getMessage());
            }
            default -> throw new IllegalStateException("未知 scenario: " + scenario);
        }
    }

    private static GateResult passedGate() {
        GateResult g = new GateResult();
        g.setPassed(true);
        return g;
    }

    private static Map<String, Object> row(String id, String status, String author) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("status", status);
        m.put("author", author);
        m.put("domain_code", "default");
        m.put("proposal_type", "CREATE_ENTITY");
        assertNotNull(id);
        return m;
    }
}
