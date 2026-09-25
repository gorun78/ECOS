package com.chinacreator.gzcm.engine.data.dto;

import lombok.Data;
import java.util.List;

/**
 * 资产字段级敏感度打标请求 — 人工确认后落库 + 发 Kafka 事件（PMO-data10 范围B）。
 * <p>PredictScriptVO 的姊妹类 — 传入的 {@code fields} 全量确认后才写库、
 * 生成 RFC 事件；未 {@code confirmed=true} 的记录只写 recommend_ 字段，不触发 CLS/RLS。</p>
 */
@Data
public class AssetSecurityTagRequest {

    /** 是否人工确认（true 才多发 ecos.data.security-tagged 事件） */
    private Boolean confirmed = Boolean.TRUE;

    /** 操作者（前端从 JWT 取） */
    private String operator;

    /** 打标的字段清单（一条记录一个字段） */
    private List<FieldTag> fields;

    @Data
    public static class FieldTag {
        /** 字段 ID（ecos_data_asset_field.field_asset_id），若为空则从 fieldName 取 */
        private String fieldId;

        /** 字段名（读库后才能完成 INSERT UPDATE to ecos_data_asset_field） */
        private String fieldName;

        /** 敏感度 L1-L4 */
        private String fieldSensitivity;

        /** 数据类型（GENERAL/ID_CARD/PHONE/EMAIL/BANK_CARD/AMOUNT/ADDRESS） */
        private String dataType;

        /** 脱敏策略（none/prefix3/suffix4/middle4/full） */
        private String maskStrategy;

        /** 推荐等级（可空） */
        private String recommendLevel;

        /** 推荐来源（MANUAL/LLM） */
        private String recommendSource;
    }
}
