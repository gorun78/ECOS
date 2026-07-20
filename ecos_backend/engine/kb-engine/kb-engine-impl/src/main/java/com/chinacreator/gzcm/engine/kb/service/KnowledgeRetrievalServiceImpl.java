package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.KnowledgeRetrievalService;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeArticle;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeArticleMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEmbeddingMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEdgeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class KnowledgeRetrievalServiceImpl implements KnowledgeRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalServiceImpl.class);

    private final KnowledgeArticleMapper articleMapper;
    private final KnowledgeEmbeddingMapper embeddingMapper;
    private final KnowledgeNodeMapper nodeMapper;
    private final KnowledgeEdgeMapper edgeMapper;

    public KnowledgeRetrievalServiceImpl(KnowledgeArticleMapper articleMapper,
                                         KnowledgeEmbeddingMapper embeddingMapper,
                                         KnowledgeNodeMapper nodeMapper,
                                         KnowledgeEdgeMapper edgeMapper) {
        this.articleMapper = articleMapper;
        this.embeddingMapper = embeddingMapper;
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
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
        log.info("Knowledge RAG query (placeholder): query={}, topK={}, threshold={}", queryText, topK, threshold);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", queryText);
        result.put("topK", topK);
        result.put("threshold", threshold);
        result.put("documents", Collections.emptyList());
        result.put("totalTokens", 0);
        result.put("latencyMs", 0);
        return result;
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