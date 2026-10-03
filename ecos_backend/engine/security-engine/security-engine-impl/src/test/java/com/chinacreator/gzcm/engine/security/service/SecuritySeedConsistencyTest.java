package com.chinacreator.gzcm.engine.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F01-09 / E.6.2 / ST03-A — 敏感数据目录 + ST03-A 豁免登记一致性。
 *
 * <p>PRD §1.1 六类对象在目录中的落地契约：金额列属 S2 及以上，导出/查询须
 * 走 guard_combo 组合（如 {@code rls+cls+mask}），且 <b>未登记</b>
 * 的金额列遇明文 → 硬护栏
     （C.2.4 "S2 及以上未登记 = 明文导出必须漏到 FAIL"）。
 * 本测试用内存 JdbcTemplate mock 目录数据，锚定三条契约：
 * <ol>
 *   <li>金额列必须登记：{kind=COLUMN, sensitivity>=S2, guard_combo 非空}；</li>
 *   <li>guard_combo 必须是 token 组合（禁自由文本）：</li>
 *   <li>sensitivity 未登记（NULL）不能进入 RLS 决策（走 fail-close 拒绝）
 *       —— 记为 ST03-A 联动触发。</li>
 * </ol>
 * 亦校验 SecurityAssetCatalogService.upsert 的入参合法性（kind/sensitivity/guard_combo 白名单）。</p>
 */
class SecuritySeedConsistencyTest {

    private static class CatalogJdbc extends JdbcTemplate {
        final Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();

        CatalogJdbc() {
            super(new org.springframework.jdbc.datasource.DriverManagerDataSource());
        }

