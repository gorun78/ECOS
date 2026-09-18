package com.chinacreator.gzcm.engine.data.dto;

import lombok.Data;

import java.util.List;

/**
 * 数据采集即时执行请求体。
 */
@Data
public class IngestRunRequest {

    /** 源数据源 ID（必填） */
    private String datasourceId;

    /** 待采集表名列表（必填，白名单校验） */
    private List<String> tableNames;
}
