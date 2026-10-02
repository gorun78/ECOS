package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * <p>V200/R3-B1: schema 由 `${kb.control-schema:ecos_knowledge}` 注入 (MC06 全面落地)。</p>
 *
 * <p>数据访问收口（R2-B2b kb 子包 / 架构铁律 §2.5 / IR06）：知识导航（目录/标签/关系/资产）
 * 全量 JDBC 落点。
 *
 * <p>SQL 原样搬运自 {@code service.nav.NavTaxonServiceImpl}，schema 前缀
 * {@code ecos_knowledge} 全部保留。Service 侧业务编排（ABAC / undo / 越界校验 /
 * fallback 回退 default 域）维持不变；dynamic WHERE 拼接随 SQL 一并落入 DAO
 * （byte-for-byte）。</p>
 */
@Repository
public class NavTaxonDao extends JdbcTemplateDaoSupport {

    private final String schema;

    public NavTaxonDao(
            @Value("${kb.control-schema:ecos_knowledge}") String schema) {
        this.schema = schema;
    }

    // ── 目录 CRUD ────────────────────────────────────────────────

    /** 目录列表（4 段 dynamic WHERE：domain 必选；parent / 名称 path 双 like 可选）。 */
    public List<Map<String, Object>> listCategories(String domain, String parentId, String search) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT c.id, c.domain, c.parent_id, c.name, c.path, c.level, c.sort_order, ")
           .append("       c.create_time, c.update_time, ")
           .append("       COALESCE(p.name, '') AS parent_name, ")
           .append("       (SELECT COUNT(*) FROM " + schema + ".kb_nav_category child ")
           .append("         WHERE child.parent_id = c.id AND child.is_deleted = 0) AS child_count, ")
           .append("       (SELECT COUNT(*) FROM " + schema + ".kb_nav_article_rel rel ")
           .append("       JOIN " + schema + ".kb_nav_category sub ON sub.id = rel.node_id AND sub.is_deleted = 0 ")
           .append("       WHERE rel.is_deleted = 0 AND rel.scope = 'category' AND sub.path LIKE c.path || '%') AS article_count ")
           .append("FROM " + schema + ".kb_nav_category c ")
           .append("LEFT JOIN " + schema + ".kb_nav_category p ON p.id = c.parent_id AND p.is_deleted = 0 ")
           .append("WHERE c.is_deleted = 0 AND c.domain = ? ");
        List<Object> args = new ArrayList<>();
        args.add(domain);
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
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    /** 新建目录（uuid v4 / F04-16 归因 create_by / update_by 同 actor）。 */
    public void insertCategory(String domain, String parentIdOrNull, String name,
                               String path, int level, int sortOrder, String actor) {
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".kb_nav_category (id, domain, parent_id, name, path, level, sort_order, create_by, update_by) "
                        + "VALUES (gen_random_uuid()::text, ?, ?, ?, ?, ?, ?, ?, ?)",
                domain,
                parentIdOrNull,
                name,
                path,
                level,
                sortOrder,
                actor,
                actor);
    }

    /** 校验同 domain 同名是否已存在（排除给定 id）。 */
    public Integer countSameNameCategory(String domain, String name, String excludeId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".kb_nav_category WHERE domain = ? AND name = ? AND id <> ? AND is_deleted = 0",
                Integer.class, domain, name, excludeId);
    }

    /** 更新目录（name + sort_order，version_no 递增）。 */
    public void updateCategory(String name, int sortOrder, String actor, String id) {
        jdbcTemplate.update(
                "UPDATE " + schema + ".kb_nav_category SET name = ?, sort_order = ?, update_time = NOW(), "
                        + "update_by = ?, version_no = (CAST(version_no AS INTEGER) + 1)::text "
                        + "WHERE id = ? AND is_deleted = 0",
                name, sortOrder, actor, id);
    }

    /** 删除前置：子目录计数。 */
    public Integer countChildCategories(String parentId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".kb_nav_category WHERE parent_id = ? AND is_deleted = 0",
                Integer.class, parentId);
    }

    /** 软删目录（is_deleted=1）。 */
    public int softDeleteCategory(String actor, String id) {
        return jdbcTemplate.update(
                "UPDATE " + schema + ".kb_nav_category SET is_deleted = 1, update_time = NOW(), "
                        + "update_by = ? WHERE id = ? AND is_deleted = 0",
                actor, id);
    }

    /** 移动单个节点（parent/level/path 三字段联动）。 */
    public void moveCategory(String targetParentIdOrNull, int level, String path, String actor, String id) {
        jdbcTemplate.update(
                "UPDATE " + schema + ".kb_nav_category SET parent_id = ?, level = ?, path = ?, "
                        + "update_time = NOW(), update_by = ?, "
                        + "version_no = (CAST(version_no AS INTEGER) + 1)::text "
                        + "WHERE id = ? AND is_deleted = 0",
                targetParentIdOrNull, level, path, actor, id);
    }

    /** 按 path LIKE 统计关联文章数。 */
    public Integer countArticlesByPath(String pathPrefix) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".kb_nav_article_rel r "
                        + "JOIN " + schema + ".kb_nav_category c ON c.id = r.node_id AND c.is_deleted = 0 "
                        + "WHERE r.is_deleted = 0 AND r.scope = 'category' AND c.path LIKE ?",
                Integer.class, pathPrefix);
    }

    /** 按 path LIKE 统计关联 tag 去重数。 */
    public Integer countDistinctTagsByPath(String pathPrefix) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT t.tag_name) FROM " + schema + ".kb_nav_article_rel r "
                        + "JOIN " + schema + ".kb_nav_tag t ON t.id = r.node_id AND t.is_deleted = 0 "
                        + "JOIN " + schema + ".kb_nav_article_rel cr ON cr.article_id = r.article_id AND cr.scope = 'category' AND cr.is_deleted = 0 "
                        + "JOIN " + schema + ".kb_nav_category c ON c.id = cr.node_id AND c.is_deleted = 0 "
                        + "WHERE r.scope = 'tag' AND r.is_deleted = 0 AND c.path LIKE ?",
                Integer.class, pathPrefix);
    }

    /** 按 path LIKE 抽样关联 tag 名称（限 50）。 */
    public List<String> listTagNamesByPath(String pathPrefix) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT t.tag_name FROM " + schema + ".kb_nav_article_rel r "
                        + "JOIN " + schema + ".kb_nav_tag t ON t.id = r.node_id AND t.is_deleted = 0 "
                        + "JOIN " + schema + ".kb_nav_article_rel cr ON cr.article_id = r.article_id AND cr.scope = 'category' AND cr.is_deleted = 0 "
                        + "JOIN " + schema + ".kb_nav_category c ON c.id = cr.node_id AND c.is_deleted = 0 "
                        + "WHERE r.scope = 'tag' AND r.is_deleted = 0 AND c.path LIKE ? "
                        + "ORDER BY t.tag_name LIMIT 50",
                String.class, pathPrefix);
    }

    // ── 标签 CRUD ────────────────────────────────────────────────

    /** 标签列表（附 use_count）。 */
    public List<Map<String, Object>> listTags(String domain) {
        return jdbcTemplate.queryForList(
                "SELECT t.id, t.domain, t.tag_name, t.create_time, t.update_time, "
                        + "(SELECT COUNT(*) FROM " + schema + ".kb_nav_article_rel r "
                        + "   WHERE r.node_id = t.id AND r.scope = 'tag' AND r.is_deleted = 0) AS use_count "
                        + "FROM " + schema + ".kb_nav_tag t WHERE t.domain = ? AND t.is_deleted = 0 "
                        + "ORDER BY t.tag_name ASC", domain);
    }

    /** 新建 tag（uuid + actor 归因）。 */
    public void insertTag(String domain, String name, String actor) {
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".kb_nav_tag (id, domain, tag_name, create_by, update_by) "
                        + "VALUES (gen_random_uuid()::text, ?, ?, ?, ?)",
                domain, name, actor, actor);
    }

    /** 按 domain+tag_name 回读单行（Empty 聚合在 service 侧）。 */
    public Map<String, Object> fetchTagByName(String domain, String name) {
        return jdbcTemplate.queryForMap(
                "SELECT id, domain, tag_name, create_time, update_time FROM " + schema + ".kb_nav_tag WHERE domain = ? AND tag_name = ? AND is_deleted = 0",
                domain, name);
    }

    /** 软删 tag。 */
    public int softDeleteTag(String actor, String tagId) {
        return jdbcTemplate.update(
                "UPDATE " + schema + ".kb_nav_tag SET is_deleted = 1, update_time = NOW(), update_by = ? WHERE id = ? AND is_deleted = 0",
                actor, tagId);
    }

    // ── 文章 × (category/tag) 关联 ─────────────────────────────

    /** set 前置：清空该文章的 category 关联。 */
    public int clearArticleCategories(String actor, String articleId) {
        return jdbcTemplate.update(
                "UPDATE " + schema + ".kb_nav_article_rel SET is_deleted = 1, update_time = NOW(), update_by = ? "
                        + "WHERE article_id = ? AND scope = 'category' AND is_deleted = 0",
                actor, articleId);
    }

    /** set 前置：清空该文章的 tag 关联。 */
    public int clearArticleTags(String actor, String articleId) {
        return jdbcTemplate.update(
                "UPDATE " + schema + ".kb_nav_article_rel SET is_deleted = 1, update_time = NOW(), update_by = ? "
                        + "WHERE article_id = ? AND scope = 'tag' AND is_deleted = 0",
                actor, articleId);
    }

    /** set 新增单条 category 关联（不带限定，重复依赖调用方去重）。 */
    public int insertArticleCategory(String articleId, String categoryNodeId, String actor) {
        return jdbcTemplate.update(
                "INSERT INTO " + schema + ".kb_nav_article_rel (article_id, node_id, scope, create_by, update_by) "
                        + "VALUES (?, ?, 'category', ?, ?)",
                articleId, categoryNodeId, actor, actor);
    }

    /** set 新增单条 tag 关联。 */
    public int insertArticleTag(String articleId, String tagNodeId, String actor) {
        return jdbcTemplate.update(
                "INSERT INTO " + schema + ".kb_nav_article_rel (article_id, node_id, scope, create_by, update_by) "
                        + "VALUES (?, ?, 'tag', ?, ?)",
                articleId, tagNodeId, actor, actor);
    }

    /** add 幂等插入 category 关联（NOT EXISTS 卫）。 */
    public int addArticleCategoryIfAbsent(String articleId, String categoryNodeId, String actor) {
        return jdbcTemplate.update(
                "INSERT INTO " + schema + ".kb_nav_article_rel (article_id, node_id, scope, create_by, update_by) "
                        + "SELECT ?, ?, 'category', ?, ? "
                        + "WHERE NOT EXISTS (SELECT 1 FROM " + schema + ".kb_nav_article_rel r2 "
                        + "  WHERE r2.article_id = ? AND r2.node_id = ? AND r2.scope = 'category' AND r2.is_deleted = 0)",
                articleId, categoryNodeId, actor, actor, articleId, categoryNodeId);
    }

    /** add 幂等插入 tag 关联（NOT EXISTS 卫）。 */
    public int addArticleTagIfAbsent(String articleId, String tagNodeId, String actor) {
        return jdbcTemplate.update(
                "INSERT INTO " + schema + ".kb_nav_article_rel (article_id, node_id, scope, create_by, update_by) "
                        + "SELECT ?, ?, 'tag', ?, ? "
                        + "WHERE NOT EXISTS (SELECT 1 FROM " + schema + ".kb_nav_article_rel r2 "
                        + "  WHERE r2.article_id = ? AND r2.node_id = ? AND r2.scope = 'tag' AND r2.is_deleted = 0)",
                articleId, tagNodeId, actor, actor, articleId, tagNodeId);
    }

    /** remove 按 node id 软删 category 关联。 */
    public int removeArticleCategory(String actor, String articleId, String categoryNodeId) {
        return jdbcTemplate.update(
                "UPDATE " + schema + ".kb_nav_article_rel SET is_deleted = 1, update_time = NOW(), update_by = ? "
                        + "WHERE article_id = ? AND node_id = ? AND scope = 'category' AND is_deleted = 0",
                actor, articleId, categoryNodeId);
    }

    /** remove 按 tag_name 软删 tag 关联。 */
    public int removeArticleTagNamed(String actor, String tagNameTrimmed, String articleId) {
        return jdbcTemplate.update(
                "UPDATE " + schema + ".kb_nav_article_rel r SET is_deleted = 1, update_time = NOW(), update_by = ? "
                        + "FROM " + schema + ".kb_nav_tag t "
                        + "WHERE r.node_id = t.id AND t.tag_name = ? AND t.is_deleted = 0 "
                        + "AND r.article_id = ? AND r.scope = 'tag' AND r.is_deleted = 0",
                actor, tagNameTrimmed, articleId);
    }

    /** undo：按 scope 全软删该文章的匹配关联。 */
    public int undoArticle(String actor, String articleId, String scope) {
        return jdbcTemplate.update(
                "UPDATE " + schema + ".kb_nav_article_rel SET is_deleted = 1, update_time = NOW(), update_by = ? "
                        + "WHERE article_id = ? AND scope = ? AND is_deleted = 0",
                actor, articleId, scope);
    }

    // ── 资产列表 ────────────────────────────────────────────────

    /** 资产分页查询（WHERE 由调用方组装 + args 位序对齐）。 */
    public List<Map<String, Object>> listProducts(String whereClause, Object[] args, int pageSize, int offset) {
        String sql = "SELECT a.id, a.title, a.source, a.domain, a.category, a.status, a.updated_at "
                + "FROM " + schema + ".knowledge_article a "
                + "WHERE " + whereClause + " "
                + "ORDER BY a.updated_at DESC "
                + "LIMIT " + pageSize + " OFFSET " + offset;
        return jdbcTemplate.queryForList(sql, args);
    }

    /** 资产计数（同 WHERE）。 */
    public Long countProducts(String whereClause, Object[] args) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + schema + ".knowledge_article a WHERE " + whereClause,
                Long.class, args);
    }

    // ── LLM 建议 / 域清单 ───────────────────────────────────────

    /** 取 recommend 所需的文章 title / content / domain（Empty 由 service 侧捕获）。 */
    public Map<String, Object> readArticleForRecommend(String articleId) {
        return jdbcTemplate.queryForMap(
                "SELECT id, title, content, domain FROM " + schema + ".knowledge_article WHERE id = ?",
                articleId);
    }

    /** distinct 目录 domain UNION distinct 标签 domain（升序去 duplicate）。 */
    public List<Map<String, Object>> listDomains() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT domain FROM " + schema + ".kb_nav_category WHERE is_deleted = 0 "
                        + "UNION "
                        + "SELECT DISTINCT domain FROM " + schema + ".kb_nav_tag WHERE is_deleted = 0 "
                        + "ORDER BY 1 ASC");
    }

    // ── 私有 helpers 对应落点 ────────────────────────────────

    /** 取目录 level（用于层级越界校验；空 = 上层目录不存在）。 */
    public Integer fetchCategoryLevel(String id) {
        return jdbcTemplate.queryForObject(
                "SELECT level FROM " + schema + ".kb_nav_category WHERE id = ? AND is_deleted = 0",
                Integer.class, id);
    }

    /** 节点（含自身）最大深度，用于移动越界校验（CTE 递归）。 */
    public Integer fetchDescDepth(String rootId) {
        return jdbcTemplate.queryForObject(
                "WITH RECURSIVE walk AS ("
                        + "  SELECT id AS self_id, 1 AS d FROM " + schema + ".kb_nav_category "
                        + "   WHERE id = ? AND is_deleted = 0 "
                        + "  UNION ALL "
                        + "  SELECT c.id, w.d + 1 FROM " + schema + ".kb_nav_category c "
                        + "  JOIN walk w ON c.parent_id = w.self_id WHERE c.is_deleted = 0"
                        + ") SELECT MAX(d) FROM walk",
                Integer.class, rootId);
    }

    /** 祖先链（含自身，CTE 递归）— 循环检测。 */
    public List<String> fetchSelfAndAncestors(String rootId) {
        return jdbcTemplate.queryForList(
                "WITH RECURSIVE up AS ("
                        + "  SELECT id FROM " + schema + ".kb_nav_category WHERE id = ? AND is_deleted = 0 "
                        + "  UNION ALL "
                        + "  SELECT c.id FROM " + schema + ".kb_nav_category c "
                        + "  JOIN up u ON c.id = ("
                        + "     SELECT p2.parent_id::text FROM " + schema + ".kb_nav_category p2 WHERE p2.id = u.id"
                        + "  ) WHERE c.is_deleted = 0"
                        + ") SELECT id FROM up",
                String.class, rootId);
    }

    /** 全量 alive 目录（id / parent_id / name）— path 重建拓扑用。 */
    public List<Map<String, Object>> fetchAllAliveCategories() {
        return jdbcTemplate.queryForList(
                "SELECT id, parent_id, name FROM " + schema + ".kb_nav_category WHERE is_deleted = 0");
    }

    /** 单点重写子节点 path（refreshDescendantPath 循环内）。 */
    public void updateCategoryPath(String path, String id) {
        jdbcTemplate.update("UPDATE " + schema + ".kb_nav_category SET path = ? WHERE id = ?",
                path, id);
    }

    /** 按 name+domain 反查 tag id（normalizeTagIds 用；Empty 由 service 侧捕获）。 */
    public Map<String, Object> fetchTagIdByName(String nameTrimmed, String domain) {
        return jdbcTemplate.queryForMap(
                "SELECT id FROM " + schema + ".kb_nav_tag WHERE tag_name = ? AND domain = ? AND is_deleted = 0",
                nameTrimmed, domain);
    }

    /** 文章存在性检查。 */
    public Integer countArticle(String articleId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".knowledge_article WHERE id = ?",
                Integer.class, articleId);
    }

    /** fetchCategory 精确 id 行（Empty 由 service 侧捕获）。 */
    public Map<String, Object> fetchCategoryRow(String id) {
        return jdbcTemplate.queryForMap(
                "SELECT id, domain, parent_id, name, path, level, sort_order, create_time, update_time "
                        + "FROM " + schema + ".kb_nav_category WHERE id = ? AND is_deleted = 0", id);
    }

    /** fetchTag 精确 id 行（Empty 由 service 侧捕获）。 */
    public Map<String, Object> fetchTagRow(String tagId) {
        return jdbcTemplate.queryForMap(
                "SELECT id, domain, tag_name, create_time, update_time "
                        + "FROM " + schema + ".kb_nav_tag WHERE id = ? AND is_deleted = 0", tagId);
    }

    /** findByIdRequired 按 domain+name 反查（Empty 由 service 侧抛 NotFound）。 */
    public Map<String, Object> fetchCategoryByNameRequired(String domain, String name) {
        return jdbcTemplate.queryForMap(
                "SELECT id, domain, parent_id, name, path, level, sort_order, create_time, update_time "
                        + "FROM " + schema + ".kb_nav_category WHERE domain = ? AND name = ? AND is_deleted = 0",
                domain, name);
    }
}
