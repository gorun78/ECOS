package com.chinacreator.gzcm.engine.kb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 知识检索质量评估入参 — {@code POST /api/v1/knowledge/eval/run}（方案 §6.2 K6 检索质量评估）。
 *
 * <p>前端（KnowledgeEvalTab）当前仅传 {@code seedSetName}（种子集存 localStorage，后端无种子集表），
 * 故后端以 knowledge_embedding 真实分块做「自检索」评估，其余字段仅用于收敛计算规模。</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EvalRunRequest {

    /** 种子集名称（仅作报告回显，不参与计算） */
    private String seedSetName;

    /** 检索 top-K（默认 5，收敛 [1,10]） */
    private Integer topK;

    /** 参与评估的采样查询数上限（默认 10，收敛 [1,50]，防慢评估） */
    private Integer maxQueries;
}
