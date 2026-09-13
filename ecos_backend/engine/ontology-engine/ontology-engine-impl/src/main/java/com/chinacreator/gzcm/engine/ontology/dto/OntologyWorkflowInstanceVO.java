package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 工作流实例（Workflow Instance）VO — T16-2 强类型返回。
 *
 * <p>字段对齐 {@code WorkflowInstanceService.toMap(WorkflowInstanceEntity)} 输出。
 * 启动实例（{@code startInstance}）额外返回 {@code definition}（子集定义摘要）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyWorkflowInstanceVO {

    /** 实例 ID（pi 前缀） */
    private String id;

    /** 所属工作流 ID */
    private String workflowId;

    /** 所属工作流名称 */
    private String workflowName;

    /** 状态（Running / Suspended / Completed / Failed / Terminated） */
    private String status;

    /** 触发类型（MANUAL / SCHEDULED） */
    private String triggerType;

    /** 触发者 */
    private String triggeredBy;

    /** 开始时间 ISO 字符串 */
    private String startedAt;

    /** 完成时间 ISO 字符串 */
    private String completedAt;

    /** 错误消息 */
    private String errorMessage;

    /** 创建时间 ISO 字符串 */
    private String createdAt;

    /**
     * 关联的工作流定义摘要（仅 {@code startInstance} 返回时填充）。
     * 嵌套子集动态摘要，保持 Object 类型（与既有 Map 内 {@code definition} 子结构对齐）。
     */
    private Object definition;
}
