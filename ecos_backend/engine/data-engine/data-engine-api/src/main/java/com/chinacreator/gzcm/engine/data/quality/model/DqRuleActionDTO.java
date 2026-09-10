package com.chinacreator.gzcm.engine.data.quality.model;

import lombok.Data;

/**
 * DQ 规则状态机操作请求体 — 统一承载 submit/approve/reject/deprecate/supersede/disable 的入参。
 * <p>
 * 不同动作使用不同字段，未用字段保持为 null 即可：
 * <ul>
 *   <li>submit:     submitter</li>
 *   <li>approve:    approver</li>
 *   <li>reject:     rejector + reason</li>
 *   <li>deprecate:  operator</li>
 *   <li>supersede:  operator + reason</li>
 *   <li>disable:    operator</li>
 * </ul>
 * </p>
 *
 * @author PMO-48-B T7c
 */
@Data
public class DqRuleActionDTO {

    /** 提交人（submit 动作必填） */
    private String submitter;

    /** 审批人（approve 动作必填） */
    private String approver;

    /** 驳回人（reject 动作必填） */
    private String rejector;

    /** 操作人（deprecate/supersede/disable 动作必填） */
    private String operator;

    /** 理由（reject/supersede 可选） */
    private String reason;
}
