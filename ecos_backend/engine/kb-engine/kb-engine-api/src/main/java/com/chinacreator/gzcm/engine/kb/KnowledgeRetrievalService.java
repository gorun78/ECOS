package com.chinacreator.gzcm.engine.kb;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeArticle;

import java.util.List;
import java.util.Map;

public interface KnowledgeRetrievalService {

    Map<String, Object> getIndexStatus();

    void triggerSync();

    List<Object> query(String queryText);

    Map<String, Object> ragQuery(String queryText, int topK, double threshold);

    KnowledgeArticle createArticle(KnowledgeArticle article);

    KnowledgeArticle getArticle(String articleId);

    List<KnowledgeArticle> searchArticles(String queryText, int limit);
}