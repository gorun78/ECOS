package com.chinacreator.gzcm.engine.data.gitignore;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W48 / M0《SchemaDriftLintTest (gitignore 变体)》— 仓库根 .gitignore 宽模式回归守卫
 * （设计分册 02 W48，M0 缺口收口，2026-09-30 起算）。
 *
 * <p>W48 关联条目：机制层曾出现「<code>pom.xml</code> 被 <em>宽模式</em>忽略屏蔽」的漏网
 * 事故 —— 根目录 {@code .gitignore} 的一条<b>无路径、无锚</b>的 {@code pom.xml} 模式把
 * 仓库内所有 pom.xml（artifact 上卷面）一并忽略，导致构建文件漏交/恢复困难。
 * 设计订正方向：pom 只能被「artifact 级 {@code target/} 产物规则」或「显式锚定路径」
 * （如 {@code /ecos_backend/pom.xml}）处理；<b>源码/构建/配置文件永远不入库级忽略</b>。</p>
 *
 * <p>本测试对 4 个 .gitignore（根 / ecos_backend / ecos_frontend / ecos-tests）做静态断言：</p>
 * <ol>
 *   <li><b>FAIL-on-detection</b>：任何单一模式精确等于
 *       {@code pom.xml} / {@code *.java} / {@code *.yml} / {@code *.yaml} /
 *       {@code *.properties}（即该模式会屏蔽仓库级源码/构建/配置）→ 立即 FAIL（回归即缺陷）。</li>
 *   <li><b>负向护栏</b>：{@code *.class} 这类构建产物后缀允许存在（{@code *.java} 等
 *       是源码，不允许；{@code *.class} 不是源码）。白名单只覆盖产物后缀，不覆盖源码扩展名。</li>
 *   <li><b>正向护栏</b>：后端产物规则 {@code target/} 必须存在于
 *       {@code ecos_backend/.gitignore}（build-artifact-suffix 是允许且必需的形态）。</li>
 *   <li>无 git 依赖：不执行 git CLI（M0 约束），仅静态文本扫描。</li>
 * </ol>
 *
 * @author ecos-p0-data-engine
 * <p>2026-09-30（W48 / M0；设计行：W48 gitignore 宽模式屏蔽订正 + ST07 关联构建产物规则白名单）</p>
 */
@DisplayName("W48/M0：仓库级 .gitignore 宽模式回归守卫（pom.xml / *.java / *.yml / *.properties 不得裸忽略）")
class SchemaDriftLintTest {

    /** 禁止出现为独立模式的宽模式字面量（源码/构建/配置双重命中的最高危四件）。 */
    private static final List<String> FORBIDDEN_BARE = List.of(
            "pom.xml",      // W48 缺陷核心：会屏蔽仓库内所有 pom 文件（上卷）
            "*.java",       // 源码宽忽略：任何 Java 源文件都不入库
            "*.yml",        // 配置宽忽略：application-*.yml / docker-compose.yml 都不入库
            "*.yaml",
            "*.properties"  // 配置宽忽略：jdbc.properties / 参数文件不入库
    );

    /** 空白名单（源码扩展名的近邻产物后缀）；*, *.class — 构建产物合法。 */
    private static final List<String> ALLOWED_BUILD_ARTIFACT_SUFFIX = List.of(
            "target/",     // Maven 构建产物目录（必需）
            "*.class",     // 编译产物
            "*.jar",       // 本地临时构建产物
            "*.log",       // 运行日志
            "*.zip",
            "*.tar.gz",
            "*.7z"
    );

