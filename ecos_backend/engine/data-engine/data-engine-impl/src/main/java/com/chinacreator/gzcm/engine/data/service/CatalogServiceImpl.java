package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.data.dto.CatalogQueryDTO;
import com.chinacreator.gzcm.common.data.model.CatalogItem;
import com.chinacreator.gzcm.common.data.model.DataResource;
import com.chinacreator.gzcm.engine.data.CatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CatalogService JdbcTemplate 实现（PMO-E3: 替换启动兜底 stub）。
 * 表: td_catalog_item, td_data_resource
 */
@Service
public class CatalogServiceImpl implements CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogServiceImpl.class);
    private static final String TABLE = "td_catalog_item";

    private final JdbcTemplate jdbc;

    public CatalogServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CatalogItem register(DataResource resource) {
        String catalogId = UUID.randomUUID().toString().replace("-", "");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        jdbc.update(
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
        log.info("Registered catalog item: id={}, resource={}", catalogId, resource.getResourceName());
        return getById(catalogId);
    }

    @Override
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
        return jdbc.query(sql.toString(), (rs, i) -> mapRow(rs), params.toArray());
    }

    @Override
    public CatalogItem getById(String catalogId) {
        List<CatalogItem> list = jdbc.query(
            "SELECT * FROM " + TABLE + " WHERE catalog_id = ?",
            (rs, i) -> mapRow(rs), catalogId
        );
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public CatalogItem getByResourceId(String resourceId) {
        List<CatalogItem> list = jdbc.query(
            "SELECT * FROM " + TABLE + " WHERE resource_id = ?",
            (rs, i) -> mapRow(rs), resourceId
        );
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<CatalogItem> listByOrg(String orgId) {
        return jdbc.query(
            "SELECT * FROM " + TABLE + " WHERE org_name = ? ORDER BY last_updated DESC",
            (rs, i) -> mapRow(rs), orgId
        );
    }

    @Override
    public long count() {
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM " + TABLE, Long.class);
        return c != null ? c : 0;
    }

    @Override
    public List<CatalogItem> searchByFieldName(String fieldName, int page, int pageSize) {
        String sql = "SELECT c.* FROM " + TABLE + " c " +
            "INNER JOIN td_data_field f ON c.resource_id = f.resource_id " +
            "WHERE f.field_name ILIKE ? " +
            "GROUP BY c.catalog_id ORDER BY c.last_updated DESC LIMIT ? OFFSET ?";
        return jdbc.query(sql, (rs, i) -> mapRow(rs),
            "%" + fieldName + "%", pageSize, (page - 1) * pageSize);
    }

    @Override
    public long countByFieldName(String fieldName) {
        Long c = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT c.catalog_id) FROM " + TABLE + " c " +
            "INNER JOIN td_data_field f ON c.resource_id = f.resource_id " +
            "WHERE f.field_name ILIKE ?",
            Long.class, "%" + fieldName + "%");
        return c != null ? c : 0;
    }

    @Override
    public CatalogItem update(CatalogItem item) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        jdbc.update(
            "UPDATE " + TABLE + " SET resource_name = ?, resource_type = ?, description = ?, " +
            "tags = ?, category_path = ?, access_type = ?, status = ?, last_updated = ? " +
            "WHERE catalog_id = ?",
            item.getResourceName(), item.getResourceType(), item.getDescription(),
            item.getTags(), item.getCategoryPath(), item.getAccessType(),
            item.getStatus(), now, item.getCatalogId()
        );
        return getById(item.getCatalogId());
    }

    @Override
    public void remove(String catalogId) {
        jdbc.update("DELETE FROM " + TABLE + " WHERE catalog_id = ?", catalogId);
        log.info("Removed catalog item: {}", catalogId);
    }

    private CatalogItem mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
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
