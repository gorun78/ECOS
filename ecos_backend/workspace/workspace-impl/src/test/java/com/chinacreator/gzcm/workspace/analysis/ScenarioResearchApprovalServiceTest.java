package com.chinacreator.gzcm.workspace.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.security.header.ServiceContext;
import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;

/**
 * L3 研究候选审批单测（2026-11-25，可行性报告 v2.0 §5.5）。
 *
 * <p>覆盖四条不可让步的语义：①批准缺 approvalRef 即 400（禁生成即审批）；
 * ②decision 词表封闭；③运行不存在 404 且不落任何行；④驳回后仍可批准（append-only 语义，
 * 历史不被覆盖）；⑤审批只留痕、不改运行数据。</p>
 */
class ScenarioResearchApprovalServiceTest {

    private final WorkspaceDataDao dao = mock(WorkspaceDataDao.class);
    private final ScenarioResearchApprovalService service = new ScenarioResearchApprovalService(dao);

    @BeforeEach
    void bindApprover() {
        // 审批必须可追责：注入真实主体上下文（与 :18090 由 HeaderAuthInterceptor 还原同源）
        ServiceContext.set(ServiceContext.of("t-1", "u-admin", "ROLE_SUPER_ADMIN,ADMIN",
                "tenant-a", "default", "workspace"));
    }

    @AfterEach
    void clearContext() {
        ServiceContext.clear();
    }

    private void stubRun(String runId, String stopReason) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", runId);
        row.put("scenario_id", "sc-1");
        row.put("stop_reason", stopReason);
        row.put("sota_run_id", null);
        row.put("advice", "{\"summary\":\"s\"}");
        row.put("exec_trace", "[]");
        when(dao.findResearchRun(runId)).thenReturn(List.of(row));
    }

    @Test
    @DisplayName("批准缺 approvalRef ⇒ 400，且不落任何审批行（禁生成即审批）")
    void approveRequiresApprovalRef() {
        stubRun("run-1", "MAX_LOOPS");
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.decide("run-1", "APPROVED", "  ", null, null));
        assertEquals(400, e.getHttpStatus());
        verify(dao, never()).insertResearchApproval(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("decision 词表封闭：APPROVED/REJECTED 之外一律 400")
    void decisionVocabularyIsClosed() {
        stubRun("run-1", "MAX_LOOPS");
        for (String bad : List.of("PENDING", "approved?", "", "OK")) {
            BusinessException e = assertThrows(BusinessException.class,
                    () -> service.decide("run-1", bad, "ref-1", null, null));
            assertEquals(400, e.getHttpStatus());
        }
    }

    @Test
    @DisplayName("运行不存在 ⇒ 404，且不落审批行")
    void unknownRunIs404() {
        when(dao.findResearchRun("missing")).thenReturn(List.of());
        DataBridgeException e = assertThrows(DataBridgeException.class,
                () -> service.decide("missing", "REJECTED", null, "x", null));
        assertEquals(404, e.getHttpStatus());
        verify(dao, never()).insertResearchApproval(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("驳回不要求 approvalRef；驳回后批准追加第二行，历史不被覆盖")
    void rejectThenApproveAppendsAndNeverOverwrites() {
        stubRun("run-1", "NO_PROGRESS");
        when(dao.insertResearchApproval(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        Map<String, Object> rejected = service.decide("run-1", "REJECTED", null, "证据不足", null);
        assertEquals("REJECTED", rejected.get("decision"));
        assertNotNull(rejected.get("evidenceDigest"));

        Map<String, Object> approved = service.decide("run-1", "APPROVED", "APR-2026-001", null, null);
        assertEquals("APPROVED", approved.get("decision"));

        // 两次调用两次 INSERT，无 UPDATE 面 → append-only 由「没有更新方法」结构性保证
        verify(dao, times(2)).insertResearchApproval(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
        verify(dao, never()).updateResearchStopReason(any(), any());
    }

    @Test
    @DisplayName("审批只留痕：不回写运行状态、不把 SANDBOX 升 FORMAL、标记候选态")
    void approvalDoesNotMutateRun() {
        stubRun("run-1", "ACCEPTED");
        when(dao.insertResearchApproval(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        Map<String, Object> out = service.decide("run-1", "APPROVED", "APR-2", null, null);

        assertEquals(Boolean.TRUE, out.get("candidateOnly"));
        assertTrue(String.valueOf(out.get("note")).contains("不自动回写"));
        verify(dao, never()).updateResearchStopReason(any(), any());
    }

    @Test
    @DisplayName("审批必须可追责：无主体上下文时 401，禁匿名审批")
    void approvalRequiresAuthenticatedSubject() {
        ServiceContext.clear();
        stubRun("run-1", "MAX_LOOPS");
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.decide("run-1", "APPROVED", "APR-1", null, null));
        assertEquals(401, e.getHttpStatus());
        verify(dao, never()).insertResearchApproval(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("审批不预设审批角色：仅要求已认证主体 + approvalRef（裁决 2026-11-25「维持现状」）")
    void anyAuthenticatedRoleMayApproveByDesign() {
        // 裁决依据：与全仓既有 DQ 规则审批、DataGovernance 候选审批同构——两者均不做
        // 角色鉴权，权限委派给 security-engine / OPA。此处用最小角色锁定该决策：
        // 若未来要加严，必须连同 DQ 面一起裁，并同步改本断言，禁静默收紧。
        ServiceContext.set(ServiceContext.of("t-2", "u-plain", "user",
                "tenant-a", "default", "workspace"));
        stubRun("run-1", "ACCEPTED");
        when(dao.insertResearchApproval(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        Map<String, Object> out = service.decide("run-1", "APPROVED", "APR-2026-003", null, null);

        assertEquals("APPROVED", out.get("decision"));
        assertEquals("u-plain", out.get("approver"));
    }

    @Test
    @DisplayName("证据摘要稳定：同输入同摘要，且形如 sha256:<64hex>")
    void evidenceDigestIsStable() {
        String a = ScenarioResearchApprovalService.digestOf("run-1", "ACCEPTED", "adv", "[]");
        String b = ScenarioResearchApprovalService.digestOf("run-1", "ACCEPTED", "adv", "[]");
        assertEquals(a, b);
        assertNotNull(a);
        assertTrue(a.matches("sha256:[0-9a-f]{64}"), "摘要形态不符: " + a);
    }
}
