package com.chinacreator.gzcm.engine.security.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 详细设计-01 F01-09 — 敏感数据目录（表/列/级别/防护组合/豁免引用）数据访问服务。
 *
 * <p>落库形态：{@code public.ecos_security_asset}（V164 建表 + V246 首批预测场景种子）。
 * 目录承载 PRD-REQ-SEC-01 §1.1 六类敏感对象的**治理画像**：ID、granularity、
 * sensitivity 级别（S0-S4）、防护组合（{@code rls}+{@code cls}+{@code mask} 固定 token 组合）、
 * 豁免引用（指向 ST03-A 豁免登记表）、责任角色、描述。目录<b>不含</b>密钥材料、
 * 亦不承担运行时裁决 —— 运行裁决由 {@code SecurityDecisionService} 消费 +
 * security-engine 各子服务（RLS/CLS/OPA/Mask）独立完成。</p>
 *
 * <p>纪律：DR01~DR08、MC01~MC03、IR03（只加不删）、ST07（控制域主 schema）。</p>
 */
@Service
public class SecurityAssetCatalogService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAssetCatalogService.class);

    /** 允许 token：{@code rls}, {@code cls}, {@code mask}；组合用 {@code +} 连接，禁止自由文本。 */
    private static final Pattern GUARD_COMBO = Pattern.compile("^(rls|cls|mask)((\\+(rls|cls|mask))*)?$");
    /** sensitivity 白名单 S0-S4。 */
    private static final Pattern SENSITIVITY = Pattern.compile("^S[0-4]$");
    /** kind 白名单 COLUMN|TABLE。 */
    private static final Pattern KIND = Pattern.compile("^(COLUMN|TABLE)$");
    private static final Pattern ASSET_KEY = Pattern.compile("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+){1,2}$");

    private final JdbcTemplate jdbc;

    public SecurityAssetCatalogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 列表查询（未删除的全部）。
     */
    public List<Map<String, Object>> listAll() {
        String sql = "SELECT id, asset_key, kind, sensitivity, guard_combo, exemption_ref, owner_role, "
                + "description, version_no, is_deleted, domain, tenant_id, create_time, update_time "
                + "FROM public.ecos_security_asset WHERE is_deleted = 0 ORDER BY asset_key";
        return jdbc.queryForList(sql);
    }

    /**
     * 按 asset_key 查询单条。
     */
    public Map<String, Object> findByAssetKey(String assetKey) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, asset_key, kind, sensitivity, guard_combo, exemption_ref, owner_role, description, "
                        + "version_no, is_deleted, domain, tenant_id, create_time, update_time "
                        + "FROM public.ecos_security_asset WHERE is_deleted = 0 AND asset_key = ? LIMIT 1",
                assetKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Upsert 一条目录记录（(tenant_id, asset_key) 维度的条件唯一，PG 侧部分索引 + 应用侧软删判定）。
     *
     * @throws IllegalArgumentException 参数校验失败（非法 token/sensitivity/kind/empty key），Controller 侧映射为 400。
     * @return 新/更新后的整条记录。
     */
    public Map<String, Object> upsert(String assetKey, String kind, String sensitivity,
                                      String guardCombo, String exemptionRef, String ownerRole,
                                      String description, String tenantId) {
        if (assetKey == null || !ASSET_KEY.matcher(assetKey).matches()) {
            throw new IllegalArgumentException("asset_key 形态非法（需 schema.table 或 schema.table.column）: " + assetKey);
        }
        if (kind == null || !KIND.matcher(kind).matches()) {
            throw new IllegalArgumentException("kind 非法（须 COLUMN|TABLE）: " + kind);
        }
        if (sensitivity == null || !SENSITIVITY.matcher(sensitivity).matches()) {
            throw new IllegalArgumentException("sensitivity 非法（须 S0-S4）: " + sensitivity);
        }
        if (guardCombo == null || !GUARD_COMBO.matcher(guardCombo).matches()) {
            throw new IllegalArgumentException("guard_combo 非法（须 rls/cls/mask 组合, '+' 连接）: " + guardCombo);
        }

        Map<String, Object> existing = findByAssetKey(assetKey);
        if (existing == null) {
            // insert
            String id = "sec-asset-" + assetKey.hashCode();
            jdbc.update(
                    "INSERT INTO public.ecos_security_asset "
                            + "(id, asset_key, kind, sensitivity, guard_combo, exemption_ref, owner_role, "
                            + "     description, version_no, is_deleted, domain, tenant_id, "
                            + "      create_time, update_time, create_by, update_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, '1', 0, 'default', ?, NOW(), NOW(), 'api', 'api')",
                    id, assetKey, kind, sensitivity, guardCombo, exemptionRef, ownerRole, description, tenantId);
            Map<String, Object> row = new LinkedHashMap<>(freshRow(assetKey, kind, sensitivity, guardCombo,
                    exemptionRef, ownerRole, description, id));
            log.info("安全资产目录新增: key={}, kind={}, sensitivity={}, guard={}", assetKey, kind, sensitivity, guardCombo);
            return row;
        }

        String id = String.valueOf(existing.get("id"));
        jdbc.update(
                "UPDATE public.ecos_security_asset SET kind = ?, sensitivity = ?, guard_combo = ?, "
                        + "exemption_ref = ?, owner_role = ?, description = ?, update_time = NOW(), update_by = 'api', "
                        + "version_no = CAST(CAST(version_no AS INT) + 1 AS VARCHAR) "
                        + "WHERE id = ?",
                kind, sensitivity, guardCombo, exemptionRef, ownerRole, description, id);
        Map<String, Object> row = new LinkedHashMap<>(existing);
        row.put("kind", kind);
        row.put("sensitivity", sensitivity);
        row.put("guard_combo", guardCombo);
        row.put("exemption_ref", exemptionRef);
        row.put("owner_role", ownerRole);
        row.put("description", description);
        log.info("安全资产目录更新: key={}, version+1", assetKey);
        return row;
    }

    /** 逻辑删除（只加不删 → is_deleted=1）。 */
    public boolean logicalDelete(String assetKey) {
        Map<String, Object> existing = findByAssetKey(assetKey);
        if (existing == null) return false;
        int rows = jdbc.update("UPDATE public.ecos_security_asset SET is_deleted = 1, "
                + "update_time = NOW(), update_by = 'api' WHERE id = ?",
                existing.get("id"));
        return rows > 0;
    }

    /** 供 upsert 新建路径返回的行快照。 */
    private static Map<String, Object> freshRow(String assetKey, String kind, String sensitivity,
                                                     String guard, String exemptionRef, String ownerRole,
                                                     String description, String id) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("asset_key", assetKey);
        r.put("kind", kind);
        r.put("sensitivity", sensitivity);
        r.put("guard_combo", guard);
        r.put("exemption_ref", exemptionRef);
        r.put("owner_role", ownerRole);
        r.put("description", description);
        r.put("version_no", "1");
        r.put("is_deleted", 0);
        r.put("domain", "default");
        return r;
    }

    /** 参数校验工具：暴露给测试无需反射。 */
    public static boolean isLegalGuardCombo(String combo) {
        return combo != null && GUARD_COMBO.matcher(combo).matches();
    }

    public static boolean isLegalSensitivity(String level) {
        return level != null && SENSITIVITY.matcher(level).matches();
    }

    public static boolean isLegalKind(String kind) {
        return kind != null && KIND.matcher(kind).matches();
    }

    public static boolean isLegalAssetKey(String key) {
        return key != null && ASSET_KEY.matcher(key).matches();
    }
}
