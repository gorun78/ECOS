package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 动作类型执行结果 VO — T16-5 强类型返回容器。
 *
 * <p>对齐 {@code ActionTypeServiceImpl.executeAction} 既有 Map 输出契约：
 * {@code actionId / objectId / preconditionCheck / execution / postActions / auditId}。
 *
 * <p>T16-5: 执行动态结构豁免 — {@code preconditionCheck}（PreconditionEngine 输出）、
 * {@code execution}（执行明细，含 success/changes/message）、{@code postActions}
 * （后置动作结果集合）均为运行时动态结构，保持 {@code Object} 承载
 * （Jackson Map→VO 转换，POJO 序列化 == Map 序列化，API 输出契约严格等价）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActionTypeExecuteVO {

    /** 动作类型 ID */
    private String actionId;

    /** 对象 ID（入参透传） */
    private String objectId;

    /** 前置条件检查结果（动态结构，T16-5 执行动态结构豁免） */
    private Object preconditionCheck;

    /** 执行明细（动态结构，T16-5 执行动态结构豁免） */
    private Object execution;

    /** 后置动作结果（动态结构，T16-5 执行动态结构豁免） */
    private Object postActions;

    /** 审计 ID（audit_ 前缀） */
    private String auditId;
}
