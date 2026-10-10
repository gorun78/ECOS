package com.chinacreator.gzcm.engine.ai.wagent.dataobject;

/**
 * V236 {@code ecos_ai.ecos_wagent_tool_contract} 工具契约 DO（注册/发现/披露/治理四要件，§5.1/§5.4）。
 * <p>词表单源（均 {@link com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums}）：
 * {@code status} = ToolContractStatus 六态；{@code category} = ToolCategory 五值
 * （read/compute/candidate/commit/notify，PRD REQ-WAG-17 冻结小写）；{@code side_effect} = SideEffect
 * 三值；{@code disclosure} = Disclosure 两值（always_loaded 13 字符——列宽 12→16 已订正，脚本未实跑）；
 * {@code min_level} 档位 = AutomationLevel。</p>
 * <p>{@code engine} 属主引擎代号值名录单源 = {@link
 * com.chinacreator.gzcm.engine.ai.wagent.WAgentEngineCatalog#contractEngineCodesForDdlCheck()}
 * （data/ontology/kb/cognitive/ai/security；workspace 场景层不入工具面——红线 3）。
 * 本列未设 CHECK：存量登记形态未定版（R-15 纪律）。</p>
 * <p>UNIQUE(name, version, is_deleted)；commit 类必带 {@code rollback_plan_text}
 * （ck_wagent_tool_rollback）。JSON Schema 语义一律 TEXT（MC02）。</p>
 */
public class WAgentToolContractDO extends WAgentBaseDO {

    private String id;
    private String name;
    private String version;
    private String engine;
    private String category;
    private String sideEffect;
    private Integer minLevel;
    private String requiredPermission;
    private String inputSchemaJson;
    private String outputSchemaJson;
    private Integer timeoutMs;
    private String retryText;
    private Integer isIdempotent;
    private String costClass;
    private Integer isEvidenceOutput;
    private String dataClassificationText;
    private String rollbackPlanText;
    private String endpointKind;
    private String endpointUrl;
    private String endpointMethod;
    private String owner;
    private String slaText;
    private String status;
    private String deprecatedBy;
    private String toolset;
    private String summary;
    private String disclosure;
    private String capabilityDepsText;

    // ── V270 契约机校补列（《补充设计 v1.0》§11.1，只增不改；BeanPropertyRowMapper 按命名自动映射）──
    /** 关联能力目录（V270 capability_id；词表单源 {@code AiCapabilityId}）。 */
    private String capabilityId;
    /** 操作类别 READ/SUGGEST/WRITE（V270 operation_class；NULL 读侧回落 V236 side_effect，见 {@link #operationClassResolved()}）。 */
    private String operationClass;
    /** 机器可读输入契约引用（V270 input_schema_ref；与既有 input_schema_json 正文列并存，本列存引用键）。 */
    private String inputSchemaRef;
    /** 机器可读输出契约引用（V270 output_schema_ref）。 */
    private String outputSchemaRef;
    /** 是否要求来源引用（V270 evidence_required SMALLINT；与 V236 is_evidence_output 并存，读以本列为准）。 */
    private Integer evidenceRequired;
    /** 输出是否应由确定性计算产生（V270 deterministic_output SMALLINT）。 */
    private Integer deterministicOutput;
    /** 工具所需自动化等级档位（V270 automation_level L0~L3；NULL 回落 V236 min_level，读侧单源在 Java 合并）。 */
    private String automationLevel;
    /** 允许处理的最高密级档位（V270 data_classification_max；security-engine 权威裁决，本列只声明上限）。 */
    private String dataClassificationMax;
    /** 审批要求档位（V270 requires_approval VARCHAR；NONE/HUMAN/TWO_EYE 语义；NULL 读侧默认 NONE）。 */
    private String requiresApproval;
    /** 幂等约束（V270 idempotent SMALLINT；与 V236 is_idempotent 并存）。 */
    private Integer idempotent;
    /** 幂等键强制位（V270 idempotency_key_required SMALLINT）。 */
    private Integer idempotencyKeyRequired;
    /** 超时毫秒（V270 timeout_ms BIGINT；与 V236 timeout_ms 并存，读以本列为准）。 */
    private Long timeoutMsContract;
    /** 重试策略引用（V270 retry_policy_ref；与 V236 retry_text 并存）。 */
    private String retryPolicyRef;
    /** 影响的资产/范围（V270 side_effect_scope；V236 side_effect 停写不改，本列细化）。 */
    private String sideEffectScope;
    /** 回滚能力档位（V270 rollback_capability；NONE/NATURAL/MANUAL 语义）。 */
    private String rollbackCapability;
    /** 回滚方式引用（V270 rollback_ref；V236 rollback_plan_text 停写不改，本列细化）。 */
    private String rollbackRef;
    /** 统一审计事件类型（V270 audit_event_type；值必须命中 EventTypes 单源清单）。 */
    private String auditEventType;
    /** 正式语义与写入权归属引擎（V270 owner_engine；六名见 WAgentEngineCatalog）。 */
    private String ownerEngine;

