package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 工作流定义验证结果 VO — T16-4 强类型返回。
 *
 * <p>对齐 {@code WorkflowValidationService.ValidationResult.toMap()} 输出
 * （{@code valid / errors / warnings / suggestions}）。
 * 错误/警告/建议条目为 {@code {code?, nodeId?, message}} 动态 KV 集合
 * （T16-4: workflow 校验条目动态结构豁免），保持 {@code List<Object>}。
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class WorkflowValidationVO {

    /** 验证是否通过 */
    private Boolean valid;

    /** 错误条目（T16-4: workflow 校验条目动态结构豁免） */
    private List<Object> errors;

    /** 警告条目（T16-4: workflow 校验条目动态结构豁免） */
    private List<Object> warnings;

    /** 建议条目（T16-4: workflow 校验条目动态结构豁免） */
    private List<Object> suggestions;
}