    @Test
    @DisplayName("根 + 各子仓 .gitignore 不得包含裸 pom.xml / *.java / *.yml / *.yaml / *.properties")
    void noBareWideIgnorePatterns() throws IOException {
        List<Path> ignores = discoverGitignores();
        assertFalse(ignores.isEmpty(), "仓库内未发现任何 .gitignore（结构漂移？）");

        List<String> violations = new java.util.ArrayList<>();
        for (Path gi : ignores) {
            String rel = rel(gi);
            List<String> lines = Files.readAllLines(gi, StandardCharsets.UTF_8);
            for (String raw : lines) {
                String line = stripComment(raw).trim();
                if (line.isEmpty()) {
                    continue;
                }
                // 宽模式 = 该模式自身就是裸字面量（无子目录前缀 / 无锚 / 无路径限定）
                for (String bad : FORBIDDEN_BARE) {
                    if (line.equals(bad) || line.equals("**/" + bad) || line.equals("**" + bad)) {
                        // 注意：**/pom.xml 同样是宽模式（匹配任意层级所有 pom）。
                        violations.add(rel + " 含宽模式：" + line);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "W48/M0：以下 .gitignore 规则把 pom/源码/配置整体屏蔽（宽模式回归）。"
                        + "修正方向：仅维持 artifact 级 target/ 规则；如需忽略特例 pom，"
                        + "改用显式锚定路径（/ecos_backend/archive/…/pom.xml）。"
                        + "命中：\n" + String.join("\n", violations));
    }

    @Test
    @DisplayName("后端构建产物规则 target/ 必须保留（build-artifact-suffix 白名单稳态）")
    void backendBuildArtifactRulePreserved() throws IOException {
        Path root = findRepoRoot();
        Path backendGi = root.resolve("ecos_backend").resolve(".gitignore");
        assertTrue(Files.isRegularFile(backendGi),
                "缺 ecos_backend/.gitignore: " + backendGi);
        List<String> lines = Files.readAllLines(backendGi, StandardCharsets.UTF_8);
        boolean hasTargetDir = lines.stream()
                .map(SchemaDriftLintTest::stripComment)
                .map(String::trim)
                .anyMatch(l -> l.equals("target/") || l.equals("*/target/")
                        || l.equals("**/target/") || l.matches("^[^/]*target/$"));
        assertTrue(hasTargetDir,
                "ecos_backend/.gitignore 必须有构建产物目录规则 target/（白名单合法形态）—— "
                        + "目标未找到，可能被误删或改写成源码宽模式。");
    }

    @Test
    @DisplayName("白名单形态自洽：target/ 等产物后缀规则可出现，源码扩展名宽模式不可以")
    void buildArtifactWhitelistIsClosed() {
        // 结构性自证：禁清单与白名单模式不构成同一字符串（防止白名单误放行禁模式）
        for (String forbidden : FORBIDDEN_BARE) {
            assertFalse(ALLOWED_BUILD_ARTIFACT_SUFFIX.contains(forbidden),
                    "白名单与禁清单交叠于 " + forbidden + "（规则实现漂移）");
        }
        // 反向：白名单中含源码扩展名（.java）→ FAIL
        assertTrue(ALLOWED_BUILD_ARTIFACT_SUFFIX.stream()
                        .noneMatch(s -> s.toLowerCase(Locale.ROOT).endsWith(".java")
                                || s.toLowerCase(Locale.ROOT).endsWith(".yml")
                                || s.toLowerCase(Locale.ROOT).endsWith(".properties")),
                "白名单不得放行源码/配置扩展名的宽产物规则");
    }

    // ==================== 工具 ====================

    /** 找出仓库内 4 项目 .gitignore；缺失某层不 FAIL（本仓结构基线含根 + 3 子项目）。 */
    private static List<Path> discoverGitignores() {
        Path root = findRepoRoot();
        List<String> rels = List.of(
                ".gitignore",
                 "ecos_backend" + File.separator + ".gitignore",
                "ecos_frontend" + File.separator + ".gitignore",
                "ecos-tests" + File.separator + ".gitignore"
        );
        return rels.stream()
                .map(root::resolve)
                .filter(Files::isRegularFile)
                .collect(Collectors.toList());
    }

    /** 去注释：从 #{...} 或行首 # 起截断；去掉行内空格允许（不处理转义 #，.gitignore 场景足够）。 */
    static String stripComment(String line) {
        int hash = line.indexOf('#');
        String t = hash >= 0 ? line.substring(0, hash) : line;
        // 尾部空白
        int end = t.length();
        while (end > 0 && (t.charAt(end - 1) == ' ' || t.charAt(end - 1) == '\t'
                || t.charAt(end - 1) == '\r')) {
            end--;
        }
        return t.substring(0, end);
    }

    private static String rel(Path p) {
        Path root = findRepoRoot();
        try {
            return root.relativize(p).toString();
        } catch (IllegalArgumentException e) {
            return p.toString();
        }
    }

    static Path findRepoRoot() {
        Path cursor = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve(".git"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        cursor = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (cursor != null) {
            if (Files.isRegularFile(cursor.resolve("pom.xml"))
                    || Files.isRegularFile(cursor.resolve("ecos_backend" + File.separator + "pom.xml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("无法从 user.dir 定位仓库根（.git / pom.xml 均缺失）");
    }
}
