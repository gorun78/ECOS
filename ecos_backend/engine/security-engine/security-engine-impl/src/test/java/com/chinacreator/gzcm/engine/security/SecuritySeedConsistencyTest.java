package com.chinacreator.gzcm.engine.security;

import com.chinacreator.gzcm.engine.security.catalog.SecurityAssetCatalogService;
import com.chinacreator.gzcm.engine.security.rls.PredicateBinding;
import com.chinacreator.gzcm.engine.security.rls.RlsPredicateValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-01 F01-09 验收 —— 预测场景策略种子一致性门禁。
 *
 * <p>从 {@code gateway/.../db/migration/V246__security_policy_seed.sql} 用轻量 SQL 词法
 * 切分抽取每条 RLS INSERT 的 12 列值，断言：</p>
 * <ol>
 *   <li>参数化（{@code source_kind='parameterized'}）谓词 {@code predicate_template} 必须通过
 *       {@link RlsPredicateValidator}（SQL 关键字/拼接黑名单 + 占位符↔bindings 白名单一致）；</li>
 *   <li>legacy_text 谓词必须恰好 {@code 1=1}（唯一允许 1=1 的主体 = rls-finance-all）；</li>
 *   <li>{@code guard_combo} 值全部落在 {@link SecurityAssetCatalogService} 白名单；</li>
 *   <li>结构完整：asset/rls/cls 三类 seed + 幂等 ON CONFLICT。</li>
 * </ol>
 *
 * <p>V246 被越权改动（引入 UNION/DROP 字面量、偷放宽绑定 source、删除 bindings）即 FAIL。</p>
 */
class SecuritySeedConsistencyTest {

    @Test
    @DisplayName("V246 存在且含 rls/cls/asset 三类种子（幂等 ON CONFLICT）")
    void seedFileExistsWithAllKinds() throws IOException {
        Path seed = resolveSeedFile();
        assertNotNull(seed, "V246 必须落地（F01-09 M1 保底项）");
        String sql = Files.readString(seed);
        assertTrue(sql.contains("ecos_security_asset"), "V246 必须含敏感数据目录 seed");
        assertTrue(sql.contains("ecos_rls_policy"), "V246 必须含 RLS seed");
        assertTrue(sql.contains("ecos_cls_policy"), "V246 必须含 CLS seed");
        assertTrue(sql.contains("ON CONFLICT"), "所有 seed 必须幂等（ON CONFLICT DO NOTHING）");
    }

    @Test
    @DisplayName("guard_combo 值全部命中白名单 (rls|cls|mask, '+' 组合)")
    void guardComboTokensAllLegal() throws IOException {
        String sql = Files.readString(resolveSeedFile());
        Matcher m = Pattern.compile("'(rls|cls|mask)((\\+(rls|cls|mask))*)'").matcher(sql);
        int tokens = 0;
        while (m.find()) {
            String combo = m.group(1);
            assertTrue(SecurityAssetCatalogService.isLegalGuardCombo(combo),
                    "seed 中越位 guard_combo: " + combo);
            tokens++;
        }
        assertTrue(tokens > 0, "seed 中未找到任何 guard_combo token");
    }

    @Test
    @DisplayName("参数化 RLS 谓词通过白名单校验；legacy_text 只有 1=1（rls-finance-all）")
    void rlsSeedTemplatesPassWhitelist() throws IOException {
        String sql = Files.readString(resolveSeedFile());
        RlsPredicateValidator validator = new RlsPredicateValidator();
        int parameterized = 0;
        int legacyText = 0;
        for (List<String> vals : extractRlsValues(sql)) {
            // V246 rls 12 列: id, policy_name, table_name, filter_expr, role_id, priority,
            // enabled, description, predicate_template, bindings_json, source_kind, create_by
            if (vals.size() < 12) continue;
            String pred = vals.get(8);
            String bindingsJson = vals.get(9);
            String sourceKind = vals.get(10);
            if ("parameterized".equals(sourceKind)) {
                parameterized++;
                validator.validate(pred, parseBindings(bindingsJson));
            } else {
                legacyText++;
                assertTrue("1=1".equals(pred.trim()),
                        "legacy_text 谓词必须为 1=1（唯一允许）: " + pred);
            }
        }
        assertTrue(parameterized >= 6, "参数化 RLS 种子不足 6 条, 实际 " + parameterized);
        assertTrue(legacyText >= 3, "rls-finance-all(1=1) 种子不足 3 条, 实际 " + legacyText);
    }

