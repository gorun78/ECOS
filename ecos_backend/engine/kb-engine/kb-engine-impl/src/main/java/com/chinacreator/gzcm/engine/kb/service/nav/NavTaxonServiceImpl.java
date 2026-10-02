package com.chinacreator.gzcm.engine.kb.service.nav;

import com.chinacreator.gzcm.common.exception.ForbiddenException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.common.security.header.ServiceContext;
import com.chinacreator.gzcm.engine.kb.dao.NavTaxonDao;
import com.chinacreator.gzcm.engine.kb.nav.INavService;
import com.chinacreator.gzcm.engine.kb.nav.model.NavCategorySaveDTO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavCategoryVO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavFolderQuery;
import com.chinacreator.gzcm.engine.kb.nav.model.NavNodeStatsVO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavProductItemVO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavRecommendVO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavTagVO;
import com.chinacreator.gzcm.engine.kb.security.KnowledgeNavSecurityEngineClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 知识导航 (kb-nav) 主业务服务 — 目录 CRUD + 标签 CRUD + 多选/单选关联 + 子树统计 + LLM 候选。
 *
 * <p>PMO-A A3/A4 主实现。</p>
 *
 * <p><b>数据访问</b>：R2-B2b 收口（架构铁律 §2.5 / IR06）后 raw-SQL 全部下沉
 * {@link NavTaxonDao}，本类仅保留业务编排（ABAC / 越界校验 / 审计 / undo / VO 映射），
 * schema {@code ecos_knowledge}（不跨引擎 SQL）。</p>
 *
 * <p><b>ABAC</b>：写操作前调用 {@code KnowledgeNavSecurityEngineClient.evaluatePolicy}，
 * 不可用/拒绝一律抛 403（铁律 §2.4-6：宁可误拒）。</p>
 *
 * <p><b>审计</b>：写操作成功后调 {@code KnowledgeNavSecurityEngineClient.audit(...)}
 * 经 EventBus 发 {@code ecos.audit}；F04-15 fail-closed：audit 抛 DomainException 时
 * 落 log.error 但不回滚已落库写（写已成功，审计降级由上层感知告警）。</p>
 *
 * <p><b>F04-16 写入可归因</b>：所有写操作 {@code create_by}/{@code update_by} 不再
 * 硬编码 'current-user'，改为 JDBC {@code ?} 绑定 {@link #currentActor()}（ServiceContext
 * 实际 userId，缺失回退 "system"）。每个写方法在入口一次性解析 actor，保证所有
 * 子 SQL 携带同一归因值。</p>
 *
 * <p><b>层级约束</b>：{@code level ∈ [1,3]}（含叶子）；parentId 为空 = 一级目录，parentId 是 level 1 的节点 = 二级，
 * 其余抛 {@link ValidationException}。</p>
 */
@Service
public class NavTaxonServiceImpl implements INavService {

    private static final Logger log = LoggerFactory.getLogger(NavTaxonServiceImpl.class);

    /** 一级 level */
    private static final int LEVEL_1 = 1;
    /** 三级 level（叶子极限） */
    private static final int LEVEL_3 = 3;

    /** association 维度固定值 */
    private static final String SCOPE_CATEGORY = "category";
    private static final String SCOPE_TAG = "tag";

    /** 分页大小上限（防慢查询） */
    private static final int MAX_PAGE_SIZE = 100;

    private final NavTaxonDao dao;
    private final KnowledgeNavSecurityEngineClient securityClient;
    private final NavLlmRecommendService llmRecommendService;

    /**
     * <p>V200/R3-B1: schema 由 `${kb.control-schema:ecos_knowledge}` 注入 (MC06 全面落地)。</p>
     */
    private final String schema;

    @Autowired
    public NavTaxonServiceImpl(NavTaxonDao dao,
                               KnowledgeNavSecurityEngineClient securityClient,
                               NavLlmRecommendService llmRecommendService,
                               @Value("${kb.control-schema:ecos_knowledge}") String schema) {
        this.dao = dao;
        this.securityClient = securityClient;
        this.llmRecommendService = llmRecommendService;
        this.schema = schema;
    }

    /**
     * 兼容构造（测试辅助）：使用默认 schema。
     */
    public NavTaxonServiceImpl(NavTaxonDao dao,
                               KnowledgeNavSecurityEngineClient securityClient,
                               NavLlmRecommendService llmRecommendService) {
        this(dao, securityClient, llmRecommendService, "ecos_knowledge");
    }

    // ═══════════════════════ 目录 CRUD ═══════════════════════

    @Override
    public List<NavCategoryVO> listCategories(String domain, String parentId, String search) {
        String d = normalizeDomain(domain);
        List<Map<String, Object>> rows = dao.listCategories(d, parentId, search);
        List<NavCategoryVO> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            NavCategoryVO vo = new NavCategoryVO();
            vo.setId(str(row.get("id")));
            vo.setDomain(str(row.get("domain")));
            vo.setParentId(str(row.get("parent_id")));
            vo.setName(str(row.get("name")));
            vo.setPath(str(row.get("path")));
            vo.setLevel(intVal(row.get("level"), LEVEL_1));
            vo.setSortOrder(intVal(row.get("sort_order"), 0));
            vo.setChildCount(intVal(row.get("child_count"), 0));
            vo.setArticleCount(intVal(row.get("article_count"), 0));
            vo.setCreatedAt(toInstant(row.get("create_time")));
            vo.setUpdatedAt(toInstant(row.get("update_time")));
            vo.setParentName(row.get("parent_name") == null ? "" : String.valueOf(row.get("parent_name")));
            out.add(vo);
        }
        return out;
    }

    @Override
    public NavCategoryVO createCategory(NavCategorySaveDTO dto) {
        if (dto == null) {
            throw new ValidationException("body", "NavCategorySaveDTO 不能为空");
        }
        String d = normalizeDomain(dto.getDomain());
        boolean parentBlank = dto.getParentId() == null || dto.getParentId().isBlank();
        int parentLevel = parentBlank ? 0 : fetchLevel(dto.getParentId());
        int level = parentLevel + 1;
        if (level > LEVEL_3) {
            throw new ValidationException("parentId", "层级越界：知识导航最多 3 层（含叶子）");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new ValidationException("name", "目录名不能为空");
        }
        String name = dto.getName().trim();
        int sortOrder = dto.getSortOrder() == null ? 0 : dto.getSortOrder();
        checkAbac("kb.category.create", abac("domain", d, "name", name, "level", level));

        try {
            // F04-16: 写入可归因 — create_by / update_by 绑 ? → 实际 userId（ServiceContext）
            String actor = currentActor();
            dao.insertCategory(
                    d,
                    parentBlank ? null : dto.getParentId(),
                    name,
                    computePath(parentBlank ? null : dto.getParentId(), name),
                    level,
                    sortOrder,
                    actor);
        } catch (DuplicateKeyException e) {
            log.warn("kb-nav createCategory 唯一约束命中: domain={} name={}", d, name, e);
            throw new ValidationException("name", "同名目录已存在: " + name);
        }
        NavCategoryVO created = findByRow(findByIdRequired(d, name));
        safeAudit("kb.category.create", Map.of("id", created.getId(), "domain", d));
        return created;
    }

    @Override
    public NavCategoryVO updateCategory(String id, NavCategorySaveDTO dto) {
        if (id == null || id.isBlank()) {
            throw new ValidationException("id", "目录 id 不能为空");
        }
        if (dto == null) {
            throw new ValidationException("body", "NavCategorySaveDTO 不能为空");
        }
        Map<String, Object> row = fetchCategory(id);
        if (row == null) {
            throw new NotFoundException("kb_nav_category id=" + id);
        }
        String d = normalizeDomain(dto.getDomain());
        String currentName = str(row.get("name"));
        String newName = dto.getName() == null || dto.getName().isBlank() ? currentName : dto.getName().trim();
        int sortOrder = dto.getSortOrder() == null ? intVal(row.get("sort_order"), 0) : dto.getSortOrder();
        checkAbac("kb.category.update", abac("id", id, "domain", d, "name", newName));

        if (!newName.equalsIgnoreCase(currentName)) {
            Integer dup = dao.countSameNameCategory(d, newName, id);
            if (dup != null && dup > 0) {
                throw new ValidationException("name", "同名目录已存在: " + newName);
            }
        }
        dao.updateCategory(newName, sortOrder, currentActor(), id);
        NavCategoryVO updated = findByRow(findByIdRequired(d, newName == null ? currentName : newName));
        // 取精确 id（名改名场景下按 name 匹配可能不唯一，按 id 取）
        Map<String, Object> exact = fetchCategory(id);
        safeAudit("kb.category.update", Map.of("id", id, "newName", newName));
        return exact == null ? updated : findByRow(exact);
    }

    @Override
    public void deleteCategory(String id) {
        if (id == null || id.isBlank()) {
            throw new ValidationException("id", "目录 id 不能为空");
        }
        Map<String, Object> row = fetchCategory(id);
        if (row == null) {
            throw new NotFoundException("kb_nav_category id=" + id);
        }
        checkAbac("kb.category.delete", abac("id", id, "domain", str(row.get("domain"))));
        Integer childCount = dao.countChildCategories(id);
        if (childCount != null && childCount > 0) {
            throw new ValidationException("id", "请先删除子目录再删除本目录");
        }
        int affected = dao.softDeleteCategory(currentActor(), id);
        safeAudit("kb.category.delete", Map.of("id", id, "affected", affected));
    }

    @Override
    public List<NavCategoryVO> moveCategories(List<String> ids, String targetParentId) {
        if (ids == null || ids.isEmpty()) {
            throw new ValidationException("ids", "ids 不能为空");
        }
        if (ids.size() > 50) {
            throw new ValidationException("ids", "单次移动上限 50 个节点");
        }
        boolean root = targetParentId == null || targetParentId.isBlank();
        int targetLevel = root ? LEVEL_1 : (fetchLevel(targetParentId) + 1);
        if (targetLevel > LEVEL_3) {
            throw new ValidationException("targetParentId", "目标父目录层级越界：移动后不能超过 3 级");
        }
        Set<String> blocker = root ? new HashSet<>() : collectSelfAndAncestors(targetParentId);
        for (String id : ids) {
            Map<String, Object> row = fetchCategory(id);
            if (row == null) {
                throw new NotFoundException("kb_nav_category id=" + id);
            }
            if (blocker.contains(id)) {
                throw new ValidationException("ids", "循环移动：不能移动到自己的祖先下（id=" + id + "）");
            }
            int subDepth = descDepth(id);
            if (targetLevel + subDepth - 1 > LEVEL_3) {
                throw new ValidationException("ids", "移动到目标下层级越界（深度限制 3）: id=" + id + "，subDepth=" + subDepth);
            }
        }
        checkAbac("kb.category.move", abac("ids", String.join(",", ids), "targetParentId", root ? "root" : targetParentId));

        List<NavCategoryVO> moved = new ArrayList<>(ids.size());
        String moveActor = currentActor();
        for (String id : ids) {
            Map<String, Object> row = fetchCategory(id);
            String name = str(row.get("name"));
            int newLevel = root ? LEVEL_1 : targetLevel;
            String newPath = root ? ("/" + name) : recomputePath(targetParentId, name);
            dao.moveCategory(root ? null : targetParentId, newLevel, newPath, moveActor, id);
            refreshDescendantPath(id);
            Map<String, Object> exact = fetchCategory(id);
            moved.add(findByRow(exact));
        }
        safeAudit("kb.category.move", Map.of("count", ids.size(), "targetParentId", root ? "root" : targetParentId));
        return moved;
    }

    @Override
    public NavNodeStatsVO statsFor(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            throw new ValidationException("categoryId", "categoryId 不能为空");
        }
        Map<String, Object> row = fetchCategory(categoryId);
        if (row == null) {
            throw new NotFoundException("kb_nav_category id=" + categoryId);
        }
        String path = str(row.get("path"));
        Integer articleCount = dao.countArticlesByPath(path + "%");
        Integer tagCount = dao.countDistinctTagsByPath(path + "%");
        List<String> tagNames = dao.listTagNamesByPath(path + "%");
        NavNodeStatsVO vo = new NavNodeStatsVO();
        vo.setNodeId(categoryId);
        vo.setScope(SCOPE_CATEGORY);
        vo.setArticleCount(articleCount == null ? 0 : articleCount);
        vo.setTagCount(tagCount == null ? 0 : tagCount);
        vo.setTagNames(tagNames == null ? List.of() : tagNames);
        return vo;
    }

    // ═══════════════════════ 标签 CRUD ═══════════════════════

    @Override
    public List<NavTagVO> listTags(String domain) {
        String d = normalizeDomain(domain);
        List<Map<String, Object>> rows = dao.listTags(d);
        List<NavTagVO> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            NavTagVO vo = new NavTagVO();
            vo.setId(str(row.get("id")));
            vo.setDomain(str(row.get("domain")));
            vo.setTagName(str(row.get("tag_name")));
            vo.setUseCount(intVal(row.get("use_count"), 0));
            vo.setCreatedAt(toInstant(row.get("create_time")));
            vo.setUpdatedAt(toInstant(row.get("update_time")));
            out.add(vo);
        }
        return out;
    }

    @Override
    public NavTagVO createTag(String domain, String tagName) {
        String d = normalizeDomain(domain);
        if (tagName == null || tagName.isBlank()) {
            throw new ValidationException("tagName", "标签名不能为空");
        }
        String name = tagName.trim();
        checkAbac("kb.tag.create", abac("domain", d, "name", name));
        try {
            String actor = currentActor();
            dao.insertTag(d, name, actor);
        } catch (DuplicateKeyException e) {
            log.warn("kb-nav createTag 唯一约束命中: domain={} name={}", d, name, e);
            throw new ValidationException("tagName", "同名标签已存在: " + name);
        }
        Map<String, Object> row;
        try {
            row = dao.fetchTagByName(d, name);
        } catch (EmptyResultDataAccessException e) {
            throw new NotFoundException("kb_nav_tag name=" + name);
        }
        safeAudit("kb.tag.create", Map.of("id", row.get("id"), "domain", d));
        NavTagVO vo = new NavTagVO();
        vo.setId(str(row.get("id")));
        vo.setDomain(str(row.get("domain")));
        vo.setTagName(str(row.get("tag_name")));
        vo.setUseCount(0);
        vo.setCreatedAt(toInstant(row.get("create_time")));
        vo.setUpdatedAt(toInstant(row.get("update_time")));
        return vo;
    }

    @Override
    public void deleteTag(String tagId) {
        if (tagId == null || tagId.isBlank()) {
            throw new ValidationException("tagId", "tagId 不能为空");
        }
        Map<String, Object> row = fetchTag(tagId);
        if (row == null) {
            throw new NotFoundException("kb_nav_tag id=" + tagId);
        }
        checkAbac("kb.tag.delete", abac("id", tagId, "domain", str(row.get("domain"))));
        int affected = dao.softDeleteTag(currentActor(), tagId);
        safeAudit("kb.tag.delete", Map.of("id", tagId, "affected", affected));
    }

    // ═══════════════════════ 多选 / 单选 / Undo 关联 ═══════════════════════

    @Override
    @Transactional
    public void setArticleCategories(String articleId, Set<String> categoryIds) {
        checkArticleExists(articleId);
        Set<String> normalized = normalizeCategoryIds(categoryIds);
        checkAbac("kb.article.categories.set", abac("articleId", articleId, "count", normalized.size()));
        String actor = currentActor();
        // 全删再做 UNION（insert 不带限定，避免重复）
        int removed = dao.clearArticleCategories(actor, articleId);
        int added = 0;
        for (String cid : normalized) {
            added += dao.insertArticleCategory(articleId, cid, actor);
        }
        safeAudit("kb.article.categories.set",
                Map.of("articleId", articleId, "removed", removed, "added", added));
    }

    @Override
    @Transactional
    public void setArticleTags(String articleId, Set<String> tagNames) {
        checkArticleExists(articleId);
        Set<String> tagIds = normalizeTagIds(tagNames, normalizeDomain(null));
        checkAbac("kb.article.tags.set", abac("articleId", articleId, "count", tagIds.size()));
        String actor = currentActor();
        int removed = dao.clearArticleTags(actor, articleId);
        int added = 0;
        for (String tid : tagIds) {
            added += dao.insertArticleTag(articleId, tid, actor);
        }
        safeAudit("kb.article.tags.set",
                Map.of("articleId", articleId, "removed", removed, "added", added));
    }

    @Override
    @Transactional
    public void addArticleCategories(String articleId, Set<String> categoryIds) {
        checkArticleExists(articleId);
        Set<String> normalized = normalizeCategoryIds(categoryIds);
        if (normalized.isEmpty()) {
            return;
        }
        checkAbac("kb.article.categories.add", abac("articleId", articleId, "count", normalized.size()));
        String actor = currentActor();
        int added = 0;
        for (String cid : normalized) {
            added += dao.addArticleCategoryIfAbsent(articleId, cid, actor);
        }
        safeAudit("kb.article.categories.add", Map.of("articleId", articleId, "added", added));
    }

    @Override
    @Transactional
    public void addArticleTags(String articleId, Set<String> tagNames) {
        checkArticleExists(articleId);
        Set<String> tagIds = normalizeTagIds(tagNames, normalizeDomain(null));
        if (tagIds.isEmpty()) {
            return;
        }
        checkAbac("kb.article.tags.add", abac("articleId", articleId, "count", tagIds.size()));
        String actor = currentActor();
        int added = 0;
        for (String tid : tagIds) {
            added += dao.addArticleTagIfAbsent(articleId, tid, actor);
        }
        safeAudit("kb.article.tags.add", Map.of("articleId", articleId, "added", added));
    }

    @Override
    @Transactional
    public void removeArticleCategories(String articleId, Set<String> categoryIds) {
        if (articleId == null || articleId.isBlank()) {
            throw new ValidationException("articleId", "articleId 不能为空");
        }
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }
        checkAbac("kb.article.categories.remove", abac("articleId", articleId, "count", categoryIds.size()));
        String actor = currentActor();
        for (String cid : categoryIds) {
            if (cid == null || cid.isBlank()) {
                continue;
            }
            dao.removeArticleCategory(actor, articleId, cid);
        }
        safeAudit("kb.article.categories.remove", Map.of("articleId", articleId, "count", categoryIds.size()));
    }

    @Override
    @Transactional
    public void removeArticleTags(String articleId, Set<String> tagNames) {
        if (articleId == null || articleId.isBlank()) {
            throw new ValidationException("articleId", "articleId 不能为空");
        }
        if (tagNames == null || tagNames.isEmpty()) {
            return;
        }
        checkAbac("kb.article.tags.remove", abac("articleId", articleId, "count", tagNames.size()));
        String actor = currentActor();
        for (String name : tagNames) {
            if (name == null || name.isBlank()) {
                continue;
            }
            dao.removeArticleTagNamed(actor, name.trim(), articleId);
        }
        safeAudit("kb.article.tags.remove", Map.of("articleId", articleId, "count", tagNames.size()));
    }

    @Override
    @Transactional
    public void undoArticle(String articleId, String scope) {
        if (articleId == null || articleId.isBlank()) {
            throw new ValidationException("articleId", "articleId 不能为空");
        }
        if (scope == null || scope.isBlank() || !(SCOPE_CATEGORY.equals(scope) || SCOPE_TAG.equals(scope))) {
            throw new ValidationException("scope", "scope 只能是 'category' 或 'tag'");
        }
        checkArticleExists(articleId);
        checkAbac("kb.article.undo." + scope, abac("articleId", articleId, "scope", scope));
        int affected = dao.undoArticle(currentActor(), articleId, scope);
        safeAudit("kb.article.undo." + scope, Map.of("articleId", articleId, "affected", affected));
    }

    // ═══════════════════════ 资产列表 ═══════════════════════

    @Override
    public List<NavProductItemVO> listProducts(NavFolderQuery query) {
        int pageNum = query == null || query.getPageNum() == null ? 1 : Math.max(query.getPageNum(), 1);
        int pageSize = query == null || query.getPageSize() == null ? 20 : Math.min(Math.max(query.getPageSize(), 1), MAX_PAGE_SIZE);
        int offset = (pageNum - 1) * pageSize;

        List<Object> args = new ArrayList<>();
        String where = buildProductWhere(query, args);
        List<Map<String, Object>> rows = dao.listProducts(where, args.toArray(), pageSize, offset);
        List<NavProductItemVO> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            NavProductItemVO vo = new NavProductItemVO();
            vo.setId(str(row.get("id")));
            vo.setTitle(str(row.get("title")));
            vo.setSource(str(row.get("source")));
            vo.setDomain(str(row.get("domain")));
            vo.setCategory(str(row.get("category")));
            vo.setStatus(str(row.get("status")));
            vo.setUpdatedAt(toInstant(row.get("updated_at")));
            out.add(vo);
        }
        return out;
    }

    @Override
    public long countProducts(NavFolderQuery query) {
        List<Object> args = new ArrayList<>();
        String where = buildProductWhere(query, args);
        Long c = dao.countProducts(where, args.toArray());
        return c == null ? 0 : c;
    }

    /** 构造 products WHERE 子句 + 累加 args（按数组位置顺序）。 */
    private String buildProductWhere(NavFolderQuery query, List<Object> args) {
        StringBuilder sb = new StringBuilder("1=1 AND a.status IS DISTINCT FROM 'archived' ");
        if (query == null) {
            return sb.toString();
        }
        String d = query.getDomain() == null || query.getDomain().isBlank() ? null : query.getDomain().trim();
        if (d != null) {
            sb.append("AND a.domain = ? ");
            args.add(d);
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim().toLowerCase(Locale.ROOT) + "%";
            sb.append("AND (lower(a.title) LIKE ? OR lower(a.content) LIKE ?) ");
            args.add(kw);
            args.add(kw);
        }
        if (query.getType() != null && !query.getType().isBlank()) {
            sb.append("AND lower(a.category) = lower(?) ");
            args.add(query.getType().trim());
        }
        if (query.getCategoryIds() != null && !query.getCategoryIds().isEmpty()) {
            List<String> subtreePaths = new ArrayList<>();
            for (String cid : query.getCategoryIds()) {
                if (cid == null || cid.isBlank()) {
                    continue;
                }
                Map<String, Object> row = fetchCategory(cid);
                if (row == null) {
                    throw new NotFoundException("kb_nav_category id=" + cid);
                }
                String p = str(row.get("path"));
                if (p != null && !p.isBlank()) {
                    subtreePaths.add(p + "%");
                }
            }
            if (!subtreePaths.isEmpty()) {
                sb.append("AND EXISTS (SELECT 1 FROM " + schema + ".kb_nav_article_rel r ")
                  .append("JOIN " + schema + ".kb_nav_category c ON c.id = r.node_id AND c.is_deleted = 0 ")
                  .append("WHERE r.article_id = a.id AND r.scope = 'category' AND r.is_deleted = 0 AND (");
                for (int i = 0; i < subtreePaths.size(); i++) {
                    if (i > 0) {
                        sb.append(" OR ");
                    }
                    sb.append("c.path LIKE ?");
                    args.add(subtreePaths.get(i));
                }
                sb.append(")) ");
            }
        }
        if (query.getTags() != null && !query.getTags().isEmpty()) {
            int size = query.getTags().size();
            sb.append("AND EXISTS (SELECT 1 FROM " + schema + ".kb_nav_article_rel r2 ")
              .append("JOIN " + schema + ".kb_nav_tag t ON t.id = r2.node_id AND t.is_deleted = 0 ")
              .append("WHERE r2.article_id = a.id AND r2.scope = 'tag' AND r2.is_deleted = 0 ")
              .append("AND t.tag_name IN (");
            for (int i = 0; i < size; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append('?');
                args.add(query.getTags().get(i));
            }
            sb.append(")) ");
        }
        return sb.toString();
    }

    // ═══════════════════════ LLM 建议 ═══════════════════════

    @Override
    public NavRecommendVO recommend(String articleId) {
        if (articleId == null || articleId.isBlank()) {
            throw new ValidationException("articleId", "articleId 不能为空");
        }
        Map<String, Object> row;
        try {
            row = dao.readArticleForRecommend(articleId);
        } catch (EmptyResultDataAccessException e) {
            throw new NotFoundException("knowledge_article id=" + articleId);
        }
        String title = str(row.get("title"));
        String content = str(row.get("content"));
        String domain = str(row.get("domain"));
        return llmRecommendService.recommend(articleId, title, content, domain);
    }

    @Override
    public List<String> listDomains() {
        // PMO-C T2: distinct domain from category UNION distinct domain from tag（去 duplicate，升序）
        try {
            List<Map<String, Object>> rows = dao.listDomains();
            List<String> out = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                String d = str(row.get("domain"));
                if (d != null && !d.isBlank()) {
                    out.add(d);
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("nav listDomains 失败，回退为 default 单域: {}", e.getMessage(), e);
            return new ArrayList<>(List.of("default"));
        }
    }

    // ═══════════════════════ 私有 helpers ═══════════════════════

    private static Map<String, Object> abac(Object... kv) {
        if (kv == null || kv.length % 2 != 0) {
            throw new IllegalArgumentException("abac key/value 必须成对");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private void checkAbac(String action, Map<String, Object> attrs) {
        if (!securityClient.evaluatePolicy(action, attrs)) {
            throw new ForbiddenException("知识导航: " + action + " 被安全策略拒绝");
        }
    }

    /**
     * F04-16 写入归因：解析当前线程的实际 userId（经 ServiceContext 由网关注入）。
     * <p>缺失（本地/单测/无上下文的后台任务）回退 "system" — 见 F04-16 规格。</p>
     */
    private String currentActor() {
        try {
            ServiceContext ctx = ServiceContext.current();
            if (ctx != null && ctx.getUserId() != null && !ctx.getUserId().isBlank()) {
                return ctx.getUserId();
            }
        } catch (Exception ignored) { }
        return "system";
    }

    /**
     * F04-15 审计 side-call：调用 securityClient.audit 并对
     * {@link KbErrorCodes#EVENT_BASE_UNAVAILABLE} fail-closed 异常做显式降级 ——
     * 写已落库、审计失败时仅 log.error 让上层运维感知（不回滚业务写；
     * 这与"写本身 = 成功、审计 = 侧信道"的语义一致）。
     * <p>其它异常（如 IllegalState / 网络错）也按 warn 吞，不阻塞业务。</p>
     */
    private void safeAudit(String action, Map<String, Object> attr) {
        try {
            securityClient.audit(action, attr);
        } catch (Exception ex) {
            log.error("audit 失败（写已落库，审计降级）: action={} reason={}", action, ex.getMessage());
        }
    }

    private static String normalizeDomain(String d) {
        return (d == null || d.isBlank()) ? "default" : d.trim();
    }

    private String computePath(String parentId, String name) {
        if (parentId == null || parentId.isBlank()) {
            return "/" + name;
        }
        Map<String, Object> p = fetchCategory(parentId);
        if (p == null) {
            throw new ValidationException("parentId", "父目录不存在: " + parentId);
        }
        String parentPath = str(p.get("path"));
        if (parentPath == null || parentPath.isBlank()) {
            parentPath = "/" + str(p.get("name"));
        }
        return parentPath + "/" + name;
    }

    private String recomputePath(String targetParentId, String name) {
        if (targetParentId == null || targetParentId.isBlank()) {
            return "/" + name;
        }
        Map<String, Object> p = fetchCategory(targetParentId);
        if (p == null) {
            throw new ValidationException("targetParentId", "目标父目录不存在: " + targetParentId);
        }
        String parentPath = str(p.get("path"));
        if (parentPath == null || parentPath.isBlank()) {
            parentPath = "/" + str(p.get("name"));
        }
        return parentPath + "/" + name;
    }

    private int fetchLevel(String id) {
        if (id == null || id.isBlank()) {
            return 0;
        }
        Integer l = dao.fetchCategoryLevel(id);
        if (l == null) {
            throw new NotFoundException("kb_nav_category id=" + id);
        }
        return l;
    }

    /** 节点（含自身）的最大深度，用于「移动越界」校验。 */
    private int descDepth(String rootId) {
        Integer maxDepth = dao.fetchDescDepth(rootId);
        return maxDepth == null ? 1 : maxDepth;
    }

    /** 收集某节点的祖先 ID（含自身），用于循环检测。 */
    private Set<String> collectSelfAndAncestors(String rootId) {
        List<String> ids = dao.fetchSelfAndAncestors(rootId);
        return ids == null ? new HashSet<>() : new HashSet<>(ids);
    }

    /** 移动节点后刷新其子节点 path（重新计算子树 path）。 */
    private void refreshDescendantPath(String rootId) {
        Map<String, Object> root = fetchCategory(rootId);
        if (root == null) {
            return;
        }
        String rootPath = str(root.get("path"));
        if (rootPath == null || rootPath.isBlank()) {
            return;
        }
        // 子节点按 parent 链逐层重写：每层取父节点的 path + name
        // 简化实现：拉一次目录表，按 parent_id 拓扑推进（kb-nav 目录规模有限，可接受）
        List<Map<String, Object>> all = dao.fetchAllAliveCategories();
        Map<String, String> idToPath = new java.util.HashMap<>();
        idToPath.put(rootId, rootPath);
        int maxRound = 50;
        for (int round = 0; round < maxRound; round++) {
            boolean changed = false;
            for (Map<String, Object> row : all) {
                String id = str(row.get("id"));
                String pid = str(row.get("parent_id"));
                String name = str(row.get("name"));
                String parentPath = idToPath.get(pid);
                if (id.equals(rootId) || parentPath == null) {
                    continue;
                }
                if (!idToPath.containsKey(id)) {
                    idToPath.put(id, parentPath + "/" + name);
                    dao.updateCategoryPath(parentPath + "/" + name, id);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
    }

    private Set<String> normalizeCategoryIds(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            Map<String, Object> row = fetchCategory(id);
            if (row == null) {
                throw new NotFoundException("kb_nav_category id=" + id);
            }
            out.add(id);
        }
        return out;
    }

    private Set<String> normalizeTagIds(Set<String> names, String d) {
        if (names == null || names.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            try {
                Map<String, Object> row = dao.fetchTagIdByName(name.trim(), d);
                out.add(str(row.get("id")));
            } catch (EmptyResultDataAccessException e) {
                throw new NotFoundException("kb_nav_tag name=" + name + " domain=" + d);
            }
        }
        return out;
    }

    private void checkArticleExists(String articleId) {
        if (articleId == null || articleId.isBlank()) {
            throw new ValidationException("articleId", "articleId 不能为空");
        }
        Integer cnt = dao.countArticle(articleId);
        if (cnt == null || cnt == 0) {
            throw new NotFoundException("knowledge_article id=" + articleId);
        }
    }

    private Map<String, Object> fetchCategory(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return dao.fetchCategoryRow(id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private Map<String, Object> fetchTag(String tagId) {
        if (tagId == null || tagId.isBlank()) {
            return null;
        }
        try {
            return dao.fetchTagRow(tagId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private Map<String, Object> findByIdRequired(String domain, String name) {
        try {
            return dao.fetchCategoryByNameRequired(domain, name);
        } catch (EmptyResultDataAccessException e) {
            throw new NotFoundException("kb_nav_category name=" + name + " domain=" + domain);
        }
    }

    private NavCategoryVO findByRow(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        NavCategoryVO vo = new NavCategoryVO();
        vo.setId(str(row.get("id")));
        vo.setDomain(str(row.get("domain")));
        vo.setParentId(str(row.get("parent_id")));
        vo.setName(str(row.get("name")));
        vo.setPath(str(row.get("path")));
        vo.setLevel(intVal(row.get("level"), LEVEL_1));
        vo.setSortOrder(intVal(row.get("sort_order"), 0));
        vo.setChildCount(intVal(row.get("child_count"), 0));
        vo.setArticleCount(intVal(row.get("article_count"), 0));
        vo.setCreatedAt(toInstant(row.get("create_time")));
        vo.setUpdatedAt(toInstant(row.get("update_time")));
        vo.setParentName(row.get("parent_name") == null ? "" : String.valueOf(row.get("parent_name")));
        return vo;
    }

    private static int intVal(Object o, int def) {
        if (o == null) {
            return def;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(o.toString());
        } catch (Exception e) {
            return def;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static Instant toInstant(Object o) {
        if (o instanceof Timestamp ts) {
            return ts.toInstant();
        }
        return null;
    }
}
