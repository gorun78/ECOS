package com.chinacreator.gzcm.engine.ontology.dao;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.engine.ontology.model.OntologyDomain;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * B3c 数据访问收口（R2/B3c）：领域 CRUD（{@code ecos_ontology_domain} 表）。
 *
 * <p>PMO-30 P1-1 多租户 RLS 全部原样搬运：SELECT 加 {@code (tenant_id = ? OR tenant_id IS NULL)}
 * 共享可见；INSERT 从 {@link TenantContextHolder} 打标；UPDATE 不改 tenant_id；
 * DELETE 限本租户或共享行。</p>
 */
@Repository
public class OntologyDomainDao extends JdbcTemplateDaoSupport {

    private final RowMapper<OntologyDomain> ROW_MAPPER = (rs, rn) -> {
        OntologyDomain d = new OntologyDomain();
        d.setId(rs.getString("id"));
        d.setCode(rs.getString("code"));
        d.setName(rs.getString("name"));
        d.setOwner(rs.getString("owner"));
        d.setDescription(rs.getString("description"));
        d.setStatus(rs.getString("status"));
        d.setSortOrder(rs.getInt("sort_order"));
        d.setCreatedAt(ts(rs.getTimestamp("create_time")));
        d.setUpdatedAt(ts(rs.getTimestamp("update_time")));
        return d;
    };

    private static LocalDateTime ts(Timestamp t) {
        return t != null ? t.toLocalDateTime() : null;
    }

    private static String currentTenantId() {
        return TenantContextHolder.getTenantId();
    }

    public List<OntologyDomain> findAll() {
        String base = "SELECT d.* FROM ecos_ontology_domain d";
        String tid = currentTenantId();
        if (tid == null || tid.isBlank()) {
            return jdbcTemplate.query(base + " ORDER BY d.sort_order, d.create_time DESC", ROW_MAPPER);
        }
        return jdbcTemplate.query(
            base + " WHERE (d.tenant_id = ? OR d.tenant_id IS NULL) ORDER BY d.sort_order, d.create_time DESC",
            ROW_MAPPER, tid);
    }

    public Optional<OntologyDomain> findByCode(String code) {
        String tid = currentTenantId();
        String sql;
        Object[] args;
        if (tid == null || tid.isBlank()) {
            sql = "SELECT * FROM ecos_ontology_domain WHERE code = ?";
            args = new Object[]{code};
        } else {
            sql = "SELECT * FROM ecos_ontology_domain WHERE code = ? AND (tenant_id = ? OR tenant_id IS NULL)";
            args = new Object[]{code, tid};
        }
        List<OntologyDomain> list = jdbcTemplate.query(sql, ROW_MAPPER, args);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<OntologyDomain> findById(String id) {
        String tid = currentTenantId();
        String sql;
        Object[] args;
        if (tid == null || tid.isBlank()) {
            sql = "SELECT * FROM ecos_ontology_domain WHERE id = ?";
            args = new Object[]{id};
        } else {
            sql = "SELECT * FROM ecos_ontology_domain WHERE id = ? AND (tenant_id = ? OR tenant_id IS NULL)";
            args = new Object[]{id, tid};
        }
        List<OntologyDomain> list = jdbcTemplate.query(sql, ROW_MAPPER, args);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public boolean existsByCode(String code) {
        String tid = currentTenantId();
        Long count;
        if (tid == null || tid.isBlank()) {
            count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_ontology_domain WHERE code = ?", Long.class, code);
        } else {
            count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ecos_ontology_domain WHERE code = ? AND (tenant_id = ? OR tenant_id IS NULL)",
                Long.class, code, tid);
        }
        return count != null && count > 0;
    }

    public int insert(OntologyDomain domain) {
        String tid = currentTenantId();
        return jdbcTemplate.update("""
            INSERT INTO ecos_ontology_domain (id, code, name, owner, description, status, sort_order, tenant_id, create_time, update_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """, domain.getId(), domain.getCode(), domain.getName(),
            domain.getOwner(), domain.getDescription(), domain.getStatus(), domain.getSortOrder(), tid);
    }

    public int update(String id, String code, String name, String owner, String description, String status) {
        String tid = currentTenantId();
        String sql;
        Object[] args;
        if (tid == null || tid.isBlank()) {
            sql = """
                UPDATE ecos_ontology_domain SET
                    code = COALESCE(?, code),
                    name = COALESCE(?, name),
                    owner = COALESCE(?, owner),
                    description = COALESCE(?, description),
                    status = COALESCE(?, status),
                    update_time = NOW()
                WHERE id = ?
                """;
            args = new Object[]{code, name, owner, description, status, id};
        } else {
            sql = """
                UPDATE ecos_ontology_domain SET
                    code = COALESCE(?, code),
                    name = COALESCE(?, name),
                    owner = COALESCE(?, owner),
                    description = COALESCE(?, description),
                    status = COALESCE(?, status),
                    update_time = NOW()
                WHERE id = ? AND (tenant_id = ? OR tenant_id IS NULL)
                """;
            args = new Object[]{code, name, owner, description, status, id, tid};
        }
        return jdbcTemplate.update(sql, args);
    }

    public int delete(String id) {
        String tid = currentTenantId();
        String sql;
        Object[] args;
        if (tid == null || tid.isBlank()) {
            sql = "DELETE FROM ecos_ontology_domain WHERE id = ?";
            args = new Object[]{id};
        } else {
            sql = "DELETE FROM ecos_ontology_domain WHERE id = ? AND (tenant_id = ? OR tenant_id IS NULL)";
            args = new Object[]{id, tid};
        }
        return jdbcTemplate.update(sql, args);
    }

    public int countEntitiesByDomain(String domainCode) {
        String tid = currentTenantId();
        String sql;
        Object[] args;
        if (tid == null || tid.isBlank()) {
            sql = "SELECT COUNT(*) FROM ecos_ontology_entity e " +
                  "INNER JOIN ecos_ontology_domain d ON e.domain_id = d.id " +
                  "WHERE d.code = ?";
            args = new Object[]{domainCode};
        } else {
            sql = "SELECT COUNT(*) FROM ecos_ontology_entity e " +
                  "INNER JOIN ecos_ontology_domain d ON e.domain_id = d.id " +
                  "WHERE d.code = ? AND (e.tenant_id = ? OR e.tenant_id IS NULL)";
            args = new Object[]{domainCode, tid};
        }
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        return count != null ? count.intValue() : 0;
    }

    public List<OntologyDomain> searchDomains(String keyword, int limit) {
        String tid = currentTenantId();
        StringBuilder sql = new StringBuilder("SELECT d.* FROM ecos_ontology_domain d");
        List<Object> params = new ArrayList<>();

        boolean hasCond = false;
        if (tid != null && !tid.isBlank()) {
            sql.append(hasCond ? " AND " : " WHERE ");
            sql.append("(d.tenant_id = ? OR d.tenant_id IS NULL)");
            params.add(tid);
            hasCond = true;
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(hasCond ? " AND " : " WHERE ");
            sql.append("(d.name ILIKE ? OR d.code ILIKE ? OR d.description ILIKE ?)");
            String like = "%" + keyword + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            hasCond = true;
        }
        sql.append(" ORDER BY d.sort_order, d.create_time DESC LIMIT ?");
        params.add(Math.max(1, Math.min(limit, 200)));

        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }
}
