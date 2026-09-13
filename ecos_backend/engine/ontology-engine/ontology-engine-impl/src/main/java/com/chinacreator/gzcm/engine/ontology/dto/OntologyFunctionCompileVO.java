package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Function 编译结果 VO。
 *
 * <p>{@code POST /api/v1/ontology/functions/compile} 返回。
 *
 * <p>T16-3 (2026-09-13)：动态 payload 局部豁免说明——{@code params}
 * 为编译期提取的 SQL 占位参数列表（{@code List<Object>}，类型未知，
 * 数值/字符串混合），由 {@code FunctionSandboxEngine.compile} 动态生成，
 * 属"函数沙箱运行时 payload"，保留 {@code Object} 类型。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyFunctionCompileVO {

    /** 编译生成的参数化 SQL */
    private String sql;

    /** SQL 占位参数列表（动态类型，函数沙箱运行时 payload 豁免） */
    private Object params;

    /** FROM 子句实体名 */
    private String entityName;
}
