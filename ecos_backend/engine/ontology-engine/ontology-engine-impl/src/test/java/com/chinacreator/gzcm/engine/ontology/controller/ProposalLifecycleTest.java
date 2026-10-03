package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyProposalPublishVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyProposalSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyProposalVO;
import com.chinacreator.gzcm.engine.ontology.gate.GateContext;
import com.chinacreator.gzcm.engine.ontology.gate.GateResult;
import com.chinacreator.gzcm.engine.ontology.gate.PublishGateService;
import com.chinacreator.gzcm.engine.ontology.service.OntologyProposalService;
import com.chinacreator.gzcm.engine.ontology.service.OntologyService;
import com.chinacreator.gzcm.engine.ontology.service.OntologyVersionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F03-08 提案状态机（W74/C58 P0）：DRAFT → PENDING(submit) → APPROVED → EXECUTED；reject → REJECTED。
 * PENDING → EXECUTE 直接拒绝（ECOS-ONTO-012），移除旧实现"execute 也接受 PENDING"的绕行口子。
 *
 * <p>execute 端点在状态校验之后、executeAndPublish 之前挂 PublishGateService：
 * 门禁拒绝（含 503/500）时 proposal 保持 PENDING，不产生半批准态。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProposalLifecycleTest {

    @Mock OntologyProposalService proposalService;
    @Mock OntologyVersionService versionService;
    @Mock OntologyService ontologyService;
    @Mock PublishGateService publishGateService;

    private OntologyProposalController ctl() {
        return new OntologyProposalController(proposalService, versionService, ontologyService, publishGateService);
    }

    @Test
    @DisplayName("全生命周期：submit(DRAFT→PENDING) → approve(PENDING→APPROVED) → 落点 OK")
    void happyPathSubmitApprove() throws Exception {
        Map<String, Object> row = row("1", "DRAFT", "author-a");
        when(proposalService.queryForMap(eq("SELECT * FROM ecos_ontology_proposals WHERE id=?::bigint"), eq("1")))
                .thenReturn(row);

        // submit
        ApiResponse<OntologyProposalVO> sub = ctl().submitProposal("1");
        assertEquals(ApiResponse.CODE_SUCCESS, sub.getCode());

        // 重新查询走 sub 更新查询，status=PENDING
        Map<String, Object> afterSubmit = row("1", "PENDING", "author-a");
        lenient().when(proposalService.queryForMap(anyString(), any()))
                .thenReturn(afterSubmit);

        OntologyProposalSaveDTO body = new OntologyProposalSaveDTO();
        body.setReviewer("reviewer-b");
        body.setReviewComment("OK");
        ApiResponse<OntologyProposalVO> ap = ctl().approveProposal("1", body);
        assertEquals(ApiResponse.CODE_SUCCESS, ap.getCode());
        // transition 里共两次 update（一次由 approve 打）— 使用 times(2)
        org.mockito.Mockito.verify(proposalService, org.mockito.Mockito.times(2))
                .update(any(String.class), any(Object[].class));
    }

    @Test
    @DisplayName("终态 EXECUTED 不可再流转：transition → 400 ONT-005")
    void terminalStateLocked() {
        Map<String, Object> row = row("9", "EXECUTED", "a");
        when(proposalService.queryForMap(
                eq("SELECT * FROM ecos_ontology_proposals WHERE id=?::bigint"), eq("9")))
                .thenReturn(row);
        ApiResponse<OntologyProposalVO> r = ctl().submitProposal("9");
        assertEquals(ApiResponse.CODE_BAD_REQUEST, r.getCode());
        assertTrue(r.getMessage().contains("ONT-005"));
    }

    @Test
    @DisplayName("reject 走 Reviewer ≠ Author 强校验 → 400 ONT-007")
    void rejectSelfReviewedForbidden() {
        Map<String, Object> row = row("2", "PENDING", "author-a");
        when(proposalService.queryForMap(anyString(), any())).thenReturn(row);
        OntologyProposalSaveDTO body = new OntologyProposalSaveDTO();
        body.setReviewer("author-a"); // 与作者相同
        ApiResponse<OntologyProposalVO> r = ctl().rejectProposal("2", body);
        assertEquals(ApiResponse.CODE_BAD_REQUEST, r.getCode());
        assertTrue(r.getMessage().contains("ONT-007"));
    }

    @Test
    @DisplayName("approve-and-publish：PENDING → 门禁通过 → 落 APPROVED & 版本发布 & EXECUTED")
    void approveAndPublishHappy() {
        Map<String, Object> row = row("3", "PENDING", "author-a");
        when(proposalService.findProposalById("3")).thenReturn(row);
        when(publishGateService.validate(eq("3"), any(), any(GateContext.class)))
                .thenReturn(GateResult.of(true, List.of(), null));

        OntologyProposalSaveDTO body = new OntologyProposalSaveDTO();
        body.setReviewer("reviewer-b");
        body.setReviewComment("OK");

        // stub executeAndPublish 依赖
        Map<String, Object> version = new LinkedHashMap<>();
        version.put("id", "v-1");
        version.put("versionNo", "1");
        when(versionService.createVersion(anyString(), anyMap())).thenReturn(version);
        // approve 落 'approved' 后 markExecuted 落 EXECUTED
        lenient().when(proposalService.findProposalById("3")).thenReturn(row);

        ApiResponse<OntologyProposalPublishVO> r = ctl().approveAndPublish("3", body);
        assertEquals(ApiResponse.CODE_SUCCESS, r.getCode());
        assertNotNull(r.getData());
        // 结果应为 EXECUTED 且带 versionId
        assertEquals(OntologyProposalController.STATUS_EXECUTED, r.getData().getStatus());
        assertEquals("v-1", r.getData().getVersionId());
    }

    @Test
    @DisplayName("approve-and-publish：门禁依赖不可用 → 410/503 拒绝落库，proposal 保持 PENDING")
    void approveAndPublishDependencyDown() {
        Map<String, Object> row = row("4", "PENDING", "author-a");
        when(proposalService.findProposalById("4")).thenReturn(row);
        GateResult down = GateResult.dependencyDown("datanet", "ECOS-ONTO-050", "datanet 不可用");
        when(publishGateService.validate(eq("4"), any(), any(GateContext.class))).thenReturn(down);

        OntologyProposalSaveDTO body = new OntologyProposalSaveDTO();
        body.setReviewer("reviewer-b");
        ApiResponse<OntologyProposalPublishVO> r = ctl().approveAndPublish("4", body);
        // gateReject 依赖-down 分支 code=503
        assertEquals(503, r.getCode());
        // 未落 approve
        verify(proposalService, never()).approve(anyString(), anyString(), anyString(), anyString());
        verify(versionService, never()).createVersion(anyString(), any());
    }

    // ── 构造工具 ──
    private static Map<String, Object> row(String id, String status, String author) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", Long.valueOf(id));
        m.put("status", status);
        m.put("author", author);
        m.put("domain_code", "d");
        m.put("proposal_type", "CREATE_ENTITY");
        m.put("target_entity", "e");
        m.put("payload", "{}");
        return m;
    }
}
