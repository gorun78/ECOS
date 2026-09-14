package com.chinacreator.gzcm.engine.data.dto;

import lombok.Data;

/**
 * IntegrationDriftRequest — /api/integration/metadata/drift 请求体（强类型，替换 Map）。
 *
 * @author ECOS Integration
 */
@Data
public class IntegrationDriftRequest {

    /** 类型: drift / sla / reset */
    private String type;
}
