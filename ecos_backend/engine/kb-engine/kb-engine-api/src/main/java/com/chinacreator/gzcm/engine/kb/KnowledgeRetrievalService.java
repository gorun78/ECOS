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

    /**
     * 严格供数 RAG 检索（详设 §7.3.2 附录 A 第 14 行 {@code searchKnowledge} 的取数腿）。
     *
     * <p>与 5 参 {@link #ragQuery(String, int, double, List, List)} 的<b>唯一语义差异</b>：
     * 供数链路（导航子树解析／关键词回退／导航-标签过滤反查）读失败时<b>不再折叠为空结果</b>，
     * 而是抛 {@code ECOS-KB-040}（502 CURATED 供数不可达）。旧腿保持"降级为空集"的
     * 既有行为（只增不改），新腿供契约端点使用：把「查不到」与「查不了」分成两个状态行，
     * 否则运维面看到的永远是"零命中"。</p>
     *
     * <p>向量腿的异常仍走 {@code vectorDegraded=true} 的显式降级（湖规"降级须显式标记"），
     * 不属供数不可达：那是形态退化，不是无供给。</p>
     *
     * @throws com.chinacreator.gzcm.engine.kb.support.KbErrorCodes.DomainException
     *         供数不可达（502 {@code ECOS-KB-040}）
     */
    Map<String, Object> ragQueryStrict(String queryText, int topK, double threshold,
                                       List<String> categoryIds, List<String> tags);

    KnowledgeArticle createArticle(KnowledgeArticle article);

    KnowledgeArticle getArticle(String articleId);

    List<KnowledgeArticle> searchArticles(String queryText, int limit);
}