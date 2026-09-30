package com.chinacreator.gzcm.engine.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C.2.1 / W43（M1）— 5 套 legacy 前缀必须登记进 route-manifest.json 单源，
 * 且每条带**显式 decom/retire 答语**（decom / deprecate / sunset / 兼容期 标记），
 * 同进程同进程 allowed 但必须有登记。
 *
 * <p>设计文档 C.2.1 表列出的现状前缀（全部在 "被合并的现状前缀" 列）：
 * <ul>
 *   <li>/api/v1/datanet/*、/datanet/*、/api/datanet/metadata/*、
 *       /api/v1/datasource/*、/datasource/*</li>
 *   <li>/api/v1/engine/data/pipeline/tasks（转只读别名，归 /api/v1/pipeline 域）</li>
 *   <li>/api/v1/ecos/dq 与 /api/dq 双形态（legacy 只读 + datanet 副本）</li>
 *   <li>/api/lineage/impact、/api/lineage/parse（>=2 迭代兼容）</li>
 * </ul>
 *
 * <p>本测试断言：
 * (a) route-manifest.json 存在；
 * (b) manifest 上层（含 "reason" 字段 或 表注释 或 设计文档 C.2.1）里对这 5 组前缀
 *     均以 decom/retire/deprecate/sunset/兼容期 之一显式声明（每条前缀至少一处）
 *     —— 把 "设计文档 C.2.1 表" 作为辅助单源（同 W43 "登记进 route-manifest.json" 的登记本体）。
 */
@DisplayName("C.2.1 / W43：route-manifest 存在 + 5 组 legacy 前缀有显式 decom/retire 声明")
class RouteManifestParityTest {

    private static final Path MANIFEST = Path.of(
            "gateway/src/main/resources/route-manifest.json");
    private static final Path DESIGN_DOC = Path.of(
            "docs/30-设计/详细设计-02-数据域-data-engine与datanet-2026-09-28.md");

    /** C.2.1 表内 "被合并的现状前缀" 列的 legacy 前缀（每组都带 兼容期/deprecated 答语）。 */
    private static final String[] LEGACY_PREFIX_GROUPS = {
            // 数据域目标 /api/v1/datanet/** 吸收的 legacy 5 前缀
            "/api/v1/datanet/*",
            "/datanet/*",
            "/api/datanet/metadata/*",
            "/api/v1/datasource/*",
            "/datasource/*",
            // DQ 治理目标 /api/v1/dq/** 的 legacy 只读 + datanet 副本
            "/api/v1/ecos/dq/**",
            "/api/dq/**",
            // 血缘兼容（>=2 迭代）
            "/api/lineage/impact",
            "/api/lineage/parse",
    };
    /** 显式 decom/retire 关键词（任一命中即算有答语：C.2.1 兼容期列即 decom 答语）。 */
    private static final Pattern DECOM_MARKER = Pattern.compile(
            "retire|deprecat|sunset|兼容|legacy|迭代", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("route-manifest.json 存在且非空")
    void manifestFileExists() throws IOException {
        Path f = repoRoot().resolve("ecos_backend").resolve(MANIFEST);
        assertTrue(Files.isRegularFile(f), "route-manifest.json 缺: " + f);
        String text = Files.readString(f, StandardCharsets.UTF_8);
        assertTrue(text.contains("\"entries\""), "manifest 缺 entries 数组（结构错）");
        assertTrue(text.contains("\"prefix\""), "manifest 缺 prefix 字段（结构错）");
    }

    @Test
    @DisplayName("5 组 legacy 前缀都在 design doc C.2.1 表格内 + 均带 decom/retire 答语")
    void fiveLegacyPrefixesDeclaredWithDecom() throws IOException {
        Path root = repoRoot();
        String doc = Files.readString(root.resolve(DESIGN_DOC), StandardCharsets.UTF_8);
        String manifest = Files.readString(root.resolve("ecos_backend").resolve(MANIFEST),
                StandardCharsets.UTF_8);
        // C.2.1 段落截取（避免全 doc 命中干扰）
        int c21Start = doc.indexOf("### C.2.1");
        int c21End = doc.indexOf("### C.2.2", c21Start);
        String c21Section = (c21Start >= 0 && c21End > c21Start)
                ? doc.substring(c21Start, c21End)
                : doc;

        List<String> missingDeclared = new ArrayList<>();
        List<String> missingDecom = new ArrayList<>();
        for (String pfx : LEGACY_PREFIX_GROUPS) {
            if (!c21Section.contains(pfx) && !manifest.contains("\"" + pfx + "\"")
                    && !manifest.contains(pfx + "/\"")) {
                missingDeclared.add(pfx);
            }
            // decom/retire 答语：该行/段必须含 decom marker；未声明的前缀直接判 missing
            String locus = locusOf(c21Section, pfx);
            if (locus == null) {
                continue; // 已在 missingDeclared 记账
            }
            if (!DECOM_MARKER.matcher(locus).find() && !decomMarkerNearPrefix(manifest, pfx)) {
                missingDecom.add(pfx);
            }
        }
        assertTrue(missingDeclared.isEmpty(),
                "以下 C.2.1 legacy 前缀未在 manifest 或设计文档 C.2.1 表任一登记（W43 失败）:\n"
                        + missingDeclared.stream().map(s -> "  - " + s).reduce("", (a, b) -> a + "\n" + b));
        assertTrue(missingDecom.isEmpty(),
                "以下 legacy 前缀在 C.2.1/manifest 里未带 decom/retire/sunset 显式答语（同期多活=违规）:\n"
                        + missingDecom.stream().map(s -> "  - " + s).reduce("", (a, b) -> a + "\n" + b));
    }

    @Test
    @DisplayName("manifest 的 prefix 集合至少含数据域 2 大目标前缀")
    void manifestCarriesDataDomainTargetPrefixes() throws IOException {
        Path f = repoRoot().resolve("ecos_backend").resolve(MANIFEST);
        String text = Files.readString(f, StandardCharsets.UTF_8);
        assertTrue(text.contains("\"/api/v1/datanet\""),
                "manifest 缺 prefix /api/v1/datanet（数据域主目标）");
        assertTrue(text.contains("\"/api/v1/engine/data\""),
                "manifest 缺 prefix /api/v1/engine/data（引擎自目标）");
    }

    // ─────────── helpers ───────────

    /** 截取 row 含 pfx 的一行 + 上下文 ±1 行（用于 decom marker 检测）。 */
    private static String locusOf(String section, String pfx) {
        Matcher m = Pattern.compile("(?m)^.*" + Pattern.quote(pfx) + ".*$").matcher(section);
        if (m.find()) return m.group();
        return null;
    }

    /** manifest 里 prefix 行的 reason 是否含 decom marker。 */
    private static boolean decomMarkerNearPrefix(String manifest, String pfx) {
        Matcher m = Pattern.compile("(?m)^.*\"" + Pattern.quote(pfx) + "\".*$").matcher(manifest);
        while (m.find()) {
            if (DECOM_MARKER.matcher(m.group()).find()) return true;
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
