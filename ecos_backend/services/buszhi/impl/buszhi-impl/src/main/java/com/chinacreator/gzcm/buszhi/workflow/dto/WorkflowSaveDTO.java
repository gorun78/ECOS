package com.chinacreator.gzcm.buszhi.workflow.dto;

/**
 * 工作流定义（Workflow Definition）新增/编辑 DTO — 强类型入参。
 * （溯源：ontology-engine-impl T16-4 等价移植，PMO-57 T2 迁入 buszhi 服务层。）
 *
 * <p>字段对应 {@code WorkflowService.createWorkflow / updateWorkflow} 实际消费的业务语义：
 * 新增时 {@code name} 必填，其余可选；更新时所有字段可空（null 表示不动）。
 *
 * <p>{@code nodes} / {@code edges} 既可传 String（JSON 字符串）也可传 List/Map（自动序列化）。
 */
public class WorkflowSaveDTO {

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

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
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
}
