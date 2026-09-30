package com.chinacreator.gzcm.engine.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C.2.2（三滤波器）— 数据域端点在 gateway 侧的安全姿态：
 * (a) 不在 {@code SecurityConfig.permitAll} 匿名白名单（默认 DENY）；
 * (b) 不在 {@code ClearanceInterceptor} 豁免表（数据域全部要求认证 + 准入等级）。
 *
 * <p>现状两个真源：
 * <ul>
 *   <li>{@code SecurityConfig}（sysman-impl）：{@code .requestMatchers(permitAll).permitAll()}，
 *       数据来 {@code AnonymousEndpointRegistry.permitAllPatterns()}</li>
 *   <li>{@code AnonymousEndpointRegistry}：C.3.2 W05 单源；{@code ANONYMOUS} 段进 permitAll、
 *       {@code CLEARANCE_EXEMPT} 段进 ClearanceInterceptor 豁免</li>
 * </ul>
 *
 * <p>本测试覆盖 route-manifest 里数据域 5 条目标前缀，断言无一落在上述两个
 * 匿名/豁免白名单内（点小目标 anonymous 覆不允许；是 anti-test for W05 单源泄漏）。
 * 健康检查前缀 /api/v1/engine/{name}/health 允许豁免（治理架构已登记），
 * 但业务前缀（datanet-dq-facts-write-channel）不允许。
 *
 * (End-of-class comment opened by "Multi-Engine Configuration Note" --
 * the blanket rule of C.2.2 clause-2 and clause-3 is enforced here to be
 * "DENY by default for business paths, health only is exempt".)
 */
@DisplayName("C.2.2：数据域端点 none in SecurityConfig.permitAll / ClearanceInterceptor 豁免表")
class DataEndpointTriFilterTest {

    /** route-manifest 数据域目标前缀 + 数据域 controller 特有 base 抽象出的一批 key paths。 */
    private static final String[] DATA_DOMAIN_KEY_PATHS = {
            // 非健康检查业务路径 → 必须 <b>不在</b> permitAll / clearance-exempt
            "/api/v1/datanet/assets",
            "/api/v1/datanet/facts",             // L2 守护（F02-05 事实导入）
            "/api/v1/datanet/write-channel",     // C.3.6 写通道唯一入口
            "/api/v1/datanet/datasource",
            "/api/v1/datanet/metadata",
            "/api/v1/dq/rules",
            "/api/v1/dq/scores/system",
            "/api/v1/dq/work-orders",
            "/api/v1/engine/data/settings",      // L3 守护
            "/api/v1/engine/data/layers",
            "/api/v1/pipeline/debug/sessions",
    };

    /** 治理架构允许可豁免/匿名 的 <b>数据域内</b> path（只允许 health 腿）。 */
    private static final Set<String> ALLOWED_DATA_ANONYMOUS = Set.of(
            "/api/v1/engine/health",
            "/api/v1/engine/*/health");

    @Test
    @DisplayName("route-manifest 数据域 5 大目标前缀的 entry.anonymous 数组均空或仅含 health 腿")
    void dataDomainManifestEntriesHaveNoAnonymousLeak() throws IOException {
        Path manifest = repoRoot().resolve(
                "ecos_backend/gateway/src/main/resources/route-manifest.json");
        String text = Files.readString(manifest, StandardCharsets.UTF_8);

        // 抽数据域相关 prefix 的 "anonymous" 字段
        Set<String> leaks = new LinkedHashSet<>();
        String[] dataPrefixes = {
                "/api/v1/datanet", "/api/v1/engine/data", "/api/v1/pipeline",
                "/api/v1/dq", "/api/v1/datasource",
        };
        for (String pfx : dataPrefixes) {
            String[] anon = extractAnonymousArray(text, pfx);
            for (String a : anon) {
                boolean allowed = ALLOWED_DATA_ANONYMOUS.stream()
                        .anyMatch(p -> matchesPattern(p, a));
                if (!allowed) leaks.add(pfx + " → " + a);
            }
        }
        assertTrue(leaks.isEmpty(),
                "route-manifest 数据域 entry 的 anonymous 字段含业务路径（应默认 DENY）:\n"
                        + leaks.stream().sorted().map(s -> "  - " + s).reduce("", (a, b) -> a + "\n" + b));
    }

    @Test
    @DisplayName("SecurityConfig permitAll 白名单抽自 AnonymousEndpointRegistry；数据域业务 path 未在其中")
    void dataDomainNotInPermitAll() throws IOException {
        Path registry = repoRoot().resolve(
                "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/security/anonymous/AnonymousEndpointRegistry.java");
        assertTrue(Files.isRegularFile(registry), "AnonymousEndpointRegistry 缺: " + registry);
        String text = Files.readString(registry, StandardCharsets.UTF_8);

        List<String> leaks = new ArrayList<>();
        for (String path : DATA_DOMAIN_KEY_PATHS) {
            // 该 path 的 literal 或同源 pattern 出现在 registry ANONYMOUS 段（category=ANONYMOUS）
            String matchesAnonymous = anonymousPatternNear(text, path);
            if (matchesAnonymous != null) {
                leaks.add(path + "  →  " + matchesAnonymous);
            }
        }
        assertTrue(leaks.isEmpty(),
                "以下数据域 path 被登记为 ANONYMOUS（违反 C.2.2 §2 匿名面）:\n"
                        + leaks.stream().sorted().map(s -> "  - " + s).reduce("", (a, b) -> a + "\n" + b));
    }

