package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;

/**
 * 本体数据实例创建回执（{@code POST /api/v1/ontology/data}）。
 *
 * <p>键序与读面记录不同：{@code status} 恒为尾键、且不带 {@code recordKey/updatedAt}
 * （与旧 Map 时代逐字一致）。{@code properties} 为对象类型自定义属性，原样透传。</p>
 */
@JsonPropertyOrder({"id", "objectTypeId", "objectTypeName", "properties", "createdBy",
        "createdAt", "status"})
public record OntologyDataCreateVO(
        String id,
        String objectTypeId,
        String objectTypeName,
        Map<String, Object> properties,
        String createdBy,
        String createdAt,
        String status) {
}
