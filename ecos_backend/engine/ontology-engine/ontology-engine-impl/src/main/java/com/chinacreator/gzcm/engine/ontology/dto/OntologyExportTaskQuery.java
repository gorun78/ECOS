package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 本体导出任务列表查询 Query — T16-4。
 *
 * <p>对齐 {@code OntologyExportController（/api/v1/ontology/export/tasks）}
 * 既有 3 个可选过滤参数：{@code ontologyId} / {@code format} / {@code status}，
 * 全部可空（null = 不过滤）。
 */
@Data
public class OntologyExportTaskQuery {

    /** 按本体 ID 过滤（可选） */
    private String ontologyId;

    /** 按导出格式过滤（可选；JSON / CSV / DDL） */
    private String format;

    /** 按任务状态过滤（可选；COMPLETED / FAILED） */
    private String status;
}
