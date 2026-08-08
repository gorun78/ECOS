package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.KnowledgeRetrievalService;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeArticle;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeEmbedding;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeArticleMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEmbeddingMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEdgeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;

@Service
public class KnowledgeRetrievalServiceImpl implements KnowledgeRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalServiceImpl.class);

    private final KnowledgeArticleMapper articleMapper;
    private final KnowledgeEmbeddingMapper embeddingMapper;
    private final KnowledgeNodeMapper nodeMapper;
    private final KnowledgeEdgeMapper edgeMapper;
    private final JdbcTemplate jdbcTemplate;

    private volatile boolean pgVectorAvailable = false;

    public KnowledgeRetrievalServiceImpl(KnowledgeArticleMapper articleMapper,
                                         KnowledgeEmbeddingMapper embeddingMapper,
                                         KnowledgeNodeMapper nodeMapper,
                                         KnowledgeEdgeMapper edgeMapper,
                                         JdbcTemplate jdbcTemplate) {
        this.articleMapper = articleMapper;
        this.embeddingMapper = embeddingMapper;
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 检查 pg_vector 扩展是否已安装。
     */
    @PostConstruct
    public void checkPgVectorExtension() {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT extname, extversion FROM pg_extension WHERE extname = 'vector'");
            String version = (String) row.getOrDefault("extversion", "unknown");
            pgVectorAvailable = true;
            log.info("✅ pgvector extension verified — version {}", version);
        } catch (Exception e) {
            pgVectorAvailable = false;
            log.warn("⚠️  pgvector extension NOT available — vector search disabled. Error: {}", e.getMessage());
        }
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
        log.info("RAG query: query='{}', topK={}, threshold={}, pgvector={}",
                queryText, topK, threshold, pgVectorAvailable);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", queryText);
        result.put("topK", topK);
        result.put("threshold", threshold);

        List<Map<String, Object>> sources = new ArrayList<>();

        if (queryText != null && !queryText.isBlank()) {
            if (pgVectorAvailable) {
                try {
                    List<Map<String, Object>> vectorResults = embeddingMapper.searchByVector(queryText, topK);
                    for (Map<String, Object> row : vectorResults) {
                        Map<String, Object> source = new LinkedHashMap<>();
                        source.put("chunkId", row.getOrDefault("id", ""));
                        source.put("content", row.getOrDefault("chunktext", ""));
                        source.put("score", row.getOrDefault("score", 0.0));
                        source.put("source", row.getOrDefault("articleid", ""));
                        sources.add(source);
                    }
                    log.debug("Vector search returned {} results", sources.size());
                } catch (Exception e) {
                    log.warn("Vector search failed: {} — falling back to keyword search", e.getMessage());
                    sources = fallbackKeywordSearch(queryText, topK);
                }
            } else {
                sources = fallbackKeywordSearch(queryText, topK);
            }
        }

        long latencyMs = System.currentTimeMillis() - startTime;
        result.put("sources", sources);
        result.put("totalTokens", sources.size());
        result.put("sourcesCount", sources.size());
        result.put("latencyMs", latencyMs);

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
