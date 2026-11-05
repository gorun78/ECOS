package com.chinacreator.gzcm.engine.cognitive2.dto;

/** 决策因果链回执：{@code linked + sourceId + targetId}。 */
public record DecisionLinkResultVO(boolean linked, String sourceId, String targetId) {
}
