package com.chinacreator.gzcm.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import com.chinacreator.gzcm.common.security.anonymous.AnonymousEndpointRegistry;

/**
 * #140 门禁补实现批 · 子批 C-2 —— 本体域匿名面零例外门禁
 * （详设-03 §D.5 line 572-576 规格：新增本体端点<b>默认不得</b> ①进 {@code VersionPrefixRewriteFilter}
 * 映射白名单（除非需要 {@code /api/} 别名）②进 {@code SecurityConfig} permitAll ③进
 * {@code ClearanceInterceptor} 豁免清单；理由=本体是控制域语义源，{@code entity-mappings} 的调用方是
 * 同 JVM 引擎（kb），走服务身份（ADR-7 信任头 + 分册 00 W02 {@code HeaderAuthInterceptor}），<b>不是匿名</b>。
 * 设计给出的验收命令原文：{@code mvn -pl gateway -Dtest='OntologyAnonymousSurfaceTest'}）。
 *
 * <p><b>差距来源</b>：实现差距分析 §6.7 / §15.3 登记本类不存在（grep 零命中）。
 * 按 R9② 裁决「缺的门禁＝补代码实现」，本类即该门禁实现；断言对象是生产在位的三处单源
 * （{@link AnonymousEndpointRegistry} 展开集 + {@code VersionPrefixRewriteFilter} 三张静态表 +
 * {@code ClearanceInterceptor} 的委托接线），不是自造副本。</p>
 *
 * <p><b>本体业务路径集合的来源</b>：不写死一份人肉清单（会随 Controller 增删漂移），
 * 而是解析 {@code ontology-engine-impl/.../controller/} 全部类的<b>类级</b>
 * {@code @RequestMapping} 字面量导出（{@link #ontologyBusinessPrefixes()}）；
 * 解析器自身由 {@link #ontologyPrefixUniverseIsParsedAndNonTrivial()} 钉住规模与关键成员，
 * 防「解析出空集合 ⇒ 三条红线恒 PASS」这一本仓已登记的假绿形态。</p>
 *
 * <p><b>反假绿三把锁</b>：
 * <ol>
 *   <li>判定用生产函数本身（{@link AnonymousEndpointRegistry#isAnonymous}/{@link
 *       AnonymousEndpointRegistry#isAnonymousOrExempt}）+ 独立 {@link AntPathMatcher} 双通道，
 *       并以钉死的样本路径做<b>反向对照</b>（{@link #anonymousPredicateActuallyBites()}：
 *       同一条判定必须能认出 {@code /api/health} 是匿名面），否则"恒判非匿名"的实现也能让红线变绿；</li>
 *   <li>{@code VersionPrefixRewriteFilter} 条目总数钉死（25 条，与 route-rewrite-lint 口径同源），
 *       表被清空/挪走即变红；</li>
 *   <li>唯一豁免（glossary 遗留条目）附带<b>惰性自证</b>：断言其重写目标在全仓无任何 Controller 映射，
 *       即"该条目不产生任何匿名暴露面"是可跑事实而非口头理由。</li>
 * </ol>
 *
 * <p><b>钉死豁免（带理由，禁静默扩大）</b>：{@link #PINNED_INERT_REWRITE_ENTRIES} =
 * {@code /api/v1/glossary/ → /api/glossary/} 一条。它是 v1 时代遗留（术语 API 前缀），
 * 现网术语端点实际挂在 {@code /api/v1/ontology/glossary}（{@code GlossaryController:30}），
 * 故该条目<b>指向不存在的路径＝空转</b>，不放宽任何鉴权；已在实现差距分析登记为
 * 「随 route-rewrite-lint fail=6 存量裁决收口后删除」，本类把它钉成唯一例外并做惰性自证。</p>
 *
 * <p><b>不主张的范围</b>：本体 4 个 Controller 自带 {@code /api/ontology}、
 * {@code /api/ecos/ontology}、{@code /api/ecos/domains} 裸路径别名注解（Controller 层直挂，
 * 不经重写滤波器，仍受 {@code anyRequest().authenticated()} 约束）。本门禁主张的是
 * 「匿名面 / 豁免面 / 重写白名单」三处清单零本体业务条目，不主张"本域不存在 /api/ 别名形态"。</p>
 */