    @Test
    @DisplayName("ClearanceInterceptor 豁免表不含数据域业务 path（facts L2 / settings L3 / dq L1 都受管控）")
    void dataDomainNotInClearanceExempt() throws IOException {
        Path registry = repoRoot().resolve(
                "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/security/anonymous/AnonymousEndpointRegistry.java");
        String text = Files.readString(registry, StandardCharsets.UTF_8);

        List<String> leaks = new ArrayList<>();
        for (String path : DATA_DOMAIN_KEY_PATHS) {
            // CLEARANCE_EXEMPT reg 行里包含 path 前缀 / pattern
            if (clearanceExemptPatternNear(text, path) != null) {
                leaks.add(path);
            }
        }
        assertTrue(leaks.isEmpty(),
                "以下数据域 path 被登记为 CLEARANCE_EXEMPT（应仍受 clearance 等级管控 L1~L3）:\n"
                        + leaks.stream().sorted().map(s -> "  - " + s).reduce("", (a, b) -> a + "\n" + b));
    }

    @Test
    @DisplayName("SecurityConfig 仍走 permitAllPatterns 单一真源（不到处字面铺 permitAll）")
    void securityConfigDrivenByRegistry() throws IOException {
        Path ssc = repoRoot().resolve(
                "ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/security/SecurityConfig.java");
        assertTrue(Files.isRegularFile(ssc), "SecurityConfig 缺: " + ssc);
        String text = Files.readString(ssc, StandardCharsets.UTF_8);
        assertTrue(text.contains("AnonymousEndpointRegistry"),
                "SecurityConfig filterChain MUST 引 AnonymousEndpointRegistry 单源");
        assertTrue(text.contains("permitAllPatterns") || text.contains("permitAll"),
                "SecurityConfig MUST 保留 permitAll 段");
    }

    // ─────────── helpers ───────────

    /** 从 manifest entry（regex “prefix": "<pfx>" 的同一 entry 行）抽 "anonymous": [...] 数组内容。 */
    static String[] extractAnonymousArray(String manifestText, String prefix) {
        // entry 是单行 JSON
        String line = locateEntryLine(manifestText, prefix);
        if (line == null) return new String[0];
        Matcher m = Pattern.compile("\"anonymous\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(line);
        if (!m.find()) return new String[0];
        String inner = m.group(1);
        Matcher lit = Pattern.compile("\"([^\"]+)\"").matcher(inner);
        List<String> out = new ArrayList<>();
        while (lit.find()) out.add(lit.group(1));
        return out.toArray(new String[0]);
    }

    private static String locateEntryLine(String manifest, String prefix) {
        for (String line : manifest.split("\\R")) {
            if (line.contains("\"" + prefix + "\"")) return line;
        }
        return null;
    }

    /** 简化 pattern 灵活匹配单段 * 多段 **。 */
    static boolean matchesPattern(String pattern, String path) {
        // strict：pattern 恰好等于 path 或 path 段前缀
        String p = pattern.replaceAll("/\\*\\*$", "").replaceAll("/\\*$", "");
        return path.equals(p) || path.startsWith(p + "/") || path.startsWith(p);
    }

    /** 若 text 里 ANONYMOUS category 的 reg() 行包含与 path 匹配的 pattern，返回该行；否则 null。 */
    static String anonymousPatternNear(String registryText, String path) {
        // 抓 reg("...path...", Category.ANONYMOUS, ...) 里的 pattern
        for (String line : registryText.split("\\R")) {
            if (!line.contains("ANONYMOUS")) continue;
            Matcher m = Pattern.compile("reg\\(\\s*\"([^\"]+)\"").matcher(line);
            if (m.find()) {
                if (matchesPattern(m.group(1), path)) return line.trim();
            }
        }
        return null;
    }

    /** 若 text 里 CLEARANCE_EXEMPT category 的 reg() 行包含与 path 匹配 pattern，返回该行；否则 null。 */
    static String clearanceExemptPatternNear(String registryText, String path) {
        for (String line : registryText.split("\\R")) {
            if (!line.contains("CLEARANCE_EXEMPT")) continue;
            Matcher m = Pattern.compile("reg\\(\\s*\"([^\"]+)\"").matcher(line);
            if (m.find()) {
                if (matchesPattern(m.group(1), path)) return line.trim();
            }
        }
        return null;
    }

    private static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path root = dir;
        while (root != null) {
            if (Files.isRegularFile(root.resolve(".git")) || Files.isDirectory(root.resolve(".git"))) {
                return root;
            }
            root = root.getParent();
        }
        Path cur = dir;
        while (cur != null) {
            if (Files.isDirectory(cur.resolve("ecos_backend"))) return cur;
            cur = cur.getParent();
        }
        throw new IllegalStateException("未找到 ECOS 仓库根（缺 .git）");
    }
}
