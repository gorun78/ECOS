package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyProposalVO;
import com.chinacreator.gzcm.engine.ontology.gate.GateContext;
import com.chinacreator.gzcm.engine.ontology.gate.GateResult;
import com.chinacreator.gzcm.engine.ontology.gate.GateViolation;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F03-08（W74/C58 P0）：execute 端点 PENDING 直执禁令。
 *
 * <p>旧实现允许 "PENDING ↔ execute"，绕过 review/verify 直接落库（O-7 提案链 0 行的
 * 根因之一）。F03-08 明确"移除当前也允许的 PENDING 直执"，仅 {@code APPROVED|VERIFIED}
 * 可 execute，其余 → 400 ECOS-ONTO-012。
 *
 * <p>本测试用反射式断言 —— 直接调用 {@code executeProposal}，验证：</p>
 * <ol>
 *   <li>PENDING 提案 → 400 ECOS-ONTO-012，未调用 {@code PublishGateService.validate}；</li>
 *   <li>DRAFT 提案 → 同样 400 ECOS-ONTO-012；</li>
 *   <li>APPROVED 提案 → 状态校验通过后调 publishGateService.validate；若 gate 拒绝（400/503）
 *       则不进入 executeAndPublish（用 mock 计数验证）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProposalPendingDirectExecuteRejectedTest {

    @Mock OntologyProposalService proposalService;
    @Mock OntologyVersionService versionService;
    @Mock OntologyService ontologyService;
    @Mock PublishGateService publishGateService;

    private OntologyProposalController ctl() {
        return new OntologyProposalController(proposalService, versionService, ontologyService, publishGateService);
    }

    @Test
    @DisplayName("execute + 状态=PENDING → 400 ECOS-ONTO-012（PENDING 直执已禁）")
    void pendingDirectExecuteRejected() {
        Map<String, Object> row = row("1", "PENDING");
        when(proposalService.queryForMap(
                eq("SELECT * FROM ecos_ontology_proposals WHERE id=?::bigint"), eq("1")))
                .thenReturn(row);

        ApiResponse<OntologyProposalVO> r = ctl().executeProposal("1");
        assertEquals(ApiResponse.CODE_BAD_REQUEST, r.getCode());
        assertTrue(r.getMessage().contains("ECOS-ONTO-012"),
                "消息应含新错误码: " + r.getMessage());

        // 未调门禁
        verify(publishGateService, never()).validate(anyString(), any(), any(GateContext.class));
        // 未 executeAndPublish —— markExecuted 也没被调用
        verify(proposalService, never()).markExecuted(anyString(), anyString());
        verify(proposalService, never()).markExecutedWithVersion(anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("execute + 状态=DRAFT → 400 ECOS-ONTO-012（同样拒绝）")
    void draftDirectExecuteRejected() {
        Map<String, Object> row = row("2", "DRAFT");
        when(proposalService.queryForMap(anyString(), any())).thenReturn(row);
        ApiResponse<OntologyProposalVO> r = ctl().executeProposal("2");
        assertEquals(ApiResponse.CODE_BAD_REQUEST, r.getCode());
        assertTrue(r.getMessage().contains("ECOS-ONTO-012"));
    }

    @Test
    @DisplayName("execute + REJECTED 状态 → 400 ECOS-ONTO-012")
    void rejectedProposalCannotExecute() {
        Map<String, Object> row = row("3", "REJECTED");
        when(proposalService.queryForMap(anyString(), any())).thenReturn(row);
        ApiResponse<OntologyProposalVO> r = ctl().executeProposal("3");
        assertEquals(ApiResponse.CODE_BAD_REQUEST, r.getCode());
    }

    @Test
    @DisplayName("execute + APPROVED：状态通过 → 进入门禁 → 门禁拒绝 400 ECOS-ONTO-040，未执行 executeAndPublish")
    void approvedThenGateRejects() {
        Map<String, Object> row = row("4", "APPROVED");
        when(proposalService.queryForMap(anyString(), any())).thenReturn(row);

        GateResult g = GateResult.of(false, List.of(new GateViolation(GateViolation.Gate.V1_CALIBER, "ECOS-ONTO-040",
                "指标 M_X 缺 caliber_id")), null);
        when(publishGateService.validate(anyString(), any(), any(GateContext.class))).thenReturn(g);

        ApiResponse<OntologyProposalVO> r = ctl().executeProposal("4");
        assertEquals(ApiResponse.CODE_BAD_REQUEST, r.getCode());
        assertTrue(r.getMessage().contains("ECOS-ONTO-040"));
        verify(proposalService, never()).markExecuted(anyString(), anyString());
    }

    private static Map<String, Object> row(String id, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", Long.valueOf(id));
        m.put("status", status);
        m.put("domain_code", "d");
        m.put("proposal_type", "CREATE_ENTITY");
        m.put("payload", "{}");
        return m;
    }
}
