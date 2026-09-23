package com.chinacreator.gzcm.engine.kb;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeArticle;

import java.util.List;
import java.util.Map;

public interface KnowledgeRetrievalService {

    Map<String, Object> getIndexStatus();

    void triggerSync();

    List<Object> query(String queryText);

    /**
     * 全量 RAG 检索（兼容旧契约，categoryIds/tags 不出参 → 不加 WHERE 过滤）。
     */
    Map<String, Object> ragQuery(String queryText, int topK, double threshold);

    /**
     * RAG 检索带导航过滤 — PMO-B T1：可选按 {@code categoryIds} 收窄召回集。
     *
     * <p>过滤语义：{@code categoryIds == null || empty} 时与 {@link #ragQuery(String, int, double)} 完全一致
     * （全量检索，不加 WHERE）；非空时按向量检索 Top-K + 关键词回退两条召回路径追加
     * `EXISTS (kb_nav_article_rel r JOIN kb_nav_category c ON ... WHERE r.article_id = e.document_id
     * AND r.scope = 'category' AND c.path LIKE ?)`，取在选中目录子树下的行。</p>
     *
     * @param queryText   查询文本
     * @param topK        Top-K 上限
     * @param threshold   余弦相似度阈值（本 impl 未硬筛，保留给前端展示用）
     * @param categoryIds 知识导航目录 ID 集合（兼容 null/empty）
     * @param tags        知识导航标签名集合（仅占位，本期不做下行 WHERE）
     */
    Map<String, Object> ragQuery(String queryText, int topK, double threshold,
                                 List<String> categoryIds, List<String> tags);

    KnowledgeArticle createArticle(KnowledgeArticle article);

    KnowledgeArticle getArticle(String articleId);

    List<KnowledgeArticle> searchArticles(String queryText, int limit);
}