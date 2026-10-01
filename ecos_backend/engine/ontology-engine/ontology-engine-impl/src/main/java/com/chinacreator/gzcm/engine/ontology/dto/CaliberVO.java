package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * F03-01 — 口径列表/详情 VO。
 *
 * <p>字段对齐 {@code CaliberService} 显式列清单输出（{@link java.util.Map} 直传，
 * 避免引入实体类）；{@code versions} 仅在
 * {@code GET /api/v1/ontology/calibers/{code}} 详情端点填充（升版历史）。</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CaliberVO {

    /** 口径主键（VARCHAR(36) 应用侧 UUID）。 */
    private String id;
    /** 口径业务编码。 */
    private String code;
    /** 名称。 */
    private String name;
    /** 状态（ACTIVE/…）。 */
    private String status;
    /** 单位。 */
    private String unit;
    /** 币种。 */
    private String currency;
    /** 责任角色。 */
    private String ownerRole;
    /** 维度 JSON（TEXT）。 */
    private String dimensionJson;
    /** 域。 */
    private String domain;
    /** 创建时间。 */
    private Object createTime;
    /** 版本历史（仅详情端点填充）。 */
    private List<Map<String, Object>> versions;

    /** 便捷构造：主表行 → VO。 */
    public static CaliberVO of(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        CaliberVO vo = new CaliberVO();
        vo.id = toString(row.get("id"));
        vo.code = toString(row.get("code"));
        vo.name = toString(row.get("name"));
        vo.status = toString(row.get("status"));
        vo.unit = toString(row.get("unit"));
        vo.currency = toString(row.get("currency"));
        vo.ownerRole = toString(row.get("owner_role"));
        vo.dimensionJson = toString(row.get("dimension_json"));
        vo.domain = toString(row.get("domain"));
        vo.createTime = row.get("create_time");
        return vo;
    }

    private static String toString(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
