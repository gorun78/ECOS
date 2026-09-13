package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 工作流定义（Workflow Definition）VO — T16-2 强类型返回。
 *
 * <p>字段对齐 {@code WorkflowService.toMap(WorkflowEntity)} 输出。
 * 注意 {@code nodes} / {@code edges} 在持久层为 JSON 字符串，
 * 此处保持 String 形态以与 service 层输出一致（反序列化由前端负责）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyWorkflowVO {

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
}
