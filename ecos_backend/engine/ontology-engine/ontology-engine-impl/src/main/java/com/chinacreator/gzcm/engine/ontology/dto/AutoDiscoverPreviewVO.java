package com.chinacreator.gzcm.engine.ontology.dto;

import java.util.List;

/**
 * 自动发现预览回执：{@code domainCode/datasourceId/dryRun/entities/totalResources/totalFields/note}。
 */
public record AutoDiscoverPreviewVO(
        String domainCode,
        String datasourceId,
        boolean dryRun,
        List<AutoDiscoverEntityPreviewVO> entities,
        int totalResources,
        int totalFields,
        String note) {
}
