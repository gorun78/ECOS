package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.KnowledgeRetrievalService;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeArticle;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeEmbedding;
import com.chinacreator.gzcm.engine.kb.repo.PgVectorSupport;
import com.chinacreator.gzcm.engine.kb.repo.QueryEmbeddingHelper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeArticleMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEmbeddingMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEdgeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;

@Service
public class KnowledgeRetrievalServiceImpl implements KnowledgeRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalServiceImpl.class);

    /** Top-K 上限（防慢 SQL：向量检索 LIMIT 与关键词回退 LIMIT 均受此约束） */
    private static final int MAX_TOP_K = 50;

    private final KnowledgeArticleMapper articleMapper;
    private final KnowledgeEmbeddingMapper embeddingMapper;
    private final KnowledgeNodeMapper nodeMapper;
    private final KnowledgeEdgeMapper edgeMapper;
    // PMO-50 T1: RAG 向量检索真实化 — 注入 llm-gateway 取真实向量
    private final QueryEmbeddingHelper queryEmbeddingHelper;
    // B4: pgvector 可用性单点判定（与向量写入共用）
    private final PgVectorSupport pgVectorSupport;
    private final String embeddingModel;
    private final String llmGatewayBase;

    private volatile boolean pgVectorAvailable = false;

    public KnowledgeRetrievalServiceImpl(KnowledgeArticleMapper articleMapper,
                                         KnowledgeEmbeddingMapper embeddingMapper,
                                         KnowledgeNodeMapper nodeMapper,
                                         KnowledgeEdgeMapper edgeMapper,
                                         @Lazy QueryEmbeddingHelper queryEmbeddingHelper,
                                         PgVectorSupport pgVectorSupport,
                                         @Value("${ecos.rag.embedding-model:text-embedding-3-small}")
                                         String embeddingModel,
                                         @Value("${ecos.rag.llm-gateway-base:}")
                                         String llmGatewayBase) {
        this.articleMapper = articleMapper;
        this.embeddingMapper = embeddingMapper;
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
        this.queryEmbeddingHelper = queryEmbeddingHelper;
        this.pgVectorSupport = pgVectorSupport;
        this.embeddingModel = embeddingModel;
        this.llmGatewayBase = llmGatewayBase;
    }

    /**
     * 同步 pgvector 可用性（探测逻辑集中在 {@link PgVectorSupport}，失败不阻断启动）。
     */
    @PostConstruct
    public void checkPgVectorExtension() {
        pgVectorAvailable = pgVectorSupport.isAvailable();
    }

    @Override
    public Map<String, Object> getIndexStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        try {
            status.put("nodeCount", nodeMapper.count());
            status.put("edgeCount", edgeMapper.count());
            status.put("articleCount", articleMapper.count());
            status.put("embeddingCount", embeddingMapper.count());
        } catch (Exception e) {
            status.put("nodeCount", 0);
            status.put("edgeCount", 0);
            status.put("articleCount", 0);
            status.put("embeddingCount", 0);
            status.put("error", e.getMessage());
        }
        status.put("pgVectorAvailable", pgVectorAvailable);
        status.put("lastSyncTime", null);
        return status;
    }

    @Override
    public void triggerSync() {
        log.info("Knowledge full sync triggered (placeholder)");
    }

    @Override
    public List<Object> query(String queryText) {
        log.info("Knowledge query (placeholder): {}", queryText);
        return Collections.emptyList();
    }

    @Override
    public Map<String, Object> ragQuery(String queryText, int topK, double threshold) {
        long startTime = System.currentTimeMillis();
        // B4: topK 收敛到 [1, MAX_TOP_K]，防慢 SQL
        int effectiveTopK = Math.min(Math.max(topK, 1), MAX_TOP_K);
        log.info("RAG query: query='{}', topK={}, threshold={}, pgvector={}",
                queryText, effectiveTopK, threshold, pgVectorAvailable);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", queryText);
        result.put("topK", effectiveTopK);
        result.put("threshold", threshold);

        List<Map<String, Object>> sources = new ArrayList<>();

        if (queryText != null && !queryText.isBlank()) {
            if (pgVectorAvailable) {
                // B4: 真实向量检索 — 嵌入走 llm-gateway（QueryEmbeddingHelper），
                // 命中 embedding_vec + HNSW（余弦距离 <=>）；任一步失败必须显式告警后降级
                String queryVector = queryEmbeddingHelper.embed(queryText, embeddingModel, llmGatewayBase);
                boolean vectorSuccess = false;
                if (queryVector == null) {
                    log.warn("RAG 降级关键词检索：嵌入向量获取失败/为空（model={}, gatewayBase='{}', query='{}'）",
                            embeddingModel, llmGatewayBase, queryText);
                } else {
                    try {
                        List<Map<String, Object>> vectorResults = embeddingMapper.searchByVector(queryVector, effectiveTopK);
                        if (vectorResults != null && !vectorResults.isEmpty()) {
                            for (Map<String, Object> row : vectorResults) {
                                Map<String, Object> source = new LinkedHashMap<>();
                                source.put("chunkId", row.getOrDefault("id", ""));
                                source.put("content", row.getOrDefault("chunktext", ""));
                                source.put("score", row.getOrDefault("score", 0.0));
                                source.put("source", row.getOrDefault("articleid", ""));
                                sources.add(source);
                            }
                            vectorSuccess = !sources.isEmpty();
                            log.debug("Vector search (embedding_vec + HNSW) returned {} results", sources.size());
                        }
                    } catch (Exception e) {
                        log.warn("RAG 降级关键词检索：向量检索异常（query='{}'）: {}", queryText, e.getMessage(), e);
                    }
                    if (!vectorSuccess) {
                        log.warn("RAG 降级关键词检索：向量检索无命中（embedding_vec 可能为 NULL 或维度不符，query='{}'）",
                                queryText);
                    }
                }
                if (!vectorSuccess) {
                    sources = fallbackKeywordSearch(queryText, effectiveTopK);
                }
            } else {
                log.warn("RAG 降级关键词检索：pgvector 扩展不可用（镜像需内置 pgvector，query='{}'）", queryText);
                sources = fallbackKeywordSearch(queryText, effectiveTopK);
            }
        }

        long latencyMs = System.currentTimeMillis() - startTime;
        result.put("sources", sources);
        result.put("sourcesCount", sources.size());
        result.put("totalTokens", sources.size());
        result.put("latencyMs", latencyMs);

        // PMO-50 T1: 拼 answer 字段（前端直接展示首段；topK>1 拼接分段）
        StringBuilder answerBuf = new StringBuilder();
        for (Map<String, Object> src : sources) {
            String content = src.get("content") == null ? "" : String.valueOf(src.get("content"));
            answerBuf.append(content).append('\n');
        }
        result.put("answer", answerBuf.toString().trim());

        if (latencyMs > 2000) {
            log.warn("⚠️  RAG query latency {}ms exceeded 2s target", latencyMs);
        }

        return result;
    }

    /**
     * RAG检索简版 — 默认threshold=0.6。
     */
    public Map<String, Object> ragQuery(String queryText, int topK) {
        return ragQuery(queryText, topK, 0.6);
    }

    /**
     * Neo4j图数据库健康检查。
     */
    public Map<String, Object> graphHealth() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            long nodeCount = nodeMapper.count();
            long edgeCount = edgeMapper.count();
            result.put("healthy", true);
            result.put("nodeCount", nodeCount);
            result.put("edgeCount", edgeCount);
            result.put("pgVectorAvailable", pgVectorAvailable);
        } catch (Exception e) {
            result.put("healthy", false);
            result.put("error", e.getMessage());
            log.warn("Neo4j health check failed: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 关键词回退检索 — ILIKE 匹配。
     */
    private List<Map<String, Object>> fallbackKeywordSearch(String queryText, int topK) {
        List<Map<String, Object>> sources = new ArrayList<>();
        try {
            List<KnowledgeEmbedding> embeddings = embeddingMapper.searchByKeyword(queryText.trim(), topK);
            for (KnowledgeEmbedding emb : embeddings) {
                Map<String, Object> source = new LinkedHashMap<>();
                source.put("chunkId", emb.getId());
                source.put("content", emb.getChunkText());
                source.put("score", 0.5); // keyword match baseline score
                source.put("source", emb.getArticleId());
                sources.add(source);
            }
        } catch (Exception e) {
            log.warn("Keyword fallback search failed: {}", e.getMessage());
        }
        return sources;
    }

    @Override
    public KnowledgeArticle createArticle(KnowledgeArticle article) {
        if (article.getId() == null) article.setId(UUID.randomUUID().toString());
        article.setCreatedAt(System.currentTimeMillis());
        article.setUpdatedAt(System.currentTimeMillis());
        if (article.getStatus() == null) article.setStatus("draft");
        articleMapper.insert(article);
        log.info("Created knowledge article: {} [{}]", article.getId(), article.getTitle());
        return article;
    }

    @Override
    public KnowledgeArticle getArticle(String articleId) {
        return articleMapper.findById(articleId);
    }

    @Override
    public List<KnowledgeArticle> searchArticles(String queryText, int limit) {
        return articleMapper.search(queryText, limit);
    }
}
