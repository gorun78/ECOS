package com.chinacreator.gzcm.workspace.blueprint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 场景蓝图逻辑模型（补充设计 v1.0 §6.2 数据契约，<b>API 投影唯一属主形态</b>）。
 *
 * <p>§6.1 对象属主 = workspace/场景工作台（唯一写入面；ai/cognitive 域禁复制权威副本）。
 * 与持久化行实体 {@link ScenarioBlueprintRow}（DDL V271 物理列投影）由
 * {@link ScenarioBlueprintService} 双向映射：结构化子段（scope/dependencies/capabilities/
 * gaps/risks/tests）落 *_text JSON 列，{@code content_json} 保留完整冻结快照（发布冻结体，
 * 运行回放以本列解析，<b>SUP-BP-03</b>）。</p>
 *
 * <p>承重约束（SUP-BP-01/02/03，§16 验收矩阵）：</p>
 * <ul>
 *   <li>status 词表单源 = {@link BlueprintStatus}（16 态，§6.3；状态机非法迁移 409）；</li>
 *   <li>goal 必含 statement / ownerRole / successCriteria（§6.2 三要素）；</li>
 *   <li>gaps 为强类型列表（§6.5 十字段）；</li>
 *   <li>release.artifactDigest 仅 RELEASED 起填充（§6.6 发布不可变）。</li>
 * </ul>
 *
 * <p>契约层纪律：纯 POJO，禁 {@code @Value} 字面量与 Spring 注解；集合字段默认空集合，
 * 序列化缺省安全（null → []）。</p>
 */
public class ScenarioBlueprint {

    /** 稳定 ID（= 物理行 id / 应用侧 UUID，MC01）。 */
    private String blueprintId;
    /** 逻辑引用 workspace 场景主表（同域，ST09 不建 FK）。 */
    private String scenarioId;
    /** 版本号（从 1 起；发布不可变，修订 = version+1 新行，§6.6）。 */
    private int version;
    /** 状态（词表单源 {@link BlueprintStatus}，§6.3）。 */
    private String status;

    private Goal goal;
    private Scope scope;
    private List<Dependency> dependencies = new ArrayList<>();
    private List<Capability> capabilities = new ArrayList<>();
    /** §6.5 Gap 项（强类型，全十字段）。 */
    private List<Map<String, Object>> gaps = new ArrayList<>();
    private List<Risk> risks = new ArrayList<>();
    private List<Test> tests = new ArrayList<>();

    private Approval approval = new Approval();
    private Release release = new Release();

    /** 运行回链：关联 Context Pack 摘要（§5.2 contextDigest；运行记录引用完整且可解析，SUP-BP-03）。 */
    private String contextDigest;
    private String traceId;

    /**
     * 行形态热列（DDL V271 物理列直存值）。Service/DAO 的读写面一律走 *_text / contentJson /
     * actor / transitionReason 文本访问器；结构化子段（goal/scope/列表）仅作可选投影——
     * text getter 优先返回本组 raw 值，raw 为 null 时回落结构化序列化。
     * （2026-10-11 live 契约核验修复：原 no-op setter + 恒 null getter 导致热列静默丢写，§6.2/§6.6。）
     */
    private String goalSuccessText;
    private String scopeJsonText;
    private String dependenciesText;
    private String capabilitiesText;
    private String gapsText;
    private String risksText;
    private String testsText;
    private String contentJson;
    /** 最近状态迁移操作者/原因（§6.3 五要素留痕热列）。 */
    private String actor;
    private String transitionReason;
    private java.time.LocalDateTime releasedTime;

    /** 审计字段（持久化层映射，逻辑模型保留用于 DAO 兼容）。 */
    private String tenantId;
    private String orgId;
    private String domain;
    private String versionNo;
    private String createBy;
    private java.time.LocalDateTime createTime;
    private String updateBy;
    private java.time.LocalDateTime updateTime;
    private int isDeleted = 0;
    private String releaseArtifactRef;

    /** 迁移历史（§6.3 五要素留痕，落 content_json.transitionHistory 数组，不建迁移表）。 */
    public static class Transition {
        private String fromStatus;
        private String toStatus;
        private String actor;
        private String time;
        private String reason;
        private String traceId;
        private int blueprintVersion;

