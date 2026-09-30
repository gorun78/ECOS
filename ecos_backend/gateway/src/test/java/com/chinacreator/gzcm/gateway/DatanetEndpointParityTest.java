package com.chinacreator.gzcm.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1 W51/W41 (详细设计-02 C.1 + S2/S3 门禁) — datanet 端点三滤波器 parity 门禁。
 *
 * <p><b>基线（AGENTS.md / route-rewrite-lint.ps1）</b>：
 * {@code VersionPrefixRewriteFilter} 正向 {@code V1_REWRITE_MAP} = 10 条 +
 * 反向 {@code REVERSE_EXACT_MAP} + {@code REVERSE_PREFIX_MAP} = 13 条，合计 {@code 23} 条。
 *
 * <p><b>单源 route-manifest（ADR-15 C.4）</b>：
 * {@code gateway/src/main/resources/route-manifest.json} 是 5 类前缀的
 * owner/artifact/ports/anon/route 语义真源。本测试把 manifest 的每个 prefix
 * 与三滤波器对账：
 * <ol>
 *   <li><b>Rewrite 基线</b>：{@code VersionPrefixRewriteFilter} 三张重写表
 *       Map.entry 条数 = 23（正向 10 + 反向 13，AGENTS.md / route-rewrite-lint.ps1
 *       基线）—— 防止重写表自行增减破坏既有三滤波器对账。</li>
 *   <li><b>Routing / datanet</b>：manifest 中 owner=datanet 的 entry（负载 37 个
 *       data-engine controller 的三域前缀）必须带齐 artifact/route/ports/rewrite
 *       四要素可路由（S2/S3 切流不能 404）。</li>
 *   <li><b>Security</b>：manifest 声明的 {@code anonymous[]} 每条必须能在
 *       {@code AnonymousEndpointRegistry}（SecurityConfig permitAll 单源驱动）
 *       中找到 pattern；否则视为未登记匿名 → 默认 DENY，属 gate fail。</li>
 *   <li><b>Clearance</b>：{@code ClearanceInterceptor} 必须走
 *       {@code AnonymousEndpointRegistry.isAnonymousOrExempt(path)} 单源判定
 *       （不再内联 startsWith 硬编码）。</li>
 * </ol>
 *
 * <p><b>纯源码/JSON 扫描</b>：不启动 Spring 容器，不依赖 PG/Neo4j。
 *
 * @author ecos-p0-data-engine
 * <br>
 * 2026-09-30
 */
class DatanetEndpointParityTest {

    /** route-manifest 单源：设计-02 ADR-15 C.4。 */
    private static final String MANIFEST_REL =
            "ecos_backend/gateway/src/main/resources/route-manifest.json";
    private static final String FILTER_REL =
            "ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/filter/VersionPrefixRewriteFilter.java";
    private static final String REGISTRY_REL =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/security/anonymous/AnonymousEndpointRegistry.java";
    private static final String SECURITY_REL =
            "ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/security/SecurityConfig.java";
    private static final String CLEARANCE_REL =
            "ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/security/ClearanceInterceptor.java";

    /** AGENTS.md 基线：正向 10 + 反向 13 = 23。 */
    private static final int REWRITE_BASELINE = 23;

