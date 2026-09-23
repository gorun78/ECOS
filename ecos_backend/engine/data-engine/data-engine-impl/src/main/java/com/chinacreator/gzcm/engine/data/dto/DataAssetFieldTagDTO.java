package com.chinacreator.gzcm.engine.data.dto;

import lombok.Data;

import java.util.List;

/**
 * 资产-字段敏感度标记 DTO（强类型 —— 后端规范 §4 接口禁用 Map）。
 *
 * <p>打标语义：</p>
 * <ul>
 *   <li>人工确认落库 → 触发 Kafka {@code ecos.data.security-tagged} 事件</li>
 *   <li>LLM 推荐仅写 recommend_level / recommend_source，confirmed 必须人工点头</li>
 * </ul>
 *
 * @author DataBridge Datanet Team
 */
@Data
public class DataAssetFieldTagDTO {

    /** 是否人工确认（true = 真落库生效并触发事件；false = 仅推荐，confirmed=false 不触发事件） */
    private Boolean confirmed;

    /** 字段打标条目列表（每次最多 100 条，IR06 批量上限） */
    private List<FieldTagItem> fields;

    /** 操作人（从 SecurityContext 取，前端不强求） */
    private String operator;

    /** 通用条目：单个字段的敏感度 + 脱敏策略 + 推荐来源。 */
    @Data
    public static class FieldTagItem {

        /** 字段 ID（指向 td_data_field.field_id） */
        private String fieldId;

        /** 字段名（冗余缓存，便于事件里展示） */
        private String fieldName;

        /** 字段类型（VARCHAR / INT / JSONB 等） */
        private String fieldType;

        /** 数据类型（ID_CARD / PHONE / EMAIL / BANK_CARD / AMOUNT / ADDRESS / GENERAL） */
        private String dataType;

        /** 字段敏感度（L1 / L2 / L3 / L4） */
        private String fieldSensitivity;

        /** 脱敏策略（none / middle4 / prefix3 / suffix4 / full） */
        private String maskStrategy;

        /** 推荐级别（LLM/规则产出，确认前不生效） */
        private String recommendLevel;

        /** 推荐来源（LLM / RULE / MANUAL） */
        private String recommendSource;
    }
}
