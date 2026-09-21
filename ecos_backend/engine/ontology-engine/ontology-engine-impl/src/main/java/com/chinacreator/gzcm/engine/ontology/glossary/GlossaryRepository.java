package com.chinacreator.gzcm.engine.ontology.glossary;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import com.chinacreator.gzcm.common.context.TenantContextHolder;

/**
 * JdbcTemplate 仓库 — 词条（Wiki）CRUD，对应 ecos_glossary_term 表。
 *
 * <p>显式列出查询列（禁止 {@code SELECT *}）；{@code aliases / examples / tags}
 * 三列为 PG 的 {@code TEXT[]}，写入时以数组字面量 + {@code ?::text[]} 显式转换，
 * 读取时经 {@link Array} 还原为 {@code List<String>}。
 *
 * <p>PMO-30 P1-1 多租户 RLS（仓库内手工处理，与 {@code OntologyDomainRepository} 同模式）：
 * {@code TenantAwareJdbcTemplate} 对 SQL 一律追加 {@code WHERE tenant_id = ?}，
 * 而 INSERT 追加 WHERE 属语法非法，故本仓库不再经自动重写，改为仓库内显式处理：
 * <ul>
 *   <li>SELECT 加 {@code AND (tenant_id = ? OR tenant_id IS NULL)} — NULL 行共享可见</li>
 *   <li>INSERT 从 {@link TenantContextHolder} 打 tenantId 标签（NULL 兼容旧数据）</li>
 *   <li>UPDATE 不修改 tenant_id（数据归属不漂移），WHERE 带 {@code (tenant_id = ? OR tenant_id IS NULL)}</li>
 *   <li>DELETE 限本租户行或共享行（{@code tenant_id IS NULL}），不跨租户删他租户数据</li>
 * </ul>
 */
@Repository
public class GlossaryRepository {

    /** 显式列清单（新增字段只需在此追加） */
    private static final String COLUMNS =
        "id, code, name, definition, domain, owner, status, created_by, created_at, updated_at, "
        + "term_type, aliases, object_type_id, parent_term_id, version, examples, tags, is_primary";

    private final JdbcTemplate jdbc;

    public GlossaryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ═══ PMO-30 P1-1 辅助：取当前租户（admin / TenantContextHolder 为空时返回 null）═══
    /** 当前请求的 tenantId，无租户上下文时返回 null（旧数据兼容共享可见） */
    private static String currentTenantId() {
        return TenantContextHolder.getTenantId();
    }