    // V270 读侧兼容默认的字面量单源（§11.1 首行：已有同义字段则映射）
    private static final String OPERATION_READ = "READ";
    private static final String SIDE_EFFECT_NONE = "none";
    private static final String APPROVAL_NONE = "NONE";
    private static final String ROLLBACK_NONE = "NONE";

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSideEffect() { return sideEffect; }
    public void setSideEffect(String sideEffect) { this.sideEffect = sideEffect; }

    public Integer getMinLevel() { return minLevel; }
    public void setMinLevel(Integer minLevel) { this.minLevel = minLevel; }

    public String getRequiredPermission() { return requiredPermission; }
    public void setRequiredPermission(String requiredPermission) { this.requiredPermission = requiredPermission; }

    public String getInputSchemaJson() { return inputSchemaJson; }
    public void setInputSchemaJson(String inputSchemaJson) { this.inputSchemaJson = inputSchemaJson; }

    public String getOutputSchemaJson() { return outputSchemaJson; }
    public void setOutputSchemaJson(String outputSchemaJson) { this.outputSchemaJson = outputSchemaJson; }

    public Integer getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(Integer timeoutMs) { this.timeoutMs = timeoutMs; }

    public String getRetryText() { return retryText; }
    public void setRetryText(String retryText) { this.retryText = retryText; }

    public Integer getIsIdempotent() { return isIdempotent; }
    public void setIsIdempotent(Integer isIdempotent) { this.isIdempotent = isIdempotent; }

    public String getCostClass() { return costClass; }
    public void setCostClass(String costClass) { this.costClass = costClass; }

    public String getRollbackPlanText() { return rollbackPlanText; }
    public void setRollbackPlanText(String rollbackPlanText) { this.rollbackPlanText = rollbackPlanText; }

    public Integer getIsEvidenceOutput() { return isEvidenceOutput; }
    public void setIsEvidenceOutput(Integer isEvidenceOutput) { this.isEvidenceOutput = isEvidenceOutput; }

    public String getDataClassificationText() { return dataClassificationText; }
    public void setDataClassificationText(String dataClassificationText) { this.dataClassificationText = dataClassificationText; }