@DisplayName("#140/C-2：本体域匿名面零例外（permitAll·Clearance 豁免·重写白名单 三处清单）")
class OntologyAnonymousSurfaceTest {

    private static final String ONTOLOGY_CONTROLLER_REL =
            "engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/controller";
    private static final String REWRITE_FILTER_REL =
            "gateway/src/main/java/com/chinacreator/gzcm/gateway/filter/VersionPrefixRewriteFilter.java";
    private static final String CLEARANCE_INTERCEPTOR_REL =
            "services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/security/ClearanceInterceptor.java";

    /** 重写表条目总数基线（正向 10 + 反向精确 2 + 反向前缀 13 ⇒ 25，与 {@code route-rewrite-lint.ps1} 同口径）。 */
    private static final int PINNED_REWRITE_ENTRY_COUNT = 25;

    /** 唯一允许命中"本域语义前缀"的重写条目（遗留空转，理由见类注释）。 */
    private static final Set<String> PINNED_INERT_REWRITE_ENTRIES = Set.of(
            "/api/v1/glossary/ -> /api/glossary/");

    /** 术语族扩展判定集：Controller 派生前缀之外的本域语义段（glossary 在 v1 命名下与本域同源）。 */
    private static final Set<String> EXTRA_ONTOLOGY_SEMANTIC_PREFIXES = Set.of(
            "/api/v1/glossary", "/api/glossary");

    /** 反向对照样本：必须被生产判定函数认成匿名面（证明红线不是"恒判非匿名"）。 */
    private static final List<String> KNOWN_ANONYMOUS_SAMPLES = List.of(
            "/api/health", "/health", "/actuator/health", "/auth/ping", "/api/v1/auth/login", "/error");

    /** 反向对照样本：必须被认成"需认证"（证明判定函数对非匿名面同样有分辨力）。 */
    private static final List<String> KNOWN_PROTECTED_SAMPLES = List.of(
            "/api/v1/ontology/calibers/CAL-1", "/api/v1/ecos/ontology/entities",
            "/api/v1/workspace/scenarios", "/api/v1/datanet/metadata/columns");

    private final AntPathMatcher ant = new AntPathMatcher();

    // ═══════════════════════ ① 解析面自证 ═══════════════════════

    @Test
    @DisplayName("本体业务路径集 = 由 ontology-engine Controller 类级映射导出（规模与关键成员钉死，防空集合假绿）")
    void ontologyPrefixUniverseIsParsedAndNonTrivial() throws IOException {
        Set<String> prefixes = ontologyBusinessPrefixes();
        assertTrue(prefixes.size() >= 25,
                "本体 Controller 类级映射解析结果骤减（< 25 条）⇒ 解析器或目录布局失效，"
                        + "此时三条红线都可能变成恒真: 实扫=" + prefixes.size() + " " + prefixes);
        for (String must : List.of("/api/v1/ontology", "/api/v1/ontology/glossary",
                "/api/v1/ontology/calibers", "/api/v1/ecos/ontology", "/api/v1/engine/ontology")) {
            assertTrue(prefixes.contains(must), "解析结果丢失关键本体前缀 " + must + "，实扫=" + prefixes);
        }
    }

