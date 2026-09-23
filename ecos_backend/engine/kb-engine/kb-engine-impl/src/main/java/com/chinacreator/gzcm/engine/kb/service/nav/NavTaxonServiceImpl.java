package com.chinacreator.gzcm.engine.kb.service.nav;

import com.chinacreator.gzcm.common.exception.ForbiddenException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.common.exception.ValidationException;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * <p><b>数据访问</b>：与 kb-engine-impl 现存风格一致（{@code KnowledgeLifecycleService}）走
 * {@link JdbcTemplate}，schema {@code ecos_knowledge}（不跨引擎 SQL）。</p>
 *
 * <p><b>ABAC</b>：写操作前调用 {@code KnowledgeNavSecurityEngineClient.evaluatePolicy}，
 * 不可用/拒绝一律抛 403（铁律 §2.4-6：宁可误拒）。</p>
 *
 * <p><b>审计</b>：写操作成功后调 {@code KnowledgeNavSecurityEngineClient.audit(...)}
 * 发 Kafka {@code ecos.audit}（失败仅 warn，不阻塞业务）。</p>
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

    private final JdbcTemplate jdbc;
    private final KnowledgeNavSecurityEngineClient securityClient;
    private final NavLlmRecommendService llmRecommendService;

    public NavTaxonServiceImpl(JdbcTemplate jdbc,
                               KnowledgeNavSecurityEngineClient securityClient,
                               NavLlmRecommendService llmRecommendService) {
        this.jdbc = jdbc;
        this.securityClient = securityClient;
        this.llmRecommendService = llmRecommendService;
    }

    // ═══════════════════════ 目录 CRUD ═══════════════════════

    @Override
    public List<NavCategoryVO> listCategories(String domain, String parentId, String search) {
        String d = normalizeDomain(domain);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT c.id, c.domain, c.parent_id, c.name, c.path, c.level, c.sort_order, ")
           .append("       c.create_time, c.update_time, ")
           .append("       COALESCE(p.name, '') AS parent_name, ")
           .append("       (SELECT COUNT(*) FROM ecos_knowledge.kb_nav_category child ")
           .append("         WHERE child.parent_id = c.id AND child.is_deleted = 0) AS child_count, ")
           .append("       (SELECT COUNT(*) FROM ecos_knowledge.kb_nav_article_rel rel ")
           .append("       JOIN ecos_knowledge.kb_nav_category sub ON sub.id = rel.node_id AND sub.is_deleted = 0 ")
           .append("       WHERE rel.is_deleted = 0 AND rel.scope = 'category' AND sub.path LIKE c.path || '%') AS article_count ")
           .append("FROM ecos_knowledge.kb_nav_category c ")
           .append("LEFT JOIN ecos_knowledge.kb_nav_category p ON p.id = c.parent_id AND p.is_deleted = 0 ")
           .append("WHERE c.is_deleted = 0 AND c.domain = ? ");
        List<Object> args = new ArrayList<>();
        args.add(d);
        if (parentId != null && !parentId.isBlank()) {
            sql.append("AND c.parent_id = ? ");
            args.add(parentId);
        }
        if (search != null && !search.isBlank()) {
            sql.append("AND (lower(c.name) LIKE ? OR lower(c.path) LIKE ?) ");
            String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            args.add(like);
            args.add(like);
        }
        sql.append("ORDER BY c.level ASC, c.sort_order ASC, c.name ASC");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
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
            jdbc.update(
                    "INSERT INTO ecos_knowledge.kb_nav_category (id, domain, parent_id, name, path, level, sort_order, create_by, update_by) "
                            + "VALUES (gen_random_uuid()::text, ?, ?, ?, ?, ?, ?, 'current-user', 'current-user')",
                    d,
                    parentBlank ? null : dto.getParentId(),
                    name,
                    computePath(parentBlank ? null : dto.getParentId(), name),
                    level,
                    sortOrder);
        } catch (DuplicateKeyException e) {
            log.warn("kb-nav createCategory 唯一约束命中: domain={} name={}", d, name, e);
            throw new ValidationException("name", "同名目录已存在: " + name);
        }
        NavCategoryVO created = findByRow(findByIdRequired(d, name));
        securityClient.audit("kb.category.create", Map.of("id", created.getId(), "domain", d));
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
            Integer dup = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecos_knowledge.kb_nav_category WHERE domain = ? AND name = ? AND id <> ? AND is_deleted = 0",
                    Integer.class, d, newName, id);
            if (dup != null && dup > 0) {
                throw new ValidationException("name", "同名目录已存在: " + newName);
            }
        }
        jdbc.update(
                "UPDATE ecos_knowledge.kb_nav_category SET name = ?, sort_order = ?, update_time = NOW(), "
                        + "update_by = 'current-user', version_no = (CAST(version_no AS INTEGER) + 1)::text "
                        + "WHERE id = ? AND is_deleted = 0",
                newName, sortOrder, id);
        NavCategoryVO updated = findByRow(findByIdRequired(d, newName == null ? currentName : newName));
        // 取精确 id（名改名场景下按 name 匹配可能不唯一，按 id 取）
        Map<String, Object> exact = fetchCategory(id);
        securityClient.audit("kb.category.update", Map.of("id", id, "newName", newName));
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
        Integer childCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ecos_knowledge.kb_nav_category WHERE parent_id = ? AND is_deleted = 0",
                Integer.class, id);
        if (childCount != null && childCount > 0) {
            throw new ValidationException("id", "请先删除子目录再删除本目录");
        }
        int affected = jdbc.update(
                "UPDATE ecos_knowledge.kb_nav_category SET is_deleted = 1, update_time = NOW(), "
                        + "update_by = 'current-user' WHERE id = ? AND is_deleted = 0",
                id);
        securityClient.audit("kb.category.delete", Map.of("id", id, "affected", affected));
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
        for (String id : ids) {
            Map<String, Object> row = fetchCategory(id);
            String name = str(row.get("name"));
            int newLevel = root ? LEVEL_1 : targetLevel;
            String newPath = root ? ("/" + name) : recomputePath(targetParentId, name);
            jdbc.update(
                    "UPDATE ecos_knowledge.kb_nav_category SET parent_id = ?, level = ?, path = ?, "
                            + "update_time = NOW(), update_by = 'current-user', "
                            + "version_no = (CAST(version_no AS INTEGER) + 1)::text "
                            + "WHERE id = ? AND is_deleted = 0",
                    root ? null : targetParentId, newLevel, newPath, id);
            refreshDescendantPath(id);
            Map<String, Object> exact = fetchCategory(id);
            moved.add(findByRow(exact));
        }
        securityClient.audit("kb.category.move", Map.of("count", ids.size(), "targetParentId", root ? "root" : targetParentId));
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
        Integer articleCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ecos_knowledge.kb_nav_article_rel r "
                        + "JOIN ecos_knowledge.kb_nav_category c ON c.id = r.node_id AND c.is_deleted = 0 "
                        + "WHERE r.is_deleted = 0 AND r.scope = 'category' AND c.path LIKE ?",
                Integer.class, path + "%");
        Integer tagCount = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT t.tag_name) FROM ecos_knowledge.kb_nav_article_rel r "
                        + "JOIN ecos_knowledge.kb_nav_tag t ON t.id = r.node_id AND t.is_deleted = 0 "
                        + "JOIN ecos_knowledge.kb_nav_article_rel cr ON cr.article_id = r.article_id AND cr.scope = 'category' AND cr.is_deleted = 0 "
                        + "JOIN ecos_knowledge.kb_nav_category c ON c.id = cr.node_id AND c.is_deleted = 0 "
                        + "WHERE r.scope = 'tag' AND r.is_deleted = 0 AND c.path LIKE ?",
                Integer.class, path + "%");
        List<String> tagNames = jdbc.queryForList(
                "SELECT DISTINCT t.tag_name FROM ecos_knowledge.kb_nav_article_rel r "
                        + "JOIN ecos_knowledge.kb_nav_tag t ON t.id = r.node_id AND t.is_deleted = 0 "
                        + "JOIN ecos_knowledge.kb_nav_article_rel cr ON cr.article_id = r.article_id AND cr.scope = 'category' AND cr.is_deleted = 0 "
                        + "JOIN ecos_knowledge.kb_nav_category c ON c.id = cr.node_id AND c.is_deleted = 0 "
                        + "WHERE r.scope = 'tag' AND r.is_deleted = 0 AND c.path LIKE ? "
                        + "ORDER BY t.tag_name LIMIT 50",
                String.class, path + "%");
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
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT t.id, t.domain, t.tag_name, t.create_time, t.update_time, "
                        + "(SELECT COUNT(*) FROM ecos_knowledge.kb_nav_article_rel r "
                        + "   WHERE r.node_id = t.id AND r.scope = 'tag' AND r.is_deleted = 0) AS use_count "
                        + "FROM ecos_knowledge.kb_nav_tag t WHERE t.domain = ? AND t.is_deleted = 0 "
                        + "ORDER BY t.tag_name ASC", d);
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
            jdbc.update(
                    "INSERT INTO ecos_knowledge.kb_nav_tag (id, domain, tag_name, create_by, update_by) "
                            + "VALUES (gen_random_uuid()::text, ?, ?, 'current-user', 'current-user')",
                    d, name);
        } catch (DuplicateKeyException e) {
            log.warn("kb-nav createTag 唯一约束命中: domain={} name={}", d, name, e);
            throw new ValidationException("tagName", "同名标签已存在: " + name);
        }
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap(
                    "SELECT id, domain, tag_name, create_time, update_time FROM ecos_knowledge.kb_nav_tag WHERE domain = ? AND tag_name = ? AND is_deleted = 0",
                    d, name);
        } catch (EmptyResultDataAccessException e) {
            throw new NotFoundException("kb_nav_tag name=" + name);
        }
        securityClient.audit("kb.tag.create", Map.of("id", row.get("id"), "domain", d));
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
        int affected = jdbc.update(
                "UPDATE ecos_knowledge.kb_nav_tag SET is_deleted = 1, update_time = NOW(), update_by = 'current-user' WHERE id = ? AND is_deleted = 0",
                tagId);
        securityClient.audit("kb.tag.delete", Map.of("id", tagId, "affected", affected));
    }

    // ═══════════════════════ 多选 / 单选 / Undo 关联 ═══════════════════════

    @Override
    @Transactional
    public void setArticleCategories(String articleId, Set<String> categoryIds) {
        checkArticleExists(articleId);
        Set<String> normalized = normalizeCategoryIds(categoryIds);
        checkAbac("kb.article.categories.set", abac("articleId", articleId, "count", normalized.size()));
        // 全删再做 UNION（insert 不带限定，避免重复）
        int removed = jdbc.update(
                "UPDATE ecos_knowledge.kb_nav_article_rel SET is_deleted = 1, update_time = NOW(), update_by = 'current-user' "
                        + "WHERE article_id = ? AND scope = 'category' AND is_deleted = 0",
                articleId);
        int added = 0;
        for (String cid : normalized) {
            added += jdbc.update(
                    "INSERT INTO ecos_knowledge.kb_nav_article_rel (article_id, node_id, scope, create_by, update_by) "
                            + "VALUES (?, ?, 'category', 'current-user', 'current-user')",
                    articleId, cid);
        }
        securityClient.audit("kb.article.categories.set",
                Map.of("articleId", articleId, "removed", removed, "added", added));
    }

    @Override
    @Transactional
    public void setArticleTags(String articleId, Set<String> tagNames) {
        checkArticleExists(articleId);
        Set<String> tagIds = normalizeTagIds(tagNames, normalizeDomain(null));
        checkAbac("kb.article.tags.set", abac("articleId", articleId, "count", tagIds.size()));
        int removed = jdbc.update(
                "UPDATE ecos_knowledge.kb_nav_article_rel SET is_deleted = 1, update_time = NOW(), update_by = 'current-user' "
                        + "WHERE article_id = ? AND scope = 'tag' AND is_deleted = 0",
                articleId);
        int added = 0;
        for (String tid : tagIds) {
            added += jdbc.update(
                    "INSERT INTO ecos_knowledge.kb_nav_article_rel (article_id, node_id, scope, create_by, update_by) "
                            + "VALUES (?, ?, 'tag', 'current-user', 'current-user')",
                    articleId, tid);
        }
        securityClient.audit("kb.article.tags.set",
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
        int added = 0;
        for (String cid : normalized) {
            added += jdbc.update(
                    "INSERT INTO ecos_knowledge.kb_nav_article_rel (article_id, node_id, scope, create_by, update_by) "
                            + "SELECT ?, ?, 'category', 'current-user', 'current-user' "
                            + "WHERE NOT EXISTS (SELECT 1 FROM ecos_knowledge.kb_nav_article_rel r2 "
                            + "  WHERE r2.article_id = ? AND r2.node_id = ? AND r2.scope = 'category' AND r2.is_deleted = 0)",
                    articleId, cid, articleId, cid);
        }
        securityClient.audit("kb.article.categories.add", Map.of("articleId", articleId, "added", added));
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
        int added = 0;
        for (String tid : tagIds) {
            added += jdbc.update(
                    "INSERT INTO ecos_knowledge.kb_nav_article_rel (article_id, node_id, scope, create_by, update_by) "
                            + "SELECT ?, ?, 'tag', 'current-user', 'current-user' "
                            + "WHERE NOT EXISTS (SELECT 1 FROM ecos_knowledge.kb_nav_article_rel r2 "
                            + "  WHERE r2.article_id = ? AND r2.node_id = ? AND r2.scope = 'tag' AND r2.is_deleted = 0)",
                    articleId, tid, articleId, tid);
        }
        securityClient.audit("kb.article.tags.add", Map.of("articleId", articleId, "added", added));
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
        for (String cid : categoryIds) {
            if (cid == null || cid.isBlank()) {
                continue;
            }
            jdbc.update(
                    "UPDATE ecos_knowledge.kb_nav_article_rel SET is_deleted = 1, update_time = NOW(), update_by = 'current-user' "
                            + "WHERE article_id = ? AND node_id = ? AND scope = 'category' AND is_deleted = 0",
                    articleId, cid);
        }
        securityClient.audit("kb.article.categories.remove", Map.of("articleId", articleId, "count", categoryIds.size()));
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
        for (String name : tagNames) {
            if (name == null || name.isBlank()) {
                continue;
            }
            jdbc.update(
                    "UPDATE ecos_knowledge.kb_nav_article_rel r SET is_deleted = 1, update_time = NOW(), update_by = 'current-user' "
                            + "FROM ecos_knowledge.kb_nav_tag t "
                            + "WHERE r.node_id = t.id AND t.tag_name = ? AND t.is_deleted = 0 "
                            + "AND r.article_id = ? AND r.scope = 'tag' AND r.is_deleted = 0",
                    name.trim(), articleId);
        }
        securityClient.audit("kb.article.tags.remove", Map.of("articleId", articleId, "count", tagNames.size()));
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
        int affected = jdbc.update(
                "UPDATE ecos_knowledge.kb_nav_article_rel SET is_deleted = 1, update_time = NOW(), update_by = 'current-user' "
                        + "WHERE article_id = ? AND scope = ? AND is_deleted = 0",
                articleId, scope);
        securityClient.audit("kb.article.undo." + scope, Map.of("articleId", articleId, "affected", affected));
    }

    // ═══════════════════════ 资产列表 ═══════════════════════

    @Override
    public List<NavProductItemVO> listProducts(NavFolderQuery query) {
        int pageNum = query == null || query.getPageNum() == null ? 1 : Math.max(query.getPageNum(), 1);
        int pageSize = query == null || query.getPageSize() == null ? 20 : Math.min(Math.max(query.getPageSize(), 1), MAX_PAGE_SIZE);
        int offset = (pageNum - 1) * pageSize;

        List<Object> args = new ArrayList<>();
        String where = buildProductWhere(query, args);
        String sql = "SELECT a.id, a.title, a.source, a.domain, a.category, a.status, a.updated_at "
                + "FROM ecos_knowledge.knowledge_article a "
                + "WHERE " + where + " "
                + "ORDER BY a.updated_at DESC "
                + "LIMIT " + pageSize + " OFFSET " + offset;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args.toArray());
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
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM ecos_knowledge.knowledge_article a WHERE " + where,
                Long.class, args.toArray());
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
                sb.append("AND EXISTS (SELECT 1 FROM ecos_knowledge.kb_nav_article_rel r ")
                  .append("JOIN ecos_knowledge.kb_nav_category c ON c.id = r.node_id AND c.is_deleted = 0 ")
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
            sb.append("AND EXISTS (SELECT 1 FROM ecos_knowledge.kb_nav_article_rel r2 ")
              .append("JOIN ecos_knowledge.kb_nav_tag t ON t.id = r2.node_id AND t.is_deleted = 0 ")
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
            row = jdbc.queryForMap(
                    "SELECT id, title, content, domain FROM ecos_knowledge.knowledge_article WHERE id = ?",
                    articleId);
        } catch (EmptyResultDataAccessException e) {
            throw new NotFoundException("knowledge_article id=" + articleId);
        }
        String title = str(row.get("title"));
        String content = str(row.get("content"));
        String domain = str(row.get("domain"));
        return llmRecommendService.recommend(articleId, title, content, domain);
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
        Integer l = jdbc.queryForObject(
                "SELECT level FROM ecos_knowledge.kb_nav_category WHERE id = ? AND is_deleted = 0",
                Integer.class, id);
        if (l == null) {
            throw new NotFoundException("kb_nav_category id=" + id);
        }
        return l;
    }

    /** 节点（含自身）的最大深度，用于「移动越界」校验。 */
    private int descDepth(String rootId) {
        Integer maxDepth = jdbc.queryForObject(
                "WITH RECURSIVE walk AS ("
                        + "  SELECT id AS self_id, 1 AS d FROM ecos_knowledge.kb_nav_category "
                        + "   WHERE id = ? AND is_deleted = 0 "
                        + "  UNION ALL "
                        + "  SELECT c.id, w.d + 1 FROM ecos_knowledge.kb_nav_category c "
                        + "  JOIN walk w ON c.parent_id = w.self_id WHERE c.is_deleted = 0"
                        + ") SELECT MAX(d) FROM walk",
                Integer.class, rootId);
        return maxDepth == null ? 1 : maxDepth;
    }

    /** 收集某节点的祖先 ID（含自身），用于循环检测。 */
    private Set<String> collectSelfAndAncestors(String rootId) {
        List<String> ids = jdbc.queryForList(
                "WITH RECURSIVE up AS ("
                        + "  SELECT id FROM ecos_knowledge.kb_nav_category WHERE id = ? AND is_deleted = 0 "
                        + "  UNION ALL "
                        + "  SELECT c.id FROM ecos_knowledge.kb_nav_category c "
                        + "  JOIN up u ON c.id = ("
                        + "     SELECT p2.parent_id::text FROM ecos_knowledge.kb_nav_category p2 WHERE p2.id = u.id"
                        + "  ) WHERE c.is_deleted = 0"
                        + ") SELECT id FROM up",
                String.class, rootId);
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
        List<Map<String, Object>> all = jdbc.queryForList(
                "SELECT id, parent_id, name FROM ecos_knowledge.kb_nav_category WHERE is_deleted = 0");
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
                    jdbc.update("UPDATE ecos_knowledge.kb_nav_category SET path = ? WHERE id = ?",
                            parentPath + "/" + name, id);
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
                Map<String, Object> row = jdbc.queryForMap(
                        "SELECT id FROM ecos_knowledge.kb_nav_tag WHERE tag_name = ? AND domain = ? AND is_deleted = 0",
                        name.trim(), d);
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
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ecos_knowledge.knowledge_article WHERE id = ?",
                Integer.class, articleId);
        if (cnt == null || cnt == 0) {
            throw new NotFoundException("knowledge_article id=" + articleId);
        }
    }

    private Map<String, Object> fetchCategory(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return jdbc.queryForMap(
                    "SELECT id, domain, parent_id, name, path, level, sort_order, create_time, update_time "
                            + "FROM ecos_knowledge.kb_nav_category WHERE id = ? AND is_deleted = 0", id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private Map<String, Object> fetchTag(String tagId) {
        if (tagId == null || tagId.isBlank()) {
            return null;
        }
        try {
            return jdbc.queryForMap(
                    "SELECT id, domain, tag_name, create_time, update_time "
                            + "FROM ecos_knowledge.kb_nav_tag WHERE id = ? AND is_deleted = 0", tagId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private Map<String, Object> findByIdRequired(String domain, String name) {
        try {
            return jdbc.queryForMap(
                    "SELECT id, domain, parent_id, name, path, level, sort_order, create_time, update_time "
                            + "FROM ecos_knowledge.kb_nav_category WHERE domain = ? AND name = ? AND is_deleted = 0",
                    domain, name);
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