        public String getFromStatus() { return fromStatus; }
        public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
        public String getToStatus() { return toStatus; }
        public void setToStatus(String toStatus) { this.toStatus = toStatus; }
        public String getActor() { return actor; }
        public void setActor(String actor) { this.actor = actor; }
        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getTraceId() { return traceId; }
        public void setTraceId(String traceId) { this.traceId = traceId; }
        public int getBlueprintVersion() { return blueprintVersion; }
        public void setBlueprintVersion(int blueprintVersion) { this.blueprintVersion = blueprintVersion; }
    }

    /** §6.2 goal：业务目标（statement/ownerRole/successCriteria 三要素）。 */
    public static class Goal {
        private String statement;
        private String ownerRole;
        private List<String> successCriteria = new ArrayList<>();

        public String getStatement() { return statement; }
        public void setStatement(String statement) { this.statement = statement; }
        public String getOwnerRole() { return ownerRole; }
        public void setOwnerRole(String ownerRole) { this.ownerRole = ownerRole; }
        public List<String> getSuccessCriteria() { return successCriteria; }
        public void setSuccessCriteria(List<String> successCriteria) {
            this.successCriteria = successCriteria == null ? new ArrayList<>() : successCriteria;
        }
    }

    /** §6.2 scope：租户/期间/项目/部门/口径（批次 B 红线「场景变更限定指定项目/部门/期间」）。 */
    public static class Scope {
        private String tenantId;
        private List<String> periods = new ArrayList<>();
        private List<String> projectIds = new ArrayList<>();
        private List<String> orgIds = new ArrayList<>();
        private String caliberId;

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public List<String> getPeriods() { return periods; }
        public void setPeriods(List<String> periods) {
            this.periods = periods == null ? new ArrayList<>() : periods;
        }
        public List<String> getProjectIds() { return projectIds; }
        public void setProjectIds(List<String> projectIds) {
            this.projectIds = projectIds == null ? new ArrayList<>() : projectIds;
        }
        public List<String> getOrgIds() { return orgIds; }
        public void setOrgIds(List<String> orgIds) {
            this.orgIds = orgIds == null ? new ArrayList<>() : orgIds;
        }
        public String getCaliberId() { return caliberId; }
        public void setCaliberId(String caliberId) { this.caliberId = caliberId; }
    }

