package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-06 F06-21-3 / 验收 —— AI 面"前端调用端点 ↔ 后端属主 mapping"完整性守卫。
 *
 * <p><b>可判化</b>：源码扫描（无 DB / 无 Spring / 无启动），把前端 {@code ecos_frontend/src}
 * 里所有 {@code /api/**} 字符串字面量（AI 域：agent / aip / cognitive / guardrail / oag /
 * knowledge / model / copilot 前缀）与后端 {@code @RequestMapping("/api/...")} 基础映射做差，
 * 断言差集为空 —— 每条前端死调用要么被"改指真端点"、要么被"删除"，不允许凭空指向不存在的端点
 * （X-77 全 9 条死调用只能这两处置）。
 *
 * <p><b>归一化</b>：前端请求先进 {@code VersionPrefixRewriteFilter}，其中
 * {@code /api/v1/agent-mesh/} → {@code /api/agent-mesh/}、
 * {@code /api/v1/agent-call/} → {@code /api/agent-call/}（v1→no-v1 正向重写）。本守卫把
 * 前端路径按这两条重写后的<b>落点</b>与后端基础 mapping 比对，避免把重写桥接的端点误判为死亡
 * （否则 {@code /api/v1/agent-mesh} 会与后端 {@code /api/agent-mesh} 假阳）。
 *
 * <p>基础 mapping 匹配取「base == path 或 path 以 base + '/' 开头（含 base 以 '/' 结尾的
 * 前缀匹配）」，即一个 Controller 前缀覆盖其全部子资源（含运行时拼接的模板路径）。
 *
 * <p>验收：
 * {@code mvn -f ecos_backend/pom.xml -pl gateway -am test
 * -Dtest='AiRouteIntegrityTest#everyFrontendAiPathHasOwningMapping'
 * -Dsurefire.failIfNoSpecifiedTests=false}
 */
class AiRouteIntegrityTest {

    /** AI 域前缀（前端路径首段命中任一即纳入本守卫）。 */
    private static final Pattern AI_PATH = Pattern.compile(
            "^/api/(v1/)?(agent|aip|cognitive|guardrail|oag|knowledge|model|copilot)");

    /** 前端字符串字面量 /api/... 抓取。 */
    private static final Pattern FE_LIT = Pattern.compile("['\"`](/api/[A-Za-z0-9_/-]+)");

    /** 后端 @RequestMapping 基础映射（含数组/ value= 多形态）。 */
    private static final Pattern BE_BASE = Pattern.compile(
            "@RequestMapping\\(?(?:value\\s*=\\s*)?(?:\\{([^}]+)\\}|\"([^\"]+)\")");

    /**
     * v1→no-v1 正向重写（镜像 {@code VersionPrefixRewriteFilter.V1_REWRITE_MAP} 中 AI 相关项）。
     * 命中时把前端路径锚定到服务端真实落点再比对。
     */
    private static final String[][] REWRITES = {
            {"/api/v1/agent-mesh/", "/api/agent-mesh/"},
            {"/api/v1/agent-call/", "/api/agent-call/"},
    };

    /** 后端源码扫描范围（根聚合目录下含 Controller 的顶层模块）。 */
    private static final String[] BE_MODULES = {"engine", "services", "runtime", "workspace", "gateway"};

    // ═══════════════════════════════════════════════════
    //  验收 1：每条前端 AI 端点都有后端属主 mapping
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("F06-21-3：前端每条 AI 端点字符串都有后端 @RequestMapping 属主（差集为空）")
    void everyFrontendAiPathHasOwningMapping() throws IOException {
        Path be = backendRoot();
        Path fe = be.toAbsolutePath().getParent().resolve("ecos_frontend/src");
        assertTrue(Files.isDirectory(fe), "前端 src/ 缺失: " + fe);

        Set<String> fePaths = scanFePaths(fe);
        Set<String> beBases = scanBeBases(be);
        assertFalse(fePaths.isEmpty(), "前端未扫到任何 /api/** 字面量（解析异常？）");
        assertFalse(beBases.isEmpty(), "后端未扫到任何 @RequestMapping 基础映射（解析异常？）");

        List<String> orphans = new ArrayList<>();
        for (String p : new TreeSet<>(fePaths)) {
            if (!owned(p, beBases)) {
                orphans.add(p);
            }
        }
        assertTrue(orphans.isEmpty(),
                "以下前端 AI 端点无后端属主 mapping（X-77 死调用：应改指真端点或删除）：\n  "
                        + String.join("\n  ", orphans));
    }

    // ═══════════════════════════════════════════════════
    //  验收 2：会话列表端点在 /api/v1/ 与 /api/ 双前缀都存在
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("F06-21-3/X-77①：GET /api/v1/agent-loop/sessions 端点在后端真实存在（承接会话列表死调用）")
    void sessionsListEndpointExistsInBothPrefixes() throws IOException {
        Path be = backendRoot();

        // AgentLoopController 类级 @RequestMapping("/api/v1/agent-loop")
        boolean classBase = hasBaseMapping(be, "/api/v1/agent-loop");
        assertTrue(classBase,
                "AgentLoopController 类级 @RequestMapping(\"/api/v1/agent-loop\") 缺失 —— "
                        + "会话列表端点失去属主前缀");

        // 方法级 @GetMapping("/sessions") 挂在上述类级前缀下 → 组合成 /api/v1/agent-loop/sessions
        boolean methodMapping = hasGetMappingOnSession(be);
        assertTrue(methodMapping,
                "@GetMapping(\"/sessions\") 缺失 —— GET /api/v1/agent-loop/sessions 未实现");
    }

    // ── 扫描 ──────────────────────────────────────────

    private Set<String> scanFePaths(Path fe) throws IOException {
        Set<String> out = new LinkedHashSet<>();
        try (Stream<Path> s = Files.walk(fe)) {
            s.filter(p -> p.toString().endsWith(".ts") || p.toString().endsWith(".tsx"))
             .forEach(p -> {
                 String t = read(p);
                 Matcher m = FE_LIT.matcher(t);
                 while (m.find()) {
                     String lit = m.group(1);
                     if (AI_PATH.matcher(lit).matches()) out.add(lit);
                 }
             });
        }
        return out;
    }

    private static Set<String> scanBeBases(Path be) throws IOException {
        Set<String> out = new LinkedHashSet<>();
        for (String mod : BE_MODULES) {
            Path d = be.resolve(mod);
            if (!Files.isDirectory(d)) continue;
            try (Stream<Path> s = Files.walk(d)) {
                s.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                    String t = read(p);
                    Matcher m = BE_BASE.matcher(t);
                    while (m.find()) {
                        String raw = m.group(1) != null ? m.group(1) : m.group(2);
                        if (raw == null) continue;
                        for (String q : raw.split(",")) {
                            String v = q.trim().replace("\"", "");
                            if (v.startsWith("/api")) out.add(v);
                        }
                    }
                });
            }
        }
        return out;
    }

    /** 端点归属判定：路径经 v1→no-v1 重写后的任一候选，被某个 base 精确/前缀覆盖即归属。 */
    private static boolean owned(String path, Set<String> bases) {
        for (String cand : rewrittenCandidates(path)) {
            for (String base : bases) {
                if (cand.equals(base)
                        || cand.startsWith(base + "/")
                        || (base.endsWith("/") && cand.startsWith(base))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> rewrittenCandidates(String path) {
        Set<String> s = new LinkedHashSet<>();
        s.add(path);
        for (String[] rw : REWRITES) {
            for (String q : new ArrayList<>(s)) {
                if (q.equals(rw[0]) || q.startsWith(rw[0])) s.add(rw[1] + q.substring(rw[0].length()));
            }
        }
        return new ArrayList<>(s);
    }

    private static boolean hasBaseMapping(Path be, String exact) throws IOException {
        Set<String> bases = scanBeBases(be);
        return bases.contains(exact);
    }

    private static boolean hasGetMappingOnSession(Path be) throws IOException {
        Path p = be.resolve("engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/controller/AgentLoopController.java");
        if (!Files.exists(p)) return false;
        String t = read(p);
        return t.contains("@GetMapping(\"/sessions\")");
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new RuntimeException("读文件失败: " + p, e);
        }
    }

    private static Path backendRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("ecos_backend"))) return p.resolve("ecos_backend");
            Path parent = p.getParent();
            if (parent == null || parent.equals(p)) break;
            p = parent;
        }
        throw new IllegalStateException("cwd 未锚定 ECOS 仓库根（找不到 ecos_backend/）");
    }
}
