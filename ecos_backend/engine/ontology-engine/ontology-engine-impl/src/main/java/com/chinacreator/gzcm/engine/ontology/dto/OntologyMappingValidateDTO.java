package com.chinacreator.gzcm.engine.ontology.dto;

import java.util.List;

import lombok.Data;

/**
 * 映射一致性校验（方案 §2.3 C4 映射有效性）入参 —
 * {@code POST /api/v1/ontology/mappings/validate}。
 *
 * <p>PMO-B2 T2。校验范围按优先级解析：
 * <ol>
 *   <li>{@link #mappingId} 非空 → 仅校验该条已存映射；</li>
 *   <li>{@link #fieldMappings} 非空 → 保存前内联校验（配合 {@link #datasetId} 定位 DW 资源）；</li>
 *   <li>均空 → 校验全部已存映射（{@link #entityCode} 非空时按实体过滤）。</li>
 * </ol>
 */
@Data
public class OntologyMappingValidateDTO {

    /** 已存映射主键 id；指定时仅校验该条 */
    private String mappingId;

    /** 本体实体 id 或 code（对应 {@code entity_code}）；批量校验时用于过滤 */
    private String entityCode;

    /** 目标 DW 数据资源 id（{@code td_data_resource.resource_id}）；保存前内联校验用 */
    private String datasetId;

    /** 内联字段映射（保存前校验用） */
    private List<FieldMappingItemDTO> fieldMappings;
}
