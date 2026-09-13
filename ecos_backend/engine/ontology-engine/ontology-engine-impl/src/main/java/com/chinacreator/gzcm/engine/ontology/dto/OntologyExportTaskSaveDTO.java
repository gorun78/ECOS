package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 本体导出任务创建入参 DTO — T16-4。
 *
 * <p>对应 {@code OntologyExportController.createExport} body 字段：
 * {@code ontologyId}（必填）、{@code format}（可选，默认 JSON）、
 * {@code scope}（可选，默认 FULL）。
 */
@Data
public class OntologyExportTaskSaveDTO {

    /** 所属本体 ID（必填） */
    private String ontologyId;

    /** 导出格式（可选，默认 JSON；JSON / CSV / DDL） */
    private String format;

    /** 导出范围（可选，默认 FULL；FULL / ENTITIES / RELATIONSHIPS） */
    private String scope;
}
