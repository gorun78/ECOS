package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 工作流定义（Workflow Definition）新增/编辑 DTO — T16-2。
 *
 * <p>字段对应 {@code WorkflowService.createWorkflow / updateWorkflow} 实际消费的业务语义：
 * 新增时 {@code name} 必填，其余可选；更新时所有字段可空（null 表示不动）。
 *
 * <p>{@code nodes} / {@code edges} 既可传 String（JSON 字符串）也可传 List/Map（自动序列化）。
 */
@Data
public class OntologyWorkflowSaveDTO {

    /** 工作流名称（新增必填；更新时 null 不动） */
    private String name;

    /** 描述（更新时 null 不动） */
    private String description;

    /** 模式（默认 sequential） */
    private String mode;

    /** 节点定义（JSON 字符串或结构化集合） */
    private Object nodes;

    /** 边定义（JSON 字符串或结构化集合） */
    private Object edges;
}
