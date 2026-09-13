package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 工作流定义导出 VO — T16-4 强类型返回。
 *
 * <p>对齐 {@code WorkflowService.exportWorkflow} 输出：定义全量字段 +
 * {@code $schema} 扩展键（强类型 VO 无法声明 {@code $} 开头字段，
 * 统一走 {@code @JsonAnyGetter extras} 承载，T16-4: 导出 blob 动态结构豁免 Map）。
 * {@code nodes / edges} 元素为动态 node 定义
 * （T16-4: 导出 blob 动态结构豁免 Map）。
 *
 * <p>{@code $schema} 为 Java 标识字面量不可用的键名，以
 * {@code @JsonProperty("$schema")} 注解映射到 {@code schema} 字段；
 * {@code extras} 当前为空占位（保证 @JsonAnyGetter 存在，输出契约稳定）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowExportVO {

    /** $schema 等无法强类型化的扩展键（T16-4: 导出 blob 动态结构豁免 Map） */
    private java.util.Map<String, Object> extras = new java.util.LinkedHashMap<>();

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

    /** 节点定义（动态 node 集合，T16-4: 导出 blob 动态结构豁免 Map） */
    private Object nodes;

    /** 边定义（动态 edge 集合，T16-4: 导出 blob 动态结构豁免 Map） */
    private Object edges;

    /** 创建时间 ISO 字符串 */
    private String createdAt;

    /** 更新时间 ISO 字符串 */
    private String updatedAt;

    /**
     * 承载 $schema 等扩展键的映射器：service 原始 Map 中 $ 开头键之外的
     * 不可映射残留统一收敛到此处（当前实现中 $schema 已由 schema 字段
     * @JsonProperty("$schema") 强类型承载，extras 保持空 Map 占位以对齐
     * 既有"无额外键"输出）。
     */
    @JsonAnyGetter
    public java.util.Map<String, Object> getExtras() {
        return this.extras;
    }
}
