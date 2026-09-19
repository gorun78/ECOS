package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 映射校验问题项 — 对应方案 §2.3 C4 的单条失败明细。
 *
 * <p>PMO-B2 T2。{@link #code} 取值：
 * <ul>
 *   <li>{@code TABLE_NOT_FOUND} — 映射目标 DW 表不在 CURATED 层；</li>
 *   <li>{@code COLUMN_NOT_FOUND} — 映射列在 DW 表中不存在；</li>
 *   <li>{@code TYPE_INCOMPATIBLE} — DW 列类型与本体属性类型不兼容；</li>
 *   <li>{@code METADATA_UNAVAILABLE} — data-engine 元数据不可用或未采集（默认拒绝，不降级放行）；</li>
 *   <li>{@code MAPPING_NOT_FOUND} — 指定的映射主键不存在。</li>
 * </ul>
 */
@Data
public class OntologyMappingIssueVO {

    /** 本体实体 id/code（{@code entity_code}） */
    private String entityCode;

    /** 目标 DW 表名 */
    private String tableName;

    /** 问题列名（表级问题为 null） */
    private String columnName;

    /** 问题码（见类注释枚举） */
    private String code;

    /** 可读说明 */
    private String message;

    public OntologyMappingIssueVO() {
    }

    public OntologyMappingIssueVO(String entityCode, String tableName, String columnName,
                                  String code, String message) {
        this.entityCode = entityCode;
        this.tableName = tableName;
        this.columnName = columnName;
        this.code = code;
        this.message = message;
    }
}
