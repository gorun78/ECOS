package com.chinacreator.gzcm.engine.data.governance;

import com.chinacreator.gzcm.engine.data.governance.conf.GovernanceConfidenceResolver;
import com.chinacreator.gzcm.engine.data.governance.dao.DataGovernanceCandidateDao;
import com.chinacreator.gzcm.engine.data.governance.dto.DataGovernanceCandidateCreateDTO;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineSecurityService;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 补充设计 v1.0 §16 <b>SUP-DATA-01/02</b> 冻结测试：{@link DataGovernanceCandidateService}
 * （V272 数据治理候选，§7.1/§7.2/§7.4/§7.5）。
 *
 * <p>mock 面：{@link DataGovernanceCandidateDao} / {@link PipelineSecurityService} /
 * {@link ObjectProvider}(空 = 事件总线降级)；生产真实执行：状态机校验白名单、
 * 置信度规范化（§7.2 单源解析器）、幂等回读、security 裁决咽喉分支。</p>
 */
@ExtendWith(MockitoExtension.class)
class DataGovernanceCandidateServiceTest {

    private static final String CAND_ID = "cand-001";

    @Mock
    private DataGovernanceCandidateDao dao;
    @Mock
    private PipelineSecurityService security;
    @Mock
    private ObjectProvider<EventBusService> eventBusProvider;

    private DataGovernanceCandidateService service;

    @BeforeEach
    void setUp() {
        // 事件总线 provider 空 = 降级不阻塞主流程（生产同纪律：runtime-event 未装配静默降级）
        service = new DataGovernanceCandidateService(dao, security, eventBusProvider);
        lenient().when(eventBusProvider.getIfAvailable()).thenReturn(null);
    }

    private DataGovernanceCandidateCreateDTO validDqRuleDto() {
        DataGovernanceCandidateCreateDTO dto = new DataGovernanceCandidateCreateDTO();
        dto.setCandidateType("DQ_RULE");
        dto.setTargetRef("asset-001.amount");
        dto.setIdempotencyKey("idem-001");
        dto.setTenantId("t-1");
        dto.setCreatedBy("actor-1");
        dto.setTraceId("trace-1");
        dto.setSourceRunId("run-1");
        return dto;
    }

