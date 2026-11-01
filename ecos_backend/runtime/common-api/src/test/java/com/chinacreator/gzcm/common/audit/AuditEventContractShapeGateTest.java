package com.chinacreator.gzcm.common.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 治理审计事件契约 × DDL 一致性门（§2.9.4③；#147M）。
 *
 * <p>钉四件事：</p>
 * <ol>
 *   <li>契约字段名与 V303 新增列<b>双向相等</b>（除 {@code timestamp → created_at} 映射）；</li>
 *   <li>字段实数 = 21，且<b>显式登记</b>设计册标称 22 与枚举 21 的差额（禁为凑数臆造字段）；</li>
 *   <li>必填子集 = 9 项；缺任一项即不可用于治理发布审计；</li>
 *   <li>{@code risk_level} 只能映射 L1~L4，未知值不静默降级。</li>
 * </ol>
 */
class AuditEventContractShapeGateTest {

    private static final Pattern ADD_COLUMN = Pattern.compile(
            "(?i)ALTER\\s+TABLE\\s+public\\.ecos_audit_log\\s+ADD\\s+COLUMN\\s+IF\\s+NOT\\s+EXISTS\\s+([a-z_]+)");

    private static Path repoRoot() {
        Path cur = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path p = cur; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("ecos_backend"))) {
                return p;
            }
        }
        return fail("无法定位仓库根: user.dir=" + cur);
    }

    private static Path v303() {
        return repoRoot().resolve("docs/50-db_script/02-flyway-migration/gateway/"
                + "V303__audit_event_contract_columns.sql");
    }

    @Test
    @DisplayName("契约 21 字段 ↔ V303 新增 20 列（timestamp→created_at）双向相等")
    void contractFieldsMatchDdlColumns() throws IOException {
        Path script = v303();
        assertTrue(Files.isRegularFile(script), "V303 不在位：" + script);
        String sql = Files.readString(script, StandardCharsets.UTF_8);
        Set<String> columns = new LinkedHashSet<>();
        Matcher m = ADD_COLUMN.matcher(sql);
        while (m.find()) {
            columns.add(m.group(1));
        }
        assertEquals(20, columns.size(), "V303 新增列数应为 20（timestamp 复用 created_at）");

        Set<String> expected = new LinkedHashSet<>();
        for (String f : AuditEventContract.FIELDS) {
            if ("timestamp".equals(f)) {
                continue;
            }
            expected.add(AuditEventContract.columnOf(f));
        }
        assertEquals(expected, columns, "契约字段与 DDL 列不一致（任一侧新增/删改即红）");

        // timestamp 必须映射到既有列，而不是新建同名列
        assertEquals("created_at", AuditEventContract.columnOf("timestamp"));
        assertFalse(columns.contains("timestamp"), "禁新建 timestamp 列（PG 关键字且与 created_at 重复语义）");
    }

    @Test
    @DisplayName("字段实数 21：设计册标称 22 的差额显式登记，禁臆造第 22 项")
    void declaredCountDiscrepancyIsExplicit() {
        assertEquals(21, AuditEventContract.FIELDS.size());
        assertEquals(22, AuditEventContract.DOC_DECLARED_COUNT);
        assertEquals(1, AuditEventContract.DOC_DECLARED_COUNT - AuditEventContract.FIELDS.size(),
                "差额必须恰为 1（文档勘误待裁）；若新增字段须同步改本断言与设计册回写");
        assertEquals(AuditEventContract.FIELDS.size(), new LinkedHashSet<>(AuditEventContract.FIELDS).size(),
                "字段名不得重复");
    }

    @Test
    @DisplayName("治理必填 9 项与风险等级 L1~L4 映射")
    void governanceRequiredSubsetAndRiskLevel() {
        AuditEventContract empty = builder().build();
        assertEquals(List.of("audit_event_id", "change_request_id", "actor_id", "domain",
                "object_type", "object_id", "risk_level", "timestamp", "trace_id"),
                empty.missingFieldsForGovernance());

        AuditEventContract full = builder()
                .auditEventId("evt-1").changeRequestId("cr-1").actorId("u-1")
                .domain("ontology").objectType("rule").objectId("r-1")
                .riskLevel("L3").traceId("trace-1").timestamp(Instant.parse("2026-11-01T00:00:00Z")).build();
        assertEquals(List.of(), full.missingFieldsForGovernance());
        assertEquals(AuditLevel.L3_HIGH_RISK, full.auditLevel());

        assertNull(builder().riskLevel("BOGUS").build().auditLevel(), "未知风险等级禁静默降级");
        assertEquals(AuditLevel.L4_CRITICAL, builder().riskLevel("l4_critical").build().auditLevel());
    }

    @Test
    @DisplayName("toColumns 键面与 DDL 列一一对应（不含 timestamp）")
    void toColumnsMatchDdlColumnKeys() {
        Map<String, Object> cols = builder()
                .auditEventId("evt-1").changeRequestId("cr-1").actorId("u-1")
                .approverIds(List.of("u-2", "u-3")).domain("ontology")
                .objectType("rule").objectId("r-1").riskLevel("L3")
                .gitRepository("ontology-model").gitCommitSha("abc123")
                .traceId("trace-1").build().toColumns();

        Set<String> expected = new LinkedHashSet<>();
        for (String f : AuditEventContract.FIELDS) {
            if (!"timestamp".equals(f)) {
                expected.add(AuditEventContract.columnOf(f));
            }
        }
        assertEquals(expected, cols.keySet());
        assertEquals("u-2,u-3", cols.get("approver_ids"));
        assertNotNull(cols.get("git_commit_sha"));
    }

    private static Builder builder() {
        return new Builder();
    }

    /** 测试构造器（生产侧按 record 规范构造；此处只服务本门）。 */
    private static final class Builder {
        private String auditEventId;
        private String changeRequestId;
        private String actorId;
        private List<String> approverIds = List.of();
        private String domain;
        private String objectType;
        private String objectId;
        private String riskLevel;
        private String gitRepository;
        private String gitCommitSha;
        private String gitParentSha;
        private String contentHash;
        private String targetEnvironment;
        private String approvalPolicyVersion;
        private String deploymentId;
        private String beforeHash;
        private String afterHash;
        private String applyStatus;
        private String verificationStatus;
        private Instant timestamp;
        private String traceId;

        Builder auditEventId(String v) { this.auditEventId = v; return this; }
        Builder changeRequestId(String v) { this.changeRequestId = v; return this; }
        Builder actorId(String v) { this.actorId = v; return this; }
        Builder approverIds(List<String> v) { this.approverIds = v; return this; }
        Builder domain(String v) { this.domain = v; return this; }
        Builder objectType(String v) { this.objectType = v; return this; }
        Builder objectId(String v) { this.objectId = v; return this; }
        Builder riskLevel(String v) { this.riskLevel = v; return this; }
        Builder gitRepository(String v) { this.gitRepository = v; return this; }
        Builder gitCommitSha(String v) { this.gitCommitSha = v; return this; }
        Builder timestamp(Instant v) { this.timestamp = v; return this; }
        Builder traceId(String v) { this.traceId = v; return this; }

        AuditEventContract build() {
            return new AuditEventContract(auditEventId, changeRequestId, actorId, approverIds,
                    domain, objectType, objectId, riskLevel, gitRepository, gitCommitSha,
                    gitParentSha, contentHash, targetEnvironment, approvalPolicyVersion,
                    deploymentId, beforeHash, afterHash, applyStatus, verificationStatus,
                    timestamp, traceId);
        }
    }

    /** 防扫描面失明：仓根必须能看到单源目录与 V303。 */
    @Test
    @DisplayName("扫描面可信：单源目录存在且含 V303")
    void scanSurfaceIsTrustworthy() throws IOException {
        Path dir = repoRoot().resolve("docs/50-db_script/02-flyway-migration/gateway");
        assertTrue(Files.isDirectory(dir));
        try (Stream<Path> walk = Files.list(dir)) {
            assertTrue(walk.anyMatch(p -> p.getFileName().toString().startsWith("V303__")));
        }
    }
}
