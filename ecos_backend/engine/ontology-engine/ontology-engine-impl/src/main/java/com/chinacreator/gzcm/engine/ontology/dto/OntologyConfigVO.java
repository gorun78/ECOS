package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 本体配置项 VO。
 *
 * <p>字段对齐 {@code SysConfigService.listByGroup} 返回 row（PG
 * sys_config 表 SELECT * 投影）。
 *
 * <p>T16-3 (2026-09-13)：跟随 T16-1/2 命名先例。SysConfigService 在
 * sysman-impl 横切底座（不动），返回 raw Map；本 VO 在 Controller 层
 * 从 Map 重建，字段白名单覆盖 sys_config 表常用列，未知列忽略。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyConfigVO {

    /** 主键 UUID */
    private String id;

    /** 配置 key */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 配置分组（如 ontology-engine） */
    private String configGroup;

    /** 配置类型（string/int/bool/enum/password） */
    private String configType;

    /** 配置 label（展示名） */
    private String configLabel;

    /** 配置描述 */
    private String description;

    /** 排序权重 */
    private Integer sortOrder;

    /** 状态（active 等） */
    private String status;

    /** 适用档位（all/standard/enterprise/ultimate） */
    private String edition;

    /** 创建时间 ISO 字符串 */
    private String createdAt;

    /** 更新时间 ISO 字符串 */
    private String updatedAt;
}
