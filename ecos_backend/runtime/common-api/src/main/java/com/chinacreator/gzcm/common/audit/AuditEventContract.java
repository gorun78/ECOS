package com.chinacreator.gzcm.common.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 治理审计事件契约（R7 §2.9.4③；#147M）。
 *
 * <p><b>字段面</b>：设计册标称「22 项」，但枚举给出的字段名只有 21 个（audit_event_id …
 * trace_id）。本契约<b>按枚举实现 21 项</b>，差额 1 项作为文档勘误登记待裁——禁为凑数臆造字段，
 * 那会让契约成为"看起来 22 项、实际没人知道第 22 项是什么"的假账。</p>
 *
 * <p><b>与既有列的关系（不改哈希链）</b>：{@link #timestamp()} 落既有 {@code created_at}；
 * 其余 20 项由 V303 新增为可空列。{@code AuditHashChainService} 的 6 字段哈希输入
 * <b>不变</b>——改输入会让历史链验签全部失效。</p>
 *
 * <p><b>必填子集</b>：{@link #missingFieldsForGovernance()} 只把治理裁决必需的 9 项列为必填；
 * Git/内容哈希等字段在"非 Git 变更"时允许为空（如纯配置项变更），不填不等于成功。</p>
 *
 * @author ECOS Control Domain #147M
 */
public record AuditEventContract(
        String auditEventId,
        String changeRequestId,
        String actorId,
        List<String> approverIds,
        String domain,
        String objectType,
        String objectId,
        String riskLevel,
        String gitRepository,
        String gitCommitSha,
        String gitParentSha,
        String contentHash,
        String targetEnvironment,
        String approvalPolicyVersion,
        String deploymentId,
        String beforeHash,
        String afterHash,
        String applyStatus,
        String verificationStatus,
        Instant timestamp,
        String traceId) {

    /** 契约字段名（顺序即设计册枚举顺序；{@code timestamp} 落库列名见 {@link #columnOf}）。 */
    public static final List<String> FIELDS = List.of(
            "audit_event_id", "change_request_id", "actor_id", "approver_ids", "domain",
            "object_type", "object_id", "risk_level", "git_repository", "git_commit_sha",
            "git_parent_sha", "content_hash", "target_environment", "approval_policy_version",
            "deployment_id", "before_hash", "after_hash", "apply_status", "verification_status",
            "timestamp", "trace_id");

    /** 设计册标称的 22 项与枚举 21 项之差（待裁勘误，禁臆造第 22 项）。 */
    public static final int DOC_DECLARED_COUNT = 22;

    public AuditEventContract {
        auditEventId = trim(auditEventId);
        changeRequestId = trim(changeRequestId);
        actorId = trim(actorId);
        approverIds = approverIds == null ? List.of() : List.copyOf(approverIds);
        domain = trim(domain);
        objectType = trim(objectType);
        objectId = trim(objectId);
        riskLevel = trim(riskLevel);
        gitRepository = trim(gitRepository);
        gitCommitSha = trim(gitCommitSha);
        gitParentSha = trim(gitParentSha);
        contentHash = trim(contentHash);
        targetEnvironment = trim(targetEnvironment);
        approvalPolicyVersion = trim(approvalPolicyVersion);
        deploymentId = trim(deploymentId);
        beforeHash = trim(beforeHash);
        afterHash = trim(afterHash);
        applyStatus = trim(applyStatus);
        verificationStatus = trim(verificationStatus);
        traceId = trim(traceId);
    }

    /** 契约字段 → 落库列名（唯二差异：timestamp→created_at；其余同名）。 */
    public static String columnOf(String field) {
        return "timestamp".equals(field) ? "created_at" : field;
    }

    /** 治理裁决必填的 9 项（缺失即不可用于发布审计）。 */
    public List<String> missingFieldsForGovernance() {
        List<String> missing = new ArrayList<>();
        if (auditEventId == null) missing.add("audit_event_id");
        if (changeRequestId == null) missing.add("change_request_id");
        if (actorId == null) missing.add("actor_id");
        if (domain == null) missing.add("domain");
        if (objectType == null) missing.add("object_type");
        if (objectId == null) missing.add("object_id");
        if (riskLevel == null) missing.add("risk_level");
        if (timestamp == null) missing.add("timestamp");
        if (traceId == null) missing.add("trace_id");
        return missing;
    }

    /** 风险等级必须落在既有 L1~L4（不替换 clearance_level，只要求可核对）。 */
    public AuditLevel auditLevel() {
        if (riskLevel == null) {
            return null;
        }
        return switch (riskLevel.trim().toUpperCase()) {
            case "L1", "L1_REGULAR" -> AuditLevel.L1_REGULAR;
            case "L2", "L2_CONTROLLED" -> AuditLevel.L2_CONTROLLED;
            case "L3", "L3_HIGH_RISK" -> AuditLevel.L3_HIGH_RISK;
            case "L4", "L4_CRITICAL" -> AuditLevel.L4_CRITICAL;
            default -> null;
        };
    }

    /**
     * 落库列值（键 = V303 列名；{@code approver_ids} 以逗号连接，空列表存 NULL）。
     * 供 DAO 以参数化 INSERT/UPDATE 使用；不含任何 SQL 拼接。
     */
    public Map<String, Object> toColumns() {
        Map<String, Object> cols = new LinkedHashMap<>();
        cols.put("audit_event_id", auditEventId);
        cols.put("change_request_id", changeRequestId);
        cols.put("actor_id", actorId);
        cols.put("approver_ids", approverIds.isEmpty() ? null : String.join(",", approverIds));
        cols.put("domain", domain);
        cols.put("object_type", objectType);
        cols.put("object_id", objectId);
        cols.put("risk_level", riskLevel);
        cols.put("git_repository", gitRepository);
        cols.put("git_commit_sha", gitCommitSha);
        cols.put("git_parent_sha", gitParentSha);
        cols.put("content_hash", contentHash);
        cols.put("target_environment", targetEnvironment);
        cols.put("approval_policy_version", approvalPolicyVersion);
        cols.put("deployment_id", deploymentId);
        cols.put("before_hash", beforeHash);
        cols.put("after_hash", afterHash);
        cols.put("apply_status", applyStatus);
        cols.put("verification_status", verificationStatus);
        cols.put("trace_id", traceId);
        return cols;
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
