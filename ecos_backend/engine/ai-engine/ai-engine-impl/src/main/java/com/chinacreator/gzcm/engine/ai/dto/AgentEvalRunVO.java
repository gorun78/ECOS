package com.chinacreator.gzcm.engine.ai.dto;

/** 评估启动回执：{@code evalId + questionSet + message}。 */
public record AgentEvalRunVO(String evalId, String questionSet, String message) {
}