    private final RowMapper<GlossaryEntity> rowMapper = (rs, rowNum) -> {
        GlossaryEntity e = new GlossaryEntity();
        e.setId(rs.getLong("id"));
        e.setCode(rs.getString("code"));
        e.setName(rs.getString("name"));
        e.setDefinition(rs.getString("definition"));
        e.setDomain(rs.getString("domain"));
        e.setOwner(rs.getString("owner"));
        e.setStatus(rs.getString("status"));
        e.setCreatedBy(rs.getString("created_by"));
        e.setCreatedAt(rs.getTimestamp("created_at") != null
            ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        e.setUpdatedAt(rs.getTimestamp("updated_at") != null
            ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
        e.setTermType(rs.getString("term_type"));
        e.setAliases(readTextArray(rs.getArray("aliases")));
        e.setObjectTypeId(rs.getString("object_type_id"));
        long parentId = rs.getLong("parent_term_id");
        e.setParentTermId(rs.wasNull() ? null : parentId);
        int version = rs.getInt("version");
        e.setVersion(rs.wasNull() ? null : version);
        e.setExamples(readTextArray(rs.getArray("examples")));
        e.setTags(readTextArray(rs.getArray("tags")));
        e.setIsPrimary(rs.getBoolean("is_primary"));
        return e;
    };

    /**
     * 按条件查询词条列表。
     *
     * @param domain       领域字典 code，可空
     * @param status       状态，可空
     * @param termType     词条分类，可空
     * @param objectTypeId 关联本体实体主键，可空
     * @param keyword      名称 / 编码 / 别名 模糊匹配关键字，可空
     */
    public List<GlossaryEntity> findAll(String domain, String status,
                                        String termType, String objectTypeId, String keyword) {
        StringBuilder sql = new StringBuilder(
            "SELECT " + COLUMNS + " FROM ecos_glossary_term WHERE 1=1");
        List<Object> params = new ArrayList<>();
        // PMO-30 P1-1：有租户上下文时限制为本租户或共享行（tenant_id IS NULL）
        String tid = currentTenantId();
        if (tid != null && !tid.isBlank()) {
            sql.append(" AND (tenant_id = ? OR tenant_id IS NULL)");
            params.add(tid);
        }
        if (domain != null && !domain.isBlank()) {
            sql.append(" AND domain = ?");
            params.add(domain);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        if (termType != null && !termType.isBlank()) {
            sql.append(" AND term_type = ?");
            params.add(termType);
        }
        if (objectTypeId != null && !objectTypeId.isBlank()) {
            sql.append(" AND object_type_id = ?");
            params.add(objectTypeId);
        }
        if (keyword != null && !keyword.isBlank()) {
            // 名称 / 编码 / 别名数组 三处模糊匹配
            sql.append(" AND (name ILIKE ? OR code ILIKE ? OR array_to_string(aliases, ',') ILIKE ?)");
            String like = "%" + keyword + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY created_at DESC, id DESC");

        return jdbc.query(sql.toString(), rowMapper, params.toArray());
    }

    /** 按主键查词条。 */
    public Optional<GlossaryEntity> findById(Long id) {
        String tid = currentTenantId();
        String sql;
        Object[] args;
        if (tid == null || tid.isBlank()) {
            sql = "SELECT " + COLUMNS + " FROM ecos_glossary_term WHERE id = ?";
            args = new Object[]{id};
        } else {
            sql = "SELECT " + COLUMNS + " FROM ecos_glossary_term"
                + " WHERE id = ? AND (tenant_id = ? OR tenant_id IS NULL)";
            args = new Object[]{id, tid};
        }
        List<GlossaryEntity> list = jdbc.query(sql, rowMapper, args);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** 按编码查词条（编码为演示 / 导入场景的稳定业务键）。 */
    public Optional<GlossaryEntity> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String tid = currentTenantId();
        String sql;
        Object[] args;
        if (tid == null || tid.isBlank()) {
            sql = "SELECT " + COLUMNS + " FROM ecos_glossary_term WHERE code = ?";
            args = new Object[]{code};
        } else {
            sql = "SELECT " + COLUMNS + " FROM ecos_glossary_term"
                + " WHERE code = ? AND (tenant_id = ? OR tenant_id IS NULL)";
            args = new Object[]{code, tid};
        }
        List<GlossaryEntity> list = jdbc.query(sql, rowMapper, args);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** 按主键集合批量查词条（图谱组装用）。 */
    public List<GlossaryEntity> findAllByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String tid = currentTenantId();
        List<Object> params = new ArrayList<>(ids);
        StringBuilder sql = new StringBuilder(
            "SELECT " + COLUMNS + " FROM ecos_glossary_term WHERE id IN (" + placeholders + ")");
        if (tid != null && !tid.isBlank()) {
            sql.append(" AND (tenant_id = ? OR tenant_id IS NULL)");
            params.add(tid);
        }
        return jdbc.query(sql.toString(), rowMapper, params.toArray());
    }

    /**
     * 新增词条（PMO-30 P1-1：写入时打当前租户标签，无上下文则 NULL）。
     *
     * <p>以 {@code RETURNING id} 回填自增主键到入参实体 —— 否则调用方拿不到新词条 id，
     * 创建后的回读与前端定位均会落空。
     */
    public int insert(GlossaryEntity entity) {
        String sql = """
            INSERT INTO ecos_glossary_term
                (code, name, definition, domain, owner, status, created_by,
                 term_type, aliases, object_type_id, parent_term_id, version, examples, tags,
                 is_primary, tenant_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::text[], ?, ?, ?, ?::text[], ?::text[], FALSE, ?, NOW(), NOW())
            RETURNING id
            """;
        List<Object> params = new ArrayList<>();
        Collections.addAll(params,
            entity.getCode(), entity.getName(), entity.getDefinition(),
            entity.getDomain(), entity.getOwner(), entity.getStatus(), entity.getCreatedBy(),
            entity.getTermType(), toArrayLiteral(entity.getAliases()), entity.getObjectTypeId(),
            entity.getParentTermId(), entity.getVersion() == null ? 1 : entity.getVersion(),
            toArrayLiteral(entity.getExamples()), toArrayLiteral(entity.getTags()),
            currentTenantId());
        Long id = jdbc.queryForObject(sql, Long.class, params.toArray());
        entity.setId(id);
        return id == null ? 0 : 1;
    }

    /**
     * 更新词条（各字段 null 表不动，数组字段亦然）。
     *
     * <p>PMO-30 P1-1：不修改 tenant_id（数据归属不漂移）；WHERE 限制为本租户行或共享行
     * （tenant_id IS NULL 兼容共享可见）。
     */
    public int update(GlossaryEntity entity) {
        String tid = currentTenantId();
        String where = (tid == null || tid.isBlank())
            ? "WHERE id = ?"
            : "WHERE id = ? AND (tenant_id = ? OR tenant_id IS NULL)";
        String sql = """
            UPDATE ecos_glossary_term SET
                code           = COALESCE(?, code),
                name           = COALESCE(?, name),
                definition     = COALESCE(?, definition),
                domain         = COALESCE(?, domain),
                owner          = COALESCE(?, owner),
                status         = COALESCE(?, status),
                created_by     = COALESCE(?, created_by),
                term_type      = COALESCE(?, term_type),
                aliases        = COALESCE(?::text[], aliases),
                object_type_id = COALESCE(?, object_type_id),
                parent_term_id = COALESCE(?, parent_term_id),
                version        = COALESCE(?, version),
                examples       = COALESCE(?::text[], examples),
                tags           = COALESCE(?::text[], tags),
                updated_at     = NOW()
            """ + where;
        List<Object> params = new ArrayList<>();
        Collections.addAll(params,
            entity.getCode(), entity.getName(), entity.getDefinition(),
            entity.getDomain(), entity.getOwner(), entity.getStatus(), entity.getCreatedBy(),
            entity.getTermType(), toArrayLiteral(entity.getAliases()), entity.getObjectTypeId(),
            entity.getParentTermId(), entity.getVersion(),
            toArrayLiteral(entity.getExamples()), toArrayLiteral(entity.getTags()),
            entity.getId());
        if (tid != null && !tid.isBlank()) {
            params.add(tid);
        }
        return jdbc.update(sql, params.toArray());
    }

    /**
     * 物理删除词条（关联关系边由外键 ON DELETE CASCADE 一并清理）。
     *
     * <p>PMO-30 P1-1：WHERE 限制为本租户行或共享行（tenant_id IS NULL），
     * 与 {@code OntologyDomainRepository.delete} 同口径，避免跨租户误删他租户数据。
     */
    public int deleteById(Long id) {
        String tid = currentTenantId();
        if (tid == null || tid.isBlank()) {
            return jdbc.update("DELETE FROM ecos_glossary_term WHERE id = ?", id);
        }
        return jdbc.update(
            "DELETE FROM ecos_glossary_term WHERE id = ? AND (tenant_id = ? OR tenant_id IS NULL)", id, tid);
    }

    /**
     * 更新词条 ↔ 本体实体 绑定（T1 本体消费词条）。
     *
     * <p>{@code objectTypeId} 为 null 即解绑（同时撤下主术语标记）。
     * 通用编辑 {@link #update} 有意不触碰 {@code object_type_id / is_primary}，
     * 使绑定关系只经本方法变更（单一写入方）。
     */
    public int updateBinding(Long id, String objectTypeId, boolean primary) {
        String tid = currentTenantId();
        String where = (tid == null || tid.isBlank())
            ? "WHERE id = ?"
            : "WHERE id = ? AND (tenant_id = ? OR tenant_id IS NULL)";
        String sql = "UPDATE ecos_glossary_term SET object_type_id = ?, is_primary = ?, updated_at = NOW() "
            + where;
        if (tid == null || tid.isBlank()) {
            return jdbc.update(sql, objectTypeId, primary, id);
        }
        return jdbc.update(sql, objectTypeId, primary, id, tid);
    }

    /**
     * 撤下该实体下（除 {@code exceptId} 外）的主术语标记。
     *
     * <p>刻意不加租户条件：{@code uniq_glossary_term_primary} 是全局部分唯一索引，
     * 主术语归属属语义层事实（跨租户共享本体），若只清本租户行会导致他租户主术语
     * 残留并触发唯一约束冲突。
     */
    public int clearPrimaryForEntity(String objectTypeId, Long exceptId) {
        return jdbc.update(
            "UPDATE ecos_glossary_term SET is_primary = FALSE, updated_at = NOW() "
                + "WHERE object_type_id = ? AND is_primary AND id <> ?",
            objectTypeId, exceptId);
    }

    /** 词条总数。 */
    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ecos_glossary_term", Long.class);
    }

    // ═══════════════ TEXT[] 读写辅助 ═══════════════

    /** PG TEXT[] → List&lt;String&gt;（null / 空 视为空列表）。 */
    private static List<String> readTextArray(Array array) throws SQLException {
        if (array == null) {
            return Collections.emptyList();
        }
        Object raw = array.getArray();
        if (raw instanceof Object[] objects) {
            List<String> result = new ArrayList<>(objects.length);
            for (Object o : objects) {
                result.add(o == null ? null : String.valueOf(o));
            }
            return result;
        }
        return Collections.emptyList();
    }

    /**
     * List&lt;String&gt; → PG 数组字面量（{@code {"a","b"}}）。
     *
     * <p>null 返回 null（配合 COALESCE 实现「不传即不改」）；空列表返回 {@code {}}，
     * 即显式清空数组。元素内的 {@code \} 与 {@code "} 按 PG 数组语法转义。
     */
    private static String toArrayLiteral(List<String> values) {
        if (values == null) {
            return null;
        }
        if (values.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            String v = values.get(i);
            if (v == null) {
                sb.append("NULL");
                continue;
            }
            sb.append('"')
              .append(v.replace("\\", "\\\\").replace("\"", "\\\""))
              .append('"');
        }
        return sb.append('}').toString();
    }
}