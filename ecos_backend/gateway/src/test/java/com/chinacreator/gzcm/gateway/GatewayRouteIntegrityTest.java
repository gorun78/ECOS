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
 * 卷07 F07-24（W188 / X-12）—— gateway 路由表<b>静态</b>完整性测试，去假阳性重写版。
 *
 * <p><b>本测试能证明什么（口径收紧）</b>：只做「制品口径」的事 ——
 * 源码里 {@code @*Mapping} 字面量构成的<b>声明路由表</b>的三件事：
 * <ol>
 *   <li>前缀归属：每条 TARGET_PREFIXES 是否有源码声明落在其下</li>
 *   <li>最长前缀优先：{@code /api/v1/ecos/git/x} 命中最长声明</li>
 *   <li>承载分散度：前缀由 ≥ 2 个 controller 文件承载</li>
 * </ol>
 *
 * <p><b>本测试不能证明什么（原文「非 404」断言已删除）</b>：
 * 类注释旧版自述「至少一条命中（≡ 非 404）」是本册最大的假阳性来源（X-12）：
 * 源码里存在 {@code /api/v1/workspace/**} 的 {@code @RequestMapping} 字面量，
 * 与「请求该路径得到非 404」之间隔着三件本测试看不见的事 ——
 * ① {@code GatewayApplication} 的 REGEX 排除 {@code workspace.controller..*}
 * 让 17 个 Controller 根本不进容器（ADR-15 制品口径 ≠ 承流口径，X-3）；
 * ② 双路径 rewrite（{@code VersionPrefixRewriteFilter}）；③ 安全过滤链放行与否。
 * 因此"命中即非 404"的措辞连同其含义一并移除，可达性由
 * {@code WorkspaceRouteReachabilityIT}（真容器 + {@code RequestMappingHandlerMapping}
 * 反射枚举）与 {@code WorkspaceTestHarnessSmokeTest#springContextBootsInBothShapes}
 * （薄切片量化两形态端点数）承担。</p>
 *
 * <p>反身门禁见 {@link #staticScanDoesNotClaimReachability()}：本类的源码本身
 * 被断言不含任何状态码断言与 HTTP 客户端，防止"日后又加了一行 200 断言"的回归。</p>
 *
 * @author PMO-A/B → 卷07 F07-24 重写
 */
public class GatewayRouteIntegrityTest {

    /** 仅用于「源码声明归属」检查的前缀，不代表任何运行期可达性结论。 */
    private static final List<String> TARGET_PREFIXES = List.of(
        "/api/dq",
        "/api/v1/ecos/git",
        "/api/datalake",
        "/api/v1/workspace",
        "/api/v1/cognitive"
    );

    private static final Pattern MappingPattern =
        Pattern.compile(
            "@(Request|Get|Post|Put|Delete|Patch)Mapping\\(\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);

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
                String literal = stripMappingPlaceholders(all.group(2));
                if (literal.isEmpty()) continue;
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

    /** {@code {"/a/b", "/c/d"}} 形式取首个能当路由用的字面量；全占位符则返回空。 */
    private static String stripMappingPlaceholders(String raw) {
        String s = raw.trim();
        if (s.startsWith("{")) {
            for (String part : s.split(",")) {
                String cand = part.trim().replaceAll("[{}\"\u2018\u2019]", "");
                if (cand.startsWith("/")) return cand;
            }
            return "";
        }
        return s.replaceAll("\"", "");
    }

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

    /** 断言 1：前缀归属（源码<b>声明</b>层）—— 仅结论"有没有人声明该前缀下的路由"。 */
    @Test
    void declaredPrefixOwnership() throws IOException {
        Map<String, String> table = scanRoutes();
        assertFalse(table.isEmpty(), "Route 字面量扫描为空 — controller 源码不在预期目录");

        Set<String> undeclared = new HashSet<>();
        for (String prefix : TARGET_PREFIXES) {
            boolean declared = table.keySet().stream()
                .anyMatch(route -> prefixCovers(route, prefix + "/x"));
            if (!declared) {
                undeclared.add(prefix);
            } else {
                String example = table.keySet().stream()
                    .filter(route -> prefixCovers(route, prefix + "/x"))
                    .sorted().findFirst().orElse("?");
                Set<String> controllers = table.keySet().stream()
                    .filter(route -> prefixCovers(route, prefix + "/x"))
                    .map(table::get).collect(Collectors.toSet());
                System.out.println("[F07-24 静态声明口径] " + prefix
                    + " → e.g. " + example + "  (源文件 " + controllers + ")"
                    + "；<b>不代表 gateway 承流可达</b>");
            }
        }
        assertTrue(undeclared.isEmpty(),
            "以下前缀在源码里无任何 @*Mapping 声明（连制品都没有）: " + undeclared);
    }

    /** 断言 2：/api/v1/ecos/git/x 命中 git 子路径而非被短 /api/v1/x 抢先（最长前缀优先，保留） */
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
        assertNotNull(match, "/api/v1/ecos/git/x 无任何声明路由覆盖");
        assertTrue(match.startsWith("/api/v1/ecos"),
            "Longest prefix 应命中 /api/v1/ecos/... 子路径，实际: " + match);
    }

    /** 断言 3：前缀由 ≥ 2 个不同 controller 文件承载（分散度，不 closet 到单文件） */
    @Test
    void allFivePrefixesUnderMultipleOwners() throws IOException {
        Map<String, String> table = scanRoutes();
        Set<String> distinct = new HashSet<>();
        for (String prefix : TARGET_PREFIXES) {
            table.keySet().stream()
                .filter(route -> prefixCovers(route, prefix + "/x"))
                .forEach(route -> distinct.add(table.get(route)));
        }
        System.out.println("[F07-24 静态声明口径] 5 前缀源文件分布: " + distinct);
        assertTrue(distinct.size() >= 2,
            "5 前缀应由 ≥ 2 个不同 controller 文件承载，实际: " + distinct);
    }

    /**
     * 反身门禁（F07-24 W188 要点 3）：本类源码<b>自身</b>禁止出现任何运行期可达性声称。
     *
     * <p>三条禁止项各对应一次真实过的假阳性形态：
     * <ol>
     *   <li>状态码断言（{@code assertEquals(200 …)} / {@code notEquals(404 …)} /
     *       {@code status().isOk()}）—— 静态扫描器没有 HTTP 客户端，出现即说明有人
     *       把"声明存在"改写成"请求得到"</li>
     *   <li>HTTP 客户端 / 容器启动 import（{@code TestRestTemplate}、{@code MockMvc}、
     *       {@code @SpringBootTest}）—— 出现即说明本类已被改造成 IT，
     *       应新建 {@code WorkspaceRouteReachabilityIT} 而非在此扩权</li>
     *   <li>「非 404」类措辞回到注释/断言文案里</li>
     * </ol>
     * 可达性声称的正解在 {@code WorkspaceTestHarnessSmokeTest}（薄切片、可跑）与
     * {@code WorkspaceRouteReachabilityIT}（真容器、待 P-2 活栈）。</p>
     */
    @Test
    void staticScanDoesNotClaimReachability() throws IOException {
        String self = ownSource();
        String code = codeOnly(self);

        List<Pattern> forbidden = List.of(
            Pattern.compile("assert\\w*\\s*\\(\\s*(200|404|503)\\b"),
            Pattern.compile("assertEquals\\s*\\(\\s*\\w*[Gg]etStatus\\(\\)"),
            Pattern.compile("\\bnotEquals\\s*\\(\\s*404"),
            Pattern.compile("status\\(\\s*\\)\\.is(Ok|NotFound|Forbidden|Unauthorized)\\("),
            Pattern.compile("TestRestTemplate|RestTemplate|WebClient|HttpClient"),
            Pattern.compile("@SpringBootTest|WebEnvironment\\."),
            Pattern.compile("非\\s*404")
        );
        for (Pattern p : forbidden) {
            Matcher m = p.matcher(code);
            boolean hit = m.find();
            assertFalse(hit,
                "GatewayRouteIntegrityTest 是本册的反面教材（X-12：源码 regex 扫到字面量"
                    + "即声称路由可用）。本类只允许静态声明口径断言，代码里禁止出现 "
                    + p + (hit ? "；实际命中: " + m.group() : "")
                    + "。可达性请走 WorkspaceRouteReachabilityIT / WorkspaceTestHarnessSmokeTest");
        }

        // 反向自证：本类确实在做静态扫描（禁被整体替换成空壳）
        assertTrue(self.contains("MappingPattern") && self.contains("scanRoutes"),
            "本类的静态声明扫描能力必须保留（最长前缀/归属/分散度三项）");
    }

    /**
     * 去掉字符串字面量与注释后的"纯代码"视图 —— 反身门禁只判代码，
     * 否则本类 javadoc 里那些"解释为什么禁 200/404 断言"的文字会自己触发规则。
     */
    private static String codeOnly(String source) {
        return source
            .replaceAll("\"(?:[^\"\\\\]|\\\\.)*\"", "\"\"")   // 字符串字面量
            .replaceAll("'(?:[^'\\\\]|\\\\.)*'", "")          // 字符字面量
            .replaceAll("(?m)//.*", "")                        // 行注释
            .replaceAll("(?s)/\\*.*?\\*/", "");                // 块/Javadoc 注释
    }

    /** 读本类自身源码：优先 {@code ./src/test/java/...}（mvn 模块目录为 cwd）。 */
    private static String ownSource() throws IOException {
        String rel = "src/test/java/com/chinacreator/gzcm/gateway/GatewayRouteIntegrityTest.java";
        Path direct = Paths.get(rel);
        if (Files.exists(direct)) return new String(Files.readAllBytes(direct), StandardCharsets.UTF_8);
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            Path cand = p.resolve("gateway").resolve(rel);
            if (Files.exists(cand)) return new String(Files.readAllBytes(cand), StandardCharsets.UTF_8);
            cand = p.resolve(rel);
            if (Files.exists(cand)) return new String(Files.readAllBytes(cand), StandardCharsets.UTF_8);
            p = p.getParent();
        }
        throw new IOException("找不到本类源文件，反身门禁无法自证: " + rel);
    }
}
