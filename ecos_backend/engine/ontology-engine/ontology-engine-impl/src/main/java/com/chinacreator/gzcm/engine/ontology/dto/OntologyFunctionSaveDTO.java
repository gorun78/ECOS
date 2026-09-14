package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * Function 测试请求 SaveDTO。
 *
 * <p>{@code POST /api/v1/ontology/functions/test} 请求体字段：
 * <ul>
 *   <li>{@code expression} — 必填，SQL 表达式（如 "SUM(amount) FROM t WHERE period='2026-07'"）</li>
 *   <li>{@code entityName} — 必填，FROM 子句表名</li>
 *   <li>{@code callerId} — 可选，调用方 id（默认 "anonymous"，写入审计日志）</li>
 * </ul>
 *
 * <p>T16-3 (2026-09-13)：入参由 {@code Map<String,Object>} 改强类型。
 * 返回 {@code FunctionResult}（已有强类型 POJO），保持原样。
 */
@Data
public class OntologyFunctionSaveDTO {

    /** SQL 表达式（必填） */
    private String expression;

    /** 目标实体名（必填） */
    private String entityName;

    /** 调用方 id（可选，默认 "anonymous"） */
    private String callerId;
}