    @Test
    @DisplayName("匿名判定函数分辨力自证：同一条判定必须认得出真实匿名面样本（禁「恒不匿名」式假绿）")
    void anonymousPredicateActuallyBites() {
        for (String sample : KNOWN_ANONYMOUS_SAMPLES) {
            assertTrue(AnonymousEndpointRegistry.isAnonymousOrExempt(sample),
                    "判定函数失去对真实匿名面的识别力（" + sample + "）⇒ 本门禁的红线断言不可信");
        }
        for (String sample : KNOWN_PROTECTED_SAMPLES) {
            assertFalse(AnonymousEndpointRegistry.isAnonymousOrExempt(sample),
                    "判定函数把受保护面误判为匿名（" + sample + "）：这是既有基线事实，若变即说明 registry 被扩");
        }
    }

    // ═══════════════════════ ② 规格三处清单红线 ═══════════════════════

    @Test
    @DisplayName("红线②：本体业务路径零条进 permitAll（生产判定 + AntPathMatcher 双通道，含 /api/ 别名形态）")
    void noOntologyPathIsPermitAll() throws IOException {
        List<String> permitAll = AnonymousEndpointRegistry.permitAllPatterns();
        assertFalse(permitAll.isEmpty(), "permitAll 展开集为空 ⇒ 载体失效，PASS 不可信");
        List<String> offenders = new ArrayList<>();
        for (String prefix : allOntologyPrefixes()) {
            for (String probe : probes(prefix)) {
                if (AnonymousEndpointRegistry.isAnonymous(probe)) {
                    offenders.add("isAnonymous=" + probe);
                }
                for (String pattern : permitAll) {
                    if (ant.match(pattern, probe)) offenders.add(pattern + " 命中 " + probe);
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "详设-03 §D.5 红线②：本体是控制域语义源，其业务端点不得进 SecurityConfig permitAll。"
                        + "违例:\n  " + String.join("\n  ", offenders)
                        + "\n本域调用方是 kb/cognitive 同 JVM 引擎，须走服务身份（ADR-7 信任头 + W02 HeaderAuthInterceptor）。");
        // 清单文本层再加一道关键字锁：任何人往 registry 塞 ontology/glossary/caliber 字样都会在这里变红
        for (String pattern : permitAll) {
            String lower = pattern.toLowerCase();
            for (String keyword : List.of("ontology", "glossary", "caliber", "proposal", "metric_definition")) {
                assertFalse(lower.contains(keyword),
                        "permitAll 出现本域语义关键字: " + pattern + "（含 " + keyword + "）");
            }
        }
    }

    @Test
    @DisplayName("红线③：Clearance 豁免面零本体路径 + 豁免来源必须是 registry 单源（禁回退内联 startsWith 清单）")
    void clearanceExemptionSurfaceStaysRegistryDriven() throws IOException {
        List<String> exempt = AnonymousEndpointRegistry.clearanceExemptPatterns();
        assertFalse(exempt.isEmpty(), "CLEARANCE_EXEMPT 展开集为空 ⇒ 载体失效，PASS 不可信");
        for (String pattern : exempt) {
            String lower = pattern.toLowerCase();
            assertFalse(lower.contains("ontology") || lower.contains("glossary") || lower.contains("caliber"),
                    "Clearance 豁免面出现本域路径: " + pattern);
            for (String prefix : allOntologyPrefixes()) {
                assertFalse(ant.match(pattern, prefix + "/sample"),
                        "Clearance 豁免 pattern " + pattern + " 覆盖本体业务路径 " + prefix);
            }
        }
        // 接线钉：拦截器必须仍委托 registry（内联清单一旦回归，本门禁的 registry 断言就失去意义）
        String interceptor = read(repoRoot().resolve("ecos_backend").resolve(CLEARANCE_INTERCEPTOR_REL));
        assertTrue(interceptor.contains("AnonymousEndpointRegistry.isAnonymousOrExempt"),
                "ClearanceInterceptor 丢失 registry 委托接线 ⇒ 豁免面重新变成可内联扩的暗面");
        assertTrue(java.util.regex.Pattern.compile("\\{\\s*\"/api/v1/\",\\s*\"1\"\\s*\\}")
                        .matcher(interceptor).find(),
                "ClearanceInterceptor 的 /api/v1/ 兜底等级规则消失 ⇒ 本域端点可能落到「无规则即放行」分支");
    }

    @Test
    @DisplayName("红线①：重写三表零本体业务条目（唯一豁免=glossary 遗留空转条目，并自证其目标无 Controller）")
    void rewriteFilterWhitelistCarriesNoOntologyBusinessEntry() throws IOException {
        String filter = stripComments(read(repoRoot().resolve("ecos_backend").resolve(REWRITE_FILTER_REL)));
        List<String[]> entries = rewriteEntries(filter);
        assertEquals(PINNED_REWRITE_ENTRY_COUNT, entries.size(),
                "VersionPrefixRewriteFilter 条目总数与基线不符（正向 10 + 反向 15 = 25）："
                        + "表被清空会让本红线恒真，故先钉规模");

        Set<String> prefixSet = allOntologyPrefixes();
        List<String> hits = new ArrayList<>();
        for (String[] e : entries) {
            for (String side : e) {
                for (String prefix : prefixSet) {
                    if (side.startsWith(prefix)) {
                        hits.add(e[0] + " -> " + e[1] + "（命中 " + prefix + "）");
                        break;
                    }
                }
            }
        }
        Set<String> hitKeys = new TreeSet<>();
        for (String h : hits) {
            int idx = h.indexOf("（命中");
            hitKeys.add(h.substring(0, idx).replace(" -> ", " -> "));
        }
        assertEquals(PINNED_INERT_REWRITE_ENTRIES, hitKeys,
                "重写白名单出现未登记豁免的本体条目（违 §D.5 红线①）: " + hits);

        // 豁免惰性自证：/api/glossary 与 /api/v1/glossary 在全仓无任何 Controller 映射 ⇒ 该条目不产生暴露面
        List<String> glossaryServers = controllersMappingTo("/api/glossary");
        assertTrue(glossaryServers.isEmpty(),
                "glossary 遗留豁免条目开始产生真实路由（本门禁的「空转不放宽鉴权」前提失效）: " + glossaryServers);
    }

    // ═══════════════════════ 解析辅助 ═══════════════════════

    /** 全部本域语义前缀 = Controller 类级映射导出 ∪ 显式登记扩展集。 */
    private Set<String> allOntologyPrefixes() throws IOException {
        Set<String> out = new LinkedHashSet<>(ontologyBusinessPrefixes());
        out.addAll(EXTRA_ONTOLOGY_SEMANTIC_PREFIXES);
        return out;
    }

    /** 解析 ontology-engine Controller 目录的类级 {@code @RequestMapping} 字面量（数组形态逐条取）。 */
    private static Set<String> ontologyBusinessPrefixes() throws IOException {
        Path dir = repoRoot().resolve("ecos_backend").resolve(ONTOLOGY_CONTROLLER_REL);
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.getFileName().toString().endsWith(".java")).sorted().toList();
        }
        assertTrue(files.size() >= 20, "本体 Controller 目录文件数异常（< 20）: " + files.size());
        Pattern mapping = Pattern.compile(
                "@RequestMapping\\s*\\(\\s*(?:value\\s*=\\s*)?(\\{[^}]*\\}|\"[^\"]*\")",
                Pattern.DOTALL);
        Set<String> out = new LinkedHashSet<>();
        for (Path f : files) {
            String src = stripComments(Files.readString(f, StandardCharsets.UTF_8));
            int classIdx = src.indexOf("\nclass ");
            if (classIdx < 0) classIdx = src.indexOf("public class");
            String head = classIdx < 0 ? src : src.substring(0, classIdx);
            Matcher m = mapping.matcher(head);
            while (m.find()) {
                Matcher lit = Pattern.compile("\"([^\"]+)\"").matcher(m.group(1));
                while (lit.find()) {
                    String path = lit.group(1).trim();
                    if (path.isEmpty() || "/".equals(path)) continue;
                    out.add(path.endsWith("/") ? path.substring(0, path.length() - 1) : path);
                }
            }
        }
        return out;
    }

