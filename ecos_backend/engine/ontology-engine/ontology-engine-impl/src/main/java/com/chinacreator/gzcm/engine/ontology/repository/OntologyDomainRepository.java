package com.chinacreator.gzcm.engine.ontology.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.engine.ontology.model.OntologyDomain;

/**
 * JdbcTemplate 仓库 — 领域 CRUD（ecos_domain 表）
 *
 * <p>PMO-30 P1-1 多租户 RLS：查询带 tenant_id 过滤（兼容 NULL 行共享可见），
 * 写操作自动打 tenantId 标签。</p>
 *
 * <p>租户隔离策略：
 * <ul>
 *   <li>SELECT 自动加 {@code AND (tenant_id = ? OR tenant_id IS NULL)} — NULL 行共享可见</li>
 *   <li>INSERT 从 {@link TenantContextHolder} 打 tenantId 标签（NULL 兼容旧数据)</li>
 *   <li>UPDATE 不修改 tenant_id（数据归属不漂移）</li>
 *   <li>DELETE 仅删除当前租户的行（NULL 行不删）</li>
 * </ul></p>
 */
@Repository
public class OntologyDomainRepository {

    private final JdbcTemplate jdbc;

    public OntologyDomainRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<OntologyDomain> ROW_MAPPER = (rs, rn) -> {
        OntologyDomain d = new OntologyDomain();
        d.setId(rs.getString("id"));
        d.setCode(rs.getString("code"));
        d.setName(rs.getString("name"));
        d.setOwner(rs.getString("owner"));
        d.setDescription(rs.getString("description"));
        d.setStatus(rs.getString("status"));
        d.setSortOrder(rs.getInt("sort_order"));
        d.setCreatedAt(ts(rs.getTimestamp("created_at")));
        d.setUpdatedAt(ts(rs.getTimestamp("updated_at")));
        return d;
    };

    private static LocalDateTime ts(Timestamp t) {
        return t != null ? t.toLocalDateTime() : null;
    }

    // ═══ PMO-30 P1-1 辅助: 取当前租户（admin / TenantContextHolder 为空时返回 null）═
    /** 当前请求的 tenantId，无租户上下文时返回 null（旧数据兼容共享可见） */
    private static String currentTenantId() {
        return TenantContextHolder.getTenantId();
    }

    public List<OntologyDomain> findAll() {
        String base = "SELECT d.* FROM ecos_domain d";
        String tid = currentTenantId();
        if (tid == null || tid.isBlank()) {
            return jdbc.query(base + " ORDER BY d.sort_order, d.created_at DESC", ROW_MAPPER);
        }
        return jdbc.query(
            base + " WHERE (d.tenant_id = ? OR d.tenant_id IS NULL) ORDER BY d.sort_order, d.created_at DESC",
            ROW_MAPPER, tid);
    }

