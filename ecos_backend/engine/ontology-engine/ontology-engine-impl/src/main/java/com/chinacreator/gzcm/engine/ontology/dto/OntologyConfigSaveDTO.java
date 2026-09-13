package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

import java.util.List;

/**
 * 本体配置批量更新 SaveDTO。
 *
 * <p>{@code PUT /api/v1/engine/ontology/settings} 包装 {@code items}
 * 字段，便于前端展开/折叠。
 */
@Data
public class OntologyConfigSaveDTO {

    /** 批量更新项列表 */
    private List<OntologyConfigItem> items;
}
