package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 日志导出请求 DTO（T6 批次 POST /export-log 入参）。
 */
@Data
public class ExportLogRequest {

    /** 抽取作业标识（非空） */
    private String jobId;
}
