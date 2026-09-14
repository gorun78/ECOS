package com.chinacreator.gzcm.buszhi.workflow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工作流定义导出 VO — 强类型返回。
 * （溯源：ontology-engine-impl T16-4 等价移植，PMO-57 T2 迁入 buszhi 服务层。）
 *
 * <p>对齐 {@code WorkflowService.exportWorkflow} 输出：定义全量字段 +
 * {@code $schema} 扩展键（强类型 VO 无法声明 {@code $} 开头字段，
 * 统一走 {@code @JsonAnyGetter extras} 承载，导出 blob 动态结构豁免 Map）。
 * {@code nodes / edges} 元素为动态 node 定义
 * （导出 blob 动态结构豁免 Map）。
 *
 * <p>{@code $schema} 为 Java 标识字面量不可用的键名，以
 * {@code @JsonProperty("$schema")} 注解映射到 {@code schema} 字段；
 * Controller 转换时把 service 原始 Map 中未被命名字段声明的 $ 开头键
 * 收敛到 {@code extras}。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowExportVO {

    /** $schema 等无法强类型化的扩展键（导出 blob 动态结构豁免 Map） */
    private Map<String, Object> extras = new LinkedHashMap<>();

    /** 导出格式 schema URI（workflow-definition-v1.json，映射为输出键 $schema） */
    @com.fasterxml.jackson.annotation.JsonProperty("$schema")
    private String schema;

    /** 工作流 ID */
    private String id;

    /** 工作流名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 导出版本号（固定 1.0.0，service 常量） */
    private String version;

    /** 模式（sequential / parallel） */
    private String mode;

    /** 状态 */
    private String status;

    /** 节点定义（动态 node 集合，导出 blob 动态结构豁免 Map） */
    private Object nodes;

    /** 边定义（动态 edge 集合，导出 blob 动态结构豁免 Map） */
    private Object edges;

    /** 创建时间 ISO 字符串 */
    private String createdAt;

    /** 更新时间 ISO 字符串 */
    private String updatedAt;

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Object getNodes() {
        return nodes;
    }

    public void setNodes(Object nodes) {
        this.nodes = nodes;
    }

    public Object getEdges() {
        return edges;
    }

    public void setEdges(Object edges) {
        this.edges = edges;
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

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @com.fasterxml.jackson.annotation.JsonAnyGetter
    public Map<String, Object> getExtras() {
        return this.extras;
    }

    public void setExtras(Map<String, Object> extras) {
        this.extras = extras;
    }
}
