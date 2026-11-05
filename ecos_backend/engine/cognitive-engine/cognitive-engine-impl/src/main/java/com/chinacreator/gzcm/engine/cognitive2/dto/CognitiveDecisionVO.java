package com.chinacreator.gzcm.engine.cognitive2.dto;

import com.chinacreator.gzcm.engine.cognitive2.model.Decision;

import java.sql.Timestamp;

/**
 * 认知决策行（similar / chain 两腿共用）。
 *
 * <p>键序：{@code id/category/scenario/reasoning/outcome/confidence/decisionMaker/createdAt}；
 * {@code createdAt} 保持 JDBC {@link Timestamp}（旧 Map 同值，序列化形态不变）。</p>
 */
public record CognitiveDecisionVO(
        String id,
        String category,
        String scenario,
        String reasoning,
        String outcome,
        double confidence,
        String decisionMaker,
        Timestamp createdAt) {

    public static CognitiveDecisionVO of(Decision d) {
        return new CognitiveDecisionVO(
                d.getId(),
                d.getCategory(),
                d.getScenario(),
                d.getReasoning(),
                d.getOutcome(),
                d.getConfidence(),
                d.getDecisionMaker(),
                d.getCreatedAt());
    }
}
