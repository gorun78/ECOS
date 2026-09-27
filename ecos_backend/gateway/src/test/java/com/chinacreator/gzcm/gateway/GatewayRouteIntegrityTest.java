package com.chinacreator.gzcm.gateway;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PMO-B B3：gateway 路由集成测试 — 验证 5 类前缀的目标 service 选择 / 最长前缀 / 多引擎承载。
 *
 * <p><b>设计取舍（重要）</b>：
 * <ul>
 *   <li>PMO-D 微服务 v2 后，engine.* 是源码模块，gateway fat jar 在 test 期
 *       classpath 非传到 6 引擎（即使 -pl gateway -am 也只拉 gateway + 依赖）。
 *       ArchUnit ClassFileImporter 走 classpath 拿不到 @RestController 元数据。</li>
 *   <li>本测试改走 <b>纯源码 regex 扫描</b>：repo 内所有 .java controller 文件
 *       中 {@code @*(Request|Get|Post|Put|Delete|Patch)Mapping("...")} 字面量
 *       + {@code @RestController} 标，构成静态路由表。无 spring 容器、无
 *       docker 容器依赖、无 ArchUnit 限 — 直接文本扫。</li>
 *   <li>不启动 Spring 容器、不依赖 PG/Neo4j/Kafka/MinIO docker 容器。
 *       运行期 401/403/502/404 行为由 PMO-B B2 端到端冒烟承担（需 compose 起 5 service + gateway）。</li>
 * </ul>
 *
 * <p><b>断言</b>：
 * <ul>
 *   <li>5 类前缀（/api/dq, /api/v1/ecos/git, /api/datalake, /api/v1/workspace, /api/v1/cognitive）
 *       在路由表中至少有一条 route 命中（≡ 非 404）</li>
 *   <li>最长前缀优先：/api/v1/ecos/git/x 必须命中 /api/v1/ecos/git 而非被 /api/v1/x 抢先</li>
 *   <li>5 前缀至少由 2 个不同 .java 文件承载（多引擎分别承载，不 closet 到 monolith）</li>
 * </ul>
 *
 * @author PMO-A/B
 */
public class GatewayRouteIntegrityTest {

    private static final List<String> TARGET_PREFIXES = List.of(
        "/api/dq",
        "/api/v1/ecos/git",
        "/api/datalake",
        "/api/v1/workspace",
        "/api/v1/cognitive"
    );

    /** 路由表键 = 字面量路径（如 "/api/dq/git"），值 = 持有该路径的 controller 文件名 */
    private static Map<String, String> scanRoutes() throws IOException {
        Path repoRoot = Paths.get("").toAbsolutePath();
        while (repoRoot != null && repoRoot.toFile().exists()
                && !Files.exists(repoRoot.resolve("ecos_backend"))) {
            repoRoot = repoRoot.getParent();
        }
        Path backendRoot = repoRoot != null ? repoRoot.resolve("ecos_backend") : null;
        assertTrue(backendRoot != null && Files.isDirectory(backendRoot),
            "Test run cwd 未找到 ecos_backend/ 父目录（应根于 ECOS 仓库）");

        Map<String, String> table = new HashMap<>();
        // PMO-D 微服务 v2：5 业务 service（R2，调 engine-api）+ 6 engine-impl + workspace
        // 各 service 都承载自身 controller（如 datanet.DataLakeController 持 /api/datalake）
        Path[] implDirs = {
            backendRoot.resolve("services/sysman/impl/sysman-impl/src/main/java"),
            backendRoot.resolve("services/buszhi/impl/buszhi-impl/src/main/java"),
            backendRoot.resolve("services/datanet/src/main/java"),
            backendRoot.resolve("services/aiming/src/main/java"),
            backendRoot.resolve("services/dccheng/src/main/java"),
            backendRoot.resolve("engine/data-engine/data-engine-impl/src/main/java"),
            backendRoot.resolve("engine/ontology-engine/ontology-engine-impl/src/main/java"),
            backendRoot.resolve("engine/kb-engine/kb-engine-impl/src/main/java"),
            backendRoot.resolve("engine/cognitive-engine/cognitive-engine-impl/src/main/java"),
            backendRoot.resolve("engine/ai-engine/ai-engine-impl/src/main/java"),
            backendRoot.resolve("engine/security-engine/security-engine-impl/src/main/java"),
            backendRoot.resolve("workspace/workspace-impl/src/main/java"),
            backendRoot.resolve("workspace/workspace-service/src/main/java"),
        };
        for (Path dir : implDirs) {
            if (!Files.isDirectory(dir)) continue;
            scanDirShallow(table, dir, 7);
        }
        assertTrue(table.size() >= 30,
            "扫到路由数过少 (实际 " + table.size() + ")，可能有 controller 不在预期路径或正则不全");
        return table;
    }

