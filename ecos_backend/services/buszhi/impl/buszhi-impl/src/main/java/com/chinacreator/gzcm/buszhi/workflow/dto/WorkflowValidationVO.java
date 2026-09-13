package com.chinacreator.gzcm.buszhi.workflow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 工作流定义验证结果 VO — 强类型返回。
 * （溯源：ontology-engine-impl T16-4 等价移植，PMO-57 T2 迁入 buszhi 服务层。）
 *
 * <p>对齐 {@code WorkflowValidationService.ValidationResult.toMap()} 输出
 * （{@code valid / errors / warnings / suggestions}）。
 * 错误/警告/建议条目为 {@code {code?, nodeId?, message}} 动态 KV 集合
 * （workflow 校验条目动态结构豁免），保持 {@code List<Object>}。
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class WorkflowValidationVO {

    /** 验证是否通过 */
    private Boolean valid;

    /** 错误条目（workflow 校验条目动态结构豁免） */
    private List<Object> errors;

    /** 警告条目（workflow 校验条目动态结构豁免） */
    private List<Object> warnings;

    /** 建议条目（workflow 校验条目动态结构豁免） */
    private List<Object> suggestions;

    public WorkflowValidationVO() {
    }

    public Boolean getValid() {
        return valid;
    }

    public void setValid(Boolean valid) {
        this.valid = valid;
    }

    public List<Object> getErrors() {
        return errors;
    }

    public void setErrors(List<Object> errors) {
        this.errors = errors;
    }

    public List<Object> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<Object> warnings) {
        this.warnings = warnings;
    }

    public List<Object> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<Object> suggestions) {
        this.suggestions = suggestions;
    }
}
