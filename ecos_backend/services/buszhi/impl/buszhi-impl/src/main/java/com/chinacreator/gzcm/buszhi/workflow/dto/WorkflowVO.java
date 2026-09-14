package com.chinacreator.gzcm.buszhi.workflow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 工作流定义（Workflow Definition）VO — 强类型返回。
 *
 * <p>字段对齐 {@code WorkflowService.toMap(WorkflowEntity)} 输出。
 * 注意 {@code nodes} / {@code edges} 在持久层为 JSON 字符串，
 * 此处保持 String 形态以与 service 层输出一致（反序列化由前端负责）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowVO {

    /** 工作流 ID（wf 前缀） */
    private String id;

    /** 工作流名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 状态（draft / published / ...） */
    private String status;

    /** 模式（sequential / parallel） */
    private String mode;

    /** 节点定义（JSON 字符串） */
    private String nodes;

    /** 边定义（JSON 字符串） */
    private String edges;

    /** 发布时间 ISO 字符串 */
    private String publishedAt;

    /** 创建时间 ISO 字符串 */
    private String createdAt;

    /** 更新时间 ISO 字符串 */
    private String updatedAt;

    public WorkflowVO() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getNodes() {
        return nodes;
    }

    public void setNodes(String nodes) {
        this.nodes = nodes;
    }

    public String getEdges() {
        return edges;
    }

    public void setEdges(String edges) {
        this.edges = edges;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(String publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
