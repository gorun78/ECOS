package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * Function 沙箱执行/编译请求 Query。
 *
 * <p>对应端点：
 * <ul>
 *   <li>{@code POST /api/v1/ontology/functions/test} — {@code expression} 必填、
 *       {@code entityName} 必填、{@code callerId} 可选（默认 "anonymous"）</li>
 *   <li>{@code POST /api/v1/ontology/functions/compile} — {@code expression}
 *       必填、{@code entityName} 必填</li>
 * </ul>
 *
 * <p>T16-3 (2026-09-13)：入参由 {@code Map<String,Object>} 改强类型。
 * 返回 {@code FunctionResult} / compile 动态 Map（{@code params} List）
 * 属函数沙箱运行时 payload 动态结构豁免。
 */
@Data
public class OntologyFunctionExecuteQuery {

    /** Function SQL 表达式（必填），如 "SUM(amount) FROM fin_revenue WHERE period='2026-07'" */
    private String expression;

    /** 目标实体名（必填，FROM 子句表名） */
    private String entityName;

    /** 调用方 id（可选，默认 "anonymous"，写入审计日志） */
    private String callerId;
}
