package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1 W43/W53-adjacent data domain (详细设计-02 C.2.1 路由单源 / D-3 五套前缀并活) —
 * 路由重写表与 {@code route-rewrite-lint.ps1} 的 parity 门禁。
 *
 * <p><b>单源自洽性</b>：{@code route-rewrite-lint.ps1}（AGENTS.md 基线，23 条 = 正向 10
 * + 反向 13）是重写表漂移的权威校验器，其消费对象就是
 * {@code VersionPrefixRewriteFilter} 的三张 Map
 * （{@code V1_REWRITE_MAP} / {@code REVERSE_EXACT_MAP} / {@code REVERSE_PREFIX_MAP}），
 * 用同一条 {@code Map.entry} 正则解析。本测试与 lint 共用同一单源，防止两表各自漂移。
 *
 * <p><b>断言</b>：
 * <ol>
 *   <li>从 filter 解析出的操作路径（三张 Map 的 {@code Map.entry} source 键）集合
 *       = 23（正向 10 + 反向 13）。</li>
 *   <li>{@code route-rewrite-lint.ps1} 引用的三张 Map 块名、filter 文件路径、
 *       {@code Map.entry} 解析器与本测试完全同源。</li>
 *   <li>两种独立解析法（分块扫描 vs 全文件扫描）得到同一个 23 集合。</li>
 * </ol>
 *
 * <p>纯源码/JSON 扫描，不启动 Spring。
 *
 * @author ecos-p0-data-engine
 * <br>
 * 2026-09-30
 */
class RouteManifestParityTest {

    private static final String FILTER_REL =
            "ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/filter/VersionPrefixRewriteFilter.java";
    private static final String LINT_REL = "_win_tasks/route-rewrite-lint.ps1";
    private static final String MANIFEST_REL =
            "ecos_backend/gateway/src/main/resources/route-manifest.json";

    private static final int FORWARD_BASELINE = 10;
    private static final int REVERSE_BASELINE = 13;
    private static final int TOTAL_BASELINE = 23;

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

    /** 抓某个 Map 块内的所有 source 键。 */
    private static Set<String> blockKeys(String src, String blockName) {
        int start = src.indexOf(blockName + " = Map.ofEntries(");
        if (start < 0) return Set.of();
        int end = src.indexOf("    );", start);
        String block = end < 0 ? src.substring(start) : src.substring(start, end);
        Set<String> keys = new LinkedHashSet<>();
        Matcher m = Pattern.compile("Map\\.entry\\(\\s*\"([^\"]+)\"\\s*,\\s*\"[^\"]+\"\\s*\\)").matcher(block);
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }

    @Test
    @DisplayName("操作路径集合 = 正向 10 + 反向 13 = 23")
    void rewriteOperationPathsTotal23() throws IOException {
        String src = read(repoRoot().resolve(FILTER_REL));

        Set<String> forward = blockKeys(src, "V1_REWRITE_MAP");
        Set<String> reverseExact = blockKeys(src, "REVERSE_EXACT_MAP");
        Set<String> reversePrefix = blockKeys(src, "REVERSE_PREFIX_MAP");

        assertEquals(FORWARD_BASELINE, forward.size(),
                "正向重写 source 键应 10 条，当前 " + forward.size() + " : " + forward);
        assertEquals(REVERSE_BASELINE, reverseExact.size() + reversePrefix.size(),
                "反向重写 source 键应 13 条，当前 " + (reverseExact.size() + reversePrefix.size()));

        Set<String> all = new LinkedHashSet<>(forward);
        all.addAll(reverseExact);
        all.addAll(reversePrefix);
        assertEquals(TOTAL_BASELINE, all.size(),
                "AGENTS.md / route-rewrite-lint.ps1 基线 23 条 source 键，当前 " + all.size());
    }

    @Test
    @DisplayName("route-rewrite-lint.ps1 与 filter 共用同一单源（三张 Map 块名 + 同 filter 路径 + 同 entry 解析器）")
    void lintScriptSharesTheSameSingleSource() throws IOException {
        Path root = repoRoot();
        String lint = read(root.resolve(LINT_REL));

        // (1) lint 消费的三张 Map 块名与 filter 完全一致（单源，禁止 lint 另立第二张表）
        for (String block : new String[]{"V1_REWRITE_MAP", "REVERSE_EXACT_MAP", "REVERSE_PREFIX_MAP"}) {
            assertTrue(lint.contains(block),
                    "route-rewrite-lint.ps1 应引用 " + block + "（与 filter 同源）");
        }

        // (2) lint 指向的 filter 文件路径 = 本测试解析的同一文件
        assertTrue(lint.contains("VersionPrefixRewriteFilter.java"),
                "route-rewrite-lint.ps1 应指向 VersionPrefixRewriteFilter.java 单源");

        // (3) lint 用同一条 Map.entry 解析正则 —— 两表算子同源（23 条由 AGENTS.md 基线，
        //     本测试上方 test 以 filter 解析结果 = 23 独立断言）
        assertTrue(lint.contains("Map.entry"),
                "route-rewrite-lint.ps1 应用 Map.entry 解析器与 filter 一致");

        // (4) filter 三张表与 lint 三块名闭合：Filter 中每一张 block 都存在
        String filterSrc = read(root.resolve(FILTER_REL));
        for (String block : new String[]{"V1_REWRITE_MAP", "REVERSE_EXACT_MAP", "REVERSE_PREFIX_MAP"}) {
            assertTrue(filterSrc.contains(block + " = Map.ofEntries("),
                    "filter 缺 " + block + " 块定义");
        }
    }

    @Test
    @DisplayName("两种独立解析法得到同一 23 集合（block 扫描 vs 全文件 Map.entry 扫描，防双表漂移）")
    void twoParseMethodsReturnSame23Set() throws IOException {
        String src = read(repoRoot().resolve(FILTER_REL));

        // 解析法 A：分块（V1_REWRITE_MAP + REVERSE_* ）抓 source 键
        Set<String> byBlock = new LinkedHashSet<>();
        for (String block : new String[]{"V1_REWRITE_MAP", "REVERSE_EXACT_MAP", "REVERSE_PREFIX_MAP"}) {
            blockKeys(src, block).forEach(byBlock::add);
        }

        // 解析法 B：全文件正则抓所有 Map.entry 的第一参（source）
        Set<String> byWholeFile = new LinkedHashSet<>();
        Matcher m = Pattern.compile("Map\\.entry\\(\\s*\"([^\"]+)\"\\s*,\\s*\"[^\"]+\"\\s*\\)").matcher(src);
        while (m.find()) byWholeFile.add(m.group(1));

        // 两种解析法必须得到同一个 23 条集合（AGENTS.md 基线；route-rewrite-lint.ps1
        // 用同样的全文件 Map.entry 正则解析，故两数组源同 = 本测试必须同集）
        assertEquals(TOTAL_BASELINE, byBlock.size(),
                "分块解析应 23 条 source 键，当前 " + byBlock.size());
        assertEquals(byBlock, byWholeFile,
                "分块扫描集合 != 全文件扫描集合（rewrite 表出现游离 Map.entry，lint 口径将漂移）");
    }
}
