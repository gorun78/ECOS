package com.chinacreator.gzcm.engine.security.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 敏感数据目录 DAO（R2-B3b 数据访问收口；只搬不改语义）。
 *
 * <p>表：{@code ecos_control.ecos_security_asset}（V200 控制域 schema 治理 B0 已加
 * {@code ecos_control} 前缀 —— 前修必须保留，禁止回退裸表名）。
 * 供 {@code SecurityAssetCatalogService}（catalog CRUD）使用。
 * mask 目录读取（同表）见 {@link SecurityDecisionDao}。</p>
 */
@Repository
public class SecurityAssetCatalogDao extends JdbcTemplateDaoSupport {

    /** V200 控制域主 schema（B0 已加前缀，literal 与存量代码对齐，避免引入新配置键）。 */
    private static final String TABLE = "ecos_control.ecos_security_asset";

    private static final String COLS = "id, asset_key, kind, sensitivity, guard_combo, exemption_ref, owner_role, "
            + "description, version_no, is_deleted, domain, tenant_id, create_time, update_time";

    /** 列表查询（未删除的全部）。 */
    public List<Map<String, Object>> listAll() {
        return jdbcTemplate.queryForList("SELECT " + COLS + " FROM " + TABLE
                + " WHERE is_deleted = 0 ORDER BY asset_key");
    }

    /** 按 asset_key 查询单条。 */
    public List<Map<String, Object>> findByAssetKey(String assetKey) {
        return jdbcTemplate.queryForList("SELECT " + COLS + " FROM " + TABLE
                + " WHERE is_deleted = 0 AND asset_key = ? LIMIT 1", assetKey);
    }

    /** 目录新增（id 由调用方以 "sec-asset-" + hashCode 规则生成）。 */
    public int insert(String id, String assetKey, String kind, String sensitivity,
                      String guardCombo, String exemptionRef, String ownerRole,
                      String description, String tenantId) {
        return jdbcTemplate.update(
                "INSERT INTO " + TABLE + " "
                        + "(id, asset_key, kind, sensitivity, guard_combo, exemption_ref, owner_role, "
                        + "     description, version_no, is_deleted, domain, tenant_id, "
                        + "      create_time, update_time, create_by, update_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, '1', 0, 'default', ?, NOW(), NOW(), 'api', 'api')",
                id, assetKey, kind, sensitivity, guardCombo, exemptionRef, ownerRole, description, tenantId);
    }

    /** 目录更新（version_no + 1）。 */
    public int update(String kind, String sensitivity, String guardCombo, String exemptionRef,
                      String ownerRole, String description, String id) {
        return jdbcTemplate.update(
                "UPDATE " + TABLE + " SET kind = ?, sensitivity = ?, guard_combo = ?, "
                        + "exemption_ref = ?, owner_role = ?, description = ?, update_time = NOW(), update_by = 'api', "
                        + "version_no = CAST(CAST(version_no AS INT) + 1 AS VARCHAR) "
                        + "WHERE id = ?",
                kind, sensitivity, guardCombo, exemptionRef, ownerRole, description, id);
    }

    /** 逻辑删除（只加不删 → is_deleted=1）。 */
    public int logicalDelete(String id) {
        return jdbcTemplate.update("UPDATE " + TABLE + " SET is_deleted = 1, "
                + "update_time = NOW(), update_by = 'api' WHERE id = ?",
                id);
    }
}
