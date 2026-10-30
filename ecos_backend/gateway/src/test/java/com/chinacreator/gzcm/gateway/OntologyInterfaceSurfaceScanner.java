package com.chinacreator.gzcm.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 详设-06 F6-07（本体接口面收敛）门禁的<b>共用扫描底座</b>（#146ae）。
 *
 * <p>两条消费门禁（{@code OntologyNoDuplicateBaseMappingTest}／
 * {@code OntologyRouteManifestParityTest}）判的是同一个面——「本体域对外声明了哪些类级基址」，
 * 因此基址集合必须只有一处解析实现；两份各写一遍正则＝两套真值，漂移时两条门禁会给出互相矛盾的结论。</p>
 *
 * <p><b>口径边界（本类主张什么、不主张什么）</b>：
 * <ul>
 *   <li>主张「源码<b>声明</b>口径」：类级 {@code @RequestMapping} 字面量的集合。与「请求该路径得到非 404」
 *       之间隔着装配（ADR-15 制品≠承流）、双路径重写、过滤链三件事，本类一律不声称可达性
 *       ——同 {@code GatewayRouteIntegrityTest#staticScanDoesNotClaimReachability} 的收紧先例。</li>
 *   <li>范围＝{@code ontology-engine-impl} 的 Controller 目录（与既有
 *       {@code OntologyAnonymousSurfaceTest} 同一派生面，禁另立第二套本体前缀清单）。
 *       buszhi-impl 侧的本体族别名（{@code /api/v1/ecos/workflows} 等）不在本底座面上，
 *       已作为 F6-07 余项登记在台账，不在门禁里静默纳入或静默排除。</li>
 * </ul>
 *
 * <p><b>注释必须剥</b>：本域 Controller 的 Javadoc 里大量出现 {@code /api/v1/...} 形态引用
 * （实扫命中 3 处注释噪音），不剥注释会把「文档里提到的路径」当成「声明的基址」⇒ 差集虚大、
 * 分组虚多。剥法沿用 {@code OntologyAnonymousSurfaceTest#stripComments} 的实证顺序
 * （<b>先截行尾 {@code //} 再处理块注释</b>，否则 {@code /**} 会被误判成块注释起点吞掉后续条目）。</p>
 */
final class OntologyInterfaceSurfaceScanner {

    /** 本体域 Controller 目录（相对 {@code ecos_backend/}）。 */
    static final String ONTOLOGY_CONTROLLER_DIR_REL =
            "engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/controller";

    /** route-manifest 单源（相对仓库根）。 */
    static final String MANIFEST_REL =
            "ecos_backend/gateway/src/main/resources/route-manifest.json";

    /** 类级 {@code @RequestMapping}：兼容单字面量与 {@code {...}} 数组形态，取值面不含方法级注解。 */
    private static final Pattern CLASS_LEVEL_MAPPING = Pattern.compile(
            "@RequestMapping\\s*\\(\\s*(?:value\\s*=\\s*)?(\\{[^}]*\\}|\"[^\"]*\")",
            Pattern.DOTALL);

    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]*)\"");

    private OntologyInterfaceSurfaceScanner() {
    }

    // ═══════════════════════ 实现侧（声明口径） ═══════════════════════

    /** 一支 Controller 文件解析出的类级基址集合（数组形态已全部展开）。 */
    record DeclaredBases(String file, List<String> bases) {
    }

    /** 全部本体 Controller 的类级基址声明（解析驱动，禁人肉清单）。 */
    static List<DeclaredBases> scanControllerBases() throws IOException {
        Path dir = repoRoot().resolve("ecos_backend").resolve(ONTOLOGY_CONTROLLER_DIR_REL);
        assertTrueDir(dir);
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.getFileName().toString().endsWith(".java")).sorted().toList();
        }
        List<DeclaredBases> out = new ArrayList<>();
        for (Path f : files) {
            out.add(new DeclaredBases(f.getFileName().toString(), classLevelBasesOf(read(f))));
        }
        return List.copyOf(out);
    }

    /** 从单个源文件导出类级基址（{@code class} 关键字之前的头部注释剥离面内扫描）。 */
    static List<String> classLevelBasesOf(String source) {
        String stripped = stripComments(source);
        int classIdx = stripped.indexOf("\nclass ");
        if (classIdx < 0) {
            classIdx = stripped.indexOf("public class");
        }
        String head = classIdx < 0 ? stripped : stripped.substring(0, classIdx);
        List<String> bases = new ArrayList<>();
        Matcher m = CLASS_LEVEL_MAPPING.matcher(head);
        while (m.find()) {
            Matcher lit = STRING_LITERAL.matcher(m.group(1));
            while (lit.find()) {
                String path = lit.group(1).trim();
                if (path.isEmpty() || "/".equals(path) || !path.startsWith("/")) {
                    continue;
                }
                bases.add(path.endsWith("/") ? path.substring(0, path.length() - 1) : path);
            }
        }
        return List.copyOf(bases);
    }

    /**
     * 同基址多 Controller 分组：基址 → 声明该基址的 Controller 文件名（升序），只保留 {@code size ≥ 2}。
     *
     * <p>判等口径＝<b>基址字面量全等</b>，不是前缀嵌套。这是刻意的：{@code /api/v1/engine/ontology/git}
     * 与 {@code /api/v1/engine/ontology/copilot} 是 F6-07 点名要的「职责子族」分文件形态，
     * 属正常拆分；把父子前缀也算重复会让门禁红成一团并把正解一起否掉（消费方有夹具自证）。 */
    static Map<String, List<String>> duplicateBaseGroups(List<DeclaredBases> declared) {
        Map<String, Set<String>> byBase = new LinkedHashMap<>();
        for (DeclaredBases d : declared) {
            for (String base : d.bases()) {
                byBase.computeIfAbsent(base, k -> new LinkedHashSet<>()).add(d.file());
            }
        }
        Map<String, List<String>> out = new TreeMap<>();
        for (Map.Entry<String, Set<String>> e : byBase.entrySet()) {
            if (e.getValue().size() >= 2) {
                out.put(e.getKey(), e.getValue().stream().sorted().toList());
            }
        }
        return out;
    }

    /** 实现侧基址全集（去重、稳定序）。 */
    static Set<String> distinctBases(List<DeclaredBases> declared) {
        Set<String> out = new LinkedHashSet<>();
        for (DeclaredBases d : declared) {
            out.addAll(d.bases());
        }
        return out;
    }

    // ═══════════════════════ 契约册侧（route-manifest） ═══════════════════════

    /** route-manifest 一行的消费视图（只取门禁需要的字段）。 */
    record ManifestEntry(String prefix, String owner, String artifact, String route, List<String> anonymous) {
    }

    static List<ManifestEntry> readManifest() throws IOException {
        Path manifest = repoRoot().resolve(MANIFEST_REL);
        assertTrue(Files.isRegularFile(manifest), "route-manifest 单源缺失: " + manifest);
        JsonNode entries = new ObjectMapper()
                .readTree(read(manifest).getBytes(StandardCharsets.UTF_8))
                .path("entries");
        assertTrue(entries.isArray(), "route-manifest.entries 不是数组（解析口径将静默失效）");
        List<ManifestEntry> out = new ArrayList<>();
        for (JsonNode e : entries) {
            List<String> anon = new ArrayList<>();
            for (JsonNode a : e.path("anonymous")) {
                anon.add(a.asText());
            }
            out.add(new ManifestEntry(
                    e.path("prefix").asText(),
                    e.path("owner").asText(),
                    e.path("artifact").asText(),
                    e.path("route").asText(),
                    List.copyOf(anon)));
        }
        return List.copyOf(out);
    }

    /**
     * 前缀覆盖判据（与 Spring 的 path 段语义一致：整段命中，不做字符级前缀）。
     *
     * <p>刻意<b>不</b>写成 {@code base.startsWith(prefix)}：那样 {@code /api/v1/ecos/entities}
     * 会被 {@code /api/v1/ecos/entit} 之类覆盖，也会让「兄弟段互为覆盖」的假象通过。
     * 段界判断由 {@link #coveragePredicateActuallyBites()} 夹具双向自证。 */
    static boolean coveredBy(String prefix, String base) {
        String p = trimTail(prefix);
        String b = trimTail(base);
        if (p.isEmpty()) {
            return false;
        }
        return b.equals(p) || b.startsWith(p + "/");
    }

    private static String trimTail(String s) {
        String v = s.trim();
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }

    /** 实现基址是否被 manifest 任一条目的 prefix 覆盖。 */
    static boolean coveredByManifest(List<ManifestEntry> manifest, String base) {
        return manifest.stream().anyMatch(e -> coveredBy(e.prefix(), base));
    }

    // ═══════════════════════ 通用辅助 ═══════════════════════

    static Path repoRoot() throws IOException {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("ecos_backend")) && Files.isDirectory(p.resolve("docs"))) {
                return p;
            }
            Path parent = p.getParent();
            if (parent == null || parent.equals(p)) {
                break;
            }
            p = parent;
        }
        throw new IOException("cwd 未锚定 ECOS 仓库根（ecos_backend/ 与 docs/ 未同时命中）");
    }

    static String read(Path p) throws IOException {
        assertTrue(Files.isRegularFile(p), "门禁载体文件缺失: " + p);
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    private static void assertTrueDir(Path p) {
        assertTrue(Files.isDirectory(p), "本体 Controller 目录缺失: " + p);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    /**
     * 剥 Java 注释：先截行尾 {@code //}，再处理块注释（顺序不能反，理由见类注释）。
     * 与 {@code OntologyAnonymousSurfaceTest} 同型；本域类级映射字面量不含 {@code //}，故不会误伤字符串。
     */
    static String stripComments(String src) {
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
            if (lineCommented) {
                cur = cur.substring(0, slashSlash);
            }
            while (true) {
                int b = cur.indexOf("/*");
                if (b < 0) {
                    break;
                }
                int e = cur.indexOf("*/", b + 2);
                if (e < 0) {
                    inBlock = true;
                    cur = cur.substring(0, b);
                    break;
                }
                cur = cur.substring(0, b) + " " + cur.substring(e + 2);
            }
            if (lineCommented && !inBlock) {
                sb.append(cur).append('\n');
                continue;
            }
            sb.append(cur).append('\n');
        }
        return sb.toString();
    }
}
