package com.chinacreator.gzcm.engine.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 详细设计-01 E.6.2 / C.2.1 — 敏感数据目录服务。
 *
 * <p>表 {@code public.ecos_security_asset}（V164 DDL 实测列集）：
 * {@code id, tenant_id, asset_key, kind, sensitivity, guard_combo, exemption_ref,
 * owner_role, description, version_no, is_deleted, domain, create_time, update_time,
 * create_by, update_by}。</p>
 *
 * <ul>
 *   <li>{@code asset_key} = {@code schema.table}（kind=TABLE）或
 *       {@code schema.table.column}（kind=COLUMN），ST09 跨域只存定位符（引擎侧不写裸表）；</li>
 *   <li>{@code kind ∈ {COLUMN, TABLE}}、{@code sensitivity ∈ {S0..S4}}、
 *       {@code guard_combo} = 固定 token 组合（{@code rls+cls+mask+gcm} — 禁自由文本）；</li>
 *   <li>{@code exemption_ref} = ST03-A 豁免登记条目号；未登记的受保护资产 = 硬护栏（C.2.4 / E.6.3）；</li>
 *   <li>治理 = upsert + 停用（IR03 只加不删；version_no 递增，DOMAIN 默认 default）。</li>
 * </ul>
 */
@Service
public class SecurityAssetCatalogService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAssetCatalogService.class);

    /** 显式列白名单（IR04：禁 SELECT *） */
    private static final String COLUMNS =
            "id, tenant_id, asset_key, kind, sensitivity, guard_combo, exemption_ref, " +
            "owner_role, description, version_no, is_deleted, domain, create_time, update_time, " +
            "create_by, update_by";

    private static final java.util.Set<String> KINDS = java.util.Set.of("COLUMN", "TABLE");
    private static final java.util.Set<String> SENSITIVITIES = java.util.Set.of("S0", "S1", "S2", "S3", "S4");
    private static final java.util.regex.Pattern GUARD_TOKENS =
            java.util.regex.Pattern.compile("^[a-z0-9+_.-]{4,64}$");

    private final JdbcTemplate jdbc;

    public SecurityAssetCatalogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(String tenantId, String assetKeyLike) {
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(COLUMNS);
        sql.append(" FROM ecos_security_asset WHERE is_deleted = 0");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (tenantId != null && !tenantId.isBlank()) {
            sql.append(" AND tenant_id = ?");
            args.add(tenantId);
        }
        if (assetKeyLike != null && !assetKeyLike.isBlank()) {
            sql.append(" AND asset_key ILIKE ?");
            args.add("%" + assetKeyLike.trim() + "%");
        }
        sql.append(" ORDER BY asset_key ASC");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> getByKey(String tenantId, String assetKey) {
        java.util.List<Map<String, Object>> rows = tenantId != null && !tenantId.isBlank()
                ? jdbc.queryForList(
                        "SELECT " + COLUMNS + " FROM ecos_security_asset " +
                        "WHERE tenant_id = ? AND asset_key = ? AND is_deleted = 0 LIMIT 1",
                        tenantId, assetKey)
                : jdbc.queryForList(
                        "SELECT " + COLUMNS + " FROM ecos_security_asset " +
                        "WHERE asset_key = ? AND is_deleted = 0 LIMIT 1",
                        assetKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 校验后 upsert 一条资产目录行；命中 (tenant_id, asset_key) 唯一索引 → 更新版本，
     * 否则新建。guard_combo 必须匹配 {@link #GUARD_TOKENS}（token 组合形态，非自由文本）。
     */
    public Map<String, Object> upsert(Map<String, Object> body) {
        String tenantId = str(body.getOrDefault("tenantId", "default"));
        String assetKey = str(body.get("assetKey"));
        String kind = str(body.get("kind"));
        String sensitivity = str(body.get("sensitivity"));
        String guardCombo = str(body.getOrDefault("guardCombo", ""));
        String exemptionRef = str(body.get("exemptionRef"));
        String ownerRole = str(body.get("ownerRole"));
        String description = str(body.getOrDefault("description", ""));
        String domain = str(body.getOrDefault("domain", "default"));

        if (assetKey == null || assetKey.isBlank()) {
            throw new IllegalArgumentException("assetKey 必填（schema.table 或 schema.table.column）");
        }
        if (!KINDS.contains(kindToUc(kind))) {
            throw new IllegalArgumentException("kind ∈ {COLUMN, TABLE} 必填");
        }
        if (!SENSITIVITIES.contains(sensitivityToUc(sensitivity))) {
            throw new IllegalArgumentException("sensitivity ∈ {S0..S4} 必填");
        }
        if (guardCombo == null || guardCombo.isBlank() || !GUARD_TOKENS.matcher(guardCombo).matches()) {
            throw new IllegalArgumentException("guardCombo 必须为固定 token 组合（如 rls+cls+mask）");
        }

        Map<String, Object> existing = getByKey(tenantId, assetKey);
        String createdBy = str(body.getOrDefault("createdBy", existing != null ? existing.get("create_by") : "system"));
        if (existing != null) {
            String nextVersion = bump(str(existing.get("version_no")));
            jdbc.update("""
                    UPDATE ecos_security_asset SET kind = ?, sensitivity = ?, guard_combo = ?,
                        exemption_ref = ?, owner_role = ?, description = ?, domain = ?,
                        version_no = ?, update_time = NOW(), update_by = ?
                    WHERE tenant_id = ? AND asset_key = ?
                    """,
                    kindToUc(kind), sensitivityToUc(sensitivity), guardCombo,
                    exemptionRef, ownerRole, description, domain, nextVersion, createdBy,
                    tenantId, assetKey);
            log.info("security asset updated: tenant={}, key={}, version_no={}", tenantId, assetKey, nextVersion);
        } else {
            String id = UUID.randomUUID().toString().replace("-", "");
            jdbc.update("""
                    INSERT INTO ecos_security_asset (id, tenant_id, asset_key, kind, sensitivity,
                        guard_combo, exemption_ref, owner_role, description, domain,
                        version_no, is_deleted, create_time, update_time, create_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '1', 0, NOW(), NOW(), ?)
                    """,
                    id, tenantId, assetKey, kindToUc(kind), sensitivityToUc(sensitivity),
                    guardCombo, exemptionRef, ownerRole, description, domain, createdBy);
            log.info("security asset created: id={}, tenant={}, key={}", id, tenantId, assetKey);
        }
        return getByKey(tenantId, assetKey);
    }

    public Map<String, Object> disable(String tenantId, String assetKey) {
        Map<String, Object> existing = getByKey(tenantId, assetKey);
        if (existing == null) return null;
        String nextVersion = bump(str(existing.get("version_no")));
        jdbc.update("UPDATE ecos_security_asset SET is_deleted = 1, version_no = ?, " +
                        "update_time = NOW(), update_by = 'security-admin' WHERE tenant_id = ? AND asset_key = ?",
                nextVersion, tenantId, assetKey);
        log.info("security asset disabled: tenant={}, key={}", tenantId, assetKey);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("tenantId", tenantId);
        r.put("assetKey", assetKey);
        r.put("disabled", true);
        r.put("versionNo", nextVersion);
        return r;
    }

    private static String kindToUc(String k) {
        return k == null ? null : k.toUpperCase(java.util.Locale.ROOT);
    }

    private static String sensitivityToUc(String s) {
        return s == null ? null : s.toUpperCase(java.util.Locale.ROOT);
    }

    private static String bump(String v) {
        try {
            return String.valueOf(Math.max(Integer.parseInt(v == null ? "" : v.trim()), 1) + 1);
        } catch (Exception e) {
            return "2";
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
