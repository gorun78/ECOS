package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 实体链接请求 DTO（PMO-74 H11-T4）。
 *
 * <p>替代 {@code EntityLinkController#linkEntity} 原 {@code @RequestBody Map<String,Object>}，
 * 线上 JSON 形态不变（仅识别 entityName/entityType 两键，未知键与 Map 版一样被忽略）。</p>
 */
@Data
public class EntityLinkRequestDTO {

    /** 实体名称（必填，如"应收账款"） */
    private String entityName;

    /** 实体类型（可选，缺省 "unknown"，与原 Map.getOrDefault 语义一致） */
    private String entityType;
}
