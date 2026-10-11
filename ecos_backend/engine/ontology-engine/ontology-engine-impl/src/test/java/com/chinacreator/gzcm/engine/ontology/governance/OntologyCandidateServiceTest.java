package com.chinacreator.gzcm.engine.ontology.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.common.exception.ForbiddenException;
import com.chinacreator.gzcm.engine.ontology.governance.dao.OntologyCandidateDao;
import com.chinacreator.gzcm.engine.ontology.governance.dto.OntologyCandidateCreateDTO;
import com.chinacreator.gzcm.engine.ontology.security.SecurityEngineClient;
import com.chinacreator.gzcm.engine.ontology.service.OntologyProposalService;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;

/**
 * SUP-ONTO-01/02 冻结验收用例 --《docs/30-设计/ECOS-认知域与智能域补充详细设计-v1.0.md》§19.5。
 *
 * <p>SUP-ONTO-01(缺证据/未解冲突禁提提案):ENTITY/PROPERTY/RELATION 候选必须
 * 携带非空 evidenceRefs,缺证据在 Service 层即被拒(IllegalArgumentException 转 400),
 * 不触 DB insert。</p>
 *
 * <p>SUP-ONTO-02(AI 无权直发正式本体):候选侧不存在任何直接 publish / 直写正式
 * 资产(entity/property/relationship 表)的路径;唯一出口 submitProposal 仅
 * 桥接 proposals 逻辑引用(proposal_ref);applyCandidate 仅做候选行自身状态
 * 回执,不落任何正式资产写。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SUP-ONTO-01/02 本体候选治理(OntologyCandidateService)")
class OntologyCandidateServiceTest {

    @Mock
    private OntologyCandidateDao dao;
    @Mock
    private OntologyProposalService proposalService;
    @Mock
    private ObjectProvider<SecurityEngineClient> securityProvider;
    @Mock
    private ObjectProvider<EventBusService> eventBusProvider;
    @Mock
    private SecurityEngineClient securityClient;

    private OntologyCandidateService service;

    @BeforeEach
    void setUp() {
        service = new OntologyCandidateService(dao, proposalService, securityProvider, eventBusProvider);
        // 空 provider(security 缺失 = fail-closed)为基础态;个别用例再覆盖
        lenient().when(securityProvider.getIfAvailable()).thenReturn(null);
        lenient().when(eventBusProvider.getIfAvailable()).thenReturn(null);
    }

    // ── helpers ──

    private static OntologyCandidateCreateDTO baseCreate(String type) {
        OntologyCandidateCreateDTO dto = new OntologyCandidateCreateDTO();
        dto.setCandidateType(type);
        dto.setTargetRef("Ent001");
        dto.setTenantId("t1");
        dto.setCreatedBy("tester");
        return dto;
    }

    /** 装配 findByCandidateId 返回的候选行(状态机用例共用)。 */
    private static Map<String, Object> candidateRow(String status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "c-1");
        row.put("status", status);
        row.put("candidateType", "ENTITY");
        row.put("tenantId", "t1");
        row.put("domain", "default");
        row.put("targetRef", "Ent001");
        row.put("proposalText", "{\"code\":\"Ent001\"}");
        row.put("traceId", "trace-1");
        return row;
    }

    // ── SUP-ONTO-01:证据强制 ──

    @Test
    @DisplayName("SUP-ONTO-01:ENTITY/PROPERTY/RELATION 候选缺证据被拒(IllegalArgumentException),insert 不触发")
    void createEntityPropertyRelationWithoutEvidenceIsRejected() {
        for (String type : List.of("ENTITY", "PROPERTY", "RELATION")) {
            OntologyCandidateCreateDTO dto = baseCreate(type);
            dto.setEvidenceRefs(new ArrayList<>()); // 空证据
            dto.setSubjectType("A");
            dto.setPredicate("hasRel");
            dto.setObjectType("B");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.createCandidate(dto),
                    type + " 缺证据应被拒");
            assertTrue(ex.getMessage().contains("evidenceRefs"),
                    "异常文案应说明 evidenceRefs 必填: " + ex.getMessage());
        }
        // 三类被拒均不落库
        verify(dao, never()).insert(anyString(), anyString(), nullable(String.class),
                anyString(), nullable(String.class), nullable(String.class), nullable(String.class),
                anyString(), nullable(String.class), nullable(String.class),
                nullable(Double.class), nullable(String.class), nullable(Integer.class),
                nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(String.class), anyString(), nullable(String.class), nullable(String.class),
                nullable(String.class), nullable(String.class), anyString(), anyString(),
                nullable(String.class), anyString(), anyString());
    }

    @Test
    @DisplayName("SUP-ONTO-01:RELATION 候选带证据通过,insert 一次且返回状态 GENERATED")
    void createRelationWithEvidenceSucceedsGenerated() {
        OntologyCandidateCreateDTO dto = baseCreate("RELATION");
        dto.setSubjectType("A");
        dto.setPredicate("hasRel");
        dto.setObjectType("B");
        dto.setEvidenceRefs(List.of("profile-001#doc1"));

        Map<String, Object> newRow = candidateRow(OntologyCandidateService.STATUS_GENERATED);
        when(dao.findByCandidateId(anyString())).thenReturn(newRow);
        when(dao.insert(anyString(), anyString(), nullable(String.class), eq("RELATION"),
                eq("A"), eq("hasRel"), eq("B"), eq("Ent001"), nullable(String.class),
                anyString(), nullable(Double.class), nullable(String.class), nullable(Integer.class),
                nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(String.class), eq(OntologyCandidateService.STATUS_GENERATED),
                nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(String.class), anyString(), nullable(String.class),
                eq("default"), eq("v1"), eq("tester"))).thenReturn(1);

        Map<String, Object> out = service.createCandidate(dto);

        verify(dao, times(1)).insert(anyString(), anyString(), nullable(String.class),
                eq("RELATION"), anyString(), anyString(), anyString(), anyString(),
                nullable(String.class), anyString(), nullable(Double.class),
                nullable(String.class), nullable(Integer.class), nullable(String.class),
                nullable(String.class), nullable(String.class), nullable(String.class),
                eq(OntologyCandidateService.STATUS_GENERATED), nullable(String.class),
                nullable(String.class), nullable(String.class), nullable(String.class),
                anyString(), nullable(String.class), eq("default"), eq("v1"), eq("tester"));
        assertNotNull(out, "create 应返回落库行");
        assertEquals(OntologyCandidateService.STATUS_GENERATED, out.get("status"));
    }

    // ── SUP-ONTO-02:无直发正式资产路径 ──

    @Test
    @DisplayName("SUP-ONTO-02:apply 仅候选行状态回执,不触任何正式资产 DAO 写;proposalService 零交互")
    void applyCandidateTouchesOnlyCandidateRowAndNeverProposalService() {
        Map<String, Object> row = candidateRow(OntologyCandidateService.STATUS_APPROVED);
        row.put("approvalRef", "appr-1");
        when(dao.findByCandidateId("c-1")).thenReturn(row);
        when(securityProvider.getIfAvailable()).thenReturn(securityClient);
        when(securityClient.evaluatePolicy(eq("ontology.candidate.apply"), anyMap())).thenReturn(true);
        when(dao.transitionStatus(eq("c-1"),
                eq(OntologyCandidateService.STATUS_APPROVED),
                eq(OntologyCandidateService.STATUS_APPLIED),
                eq("appr-1"), anyString())).thenReturn(1);

        Map<String, Object> out = service.applyCandidate("c-1");

        // 仅候选行 CAS 迁移 + 回读;读源(只读 entity/property/relation)也未被调
        verify(dao).transitionStatus(eq("c-1"),
                eq(OntologyCandidateService.STATUS_APPROVED),
                eq(OntologyCandidateService.STATUS_APPLIED), eq("appr-1"), anyString());
        verify(dao, never()).insert(anyString(), anyString(), nullable(String.class),
                anyString(), nullable(String.class), nullable(String.class), nullable(String.class),
                anyString(), nullable(String.class), nullable(String.class),
                nullable(Double.class), nullable(String.class), nullable(Integer.class),
                nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(String.class), anyString(), nullable(String.class), nullable(String.class),
                nullable(String.class), nullable(String.class), anyString(), anyString(),
                nullable(String.class), anyString(), anyString());
        verify(dao, never()).updateValidationText(anyString(), nullable(String.class));
        verify(dao, never()).updateDiffPreviewText(anyString(), nullable(String.class));
        verify(dao, never()).updateProposalRef(anyString(), anyString());
        // 正式资产变更只能由 proposals 审批链驱动:候选侧零 proposal 交互
        verifyNoMoreInteractions(proposalService);
        assertSame(row, out, "apply 应返回最新候选行");
        assertEquals("c-1", out.get("id"));
    }

    @Test
    @DisplayName("SUP-ONTO-02:submitProposal 是调用 proposalService 的唯一路径(insertProposal 一次且仅一次)")
    void submitProposalIsTheOnlyPathTouchingProposalService() {
        Map<String, Object> row = candidateRow(OntologyCandidateService.STATUS_VALIDATED);
        when(dao.findByCandidateId("c-1")).thenReturn(row);
        when(proposalService.insertProposal(eq("default"), eq("ONTOLOGY_CANDIDATE"),
                eq("Ent001"), anyString(), anyString(),
                eq("DRAFT"), anyString())).thenReturn("prop-1");
        when(dao.transitionStatus(eq("c-1"),
                eq(OntologyCandidateService.STATUS_VALIDATED),
                eq(OntologyCandidateService.STATUS_PENDING_APPROVAL),
                nullable(String.class), anyString())).thenReturn(1);
        when(dao.updateProposalRef(eq("c-1"), eq("prop-1"))).thenReturn(1);

        Map<String, Object> out = service.submitProposal("c-1");

        // 唯一桥接出口:恰好一次 insertProposal
        verify(proposalService, times(1)).insertProposal(eq("default"),
                eq("ONTOLOGY_CANDIDATE"), eq("Ent001"), anyString(), anyString(),
                eq("DRAFT"), anyString());
        verifyNoMoreInteractions(proposalService);
        // 回填 proposal_ref + CAS 迁 PENDING_APPROVAL
        verify(dao).updateProposalRef("c-1", "prop-1");
        verify(dao).transitionStatus(eq("c-1"),
                eq(OntologyCandidateService.STATUS_VALIDATED),
                eq(OntologyCandidateService.STATUS_PENDING_APPROVAL),
                nullable(String.class), anyString());
        assertEquals(OntologyCandidateService.STATUS_PENDING_APPROVAL, out.get("status"));
        assertEquals("prop-1", out.get("proposalId"));
    }

    // ── 幂等创建 ──

    @Test
    @DisplayName("幂等:同 idempotencyKey 预读命中既有候选,直接返回,insert 不触发")
    void createWithSameIdempotencyKeyReturnsExistingWithoutInsert() {
        OntologyCandidateCreateDTO dto = baseCreate("ENTITY");
        dto.setEvidenceRefs(List.of("profile-001#doc1"));
        dto.setIdempotencyKey("idem-1");

        Map<String, Object> existing = candidateRow(OntologyCandidateService.STATUS_VALIDATED);
        existing.put("idempotencyKey", "idem-1");
        when(dao.findByIdempotencyKey("t1", "idem-1")).thenReturn(existing);

        Map<String, Object> out = service.createCandidate(dto);

        assertSame(existing, out, "幂等命中应原样返回既有候选行");
        verify(dao, never()).insert(anyString(), anyString(), nullable(String.class),
                anyString(), nullable(String.class), nullable(String.class), nullable(String.class),
                anyString(), nullable(String.class), nullable(String.class),
                nullable(Double.class), nullable(String.class), nullable(Integer.class),
                nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(String.class), anyString(), nullable(String.class), nullable(String.class),
                nullable(String.class), nullable(String.class), anyString(), anyString(),
                nullable(String.class), anyString(), anyString());
        verify(dao, never()).findByCandidateId(anyString());
    }

    // ── submit-proposal 门槛 ──

    @Test
    @DisplayName("submit-proposal 门槛:非 VALIDATED 候选被拒(IllegalStateException),不触 proposalService")
    void submitProposalOnNonValidatedCandidateIsRejected() {
        for (String status : List.of(OntologyCandidateService.STATUS_GENERATED,
                OntologyCandidateService.STATUS_PENDING_APPROVAL)) {
            Map<String, Object> row = candidateRow(status);
            when(dao.findByCandidateId("c-1")).thenReturn(row);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.submitProposal("c-1"),
                    status + " 态提提案应被拒");
            assertTrue(ex.getMessage().contains("VALIDATED"),
                    "文案应说明仅 VALIDATED 可提: " + ex.getMessage());
        }
        verify(proposalService, never()).insertProposal(anyString(), anyString(),
                nullable(String.class), anyString(), anyString(), anyString(), anyString());
        verify(dao, never()).updateProposalRef(anyString(), anyString());
        verify(dao, never()).transitionStatus(anyString(), anyString(), anyString(),
                nullable(String.class), anyString());
    }

    // ── 状态机抽查 ──

    @Test
    @DisplayName("状态机:GENERATED 直跳 APPROVED 是非法迁移,被拒且不触 DB")
    void directGeneratedToApprovedTransitionIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> OntologyCandidateService.validateTransition(
                        OntologyCandidateService.STATUS_GENERATED,
                        OntologyCandidateService.STATUS_APPROVED),
                "GENERATED→APPROVED 应判非法");
        assertThrows(IllegalArgumentException.class,
                () -> OntologyCandidateService.validateTransition(
                        OntologyCandidateService.STATUS_VALIDATED,
                        OntologyCandidateService.STATUS_APPROVED),
                "VALIDATED→APPROVED 应判非法");

        // 端到端佐证:GENERATED 候选走通用 transition 迁 APPROVED 同样被拒
        Map<String, Object> row = candidateRow(OntologyCandidateService.STATUS_GENERATED);
        when(dao.findByCandidateId("c-1")).thenReturn(row);
        assertThrows(IllegalArgumentException.class,
                () -> service.transition("c-1",
                        OntologyCandidateService.STATUS_APPROVED, null, "jump"),
                "生成的候选不得当审批通过");
        verify(dao, never()).transitionStatus(anyString(), anyString(), anyString(),
                nullable(String.class), anyString());
    }

    @Test
    @DisplayName("状态机:非 PENDING_APPROVAL 候选 approve 被拒(IllegalStateException)")
    void approveOnNonPendingCandidateIsRejected() {
        Map<String, Object> row = candidateRow(OntologyCandidateService.STATUS_GENERATED);
        when(dao.findByCandidateId("c-1")).thenReturn(row);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.approve("c-1", "appr-1", "approver"),
                "GENERATED 候选不得直接审批");
        assertTrue(ex.getMessage().contains("PENDING_APPROVAL"),
                "文案应说明仅 PENDING_APPROVAL 可审批: " + ex.getMessage());
        verify(dao, never()).transitionStatus(anyString(), anyString(), anyString(),
                nullable(String.class), anyString());
        verify(dao, never()).insert(anyString(), anyString(), nullable(String.class),
                anyString(), nullable(String.class), nullable(String.class), nullable(String.class),
                anyString(), nullable(String.class), nullable(String.class),
                nullable(Double.class), nullable(String.class), nullable(Integer.class),
                nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(String.class), anyString(), nullable(String.class), nullable(String.class),
                nullable(String.class), nullable(String.class), anyString(), anyString(),
                nullable(String.class), anyString(), anyString());
    }

    @Test
    @DisplayName("状态机:approve 时 security 裁决 DENY → ForbiddenException(403),状态不迁 APPROVED")
    void approveDeniedBySecurityThrowsForbiddenAndDoesNotTransition() {
        Map<String, Object> row = candidateRow(OntologyCandidateService.STATUS_PENDING_APPROVAL);
        when(dao.findByCandidateId("c-1")).thenReturn(row);
        when(securityProvider.getIfAvailable()).thenReturn(securityClient);
        when(securityClient.evaluatePolicy(eq("ontology.candidate.approve"), anyMap()))
                .thenReturn(false);

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> service.approve("c-1", "appr-1", "approver"),
                "security DENY 应 fail-closed 抛 ForbiddenException");
        assertTrue(ex.getMessage().contains("DENY"),
                "文案应含 DENY 语义: " + ex.getMessage());

        // 未迁 APPROVED:没有任何 transitionStatus 调用
        verify(dao, never()).transitionStatus(anyString(), anyString(),
                eq(OntologyCandidateService.STATUS_APPROVED), anyString(), anyString());
        // 事件总线未装配 → 无事件副作用
        verify(eventBusProvider, times(1)).getIfAvailable();
    }

    @Test
    @DisplayName("状态机:security 放行时 approve 成功(PENDING_APPROVAL→APPROVED 且 approvalRef 回填)")
    void approveSucceedsWhenSecurityAllows() {
        Map<String, Object> row = candidateRow(OntologyCandidateService.STATUS_PENDING_APPROVAL);
        when(dao.findByCandidateId("c-1")).thenReturn(row);
        when(securityProvider.getIfAvailable()).thenReturn(securityClient);
        when(securityClient.evaluatePolicy(eq("ontology.candidate.approve"), anyMap()))
                .thenReturn(true);
        when(dao.transitionStatus(eq("c-1"),
                eq(OntologyCandidateService.STATUS_PENDING_APPROVAL),
                eq(OntologyCandidateService.STATUS_APPROVED),
                eq("appr-1"), anyString())).thenReturn(1);

        Map<String, Object> out = service.approve("c-1", "appr-1", "approver");

        assertEquals(OntologyCandidateService.STATUS_PENDING_APPROVAL, row.get("status"));
        verify(dao).transitionStatus(eq("c-1"),
                eq(OntologyCandidateService.STATUS_PENDING_APPROVAL),
                eq(OntologyCandidateService.STATUS_APPROVED),
                eq("appr-1"), anyString());
        assertNotNull(out);
        verify(securityClient).audit(eq("ontology.candidate.approve"),
                org.mockito.ArgumentMatchers.any());
    }

    // ── 置信度规则 ──

    @Test
    @DisplayName("置信度:confidenceLlmText 非空 → kind=UNCALIBRATED_MODEL_ESTIMATE 且 calibrated=0(INSERT 参数原文)")
    void createWithLlmConfidenceTextForcesUncalibratedKindAndZeroCalibrated() {
        OntologyCandidateCreateDTO dto = baseCreate("ENTITY");
        dto.setEvidenceRefs(List.of("profile-001#doc1"));
        dto.setConfidenceLlmText("90% sure this entity exists");

        when(dao.findByCandidateId(anyString())).thenReturn(candidateRow(
                OntologyCandidateService.STATUS_GENERATED));
        when(dao.insert(anyString(), anyString(), nullable(String.class), eq("ENTITY"),
                nullable(String.class), nullable(String.class), nullable(String.class),
                eq("Ent001"), nullable(String.class), anyString(),
                any(Double.class), eq("UNCALIBRATED_MODEL_ESTIMATE"), eq(0),
                nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(String.class), eq(OntologyCandidateService.STATUS_GENERATED),
                nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(String.class), anyString(), nullable(String.class),
                eq("default"), eq("v1"), eq("tester"))).thenReturn(1);

        service.createCandidate(dto);

        ArgumentCaptor<Double> valueCap = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Integer> calCap = ArgumentCaptor.forClass(Integer.class);
        verify(dao).insert(anyString(), anyString(), nullable(String.class), eq("ENTITY"),
                nullable(String.class), nullable(String.class), nullable(String.class),
                eq("Ent001"), nullable(String.class), anyString(),
                valueCap.capture(), eq("UNCALIBRATED_MODEL_ESTIMATE"), calCap.capture(),
                nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(String.class), eq(OntologyCandidateService.STATUS_GENERATED),
                nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(String.class), anyString(), nullable(String.class),
                eq("default"), eq("v1"), eq("tester"));
        assertEquals(0.9, valueCap.getValue(),
                "LlmText \"90% ...\" 应解析为 0.9");
        assertEquals(Integer.valueOf(0), calCap.getValue(),
                "LLM 自报置信度 calibrated 必须落 0");
    }
}
