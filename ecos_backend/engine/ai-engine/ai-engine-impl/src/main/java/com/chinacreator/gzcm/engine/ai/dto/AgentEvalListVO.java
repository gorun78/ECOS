package com.chinacreator.gzcm.engine.ai.dto;

import java.util.List;

/** 评估列表：{@code total + items[]}。 */
public record AgentEvalListVO(int total, List<AgentEvalSummaryVO> items) {
}
