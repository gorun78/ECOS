package com.chinacreator.gzcm.engine.kb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 知识资产生命周期条目 VO — {@code GET /api/v1/knowledge/assets} 出参
 * （方案 §6.2 K6：draft/active/deprecated/archived）。
 *
 * <p>字段名与前端 {@code LifecycleAsset}（typesAndConstants.ts）对齐。</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LifecycleAssetVO {

    /** 资产 ID */
    private String id;

    /** 资产名称 */
    private String name;

    /** 资产类型（如 ARTICLE） */
    private String type;

    /** 生命周期状态：draft / active / deprecated / archived */
    private String state;

    /** 最近更新时间（ISO-8601） */
    private String updatedAt;

    /** 最近更新人（当前数据源无该列，恒为 null） */
    private String updatedBy;
}
