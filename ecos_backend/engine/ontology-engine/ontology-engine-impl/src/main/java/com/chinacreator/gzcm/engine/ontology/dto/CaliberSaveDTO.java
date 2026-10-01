package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * F03-01 — 新建 DRAFT 口径入参（{@code POST /api/v1/ontology/calibers}）。
 */
@Data
public class CaliberSaveDTO {
    /** 口径业务编码（外部检索键，唯一）。 */
    private String code;
    /** 口径名称。 */
    private String name;
    /** 单位。 */
    private String unit;
    /** 币种。 */
    private String currency;
    /** 口径责任角色。 */
    private String ownerRole;
    /** 维度 JSON（TEXT）。 */
    private String dimensionJson;
    /** 状态；缺省 ACTIVE。 */
    private String status;
}
