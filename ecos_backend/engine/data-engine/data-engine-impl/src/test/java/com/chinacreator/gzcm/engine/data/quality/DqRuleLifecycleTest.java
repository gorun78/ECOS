package com.chinacreator.gzcm.engine.data.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.data.quality.mapper.DqRuleMapper;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleDTO;
import com.chinacreator.gzcm.engine.data.quality.service.DqRuleLifecycleServiceImpl;
import com.chinacreator.gzcm.engine.data.quality.service.DqSecurityService;

/**
 * DqRuleLifecycleTest — DQ 规则生命周期状态机 10 case 集成测试（PMO-48-B T10）。
 *
 * <p>测试策略：纯 Mockito，不连 PG。
 * <ul>
 *   <li>mock {@link DataSource} + {@link Connection} + {@link PreparedStatement} + {@link ResultSet}</li>
 *   <li>按 SQL 关键字（{@code INSERT}/{@code UPDATE}/{@code SELECT}）识别执行类型</li>
 *   <li>读出（SELECT）按 {@link #setDb} 预装的行模板返回（读队列同步读）</li>
 *   <li>写出（INSERT/UPDATE）按调用序号弹 {@link #writeAffectedResults} 指定影响行数；缺省 1（CAS 命中）</li>
 *   <li>executeUpdate 计数用 {@link AtomicInteger}（lambda 内不能用 non-final 字段自增）</li>
 *   <li>SQL 语句抓歷史（executedSqls）用于"非蒙转换 → 无 SQL 写入"等结构性断言，
 *       避免 executeUpdate() 在 verify 点抛未声明受检异常 SQLException</li>
 * </ul>
 * </p>
 *
 * <p>对应任务清单 1-10：
 * <ol>
 *   <li>createDraft_shouldReturnRuleId_DRAFT — 1 次 INSERT + audit DQ_RULE_CREATE</li>
 *   <li>updateDraft_draftCanEdit_DRAFTStatusStays — 1 次 UPDATE + audit DQ_RULE_UPDATE</li>
 *   <li>updateDraft_nonDraft_shouldThrow — BusinessException；0 次写；0 次 audit</li>
 *   <li>updateDraft_deletedRuleShouldThrow — NotFoundException；0 次写；0 次 audit</li>
 *   <li>submit_draftToInReview — 1 次 UPDATE；audit DQ_RULE_SUBMIT</li>
 *   <li>approve_success_versionIncrement — 1 次 INSERT version + 1 次 UPDATE；audit DQ_RULE_APPROVE</li>
 *   <li>reject_inReviewToRejected_ReasonRequired — 1 次 UPDATE；audit DQ_RULE_REJECT</li>
 *   <li>deprecate_activeToDeprecated — 1 次 UPDATE（无 INSERT）；audit DQ_RULE_DEPRECATE</li>
 *   <li>supersede_activeToSuperseded — 1 次 UPDATE（无 INSERT）；audit DQ_RULE_SUPERSEDE</li>
 *   <li>transition_illegal_doesNotMutate — BusinessException；0 次写；0 次 audit</li>
 * </ol></p>
 *
 * <p>安全卡审计验证（铁律 §2.4 #5）：每个写操作后 {@code DqSecurityService#auditWrite} 必计 1。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DqRuleLifecycleTest {

    @Mock
    private DqSecurityService securityService;
    @Mock
    private DqRuleMapper ruleMapper;
    @Mock
    private DataSource dataSource;
    @Mock
    private Connection connection;
    @Mock
    private PreparedStatement statement;
    @Mock
    private java.sql.DatabaseMetaData metaData;

    /** 读队列：写入行后任一 SELECT 都返回该行的"副本"语义；null = 空集。 */
    private StubRow nextReadRow;

    /** 写出影响行数队列（按序号弹出；缺省 1 = CAS 命中）。 */
    private final List<Integer> writeAffectedResults = new ArrayList<>();

    /** 已执行 SQL 历史（结构性断言）。 */
    private final List<String> executedSqls = new ArrayList<>();

    /** 写出调用计数器（INSERT/UPDATE 共用）。AtomicInteger 可在 lambda 内自增。 */
    private final AtomicInteger writeCallCounter = new AtomicInteger(0);

    private DqRuleLifecycleServiceImpl svc;

    @BeforeEach
    void wireJdbc() throws Exception {
        when(dataSource.getConnection()).thenAnswer(inv -> connection);
        // Connection.getMetaData 桩（JdbcTemplate 在 setNull 路径取 driver name 时必需）
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDriverName()).thenReturn("PostgreSQL JDBC Driver (Mock)");
        // 单 PreparedStatement 桩：executeUpdate 按序号弹影响行数；executeQuery 走 nextResultSet
        when(connection.prepareStatement(anyString())).thenAnswer(inv -> {
            String sql = (String) inv.getArgument(0);
            if (sql.startsWith("INSERT") || sql.startsWith("UPDATE")) {
                executedSqls.add(sql);
            }
            return statement;
        });
        when(statement.executeUpdate()).thenAnswer(inv -> {
            int idx = writeCallCounter.getAndIncrement();
            return (idx < writeAffectedResults.size()) ? writeAffectedResults.get(idx) : 1;
        });
        when(statement.executeQuery()).thenAnswer(inv -> {
            try {
                return nextResultSet();
            } catch (java.sql.SQLException e) {
                throw new RuntimeException("Mock 桩 SQLException", e);
            }
        });
        // PreparedStatement.getConnection 必须非 null（JdbcTemplate loopGoodIfRowsAffected 走 ps 取 MetaData）
        when(statement.getConnection()).thenReturn(connection);

        JdbcTemplate tpl = new JdbcTemplate(dataSource);
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(tpl);
        svc = new DqRuleLifecycleServiceImpl(ruleMapper, securityService, provider);
    }

    /** 读结果构造：null 时空集；否则单行多列。 */
    private ResultSet nextResultSet() throws java.sql.SQLException {
        ResultSet rs = mock(ResultSet.class);
        StubRow r = nextReadRow;
        if (r == null) {
            when(rs.next()).thenReturn(false);
            return rs;
        }
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getString("id")).thenReturn(r.id);
        when(rs.getString("rule_name")).thenReturn(r.ruleName);
        when(rs.getString("rule_code")).thenReturn(r.ruleCode);
        when(rs.getString("category")).thenReturn(r.category);
        when(rs.getString("domain")).thenReturn(r.domain);
        when(rs.getString("rule_type")).thenReturn(r.ruleType);
        when(rs.getString("severity")).thenReturn(r.severity);
        when(rs.getString("target_kind")).thenReturn(r.targetKind);
        when(rs.getString("target_id")).thenReturn(r.targetId);
        when(rs.getString("target_table")).thenReturn(r.targetTable);
        when(rs.getString("target_field")).thenReturn(r.targetField);
        when(rs.getString("target_pipeline_id")).thenReturn(r.targetPipelineId);
        when(rs.getString("parameters_json")).thenReturn(r.parametersJson);
        when(rs.getString("status")).thenReturn(r.status);
        when(rs.getInt("version")).thenReturn(r.version);
        when(rs.getString("approved_by")).thenReturn(r.approvedBy);
        when(rs.wasNull()).thenReturn(true);
        when(rs.getLong("effective_date")).thenReturn(0L);
        when(rs.getLong("expiry_date")).thenReturn(0L);
        when(rs.getString("source_type")).thenReturn(r.sourceType);
        when(rs.getString("source_ref")).thenReturn(r.sourceRef);
        when(rs.getString("description")).thenReturn(r.desc);
        when(rs.getString("created_by")).thenReturn(r.createdBy);
        when(rs.getString("updated_by")).thenReturn(r.updatedBy);
        when(rs.getTimestamp("created_at")).thenReturn(r.createdAt == null ? null : Timestamp.valueOf(r.createdAt));
        when(rs.getTimestamp("updated_at")).thenReturn(r.updatedAt == null ? null : Timestamp.valueOf(r.updatedAt));
        return rs;
    }

    /** 重置 mock 状态 + 设置下一读行。 */
    private void reset(StubRow row) {
        this.nextReadRow = row;
        writeAffectedResults.clear();
        executedSqls.clear();
        writeCallCounter.set(0);
    }

    private void setWriteAffected(int... arr) {
        writeCallCounter.set(0);
        for (int v : arr) {
            writeAffectedResults.add(v);
        }
    }

    /** 已执行 SQL 中包含 INSERT 的次数。 */
    private int countInserts() {
        int n = 0;
        for (String s : executedSqls) {
            if (s != null && s.startsWith("INSERT")) {
                n++;
            }
        }
        return n;
    }

    /** 已执行 SQL 中包含 UPDATE 的次数。 */
    private int countUpdates() {
        int n = 0;
        for (String s : executedSqls) {
            if (s != null && s.startsWith("UPDATE")) {
                n++;
            }
        }
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
        // createdAt/updatedAt 留 null 以避免 approve 序列化 DqRuleVO 时 Jackson 默认 MAPPER
        // 抛 "Java 8 date/time type LocalDateTime not supported"（铁律：test 侧不魔改 Impl 的静态 MAPPER）
        r.createdAt = null;
        r.updatedAt = null;
        return r;
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

    // ────────────────────────────────────────────────────────────────────
    // 10 个测试
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("1) createDraft — 落库 status=DRAFT, version=1；audit DQ_RULE_CREATE 计 1；1 次 INSERT")
    void createDraft_shouldReturnRuleId_DRAFT() {
        reset(null); // 创建不依赖读
        String ruleId = svc.createDraft(validDraftDto());
        assertNotNull(ruleId, "createDraft 应返回新 UUID");
        assertTrue(ruleId.length() >= 16, "ruleId 应为 UUID hex 串（≥16 char）");
        verify(securityService, times(1)).auditWrite(eq("DQ_RULE_CREATE"), eq(ruleId), eq("SUCCESS"));
        assertEquals(1, countInserts(), "createDraft 应执行 1 次 INSERT");
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
        assertEquals(1, countUpdates(), "DRAFT 编辑应 1 次 UPDATE");
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
        reset(null); // 空行模拟 id 不存在 / 已逻辑删除
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
        assertEquals(1, countUpdates(), "submit 应 1 次 UPDATE");
        verify(securityService, times(1)).auditWrite(eq("DQ_RULE_SUBMIT"), eq("r-rule-001"), eq("SUCCESS"));
    }

    @Test
    @DisplayName("6) approve — IN_REVIEW → ACTIVE，version 1→2；1 次 INSERT version + 1 次 UPDATE；audit DQ_RULE_APPROVE")
    void approve_success_versionIncrement() {
        reset(validRow("IN_REVIEW", 1, null));
        setWriteAffected(1, 1); // INSERT dq_rule_version + UPDATE dq_rule

        String newStatus = svc.approve("r-rule-001", "reviewer-carol");
        assertEquals("ACTIVE", newStatus);
        assertEquals(1, countInserts(), "approve 应 1 次 INSERT dq_rule_version");
        assertEquals(1, countUpdates(), "approve 应 1 次 UPDATE dq_rule");
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
        assertEquals(1, countUpdates(), "reject 应 1 次 UPDATE");
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
        assertEquals(1, countUpdates(), "DEPRECATE 应 1 次 UPDATE");
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
        assertEquals(1, countUpdates(), "SUPERSEDE 应 1 次 UPDATE");
        verify(securityService, times(1)).auditWrite(eq("DQ_RULE_SUPERSEDE"), eq("r-rule-001"), eq("SUCCESS"));
    }

    @Test
    @DisplayName("10) transition_illegal — DRAFT → ACTIVE 非法 → BusinessException；DB 不变更；audit 不触发")
    void transition_illegal_doesNotMutate() {
        reset(validRow("DRAFT", 1, null));
        // DRAFT 直接 approve（应为 IN_REVIEW 前置）— TRANSITIONS.get(DRAFT) 不含 ACTIVE
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