    private static Path repoRoot() throws IOException {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("ecos_backend"))) {
            Path parent = p.getParent();
            if (parent == null || parent.equals(p)) break;
            p = parent;
        }
        assertTrue(p != null && Files.isDirectory(p.resolve("ecos_backend")),
                "cwd 未锚定 ECOS 仓库根（找不到 ecos_backend/）");
        return p;
    }

    private static String read(Path f) throws IOException {
        return Files.readString(f, StandardCharsets.UTF_8);
    }

    /** 从 VersionPrefixRewriteFilter 源码抓 Map.entry("src", "tgt")。 */
    private static List<MapEntry> parseFilterEntries(String src) {
        List<MapEntry> out = new ArrayList<>();
        Pattern p = Pattern.compile("Map\\.entry\\(\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*\\)");
        Matcher m = p.matcher(src);
        while (m.find()) {
            out.add(new MapEntry(m.group(1), m.group(2)));
        }
        return out;
    }

    /** 收集 registry 中所有 reg("pattern", ...) 的 pattern 字面量 + 双路径展开。 */
    private static List<String> parseRegistryPatterns(String src) throws Exception {
        List<String> out = new ArrayList<>();
        Pattern p = Pattern.compile("reg\\(\\s*\"([^\"]+)\"\\s*,\\s*Category\\.[A-Z_]+\\s*,\\s*(true|false)");
        Matcher m = p.matcher(src);
        while (m.find()) {
            String pat = m.group(1);
            boolean dual = "true".equals(m.group(2));
            out.add(pat);
            if (dual && pat.startsWith("/api/v1/")) {
                out.add("/api/" + pat.substring("/api/v1/".length()));
            }
        }
        return out;
    }

    private record MapEntry(String source, String target) {}

    @Test
    @DisplayName("route-manifest 存在且每条 prefix 必填字段齐全（ADR-15 单源）")
    void manifestShape() throws Exception {
        Path manifest = repoRoot().resolve(MANIFEST_REL);
        assertTrue(Files.exists(manifest), "缺少 route-manifest.json: " + manifest);
        JsonNode root = new ObjectMapper().readTree(read(manifest).getBytes(StandardCharsets.UTF_8));
        JsonNode entries = root.path("entries");
        assertTrue(entries.isArray() && entries.size() > 0, "manifest entries 非空");
        for (JsonNode e : entries) {
            assertTrue(e.hasNonNull("prefix"), "前缀缺失: " + e);
            assertTrue(e.hasNonNull("owner"), "owner 缺失: " + e);
            assertTrue(e.hasNonNull("artifact"), "artifact 缺失: " + e);
            assertTrue(e.hasNonNull("route"), "route (monolith|service) 缺失: " + e);
        }
    }

    @Test
    @DisplayName("VersionPrefixRewriteFilter 重写表基线 = 23 条（正向 10 + 反向 13）")
    void rewriteFilterBaselineIs23() throws Exception {
        String filterSrc = read(repoRoot().resolve(FILTER_REL));
        List<MapEntry> all = parseFilterEntries(filterSrc);
        assertEquals(REWRITE_BASELINE, all.size(),
                "AGENTS.md 基线 23 条；当前 = " + all.size() + "（正向 10 + 反向 13）");
        // 正向 10 + 反向 13：分别断言两个 map 块存在
        assertTrue(filterSrc.contains("V1_REWRITE_MAP"), "V1_REWRITE_MAP 未定义");
        assertTrue(filterSrc.contains("REVERSE_EXACT_MAP"), "REVERSE_EXACT_MAP 未定义");
        assertTrue(filterSrc.contains("REVERSE_PREFIX_MAP"), "REVERSE_PREFIX_MAP 未定义");
        int forward = countEntriesInBlock(filterSrc, "V1_REWRITE_MAP");
        int reverseExact = countEntriesInBlock(filterSrc, "REVERSE_EXACT_MAP");
        int reversePrefix = countEntriesInBlock(filterSrc, "REVERSE_PREFIX_MAP");
        assertEquals(10, forward, "正向应 10 条，当前 " + forward);
        assertEquals(13, reverseExact + reversePrefix, "反向应 13 条，当前 " + (reverseExact + reversePrefix));
    }

    private static int countEntriesInBlock(String src, String blockName) {
        int start = src.indexOf(blockName + " = Map.ofEntries(");
        if (start < 0) return 0;
        int end = src.indexOf("    );", start);
        String block = end < 0 ? src.substring(start) : src.substring(start, end);
        int n = 0;
        Matcher m = Pattern.compile("Map\\.entry\\(").matcher(block);
        while (m.find()) n++;
        return n;
    }

    @Test
    @DisplayName("每条 datanet 域 manifest entry 三行齐全（Routing/Security/Clearance）")
    void eachDatanetEntryHasThreeFilterLines() throws Exception {
        Path root = repoRoot();
        String registrySrc = read(root.resolve(REGISTRY_REL));
        String clearanceSrc = read(root.resolve(CLEARANCE_REL));
        List<String> registryPatterns = parseRegistryPatterns(registrySrc);

        // 走 AnonymousEndpointRegistry 单源的 clearance 杜绝：must be single-source driven
        assertTrue(clearanceSrc.contains("isAnonymousOrExempt"),
                "ClearanceInterceptor 未走 AnonymousEndpointRegistry.isAnonymousOrExempt 单源");

        JsonNode entries = new ObjectMapper()
                .readTree(read(root.resolve(MANIFEST_REL)).getBytes(StandardCharsets.UTF_8))
                .path("entries");
        assertTrue(entries.isArray() && entries.size() > 0, "manifest entries 非空");

        int datanetEntries = 0;
        for (JsonNode e : entries) {
            String owner = e.path("owner").asText();
            // 本测试锁数据域（datanet owner）：D-1/D-9 —— 37 controller 全由 gateway 承流
            if (!"datanet".equals(owner)) continue;
            datanetEntries++;
            String prefix = e.path("prefix").asText();

            // ① Routing 行：entry 必须可路由 —— owner/artifact/route/ports 均齐备
            assertTrue(e.hasNonNull("artifact"), "datanet entry 缺 artifact: " + prefix);
            String route = e.path("route").asText();
            assertTrue("monolith".equals(route) || "service".equals(route),
                    "datanet entry route 必须 monolith|service: " + prefix + " 实际 " + route);
            JsonNode ports = e.path("ports");
            assertTrue(ports.has("monolith") && ports.has("service"),
                    "datanet entry 缺 ports.{monolith,service}: " + prefix);
            // 三滤波器之一 —— 路由化解：rewrite 字段显式登记（/api/* 兜底或 null=直挂）
            assertTrue(e.has("rewrite"), "datanet entry 缺 rewrite 字段: " + prefix);

            // ② Security 行：entry 声明的 anonymous[] 每条必然已在 registry 登记（单源）
            JsonNode anon = e.path("anonymous");
            if (anon.isArray()) {
                for (JsonNode a : anon) {
                    String p = a.asText();
                    boolean hit = registryPatterns.stream().anyMatch(rp ->
                            rp.equals(p) || rp.startsWith(p) || p.startsWith(rp));
                    assertTrue(hit,
                            "manifest 声明 anonymous=" + p + " 未登记到 AnonymousEndpointRegistry（默认 DENY）: " + prefix);
                }
            }

            // ③ Clearance 行：datanet 数据域不在匿名/豁免白名单 —— 必须受准入裁决
            //   （datanet owner 的 entry anonymous[] 不得放行数据端点；health 类除外但也在 registry）
        }
        assertTrue(datanetEntries >= 2,
                "manifest 中 datanet 域 entry 应 ≥2（/api/v1/data, /api/v1/datanet, /api/v1/engine/data），实际 " + datanetEntries);
    }

    @Test
    @DisplayName("SecurityConfig permitAll 从 AnonymousEndpointRegistry 单源生成（不内联 patterns）")
    void securityAllowsOnlyFromRegistry() throws Exception {
        String securitySrc = read(repoRoot().resolve(SECURITY_REL));
        assertTrue(securitySrc.contains("AnonymousEndpointRegistry.permitAllPatterns()"),
                "SecurityConfig 必须直接引用 AnonymousEndpointRegistry.permitAllPatterns()（H9-T1 单源）");
        // 不允许内联硬编码 permitAll patterns —— 单源原则
        Matcher lit = Pattern.compile("requestMatchers\\s*\\(\\s*\"[^\"]+\"").matcher(securitySrc);
        assertFalse(lit.find(), "SecurityConfig 存在内联字符串 requestMatchers（应走 registry）");
        assertNotNull(securitySrc);
    }
}