    /** 探测形态：裸前缀、一段资源、两段资源（覆盖 {@code /**} 与 {@code /*} 两类 pattern）。 */
    private static List<String> probes(String prefix) {
        return List.of(prefix, prefix + "/sample", prefix + "/sample/deep");
    }

    /** 抽取重写三表的 {@code Map.entry("k","v")} 对（正向 + 反向精确 + 反向前缀）。 */
    private static List<String[]> rewriteEntries(String filterSrc) {
        List<String[]> out = new ArrayList<>();
        Matcher m = Pattern.compile("Map\\.entry\\(\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*\\)")
                .matcher(filterSrc);
        while (m.find()) out.add(new String[] {m.group(1), m.group(2)});
        return out;
    }

    /** 全仓（后端主源码）里类级映射覆盖到给定路径的 Controller 文件名。 */
    private static List<String> controllersMappingTo(String path) throws IOException {
        Path root = repoRoot().resolve("ecos_backend");
        List<String> hits = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root)) {
            List<Path> files = s.filter(p -> p.getFileName().toString().endsWith("Controller.java"))
                    .filter(p -> !p.toString().contains("\\target\\") && !p.toString().contains("/target/"))
                    .filter(p -> p.toString().contains("src" + java.io.File.separator + "main"))
                    .toList();
            for (Path f : files) {
                String src = stripComments(Files.readString(f, StandardCharsets.UTF_8));
                Matcher lit = Pattern.compile("\"([^\"]+)\"").matcher(src);
                while (lit.find()) {
                    String v = lit.group(1);
                    if (v.equals(path) || v.equals(path + "/") || (v.startsWith(path) && v.startsWith("/api"))) {
                        hits.add(f.getFileName() + " " + v);
                        break;
                    }
                }
            }
        }
        return hits;
    }

    private static String read(Path p) throws IOException {
        assertTrue(Files.isRegularFile(p), "门禁载体文件缺失: " + p);
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    private static Path repoRoot() throws IOException {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("ecos_backend"))
                    && Files.isDirectory(p.resolve("docs"))) return p;
            Path parent = p.getParent();
            if (parent == null || parent.equals(p)) break;
            p = parent;
        }
        throw new IOException("cwd 未锚定 ECOS 仓库根（ecos_backend/ 与 docs/ 未同时命中）: " + p);
    }

    /**
     * 剥 Java 注释：<b>先截行尾 {@code //}</b> 再处理块注释 —— 顺序不能反：
     * 本仓注释里大量出现 {@code /api/v1/pipeline/**} 形态，若先找块注释起始，
     * {@code /**} 会被误判成块注释起点并吞掉后续真实条目（首跑实测吞 10 条 ⇒ 条目数假小）。
     * 本门禁只扫重写表与拦截器接线，两处均无 {@code "//"} 形态字面量（路径不含双斜杠），
     * 故先截行注释不会误伤字符串。
     */
    private static String stripComments(String src) {
        StringBuilder sb = new StringBuilder();
        boolean inBlock = false;
        for (String line : src.split("\r?\n")) {
            String cur = line;
            if (inBlock) {
                int end = cur.indexOf("*/");
                if (end < 0) {
                    sb.append('\n');
                    continue;
                }
                inBlock = false;
                cur = cur.substring(end + 2);
            }
            int slashSlash = cur.indexOf("//");
            boolean lineCommented = slashSlash >= 0;
            if (lineCommented) cur = cur.substring(0, slashSlash);
            while (true) {
                int b = cur.indexOf("/*");
                if (b < 0) break;
                int e = cur.indexOf("*/", b + 2);
                if (e < 0) {
                    cur = cur.substring(0, b);
                    inBlock = true;
                    break;
                }
                cur = cur.substring(0, b) + cur.substring(e + 2);
            }
            sb.append(cur).append('\n');
        }
        return sb.toString();
    }
}
