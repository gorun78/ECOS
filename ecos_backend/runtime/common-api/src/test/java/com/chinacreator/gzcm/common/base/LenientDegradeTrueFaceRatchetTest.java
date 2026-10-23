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

    /**
     * 面集基线（#142/B4-3e-54 本批实跑读数＝<b>4 枚／4 文件</b>，判据见类注释；
     * 键＝仓根相对路径，值＝该文件顶层违例枚数）。
     *
     * <p>收口前同判据实跑读数＝<b>5 枚／5 文件</b>（#142/B4-3e-54 变异跑取证：把
     * {@code PreconditionEngine} 的 {@code catch} 腿退回原形态后本门 4 枚案报 <b>2 failures</b> ⇒
     * {@code lenientDegradeTrueFaceIsRatcheted} 打印「门禁结果：违例 5 ＞ 基线 4」且面集含
     * {@code PreconditionEngine.java=1}；{@code collectedRoleCheckLegMustCarryExplicitly} 报
     * 「实读 1，应为 abac（D-B4-82）与 hasRole（D-B4-84）两枚」{@code expected: <2> but was: <1>}），
     * 其中一枚由本批收口（差距
     * <b>D-B4-84</b>：{@code checkUserRole} 的 {@code catch (Exception)} 后 {@code return true}，
     * 注释自证「宽容模式：表不存在时默认通过」）⇒ 基线 −1。余下 4 枚逐枚实读源码确认
     * {@code return true;} 位于该 {@code catch} 块<b>顶层</b>：</p>
     * <ul>
     *   <li>{@code AutoDiscoverPreviewController}：数据源存在性＋归属校验失败 ⇒ 「宽松降级：允许预览继续」；</li>
     *   <li>{@code MindAssetDao.mindTableExists}：schema 预探测抛异常 ⇒ 注释自称「保守」实为宽松，
     *       与该文件 javadoc 自述的「{@code to_regclass IS NULL → 503 ECOS-COG-501}」自相矛盾；</li>
     *   <li>{@code AgentMetricsService}：{@code exists()} 查询失败 ⇒ 「宽松降级：不判 404，继续走主流程」；</li>
     *   <li>{@code RuleGraphService.switchNeo4jEnabled}：{@code Boolean.parseBoolean} 按规范不抛异常
     *       ⇒ 该 {@code catch} 体<b>不可达</b>，属 D-B4-77 死代码族的<b>死面</b>，不是运行期 fail-open；
     *       仍留在面集内不豁免，因为「数得到」优先于「数得漂亮」，删除它属 D-B4-77 治理腿。</li>
     * </ul>
     *
     * <p><b>同批勘察中被本判据正确排除的一枚近似形态</b>（如实登记，不当成已收口）：
     * {@code data-engine QualityServiceImpl} 的「无法解析的表达式，默认通过」写在
     * {@code catch (NumberFormatException)} 块<b>之后的公共出口</b>，即表达式既不匹配前缀语法也不
     * 数值化时统一返回通过——违的是同一条 §4.7.2，但落点不在 {@code catch} 顶层，故本门数不到它。
     * 该枚属「无效输入折成通过」另一族，登记为 <b>D-B4-88</b> 余项的判据扩展面（随 D-B4-72 收口）。</p>
     */
    private static final Map<String, Integer> BASELINE_FACE = new TreeMap<>(Map.of(
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/"
            + "com/chinacreator/gzcm/engine/cognitive2/dao/MindAssetDao.java", 1,
            "ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/"
            + "com/chinacreator/gzcm/engine/kb/service/RuleGraphService.java", 1,
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/"
            + "com/chinacreator/gzcm/engine/ai/service/AgentMetricsService.java", 1,
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/"
            + "com/chinacreator/gzcm/engine/ontology/controller/AutoDiscoverPreviewController.java", 1));

    /** 面集总数基线（必须与 {@link #BASELINE_FACE} 各值之和同号，防拆细后总数对不上）。 */
    private static final int BASELINE_SITES = 4;

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

    // ── 3. 判据自证（能变红 + 不许改过头）───────────

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
