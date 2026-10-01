package com.chinacreator.gzcm.engine.ontology.dao;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.engine.ontology.glossary.GlossaryEntity;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * B3c 数据访问收口（R2/B3c）：词条（Wiki）CRUD，对应 {@code ecos_glossary_term} 表。
 *
 * <p>显式列出查询列（禁 {@code SELECT *}）；{@code aliases / examples / tags} 三列为 PG
 * {@code TEXT[]}，写入以数组字面量 + {@code ?::text[]} 显式转换，读取经 {@link Array} 还原。
 *
 * <p>PMO-30 P1-1 多租户 RLS（仓库内手工处理）：{@link TenantContextHolder}.getTenantId()
 * 打标/过滤全部原样搬运到本 DAO 方法（NULL 兼容共享可见）。</p>
 */
@Repository
public class GlossaryDao extends JdbcTemplateDaoSupport {

    private static final String COLUMNS =
        "id, code, name, definition, domain, owner, status, created_by, created_at, updated_at, "
        + "term_type, aliases, object_type_id, parent_term_id, version, examples, tags, is_primary";

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

    public List<GlossaryEntity> findAll(String domain, String status,
                                        String termType, String objectTypeId, String keyword) {
        StringBuilder sql = new StringBuilder(
            "SELECT " + COLUMNS + " FROM ecos_glossary_term WHERE 1=1");
        List<Object> params = new ArrayList<>();
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
            sql.append(" AND (name ILIKE ? OR code ILIKE ? OR array_to_string(aliases, ',') ILIKE ?)");
            String like = "%" + keyword + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY created_at DESC, id DESC");

        return jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
    }

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
        List<GlossaryEntity> list = jdbcTemplate.query(sql, rowMapper, args);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

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
        List<GlossaryEntity> list = jdbcTemplate.query(sql, rowMapper, args);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

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
        return jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
    }

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
        Long id = jdbcTemplate.queryForObject(sql, Long.class, params.toArray());
        entity.setId(id);
        return id == null ? 0 : 1;
    }

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
        return jdbcTemplate.update(sql, params.toArray());
    }

    public int deleteById(Long id) {
        String tid = currentTenantId();
        if (tid == null || tid.isBlank()) {
            return jdbcTemplate.update("DELETE FROM ecos_glossary_term WHERE id = ?", id);
        }
        return jdbcTemplate.update(
            "DELETE FROM ecos_glossary_term WHERE id = ? AND (tenant_id = ? OR tenant_id IS NULL)", id, tid);
    }

    public int updateBinding(Long id, String objectTypeId, boolean primary) {
        String tid = currentTenantId();
        String where = (tid == null || tid.isBlank())
            ? "WHERE id = ?"
            : "WHERE id = ? AND (tenant_id = ? OR tenant_id IS NULL)";
        String sql = "UPDATE ecos_glossary_term SET object_type_id = ?, is_primary = ?, updated_at = NOW() "
            + where;
        if (tid == null || tid.isBlank()) {
            return jdbcTemplate.update(sql, objectTypeId, primary, id);
        }
        return jdbcTemplate.update(sql, objectTypeId, primary, id, tid);
    }

    public int clearPrimaryForEntity(String objectTypeId, Long exceptId) {
        return jdbcTemplate.update(
            "UPDATE ecos_glossary_term SET is_primary = FALSE, updated_at = NOW() "
                + "WHERE object_type_id = ? AND is_primary AND id <> ?",
            objectTypeId, exceptId);
    }

    public long count() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_glossary_term", Long.class);
    }

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
