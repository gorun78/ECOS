package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.common.data.dto.CatalogQueryDTO;
import com.chinacreator.gzcm.common.data.model.CatalogItem;
import com.chinacreator.gzcm.common.data.model.DataResource;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * CatalogServiceImpl 的 JDBC 数据访问层。
 * <p>表: td_catalog_item, td_data_resource。原始 SQL 均在此执行。</p>
 */
@Repository
public class CatalogDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "td_catalog_item";

    public void register(DataResource resource, String catalogId) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        jdbcTemplate.update(
            "INSERT INTO " + TABLE + " (catalog_id, resource_id, resource_name, resource_type, " +
            "org_name, description, tags, category_path, access_type, data_format, " +
            "field_count, record_count, last_updated, status, tenant_id) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            catalogId, resource.getResourceId(), resource.getResourceName(),
            resource.getResourceType(), resource.getOrgName(),
            resource.getDescription(), resource.getTags(),
            "", "internal", "",
            resource.getFieldCount(), resource.getRecordCount(),
            now, "active", null
        );
    }

    public List<CatalogItem> search(CatalogQueryDTO query) {
        StringBuilder sql = new StringBuilder("SELECT * FROM " + TABLE + " WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            sql.append(" AND (resource_name ILIKE ? OR description ILIKE ? OR tags ILIKE ?)");
            String kw = "%" + query.getKeyword() + "%";
            params.add(kw); params.add(kw); params.add(kw);
        }
        if (query.getResourceType() != null && !query.getResourceType().isBlank()) {
            sql.append(" AND resource_type = ?");
            params.add(query.getResourceType());
        }
        if (query.getOrgId() != null && !query.getOrgId().isBlank()) {
            sql.append(" AND org_name = ?");
            params.add(query.getOrgId());
        }
        if (query.getCategoryPath() != null && !query.getCategoryPath().isBlank()) {
            sql.append(" AND category_path ILIKE ?");
            params.add(query.getCategoryPath() + "%");
        }
        int page = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
        int pageSize = query.getPageSize() != null && query.getPageSize() > 0 ? query.getPageSize() : 20;
        sql.append(" ORDER BY last_updated DESC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add((page - 1) * pageSize);
        return jdbcTemplate.query(sql.toString(), (rs, i) -> mapRow(rs), params.toArray());
    }

    public CatalogItem getById(String catalogId) {
        List<CatalogItem> list = jdbcTemplate.query(
            "SELECT * FROM " + TABLE + " WHERE catalog_id = ?",
            (rs, i) -> mapRow(rs), catalogId
        );
        return list.isEmpty() ? null : list.get(0);
    }

    public CatalogItem getByResourceId(String resourceId) {
        List<CatalogItem> list = jdbcTemplate.query(
            "SELECT * FROM " + TABLE + " WHERE resource_id = ?",
            (rs, i) -> mapRow(rs), resourceId
        );
        return list.isEmpty() ? null : list.get(0);
    }

    public List<CatalogItem> listByOrg(String orgId) {
        return jdbcTemplate.query(
            "SELECT * FROM " + TABLE + " WHERE org_name = ? ORDER BY last_updated DESC",
            (rs, i) -> mapRow(rs), orgId
        );
    }

    public long count() {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + TABLE, Long.class);
        return c != null ? c : 0;
    }

    public List<CatalogItem> searchByFieldName(String fieldName, int page, int pageSize) {
        String sql = "SELECT c.* FROM " + TABLE + " c " +
            "INNER JOIN td_data_field f ON c.resource_id = f.resource_id " +
            "WHERE f.field_name ILIKE ? " +
            "GROUP BY c.catalog_id ORDER BY c.last_updated DESC LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, (rs, i) -> mapRow(rs),
            "%" + fieldName + "%", pageSize, (page - 1) * pageSize);
    }

    public long countByFieldName(String fieldName) {
        Long c = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT c.catalog_id) FROM " + TABLE + " c " +
            "INNER JOIN td_data_field f ON c.resource_id = f.resource_id " +
            "WHERE f.field_name ILIKE ?",
            Long.class, "%" + fieldName + "%");
        return c != null ? c : 0;
    }

    public void update(CatalogItem item) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        jdbcTemplate.update(
            "UPDATE " + TABLE + " SET resource_name = ?, resource_type = ?, description = ?, " +
            "tags = ?, category_path = ?, access_type = ?, status = ?, last_updated = ? " +
            "WHERE catalog_id = ?",
            item.getResourceName(), item.getResourceType(), item.getDescription(),
            item.getTags(), item.getCategoryPath(), item.getAccessType(),
            item.getStatus(), now, item.getCatalogId()
        );
    }

    public void remove(String catalogId) {
        jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE catalog_id = ?", catalogId);
    }

    private CatalogItem mapRow(ResultSet rs) throws SQLException {
        CatalogItem item = new CatalogItem();
        item.setCatalogId(rs.getString("catalog_id"));
        item.setResourceId(rs.getString("resource_id"));
        item.setResourceName(rs.getString("resource_name"));
        item.setResourceType(rs.getString("resource_type"));
        item.setOrgName(rs.getString("org_name"));
        item.setDescription(rs.getString("description"));
        item.setTags(rs.getString("tags"));
        item.setCategoryPath(rs.getString("category_path"));
        item.setAccessType(rs.getString("access_type"));
        item.setDataFormat(rs.getString("data_format"));
        item.setFieldCount(rs.getInt("field_count"));
        long rc = rs.getLong("record_count");
        item.setRecordCount(rs.wasNull() ? null : rc);
        Timestamp ts = rs.getTimestamp("last_updated");
        item.setLastUpdated(ts != null ? ts.toLocalDateTime() : null);
        item.setStatus(rs.getString("status"));
        return item;
    }
}
