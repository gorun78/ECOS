package com.chinacreator.gzcm.engine.kb;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 知识域路由完整性测试（F04-01/F04-02 验收，无 Spring 上下文的源码级断言）。
 *
 * <p>四条断言逐条对治实测结构必败：
 * <ul>
 *   <li>{@link #noNestedApiPrefixInHandlerMapping()} — K-11：任何 kb Controller 方法级禁写
 *       全路径 {@code /api/v1/...}（类级前缀 + 方法级全路径会拼出二次 /api/ 的永不可达 URL）。
 *   <li>{@link #everyKnowledgeMappingHasV1AndBarePair()} — K-14：knowledge-bases 的
 *       v1↔bare 双路径须在 VersionPrefixRewriteFilter 成对存在（K-14 缺口）。
 *   <li>{@link #knowledgePrefixHasSingleOwningModule()} — K-13：gateway 侧同名
 *       {@code EcosKnowledgeGraphController} 死类应已删除；workspace 侧应保留旧
 *       {@code /api/v1/knowledge} 别名 + 新增 {@code /api/v1/scenarios/knowledge}。
 *   <li>{@link #devProxyTargetsKbPort()} — K-15：前端 vite dev 代理 knowledge 前缀必须指
 *       18086（kb 实端口），不得再写 18084（ai-engine 误指）。
 * </ul>
 *
 * <p>断言来源：全仓 tracked 文件（不走 {@code find} / 不稳定 glob）。仅依赖 JUnit5（本模块无 AssertJ）。
 *
 * @author ECOS KB Team
 */
class KnowledgeRouteIntegrityTest {

    /** kb Controller 目录（相对 kb-engine-impl 模块根，Maven test CWD = 模块根）。 */
    private static final Path KB_CONTROLLER_DIR =
            Path.of("src", "main", "java", "com", "chinacreator", "gzcm", "engine", "kb", "controller");

    private static final Pattern CLASS_MAPPING =
            Pattern.compile("@RequestMapping\\s*\\(([^)]*)\\)");

    private static final Pattern METHOD_MAPPING =
            Pattern.compile("@(Get|Post|Put|Delete|Patch)Mapping\\s*\\(([^)]*)\\)");

    @Test
    void noNestedApiPrefixInHandlerMapping() {
        List<String> violations = new ArrayList<>();
        for (Path file : listKbControllerSources()) {
            String src;
            try {
                src = Files.readString(file);
            } catch (IOException e) {
                violations.add(file.getFileName() + " 读取失败: " + e.getMessage());
                continue;
            }
            Matcher cm = CLASS_MAPPING.matcher(src);
            String classBase = cm.find() ? firstStringLiteral(cm.group(1)) : null;
            Matcher mm = METHOD_MAPPING.matcher(src);
            while (mm.find()) {
                String methodPath = firstStringLiteral(mm.group(2));
                if (methodPath == null) {
                    continue;
                }
                if (methodPath.startsWith("/api/v1/")) {
                    violations.add(file.getFileName() + " 方法级全路径: " + methodPath);
                    continue;
                }
                String joined = join(classBase, methodPath);
                if (joined != null && joined.contains("/api/") && joined.indexOf("/api/", joined.indexOf("/api/") + 4) >= 0) {
                    violations.add(file.getFileName() + " 拼出二次 /api/: " + joined);
                }
                if (joined != null && joined.contains("/settings/api/")) {
                    violations.add(file.getFileName() + " 类级+方法级嵌套 settings: " + joined);
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "F04-02 K-11 方法级禁全路径；类级+方法级拼接不得出现二次 /api/：\n" + String.join("\n", violations));
    }

    @Test
    void everyKnowledgeMappingHasV1AndBarePair() {
        Path filter = repoFile("ecos_backend", "gateway", "src", "main", "java",
                "com", "chinacreator", "gzcm", "gateway", "filter", "VersionPrefixRewriteFilter.java");
        String text = readFileOrFail(filter, "VersionPrefixRewriteFilter");
        // K-14（结构必败③）：`/api/v1/knowledge-bases` 缺 bare 侧映射。
        // 修复口径：REVERSE_EXACT_MAP 须含 ("/api/knowledge-bases" → "/api/v1/knowledge-bases")。
        // 不做正向 v1→bare 重写（KnowledgeListController 无 bare RequestMapping，正向会 404）。
        assertTrue(text.contains("/api/v1/knowledge-bases"), "filter 缺失 v1 侧 /api/v1/knowledge-bases");
        assertTrue(text.contains("/api/knowledge-bases"), "filter 缺失 bare 侧 /api/knowledge-bases（K-14）");
        // F04-01(收紧)：正向 V1_REWRITE_MAP 不得含 /api/v1/knowledge/ 条目（kb 全直映射 v1，正向→404）。
        assertFalse(Pattern.compile("Map\\.entry\\(\"/api/v1/knowledge/\"").matcher(text).find(),
                "正向 V1_REWRITE_MAP 不应含 /api/v1/knowledge/ 条目");
    }

    @Test
    void knowledgePrefixHasSingleOwningModule() {
        Path gatewayTwin = repoFile("ecos_backend", "gateway", "src", "main", "java",
                "com", "chinacreator", "gzcm", "gateway", "controller", "EcosKnowledgeGraphController.java");
        assertFalse(Files.exists(gatewayTwin),
                "K-13：gateway 侧同名 EcosKnowledgeGraphController 死类必须已删");

        Path wsCtrl = repoFile("ecos_backend", "workspace", "workspace-impl", "src", "main", "java",
                "com", "chinacreator", "gzcm", "workspace", "knowledge", "KnowledgeWorkbenchController.java");
        String ws = readFileOrFail(wsCtrl, "workspace KnowledgeWorkbenchController");
        assertTrue(ws.contains("/api/v1/scenarios/knowledge"),
                "F04-01 ‡：workspace 应新增 /api/v1/scenarios/knowledge 主路径");
        assertTrue(ws.contains("/api/v1/knowledge"),
                "F04-01 ‡：workspace 应保留旧 /api/v1/knowledge 别名（API 只增不改）");
    }

    @Test
    void devProxyTargetsKbPort() {
        Path vite = repoFile("ecos_frontend", "vite.config.ts");
        String text = readFileOrFail(vite, "ecos_frontend/vite.config.ts");
        Matcher m = Pattern.compile("'?/api/v1/knowledge'?[^}]*?target:\\s*'(http://[^']+)'").matcher(text);
        assertTrue(m.find(), "vite.config 应有 /api/v1/knowledge 直连条目（dev 双轨）");
        String target = m.group(1);
        assertTrue(target.contains(":18086"),
                "K-15：knowledge 前缀 dev 直连必须指 kb :18086，实测 target=" + target);
        assertFalse(text.matches("(?s).*'?/api/v1/knowledge'?[^}]*?18084.*"),
                "K-15 反向禁令：knowledge 前缀不得再指 18084（ai-engine 误指）");
    }

    // ───────────────────────── helpers ─────────────────────────

    private static Path repoFile(String... rel) {
        // 模块根(kb-engine-impl) → 上 4 层 = 仓库根 ECOS
        Path root = Path.of("").toAbsolutePath();
        for (int i = 0; i < 4; i++) {
            root = root.getParent();
        }
        Path p = Path.of(rel[0]);
        for (int i = 1; i < rel.length; i++) {
            p = p.resolve(rel[i]);
        }
        return root.resolve(p);
    }

    private List<Path> listKbControllerSources() {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(KB_CONTROLLER_DIR)) {
            return out;
        }
        try (var stream = Files.list(KB_CONTROLLER_DIR)) {
            stream.filter(p -> p.getFileName().toString().endsWith("Controller.java"))
                    .forEach(out::add);
        } catch (IOException ignored) {
            // 目录读取失败 → 空集，由其它断言入口兜底
        }
        return out;
    }

    private static String readFileOrFail(Path p, String desc) {
        if (!Files.exists(p)) {
            fail(desc + " 未找到: " + p.toAbsolutePath());
        }
        try {
            return Files.readString(p);
        } catch (IOException e) {
            fail("无法读 " + desc + ": " + e.getMessage());
            return ""; // unreachable
        }
    }

    private static String firstStringLiteral(String inner) {
        if (inner == null) {
            return null;
        }
        int q1 = inner.indexOf('"');
        if (q1 < 0) {
            return null;
        }
        int q2 = inner.indexOf('"', q1 + 1);
        if (q2 < 0) {
            return null;
        }
        return inner.substring(q1 + 1, q2);
    }

    private static String join(String a, String b) {
        if (a == null) {
            return b;
        }
        if (b == null || b.isEmpty()) {
            return a;
        }
        if (a.endsWith("/") && b.startsWith("/")) {
            return a + b.substring(1);
        }
        if (!a.endsWith("/") && !b.startsWith("/")) {
            return a + "/" + b;
        }
        return a + b;
    }
}