    private static Map<String, Object> candidateRow(String status, String approvalRef) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("candidateId", CAND_ID);
        row.put("status", status);
        row.put("tenantId", "t-1");
        row.put("traceId", "trace-1");
        row.put("sourceRunId", "run-1");
        row.put("candidateType", "DATA_CHANGE_PROPOSAL");
        row.put("rollbackPlanText", "回滚: 见计划");
        row.put("approvalRef", approvalRef);
        return row;
    }

    // ═══════════════ 幂等创建（§7.5） ═══════════════

    @Test
    @DisplayName("SUP-DATA (幂等): 同 idempotencyKey 预读命中 -> 原样返回既有行, insert 永不触发")
    void idempotentCreateReturnsExistingRowWithoutInsert() {
        DataGovernanceCandidateCreateDTO dto = validDqRuleDto();
        Map<String, Object> existing = candidateRow(DataGovernanceCandidateService.STATUS_GENERATED, null);
        when(dao.findByIdempotencyKey("t-1", "idem-001")).thenReturn(existing);

        Map<String, Object> out = service.createCandidate(dto);

        assertSame(existing, out, "幂等重试必须返回既有行 (不重新生成)");
        verify(dao, times(1)).findByIdempotencyKey("t-1", "idem-001");
        verify(dao, never()).insert(anyString(), anyString(), nullable(String.class), anyString(),
                anyString(), anyString(), nullable(String.class), nullable(Double.class),
                nullable(String.class), nullable(Integer.class), nullable(String.class),
                nullable(String.class), nullable(String.class), nullable(Double.class),
                nullable(String.class), anyString(), nullable(String.class),
                nullable(String.class), nullable(String.class), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("SUP-DATA (幂等): 新键首建 -> insert 后回读, 状态恒为 GENERATED")
    void freshCreateInsertsAndReturnsGeneratedRow() {
        DataGovernanceCandidateCreateDTO dto = validDqRuleDto();
        when(dao.findByIdempotencyKey("t-1", "idem-001")).thenReturn(null);
        Map<String, Object> created = candidateRow(DataGovernanceCandidateService.STATUS_GENERATED, null);
        when(dao.findByCandidateId(anyString())).thenReturn(created);

        Map<String, Object> out = service.createCandidate(dto);

        assertSame(created, out);
        verify(dao, times(1)).insert(anyString(), eq("t-1"), nullable(String.class), eq("DQ_RULE"),
                eq("asset-001.amount"), anyString(), nullable(String.class),
                nullable(Double.class), nullable(String.class), nullable(Integer.class),
                nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(Double.class), nullable(String.class),
                eq(DataGovernanceCandidateService.STATUS_GENERATED),
                nullable(String.class), nullable(String.class),
                eq("run-1"), anyString(), eq("idem-001"), anyString(), anyString(),
                eq("actor-1"));
    }

    // ═══════════════ SUP-DATA-01: DQ 预览隔离（预览不触正式资产） ═══════════════

    @Test
    @DisplayName("SUP-DATA-01: previewDqRule 只读候选自身 + 仅回写本行 preview_pass_rate, 不触正式资产写")
    void previewDqRuleIsolatesFromFormalAssets() {
        Map<String, Object> current = candidateRow(DataGovernanceCandidateService.STATUS_VALIDATED, null);
        current.put("evidenceRefsText", "[\"profile-001\",\"ontology-ref-4\",\"field-hits\"]");
        when(dao.findByCandidateId(CAND_ID)).thenReturn(current);

        Map<String, Object> out = service.previewDqRule(CAND_ID);

        // 证据齐备度保守基线 (3 条证据 -> 0.95); 实质动作 = 回写本候选行
        assertEquals(0.95, out.get("passRate"));
        assertEquals(Boolean.TRUE, out.get("readOnly"));
        verify(dao, times(1)).updatePreviewPassRate(CAND_ID, 0.95);
        // 预览不推进状态机 / 不建候选 (DAO 面全部写动作 = transitionStatus / insert, 均须永不触发)
        verify(dao, never()).transitionStatus(anyString(), anyString(), anyString(), nullable(String.class),
                anyString());
        verify(dao, never()).insert(anyString(), anyString(), nullable(String.class), anyString(),
                anyString(), anyString(), nullable(String.class), nullable(Double.class),
                nullable(String.class), nullable(Integer.class), nullable(String.class),
                nullable(String.class), nullable(String.class), nullable(Double.class),
                nullable(String.class), anyString(), nullable(String.class),
                nullable(String.class), nullable(String.class), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("SUP-DATA-01: 未经证据校验的候选 (GENERATED) 禁预览, 不落任何写")
    void previewRejectedBeforeEvidenceChecked() {
        when(dao.findByCandidateId(CAND_ID)).thenReturn(
                candidateRow(DataGovernanceCandidateService.STATUS_GENERATED, null));

        assertThrows(IllegalArgumentException.class, () -> service.previewDqRule(CAND_ID));
        verify(dao, never()).updatePreviewPassRate(anyString(), any(Double.class));
        verify(dao, never()).transitionStatus(anyString(), anyString(), anyString(), nullable(String.class),
                anyString());
    }

    // ═══════════════ SUP-DATA-02: apply 咽喉 (security 裁决 DENY → 无 APPLIED 落库) ═══════════════

    @Test
    @DisplayName("SUP-DATA-02: apply 时 security 裁决 DENY (POLICY_DENIED 语义) -> SecurityDenyException, 状态禁迁 APPLIED")
    void applyDeniedBySecurityThrowsWithoutStatusTransition() {
        when(dao.findByCandidateId(CAND_ID)).thenReturn(
                candidateRow(DataGovernanceCandidateService.STATUS_APPROVED, "approval-9001"));
        when(security.evaluateDataGovernanceApply(CAND_ID))
                .thenReturn(new PipelineSecurityService.AllowedResult(false, "opa policy deny"));

        SecurityDenyException ex = assertThrows(SecurityDenyException.class,
                () -> service.applyApprovedProposal(CAND_ID));
        assertTrue(ex.getMessage().contains("DENY"), "异常须携 DENY 裁决原因");

        // 拒绝态: 无任何状态迁移落库 (APPLIED 未持久化); 总线 provider 空时 POLICY_DENIED 事件静默降级
        verify(dao, never()).transitionStatus(anyString(), anyString(), anyString(), nullable(String.class),
                anyString());
        verify(eventBusProvider).getIfAvailable();
    }

    @Test
    @DisplayName("SUP-DATA-02: apply 裁决 ALLOW -> APPROVED->APPLIED CAS 成功落库")
    void applyAllowedTransitionsToApplied() {
        when(dao.findByCandidateId(CAND_ID)).thenReturn(
                candidateRow(DataGovernanceCandidateService.STATUS_APPROVED, "approval-9001"));
        when(security.evaluateDataGovernanceApply(CAND_ID))
                .thenReturn(new PipelineSecurityService.AllowedResult(true, "ok"));
        when(dao.transitionStatus(eq(CAND_ID),
                eq(DataGovernanceCandidateService.STATUS_APPROVED),
                eq(DataGovernanceCandidateService.STATUS_APPLIED),
                eq("approval-9001"), anyString())).thenReturn(1);

        service.applyApprovedProposal(CAND_ID);

        verify(dao, times(1)).transitionStatus(eq(CAND_ID),
                eq(DataGovernanceCandidateService.STATUS_APPROVED),
                eq(DataGovernanceCandidateService.STATUS_APPLIED),
                eq("approval-9001"), anyString());
    }

    @Test
    @DisplayName("SUP-DATA-02: 未批准候选 (GENERATED) 禁 apply, 状态机/裁决咽喉前即拒")
    void applyRejectedUnlessApproved() {
        when(dao.findByCandidateId(CAND_ID)).thenReturn(
                candidateRow(DataGovernanceCandidateService.STATUS_GENERATED, null));

        assertThrows(IllegalStateException.class, () -> service.applyApprovedProposal(CAND_ID));
        verify(security, never()).evaluateDataGovernanceApply(anyString());
        verify(dao, never()).transitionStatus(anyString(), anyString(), anyString(), nullable(String.class),
                anyString());
    }

    // ═══════════════ 状态机（§7.4） ═══════════════

    @Test
    @DisplayName("SUP (状态机): GENERATED 直跳 APPROVED 拒绝 (生成与审批强分离)")
    void generatedCannotJumpDirectlyToApproved() {
        when(dao.findByCandidateId(CAND_ID)).thenReturn(
                candidateRow(DataGovernanceCandidateService.STATUS_GENERATED, null));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.transition(CAND_ID, DataGovernanceCandidateService.STATUS_APPROVED,
                        "approval-9001", "jump"));
        assertTrue(ex.getMessage().contains("APPROVED"), "异常文案须指出 APPROVED 迁移违例");
        verify(dao, never()).transitionStatus(anyString(), anyString(), anyString(), nullable(String.class),
                anyString());
    }

    @Test
    @DisplayName("SUP (状态机): 迁 APPROVED 缺 approvalRef 拒绝; approve 方法同门禁")
    void approveWithoutApprovalRefRejected() {
        when(dao.findByCandidateId(CAND_ID)).thenReturn(
                candidateRow(DataGovernanceCandidateService.STATUS_PENDING_APPROVAL, null));

        assertThrows(IllegalArgumentException.class,
                () -> service.transition(CAND_ID, DataGovernanceCandidateService.STATUS_APPROVED,
                        "  ", "approve"));
        assertThrows(IllegalArgumentException.class,
                () -> service.approve(CAND_ID, "  ", "approver-1"));
        verify(security, never()).evaluateDataGovernanceApply(anyString());
        verify(dao, never()).transitionStatus(anyString(), anyString(), anyString(), nullable(String.class),
                anyString());
    }

    @Test
    @DisplayName("SUP (状态机): 合法链 GENERATED->EVIDENCE_CHECKED->VALIDATED->PENDING_APPROVAL 逐步放行")
    void legalChainProceedsStepByStep() {
        when(dao.findByCandidateId(CAND_ID)).thenReturn(
                candidateRow(DataGovernanceCandidateService.STATUS_GENERATED, null));
        when(dao.transitionStatus(anyString(), anyString(), anyString(), nullable(String.class),
                anyString())).thenReturn(1);

        service.transition(CAND_ID, DataGovernanceCandidateService.STATUS_EVIDENCE_CHECKED,
                null, "evidence checked");

        Map<String, Object> checked = candidateRow(
                DataGovernanceCandidateService.STATUS_EVIDENCE_CHECKED, null);
        when(dao.findByCandidateId(CAND_ID)).thenReturn(checked);
        service.transition(CAND_ID, DataGovernanceCandidateService.STATUS_VALIDATED,
                null, "validated");
    }

    // ═══════════════ §7.2 置信度规则（LLM 自报强制未校准） ═══════════════

    @Test
    @DisplayName("SUP (置信度 §7.2): confidenceLlmText 命中 -> insert 强制 kind=UNCALIBRATED_MODEL_ESTIMATE + calibrated=0")
    void llmConfidenceTextForcesUncalibratedKindAndFalseCalibrated() {
        DataGovernanceCandidateCreateDTO dto = validDqRuleDto();
        dto.setConfidenceLlmText("90% confident");
        when(dao.findByIdempotencyKey("t-1", "idem-001")).thenReturn(null);
        Map<String, Object> created = candidateRow(DataGovernanceCandidateService.STATUS_GENERATED, null);
        when(dao.findByCandidateId(anyString())).thenReturn(created);

        service.createCandidate(dto);

        // ArgumentCaptor 捕获 insert 实参 (24 参位): 置信三元组 = 8/9/10 位
        ArgumentCaptor<String> kindCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> calibratedCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Double> valueCaptor = ArgumentCaptor.forClass(Double.class);
        verify(dao).insert(anyString(), anyString(), nullable(String.class), anyString(), anyString(),
                anyString(), nullable(String.class),
                valueCaptor.capture(), kindCaptor.capture(), calibratedCaptor.capture(),
                nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(Double.class), nullable(String.class), anyString(),
                nullable(String.class), nullable(String.class), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString());
        assertEquals(GovernanceConfidenceResolver.UNCALIBRATED_MODEL_ESTIMATE,
                kindCaptor.getValue(), "LLM 自报置信度一律 UNCALIBRATED_MODEL_ESTIMATE (§7.2)");
        assertEquals(Integer.valueOf(0), calibratedCaptor.getValue(),
                "LLM 自报 calibrated 恒 false (落库 0)");
        assertEquals(Double.valueOf(0.90), valueCaptor.getValue(),
                "90% 自报文本抽取为 0.9 归一值");
    }
}
