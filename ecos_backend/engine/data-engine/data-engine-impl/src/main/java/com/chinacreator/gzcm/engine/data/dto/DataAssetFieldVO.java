package com.chinacreator.gzcm.engine.data.dto;

import lombok.Data;

import java.util.List;

/**
 * 字段级敏感度 VO（出参，强类型 —— /assets/{id}/fields 列表使用）。
 *
 * @author DataBridge Datanet Team
 */
@Data
public class DataAssetFieldVO {

    /** 字段资产链路唯一 ID（ecos_data_asset_field.field_asset_id） */
    private String fieldAssetId;

    /** 资产 ID */
    private String assetId;

    /** 字段 ID（→ td_data_field.field_id） */
    private String fieldId;

    /** 字段名 */
    private String fieldName;

    /** 字段类型（VARCHAR / INT 等） */
    private String fieldType;

    /** 数据类型（ID_CARD / PHONE / EMAIL / BANK_CARD / AMOUNT / ADDRESS / GENERAL） */
    private String dataType;

    /** 字段敏感度（L1 / L2 / L3 / L4） */
    private String fieldSensitivity;

    /** 脱敏策略（none / middle4 / prefix3 / suffix4 / full） */
    private String maskStrategy;

    /** 推荐级别（LLM/规则/人工产出） */
    private String recommendLevel;

    /** 推荐来源（LLM / RULE / MANUAL） */
    private String recommendSource;

    /** 是否人工确认 */
    private Boolean confirmed;
}