    public String getEndpointKind() { return endpointKind; }
    public void setEndpointKind(String endpointKind) { this.endpointKind = endpointKind; }

    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }

    public String getEndpointMethod() { return endpointMethod; }
    public void setEndpointMethod(String endpointMethod) { this.endpointMethod = endpointMethod; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getSlaText() { return slaText; }
    public void setSlaText(String slaText) { this.slaText = slaText; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDeprecatedBy() { return deprecatedBy; }
    public void setDeprecatedBy(String deprecatedBy) { this.deprecatedBy = deprecatedBy; }

    public String getToolset() { return toolset; }
    public void setToolset(String toolset) { this.toolset = toolset; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getDisclosure() { return disclosure; }
    public void setDisclosure(String disclosure) { this.disclosure = disclosure; }

    public String getCapabilityDepsText() { return capabilityDepsText; }
    public void setCapabilityDepsText(String capabilityDepsText) { this.capabilityDepsText = capabilityDepsText; }

    // ── V270 补列访问器（只增不改；命名与列 camelCase 严格对齐，BeanPropertyRowMapper 自动映射）──
    public String getCapabilityId() { return capabilityId; }
    public void setCapabilityId(String capabilityId) { this.capabilityId = capabilityId; }

    public String getOperationClass() { return operationClass; }
    public void setOperationClass(String operationClass) { this.operationClass = operationClass; }

    /**
     * 操作类别读取端兼容默认（§11.1 首行"如已有同义字段则映射"）：
     * V270 {@code operation_class} 优先；存量行 NULL 时按 V236 {@code side_effect}
     * 回落——无副作用即 {@code READ}，draft 即 {@code SUGGEST}，production 即 {@code WRITE}。
     */
    public String operationClassResolved() {
        if (operationClass != null && !operationClass.isBlank()) {
            return operationClass;
        }
        if (sideEffect == null || sideEffect.isBlank() || SIDE_EFFECT_NONE.equalsIgnoreCase(sideEffect)) {
            return OPERATION_READ;
        }
        return com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.SideEffect.valueOf(sideEffect.trim().toUpperCase(java.util.Locale.ROOT))
                == com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.SideEffect.PRODUCTION ? "WRITE" : "SUGGEST";
    }

    public String getInputSchemaRef() { return inputSchemaRef; }
    public void setInputSchemaRef(String inputSchemaRef) { this.inputSchemaRef = inputSchemaRef; }

    public String getOutputSchemaRef() { return outputSchemaRef; }
    public void setOutputSchemaRef(String outputSchemaRef) { this.outputSchemaRef = outputSchemaRef; }

    public Integer getEvidenceRequired() { return evidenceRequired; }
    public void setEvidenceRequired(Integer evidenceRequired) { this.evidenceRequired = evidenceRequired; }

    /**
     * 来源引用要求读取端（§7.5）：V270 {@code evidence_required} 优先；
     * 存量行 NULL 回落 V236 {@code is_evidence_output}（两列并存期判据）。
     */
    public Integer evidenceRequiredResolved() {
        if (evidenceRequired != null) {
            return evidenceRequired;
        }
        return isEvidenceOutput == null ? 0 : isEvidenceOutput;
    }

    public Integer getDeterministicOutput() { return deterministicOutput; }
    public void setDeterministicOutput(Integer deterministicOutput) { this.deterministicOutput = deterministicOutput; }

    public String getAutomationLevel() { return automationLevel; }
    public void setAutomationLevel(String automationLevel) { this.automationLevel = automationLevel; }

    /** 自动化档位读取端：V270 {@code automation_level}（L0~L3）优先，存量行 NULL 回落 V236 {@code min_level} 数值→L{n}。 */
    public String automationLevelResolved() {
        if (automationLevel != null && !automationLevel.isBlank()) {
            return automationLevel;
        }
        return minLevel == null ? null : "L" + minLevel;
    }

    public String getDataClassificationMax() { return dataClassificationMax; }
    public void setDataClassificationMax(String dataClassificationMax) { this.dataClassificationMax = dataClassificationMax; }

    public String getRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(String requiresApproval) { this.requiresApproval = requiresApproval; }

    /** 审批要求读取端兼容默认：存量行未声明 ⇒ NONE（无 approval 默认 false 的档位形态）。 */
    public String requiresApprovalResolved() {
        return requiresApproval == null || requiresApproval.isBlank() ? APPROVAL_NONE : requiresApproval;
    }

    public Integer getIdempotent() { return idempotent; }
    public void setIdempotent(Integer idempotent) { this.idempotent = idempotent; }

    public Integer getIdempotencyKeyRequired() { return idempotencyKeyRequired; }
    public void setIdempotencyKeyRequired(Integer idempotencyKeyRequired) { this.idempotencyKeyRequired = idempotencyKeyRequired; }

    public Long getTimeoutMsContract() { return timeoutMsContract; }
    public void setTimeoutMsContract(Long timeoutMsContract) { this.timeoutMsContract = timeoutMsContract; }

    /** 超时读取端：V270 {@code timeout_ms}（BIGINT 同名列）优先，存量行 NULL 回落 V236 {@code timeout_ms} 整数列。 */
    public Long timeoutMsResolved() {
        return timeoutMsContract == null ? (timeoutMs == null ? null : (long) timeoutMs) : timeoutMsContract;
    }

    public String getRetryPolicyRef() { return retryPolicyRef; }
    public void setRetryPolicyRef(String retryPolicyRef) { this.retryPolicyRef = retryPolicyRef; }

    public String getSideEffectScope() { return sideEffectScope; }
    public void setSideEffectScope(String sideEffectScope) { this.sideEffectScope = sideEffectScope; }

    public String getRollbackCapability() { return rollbackCapability; }
    public void setRollbackCapability(String rollbackCapability) { this.rollbackCapability = rollbackCapability; }

    /** 回滚能力读取端：存量行未声明 ⇒ NONE（既存 guess，读侧不替存量背书可回滚）。 */
    public String rollbackCapabilityResolved() {
        return rollbackCapability == null || rollbackCapability.isBlank() ? ROLLBACK_NONE : rollbackCapability;
    }

    public String getRollbackRef() { return rollbackRef; }
    public void setRollbackRef(String rollbackRef) { this.rollbackRef = rollbackRef; }

    public String getAuditEventType() { return auditEventType; }
    public void setAuditEventType(String auditEventType) { this.auditEventType = auditEventType; }

    public String getOwnerEngine() { return ownerEngine; }
    public void setOwnerEngine(String ownerEngine) { this.ownerEngine = ownerEngine; }
}
