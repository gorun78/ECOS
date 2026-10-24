package com.chinacreator.gzcm.common.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.chinacreator.gzcm.common.testscan.SourceCommentStripper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * 「宽松降级＝异常腿直接 {@code return true}」面棘轮门（差距 <b>D-B4-88</b> 登记件，
 * 首枚收口件为 <b>D-B4-84</b>，#142/B4-3e-54）。
 *
 * <p><b>为什么存在</b>：#142/B4-3e-52／53 连续收口的两枚折叠缺陷（D-B4-82／D-B4-83）都靠
 * 「注释自证宽容」被抓出来——{@code PreconditionEngine.evaluateAbac} 的「ABAC not configured,
 * allowing by default」与 {@code checkUserRole} 的「宽容模式：表不存在时默认通过」是同一形态：
 * <b>判定通道失败被当成判定通过</b>。一处违架构铁律 #3（security 不可用默认 DENY），一处违详设
 * §4.7.2（不可用 ≠ 无权限 ≠ 空结果）。该形态此前<b>没有任何门禁持有</b>：既有棘轮门只数
 * {@code ApiResponse.xxx(...)} 出口面与状态行面，而这类腿根本不产出信封——它在布尔返回值上就把
 * 「查不了」洗成了「可以」。没有面集，收口就只是逐枚救火，故本门把整条面钉成可复跑读数。</p>
 *
 * <p><b>判据（本门口径）</b>：主源码面（{@code src/main/java}，排除 {@code target/}／{@code archive/}）
 * 经 {@link SourceCommentStripper} 剥注释后，定位每个 {@code catch (…) \{} 的<b>字面量感知</b>配对
 * 块，只在该块的<b>顶层语句腿</b>（不含嵌套 {@code \{…\}} 内部）里数 {@code return true;}。
 * 三条设计约束都有反面取证（见 {@link #fixtureSeparatesTopLevelFromNestedLegs()}）：</p>
 * <ol>
 *   <li><b>块边界必须按花括号配对推进</b>，不能用「往后 N 字符」的窗口——实测朴素窗口会把
 *       {@code catch} 之后<b>同方法内的正常 {@code return true;} 兜底腿</b>（如
 *       {@code TaskSchedulerServiceImpl} 依赖检查循环末、{@code MemoryEventBusFallbackCondition}
 *       遍历末）计成违例，也会把 {@code catch} 块后的公共出口（data-engine
 *       {@code QualityServiceImpl}）计成违例；本批以 {@code assertEquals(3, naiveWindow, …)}
 *       把这条窗口口径钉成反面证据，夹具一旦被改动即红；</li>
 *   <li><b>字符串字面量内的花括号不可参与配对</b>——违例现场的日志语句普遍带 {@code \{\}} 占位符
 *       （正是 {@code PreconditionEngine} 原形态那枚），朴素深度计数会提前闭合而漏判；</li>
 *   <li><b>注释里的 {@code return true;} 不可计入</b>——复用单源剥器，与 D-B4-40 口径一致。</li>
 * </ol>
 *
 * <p><b>双向</b>：面集上升＝新写了一条宽松降级腿；面集下降＝基线陈旧，收口批必须同批下调
 * {@link #BASELINE_FACE}／{@link #BASELINE_SITES} 并在台账留痕。基线按「文件相对路径 → 枚数」
 * 整张比对（不只比总数），防止「修掉一枚 A 同时新写一枚 B」的总数对冲。</p>
 *
 * <p><b>当前面状态（#142/B4-3e-58 起）＝零枚</b>：{@link #BASELINE_FACE} 为空表，本门由「棘轮」
 * 收紧为「零容忍」——面上再出现任何一枚都直接红，不存在「还有存量所以先放行」的中间态。
 * 沿革与逐枚收口形态见 {@link #BASELINE_FACE} 注释。</p>
 *
 * <p><b>已声明盲区</b>（随 <b>D-B4-72</b> 判据扩展收口，不在本门静默放行）：
 * ① 顶层只数 {@code return true;} 字面形态，{@code return Boolean.TRUE;} 与「先把 true 赋给局部
 * 变量再返回」不在其内；② 嵌套块内的宽松降级（{@code if (…) \{ return true; \}}）属故意不计——
 * 计了会把「按条件返回」的正常代码吞进来，代价是该形态需另案；③ lambda 体内的 {@code return true;}
 * 与本方法顶层不可分辨；④ 三目/单语句无花括号的 {@code catch} 体（{@code catch (Exception e) return true;}）
 * 在本仓不存在（Java 语法要求 {@code catch} 体必须是块），故不需处理。</p>
 */
@Timeout(180)
class LenientDegradeTrueFaceRatchetTest {

    /** {@code catch (…)` 头到其开括号（参数表内不含括号，实测全仓主源面成立）。 */
    private static final Pattern CATCH_HEAD = Pattern.compile("catch\\s*\\([^()]*\\)\\s*\\{");

    /** 违例形态：顶层语句腿里的 {@code return true;}。 */
    private static final Pattern RETURN_TRUE = Pattern.compile("\\breturn\\s+true\\s*;");

    /** 扫描面阈值：main 面实扫 java 文件数下限（读不到／路径变更 ≠ 零违例）。 */
    private static final int MIN_SCANNED_MAIN_FILES = 2000;

    private static final String ONTOLOGY_PRECONDITION_ENGINE =
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/"
            + "com/chinacreator/gzcm/engine/ontology/engine/PreconditionEngine.java";

    /** 收口件：数据源存在性预览通道（ontology）。 */
    private static final String COLLECTED_AUTO_DISCOVER_PREVIEW_CONTROLLER =
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/"
            + "com/chinacreator/gzcm/engine/ontology/controller/AutoDiscoverPreviewController.java";

    /** 收口件：认知 schema 预检（cognitive）。 */
    private static final String COLLECTED_MIND_ASSET_DAO =
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/"
            + "com/chinacreator/gzcm/engine/cognitive2/dao/MindAssetDao.java";

    /** 收口件：Agent 指标存在性探测（ai）。 */
    private static final String COLLECTED_AGENT_METRICS_SERVICE =
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/"
            + "com/chinacreator/gzcm/engine/ai/service/AgentMetricsService.java";

    /** 收口件（死面删除腿）：Neo4j 写开关（kb）。 */
    private static final String COLLECTED_RULE_GRAPH_SERVICE =
            "ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/"
            + "com/chinacreator/gzcm/engine/kb/service/RuleGraphService.java";

    /** 承载型不可用腿：抛携带 503 的根异常。 */
    private static final Pattern BRIDGE_503_CARRIER = Pattern.compile(
            "throw new DataBridgeException\\(\\s*503\\s*,\\s*503\\s*,");

    /** 承载型不可用腿：认知域前置门异常（ECOS-COG-501 → 503）。 */
    private static final Pattern COGNITIVE_503_CARRIER = Pattern.compile(
            "throw new CognitiveGuardException\\(\\s*CognitiveErrorCode\\.E501_SCHEMA_NOT_READY\\s*,");

    /** 保护腿：控制器兜底前显式放行根异常。 */
    private static final Pattern BRIDGE_PASSTHROUGH_LEG = Pattern.compile(
            "catch\\s*\\(\\s*DataBridgeException\\s+\\w+\\s*\\)");

    /**
     * 面集基线（#142/B4-3e-58 收口 D-B4-88 <b>整批余面</b>后实跑读数＝<b>0 枚／0 文件</b>；
     * 键＝仓根相对路径，值＝该文件顶层违例枚数。本表为空＝「面已清零 + 只准减不准增」的
     * 零容忍态，任何新写宽松降级腿都直接红）。
     *
     * <p><b>基线沿革（逐批读数，禁凭记忆改数）</b>：</p>
     * <ul>
     *   <li>#142/B4-3e-54 建门时收口前读数＝5 枚／5 文件，收口 {@code PreconditionEngine.checkUserRole}
     *       （<b>D-B4-84</b>）后基线落下＝<b>4 枚／4 文件</b>；</li>
     *   <li>#142/B4-3e-58（<b>D-B4-88</b>）把余下 4 枚逐枚收口 ⇒ 本表归零：
     *     <ol>
     *       <li>{@code AutoDiscoverPreviewController.datasourceExists}——数据源存在性判定通道故障
     *           折成「允许预览继续」⇒ 改抛 {@code DataBridgeException(503, 503, ECOS-SYS-503…)}，
     *           并在 {@code preview()} 的兜底前加 {@code catch (DataBridgeException) → throw e}
     *           保护腿（否则被 {@code internalError(500)} 反向折叠）；</li>
     *       <li>{@code MindAssetDao.mindTableExists}——schema 预探测故障自称「保守」实为报「表在」，
     *           与该文件 javadoc 自述的「{@code to_regclass IS NULL → 503 ECOS-COG-501}」自相矛盾
     *           ⇒ 改抛同包 {@code BeliefScorer} 既有形态的 {@code CognitiveGuardException}
     *           （503／ECOS-COG-501，{@code missing} 带表名），只有<b>真取到</b> {@code false}
     *           才走返回值；</li>
     *       <li>{@code AgentMetricsService.exists}——查询故障折成「Agent 存在」⇒ 改抛 503 载体。
     *           本枚的生效前提腿在 {@code AiErrorAdvice}：该 advice 的 {@code Exception} 兜底走
     *           {@code ErrorCodeMapper.classify}，而分类器只反射 {@code getStatus()}（根的携带通道是
     *           {@code getHttpStatus()}）⇒ 根型异常按 GENERIC 面落 {@code TOOL_BACKEND_ERROR}/502，
     *           即 D-B4-67 在 ai 宿主的副本；同批新增 {@code onBridge} 根型腿走
     *           {@code resolveHttpStatus} 单源（该腿须与
     *           {@code RootCarrierHttpStatusSingleSourceRatchetTest} 判据 A 基线同号，属主表 3→4）；</li>
     *       <li>{@code RuleGraphService.switchNeo4jEnabled}——{@code Boolean.parseBoolean} 按规范
     *           不抛异常 ⇒ {@code catch} 体<b>不可达</b>，属 D-B4-77 死代码族的<b>死面</b>而非运行期
     *           fail-open；本批按 D-B4-77 治理腿删除不可达 {@code try/catch}（语义不变）。
     *           此前「数得到优先于数得漂亮」的登记口径随收口一并作废：死面留在面上只会让基线
     *           失去「一枚＝一条真实宽松降级腿」的计数意义。</li>
     *     </ol>
     *   </li>
     * </ul>
     *
     * <p><b>同批勘察中被本判据正确排除、仍留作余项的近似形态</b>（不当成已收口）：
     * {@code data-engine QualityServiceImpl} 的「无法解析的表达式，默认通过」写在
     * {@code catch (NumberFormatException)} 块<b>之后的公共出口</b>，落点不在 {@code catch} 顶层，
     * 故本门数不到它。该枚属「无效输入折成通过」另一族，判据扩展与收口已拆到
     * <b>#142/B4-3e-59</b>（保持单主题事务）。同批新登记的相近形态：同文件
     * {@code alreadyExists} 探测腿把查询失败折成「实体不存在」（值面 falsy，不上状态行），
     * 亦归该族。</p>
     */
    private static final Map<String, Integer> BASELINE_FACE = new TreeMap<>();

    /** 面集总数基线（必须与 {@link #BASELINE_FACE} 各值之和同号，防拆细后总数对不上）。 */
    private static final int BASELINE_SITES = 0;

    /** 收口件锚点：{@code PreconditionEngine} 内承载型不可用腿数（abac ＋ hasRole 两枚，不得回退）。 */
    private static final int ANCHOR_UNAVAILABLE_LEGS = 2;

    // ── 1. 面集棘轮 ─────────────────────────────────

    @Test
    @DisplayName("宽松降级面（catch 顶层 return true）按实跑基线只减不增，面集整张比对且基线陈旧同样变红")
    void lenientDegradeTrueFaceIsRatcheted() throws IOException {
        Scan scan = scanMainFace();
        assertTrue(scan.scannedFiles >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + scan.scannedFiles + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无违例）");

        if (scan.totalSites() > BASELINE_SITES) {
            fail("宽松降级腿上升：" + scan.totalSites() + " > 基线 " + BASELINE_SITES
                    + "，面集=" + scan.face + "；判定通道失败必须显式承载（抛 503 载体）或走拒绝态，"
                    + "禁在 catch 顶层 return true");
        }
        assertEquals(BASELINE_FACE, scan.face,
                "宽松降级面集与基线不符。新增文件＝新写宽松降级腿；文件消失或枚数减少＝基线陈旧，"
                        + "收口批必须同批下调 BASELINE_FACE/BASELINE_SITES 并在台账留痕。实跑面集=" + scan.face);

        int sum = scan.face.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(BASELINE_SITES, sum,
                "面集合计必须等于总数基线，否则说明拆分口径与扫描口径不一致");
    }

    // ── 2. 收口件锚点（D-B4-84）─────────────────────

    @Test
    @DisplayName("D-B4-84 收口件锚点：PreconditionEngine 退出宽松降级面，且两枚不可用承载腿在位")
    void collectedRoleCheckLegMustCarryExplicitly() throws IOException {
        Path file = locateRepoRoot().resolve(ONTOLOGY_PRECONDITION_ENGINE);
        assertTrue(Files.exists(file), "收口件文件不在位：" + file);
        String src = SourceCommentStripper.strip(Files.readString(file, StandardCharsets.UTF_8));

        assertFalse(src.contains("宽容模式"), "收口件回退：主源码面再现「宽容模式」自证注释");

        int carriers = 0;
        Matcher m = Pattern.compile("throw new DataBridgeException\\(\\s*503\\s*,\\s*503\\s*,")
                .matcher(src);
        while (m.find()) {
            carriers++;
        }
        assertEquals(ANCHOR_UNAVAILABLE_LEGS, carriers,
                "PreconditionEngine 承载型不可用腿数与凭证不符（回退即红）：实跑 " + carriers
                        + "，应为 abac（D-B4-82）＋ hasRole（D-B4-84）两枚");

        Scan scan = scanMainFace();
        assertFalse(scan.face.containsKey(ONTOLOGY_PRECONDITION_ENGINE.replace('\\', '/')),
                "收口件仍在宽松降级面上：" + scan.face);
    }

    // ── 3. 收口件锚点（D-B4-88 四枚）──────────────────

    @Test
    @DisplayName("D-B4-88 收口件锚点：四枚宽松降级腿逐枚改判后承载腿在位、死面方法体不再有 catch")
    void collectedFourLenientLegsCarryExplicitly() throws IOException {
        // ① ontology 预览通道：503 载体 + 兜底前的显式放行腿
        String preview = stripped(COLLECTED_AUTO_DISCOVER_PREVIEW_CONTROLLER);
        assertEquals(1, count(BRIDGE_503_CARRIER, preview),
                "AutoDiscoverPreviewController 的 503 承载腿数与凭证不符（回退即红）");
        assertEquals(1, count(BRIDGE_PASSTHROUGH_LEG, preview),
                "AutoDiscoverPreviewController 缺 catch (DataBridgeException) → throw e 保护腿："
                        + "只在服务侧抛载体而端点仍兜底折叠＝反向折叠，状态行照旧丢");

        // ② cognitive schema 预检：改抛认知域前置门（503／ECOS-COG-501）
        String mindDao = stripped(COLLECTED_MIND_ASSET_DAO);
        assertEquals(1, count(COGNITIVE_503_CARRIER, mindDao),
                "MindAssetDao.mindTableExists 的 CognitiveGuardException(E501) 承载腿不在位");

        // ③ ai 指标存在性探测：503 载体
        assertEquals(1, count(BRIDGE_503_CARRIER, stripped(COLLECTED_AGENT_METRICS_SERVICE)),
                "AgentMetricsService.exists 的 503 承载腿数与凭证不符");

        // ④ kb Neo4j 开关：不可达 catch 已删——方法体内不得再出现 catch
        String kb = stripped(COLLECTED_RULE_GRAPH_SERVICE);
        int sig = kb.indexOf("public boolean switchNeo4jEnabled()");
        assertTrue(sig >= 0, "RuleGraphService.switchNeo4jEnabled 签名消失，判据锚点失效");
        int open = kb.indexOf('{', sig);
        int close = matchingBrace(kb, open);
        assertTrue(close > open, "switchNeo4jEnabled 方法体花括号不闭合，判据不可信");
        String body = kb.substring(open, close + 1);
        assertFalse(body.contains("catch"),
                "死面回退：switchNeo4jEnabled 体内再现 catch（Boolean.parseBoolean 不抛异常，"
                        + "该腿不可达＝D-B4-77 死面而非运行期降级）");

        Scan scan = scanMainFace();
        for (String collected : new String[] {COLLECTED_AUTO_DISCOVER_PREVIEW_CONTROLLER,
                COLLECTED_MIND_ASSET_DAO, COLLECTED_AGENT_METRICS_SERVICE,
                COLLECTED_RULE_GRAPH_SERVICE}) {
            assertFalse(scan.face.containsKey(collected),
                    "D-B4-88 收口件回退到宽松降级面上：" + collected + "，面集=" + scan.face);
        }
    }

    // ── 4. 判据自证（能变红 + 不许改过头）───────────

    @Test
    @DisplayName("合成夹具取证：顶层违例计入、嵌套块与 catch 之外的 return true 不计、字面量花括号不破深度")
    void fixtureSeparatesTopLevelFromNestedLegs() {
        String src = """
                class Fixture {
                    void a() {
                        try { x(); } catch (Exception e) {
                            log.debug("noisy braces: {}", e.getMessage());
                            return true;
                        }
                    }
                    void b() {
                        try { x(); } catch (Exception e) {
                            if (flag) {
                                return true;
                            }
                            return false;
                        }
                    }
                    void c() {
                        try { x(); } catch (Exception e) {
                            log.debug("swallowed");
                        }
                        return true;
                    }
                    // void d() { try { x(); } catch (Exception e) { return true; } }
                }
                """;
        String stripped = SourceCommentStripper.strip(src);
        List<Integer> lines = lenientDegradeLines(stripped);
        assertEquals(1, lines.size(),
                "判据须恰好吃到 a() 那一枚（顶层 return true），并排除 b() 嵌套腿、"
                        + "c() 的 catch 之外兜底腿、注释内字面形态；实跑行号=" + lines);

        // 朴素「往后 200 字符窗口」口径会数到 3 枚（a/b/c），据此证明本门块配对口径的必要性
        int naiveWindow = 0;
        Matcher head = CATCH_HEAD.matcher(stripped);
        while (head.find()) {
            String tail = stripped.substring(head.end(),
                    Math.min(stripped.length(), head.end() + 200));
            if (RETURN_TRUE.matcher(tail).find()) {
                naiveWindow++;
            }
        }
        assertEquals(3, naiveWindow,
                "反面证据失效：窗口口径已不再高报，说明夹具或 CATCH_HEAD 被改动，"
                        + "本门「块配对 vs 窗口」的归因失去依据");
    }

    @Test
    @DisplayName("夹具落盘复扫：@TempDir 内的违例文件被扫出、仓库外夹具不留进主源码面")
    void fixtureFileOnDiskIsScannedAndCleaned(@TempDir Path tmp) throws IOException {
        Path bad = tmp.resolve("MutableFixture.java");
        Files.writeString(bad, """
                class MutableFixture {
                    boolean failOpen() {
                        try { return probe(); } catch (Exception e) {
                            return true;
                        }
                    }
                }
                """, StandardCharsets.UTF_8);
        assertEquals(1, lenientDegradeLines(
                SourceCommentStripper.strip(Files.readString(bad, StandardCharsets.UTF_8))).size(),
                "判据对真实文件失效（读不到／剥不动）＝假绿形态");

        // 把违例腿改成显式承载后必须归零：证明本门「能变红」不是靠总数兜住
        Files.writeString(bad, """
                class MutableFixture {
                    boolean failClosed() {
                        try { return probe(); } catch (Exception e) {
                            throw new DataBridgeException(503, 503, "ECOS-SYS-503: 判定通道不可用");
                        }
                    }
                }
                """, StandardCharsets.UTF_8);
        assertEquals(0, lenientDegradeLines(
                SourceCommentStripper.strip(Files.readString(bad, StandardCharsets.UTF_8))).size(),
                "显式承载形态必须不在违例面上，否则收口批无法下调基线");
    }

    // ── 扫描实现 ────────────────────────────────────

    /** 读仓根相对路径的主源码并剥注释（锚点判据与门面判据同一口径）。 */
    private static String stripped(String repoRelPath) throws IOException {
        Path file = locateRepoRoot().resolve(repoRelPath);
        assertTrue(Files.exists(file), "收口件文件不在位：" + file);
        return SourceCommentStripper.strip(Files.readString(file, StandardCharsets.UTF_8));
    }

    private static int count(Pattern p, String src) {
        int n = 0;
        Matcher m = p.matcher(src);
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static final class Scan {
        int scannedFiles;
        final Map<String, Integer> face = new TreeMap<>();

        int totalSites() {
            return face.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private static Scan scanMainFace() throws IOException {
        Path root = locateRepoRoot();
        Scan scan = new Scan();
        List<Path> files = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), files);
        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            List<Integer> hits = lenientDegradeLines(stripped);
            if (!hits.isEmpty()) {
                scan.face.put(rel, hits.size());
            }
        }
        scan.scannedFiles = files.size();
        return scan;
    }

    /**
     * 返回每个「{@code catch} 块顶层 {@code return true;}」的行号（1 起，与剥注释后文本一致）。
     */
    static List<Integer> lenientDegradeLines(String strippedSrc) {
        List<Integer> lines = new ArrayList<>();
        Matcher head = CATCH_HEAD.matcher(strippedSrc);
        while (head.find()) {
            int open = head.end() - 1;
            int close = matchingBrace(strippedSrc, open);
            if (close < 0) {
                continue;
            }
            String topLevel = maskNestedBlocks(strippedSrc.substring(open + 1, close));
            Matcher rt = RETURN_TRUE.matcher(topLevel);
            while (rt.find()) {
                lines.add(1 + countNewlines(strippedSrc, open + 1 + rt.start()));
            }
        }
        return lines;
    }

    /** 从开括号起做字面量感知的花括号配对，返回与之配对的闭括号下标；不闭合返回 -1。 */
    private static int matchingBrace(String src, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(src, i);
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** 把 catch 体内的嵌套 {@code \{…\}} 整段抹平，只留顶层语句腿（字面量整体替换为空格）。 */
    private static String maskNestedBlocks(String body) {
        StringBuilder out = new StringBuilder(body.length());
        int depth = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"' || c == '\'') {
                int end = skipLiteral(body, i);
                out.append(' ');
                i = end;
                continue;
            }
            if (c == '{') {
                depth++;
                out.append(' ');
                continue;
            }
            if (c == '}') {
                depth--;
                out.append(' ');
                continue;
            }
            out.append(depth == 0 ? c : ' ');
        }
        return out.toString();
    }

    /** 返回字面量结束引号的下标（含文本块），调用方以 {@code i = end} 继续推进。 */
    private static int skipLiteral(String src, int quoteIndex) {
        char q = src.charAt(quoteIndex);
        if (q == '"' && quoteIndex + 2 < src.length() && src.charAt(quoteIndex + 1) == '"'
                && src.charAt(quoteIndex + 2) == '"') {
            int i = quoteIndex + 3;
            while (i < src.length()) {
                if (src.charAt(i) == '\\') {
                    i += 2;
                    continue;
                }
                if (src.charAt(i) == '"' && i + 2 < src.length() && src.charAt(i + 1) == '"'
                        && src.charAt(i + 2) == '"') {
                    return i + 2;
                }
                i++;
            }
            return src.length() - 1;
        }
        int i = quoteIndex + 1;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '\n') {
                return i;
            }
            if (c == q) {
                return i;
            }
            i++;
        }
        return src.length() - 1;
    }

    private static int countNewlines(String src, int endIndexExclusive) {
        int n = 0;
        for (int i = 0; i < endIndexExclusive && i < src.length(); i++) {
            if (src.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    private static void collectJavaUnder(Path dir, List<Path> out) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .map(p -> p.toAbsolutePath().normalize())
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return s.contains("/src/main/java/")
                                && !s.contains("/target/")
                                && !s.contains("/archive/");
                    })
                    .sorted()
                    .forEach(out::add);
        }
    }

    private static Path locateRepoRoot() {
        Path cur = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path p = cur; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("ecos_backend"))) {
                return p;
            }
        }
        return fail("无法定位仓库根（找不到 ecos_backend/），扫描面不可信：user.dir=" + cur);
    }
}
