package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 工作流审批结果 VO — T16-2 强类型返回。
 *
 * <p>字段对齐 {@code WorkflowApprovalService.approve / reject} 输出：
 * approvalId / decision。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyWorkflowApprovalVO {

    /** 审批记录 ID（appr 前缀） */
    private String approvalId;

    /** 审批决策（Approved / Rejected） */
    private String decision;
}
