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
import org.springframework.jdbc.core.JdbcTemplate;
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
    /** PMO-B T1: 知识导航目录树过滤（可选） */
    private final JdbcTemplate jdbcTemplate;
    private final String embeddingModel;
    private final String llmGatewayBase;

    private volatile boolean pgVectorAvailable = false;

    public KnowledgeRetrievalServiceImpl(KnowledgeArticleMapper articleMapper,
                                         KnowledgeEmbeddingMapper embeddingMapper,
                                         KnowledgeNodeMapper nodeMapper,
                                         KnowledgeEdgeMapper edgeMapper,
                                         @Lazy QueryEmbeddingHelper queryEmbeddingHelper,
                                         PgVectorSupport pgVectorSupport,
                                         JdbcTemplate jdbcTemplate,
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
        this.jdbcTemplate = jdbcTemplate;
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
        // PMO-B T1: 兼容旧 3 参契约（不传 navigation 过滤 → 全量检索）
        return ragQuery(queryText, topK, threshold, null, null);
    }

    @Override
    public Map<String, Object> ragQuery(String queryText, int topK, double threshold,
                                        List<String> categoryIds, List<String> tags) {
        long startTime = System.currentTimeMillis();
        // B4: topK 收敛到 [1, MAX_TOP_K]，防慢 SQL
        int effectiveTopK = Math.min(Math.max(topK, 1), MAX_TOP_K);
        // PMO-B T1: 解析 categoryIds → 子树 path LIKE 集合（空 = 无导航过滤，全量）
        List<String> subtreePaths = resolveCategorySubtree(categoryIds);
        boolean navFilter = !subtreePaths.isEmpty();
        // PMO-C T1: tags 下沉（symmetry with navFilter）— 清洗后非空才过滤，兼容旧调用（tags=null → 全量）
        List<String> effectTags = sanitizeTags(tags);
        boolean tagFilter = !effectTags.isEmpty();
        log.info("RAG query: query='{}', topK={}, threshold={}, pgvector={}, navFilter={}, tagFilter={}",
                queryText, effectiveTopK, threshold, pgVectorAvailable, navFilter, tagFilter);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", queryText);
        result.put("topK", effectiveTopK);
        result.put("threshold", threshold);
        result.put("categoryIds", categoryIds == null ? List.of() : categoryIds);
        result.put("tags", tags == null ? List.of() : tags);

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
                        // PMO-B T1: navFilter 时多取 5x 余量再按 navigation 收窄（避免误截断）
                        // PMO-C T1: tagFilter 同样参与滤收窄（命中任一过滤即探测多取 5x）
                        int probeK = (navFilter || tagFilter) ? Math.min(effectiveTopK * 5, MAX_TOP_K * 5) : effectiveTopK;
                        List<Map<String, Object>> vectorResults = embeddingMapper.searchByVector(queryVector, probeK);
                        if (vectorResults != null && !vectorResults.isEmpty()) {
                            for (Map<String, Object> row : vectorResults) {
                                if (sources.size() >= effectiveTopK) {
                                    break;
                                }
                                // PMO-B T1: 导航过滤通过路径子树 EXISTS 命中（兼容旧 key 全部小写形式）
                                // PMO-C T1: tags 过滤命中标签 EXISTS 命中（navFilter OR tagFilter 二选一）
                                Object articleIdRaw = row.get("articleid");
                                if (articleIdRaw == null) {
                                    articleIdRaw = row.get("articleId");
                                }
                                String articleId = articleIdRaw == null ? "" : String.valueOf(articleIdRaw);
                                Object contentRaw = row.get("chunktext");
                                if (contentRaw == null) {
                                    contentRaw = row.get("chunkText");
                                }
                                Object scoreRaw = row.get("score");
                                if (scoreRaw == null) {
                                    scoreRaw = 0.0;
                                }
                                if (navFilter && tagFilter) {
                                    // 双过滤：需同时命中子树 AND 命中任一标签
                                    if (!matchesNavFilter(articleId, subtreePaths) || !matchesTagFilter(articleId, effectTags)) {
                                        continue;
                                    }
                                } else if (navFilter) {
                                    // 仅子树过滤
                                    if (!matchesNavFilter(articleId, subtreePaths)) {
                                        continue;
                                    }
                                } else if (tagFilter) {
                                    // 仅标签过滤
                                    if (!matchesTagFilter(articleId, effectTags)) {
                                        continue;
                                    }
                                }
                                Map<String, Object> source = new LinkedHashMap<>();
                                source.put("chunkId", row.getOrDefault("id", ""));
                                source.put("content", contentRaw == null ? "" : String.valueOf(contentRaw));
                                source.put("score", scoreRaw);
                                source.put("source", articleId);
                                sources.add(source);
                            }
                            vectorSuccess = !sources.isEmpty();
                            log.debug("Vector search (embedding_vec + HNSW) returned {} results (navFilter={}, tagFilter={})",
                                    sources.size(), navFilter, tagFilter);
                        }
                    } catch (Exception e) {
                        log.warn("RAG 降级关键词检索：向量检索异常（query='{}'）: {}", queryText, e.getMessage(), e);
                    }
                    if (!vectorSuccess) {
                        log.warn("RAG 降级关键词检索：向量检索无命中（query='{}'）", queryText);
                    }
                }
                if (!vectorSuccess) {
                        sources = fallbackKeywordSearch(queryText, effectiveTopK, subtreePaths, effectTags);
                    }
            } else {
                log.warn("RAG 降级关键词检索：pgvector 扩展不可用（镜像需内置 pgvector，query='{}'）", queryText);
                sources = fallbackKeywordSearch(queryText, effectiveTopK, subtreePaths, effectTags);
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
     * PMO-B T1: 把 categoryIds 列表展开为子树 path 前缀列表（包含自身 + 子孙节点）。
     *
     * <p>实现：取每张 {@code kb_nav_category.path}，加 '%' 后缀作为 LIKE prefix。
     * 任一 id 不存在（软删 / 跨域）时只跳过那一条，不阻塞其他 id。
     * 不可用时返回空 List → 调用方降级为全量检索（回归保证）。</p>
     */
    private List<String> resolveCategorySubtree(List<String> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>();
        for (String cid : categoryIds) {
            if (cid != null && !cid.isBlank()) {
                cleaned.add(cid.trim());
            }
        }
        if (cleaned.isEmpty()) {
            return List.of();
        }
        // 参数化占位（IR05 红线：禁字符串拼接业务 id）
        List<Object> args = new ArrayList<>(cleaned.size());
        StringBuilder inClause = new StringBuilder("(");
        for (int i = 0; i < cleaned.size(); i++) {
            if (i > 0) {
                inClause.append(" OR ");
            }
            inClause.append("id = ?");
            args.add(cleaned.get(i));
        }
        inClause.append(")");
        String sql = "SELECT id, path FROM ecos_knowledge.kb_nav_category "
                + "WHERE is_deleted = 0 AND (" + inClause + ")";
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
            List<String> out = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                Object p = row.get("path");
                if (p == null) {
                    continue;
                }
                String path = String.valueOf(p);
                if (path == null || path.isBlank()) {
                    continue;
                }
                out.add(path.endsWith("%") ? path : path + "%");
            }
            return out;
        } catch (Exception e) {
            log.warn("resolveCategorySubtree 失败，降级为全量检索: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * PMO-C T1: 清洗 tags（剔除 null/blank、trim、去重保序）；空返回 {@link List#of()}。
     *
     * <p>tags 直接可用，无须查 DB（{@code kb_nav_tag.tag_name} 在同 domain 下唯一，
     * 此处按名称匹配即语义正确）。</p>
     */
    private List<String> sanitizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(tags.size());
        for (String t : tags) {
            if (t == null || t.isBlank()) {
                continue;
            }
            String name = t.trim();
            if (!out.contains(name)) {
                out.add(name);
            }
        }
        return out;
    }

    /**
     * PMO-B T1 辅助：判断 articleId 是否命中给定子树 path 任一前缀（复用 kb_nav_article_rel + kb_nav_category）。
     * 文章没有任何导航标签（即零标记）时一律返回 false（要求"命中其中一项 OR 全部 nil"，对应 KISS 语义）。
     */
    private boolean matchesNavFilter(String articleId, List<String> subtreePaths) {
        if (articleId == null || articleId.isBlank()) {
            return false;
        }
        for (String p : subtreePaths) {
            if (p == null || p.isBlank()) {
                continue;
            }
            try {
                Integer n = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ecos_knowledge.kb_nav_article_rel r "
                                + "JOIN ecos_knowledge.kb_nav_category c ON c.id = r.node_id AND c.is_deleted = 0 "
                                + "WHERE r.is_deleted = 0 AND r.scope = 'category' AND r.article_id = ? "
                                + "AND c.path LIKE ?",
                        Integer.class, articleId, p);
                if (n != null && n > 0) {
                    return true;
                }
            } catch (Exception e) {
                log.debug("matchesNavFilter queryForObject 失败（articleId={}, path={}）: {}",
                        articleId, p, e.getMessage());
                return false;
            }
        }
        return false;
    }

    /**
     * PMO-C T1 辅助：判断 articleId 是否命中给定标签名集合（对称 {@link #matchesNavFilter}）。
     *
     * <p>实现：JOIN {@code kb_nav_tag} 把 tag_name 映射为 node_id（V157 ddl comment：
     * scope=tag 时 {@code node_id = kb_nav_tag.id}），再按 tag_name 列表参数化 IN 匹配
     * （IR05 红线：禁字符串拼接，占位符防注入）。任一 tag_name 命中即返回 true；
     * 文章零标签标记一律 false。</p>
     */
    private boolean matchesTagFilter(String articleId, List<String> tags) {
        if (articleId == null || articleId.isBlank() || tags == null || tags.isEmpty()) {
            return false;
        }
        // 参数化占位（IR05 红线：禁字符串拼接业务 name）
        List<Object> args = new ArrayList<>(tags.size() + 1);
        args.add(articleId);
        StringBuilder inClause = new StringBuilder("(");
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) {
                inClause.append(" OR ");
            }
            inClause.append("t.tag_name = ?");
            args.add(tags.get(i));
        }
        inClause.append(")");
        String sql = "SELECT COUNT(*) AS cnt FROM ecos_knowledge.kb_nav_article_rel r "
                + "JOIN ecos_knowledge.kb_nav_tag t ON t.id = r.node_id AND t.is_deleted = 0 "
                + "WHERE r.is_deleted = 0 AND r.scope = 'tag' AND r.article_id = ? "
                + "AND (" + inClause + ")";
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
            if (rows.isEmpty()) {
                return false;
            }
            Object cnt = rows.get(0).get("cnt");
            long n = (cnt instanceof Number num) ? num.longValue() : 0L;
            return n > 0;
        } catch (Exception e) {
            log.debug("matchesTagFilter 失败（articleId={}, tags={}）: {}",
                    articleId, tags, e.getMessage());
            return false;
        }
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
     * 关键词回退检索 — ILIKE 匹配（PMO-B T1 兼容无导航过滤场景；PMO-C T1 追加 tags 参数重载）。
     */
    private List<Map<String, Object>> fallbackKeywordSearch(String queryText, int topK) {
        return fallbackKeywordSearch(queryText, topK, null, null);
    }

    /**
     * 关键词回退检索 — ILIKE 匹配；{@code subtreePaths} 为空 / null 时全量返回（兼容旧调用）。
     * PMO-C T1: 追加 {@code tags} 参数，命中语义与向量路径一致（navFilter OR tagFilter 二选一）。
     */
    private List<Map<String, Object>> fallbackKeywordSearch(String queryText, int topK,
                                                            List<String> subtreePaths, List<String> tags) {
        List<Map<String, Object>> sources = new ArrayList<>();
        try {
            boolean navFilter = subtreePaths != null && !subtreePaths.isEmpty();
            boolean tagFilter = tags != null && !tags.isEmpty();
            // PMO-B T1: 有导航过滤时多取 5x，防 LIKE 子树截断全量；PMO-C T1: 标签过滤同样多取
            int probeK = (navFilter || tagFilter) ? Math.min(topK * 5, MAX_TOP_K * 5) : topK;
            List<KnowledgeEmbedding> embeddings = embeddingMapper.searchByKeyword(queryText.trim(), probeK);
            for (KnowledgeEmbedding emb : embeddings) {
                if (sources.size() >= topK) {
                    break;
                }
                String articleId = emb.getArticleId();
                if (navFilter && tagFilter) {
                    if (!matchesNavFilter(articleId, subtreePaths) || !matchesTagFilter(articleId, tags)) {
                        continue;
                    }
                } else if (navFilter) {
                    if (!matchesNavFilter(articleId, subtreePaths)) {
                        continue;
                    }
                } else if (tagFilter) {
                    if (!matchesTagFilter(articleId, tags)) {
                        continue;
                    }
                }
                Map<String, Object> source = new LinkedHashMap<>();
                source.put("chunkId", emb.getId());
                source.put("content", emb.getChunkText());
                source.put("score", 0.5); // keyword match baseline score
                source.put("source", emb.getArticleId());
                sources.add(source);
            }
        } catch (Exception e) {
            log.warn("Keyword fallback search failed: {}", e.getMessage(), e);
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
