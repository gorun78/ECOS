package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.engine.data.dto.DataAssetFieldPageVO;
import com.chinacreator.gzcm.engine.data.dto.DataAssetFieldTagDTO;
import com.chinacreator.gzcm.engine.data.dto.DataAssetQueryDTO;
import com.chinacreator.gzcm.engine.data.dto.DataAssetSaveDTO;
import com.chinacreator.gzcm.engine.data.dto.DataAssetFieldVO;
import com.chinacreator.gzcm.engine.data.dto.DataAssetVO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AssetService 的 JDBC 数据访问层。
 * <p>表: ecos_data.ecos_data_asset / ecos_data_asset_field / ecos_data_category_tree /
 * ecos_data_level_def / td_data_resource。原始 SQL 均在此执行。</p>
 */
@Repository
public class AssetDao extends JdbcTemplateDaoSupport {

    /** 1. createAsset — resource_id 存在于 td_data_resource 校验 */
    public Integer countResourceById(String resourceId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM td_data_resource WHERE resource_id = ?", Integer.class, resourceId);
    }

    /** 2. createAsset — 防重 (resource_id, domain) */
    public Integer countAssetByResourceAndDomain(String resourceId, String domain) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ecos_data.ecos_data_asset WHERE resource_id = ? AND domain = ?",
            Integer.class, resourceId, domain);
    }

    /** 3. createAsset — 同步冗余 td_data_resource 元数据 */
    public List<Map<String, Object>> physicalResourceRow(String resourceId) {
        return jdbcTemplate.queryForList(
            "SELECT resource_id, resource_name, resource_type, datasource_id, " +
            "description, field_count, record_count, layer, zone, last_sync_time " +
            "FROM td_data_resource WHERE resource_id = ?", resourceId);
    }

    /** 4. createAsset — 写入 asset */
    public void insertAsset(String assetId, DataAssetSaveDTO dto, String level, String domain,
                            String operator, Timestamp now) {
        jdbcTemplate.update(
            "INSERT INTO ecos_data.ecos_data_asset (" +
            "  asset_id, resource_id, asset_name, business_desc, owner, owner_org, data_grain, " +
            "  category_id, sensitivity_level, category_status, domain, " +
            "  create_time, update_time, create_by, update_by, version_no, is_deleted" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, '1', 0)",
            assetId,
            dto.getResourceId(),
            dto.getAssetName().trim(),
            dto.getBusinessDesc(),
            dto.getOwner(),
            dto.getOwnerOrg(),
            dto.getDataGrain(),
            dto.getCategoryId(),
            level,
            domain,
            now, now, operator, operator
        );
    }

    /** updateAsset — COALESCE 语义 */
    public void updateAsset(String assetId, DataAssetSaveDTO dto, String level, String operator) {
        jdbcTemplate.update(
            "UPDATE ecos_data.ecos_data_asset SET " +
            "  asset_name = COALESCE(?, asset_name), business_desc = COALESCE(?, business_desc), " +
            "  owner = COALESCE(?, owner), owner_org = COALESCE(?, owner_org), " +
            "  data_grain = COALESCE(?, data_grain), category_id = COALESCE(?, category_id), " +
            "  sensitivity_level = COALESCE(?, sensitivity_level), update_time = NOW(), " +
            "  update_by = ?, version_no = (CAST(version_no AS INT) + 1)::text " +
            "WHERE asset_id = ? AND is_deleted = 0",
            optStr(dto.getAssetName()), dto.getBusinessDesc(), dto.getOwner(), dto.getOwnerOrg(),
            optStr(dto.getDataGrain()), optStr(dto.getCategoryId()), level, operator, assetId);
    }

    /** deleteAsset — 逻辑删除 */
    public int softDeleteAsset(String assetId, String operator) {
        return jdbcTemplate.update(
            "UPDATE ecos_data.ecos_data_asset SET is_deleted = 1, update_time = NOW(), " +
            "  update_by = ?, version_no = (CAST(version_no AS INT) + 1)::text WHERE asset_id = ? AND is_deleted = 0",
            operator, assetId);
    }

    /** getAsset */
    public List<DataAssetVO> findAssetRows(String assetId) {
        return jdbcTemplate.query(
            "SELECT a.asset_id, a.resource_id, r.resource_name, r.resource_type, r.datasource_id, " +
            "  a.asset_name, a.business_desc, a.owner, a.owner_org, a.data_grain, " +
            "  c.category_id, c.name AS category_name, " +
            "  a.sensitivity_level, l.level_name, a.category_status, " +
            "  a.last_tagged_by, a.last_tagged_at, a.domain, " +
            "  r.description, r.field_count, r.record_count, r.layer, r.zone, r.last_sync_time, " +
            "  (SELECT COUNT(*) FROM ecos_data.ecos_data_asset_field af " +
            "    WHERE af.asset_id = a.asset_id AND af.confirmed = TRUE AND af.is_deleted = 0) " +
            "    AS confirmed_field_count " +
            "FROM ecos_data.ecos_data_asset a " +
            "LEFT JOIN td_data_resource r ON r.resource_id = a.resource_id " +
            "LEFT JOIN ecos_data.ecos_data_category_tree c ON c.category_id = a.category_id AND c.is_deleted = 0 " +
            "LEFT JOIN ecos_data.ecos_data_level_def l ON l.level_code = a.sensitivity_level " +
            "WHERE a.asset_id = ? AND a.is_deleted = 0",
            assetRowMapper(),
            assetId);
    }

    /** listAssets — 登记资产数统计 */
    public Integer countRegisteredAssets() {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ecos_data.ecos_data_asset WHERE is_deleted = 0", Integer.class);
    }

    /** listAssets — 已登记资产列表（动态 SQL） */
    public List<DataAssetVO> listAssets(DataAssetQueryDTO q) {
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(
            "a.asset_id, a.resource_id, r.resource_name, r.resource_type, r.datasource_id, " +
            "  a.asset_name, a.business_desc, a.owner, a.owner_org, a.data_grain, " +
            "  c.category_id, c.name AS category_name, " +
            "  a.sensitivity_level, l.level_name, a.category_status, " +
            "  a.last_tagged_by, a.last_tagged_at, a.domain, " +
            "  r.description, r.field_count, r.record_count, r.layer, r.zone, r.last_sync_time, " +
            "  (SELECT COUNT(*) FROM ecos_data.ecos_data_asset_field af " +
            "    WHERE af.asset_id = a.asset_id AND af.confirmed = TRUE AND af.is_deleted = 0) " +
            "    AS confirmed_field_count ");
        sql.append("FROM ecos_data.ecos_data_asset a ");
        sql.append("LEFT JOIN td_data_resource r ON r.resource_id = a.resource_id ");
        sql.append("LEFT JOIN ecos_data.ecos_data_category_tree c ON c.category_id = a.category_id AND c.is_deleted = 0 ");
        sql.append("LEFT JOIN ecos_data.ecos_data_level_def l ON l.level_code = a.sensitivity_level ");
        sql.append("WHERE a.is_deleted = 0");

        List<Object> params = new ArrayList<>();
        if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
            sql.append(" AND (a.asset_name ILIKE ? OR a.business_desc ILIKE ? OR r.description ILIKE ?)");
            String kw = "%" + q.getKeyword() + "%";
            params.add(kw); params.add(kw); params.add(kw);
        }
        if (q.getSensitivityLevel() != null && !q.getSensitivityLevel().isBlank()) {
            sql.append(" AND a.sensitivity_level = ?");
            params.add(q.getSensitivityLevel());
        }
        if (q.getCategoryId() != null && !q.getCategoryId().isBlank()) {
            sql.append(" AND a.category_id = ?");
            params.add(q.getCategoryId());
        }
        if (q.getLayer() != null && !q.getLayer().isBlank()) {
            sql.append(" AND r.layer = ?");
            params.add(q.getLayer());
        }
        if (q.getZone() != null && !q.getZone().isBlank()) {
            sql.append(" AND r.zone = ?");
            params.add(q.getZone());
        }
        if (q.getDatasourceId() != null && !q.getDatasourceId().isBlank()) {
            sql.append(" AND r.datasource_id = ?");
            params.add(q.getDatasourceId());
        }
        if (q.getOwner() != null && !q.getOwner().isBlank()) {
            sql.append(" AND a.owner = ?");
            params.add(q.getOwner());
        }
        if (q.getResourceType() != null && !q.getResourceType().isBlank()) {
            sql.append(" AND r.resource_type = ?");
            params.add(q.getResourceType());
        }
        if (q.getDomain() != null && !q.getDomain().isBlank()) {
            sql.append(" AND a.domain = ?");
            params.add(q.getDomain());
        }
        if (q.getCategoryStatus() != null && !q.getCategoryStatus().isBlank()) {
            sql.append(" AND a.category_status = ?");
            params.add(q.getCategoryStatus());
        }
        sql.append(" ORDER BY a.update_time DESC");
        int page = (q.getPage() == null || q.getPage() < 1) ? 1 : q.getPage();
        int pageSize = (q.getPageSize() == null || q.getPageSize() < 1) ? 20 : q.getPageSize();
        sql.append(" LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add((page - 1) * pageSize);
        return jdbcTemplate.query(sql.toString(), assetRowMapper(), params.toArray());
    }

    /** listPhysicalFallback — 物理层占位视图 */
    public List<DataAssetVO> listPhysicalFallback(DataAssetQueryDTO q) {
        StringBuilder sql = new StringBuilder(
            "SELECT r.resource_id AS asset_id, r.resource_id, r.resource_name AS asset_name, " +
            "  r.resource_name, r.resource_type, r.datasource_id, NULL::text AS business_desc, " +
            "  NULL::text AS owner, NULL::text AS owner_org, 'SOURCE'::text AS data_grain, " +
            "  NULL::text AS category_id, NULL::text AS category_name, 'L2'::text AS sensitivity_level, " +
            "  '内部'::text AS level_name, 'PENDING'::text AS category_status, " +
            "  NULL::text AS last_tagged_by, NULL::timestamp AS last_tagged_at, " +
            "  'default'::text AS domain, r.description, " +
            "  COALESCE(r.field_count, 0) AS field_count, COALESCE(r.record_count, -1) AS record_count, " +
            "  COALESCE(NULLIF(r.layer, ''), 'CURATED')::text AS layer, r.zone, r.last_sync_time, 0 AS confirmed_field_count " +
            "FROM td_data_resource r WHERE TRUE");
        List<Object> params = new ArrayList<>();
        if (q.getZone() != null && !q.getZone().isBlank())   { sql.append(" AND r.zone = ?");  params.add(q.getZone()); }
        if (q.getDatasourceId() != null && !q.getDatasourceId().isBlank()) { sql.append(" AND r.datasource_id = ?"); params.add(q.getDatasourceId()); }
        if (q.getResourceType() != null && !q.getResourceType().isBlank()) { sql.append(" AND r.resource_type = ?"); params.add(q.getResourceType()); }
        if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
            sql.append(" AND (r.resource_name ILIKE ? OR r.description ILIKE ?)");
            String kw = "%" + q.getKeyword() + "%"; params.add(kw); params.add(kw);
        }
        sql.append(" ORDER BY r.last_sync_time DESC NULLS LAST");
        int page = (q.getPage() == null || q.getPage() < 1) ? 1 : q.getPage();
        int pageSize = (q.getPageSize() == null || q.getPageSize() < 1) ? 50 : q.getPageSize();
        sql.append(" LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add((page - 1) * pageSize);
        return jdbcTemplate.query(sql.toString(), assetRowMapper(), params.toArray());
    }

    /** listFields — 字段总数 */
    public Long countFields(String assetId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ecos_data.ecos_data_asset_field WHERE asset_id = ? AND is_deleted = 0",
            Long.class, assetId);
    }

    /** listFields — confirmed 字段总数 */
    public Long countConfirmedFields(String assetId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ecos_data.ecos_data_asset_field " +
            "WHERE asset_id = ? AND confirmed = TRUE AND is_deleted = 0", Long.class, assetId);
    }

    /** listFields — 当页字段行 */
    public List<DataAssetFieldVO> listFieldRows(String assetId, Boolean confirmed, int page, int pageSize) {
        StringBuilder sql = new StringBuilder(
            "SELECT field_asset_id, asset_id, field_id, field_name, field_type, " +
            "data_type, field_sensitivity, mask_strategy, recommend_level, " +
            "recommend_source, confirmed " +
            "FROM ecos_data.ecos_data_asset_field WHERE asset_id = ? AND is_deleted = 0");
        List<Object> params = new ArrayList<>();
        params.add(assetId);
        if (confirmed != null) {
            if (confirmed) {
                sql.append(" AND confirmed = TRUE");
            } else {
                sql.append(" AND (confirmed = FALSE OR confirmed IS NULL)");
            }
        }
        sql.append(" ORDER BY field_name ASC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add((page - 1) * pageSize);
        return jdbcTemplate.query(sql.toString(), fieldRowMapper(), params.toArray());
    }

    /** tagAssetFields — 字段 dup check */
    public Integer countFieldDup(String assetId, String fieldId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ecos_data.ecos_data_asset_field " +
            "WHERE asset_id = ? AND field_id = ? AND is_deleted = 0",
            Integer.class, assetId, fieldId);
    }

    /** tagAssetFields — 更新既有字段 */
    public void updateField(String assetId, DataAssetFieldTagDTO.FieldTagItem it,
                            String dataType, String level, String mask, boolean confirmed, String operator) {
        jdbcTemplate.update(
            "UPDATE ecos_data.ecos_data_asset_field SET " +
            "field_name = COALESCE(?, field_name), " +
            "field_type = COALESCE(?, field_type), " +
            "data_type = ?, field_sensitivity = ?, mask_strategy = ?, " +
            "recommend_level = COALESCE(?, recommend_level), " +
            "recommend_source = COALESCE(?, recommend_source), " +
            "confirmed = ?, update_time = NOW(), update_by = ?, " +
            "version_no = (CAST(version_no AS INT) + 1)::text " +
            "WHERE asset_id = ? AND field_id = ? AND is_deleted = 0",
            optStr(it.getFieldName()), optStr(it.getFieldType()),
            dataType, level, mask, it.getRecommendLevel(), it.getRecommendSource(),
            confirmed, operator, assetId, it.getFieldId());
    }

    /** tagAssetFields — 新增字段 */
    public void insertField(String assetId, DataAssetFieldTagDTO.FieldTagItem it, String fieldAssetId,
                            String dataType, String level, String mask, boolean confirmed,
                            String operator, Timestamp now) {
        jdbcTemplate.update(
            "INSERT INTO ecos_data.ecos_data_asset_field (" +
            "  field_asset_id, asset_id, field_id, field_name, field_type, " +
            "  data_type, field_sensitivity, mask_strategy, " +
            "  recommend_level, recommend_source, confirmed, " +
            "  create_time, update_time, create_by, update_by, version_no, is_deleted" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '1', 0)",
            fieldAssetId, assetId, it.getFieldId(),
            it.getFieldName() != null ? it.getFieldName() : it.getFieldId(),
            it.getFieldType(), dataType, level, mask,
            it.getRecommendLevel(), it.getRecommendSource(),
            confirmed, now, now, operator, operator);
    }

    /** tagAssetFields — max level */
    public String maxConfirmedLevel(String assetId) {
        return jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(field_sensitivity), 'L1') FROM ecos_data.ecos_data_asset_field " +
            "WHERE asset_id = ? AND confirmed = TRUE AND is_deleted = 0",
            String.class, assetId);
    }

    /** tagAssetFields — confirmed 时回写 asset 状态为 CONFIRMED */
    public void updateAssetAfterConfirmedTag(String assetId, String maxLevel, String operator) {
        jdbcTemplate.update(
            "UPDATE ecos_data.ecos_data_asset SET sensitivity_level = ?, " +
            "  category_status = 'CONFIRMED', last_tagged_by = ?, last_tagged_at = NOW(), " +
            "  update_time = NOW(), update_by = ?, version_no = (CAST(version_no AS INT) + 1)::text " +
            "WHERE asset_id = ? AND is_deleted = 0",
            maxLevel, operator, operator, assetId);
    }

    /** tagAssetFields — non-confirmed 时回写 asset 状态为 PENDING_CONFIRMATION */
    public void updateAssetAfterPendingTag(String assetId, String operator) {
        jdbcTemplate.update(
            "UPDATE ecos_data.ecos_data_asset SET category_status = 'PENDING_CONFIRMATION', " +
            "  update_time = NOW(), update_by = ?, version_no = (CAST(version_no AS INT) + 1)::text " +
            "WHERE asset_id = ? AND is_deleted = 0",
            operator, assetId);
    }

    /** listLevelDefs */
    public List<Map<String, Object>> listLevelDefs() {
        return jdbcTemplate.queryForList(
            "SELECT level_code, level_value, level_name, level_description, " +
            "  mask_strategy_json, rls_strategy_json, sort_order " +
            "FROM ecos_data.ecos_data_level_def WHERE is_deleted = 0 ORDER BY level_value ASC");
    }

    /** listCategoryTree */
    public List<Map<String, Object>> listCategoryTree() {
        return jdbcTemplate.queryForList(
            "SELECT category_id, name, parent_id, level, description, icon " +
            "FROM ecos_data.ecos_data_category_tree WHERE is_deleted = 0 ORDER BY level, name");
    }

    private static String optStr(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /** 资产行映射器 */
    static RowMapper<DataAssetVO> assetRowMapper() {
        return new RowMapper<>() {
            @Override
            public DataAssetVO mapRow(ResultSet rs, int i) throws SQLException {
                DataAssetVO vo = new DataAssetVO();
                vo.setAssetId(rs.getString("asset_id"));
                vo.setResourceId(rs.getString("resource_id"));
                vo.setResourceName(rs.getString("resource_name"));
                vo.setResourceType(rs.getString("resource_type"));
                vo.setDatasourceId(rs.getString("datasource_id"));
                vo.setAssetName(rs.getString("asset_name"));
                vo.setBusinessDesc(rs.getString("business_desc"));
                vo.setOwner(rs.getString("owner"));
                vo.setOwnerOrg(rs.getString("owner_org"));
                vo.setDataGrain(rs.getString("data_grain"));
                vo.setCategoryId(rs.getString("category_id"));
                vo.setCategoryName(rs.getString("category_name"));
                vo.setSensitivityLevel(rs.getString("sensitivity_level"));
                vo.setLevelName(rs.getString("level_name"));
                vo.setCategoryStatus(rs.getString("category_status"));
                vo.setLastTaggedBy(rs.getString("last_tagged_by"));
                java.sql.Timestamp lastTaggedAt = rs.getTimestamp("last_tagged_at");
                if (lastTaggedAt != null) vo.setLastTaggedAt(lastTaggedAt.toLocalDateTime());
                vo.setDomain(rs.getString("domain"));
                vo.setDescription(rs.getString("description"));
                vo.setFieldCount(rs.getObject("field_count", Integer.class));
                vo.setRecordCount(rs.getObject("record_count", Long.class));
                vo.setLayer(rs.getString("layer"));
                vo.setZone(rs.getString("zone"));
                java.sql.Timestamp lastSync = rs.getTimestamp("last_sync_time");
                if (lastSync != null) vo.setLastSyncTime(lastSync.toLocalDateTime());
                vo.setConfirmedFieldCount(rs.getObject("confirmed_field_count", Integer.class));
                return vo;
            }
        };
    }

    /** 字段行映射器 */
    static RowMapper<DataAssetFieldVO> fieldRowMapper() {
        return (rs, i) -> {
            DataAssetFieldVO v = new DataAssetFieldVO();
            v.setFieldAssetId(rs.getString("field_asset_id"));
            v.setAssetId(rs.getString("asset_id"));
            v.setFieldId(rs.getString("field_id"));
            v.setFieldName(rs.getString("field_name"));
            v.setFieldType(rs.getString("field_type"));
            v.setDataType(rs.getString("data_type"));
            v.setFieldSensitivity(rs.getString("field_sensitivity"));
            v.setMaskStrategy(rs.getString("mask_strategy"));
            v.setRecommendLevel(rs.getString("recommend_level"));
            v.setRecommendSource(rs.getString("recommend_source"));
            v.setConfirmed(rs.getBoolean("confirmed"));
            return v;
        };
    }
}
