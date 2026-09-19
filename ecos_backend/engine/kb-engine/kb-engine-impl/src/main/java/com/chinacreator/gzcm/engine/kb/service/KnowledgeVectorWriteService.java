package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.common.exception.DataAccessException;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeVectorWriteItem;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeVectorWriteResultVO;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeEmbedding;
import com.chinacreator.gzcm.engine.kb.repo.PgVectorSupport;
import com.chinacreator.gzcm.engine.kb.repo.QueryEmbeddingHelper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEmbeddingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 向量写入生产路径（B4/T3，修 D4）：「已有文本 → 嵌入（llm-gateway）→ 写 embedding_vec」。
 *
 * <p>边界：不实现 chunk 切分（方案 §4 归数据工作台解析节点，B5 批次），只做向量化与落库。
 *
 * <p>关键约束：
 * <ul>
 *   <li>嵌入统一走 llm-gateway（{@link QueryEmbeddingHelper} → {@code POST /api/v1/llm/embedding}），
 *       禁止直连 Provider（架构铁律 §2.5-2）</li>
 *   <li>批量：一次 HTTP 批量嵌入 + 一条 SQL 多 VALUES upsert，禁止循环单条写库</li>
 *   <li>幂等：按主键 id upsert（id 为空时由 articleId#chunkIndex 生成确定性 UUID）</li>
 *   <li>双写过渡（R1）：同时写原 JSONB 列 embedding 与 pgvector 列 embedding_vec</li>
 *   <li>降级不静默：维度不符 / 嵌入为空 / pgvector 不可用一律 {@code log.warn}</li>
 * </ul>
 *
 * @since B4 (2026-09-19)
 */
