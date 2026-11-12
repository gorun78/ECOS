package com.chinacreator.gzcm.engine.ai.dataobject;

import java.time.LocalDateTime;

/**
 * V329 {@code ecos_ai.ecos_aip_pipeline} AI 工作台逻辑编排定义 DO。
 *
 * <p>{@code definition_json} 承载画布完整定义（nodes/edges/steps 的 JSON 序列化产物，
 * JSON 语义 TEXT——MC02）；{@code template_ref} 记录来源 Playbook 的 Git 引用
 * （{@code *_git_ref → VARCHAR(120)}，可空=手工创建），批 2 Wagent 模板融合追溯位。</p>
 *
 * <p>通用列口径对齐 DR05~DR08：tenant_id/version_no/create_time/update_time/
 * create_by/update_by/is_deleted；UNIQUE(name, version_no, is_deleted)。</p>
 */
public class AipPipelineDO {

    private String id;
    private String tenantId;
    private String name;
    private String description;
    private String definitionJson;
    private String status;
    private String templateRef;
    private String versionNo;
    private LocalDateTime lastExecutedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private String updateBy;
    private Integer isDeleted;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDefinitionJson() { return definitionJson; }
    public void setDefinitionJson(String definitionJson) { this.definitionJson = definitionJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTemplateRef() { return templateRef; }
    public void setTemplateRef(String templateRef) { this.templateRef = templateRef; }

    public String getVersionNo() { return versionNo; }
    public void setVersionNo(String versionNo) { this.versionNo = versionNo; }

    public LocalDateTime getLastExecutedAt() { return lastExecutedAt; }
    public void setLastExecutedAt(LocalDateTime lastExecutedAt) { this.lastExecutedAt = lastExecutedAt; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }

    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }

    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }
}
