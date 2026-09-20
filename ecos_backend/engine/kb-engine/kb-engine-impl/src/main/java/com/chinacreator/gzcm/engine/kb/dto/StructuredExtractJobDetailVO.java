package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 结构化抽取作业详情 VO（K1 批次 E5 端点出参）。
 */
@Data
public class StructuredExtractJobDetailVO {

    /** 作业标识 */
    private String jobId;

    /** 作业状态：SUCCESS / RUNNING / FAILED / NOT_FOUND 溯源结果 */
    private String status;

    /** 明细（job 溯源信息或原因说明） */
    private String detail;
}
