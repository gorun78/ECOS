package com.chinacreator.gzcm.engine.cognitive2.model;

import java.util.Date;
import java.util.List;

/**
 * 认知管线数据库实体 — 对应 kb_cognitive_pipeline 表。
 *
 * <p>用于 JdbcTemplate RowMapper 映射，nodes 以 JSON 字符串中转，
 * 由 {@link com.chinacreator.gzcm.engine.cognitive2.service.CognitivePipelineRepository}
 * 负责与 {@link CognitivePipeline} 的相互转换。</p>
 */
public class CognitivePipelineEntity {

    /** 自增主键 */
    private Long id;
    /** 管线业务 ID (UUID，不含连字符) */
    private String pipelineId;
    /** 管线名称 */
    private String name;
    /** 管线状态: DRAFT/ACTIVE/ARCHIVED */
    private String status;
    /** 节点集合 JSON 字符串（[{nodeId, nodeType, config, dependsOn}, ...]） */
    private String config;
    /** 描述 */
    private String description;
    /** 创建人 */
    private String createdBy;
    /** 创建时间 */
    private Date createdAt;
    /** 更新时间 */
    private Date updatedAt;
    /** 最近执行结果 JSON 字符串（可为 null） */
    private String result;
    /** 逻辑删除标记: 0=未删除, 1=已删除 */
    private Integer isDeleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }
}
