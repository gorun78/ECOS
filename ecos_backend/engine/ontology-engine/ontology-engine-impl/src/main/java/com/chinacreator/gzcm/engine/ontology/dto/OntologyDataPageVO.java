package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * 本体数据实例分页视图（{@code GET /api/v1/ontology/data}）：
 * {@code data/total/page/size/totalPages}，{@code totalPages} 由 {@code ceil(total/size)} 计算。
 */
@JsonPropertyOrder({"data", "total", "page", "size", "totalPages"})
public record OntologyDataPageVO(
        List<OntologyDataRecordVO> data,
        int total,
        int page,
        int size,
        int totalPages) {
}
