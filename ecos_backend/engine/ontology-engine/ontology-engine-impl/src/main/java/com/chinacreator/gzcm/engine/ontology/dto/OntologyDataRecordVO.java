package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;

/**
 * 本体数据实例记录视图（{@code /api/v1/ontology/data} 读面 / 建后回执 / 更新回执共用）。
 *
 * <p>键面：{@code id/objectTypeId/objectTypeName[/recordKey]/[status]/properties[/createdBy]
 * [/createdAt][/updatedAt]}。三腿共用一条记录但落键位置一致：</p>
 * <ul>
 *   <li>读面（PG 行）→ {@code recordKey/status} 在前、{@code properties} 居中、时间在后；</li>
 *   <li>创建回执 → {@code status} 恒为尾键（{@code recordKey/updatedAt} 不出现）；</li>
 *   <li>更新回执 → {@code updatedAt} 恒为尾键（{@code recordKey/status} 不出现）。</li>
 * </ul>
 *
 * <p>{@code properties} 是对象类型自定义属性（EAV），值域不冻结；{@code recordKey/createdBy/
 * createdAt/updatedAt} 为条件键（无值即省略），{@code properties} 恒出现（缺省空对象）。</p>
 */
@JsonPropertyOrder({"id", "objectTypeId", "objectTypeName", "recordKey", "status", "properties",
        "createdBy", "createdAt", "updatedAt"})
public record OntologyDataRecordVO(
        String id,
        String objectTypeId,
        String objectTypeName,
        @JsonInclude(JsonInclude.Include.NON_NULL) String recordKey,
        @JsonInclude(JsonInclude.Include.NON_NULL) String status,
        Map<String, Object> properties,
        @JsonInclude(JsonInclude.Include.NON_NULL) String createdBy,
        @JsonInclude(JsonInclude.Include.NON_NULL) String createdAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String updatedAt) {
}
