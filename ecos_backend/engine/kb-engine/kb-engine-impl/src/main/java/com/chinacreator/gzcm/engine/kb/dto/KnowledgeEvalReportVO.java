package com.chinacreator.gzcm.engine.kb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 知识检索质量评估报告 VO — {@code POST /api/v1/knowledge/eval/run} 出参
 * （方案 §6.2 K6：recall@k / MRR / nDCG）。
 *
 * <p>字段名与前端 {@code EvalReport}（typesAndConstants.ts）对齐：{@code reportId / seedSetName /
 * printedAt / recallAt5 / mrrAt5 / ndcgAt5 / degraded}。{@code hallucinationRate / citationRate}
 * 需 LLM 自评，本批次不实现（保持缺省，前端展示为「等待后端」）。</p>
 *
 * <p><b>口径说明</b>：指标基于 knowledge_embedding 真实向量与 pgvector 检索的自检索（self-retrieval）
 * 计算——把库中真实分块文本当作查询，检索其自身，命中即视为相关。因此产出的是「检索链路健康度」
 * 而非人工标注准确率；标注型 recall 需前端种子集上传后端化（依赖后续批次）。</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KnowledgeEvalReportVO {

    /** 报告 ID（后端生成） */
    private String reportId;

    /** 种子集名称（入参回显） */
    private String seedSetName;

    /** 报告生成时刻（ISO-8601） */
    private String printedAt;

    /** Recall@K（命中率） */
    private double recallAt5;

    /** MRR@K（平均倒数排名） */
    private double mrrAt5;

    /** nDCG@K（归一化折损累计增益） */
    private double ndcgAt5;

    /** 是否为降级结果（true = 无可用向量/嵌入不可用，指标为 0，前端据此回落本地评测） */
    private boolean degraded;

    /** 实际参与评估的查询数（真实计算样本量） */
    private int queryCount;

    /** 实际使用的 top-K */
    private int topK;

    /** 评估方法标识（如 vector-self-retrieval） */
    private String method;

    /** 局限说明（降级原因 / 口径限制，前端可选展示） */
    private String limitation;

    /** 评估耗时（毫秒） */
    private long durationMs;
}
