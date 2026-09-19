package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.dto.EvalRunRequest;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeEvalReportVO;
import com.chinacreator.gzcm.engine.kb.repo.PgVectorSupport;
import com.chinacreator.gzcm.engine.kb.repo.QueryEmbeddingHelper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEmbeddingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识检索质量评估服务 — {@code POST /api/v1/knowledge/eval/run}（方案 §6.2 K6）。
 *
 * <p><b>真实计算，无假数据</b>：指标基于 B4 交付的真实向量链路
 * （llm-gateway 嵌入 + pgvector {@code embedding_vec} 余弦检索）计算，采用
 * <b>自检索（self-retrieval）</b>口径：
 * <ol>
 *   <li>从 {@code ecos_knowledge.knowledge_embedding} 采样真实分块文本（embedding_vec 非空）；</li>
 *   <li>批量经 {@link QueryEmbeddingHelper} 取真实查询向量；</li>
 *   <li>用 {@link KnowledgeEmbeddingMapper#searchByVector} 取 top-K，命中采样分块自身即视为相关；</li>
 *   <li>按 Recall@K / MRR@K / nDCG@K（单个相关文档，折损因子 {@code 1/log2(rank+1)}）聚合。</li>
 * </ol>
 *
 * <p><b>局限（如实声明）</b>：无人工标注种子集存储（前端 seed 仅存 localStorage），
 * 故产出的是「检索链路健康度」而非标注准确率；无可用向量或嵌入不可用时返回
 * {@code degraded=true} + 零值，由前端回落本地评测，不编造数据。</p>
 *
 * @since B5-2（D6）
 */
@Service
public class KnowledgeEvalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeEvalService.class);

    /** 默认 top-K（与前端 Recall@5/MRR@5/NDCG@5 对齐） */
    private static final int DEFAULT_TOP_K = 5;

    /** top-K 上限（防慢检索） */
    private static final int MAX_TOP_K = 10;

    /** 默认采样查询数（防慢评估：每条查询一次嵌入 + 一次向量检索） */
    private static final int DEFAULT_SAMPLE = 10;

    /** 采样查询数上限 */
    private static final int MAX_SAMPLE = 50;

    private static final String METHOD_SELF_RETRIEVAL = "vector-self-retrieval";

    private final JdbcTemplate jdbc;

    private final KnowledgeEmbeddingMapper embeddingMapper;

    private final QueryEmbeddingHelper queryEmbeddingHelper;

    private final PgVectorSupport pgVectorSupport;

    /** 评估用嵌入模型（与 RAG 检索共用配置） */
    private final String embeddingModel;

    /** llm-gateway base（空字符串 = 不可用，评估直接降级） */
    private final String llmGatewayBase;

    public KnowledgeEvalService(JdbcTemplate jdbc,
                                KnowledgeEmbeddingMapper embeddingMapper,
                                QueryEmbeddingHelper queryEmbeddingHelper,
                                PgVectorSupport pgVectorSupport,
                                @Value("${ecos.rag.embedding-model:text-embedding-3-small}") String embeddingModel,
                                @Value("${ecos.rag.llm-gateway-base:}") String llmGatewayBase) {
        this.jdbc = jdbc;
        this.embeddingMapper = embeddingMapper;
        this.queryEmbeddingHelper = queryEmbeddingHelper;
        this.pgVectorSupport = pgVectorSupport;
        this.embeddingModel = embeddingModel;
        this.llmGatewayBase = llmGatewayBase;
    }

    /**
     * 执行一次检索质量评估。
     *
     * @param req 入参（可空；仅收敛计算规模 + 回显种子集名）
     * @return 评估报告（降级时 {@code degraded=true} 且指标为 0）
     */
    public KnowledgeEvalReportVO runEval(EvalRunRequest req) {
        long startedAt = System.currentTimeMillis();
        int topK = clamp(req == null ? null : req.getTopK(), DEFAULT_TOP_K, 1, MAX_TOP_K);
        int maxQueries = clamp(req == null ? null : req.getMaxQueries(), DEFAULT_SAMPLE, 1, MAX_SAMPLE);

        KnowledgeEvalReportVO report = new KnowledgeEvalReportVO();
        report.setReportId("eval-" + UUID.randomUUID().toString().replace("-", ""));
        report.setPrintedAt(Instant.now().toString());
        report.setTopK(topK);
        report.setMethod(METHOD_SELF_RETRIEVAL);
        String seedSetName = req == null || req.getSeedSetName() == null || req.getSeedSetName().isBlank()
                ? "default" : req.getSeedSetName().trim();
        report.setSeedSetName(seedSetName);

        // 1) 前置能力校验：pgvector 不可用 → 降级（不编造指标）
        if (!pgVectorSupport.isAvailable()) {
            report.setDegraded(true);
            report.setLimitation("pgvector 扩展不可用，无法执行向量检索评估");
            report.setDurationMs(System.currentTimeMillis() - startedAt);
            log.warn("知识检索质量评估降级：pgvector 不可用");
            return report;
        }

        // 2) 采样真实分块（embedding_vec 非空才可检索）
        List<Map<String, Object>> samples = jdbc.queryForList(
                "SELECT id, content FROM ecos_knowledge.knowledge_embedding " +
                "WHERE embedding_vec IS NOT NULL AND content IS NOT NULL AND content <> '' " +
                "ORDER BY created_at DESC LIMIT ?", maxQueries);
        if (samples.isEmpty()) {
            report.setDegraded(true);
            report.setLimitation("无可用向量分块（knowledge_embedding.embedding_vec 为空），请先完成文档解析与向量化");
            report.setDurationMs(System.currentTimeMillis() - startedAt);
            log.warn("知识检索质量评估降级：无可用向量分块");
            return report;
        }

        // 3) 批量取真实查询向量（一次 llm-gateway 调用，铁律 §2.5-2 禁直连 Provider）
        List<String> texts = new ArrayList<>(samples.size());
        for (Map<String, Object> row : samples) {
            texts.add(String.valueOf(row.get("content")));
        }
        List<float[]> vectors = queryEmbeddingHelper.embedBatch(texts, embeddingModel, llmGatewayBase);
        if (vectors.size() != texts.size()) {
            report.setDegraded(true);
            report.setLimitation("嵌入向量获取不完整（请求 " + texts.size() + " 条，返回 " + vectors.size()
                    + " 条），请检查 llm-gateway 可用性");
            report.setDurationMs(System.currentTimeMillis() - startedAt);
            log.warn("知识检索质量评估降级：嵌入向量不完整 requested={} got={}", texts.size(), vectors.size());
            return report;
        }

        // 4) 逐条检索并聚合指标
        int evaluated = 0;
        double recallSum = 0.0;
        double mrrSum = 0.0;
        double ndcgSum = 0.0;
        for (int i = 0; i < samples.size(); i++) {
            String groundTruthId = String.valueOf(samples.get(i).get("id"));
            String literal = QueryEmbeddingHelper.toLiteral(vectors.get(i));
            if (literal == null) {
                continue;
            }
            List<Map<String, Object>> hits;
            try {
                hits = embeddingMapper.searchByVector(literal, topK);
            } catch (Exception e) {
                log.warn("检索评估单条查询失败（跳过）: groundTruthId={}, err={}", groundTruthId, e.getMessage());
                continue;
            }
            int rank = indexOf(hits, groundTruthId);
            if (rank >= 0) {
                recallSum += 1.0;
                mrrSum += 1.0 / (rank + 1);
                // 单相关文档 nDCG@K：DCG = 1/log2(rank+1)，IDCG = 1 ⇒ nDCG = DCG
                ndcgSum += 1.0 / (Math.log(rank + 2) / Math.log(2));
            }
            evaluated++;
        }

        if (evaluated == 0) {
            report.setDegraded(true);
            report.setLimitation("采样分块全部检索失败，无法计算指标");
            report.setDurationMs(System.currentTimeMillis() - startedAt);
            log.warn("知识检索质量评估降级：全部查询检索失败");
            return report;
        }

        report.setDegraded(false);
        report.setQueryCount(evaluated);
        report.setRecallAt5(recallSum / evaluated);
        report.setMrrAt5(mrrSum / evaluated);
        report.setNdcgAt5(ndcgSum / evaluated);
        report.setLimitation("基于 knowledge_embedding 真实分块的自检索口径（无人工标注种子集存储），"
                + "指标反映检索链路健康度而非标注准确率");
        report.setDurationMs(System.currentTimeMillis() - startedAt);
        log.info("知识检索质量评估完成: queryCount={}, recall@{}={}, mrr={}, ndcg={}, costMs={}",
                evaluated, topK, report.getRecallAt5(), report.getMrrAt5(), report.getNdcgAt5(),
                report.getDurationMs());
        return report;
    }

    /** 在检索命中中定位 ground-truth 分块（0-based 排名；未命中返回 -1）。 */
    private int indexOf(List<Map<String, Object>> hits, String groundTruthId) {
        if (hits == null || hits.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < hits.size(); i++) {
            Object id = hits.get(i).get("id");
            if (id != null && groundTruthId.equals(String.valueOf(id))) {
                return i;
            }
        }
        return -1;
    }

    /** 数值收敛到 [min,max]；入参为 null 时取默认值。 */
    private int clamp(Integer value, int defaultValue, int min, int max) {
        int v = value == null ? defaultValue : value;
        return Math.min(Math.max(v, min), max);
    }
}
