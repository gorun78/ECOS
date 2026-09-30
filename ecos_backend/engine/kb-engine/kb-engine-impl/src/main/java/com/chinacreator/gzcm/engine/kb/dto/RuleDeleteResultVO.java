package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 合规规则删除结果 VO（PMO-74 H11-T4）。
 *
 * <p>替代 {@code ComplianceRuleController#delete} 原裸返
 * {@code Map.of("deleted", id)}，JSON 形态不变：{@code {"deleted":"<ruleId>"}}。</p>
 */
@Data
public class RuleDeleteResultVO {

    /** 被删除的规则 id（沿用原 Map key "deleted"，值语义为 id） */
    private String deleted;

    public RuleDeleteResultVO() {
    }

    public RuleDeleteResultVO(String deleted) {
        this.deleted = deleted;
    }
}
