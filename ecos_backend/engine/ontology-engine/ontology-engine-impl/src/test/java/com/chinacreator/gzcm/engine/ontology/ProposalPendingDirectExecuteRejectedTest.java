package com.chinacreator.gzcm.engine.ontology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.controller.OntologyProposalController;
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
import org.springframework.http.ResponseEntity;

/**
 * W74 / C58 《ProposalPendingDirectExecuteRejectedTest》—
 * PENDING 提案直接 execute 必须被拒绝（原 bug：execute 把 PENDING 列入允许集，绕过审批）。
 *
 * <p>F03-08：允许集只含 {@code {VERIFIED, APPROVED}}；PENDING 直达命中
 * 409 {@code ECOS-ONTO-012}（不再进入 executeAndPublish）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("W74：PENDING 提案直接 execute 被拒（409 ECOS-ONTO-012）")
class ProposalPendingDirectExecuteRejectedTest {

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

    @Test
    @DisplayName("PENDING → execute：返回 409 ECOS-ONTO-012，且不触发 versionService.createVersion")
    void pendingDirectExecuteIsRejected() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", "p1");
        row.put("status", "PENDING");
        row.put("domain_code", "default");
        row.put("proposal_type", "CREATE_ENTITY");
        when(proposalService.queryForMap(anyString(), any())).thenReturn(row);

        ResponseEntity<ApiResponse<OntologyProposalVO>> entity = controller.executeProposal("p1");
        ApiResponse<OntologyProposalVO> resp = entity.getBody();

        assertNotNull(resp, "拒绝态必须带信封体");
        // #142/B4-3e-37（D-B4-59）：状态行与信封码同号，不得只改码把行留在 200
        assertEquals(409, entity.getStatusCode().value(),
                "PENDING 直执的 HTTP 状态行应为 409，实际 " + entity.getStatusCode());
        assertEquals(409, resp.getCode(), "信封 code 应与状态行同号");
        // 400 之上的 4xx（PENDING = 409）+ ECOS-ONTO-012 语义
        assertTrue(resp.getCode() >= 400, "PENDING 直执应返回 4xx, 实际 code=" + resp.getCode());
        assertTrue(resp.getMessage() != null && resp.getMessage().contains("ECOS-ONTO-012"),
                "PENDING 直执应携带 ECOS-ONTO-012，实际 message=" + resp.getMessage());
        // 关键回归点：不得走版本创建/发布闭环（绕过审批 = 空转落库）
        verify(versionService, never()).createVersion(anyString(), any());
        assertFalse(resp.isSuccess(), "PENDING 直执不得成功");
    }
}
