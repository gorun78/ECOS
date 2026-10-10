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
}
