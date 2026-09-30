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
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C.1 / W41（M1）— datanet :18082 独立承流前「端点差集清零」源码守卫。
 *
 * <p>设计文档 C.1 验收 DatanetEndpointParityTest：对 route-manifest 数据域全部路径，
 * 在 :8080 与 :18082 上均须非 404。本测试是 CI-safe 的 <b>源码 parity</b> 替代：
 * data-engine-impl 全量 controller 的 {@code @RequestMapping} 类级 base，
 * 必须全部被 (route-manifest.json prefix ∪ 设计文档 C.2.1 登记兼容期 legacy 前缀) 覆盖
 *（同一份路由作为 gateway monolith 与 datanet service 双态共源），无差集 = 切流 0 端点丢失。
 * 不启动 Spring 容器、不 curl。
 */
@DisplayName("C.1 / W41：data-engine controller 端点集合被 (route-manifest ∪ legacy 前缀) 覆盖")
class DatanetEndpointParityTest {

    private static final Path DATA_IMPL_SRC = Path.of(
            "engine/data-engine/data-engine-impl/src/main/java");
    private static final Path ROUTE_MANIFEST = Path.of(
            "gateway/src/main/resources/route-manifest.json");
    private static final Path DESIGN_DOC = Path.of(
            "docs/30-设计/详细设计-02-数据域-data-engine与datanet-2026-09-28.md");

    /** 数据域合法目标前缀（C.2.1 声明，data-engine 允许新增/已承流；manifest 未强制登记者补此）。 */
    private static final String[] TARGET_PREFIXES = {
            // C.2.1 三线：manifest 已登记 /api/v1/datanet、/api/v1/engine/data；
            // /api/v1/pipeline（DAG 定义+执行+调试，保持承流）与 /api/v1/dq（DQ 治理主前缀）
            // 为 C.2.1 声明的「目标前缀」，manifest 暂未登记，此处按设计同源补登。
            "/api/v1/pipeline",
            "/api/v1/dq",
    };

    /** 设计文档 C.2.1 五条目标前缀行的 "被合并的现状前缀" 列（兼容期允许保留的 legacy 前缀集）。 */
    private static final String[] LEGACY_COMPAT_PREFIXES = {
            "/api/v1/datanet",
            "/datanet",
            "/api/datanet/metadata",
            "/api/v1/datasource",
            "/datasource",
            // /api/v1/pipeline 目标由 /api/v1/engine/data/pipeline/tasks 转只读别名吸收
            "/api/v1/engine/data/pipeline",
            // DQ legacy 只读 + datanet 副本
            "/api/v1/ecos/dq",
            "/api/dq",
            // 血缘兼容（>=2 迭代）
            "/api/lineage",
    };

    @Test
    @DisplayName("data-engine-impl 全部 controller 类级 base 都被 (manifest ∪ legacy 前缀) 覆盖")
    void everyControllerBaseCovered() throws IOException {
        Path root = repoRoot();
        Set<String> covered = new LinkedHashSet<>(
                parseManifestPrefixes(root.resolve("ecos_backend").resolve(ROUTE_MANIFEST)));
        covered.addAll(List.of(LEGACY_COMPAT_PREFIXES));
        covered.addAll(List.of(TARGET_PREFIXES));
        assertFalse(covered.isEmpty(), "manifest + legacy 前缀集合为空（扫描错配）");

        Set<String> controllerBases = scanControllerBases(root.resolve("ecos_backend")
                .resolve(DATA_IMPL_SRC));
        assertFalse(controllerBases.isEmpty(),
                "data-engine-impl controller @RequestMapping 扫描为空 — 正则失配");

        Set<String> missing = new LinkedHashSet<>();
        for (String base : controllerBases) {
            if (!coveredBy(covered, base)) missing.add(base);
        }
        assertTrue(missing.isEmpty(),
                "以下 data-engine controller base 未落在 (route-manifest ∪ 登记 legacy 前缀) 覆盖内"
                        + "（切流 :8080→:18082 会 404）:\n"
                        + missing.stream().map(s -> "  - " + s).reduce("", (a, b) -> a + "\n" + b)
                        + "\ncovered prefixes: " + covered);
    }

    @Test
    @DisplayName("数据域 controller base ≥ 10（非空门：防源码根路径错配静默通过）")
    void dataDomainControllersExist() throws IOException {
        Set<String> bases = scanControllerBases(repoRoot().resolve("ecos_backend")
                .resolve(DATA_IMPL_SRC));
        assertTrue(bases.size() >= 10,
                "data-engine-impl 应有 ≥10 个 controller base，实际 " + bases.size() + ": " + bases);
    }

    @Test
    @DisplayName("route-manifest.json 存在且含数据域目标前缀（M1 单源门）")
    void manifestExistsWithDataPrefix() throws IOException {
        Path f = repoRoot().resolve("ecos_backend").resolve(ROUTE_MANIFEST);
        Set<String> prefixes = parseManifestPrefixes(f);
        assertTrue(prefixes.contains("/api/v1/datanet"),
                "manifest 缺 /api/v1/datanet: " + prefixes);
        assertTrue(prefixes.contains("/api/v1/engine/data"),
                "manifest 缺 /api/v1/engine/data: " + prefixes);
    }

    // ─────────── helpers ───────────

    /** 类级 @RequestMapping 全部字面量（数组形一个不丢；仅多取首个值足够做 base 段前缀）。 */
    private static Set<String> scanControllerBases(Path srcJavaRoot) throws IOException {
        Set<String> bases = new LinkedHashSet<>();
        if (!Files.isDirectory(srcJavaRoot)) {
            throw new IllegalStateException("data-engine-impl 源码根不存在: " + srcJavaRoot);
        }
        try (Stream<Path> walk = Files.walk(srcJavaRoot)) {
            for (Path f : (List<Path>) walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(f, StandardCharsets.UTF_8);
                if (!text.contains("@RestController") && !text.contains("@Controller")) continue;
                Matcher req = REQ_MAPPING.matcher(text);
                while (req.find()) {
                    String lit = req.group(1);
                    if (!lit.startsWith("/")) lit = "/" + lit;
                    bases.add(lit);
                }
            }
        }
        return bases;
    }

    private static final Pattern REQ_MAPPING = Pattern.compile(
            "@RequestMapping\\(\\s*\\{?\\s*\"([^\"]+)", Pattern.CASE_INSENSITIVE);

    /** route-manifest 抽 "prefix" 字段（不引 JSON 库，逐 entry 正则）。 */
    private static Set<String> parseManifestPrefixes(Path manifest) throws IOException {
        assertTrue(Files.isRegularFile(manifest), "route-manifest.json 缺: " + manifest);
        String text = Files.readString(manifest, StandardCharsets.UTF_8);
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile("\"prefix\"\\s*:\\s*\"([^\"]+)\"").matcher(text);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** prefix 覆盖：target 恰等于 prefix（去尾 *）或位于其段前缀下。 */
    static boolean coveredBy(Set<String> prefixes, String target) {
        for (String p : prefixes) {
            if (p == null || !p.startsWith("/")) continue;
            String np = p.replaceAll("/?\\*+$", "");
            if (target.equals(np) || target.startsWith(np + "/")) return true;
        }
        return false;
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