    private List<List<String>> extractRlsValues(String sql) {
        List<List<String>> rows = new ArrayList<>();
        // 注：V246 冻结迁移脚本仍用 `public.ecos_rls_policy`（F01-09 单源 DDL，R1 不迁移冻结脚本），
        // 本扫描串必须与种子文件字面一致，勿随 V200 运行时 schema 改写。
        for (int s = 0; (s = sql.indexOf("INSERT INTO public.ecos_rls_policy", s)) >= 0; ) {
            int v = sql.indexOf("VALUES", s);
            if (v < 0) break;
            int open = sql.indexOf('(', v);
            if (open < 0) break;
            int close = matchingParen(sql, open);
            if (close < 0) break;
            List<String> vals = topLevelSplit(sql.substring(open + 1, close));
            if (vals.size() >= 12) rows.add(vals);
            s = close + 1;
        }
        return rows;
    }

    private int matchingParen(String sql, int start) {
        int depth = 0;
        boolean inStr = false;
        for (int i = start; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (inStr) {
                if (c == '\'') {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') i++;
                    else inStr = false;
                }
                continue;
            }
            if (c == '\'') inStr = true;
            else if (c == '(') depth++;
            else if (c == ')') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    private List<String> topLevelSplit(String inner) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        boolean inStr = false;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (inStr) {
                buf.append(c);
                if (c == '\'') {
                    if (i + 1 < inner.length() && inner.charAt(i + 1) == '\'') { buf.append('\''); i++; }
                    else inStr = false;
                }
                continue;
            }
            if (c == '\'') { inStr = true; buf.append(c); continue; }
            if (c == '(') depth++;
            else if (c == ')') depth--;
            if (c == ',' && depth == 0) { out.add(peel(buf.toString().trim())); buf.setLength(0); continue; }
            buf.append(c);
        }
        if (buf.length() > 0) out.add(peel(buf.toString().trim()));
        return out;
    }

    private static String peel(String s) {
        if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'")) {
            return s.substring(1, s.length() - 1).replace("''", "'");
        }
        return s;
    }

    private List<PredicateBinding> parseBindings(String json) {
        List<PredicateBinding> out = new ArrayList<>();
        Matcher m = Pattern.compile(
                "\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"source\"\\s*:\\s*\"([^\"]+)\"" +
                "(?:\\s*,\\s*\"value\"\\s*:\\s*([^,}]+))?\\s*\\}")
                .matcher(json);
        while (m.find()) {
            String raw = m.group(3);
            Object value = (raw == null || "null".equals(raw.trim())) ? null : raw.trim();
            out.add(new PredicateBinding(m.group(1), m.group(2), value));
        }
        return out;
    }

    private Path resolveSeedFile() throws IOException {
        Path cur = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && cur != null; i++) {
            // V200 治理后 DDL 单源目录（见数据库访问规范 v1.4 §八），优先新路径，旧 gateway 路径保留兼容
            Path canonical = cur.resolve("docs/50-db_script/02-flyway-migration/gateway/V246__security_policy_seed.sql");
            if (Files.isRegularFile(canonical)) return canonical;
            Path candidate = cur.resolve("gateway/src/main/resources/db/migration/V246__security_policy_seed.sql");
            if (Files.isRegularFile(candidate)) return candidate;
            Path candidate2 = cur.resolve("ecos_backend/gateway/src/main/resources/db/migration/V246__security_policy_seed.sql");
            if (Files.isRegularFile(candidate2)) return candidate2;
            Path direct = cur.resolve("V246__security_policy_seed.sql");
            if (Files.isRegularFile(direct)) return direct;
            cur = cur.getParent();
        }
        throw new IOException("未找到 V246__security_policy_seed.sql");
    }
}
