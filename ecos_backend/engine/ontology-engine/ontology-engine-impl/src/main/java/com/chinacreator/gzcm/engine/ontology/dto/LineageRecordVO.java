package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * 内存血缘边记录（CRUD 与图/追溯端点共用）。
 *
 * <p>键序：{@code id/source/target/lineageType[/transform][/description][/eventId]/
 * createdAt/updatedAt}。条件键说明：{@code transform/description} 仅手工建边腿出现；
 * {@code eventId} 仅 SQL/OpenLineage 解析腿出现（两腿记录同表，键面按腿收敛）。</p>
 */
@JsonPropertyOrder({"id", "source", "target", "lineageType", "transform", "description",
        "eventId", "createdAt", "updatedAt"})
public record LineageRecordVO(
        String id,
        String source,
        String target,
        String lineageType,
        @JsonInclude(JsonInclude.Include.NON_NULL) String transform,
        @JsonInclude(JsonInclude.Include.NON_NULL) String description,
        @JsonInclude(JsonInclude.Include.NON_NULL) String eventId,
        String createdAt,
        String updatedAt) {

    /** 更新腿：仅覆盖请求体出现的字段（与原 Map 部分更新语义一致）。 */
    public LineageRecordVO withUpdated(String newSource, String newTarget, String newLineageType,
                                       String newTransform, String newDescription,
                                       String newUpdatedAt) {
        return new LineageRecordVO(id,
                newSource != null ? newSource : source,
                newTarget != null ? newTarget : target,
                newLineageType != null ? newLineageType : lineageType,
                newTransform != null ? newTransform : transform,
                newDescription != null ? newDescription : description,
                eventId, createdAt, newUpdatedAt);
    }
}
