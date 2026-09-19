package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 映射校验入参 — 单条字段映射（物理列 ↔ 本体属性）。
 *
 * <p>PMO-B2 T2（C4 映射有效性）。历史数据中两种书写方向并存
 * （{@code source=列名 / target=属性} 与 {@code source=属性 / target=列名}），
 * 校验侧按 DW 列定义自动判定哪一侧是物理列，故两侧字段均可选。
 */
@Data
public class FieldMappingItemDTO {

    /** 映射一侧标识（物理列名或本体属性 id/code） */
    private String source;

    /** 映射另一侧标识（与 {@link #source} 相对） */
    private String target;

    /** 兼容键：物理列名（自动发现写入的 field 键） */
    private String field;

    /** 兼容键：本体属性 id/code（自动发现写入的 propertyCode 键） */
    private String propertyCode;
}
