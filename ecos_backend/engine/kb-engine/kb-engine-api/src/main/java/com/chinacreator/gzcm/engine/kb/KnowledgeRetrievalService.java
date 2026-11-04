package com.chinacreator.gzcm.engine.kb;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeArticle;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeArticlePage;

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

    /**
     * 修改既有知识条目的分类（#147U 编辑腿）。
     *
     * <p>校验与 {@code createArticle} 同口径（{@code KnowledgeCategoryPolicy}）：分类必须落在七类闭集内、
     * Rule 类必须带 {@code ruleRef}、非 Rule 类禁带。分类必填（本方法不提供「清空分类」语义）。
     * 条目不存在抛 {@code NotFoundException}；非法分类抛 {@code ValidationException}。</p>
     *
     * @return 更新后的条目（回读一次，供调用方回显真实落库值）
     */
    KnowledgeArticle updateArticleCategory(String articleId, String category, String ruleRef);

    KnowledgeArticle getArticle(String articleId);

    List<KnowledgeArticle> searchArticles(String queryText, int limit);

    /**
     * 按七分类列知识资产（#147U；供知识工作台分类筛选面消费）。
     *
     * <p>{@code category} 取值闭集见 {@code KnowledgeCategory}
     * （Fact/Concept/Procedure/Rule/Case/Evidence/Pattern，大小写不敏感）；
     * 未知值抛 {@code ValidationException}（HTTP 400），不做「忽略非法值返回全量」的宽容——
     * 那会让前端筛选失效却显示成功。{@code limit} 由实现方夹紧到 [1,100]。</p>
     */
    List<KnowledgeArticle> listByCategory(String category, int limit);

    /**
     * 按七分类的<b>游标分页</b>读面（#147BA；补 §7.1.2 未做项「列表游标分页」）。
     *
     * <p>与 {@link #listByCategory(String, int)} 的分工：后者是「取前 N 条」的便利面（既有前端在用），
     * 本方法是<b>可稳定翻页</b>的形态——排序键为 {@code (created_at DESC, id DESC)} 全序，
     * 用 keyset 比较推进，不用 {@code OFFSET}（深翻页 O(n)，且并发插入会漏行/重复行）。</p>
     *
     * <p>{@code cursor} 为不透明串，首次调用传 {@code null}/空白取第一页；非法游标抛
     * {@code ValidationException}（400），<b>禁</b>「非法游标当第一页」——那会让翻页静默回到开头。
     * 分类校验与 limit 夹紧同 {@link #listByCategory(String, int)}。</p>
     *
     * @return 本页条目 + 下一页游标（末页为 {@code null}）
     */
    KnowledgeArticlePage listByCategoryPage(String category, int limit, String cursor);
}