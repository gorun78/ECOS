package com.chinacreator.gzcm.engine.security.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 三通道唯一裁决 DAO（R2-B3b 数据访问收口；只搬不改语义）。
 *
 * <p>表：{@code ecos_control.ecos_security_asset}（mask 源目录，与 catalog 同表）。
 * 供 {@code SecurityDecisionService#safeMasks} 使用；RLS/CLS/OPA 三源走对应
 * service（{@code ecosRlsService} / {@code ecosClsService} / {@code OpaPolicyService}），
 * 不经本 DAO。</p>
 */
@Repository
public class SecurityDecisionDao extends JdbcTemplateDaoSupport {

    /** V200 控制域主 schema（与 B0 前修口径一致；原文裸表名与 catalog 同表，收口统一加前缀）。 */
    private static final String ASSET_TABLE = "ecos_control.ecos_security_asset";

    /**
     * mask 源：COLUMN 行 guard_combo → 强制 mask 列集合。
     */
    public List<Map<String, Object>> masksForTable(String table, String keyPrefix) {
        return jdbcTemplate.queryForList(
                "SELECT split_part(asset_key, '.', array_position(string_to_array(asset_key, '.'), ?) + 1) AS column, " +
                "       CASE WHEN guard_combo LIKE '%staff%' THEN 'STAFF_REF' " +
                "            WHEN guard_combo LIKE '%amount%' THEN 'AMOUNT' " +
                "            ELSE 'MASK' END AS strategy " +
                "FROM " + ASSET_TABLE + " " +
                "WHERE kind = 'COLUMN' AND is_deleted = 0 " +
                "  AND asset_key LIKE '%' || ? || '.%' " +
                "ORDER BY asset_key",
                table, keyPrefix);
    }
}