@Service
public class KnowledgeVectorWriteService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeVectorWriteService.class);

    private final KnowledgeEmbeddingMapper embeddingMapper;
    private final QueryEmbeddingHelper queryEmbeddingHelper;
    private final PgVectorSupport pgVectorSupport;
    private final String embeddingModel;
    private final String llmGatewayBase;
    /** 目标向量维度，须与 embedding_vec 列维度一致（V137 建列 1536） */
    private final int embeddingDimension;

    public KnowledgeVectorWriteService(KnowledgeEmbeddingMapper embeddingMapper,
                                       QueryEmbeddingHelper queryEmbeddingHelper,
                                       PgVectorSupport pgVectorSupport,
                                       @Value("${ecos.rag.embedding-model:text-embedding-3-small}")
                                       String embeddingModel,
                                       @Value("${ecos.rag.llm-gateway-base:}")
                                       String llmGatewayBase,
                                       @Value("${ecos.rag.embedding-dimension:1536}")
                                       int embeddingDimension) {
        this.embeddingMapper = embeddingMapper;
        this.queryEmbeddingHelper = queryEmbeddingHelper;
        this.pgVectorSupport = pgVectorSupport;
        this.embeddingModel = embeddingModel;
        this.llmGatewayBase = llmGatewayBase;
        this.embeddingDimension = embeddingDimension;
    }

    /**
     * 批量向量化并 upsert 到 {@code ecos_knowledge.knowledge_embedding}。
     *
     * @param items 待写入项（text 必填，articleId 必填）
     * @return 写入结果（请求/写入/跳过条数 + 跳过原因）
     * @throws DataAccessException 批量落库失败（库异常上抛，不静默吞掉）
     */
    public KnowledgeVectorWriteResultVO upsertVectors(List<KnowledgeVectorWriteItem> items) {
        KnowledgeVectorWriteResultVO result = new KnowledgeVectorWriteResultVO();
        result.setModel(embeddingModel);
        result.setDimension(embeddingDimension);
        result.setRequested(items == null ? 0 : items.size());
        if (items == null || items.isEmpty()) {
            return result;
        }

        // 1) 过滤无效项（文本/来源为空）
        List<KnowledgeVectorWriteItem> valid = new ArrayList<>(items.size());
        List<String> texts = new ArrayList<>(items.size());
        for (KnowledgeVectorWriteItem item : items) {
            if (item == null || isBlank(item.getArticleId()) || isBlank(item.getText())) {
                result.setSkipped(result.getSkipped() + 1);
                result.setSkippedReason("存在 articleId/text 为空的条目，已跳过");
                log.warn("向量写入跳过：articleId/text 为空，item={}", item);
                continue;
            }
            valid.add(item);
            texts.add(item.getText().trim());
        }
        if (valid.isEmpty()) {
            return result;
        }

        // 2) 批量嵌入（一次 HTTP 调 llm-gateway）
        List<float[]> vectors = queryEmbeddingHelper.embedBatch(texts, embeddingModel, llmGatewayBase);
        if (vectors.size() != texts.size()) {
            result.setSkipped(result.getSkipped() + valid.size());
            result.setSkippedReason("批量嵌入返回数量不匹配，整批放弃写入（避免向量与文本错位）");
            log.warn("向量写入放弃：批量嵌入数量不匹配 texts={}, vectors={}（model={}, gatewayBase='{}'）",
                    texts.size(), vectors.size(), embeddingModel, llmGatewayBase);
            return result;
        }

        // 3) 逐条校验维度并组装行（维度不符不落库，避免 vector 列写入失败）
        List<KnowledgeEmbedding> rows = new ArrayList<>(valid.size());
        for (int i = 0; i < valid.size(); i++) {
            float[] vec = vectors.get(i);
            KnowledgeVectorWriteItem item = valid.get(i);
            if (vec == null || vec.length == 0) {
                result.setSkipped(result.getSkipped() + 1);
                result.setSkippedReason("存在嵌入为空的条目，已跳过");
                log.warn("向量写入跳过：嵌入为空，articleId={}, chunkIndex={}", item.getArticleId(), item.getChunkIndex());
                continue;
            }
            if (vec.length != embeddingDimension) {
                result.setSkipped(result.getSkipped() + 1);
                result.setSkippedReason("嵌入维度与 embedding_vec(" + embeddingDimension + ") 不符，已跳过");
                log.warn("向量写入跳过：嵌入维度 {} 与目标列维度 {} 不符（model={}），articleId={}, chunkIndex={}",
                        vec.length, embeddingDimension, embeddingModel, item.getArticleId(), item.getChunkIndex());
                continue;
            }
            String literal = QueryEmbeddingHelper.toLiteral(vec);
            KnowledgeEmbedding row = new KnowledgeEmbedding();
            row.setId(resolveId(item));
            row.setArticleId(item.getArticleId());
            row.setChunkIndex(item.getChunkIndex());
            row.setChunkText(item.getText());
            row.setEmbeddingJson(literal);
            row.setEmbeddingVec(literal);
            row.setModel(embeddingModel);
            row.setTokenCount(item.getTokenCount() == null ? 0 : item.getTokenCount());
            rows.add(row);
        }
        if (rows.isEmpty()) {
            return result;
        }

        // 4) 批量 upsert（双写 JSONB + vector）；pgvector 不可用则仅写 JSONB 并告警
        try {
            int affected;
            if (pgVectorSupport.isAvailable()) {
                affected = embeddingMapper.batchUpsertVectors(rows);
            } else {
                log.warn("pgvector 不可用 → 本次仅写 JSONB 列（{} 行），embedding_vec 待扩展就绪后重跑补齐",
                        rows.size());
                affected = embeddingMapper.batchUpsertJsonOnly(rows);
            }
            result.setWritten(affected);
            log.info("向量写入完成: requested={}, written={}, skipped={}, model={}, dim={}",
                    result.getRequested(), affected, result.getSkipped(), embeddingModel, embeddingDimension);
            return result;
        } catch (Exception e) {
            log.error("向量批量写入失败: rows={}, model={}", rows.size(), embeddingModel, e);
            throw new DataAccessException("向量批量写入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析行主键：显式 id 优先；否则用 articleId#chunkIndex 生成确定性 UUID（保证重复 ingest 幂等）。
     */
    private String resolveId(KnowledgeVectorWriteItem item) {
        if (!isBlank(item.getId())) {
            return item.getId();
        }
        String seed = item.getArticleId() + "#" + (item.getChunkIndex() == null ? 0 : item.getChunkIndex());
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