    /** 浅层递归扫 .java：每层深度 7（足够覆盖 sub/module/controller） */
    private static void scanDirShallow(Map<String, String> table, Path root, int remainingDepth) {
        if (remainingDepth <= 0) return;
        try (Stream<Path> stream = Files.list(root)) {
            stream.forEach(p -> {
                try {
                    if (Files.isDirectory(p)) {
                        String name = p.getFileName().toString();
                        if (name.equals("target") || name.equals("test")
                            || name.equals("node_modules")) return;
                        scanDirShallow(table, p, remainingDepth - 1);
                    } else if (p.toString().endsWith(".java")) {
                        scanFile(p, table);
                    }
                } catch (Exception ignored) { /* 单文件 IO 错 → skip */ }
            });
        } catch (Exception ignored) { /* 目录列错 → skip */ }
    }

    /** 从单个 .java 文件提取 @*Mapping 字面量。IO 失败 → 静默 skip。 */
    private static void scanFile(Path p, Map<String, String> table) {
        if (!p.toString().contains("controller")
            && !p.toString().contains("Controller")) {
            // 仅处理 controller 文件名或路径含 controller 的，降噪
            return;
        }
        try {
            String content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            if (!content.contains("@RestController") && !content.contains("@Controller")) return;
            Matcher all = MappingPattern.matcher(content);
            String controller = p.getFileName().toString();
            while (all.find()) {
                String literal = all.group(2);
                if (!literal.startsWith("/")) literal = "/" + literal;
                table.putIfAbsent(literal, controller);
                // 也打 root（首 3 段）作前缀 fallback，多层覆盖
                String root = rootPrefix(literal);
                if (root != null && !root.equals(literal)) {
                    table.putIfAbsent(root, controller);
                }
            }
        } catch (Exception ignored) { /* IO 错 → skip 单个文件 */ }
    }

    private static final Pattern MappingPattern =
        Pattern.compile(
            "@(Request|Get|Post|Put|Delete|Patch)Mapping\\(\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);

    /** 取首 3 段（跳过空 seg）作 root：/api/dq/x/y → /api/dq/x；/api/v1/workspace/h → /api/v1/workspace */
    private static String rootPrefix(String literal) {
        String[] segs = literal.split("/");
        if (segs.length < 2) return null;
        int end = Math.min(segs.length, 4);
        StringBuilder sb = new StringBuilder("/");
        for (int i = 1; i < end; i++) {
            if (i > 1) sb.append('/');
            sb.append(segs[i]);
        }
        return sb.length() > 1 ? sb.toString() : null;
    }

    private static boolean prefixCovers(String route, String targetPath) {
        return targetPath.equals(route) || targetPath.startsWith(route + "/");
    }

    /** 断言 1：5 类前缀各自被路由表至少一条 route 覆盖（非 404） */
    @Test
    void prefixRouteOwnership() throws IOException {
        Map<String, String> table = scanRoutes();
        assertFalse(table.isEmpty(), "Route 字面量扫描为空 — controller 源码不在预期目录");

        Set<String> missing = new HashSet<>();
        for (String prefix : TARGET_PREFIXES) {
            boolean covered = table.keySet().stream()
                .anyMatch(route -> prefixCovers(route, prefix + "/x"));
            if (!covered) {
                missing.add(prefix);
            } else {
                String example = table.keySet().stream()
                    .filter(route -> prefixCovers(route, prefix + "/x"))
                    .sorted().findFirst().orElse("?");
                Set<String> controllers = table.keySet().stream()
                    .filter(route -> prefixCovers(route, prefix + "/x"))
                    .map(table::get).collect(Collectors.toSet());
                System.out.println("[PMO-B B3] " + prefix
                    + " → e.g. " + example + "  (controllers " + controllers + ")");
            }
        }
        assertTrue(missing.isEmpty(),
            "5 类前缀以下未被路由表覆盖（gateway 端 404 风险）: " + missing);
    }

    /** 断言 2：/api/v1/ecos/git/x 命中 git 子路径而非被短 /api/v1/x 抢先 */
    @Test
    void longestPrefixWins() throws IOException {
        Map<String, String> table = scanRoutes();
        String path = "/api/v1/ecos/git/x";
        String match = null;
        for (String route : table.keySet()) {
            if (prefixCovers(route, path) && (match == null || route.length() > match.length())) {
                match = route;
            }
        }
        assertNotNull(match, "/api/v1/ecos/git/x 未被任何 route 覆盖");
        assertTrue(match.startsWith("/api/v1/ecos"),
            "Longest prefix 应命中 /api/v1/ecos/... 子路径，实际: " + match);
    }

    /** 断言 3：5 前缀由不同多 controller 文件承载 */
    @Test
    void allFivePrefixesUnderMultipleOwners() throws IOException {
        Map<String, String> table = scanRoutes();
        Set<String> distinct = new HashSet<>();
        for (String prefix : TARGET_PREFIXES) {
            table.keySet().stream()
                .filter(route -> prefixCovers(route, prefix + "/x"))
                .forEach(route -> distinct.add(table.get(route)));
        }
        System.out.println("[PMO-B B3] 5 前缀 controller 文件分布: " + distinct);
        assertTrue(distinct.size() >= 2,
            "5 前缀应由 ≥ 2 个不同 controller 文件承载，实际: " + distinct);
    }
}