    public Optional<OntologyDomain> findByCode(String code) {
        String tid = currentTenantId();
        String sql;
        Object[] args;
        if (tid == null || tid.isBlank()) {
            sql = "SELECT * FROM ecos_domain WHERE code = ?";
            args = new Object[]{code};
        } else {
            sql = "SELECT * FROM ecos_domain WHERE code = ? AND (tenant_id = ? OR tenant_id IS NULL)";
            args = new Object[]{code, tid};
        }
        List<OntologyDomain> list = jdbc.query(sql, ROW_MAPPER, args);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<OntologyDomain> findById(String id) {
        String tid = currentTenantId();
        String sql;
        Object[] args;
        if (tid == null || tid.isBlank()) {
            sql = "SELECT * FROM ecos_domain WHERE id = ?";
            args = new Object[]{id};
        } else {
            sql = "SELECT * FROM ecos_domain WHERE id = ? AND (tenant_id = ? OR tenant_id IS NULL)";
            args = new Object[]{id, tid};
        }
        List<OntologyDomain> list = jdbc.query(sql, ROW_MAPPER, args);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public boolean existsByCode(String code) {
        String tid = currentTenantId();
        Long count;
        if (tid == null || tid.isBlank()) {
            count = jdbc.queryForObject("SELECT COUNT(*) FROM ecos_domain WHERE code = ?", Long.class, code);
        } else {
            count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ecos_domain WHERE code = ? AND (tenant_id = ? OR tenant_id IS NULL)",
                Long.class, code, tid);
        }
        return count != null && count > 0;
    }

    public int insert(OntologyDomain domain) {
        // 写操作：从 TenantContextHolder 打标
        String tid = currentTenantId();
        return jdbc.update("""
            INSERT INTO ecos_domain (id, code, name, owner, description, status, sort_order, tenant_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """, domain.getId(), domain.getCode(), domain.getName(),
            domain.getOwner(), domain.getDescription(), domain.getStatus(), domain.getSortOrder(), tid);
    }

    public int update(String id, String code, String name, String owner, String description, String status) {
        // UPDATE 不修改 tenant_id（数据归属不漂移）
        // 仅允许当前租户修改本租户行（NULL 行兼容共享可见，可被任何租户修改）
        String tid = currentTenantId();
        String sql;
        Object[] args;
        if (tid == null || tid.isBlank()) {
            sql = """
                UPDATE ecos_domain SET
                    code = COALESCE(?, code),
                    name = COALESCE(?, name),
                    owner = COALESCE(?, owner),
                    description = COALESCE(?, description),
                    status = COALESCE(?, status),
                    updated_at = NOW()
                WHERE id = ?
                """;
            args = new Object[]{code, name, owner, description, status, id};
        } else {
            sql = """
                UPDATE ecos_domain SET
                    code = COALESCE(?, code),
                    name = COALESCE(?, name),
                    owner = COALESCE(?, owner),
                    description = COALESCE(?, description),
                    status = COALESCE(?, status),
                    updated_at = NOW()
                WHERE id = ? AND (tenant_id = ? OR tenant_id IS NULL)
                """;
            args = new Object[]{code, name, owner, description, status, id, tid};
        }
        return jdbc.update(sql, args);
    }

    public int delete(String id) {
        // 仅删除当前租户数据（NULL 行兼容数据保持，避免误删共享数据）
        String tid = currentTenantId();
        String sql;
        Object[] args;
        if (tid == null || tid.isBlank()) {
            sql = "DELETE FROM ecos_domain WHERE id = ?";
            args = new Object[]{id};
        } else {
            sql = "DELETE FROM ecos_domain WHERE id = ? AND (tenant_id = ? OR tenant_id IS NULL)";
            args = new Object[]{id, tid};
        }
        return jdbc.update(sql, args);
    }

    public int countEntitiesByDomain(String domainCode) {
        String tid = currentTenantId();
        String sql;
        Object[] args;
        if (tid == null || tid.isBlank()) {
            sql = "SELECT COUNT(*) FROM ecos_ontology_entity e " +
                  "INNER JOIN ecos_domain d ON e.domain_id = d.id " +
                  "WHERE d.code = ?";
            args = new Object[]{domainCode};
        } else {
            sql = "SELECT COUNT(*) FROM ecos_ontology_entity e " +
                  "INNER JOIN ecos_domain d ON e.domain_id = d.id " +
                  "WHERE d.code = ? AND (e.tenant_id = ? OR e.tenant_id IS NULL)";
            args = new Object[]{domainCode, tid};
        }
        Long count = jdbc.queryForObject(sql, Long.class, args);
        return count != null ? count.intValue() : 0;
    }

    /**
     * 搜索域（按 keyword 模糊匹配 name/code/description）。
     * <p>PMO E8 端点 {@code GET /api/v1/ecos/domains/search} 后端支撑。</p>
     *
     * @param keyword 搜索关键字（可为空，返回当前租户全部域）
     * @param limit   最大返回条数（默认 50）
     * @return 匹配的 Domain 列表
     */
    public List<OntologyDomain> searchDomains(String keyword, int limit) {
        String tid = currentTenantId();
        StringBuilder sql = new StringBuilder("SELECT d.* FROM ecos_domain d");
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
        sql.append(" ORDER BY d.sort_order, d.created_at DESC LIMIT ?");
        params.add(Math.max(1, Math.min(limit, 200)));

        return jdbc.query(sql.toString(), ROW_MAPPER, params.toArray());
    }
}
