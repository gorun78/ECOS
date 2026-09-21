package com.chinacreator.gzcm.sysman.dto;

/**
 * 安全策略上/改 DTO — name/domain/policyExpr/priority。
 * <p>替代 SecurityPolicyController.create/update 原 Map&lt;String,Object&gt; 入参（P0-3 P1-2 集中修）。
 * 不使用 Lombok（sysman-impl 无 Lombok 依赖），手写 getter/setter（与 workspace-impl DTO 风格一致）。
 */
public class SkySecurityPolicyUpsertDTO {

    /** 策略名称（create 必填） */
    private String name;

    /** 业务域（可选） */
    private String domain;

    /** ABAC 策略表达式（create 必填） */
    private String policyExpr;

    /** 优先级（可选，默认 100） */
    private Integer priority;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getPolicyExpr() { return policyExpr; }
    public void setPolicyExpr(String policyExpr) { this.policyExpr = policyExpr; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
}
