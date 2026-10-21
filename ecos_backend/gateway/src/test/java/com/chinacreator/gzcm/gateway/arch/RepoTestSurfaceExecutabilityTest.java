package com.chinacreator.gzcm.gateway.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #142/B4-3e-23 收口 <b>D-B4-54 / D-B4-57</b>（死测试面＝「编译绿≠跑绿」假绿面）。
 *
 * <p>判据：{@code ecos_backend/**}{@code /src/test/java/**} 下任何<b>带活动测试注解</b>的 .java，
 * 必须被「该文件所属模块的生效 maven-surefire {@code <includes>}」命中。命中不到即死测试面
 * —— 它在 {@code mvn test} 里永不执行，只有 {@code -Dtest=类名} 手工指定才跑；
 * 于是「设计文档里的验收命令一直绿、门禁全绿、实际从未执行」（补充详设 §19.6 第 7 条同源缺陷）。</p>
 *
 * <p>生效 includes 取法是<b>严格口径</b>：模块 pom 自己声明了 surefire {@code <includes>} 就只看模块的
 * （显式 includes 覆盖而非并集，取并集会掩盖「模块漏声明某形态」），否则回落根 pom
 * {@code pluginManagement} 的 includes，两者皆无则回落 surefire 默认四项。</p>
 *
 * <p>取证纪律：本门自带 {@code @TempDir} 合成夹具（不落仓、随用例销毁），逐条证明「能变红」；
 * 扫描面有阈值守卫，防「没扫到＝绿」；豁免清单只减不增，登记项若已可执行或文件已消失即红。</p>
 */
@Timeout(120)
class RepoTestSurfaceExecutabilityTest {

    /** 活动测试注解（允许全限定形态）；{@code @Nested} 不计，内部类随外层类名命中。 */
    // 不锚行首：注解写在同一行（如 {@code void a() {} @Test void t(){}} 形态、夹具里的单行写法）
    // 同样是真实测试，锚行首会漏判。误报由两道防线兜住：① 注释与字符串先整体抹平；
    // ② {@code @} 前缀 + {@code \b} 右边界 ⇒ {@code @Testable}、{@code @NestedTest} 均不命中。
    private static final Pattern ACTIVE_TEST_ANNOTATION = Pattern.compile(
            "@(?:[A-Za-z_][\\w.]*\\.)?(?:Test|ParameterizedTest|RepeatedTest|TestFactory|ArchTest|Suite)\\b");

    /** surefire 未显式配置 includes 时的默认形态（实测 maven-surefire-plugin 3.2.5）。 */
    private static final List<String> SUREFIRE_DEFAULT_INCLUDES =
            List.of("**/Test*.java", "**/*Test.java", "**/*Tests.java", "**/*TestCase.java");

    /** 扫描面阈值守卫：2026-10-22 实测 603 个 src/test/java 文件 / 25 个含该目录的模块。 */
    private static final int MIN_SCANNED_TEST_FILES = 550;
    private static final int MIN_SCANNED_MODULES = 22;

    /**
     * 显式登记豁免（相对 {@code ecos_backend/}，只减不增；登记项若已可执行或文件已消失 ⇒ 本门变红）。
     * 本批 7 枚死测试面全部转为可执行面，无遗留豁免 —— 空清单即「零豁免严态」。
     */
    private static final List<String> REGISTERED_EXEMPTIONS = List.of();

    /** 夹具用：显式类型的空豁免清单（裸 {@code List.of()} 在此处推断成 {@code List<Object>} 会撞编译）。 */
    private static final List<String> NO_EXEMPTIONS = List.of();

    // ── 真仓判定 ─────────────────────────────────────────

    @Test
    @DisplayName("全仓带活动测试注解的测试面文件必须被所在模块生效 surefire includes 命中（违例＝0）")
    void everyAnnotatedTestFileIsExecutable() throws IOException {
        Path root = locateRepoRoot();
        Path backend = root.resolve("ecos_backend");
        Counts counts = countScanSurface(backend);

        assertTrue(counts.testFiles >= MIN_SCANNED_TEST_FILES,
                "扫描面异常收缩（实扫 " + counts.testFiles + " 个测试面文件 < 阈值 " + MIN_SCANNED_TEST_FILES
                        + "）——「空扫描＝假绿」，先修门再判绿");
        assertTrue(counts.modules >= MIN_SCANNED_MODULES,
                "含 src/test/java 的模块数异常（实测 " + counts.modules + " < 阈值 " + MIN_SCANNED_MODULES + "）");

        List<String> dead = scanDead(backend, includesOfRootPom(backend), REGISTERED_EXEMPTIONS);
        assertTrue(dead.isEmpty(),
                "死测试面（含活动测试注解却不被生效 surefire includes 命中，mvn test 永不执行）共 "
                        + dead.size() + " 枚：\n  " + String.join("\n  ", dead)
                        + "\n修法三选一：① 类名改合形态（*Test/*Tests/*TestCase/Test*）；"
                        + "② 该模块 pom 的 surefire includes 补齐形态（覆盖语义 ⇒ 须连同根 pom 全部形态一并声明）；"
                        + "③ 进 REGISTERED_EXEMPTIONS 并写明归属批次与理由。");
    }

    @Test
    @DisplayName("本批 7 枚原违例（6 枚 *IT＋1 枚具名类）确在扫描面内且已转可执行")
    void theSevenFormerlyDeadFilesAreNowCovered() throws IOException {
        Path root = locateRepoRoot();
        Path backend = root.resolve("ecos_backend");
        List<String> rootIncludes = includesOfRootPom(backend);
        List<String> formerlyDead = List.of(
                "engine/ai-engine/ai-engine-impl/src/test/java/com/chinacreator/gzcm/engine/ai/tool/CitationInducementIT.java",
                "engine/ai-engine/ai-engine-impl/src/test/java/com/chinacreator/gzcm/engine/ai/tool/ScenarioToolInvocationIT.java",
                "engine/ai-engine/ai-engine-impl/src/test/java/com/chinacreator/gzcm/engine/ai/wagent/web/WAgentGoalControllerIT.java",
                "engine/data-engine/data-engine-impl/src/test/java/com/chinacreator/gzcm/engine/data/quality/DqPhase4E2eIT.java",
                "runtime/llm-gateway/src/test/java/com/chinacreator/gzcm/runtime/llm/gateway/AiFaultInjectionIT.java",
                "workspace/workspace-impl/src/test/java/com/chinacreator/gzcm/workspace/it/WorkspaceRouteReachabilityIT.java",
                "engine/kb-engine/kb-engine-impl/src/test/java/com/chinacreator/gzcm/engine/kb/route/KbNavMissingTableReturnsServerError.java");
        for (String rel : formerlyDead) {
            Path p = backend.resolve(rel);
            assertTrue(Files.isRegularFile(p), "原违例文件应实存：" + rel);
            assertTrue(hasActiveTestAnnotation(p), "原违例文件应带活动测试注解：" + rel);
            Optional<String> dead = deadReason(p, rootIncludes);
            assertTrue(dead.isEmpty(), rel + " 仍属死测试面：" + dead.orElse(""));
        }
        // 根 pom 必须真的含 *IT 形态（否则上面 6 枚靠模块 pom 兜住＝半截子改动，违反 §19.6-7 根治口径）
        assertTrue(rootIncludes.contains("**/*IT.java"),
                "IT 执行面已上收根 pom 单源，实际根 pom includes=" + rootIncludes);
    }

    // ── 夹具：证明本门能变红 ─────────────────────────────

    @Test
    @DisplayName("夹具①：*IT.java 不被生效形态命中 → 必须变红（复现 D-B4-54 原判据）")
    void itWithoutIncludePatternIsReported(@TempDir Path tmp) throws IOException {
        Fixture fx = new Fixture(tmp).withRootIncludes(SUREFIRE_DEFAULT_INCLUDES);
        fx.addTestSource("svc/a", "FooIT.java", "class FooIT { @Test void t() {} }");
        fx.addTestSource("svc/a", "BarTest.java", "class BarTest { @Test void t() {} }");
        List<String> dead = fx.findDead();
        assertEquals(1, dead.size(), "根 pom 未含 *IT 形态时 FooIT.java 必须记为违例，BarTest.java 不该");
        assertTrue(dead.get(0).contains("FooIT.java"), dead.toString());
    }

    @Test
    @DisplayName("夹具②：根 pom 补 *IT 后转绿；模块声明 includes 属覆盖语义（漏 *IT 即红，不取并集掩盖）")
    void moduleIncludesOverrideRoot(@TempDir Path tmp) throws IOException {
        List<String> withIt = List.of("**/Test*.java", "**/*Test.java", "**/*Tests.java",
                "**/*TestCase.java", "**/*IT.java");
        Fixture fx = new Fixture(tmp).withRootIncludes(withIt);
        fx.addTestSource("svc/b", "FooIT.java", "class FooIT { @Test void t() {} }");
        assertEquals(0, fx.findDead().size(), "根 pom 含 *IT 形态时应命中");

        fx.modulePom("svc/b", "<includes><include>**/*Test.java</include></includes>");
        List<String> dead = fx.findDead();
        assertEquals(1, dead.size(), "模块显式声明 includes 时只看模块的；漏 *IT 必须记为违例");
        assertTrue(dead.get(0).contains("FooIT.java"), dead.toString());

        fx.modulePom("svc/b", "<includes>" + join(withIt) + "</includes>");
        assertEquals(0, fx.findDead().size(), "模块把全部形态重声明后应转绿");
    }

    @Test
    @DisplayName("夹具③：具名验收类进 includes 后转绿；无注解 helper 永不进判据")
    void namedClassAndPlainHelper(@TempDir Path tmp) throws IOException {
        Fixture fx = new Fixture(tmp).withRootIncludes(SUREFIRE_DEFAULT_INCLUDES);
        fx.addTestSource("svc/c", "KbNavMissingTableReturnsServerError.java",
                "class KbNavMissingTableReturnsServerError { @Test void t() {} }");
        fx.addTestSource("svc/c", "SomeTestHelper.java", "class SomeTestHelper { void help() {} }");
        List<String> dead = fx.findDead();
        assertEquals(1, dead.size(), "未进 includes 的具名验收类必须变红，helper 不计");
        assertTrue(dead.get(0).contains("KbNavMissingTableReturnsServerError.java"), dead.toString());

        fx.modulePom("svc/c", "<includes>"
                + "<include>**/*Test.java</include>"
                + "<include>**/KbNavMissingTableReturnsServerError.java</include>"
                + "</includes>");
        assertEquals(0, fx.findDead().size(), "具名类补进模块 includes 后应转绿");
    }

    @Test
    @DisplayName("夹具④：注释/字符串/@Testable 不触发；全限定 @Test 触发（大小写与注释盲区）")
    void commentAndStringBait(@TempDir Path tmp) throws IOException {
        Fixture fx = new Fixture(tmp).withRootIncludes(SUREFIRE_DEFAULT_INCLUDES);
        fx.addTestSource("svc/d", "Notes.java", "class Notes {\n"
                + "  // @Test 只是说明文字\n"
                + "  /* @ParameterizedTest */\n"
                + "  String s = \"@Test\";\n"
                + "  @Testable void t() {}\n"
                + "}");
        assertEquals(0, fx.findDead().size(), "注释、字符串、@Testable 均不得触发判据");

        fx.addTestSource("svc/d", "FullyQualified.java",
                "class FullyQualified { @org.junit.jupiter.api.Test void t() {} }");
        List<String> dead = fx.findDead();
        assertEquals(1, dead.size(), "全限定 @org.junit.jupiter.api.Test 是真实注解，必须触发");
        assertTrue(dead.get(0).contains("FullyQualified.java"), dead.toString());
    }

    @Test
    @DisplayName("夹具⑤：豁免项若已可执行或文件已消失 ⇒ 陈旧豁免即红（只减不增）")
    void staleExemptionIsRed(@TempDir Path tmp) throws IOException {
        Fixture fx = new Fixture(tmp).withRootIncludes(SUREFIRE_DEFAULT_INCLUDES);
        fx.addTestSource("svc/e", "GhostIT.java", "class GhostIT { @Test void t() {} }");
        fx.addTestSource("svc/e", "CoveredTest.java", "class CoveredTest { @Test void t() {} }");
        String ghost = "svc/e/src/test/java/a/b/GhostIT.java";
        String covered = "svc/e/src/test/java/a/b/CoveredTest.java";
        String deleted = "svc/e/src/test/java/a/b/DeletedIT.java";

        // 真实违例项进豁免 → 违例清空（豁免有效）
        assertEquals(0, fx.findDead(List.of(ghost)).size(), "有效豁免应消解违例");
        // 已可执行的文件仍挂豁免 → 陈旧即红（GhostIT 同时被豁免消解，只剩 STALE 项）
        List<String> stale = fx.findDead(List.of(ghost, covered));
        assertEquals(1, stale.size(), "登记项已可执行必须变红：" + stale);
        assertTrue(stale.get(0).startsWith("STALE-EXEMPTION"), stale.toString());
        // 文件不存在 → 陈旧即红
        List<String> missing = fx.findDead(List.of(ghost, deleted));
        assertEquals(1, missing.size(), "登记项指向不存在文件必须变红：" + missing);
        assertTrue(missing.get(0).startsWith("STALE-EXEMPTION"), missing.toString());
    }

    @Test
    @DisplayName("夹具⑥：跨段子包路径（src/test/java 下多级包）必须正确定位模块与相对路径")
    void deepPackagePath(@TempDir Path tmp) throws IOException {
        Fixture fx = new Fixture(tmp).withRootIncludes(SUREFIRE_DEFAULT_INCLUDES);
        fx.addTestSourceAt("svc/f", "com/x/y/z/DeepIT.java", "class DeepIT { @Test void t() {} }");
        List<String> dead = fx.findDead();
        assertEquals(1, dead.size(), "多级包下的 *IT 同样必须命中违例");
        assertTrue(dead.get(0).contains("com/x/y/z/DeepIT.java"), dead.toString());
    }

    // ── 核心实现 ─────────────────────────────────────────

    /** 扫描违例清单。{@code rootIncludes} 为回落形态；{@code exemptions} 为相对 backendDir 的登记项。 */
    private static List<String> scanDead(Path backendDir, List<String> rootIncludes,
                                         List<String> exemptions) throws IOException {
        List<String> out = new ArrayList<>();
        for (String rel : exemptions) {
            Path p = backendDir.resolve(rel);
            boolean stillDead = Files.isRegularFile(p) && deadReason(p, rootIncludes).isPresent();
            if (!stillDead) {
                out.add("STALE-EXEMPTION(登记项已失效，须删除) " + rel);
            }
        }
        try (Stream<Path> walk = Files.walk(backendDir)) {
            List<Path> files = walk.filter(RepoTestSurfaceExecutabilityTest::isTestSourceFile)
                    .sorted().toList();
            for (Path p : files) {
                String relPath = backendDir.relativize(p).toString().replace('\\', '/');
                if (exemptions.contains(relPath)) {
                    continue;
                }
                deadReason(p, rootIncludes).ifPresent(reason ->
                        out.add(relPath + " -> " + reason));
            }
        }
        return out;
    }

    /** 单文件判定：返回违例说明；不带活动注解或被生效 includes 命中时返回 empty。 */
    private static Optional<String> deadReason(Path testSourceFile, List<String> rootIncludes)
            throws IOException {
        if (!hasActiveTestAnnotation(testSourceFile)) {
            return Optional.empty();
        }
        Path srcTestJava = srcTestJavaDirOf(testSourceFile);
        if (srcTestJava == null) {
            return Optional.empty();
        }
        Path moduleDir = srcTestJava.getParent().getParent().getParent();
        String modulePomRel = moduleDir.getFileName().toString();
        List<String> effective = moduleIncludes(moduleDir);
        if (effective.isEmpty()) {
            effective = rootIncludes.isEmpty() ? SUREFIRE_DEFAULT_INCLUDES : rootIncludes;
        }
        String rel = srcTestJava.relativize(testSourceFile).toString().replace('\\', '/');
        for (String pattern : effective) {
            if (antMatches(pattern, rel)) {
                return Optional.empty();
            }
        }
        return Optional.of("含活动测试注解但不被生效 surefire includes 命中（模块 " + modulePomRel
                + " 生效形态=" + effective + "）");
    }

    private static boolean hasActiveTestAnnotation(Path file) throws IOException {
        String code = blankOutCommentsAndLiterals(
                Files.readString(file, StandardCharsets.UTF_8));
        return ACTIVE_TEST_ANNOTATION.matcher(code).find();
    }

    /** {@code .../src/test/java/<pkg>/X.java} → {@code .../src/test/java}，否则 null。 */
    private static Path srcTestJavaDirOf(Path file) {
        String s = file.toAbsolutePath().toString().replace('\\', '/');
        int idx = s.indexOf("/src/test/java/");
        if (idx < 0) {
            return null;
        }
        return Paths.get(s.substring(0, idx + "/src/test/java".length()));
    }

    /** 模块 pom 里 maven-surefire-plugin 的 includes；无则空。 */
    private static List<String> moduleIncludes(Path moduleDir) throws IOException {
        Path pom = moduleDir.resolve("pom.xml");
        return Files.isRegularFile(pom) ? surefireIncludesOf(Files.readString(pom, StandardCharsets.UTF_8))
                : List.of();
    }

    /** 根 pom（ecos_backend/pom.xml）pluginManagement 里的 surefire includes；读不到则默认四项。 */
    private static List<String> includesOfRootPom(Path backendDir) throws IOException {
        Path pom = backendDir.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            return SUREFIRE_DEFAULT_INCLUDES;
        }
        List<String> own = surefireIncludesOf(Files.readString(pom, StandardCharsets.UTF_8));
        return own.isEmpty() ? SUREFIRE_DEFAULT_INCLUDES : own;
    }

    private static Counts countScanSurface(Path backendDir) throws IOException {
        Counts c = new Counts();
        try (Stream<Path> walk = Files.walk(backendDir)) {
            for (Path p : walk.toList()) {
                String s = p.toString().replace('\\', '/');
                if (Files.isDirectory(p) && s.endsWith("/src/test/java")) {
                    c.modules++;
                }
                if (isTestSourceFile(p)) {
                    c.testFiles++;
                }
            }
        }
        return c;
    }

    private static final class Counts {
        private int testFiles;
        private int modules;
    }

    /** 从 pom 文本抠出 {@code maven-surefire-plugin} 所在 {@code <plugin>} 块的 {@code <include>} 值。 */
    static List<String> surefireIncludesOf(String pom) {
        List<String> out = new ArrayList<>();
        Matcher plugin = Pattern.compile("<plugin>.*?</plugin>", Pattern.DOTALL).matcher(pom);
        while (plugin.find()) {
            String block = plugin.group();
            if (!block.contains("maven-surefire-plugin")) {
                continue;
            }
            Matcher inc = Pattern.compile("<include>\\s*([^<\\s]+)\\s*</include>").matcher(block);
            while (inc.find()) {
                out.add(inc.group(1));
            }
        }
        return out;
    }

    // Ant 形态 → 正则：星号星号斜杠 可跨段，单星号 不跨斜杠。
    static boolean antMatches(String pattern, String relativePath) {
        StringBuilder rx = new StringBuilder("^");
        String p = pattern;
        if (p.startsWith("**/")) {
            rx.append("(?:.*/)?");
            p = p.substring(3);
        } else if (p.startsWith("/")) {
            p = p.substring(1);
        }
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c == '*') {
                rx.append("[^/]*");
            } else if (c == '?') {
                rx.append("[^/]");
            } else {
                rx.append(Pattern.quote(String.valueOf(c)));
            }
        }
        rx.append("$");
        return relativePath.matches(rx.toString());
    }

    private static boolean isTestSourceFile(Path p) {
        if (!Files.isRegularFile(p)) {
            return false;
        }
        String s = p.toString().replace('\\', '/');
        return s.contains("/src/test/java/") && s.endsWith(".java") && !s.contains("/target/");
    }

    /** 剥注释并抹平字符串/字符字面量内容（保留换行，便于阅读时行号对齐）。 */
    static String blankOutCommentsAndLiterals(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);
            char n = i + 1 < src.length() ? src.charAt(i + 1) : '\0';
            if (c == '/' && n == '*') {
                int end = src.indexOf("*/", i + 2);
                int stop = end < 0 ? src.length() : end;
                for (int k = i; k < stop; k++) {
                    out.append(src.charAt(k) == '\n' ? '\n' : ' ');
                }
                if (end >= 0) {
                    out.append("  ");
                    i = end + 2;
                } else {
                    i = stop;
                }
                continue;
            }
            if (c == '/' && n == '/') {
                int nl = src.indexOf('\n', i);
                int stop = nl < 0 ? src.length() : nl;
                for (int k = i; k < stop; k++) {
                    out.append(' ');
                }
                i = stop;
                continue;
            }
            if (c == '"' || c == '\'') {
                char quote = c;
                out.append(quote);
                i++;
                while (i < src.length()) {
                    char d = src.charAt(i);
                    if (d == '\\' && i + 1 < src.length()) {
                        out.append("  ");
                        i += 2;
                        continue;
                    }
                    if (d == quote) {
                        out.append(quote);
                        i++;
                        break;
                    }
                    out.append(d == '\n' ? '\n' : ' ');
                    i++;
                }
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** 从运行目录向上遍历，定位同时含 ecos_frontend 与 ecos_backend 的仓库根。 */
    private static Path locateRepoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 12 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("ecos_frontend"))
                    && Files.isDirectory(dir.resolve("ecos_backend"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "无法定位仓库根：自 user.dir 向上 12 级未找到同时含 ecos_frontend 与 ecos_backend 的目录");
    }

    private static String join(List<String> includes) {
        StringBuilder sb = new StringBuilder();
        for (String inc : includes) {
            sb.append("<include>").append(inc).append("</include>");
        }
        return sb.toString();
    }

    /** 合成夹具仓（{@code @TempDir} 之下，随用例销毁，不落仓）。 */
    private static final class Fixture {
        private final Path root;
        private List<String> rootIncludes = SUREFIRE_DEFAULT_INCLUDES;

        Fixture(Path root) {
            this.root = root;
        }

        Fixture withRootIncludes(List<String> includes) {
            this.rootIncludes = includes;
            write(root.resolve("ecos_backend/pom.xml"), "<project><artifactId>ecos-backend</artifactId>"
                    + "<build><pluginManagement><plugins><plugin>"
                    + "<artifactId>maven-surefire-plugin</artifactId>"
                    + "<configuration><includes>" + join(includes) + "</includes></configuration>"
                    + "</plugin></plugins></pluginManagement></build></project>");
            write(root.resolve("ecos_frontend/package.json"), "{}");
            return this;
        }

        void addTestSource(String moduleRel, String fileName, String body) {
            addTestSourceAt(moduleRel, "a/b/" + fileName, body);
        }

        void addTestSourceAt(String moduleRel, String pkgPath, String body) {
            write(root.resolve("ecos_backend/" + moduleRel + "/src/test/java/" + pkgPath), body);
        }

        void modulePom(String moduleRel, String surefireFragment) {
            write(root.resolve("ecos_backend/" + moduleRel + "/pom.xml"),
                    "<project><artifactId>" + moduleRel + "</artifactId><build><plugins><plugin>"
                            + "<groupId>org.apache.maven.plugins</groupId>"
                            + "<artifactId>maven-surefire-plugin</artifactId>"
                            + "<configuration>" + surefireFragment + "</configuration>"
                            + "</plugin></plugins></build></project>");
        }

        List<String> findDead() throws IOException {
            return scanDead(root.resolve("ecos_backend"), rootIncludes, NO_EXEMPTIONS);
        }

        List<String> findDead(List<String> exemptions) throws IOException {
            return scanDead(root.resolve("ecos_backend"), rootIncludes, exemptions);
        }

        private static void write(Path p, String body) {
            try {
                Files.createDirectories(p.getParent());
                Files.writeString(p, body, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("夹具写入失败：" + p, e);
            }
        }
    }
}
