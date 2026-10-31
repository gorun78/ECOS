package com.chinacreator.gzcm.engine.ontology.model;

import java.time.LocalDateTime;

/**
 * 规则持久化 POJO — 对应 ecos_ontology_rule 表
 *
 * <p>#147B（R10/R11 契约面）：承载双维路由 {@code ruleCarrier/ruleLayer}（值域闭集见
 * {@code OntologyRuleService}，I-3）与发布就绪四件套 {@code scope/versionNo/gitRef} +
 * 生命周期 {@code status}（发布门经 PUT status=PUBLISHED 触发，缺 {@code version_no}/
 * {@code git_ref} 拒发布，见册 §6.2.6⑧）。列名断裂订正：时间戳读写对齐 live 列
 * {@code create_time/update_time}（原 Dao 读写不存在的 {@code created_at/updated_at}，
 * live 42703 复证）。</p>
 */
public class OntologyRule {

    private String id;
    private String entityId;
    private String code;
    private String name;
    private String ruleType;     // VALIDATION / CALCULATION / DECISION / AGENT
    private String expression;
    private String action;
    private int priority;
    private int enabled;
    private String description;
    private String ruleCarrier;  // ONTOLOGY_ELEMENT / RULE_OBJECT / KNOWLEDGE_ASSET（I-3 闭集）
    private String ruleLayer;    // STRUCTURAL / VALIDATION / BUSINESS / POLICY / COGNITIVE（与 ruleType 正交）
    private String scope;        // 租户/组织/领域
    private String versionNo;    // 发布就绪件套之一（缺则拒发布）
    private String gitRef;       // Git 归档单通道引用（缺则拒发布）
    private String status;       // ACTIVE / PUBLISHED / ARCHIVED（16 态单源属 #147D，本批只承读写）
    private String domain;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public OntologyRule() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }

    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public int getEnabled() { return enabled; }
    public void setEnabled(int enabled) { this.enabled = enabled; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getRuleCarrier() { return ruleCarrier; }
    public void setRuleCarrier(String ruleCarrier) { this.ruleCarrier = ruleCarrier; }

    public String getRuleLayer() { return ruleLayer; }
    public void setRuleLayer(String ruleLayer) { this.ruleLayer = ruleLayer; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getVersionNo() { return versionNo; }
    public void setVersionNo(String versionNo) { this.versionNo = versionNo; }

    public String getGitRef() { return gitRef; }
    public void setGitRef(String gitRef) { this.gitRef = gitRef; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
