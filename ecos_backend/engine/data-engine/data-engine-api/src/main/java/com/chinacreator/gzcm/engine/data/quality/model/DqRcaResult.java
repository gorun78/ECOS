package com.chinacreator.gzcm.engine.data.quality.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DQ 根因分析结果 DTO（PMO-48-C T13）— cognitive-engine 返回契约。
 *
 * <p>写入 {@code ecos_dq.dq_work_order.rca_result} JSONB；{@link #confidence}
 * 单独落到 {@code rca_confidence} 列。stub 阶段 confidence=0.0。</p>
 *
 * @author PMO-48-C T13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DqRcaResult {

    /** 根因描述（自然语言） */
    private String rootCause;

    /** 置信度 0.0 ~ 1.0；stub 阶段固定 0.0 */
    private Double confidence;

    /** 因果链节点列表（如 "ruleId → assetId → pipelineId" 链路描述） */
    private List<String> causalChain;

    /** 候选根因列表（cause / prob） */
    private List<Candidate> candidates;

    /** 分析完成时间戳（毫秒） */
    private Long timestamp;

    /**
     * RCA 候选根因（cause 描述 + 概率）。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Candidate {

        /** 候选根因描述 */
        private String cause;

        /** 候选概率 0.0 ~ 1.0 */
        private Double prob;
    }
}