    /**
     * §6.2 dependencies 单条：type/id/required/versionConstraint（+ version/status 运行期装配态）。
     * 发布前每个 required 依赖须存在且 status=PUBLISHED（§6.6 门禁 2）。
     */
    public static class Dependency {
        /** DATA_ASSET / ONTOLOGY / KNOWLEDGE / MODEL / TOOL 等（§7 统一候选协议同源词表）。 */
        private String type;
        private String id;
        private boolean required;
        /** PINNED / RANGE / LATEST（§6.4 步骤 9：依赖版本要么固定，要么明确兼容范围）。 */
        private String versionConstraint;
        private String version;
        private String status;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
        public String getVersionConstraint() { return versionConstraint; }
        public void setVersionConstraint(String versionConstraint) { this.versionConstraint = versionConstraint; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /** §6.2 capabilities 单条：capabilityId/contractVersion/required + writeOperation 审批标记（§6.6 门禁 4）。 */
    public static class Capability {
        private String capabilityId;
        private String contractVersion;
        private boolean required;
        /** 写操作类能力（§6.6：写操作必须有 Tool Contract + security-engine 决策 + 审计事件）。 */
        private boolean writeOperation;

        public String getCapabilityId() { return capabilityId; }
        public void setCapabilityId(String capabilityId) { this.capabilityId = capabilityId; }
        public String getContractVersion() { return contractVersion; }
        public void setContractVersion(String contractVersion) { this.contractVersion = contractVersion; }
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
        public boolean isWriteOperation() { return writeOperation; }
        public void setWriteOperation(boolean writeOperation) { this.writeOperation = writeOperation; }
    }

    /** §6.2 risks 单条（风险登记，可读描述 + 影响面引用）。 */
    public static class Risk {
        private String riskId;
        private String description;
        private String severity;
        private List<String> evidenceRefs = new ArrayList<>();

        public String getRiskId() { return riskId; }
        public void setRiskId(String riskId) { this.riskId = riskId; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public List<String> getEvidenceRefs() { return evidenceRefs; }
        public void setEvidenceRefs(List<String> evidenceRefs) {
            this.evidenceRefs = evidenceRefs == null ? new ArrayList<>() : evidenceRefs;
        }
    }

    /** §6.2 tests 单条：testId/priority/status（P0 未通过禁进 READY_FOR_APPROVAL，§6.6 门禁 5）。 */
    public static class Test {
        private String testId;
        /** P0 / P1 / P2（P0 = 验收阻断级）。 */
        private String priority;
        /** PASSED / FAILED / UNRUN。 */
        private String status;
        /** 测试结果引用（只读预览 / 确定性计算结果引用，§6.4 步骤 7）。 */
        private String resultRef;

        public String getTestId() { return testId; }
        public void setTestId(String testId) { this.testId = testId; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getResultRef() { return resultRef; }
        public void setResultRef(String resultRef) { this.resultRef = resultRef; }
    }

    /** §6.2 approval：required/policyRef（security 权威，本域只存引用）。 */
    public static class Approval {
        private boolean required;
        private String policyRef;
        /** 审批通过后的裁决/批准记录引用（approve 端点回填，security 权威）。 */
        private String approvalRef;

        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
        public String getPolicyRef() { return policyRef; }
        public void setPolicyRef(String policyRef) { this.policyRef = policyRef; }
        public String getApprovalRef() { return approvalRef; }
        public void setApprovalRef(String approvalRef) { this.approvalRef = approvalRef; }
    }

    /** §6.2 release：artifactDigest/releasedAt（仅 RELEASED 起填充；发布不可变 §6.6）。 */
    public static class Release {
        /** content_json 的 SHA-256（release 端点填充）。 */
        private String artifactDigest;
        private String releasedAt;

        public String getArtifactDigest() { return artifactDigest; }
        public void setArtifactDigest(String artifactDigest) { this.artifactDigest = artifactDigest; }
        public String getReleasedAt() { return releasedAt; }
        public void setReleasedAt(String releasedAt) { this.releasedAt = releasedAt; }
    }

    // ───────────────── getters / setters ─────────────────

    public String getBlueprintId() { return blueprintId; }
    public void setBlueprintId(String blueprintId) { this.blueprintId = blueprintId; }

    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Goal getGoal() { return goal; }
    public void setGoal(Goal goal) { this.goal = goal; }

    public Scope getScope() { return scope; }
    public void setScope(Scope scope) { this.scope = scope; }

    public List<Dependency> getDependencies() { return dependencies; }
    public void setDependencies(List<Dependency> dependencies) {
        this.dependencies = dependencies == null ? new ArrayList<>() : dependencies;
    }

    public List<Capability> getCapabilities() { return capabilities; }
    public void setCapabilities(List<Capability> capabilities) {
        this.capabilities = capabilities == null ? new ArrayList<>() : capabilities;
    }

    public List<Map<String, Object>> getGaps() { return gaps; }
    public void setGaps(List<Map<String, Object>> gaps) {
        this.gaps = gaps == null ? new ArrayList<>() : gaps;
    }

    public List<Risk> getRisks() { return risks; }
    public void setRisks(List<Risk> risks) { this.risks = risks == null ? new ArrayList<>() : risks; }

    public List<Test> getTests() { return tests; }
    public void setTests(List<Test> tests) { this.tests = tests == null ? new ArrayList<>() : tests; }

    public Approval getApproval() { return approval; }
    public void setApproval(Approval approval) { this.approval = approval == null ? new Approval() : approval; }

    public Release getRelease() { return release; }
    public void setRelease(Release release) { this.release = release == null ? new Release() : release; }

    public String getContextDigest() { return contextDigest; }
    public void setContextDigest(String contextDigest) { this.contextDigest = contextDigest; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScenarioBlueprint that)) return false;
        return version == that.version
                && Objects.equals(blueprintId, that.blueprintId)
                && Objects.equals(scenarioId, that.scenarioId)
                && Objects.equals(status, that.status)
                && Objects.equals(goal, that.goal)
                && Objects.equals(scope, that.scope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blueprintId, scenarioId, version, status, goal, scope);
    }

    // ── Convenience getters for Service layer (flatten nested objects to text/JSON) ──
    // 口径：raw 热列优先（Service/DAO 读写面），raw 为 null 时回落结构化投影序列化。

    public String getId() { return blueprintId; }
    public String getGoalStatement() { return goal != null ? goal.getStatement() : null; }
    public String getGoalSuccessText() {
        if (goalSuccessText != null) return goalSuccessText;
        if (goal == null || goal.getSuccessCriteria() == null || goal.getSuccessCriteria().isEmpty()) return null;
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(goal.getSuccessCriteria()); }
        catch (Exception e) { return null; }
    }
    public String getScopeJsonText() {
        if (scopeJsonText != null) return scopeJsonText;
        if (scope == null) return null;
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(scope); }
        catch (Exception e) { return null; }
    }
    public String getDependenciesText() {
        if (dependenciesText != null) return dependenciesText;
        if (dependencies == null || dependencies.isEmpty()) return null;
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(dependencies); }
        catch (Exception e) { return null; }
    }
    public String getCapabilitiesText() {
        if (capabilitiesText != null) return capabilitiesText;
        if (capabilities == null || capabilities.isEmpty()) return null;
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(capabilities); }
        catch (Exception e) { return null; }
    }
    public String getGapsText() {
        if (gapsText != null) return gapsText;
        if (gaps == null || gaps.isEmpty()) return null;
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(gaps); }
        catch (Exception e) { return null; }
    }
    public String getRisksText() {
        if (risksText != null) return risksText;
        if (risks == null || risks.isEmpty()) return null;
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(risks); }
        catch (Exception e) { return null; }
    }
    public String getTestsText() {
        if (testsText != null) return testsText;
        if (tests == null || tests.isEmpty()) return null;
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(tests); }
        catch (Exception e) { return null; }
    }
    public void setDependenciesText(String json) { this.dependenciesText = json; }
    public void setCapabilitiesText(String json) { this.capabilitiesText = json; }
    public void setGapsText(String json) { this.gapsText = json; }
    public void setRisksText(String json) { this.risksText = json; }
    public void setTestsText(String json) { this.testsText = json; }
    public void setContentJson(String json) { this.contentJson = json; }
    public void setArtifactDigest(String digest) { if (release != null) release.setArtifactDigest(digest); }
    public void setApprovalRequired(int required) { if (approval != null) approval.setRequired(required == 1); }
    public void setApprovalRef(String ref) { if (approval != null) approval.setApprovalRef(ref); }
    public void setReleasedTime(java.time.LocalDateTime time) {
        this.releasedTime = time;
        if (release != null) release.setReleasedAt(time != null ? time.toString() : null);
    }
    public void setId(String id) { this.blueprintId = id; }
    public void setGoalStatement(String statement) { if (goal == null) goal = new Goal(); goal.setStatement(statement); }
    public void setGoalSuccessText(String json) { this.goalSuccessText = json; }
    public void setScopeJsonText(String json) { this.scopeJsonText = json; }
    public Integer getApprovalRequired() { return approval != null && approval.isRequired() ? 1 : 0; }
    public String getApprovalRef() { return approval != null ? approval.getApprovalRef() : null; }
    public String getArtifactDigest() { return release != null ? release.getArtifactDigest() : null; }
    public java.time.LocalDateTime getReleasedTime() { return releasedTime; }
    public String getContentJson() { return contentJson; }
    public String getActor() { return actor; }
    public String getTransitionReason() { return transitionReason; }
    public java.time.LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(java.time.LocalDateTime createTime) { this.createTime = createTime; }
    public java.time.LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(java.time.LocalDateTime updateTime) { this.updateTime = updateTime; }

    // Persistence-layer compatible getters/setters (DAO mapping)
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getVersionNo() { return versionNo; }
    public void setVersionNo(String versionNo) { this.versionNo = versionNo; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
    public int getIsDeleted() { return isDeleted; }
    public void setIsDeleted(int isDeleted) { this.isDeleted = isDeleted; }
    public String getReleaseArtifactRef() { return releaseArtifactRef; }
    public void setReleaseArtifactRef(String releaseArtifactRef) { this.releaseArtifactRef = releaseArtifactRef; }
    public void setActor(String actor) { this.actor = actor; }
    public void setTransitionReason(String reason) { this.transitionReason = reason; }
}
