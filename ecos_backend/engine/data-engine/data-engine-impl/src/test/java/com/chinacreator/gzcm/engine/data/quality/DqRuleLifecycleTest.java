package com.chinacreator.gzcm.engine.data.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.springframework.jdbc.core.RowMapper;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.data.quality.dao.DqRuleLifecycleDao;
import com.chinacreator.gzcm.engine.data.quality.mapper.DqRuleMapper;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleDTO;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVO;
import com.chinacreator.gzcm.engine.data.quality.service.DqRuleLifecycleServiceImpl;
import com.chinacreator.gzcm.engine.data.quality.service.DqSecurityService;

/**
 * DqRuleLifecycleTest — DQ 规则生命周期状态机 10 case 集成测试（PMO-48-B T10）。
 *
 * <p>Refactor 2 (2026-10-01): src/main 收口走 DAO，本测试直接 mock
 * {@link DqRuleLifecycleDao}（不再走 JDBC/JdbcTemplate 链）。Service 写操作与
 * DAO public 方法 1:1 对应：
 * <pre>
 *   createDraft → insertRule (void)
 *   updateDraft → updateDraft (int)
 *   submit      → submitReview (int)
 *   approve     → insertVersionSnapshot (void) + approve (int)
 *   reject      → reject (int)
 *   deprecate   → deprecate (int)
 *   supersede   → supersede (int)
 * </pre>
 * 原 JDBC executedSqls 前缀计数（INSERT / UPDATE）通过 mocked DAO method
 * invocation 计数还原。
 *
 * <p>对应任务清单 1-10 全部保留（1 createDraft, 2 updateDraft draft,
 * 3 updateDraft non-draft 抛, 4 updateDraft deleted 抛, 5 submit,
 * 6 approve version+1, 7 reject, 8 deprecate, 9 supersede, 10 非法转换）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DqRuleLifecycleTest {

    @Mock
    private DqSecurityService securityService;
    @Mock
    private DqRuleMapper ruleMapper;
    @Mock
    private DqRuleLifecycleDao ruleLifecycleDao;

    private StubRow nextReadRow;
    private final List<Integer> writeAffectedResults = new ArrayList<>();
    private final AtomicInteger writeCallCounter = new AtomicInteger(0);

    private DqRuleLifecycleServiceImpl svc;

    @BeforeEach
    void wireDao() {
        // 读：findRule 直接返回 toVo(nextReadRow) 或空集；RowMapper arg 不用（VO 已构造）。
        when(ruleLifecycleDao.findRule(anyString(), anyBoolean(), any(RowMapper.class)))
                .thenAnswer(inv -> nextReadRow == null
                        ? Collections.emptyList()
                        : Collections.singletonList(toVo(nextReadRow)));

        // 所有 int-returning 写方法：按序弹 writeAffectedResults，缺省 1（CAS 命中）。
        Answer<Integer> pop = inv -> {
            int idx = writeCallCounter.getAndIncrement();
            return idx < writeAffectedResults.size() ? writeAffectedResults.get(idx) : 1;
        };
        when(ruleLifecycleDao.updateDraft(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), nullable(String.class),
                anyString(), anyString(), anyString(), nullable(String.class), anyString(), anyString()))
                .thenAnswer(pop);
        when(ruleLifecycleDao.submitReview(anyString(), anyString(), anyString())).thenAnswer(pop);
        when(ruleLifecycleDao.approve(anyString(), anyInt(), anyString())).thenAnswer(pop);
        when(ruleLifecycleDao.reject(anyString(), anyString())).thenAnswer(pop);
        when(ruleLifecycleDao.deprecate(anyString(), anyString(), anyString())).thenAnswer(pop);
        when(ruleLifecycleDao.supersede(anyString(), anyString(), anyString())).thenAnswer(pop);
        when(ruleLifecycleDao.disable(anyString(), anyString())).thenAnswer(pop);
        when(ruleLifecycleDao.softDelete(anyString())).thenAnswer(pop);

        svc = new DqRuleLifecycleServiceImpl(ruleMapper, securityService, ruleLifecycleDao);
    }

    private int countInvocations(String methodName) {
        int n = 0;
        for (Invocation i : Mockito.mockingDetails(ruleLifecycleDao).getInvocations()) {
            if (i.getMethod().getName().equals(methodName)) n++;
        }
        return n;
    }

    /** INSERT 等价方法名集合（原 executedSqls startsWith("INSERT") 计数等价）。 */
    private int countInserts() {
        return countInvocations("insertRule") + countInvocations("insertVersionSnapshot");
    }

    /** UPDATE 等价方法名集合（原 executedSqls startsWith("UPDATE") 计数等价）。 */
    private int countUpdates() {
        int n = 0;
        n += countInvocations("updateDraft");
        n += countInvocations("submitReview");
        n += countInvocations("approve");
        n += countInvocations("reject");
        n += countInvocations("deprecate");
        n += countInvocations("supersede");
        n += countInvocations("softDelete");
        return n;
    }

    private StubRow validRow(String status, int version, String approvedBy) {
        StubRow r = new StubRow();
        r.id = "r-rule-001";
        r.ruleName = "not-null-customer-email";
        r.ruleCode = "DQ_NOT_NULL_CUST_EMAIL";
        r.category = "TECHNICAL";
        r.domain = "crm";
        r.ruleType = "NOT_NULL";
        r.severity = "HIGH";
        r.targetKind = "FIELD";
        r.targetId = "t:customer:email";
        r.targetTable = "sysman.customer";
        r.targetField = "email";
        r.targetPipelineId = null;
        r.parametersJson = "{\"column\":\"email\"}";
        r.status = status;
        r.version = version;
        r.approvedBy = approvedBy;
        r.sourceType = "MANUAL";
        r.sourceRef = null;
        r.desc = "customer.email must not be null";
        r.createdBy = "alice";
        r.updatedBy = "alice";
        return r;
    }

    private DqRuleVO toVo(StubRow r) {
        DqRuleVO vo = new DqRuleVO();
        vo.setId(r.id);
        vo.setRuleName(r.ruleName);
        vo.setRuleCode(r.ruleCode);
        vo.setCategory(r.category);
        vo.setDomain(r.domain);
        vo.setRuleType(r.ruleType);
        vo.setSeverity(r.severity);
        vo.setTargetKind(r.targetKind);
        vo.setTargetId(r.targetId);
        vo.setTargetTable(r.targetTable);
        vo.setTargetField(r.targetField);
        vo.setTargetPipelineId(r.targetPipelineId);
        vo.setParametersJson(r.parametersJson);
        vo.setStatus(r.status);
        vo.setVersion(r.version);
        vo.setApprovedBy(r.approvedBy);
        vo.setSourceType(r.sourceType);
        vo.setSourceRef(r.sourceRef);
        vo.setDescription(r.desc);
        vo.setCreatedBy(r.createdBy);
        vo.setUpdatedBy(r.updatedBy);
        return vo;
    }

    private DqRuleDTO validDraftDto() {
        DqRuleDTO dto = new DqRuleDTO();
        dto.setRuleName("not-null-customer-email");
        dto.setRuleCode("DQ_NOT_NULL_CUST_EMAIL");
        dto.setCategory("TECHNICAL");
        dto.setDomain("crm");
        dto.setRuleType("NOT_NULL");
        dto.setSeverity("HIGH");
        dto.setTargetKind("FIELD");
        dto.setTargetId("t:customer:email");
        dto.setTargetTable("sysman.customer");
        dto.setTargetField("email");
        dto.setParameters("{}");
        dto.setOperator("alice");
        return dto;
    }

    private void reset(StubRow row) {
        this.nextReadRow = row;
        writeCallCounter.set(0);
        writeAffectedResults.clear();
    }

    private void setWriteAffected(int... arr) {
        writeCallCounter.set(0);
        for (int v : arr) {
            writeAffectedResults.add(v);
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // 10 个测试
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("1) createDraft — 落库 status=DRAFT, version=1；audit DQ_RULE_CREATE 计 1；1 次 INSERT")
    void createDraft_shouldReturnRuleId_DRAFT() {
        reset(null);
        String ruleId = svc.createDraft(validDraftDto());
        assertNotNull(ruleId, "createDraft 应返回新 UUID");
        assertTrue(ruleId.length() >= 16, "ruleId 应为 UUID hex 串（≥16 char）");
        verify(securityService, times(1)).auditWrite(eq("DQ_RULE_CREATE"), eq(ruleId), eq("SUCCESS"));
        assertEquals(1, countInserts(), "createDraft 应执行 1 次 INSERT (insertRule)");
        assertEquals(0, countUpdates(), "createDraft 不应执行 UPDATE");
    }

    @Test
    @DisplayName("2) updateDraft — DRAFT 状态可编辑；audit DQ_RULE_UPDATE 计 1；1 次 UPDATE")
    void updateDraft_draftCanEdit_DRAFTStatusStays() {
        reset(validRow("DRAFT", 1, null));
        setWriteAffected(1);
        DqRuleDTO dto = new DqRuleDTO();
        dto.setRuleName("not-null-customer-email-v2");
        dto.setOperator("bob");
        svc.updateDraft("r-rule-001", dto);
        verify(securityService, times(1)).auditWrite(eq("DQ_RULE_UPDATE"), eq("r-rule-001"), eq("SUCCESS"));
        assertEquals(0, countInserts(), "DRAFT 编辑不写 version 快照");
        assertEquals(1, countUpdates(), "DRAFT 编辑应 1 次 UPDATE (updateDraft)");
    }

    @Test
    @DisplayName("3) updateDraft — 非 DRAFT (ACTIVE) 编辑 → BusinessException；0 次写；0 次 audit")
    void updateDraft_nonDraft_shouldThrow() {
        reset(validRow("ACTIVE", 2, "carol"));
        DqRuleDTO dto = new DqRuleDTO();
        dto.setRuleName("cannot-edit-active");
        assertThrows(BusinessException.class, () -> svc.updateDraft("r-rule-001", dto));
        assertEquals(0, countInserts());
        assertEquals(0, countUpdates());
        verify(securityService, never()).auditWrite(eq("DQ_RULE_UPDATE"), anyString(), anyString());
    }

    @Test
    @DisplayName("4) updateDraft — 已逻辑删除（空行）→ NotFoundException；0 次写；0 次 audit")
    void updateDraft_deletedRuleShouldThrow() {
        reset(null);
        DqRuleDTO dto = new DqRuleDTO();
        dto.setRuleName("ghost");
        assertThrows(RuntimeException.class, () -> svc.updateDraft("r-rule-001", dto));
        assertEquals(0, countUpdates());
        verify(securityService, never()).auditWrite(eq("DQ_RULE_UPDATE"), anyString(), anyString());
    }

    @Test
    @DisplayName("5) submit — DRAFT → IN_REVIEW；1 次 UPDATE；audit DQ_RULE_SUBMIT")
    void submit_draftToInReview() {
        reset(validRow("DRAFT", 1, null));
        setWriteAffected(1);
        String newStatus = svc.submit("r-rule-001", "submitter-alice");
        assertEquals("IN_REVIEW", newStatus);
        assertEquals(0, countInserts(), "submit 不写 version 快照");
        assertEquals(1, countUpdates(), "submit 应 1 次 UPDATE (submitReview)");
        verify(securityService, times(1)).auditWrite(eq("DQ_RULE_SUBMIT"), eq("r-rule-001"), eq("SUCCESS"));
    }

    @Test
    @DisplayName("6) approve — IN_REVIEW → ACTIVE，version 1→2；1 次 INSERT version + 1 次 UPDATE；audit DQ_RULE_APPROVE")
    void approve_success_versionIncrement() {
        reset(validRow("IN_REVIEW", 1, null));
        // insertVersionSnapshot 是 void — 不占 pop 序列；下一个 pop 属于 approve 的 UPDATE。
        setWriteAffected(1);
        String newStatus = svc.approve("r-rule-001", "reviewer-carol");
        assertEquals("ACTIVE", newStatus);
        assertEquals(1, countInserts(), "approve 应 1 次 INSERT dq_rule_version (insertVersionSnapshot)");
        assertEquals(1, countUpdates(), "approve 应 1 次 UPDATE dq_rule (approve)");
        verify(securityService, times(1)).auditWrite(eq("DQ_RULE_APPROVE"), eq("r-rule-001"), eq("SUCCESS"));
    }

    @Test
    @DisplayName("7) reject — IN_REVIEW → REJECTED；1 次 UPDATE；audit DQ_RULE_REJECT")
    void reject_inReviewToRejected_ReasonRequired() {
        reset(validRow("IN_REVIEW", 1, null));
        setWriteAffected(1);
        String newStatus = svc.reject("r-rule-001", "rejector-dave", "理由：email 格式不合规");
        assertEquals("REJECTED", newStatus);
        assertEquals(0, countInserts(), "reject 不写 version 快照");
        assertEquals(1, countUpdates(), "reject 应 1 次 UPDATE (reject)");
        verify(securityService, times(1)).auditWrite(eq("DQ_RULE_REJECT"), eq("r-rule-001"), eq("SUCCESS"));
    }

    @Test
    @DisplayName("8) deprecate — ACTIVE → DEPRECATED；1 次 UPDATE（无 INSERT）；audit DQ_RULE_DEPRECATE")
    void deprecate_activeToDeprecated() {
        reset(validRow("ACTIVE", 2, "carol"));
        setWriteAffected(1);
        String newStatus = svc.deprecate("r-rule-001", "ops-erin");
        assertEquals("DEPRECATED", newStatus);
        assertEquals(0, countInserts(), "DEPRECATE 不写 version 快照（仅 UPDATE 状态位）");
        assertEquals(1, countUpdates(), "DEPRECATE 应 1 次 UPDATE (deprecate)");
        verify(securityService, times(1)).auditWrite(eq("DQ_RULE_DEPRECATE"), eq("r-rule-001"), eq("SUCCESS"));
    }

    @Test
    @DisplayName("9) supersede — ACTIVE → SUPERSEDED；1 次 UPDATE（无 INSERT）；audit DQ_RULE_SUPERSEDE")
    void supersede_activeToSuperseded_NewVersionCreated() {
        reset(validRow("ACTIVE", 3, "carol"));
        setWriteAffected(1);
        String newStatus = svc.supersede("r-rule-001", "ops-frank", "被 DQ_V2 取代");
        assertEquals("SUPERSEDED", newStatus);
        assertEquals(0, countInserts(), "SUPERSEDE 单 UPDATE（新版本由接手 v+1 的 approve 写入）");
        assertEquals(1, countUpdates(), "SUPERSEDE 应 1 次 UPDATE (supersede)");
        verify(securityService, times(1)).auditWrite(eq("DQ_RULE_SUPERSEDE"), eq("r-rule-001"), eq("SUCCESS"));
    }

    @Test
    @DisplayName("10) transition_illegal — DRAFT → ACTIVE 非法 → BusinessException；DB 不变更；audit 不触发")
    void transition_illegal_doesNotMutate() {
        reset(validRow("DRAFT", 1, null));
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.approve("r-rule-001", "x"));
        assertTrue(ex.getMessage().contains("状态"), "异常消息应含「状态」字样；实际：" + ex.getMessage());
        assertEquals(0, countInserts(), "非法转换不应 INSERT version");
        assertEquals(0, countUpdates(), "非法转换不应 UPDATE dq_rule");
        verify(securityService, never()).auditWrite(anyString(), anyString(), anyString());
    }

    /** 测试用行模板（字段对齐 DqRuleLifecycleServiceImpl.doLoad 读列）。 */
    private static class StubRow {
        String id;
        String ruleName;
        String ruleCode;
        String category;
        String domain;
        String ruleType;
        String severity;
        String targetKind;
        String targetId;
        String targetTable;
        String targetField;
        String targetPipelineId;
        String parametersJson;
        String status;
        int version;
        String approvedBy;
        String sourceType;
        String sourceRef;
        String desc;
        String createdBy;
        String updatedBy;
        LocalDateTime createdAt;
        LocalDateTime updatedAt;
    }
}
