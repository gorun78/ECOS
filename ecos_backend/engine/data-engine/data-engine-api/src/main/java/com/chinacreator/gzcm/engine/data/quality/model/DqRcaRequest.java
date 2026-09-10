package com.chinacreator.gzcm.engine.data.quality.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DQ 根因分析请求 DTO（PMO-48-C T13）— 发往 cognitive-engine 的入参。
 *
 * <p>PMO-48-D Phase 4 将由 cognitive-engine {@code POST /api/v1/cognitive/diagnose}
 * 实际消费；本波 T13 stub 不真调用，仅按契约组装。</p>
 *
 * @author PMO-48-C T13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DqRcaRequest {

    /** 触发 RCA 的规则 ID（工单关联的规则） */
    private String failedRuleId;

    /** 受影响资产 ID（TABLE / DATASOURCE） */
    private String assetId;

    /** 分析时间窗口（如 "1h" / "24h"），供 cognitive 限定检索范围 */
    private String timeWindow;

    /** 关联管道 ID 列表（有该规则失败的管道上下文，可空） */
    private List<String> relatedPipelineIds;

    /** 触发 RCA 的工单 ID（审计追溯用） */
    private String workOrderId;
}
