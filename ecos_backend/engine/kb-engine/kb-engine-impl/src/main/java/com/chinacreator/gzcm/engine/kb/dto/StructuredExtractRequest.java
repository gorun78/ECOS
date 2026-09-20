package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 结构化（映射驱动）实例抽取触发请求（K1 批次 E3 端点入参）。
 *
 * <p>强类型入参（禁 {@code Map<String,Object>}）：指定本体范围与抽取模式。</p>
 */
@Data
public class StructuredExtractRequest {

    /** 本体业务 ID；空 / ALL 表示按 kb_ontology_snapshot 全量本体 */
    private String ontologyId;

    /** 抽取模式：FULL（全量）/ INCREMENTAL（增量，默认 INCREMENTAL） */
    private String mode;

    /** 是否 dry-run 预览（true = 只统计不落库） */
    private Boolean dryRun;
}