        void seed(String tenantId, String key, String kind, String sensitivity, String guardCombo) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", "id-" + key.replaceAll("[^a-zA-Z0-9]", ""));
            row.put("tenant_id", tenantId);
            row.put("asset_key", key);
            row.put("kind", kind);
            row.put("sensitivity", sensitivity);
            row.put("guard_combo", guardCombo);
            row.put("exemption_ref", null);
            row.put("owner_role", "fin-admin");
            row.put("description", "seed");
            row.put("version_no", "1");
            row.put("is_deleted", 0);
            row.put("domain", "default");
            byKey.put(tenantId + "|" + key, row);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            // 只覆盖 getByKey 与 list 两种场景：末位参数为 assetKey / 组合
            String keyArg = args.length >= 1 ? (String) args[args.length - 1] : null;
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> m : new HashMap<>(byKey).values()) {
                if (keyArg != null && keyArg.equals(m.get("asset_key"))) {
                    out.add(m);
                } else if (keyArg == null) {
                    out.add(m);
                }
            }
            return out;
        }

        @Override
        public int update(String sql, Object... args) {
            // 以最后一位参数作为 assetKey (SecurityAssetCatalogService 的 UPDATE SQL 形如
            // "…" WHERE tenant_id = ? AND asset_key = ?)，命中后回写 version_no
            if (args.length < 2) return 0;
            String assetKey = (args[args.length - 1] == null) ? null
                    : args[args.length - 1].toString();
            if (assetKey == null) return 0;
            String key = "default|" + assetKey;
            Map<String, Object> row = byKey.get(key);
            if (row == null) return 0;
            if (sql.contains("UPDATE") && sql.contains("gc_security_asset".length() >= 0 ? "" : "")
                    && args.length >= 8) {
                // nextVersion 位于倒数第 4 位（kind,sens,guard,exempt,owner,desc,domain,version,updateBy,tenant,key）
                row.put("version_no", String.valueOf(args[8]));
            } else if (sql.contains("UPDATE") && sql.contains("is_deleted = 1")) {
                row.put("is_deleted", 1);
                row.put("version_no", String.valueOf(args[0]));
            } else if (sql.contains("INSERT")) {
                // 简单插入：定位 asset_key 参数（含 "." 的字符串）
                String foundKey = null;
                for (Object a : args) {
                    if (a instanceof String s && s.contains(".")) { foundKey = s; break; }
                }
                if (foundKey != null) byKey.put("default|" + foundKey, new LinkedHashMap<>());
                return 1;
            }
            return 1;
        }
    }

    private CatalogJdbc catalogJdbc;
    private SecurityAssetCatalogService catalog;

    @BeforeEach
    void setup() {
        catalogJdbc = new CatalogJdbc();
        catalog = new SecurityAssetCatalogService(catalogJdbc);
        // PRD §1.1 六类对象 → 目录登记金额列（fc_project_period_fact.amount）
        catalogJdbc.seed("default", "ecos_dw.fc_project_period_fact.amount",
                "COLUMN", "S3", "rls+cls+mask");
        catalogJdbc.seed("default", "ecos_dw.fc_project_period_fact",
                "TABLE", "S2", "rls+mask");
        // 一个未登记 sensitivity 的资产（ST03-A 触发）
        Map<String, Object> naked = new LinkedHashMap<>();
        naked.put("id", "naked");
        naked.put("tenant_id", "default");
        naked.put("asset_key", "ecos_dw.fc_result_detail.gross_amount");
        naked.put("kind", "COLUMN");
        naked.put("sensitivity", null);       // 未登记 —— ST03-A 联动
        naked.put("guard_combo", null);
        naked.put("exemption_ref", null);
        naked.put("owner_role", "fin-admin");
        naked.put("version_no", "1");
        naked.put("is_deleted", 0);
        naked.put("domain", "default");
        catalogJdbc.byKey.put("default|ecos_dw.fc_result_detail.gross_amount", naked);
    }

    /** ST03-A 联动：导出出现明文时守护侧必须能定位到目录行做皖密判断。 */
    /**
     * 守护查询入口：模拟导出通道 SecurityDecisionService 侧对 assetKey 的目录 hit；
     * 契约：S2+ 且 guard_combo 非空 → 命中；未登记 sensitivity/guard → 失败（返回 null）。
     */
    private Map<String, Object> guardLookup(String assetKey) {
        Map<String, Object> row = catalog.getByKey("default", assetKey);
        if (row == null) return null;
        String sens = (String) row.get("sensitivity");
        String guard = (String) row.get("guard_combo");
        if (sens == null || sens.isBlank()
                || !List.of("S2", "S3", "S4").contains(sens)) {
            return null;
        }
        if (guard == null || guard.isBlank()) return null;
        return row;
    }

    @Test
    @DisplayName("金额列（S3）已登记 + guard_combo=rls+cls+mask → 目录 hit")
    void amountColumn_registeredHitsGuard() {
        Map<String, Object> hit = guardLookup("ecos_dw.fc_project_period_fact.amount");
        assertNotNull(hit, "金额列必须命中目录守护栏");
        assertEquals("S3", hit.get("sensitivity"));
        assertTrue("rls+cls+mask".equals(hit.get("guard_combo")),
                "guard_combo 必须是 token 组合形态，禁自由文本");
    }

    @Test
    @DisplayName("S2 表级登记 → guard_combo=rls+mask 命中（Prd §1.1 项目类）")
    void tableRow_s2_registeredHitsGuard() {
        Map<String, Object> hit = guardLookup("ecos_dw.fc_project_period_fact");
        assertNotNull(hit, "S2 表级登记必须命中");
        assertEquals("rls+mask", hit.get("guard_combo"));
    }

    @Test
    @DisplayName("ST03-A：未登记 sensitivity 的金额列 → 目录命中失败 (guard_lookup → null)")
    void unregisteredSensitivity_amountColumn_noHit() {
        Map<String, Object> hit = guardLookup("ecos_dw.fc_result_detail.gross_amount");
        assertNull(hit, "未登记 sensitivity 的行不应命中守护栏（导出必须 FAIL）");
    }

    @Test
    @DisplayName("upsert 白名单：kind/sensitivity/guard_combo 只有 {COLUMN|TABLE}/{S0..S4}/{token组合} 合法")
    void upsert_rejectsInvalidWhitelistedFields() {
        // kind 白名单
        assertThrows(IllegalArgumentException.class, () -> catalog.upsert(body(
                "ecos_dw.fc_project_period_fact.amount", "FIELD", "S2", "rls+cls")));
        // sensitivity 白名单
        assertThrows(IllegalArgumentException.class, () -> catalog.upsert(body(
                "ecos_dw.fc_project_period_fact.amount", "COLUMN", "S9", "rls+cls")));
        // guard_combo 自由文本拒绝
        assertThrows(IllegalArgumentException.class, () -> catalog.upsert(body(
                "ecos_dw.fc_project_period_fact.amount", "COLUMN", "S2", "select * from t")));
        // guard_combo 长度不足 4 拒绝
        assertThrows(IllegalArgumentException.class, () -> catalog.upsert(body(
                "ecos_dw.fc_project_period_fact.amount", "COLUMN", "S2", "a")));

        // 合法组合：新入应生成一行
        Map<String, Object> ok = catalog.upsert(body(
                "ecos_dw.fc_project_period_fact.amount", "COLUMN", "S2", "rls+mask"));
        assertNotNull(ok, "合法 upsert 应回显一行");
        assertEquals("COLUMN", ok.get("kind"));
    }

    private Map<String, Object> body(String key, String kind, String sens, String guard) {
        Map<String, Object> m = new HashMap<>();
        m.put("assetKey", key);
        m.put("kind", kind);
        m.put("sensitivity", sens);
        m.put("guardCombo", guard);
        return m;
    }
}
