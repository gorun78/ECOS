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

    /**
     * 根 pom {@code pluginManagement} 的 surefire 形态全集 = 执行面单源（2026-10-25 实测 5 项）。
     *
     * <p>#142/B4-3e-23 把 {@code *IT} 上收根 pom 时，在 kb-engine-impl 模块 pom 留了一枚<b>具名
     * 单文件 include</b>（文档冻结的验收类名）。模块侧显式 {@code <includes>} 是<b>覆盖而非并集</b>，
     * 所以那枚具名项要求该模块把五形态连同一起重声明——少写一形态，该模块就静掉一批执行面，
     * 而当时没有任何门盯住这件事（本批补，见 {@code moduleIncludesReDeclareRootFormsAndNameNothingElse}）。</p>
     */
    private static final List<String> ROOT_FORMS_BASELINE = List.of(
            "**/Test*.java", "**/*Test.java", "**/*Tests.java", "**/*TestCase.java", "**/*IT.java");

    /**
     * 具名 include 登记闭集（只减不增）。允许登记的唯一理由＝类名是设计文档冻结的验收标识；
     * 每条要双证据在位（文档实存且含该验收命令／该类文件实存且带活动测试注解），
     * 证据一失效该登记项即红——禁「登记过＝现在还成立」。
     */
    private static final List<NamedInclude> NAMED_INCLUDE_REGISTRY = List.of(
            new NamedInclude("engine/kb-engine/kb-engine-impl/pom.xml",
                    "**/KbNavMissingTableReturnsServerError.java",
                    "docs/30-设计/详细设计-04-知识域-dccheng与kb-engine-2026-09-29.md",
                    "mvn -Dtest=KbNavMissingTableReturnsServerError#absentTableYields500Not404"));

    /** 具名 include 面基线＝1 枚（F04-03 验收类）；改名或新留具名项都必须先动这条。 */
    private static final int NAMED_INCLUDE_LEGS_BASELINE = 1;

    /** {@code *Gate} 命名面正向对照下限（2026-10-25 实测 25 枚）：防「扫不到＝绿」。 */
    private static final int MIN_SCANNED_GATE_NAMED_FILES = 20;

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

    // ── #142/B4-3e-62 复核：形态单源 + 具名 include 闭集 ────────

    @Test
    @DisplayName("根 pom 五形态单源不减不增；*Gate 命名面实测全部合形态，不合形态的活动测试面只能是指名登记的那一枚")
    void rootFormsAreSingleSourceAndNamedFaceIsClosed() throws IOException {
        Path backend = locateRepoRoot().resolve("ecos_backend");
        assertEquals(ROOT_FORMS_BASELINE, includesOfRootPom(backend),
                "根 pom surefire 形态集发生增减。显式 includes 是覆盖语义，删一形态＝该形态下全部活动测试面"
                        + "从 mvn test 静掉（编译绿≠跑绿），加一形态则须先重设本钉值并说明归属批次");

        // 正向对照：Gate 命名面确实落在扫描半径里（阈值失效说明扫描器失明，不许拿空集合当证据）
        List<String> gateNamed = gateNamedTestFiles(backend);
        assertTrue(gateNamed.size() >= MIN_SCANNED_GATE_NAMED_FILES,
                "含 Gate 的测试面文件实扫 " + gateNamed.size() + " 枚 < 阈值 " + MIN_SCANNED_GATE_NAMED_FILES
                        + "——「*Gate 命名覆盖性」这条结论必须先有面再谈覆盖");
        // 只收「Gate 结尾且不带任一形态后缀」的门面类名（ZzGate.java 命中，ZzGateTest.java 不命中）
        List<String> bareGate = gateNamed.stream().filter(s -> s.endsWith("Gate.java")).toList();
        assertTrue(bareGate.isEmpty(),
                "出现以 Gate 结尾却不带任一生效形态的测试面（根 pom 五形态对这类命名不覆盖）：\n  "
                        + String.join("\n  ", bareGate));

        List<String> oddNamed = nonFormNamedTestFiles(backend);
        assertEquals(NAMED_INCLUDE_LEGS_BASELINE, oddNamed.size(),
                "带活动测试注解、却不被根 pom 五形态命中的文件数（具名豁免面，只减不增）：\n  "
                        + String.join("\n  ", oddNamed)
                        + "\n新增一枚＝要么改名合形态（*Test/*Tests/*TestCase/Test*/*IT），要么走登记闭集"
                        + "并写明「类名是文档冻结标识不可改」的依据；靠模块 pom 随手补具名 include 而不登记，"
                        + "就是把本批要收口的半截子改动再复制一遍");
        for (String rel : oddNamed) {
            String simple = rel.substring(rel.lastIndexOf('/') + 1);
            assertTrue(NAMED_INCLUDE_REGISTRY.stream().anyMatch(n -> simple.equals(n.fileName() + ".java")),
                    rel + " 属具名面却不在 NAMED_INCLUDE_REGISTRY 里（模块 pom 里那枚 include 无人认领）");
        }
    }

    @Test
    @DisplayName("模块 pom 覆盖 includes：五形态一个不能少，多出的只能是指名登记且双证据在位的具名类（双向棘轮）")
    void moduleIncludesReDeclareRootFormsAndNameNothingElse() throws IOException {
        List<String> problems = includeProblems(locateRepoRoot(), NAMED_INCLUDE_REGISTRY);
        assertTrue(problems.isEmpty(), "模块侧 surefire includes 配置失真：\n  " + String.join("\n  ", problems));
        assertEquals(NAMED_INCLUDE_LEGS_BASELINE, liveNamedIncludeCount(locateRepoRoot().resolve("ecos_backend")),
                "仓里实扫的具名 include 枚数与登记表基线不符（独立钉值，禁由登记表推导）");
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

    @Test
    @DisplayName("夹具⑦：模块覆盖 includes 漏形态／具名项未登记／登记项失效／证据缺失——四种失真各自变红，补齐转绿")
    void moduleIncludeFailuresAreVisible(@TempDir Path tmp) throws IOException {
        Fixture fx = new Fixture(tmp).withRootIncludes(ROOT_FORMS_BASELINE);

        // ① 只声明五形态、无具名项 ⇒ 绿（正向对照，防把「覆盖语义」判成恒红）
        fx.modulePom("svc/g", "<includes>" + join(ROOT_FORMS_BASELINE) + "</includes>");
        assertEquals(0, fx.findIncludeProblems(NO_NAMED_INCLUDES).size(),
                "模块把根形态完整重声明且无具名项时不得报失真：" + fx.findIncludeProblems(NO_NAMED_INCLUDES));

        // ② 漏一枚根形态 ⇒ 红（该模块的 *IT 面会从这里起静掉，而 mvn 照旧绿）
        fx.modulePom("svc/g", "<includes><include>**/Test*.java</include>"
                + "<include>**/*Test.java</include><include>**/*Tests.java</include>"
                + "<include>**/*TestCase.java</include></includes>");
        List<String> missingForm = fx.findIncludeProblems(NO_NAMED_INCLUDES);
        assertEquals(1, missingForm.size(), missingForm.toString());
        assertTrue(missingForm.get(0).contains("**/*IT.java"), missingForm.toString());

        // ③ 多出一枚未登记的具名 include ⇒ 红
        fx.modulePom("svc/g", "<includes>" + join(ROOT_FORMS_BASELINE)
                + "<include>**/AdHocNamedCase.java</include></includes>");
        List<String> unregistered = fx.findIncludeProblems(NO_NAMED_INCLUDES);
        assertEquals(1, unregistered.size(), unregistered.toString());
        assertTrue(unregistered.get(0).contains("AdHocNamedCase"), unregistered.toString());

        // ④ 具名项登记齐全＋双证据在位 ⇒ 绿；随后任撤一样证据即红（禁「登记过＝现在还成立」）
        NamedInclude reg = new NamedInclude("svc/g/pom.xml", "**/AdHocNamedCase.java",
                "docs/fake.md", "mvn -Dtest=AdHocNamedCase#t");
        fx.addTestSource("svc/g", "AdHocNamedCase.java", "class AdHocNamedCase { @Test void t() {} }");
        fx.writeAt("docs/fake.md", "验收：mvn -Dtest=AdHocNamedCase#t");
        assertEquals(0, fx.findIncludeProblems(List.of(reg)).size(),
                "具名项已登记且文档/类文件双证据在位时应为绿");

        // ④b 类文件被改名合形态、具名 include 却还挂着 ⇒ 登记项失效即红
        fx.modulePom("svc/g", "<includes>" + join(ROOT_FORMS_BASELINE) + "</includes>");
        List<String> stale = fx.findIncludeProblems(List.of(reg));
        assertEquals(1, stale.size(), "具名 include 已从模块 pom 消失，登记项必须报失效：" + stale);
        assertTrue(stale.get(0).startsWith("STALE-NAMED-INCLUDE"), stale.toString());

        // ④c 文档锚点不含冻结的验收命令 ⇒ 证据失效即红
        fx.modulePom("svc/g", "<includes>" + join(ROOT_FORMS_BASELINE)
                + "<include>**/AdHocNamedCase.java</include></includes>");
        fx.writeAt("docs/fake.md", "这里没有那条验收命令");
        List<String> noEvidence = fx.findIncludeProblems(List.of(reg));
        assertEquals(1, noEvidence.size(), "文档锚点失去冻结标识必须变红：" + noEvidence);
        assertTrue(noEvidence.get(0).contains("证据缺失"), noEvidence.toString());
    }

    // ── 核心实现 ─────────────────────────────────────────

    /** 夹具用：空具名登记清单（真仓登记表只减不增，夹具需要各自的独立清单）。 */
    private static final List<NamedInclude> NO_NAMED_INCLUDES = List.of();

    /**
     * 一枚具名 include 的登记项。
     *
     * @param pomRel                  相对 {@code ecos_backend/} 的 pom 路径
     * @param include                 该 pom 里的非形态 include
     * @param docRel                  相对仓库根的文档锚点（冻结该验收类名的设计分册）
     * @param frozenAcceptanceCommand 文档里必须原样存在的验收命令
     */
    private record NamedInclude(String pomRel, String include, String docRel, String frozenAcceptanceCommand) {

        /** 具名 include 形态去掉前缀与 {@code .java} 后缀后的类名。 */
        String fileName() {
            String s = include.startsWith("**/") ? include.substring(3) : include;
            int dot = s.lastIndexOf(".java");
            return dot < 0 ? s : s.substring(0, dot);
        }
    }

    /**
     * 模块侧 surefire includes 的失真清单：漏根形态／具名项未登记／具名项证据失效／登记项在仓里已不存在。
     * 真仓与夹具共用同一段判据，夹具才能证明「同一套规则能变红」。
     */
    private static List<String> includeProblems(Path repoRoot, List<NamedInclude> registry) throws IOException {
        Path backend = repoRoot.resolve("ecos_backend");
        List<String> out = new ArrayList<>();
        List<NamedInclude> claimed = new ArrayList<>();
        for (Path pom : modulePoms(backend)) {
            String pomRel = backend.relativize(pom).toString().replace('\\', '/');
            List<String> inc = surefireIncludesOf(Files.readString(pom, StandardCharsets.UTF_8));
            for (String form : ROOT_FORMS_BASELINE) {
                if (!inc.isEmpty() && !inc.contains(form)) {
                    out.add(pomRel + " 覆盖 surefire includes 却漏了根形态 " + form
                            + "（显式 includes 是覆盖语义 ⇒ 该形态在本模块的整批活动测试面从 mvn test 静掉，"
                            + "而 mvn test 照旧绿）");
                }
            }
            for (String extra : inc) {
                if (ROOT_FORMS_BASELINE.contains(extra)) {
                    continue;
                }
                NamedInclude reg = registry.stream()
                        .filter(n -> n.pomRel().equals(pomRel) && n.include().equals(extra))
                        .findFirst().orElse(null);
                if (reg == null) {
                    out.add(pomRel + " 出现未登记的具名 include " + extra
                            + "（具名豁免面必须闭集登记；补进 NAMED_INCLUDE_REGISTRY 要同时给文档冻结依据）");
                    continue;
                }
                claimed.add(reg);
                String miss = missingEvidence(repoRoot, pom, reg);
                if (miss != null) {
                    out.add(pomRel + " 的具名 include " + extra + " 证据缺失：" + miss);
                }
            }
        }
        for (NamedInclude n : registry) {
            if (!claimed.contains(n)) {
                out.add("STALE-NAMED-INCLUDE(登记项在仓里已不存在，须删除该行) "
                        + n.pomRel() + " -> " + n.include());
            }
        }
        return out;
    }

    /** {@code null}＝双证据在位；否则给出失效说明。 */
    private static String missingEvidence(Path repoRoot, Path pomFile, NamedInclude reg) throws IOException {
        Path doc = repoRoot.resolve(reg.docRel());
        if (!Files.isRegularFile(doc)) {
            return "文档锚点不存在：" + reg.docRel();
        }
        if (!Files.readString(doc, StandardCharsets.UTF_8).contains(reg.frozenAcceptanceCommand())) {
            return "文档 " + reg.docRel() + " 里已找不到冻结的验收标识「" + reg.frozenAcceptanceCommand() + "」";
        }
        Path srcTest = pomFile.getParent().resolve("src/test/java");
        if (!Files.isDirectory(srcTest)) {
            return "该模块没有 src/test/java 目录";
        }
        List<Path> hits;
        try (Stream<Path> w = Files.walk(srcTest)) {
            hits = w.filter(p -> p.getFileName().toString().equals(reg.fileName() + ".java")).toList();
        }
        if (hits.size() != 1) {
            return "具名类文件实扫 " + hits.size() + " 枚（应为 1 枚）：" + reg.fileName();
        }
        if (!hasActiveTestAnnotation(hits.get(0))) {
            return hits.get(0) + " 已不带活动测试注解";
        }
        return null;
    }

    /** 仓里实际存在的非形态 include 枚数（独立钉值用，禁由登记表推导）。 */
    private static int liveNamedIncludeCount(Path backend) throws IOException {
        int n = 0;
        for (Path pom : modulePoms(backend)) {
            for (String inc : surefireIncludesOf(Files.readString(pom, StandardCharsets.UTF_8))) {
                if (!ROOT_FORMS_BASELINE.contains(inc)) {
                    n++;
                }
            }
        }
        return n;
    }

    /** 模块 pom（排根 pom、target/、archive/），排序保证信息确定性。 */
    private static List<Path> modulePoms(Path backend) throws IOException {
        try (Stream<Path> walk = Files.walk(backend)) {
            return walk.filter(p -> p.getFileName().toString().equals("pom.xml"))
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return !s.contains("/target/") && !s.contains("/archive/");
                    })
                    .filter(p -> !backend.relativize(p).toString().replace('\\', '/').equals("pom.xml"))
                    .sorted().toList();
        }
    }

    /** 文件名含 Gate 的测试面（相对 ecos_backend），用于「*Gate 命名覆盖性」的正向对照。 */
    private static List<String> gateNamedTestFiles(Path backend) throws IOException {
        try (Stream<Path> walk = Files.walk(backend)) {
            return walk.filter(RepoTestSurfaceExecutabilityTest::isTestSourceFile)
                    .filter(p -> p.getFileName().toString().contains("Gate"))
                    .map(p -> backend.relativize(p).toString().replace('\\', '/'))
                    .sorted().toList();
        }
    }

    /** 带活动测试注解、却不被根 pom 五形态命中的测试面（相对 ecos_backend）＝具名豁免面。 */
    private static List<String> nonFormNamedTestFiles(Path backend) throws IOException {
        List<String> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(backend)) {
            for (Path p : walk.filter(RepoTestSurfaceExecutabilityTest::isTestSourceFile).sorted().toList()) {
                if (!hasActiveTestAnnotation(p)) {
                    continue;
                }
                Path srcTestJava = srcTestJavaDirOf(p);
                if (srcTestJava == null) {
                    continue;
                }
                String rel = srcTestJava.relativize(p).toString().replace('\\', '/');
                boolean matched = ROOT_FORMS_BASELINE.stream().anyMatch(form -> antMatches(form, rel));
                if (!matched) {
                    out.add(backend.relativize(p).toString().replace('\\', '/'));
                }
            }
        }
        return out;
    }

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

        /** 夹具文档锚点（相对夹具根，与真仓一样从仓库根起算）。 */
        void writeAt(String rel, String body) {
            write(root.resolve(rel), body);
        }

        List<String> findDead() throws IOException {
            return scanDead(root.resolve("ecos_backend"), rootIncludes, NO_EXEMPTIONS);
        }

        List<String> findDead(List<String> exemptions) throws IOException {
            return scanDead(root.resolve("ecos_backend"), rootIncludes, exemptions);
        }

        /** 与真仓共用同一段判据：模块 includes 失真清单。 */
        List<String> findIncludeProblems(List<NamedInclude> registry) throws IOException {
            return includeProblems(root, registry);
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
