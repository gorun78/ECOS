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

/**
 * 「大小写折叠 switch 的大写字面 case」面棘轮门（差距 <b>D-B4-89</b> 登记件，#142/B4-3e-59）。
 *
 * <p><b>为什么存在</b>：#142/B4-3e-54 收口 D-B4-84 时查出同文件同一枚缺陷的另一半——
 * {@code PreconditionEngine} 的 {@code switch (actionType.toLowerCase())} 的 subject 已归一小写，
 * 而 {@code case "hasRole"} 标签混写大小写 ⇒ 带 {@code hasRole} 前置条件的动作<b>一律落
 * default</b>，该标签腿根本不可达（死面而非运行期 fail-open）。3e-54 已收口那一枚现场（标签改名
 * {@code case "hasrole"}），并声明盲区：<b>粗筛是文件级交集</b>（同文件既有大写 {@code case} 标签
 * 又有 {@code toLowerCase}）而非 {@code switch} 级配对——「哪个标签死在哪个 subject 下」没有判据
 * 持有。3e-54 的粗筛余面实读＝0 枚／0 文件（唯一幸存文件 ai {@code ToolExecutorService}
 * 第 1555 行 {@code switch (severity.toLowerCase())} 逐枚标签实读全小写）。没有 switch 级判据，
 * 「余面 0」只是逐文件人眼巡视为准，下次再长一枚要重做全量人眼——本门把这条配对判据钉成可复跑
 * 读数，基线按「文件相对路径 → 枚数」整张比对，双向只减不增。</p>
 *
 * <p><b>判据（本门口径）</b>：主源码面（{@code src/main/java}，排除 {@code target/}／
 * {@code archive/}）经 {@link SourceCommentStripper} 剥注释后，对每个 {@code switch (…)} 做
 * <b>字面量感知</b>的 subject 括号配对与体花括号配对；仅当 <b>subject 文本含
 * {@code toLowerCase}</b>（裸调用或带参，如 {@code Locale.ROOT}，大小写不敏感容错）时，把<b>该
 * switch 体内</b>每个含任一 A–Z 大写的 {@code case "…":} 字符串字面量计为一枚死面。
 * 三条口径约束都有反面取证（见 {@link #fixtureSeparatesPairingFromLabelOnly()}）：</p>
 * <ol>
 *   <li><b>必须 subject 感知而非标签裸扫</b>——对未折叠的 switch（保留原始形态做精确匹配的分派表）
 *       逐枚挨打会把正常代码吞进来；夹具里 {@code switch (label)} 的 {@code case "LIVE"} 属故意
 *       不计，配对 4 枚 vs 裸口径 5 枚，取证钉成 {@code assertEquals(5, coarse, …)}；</li>
 *   <li><b>subject 提取必须括号配对</b>，不能用「第一个 {@code )}」截断——夹具 d() 的
 *       {@code switch (normalize(v).toLowerCase())} 让截断口径误读 subject 丢失折叠判定而漏判，
 *       必比配对口径恰少 1 枚，取证钉成 {@code assertEquals(lines.size() - 1, naive, …)}；</li>
 *   <li><b>注释里的 {@code case} 不可计入</b>——复用单源剥器，与 D-B4-40／D-B4-88 门同口径。</li>
 * </ol>
 *
 * <p><b>双向</b>：面集上升＝对着已归一 subject 新写了一枚永不可达标签（死面）；面集下降＝基线陈旧，
 * 收口批必须同批下调 {@link #BASELINE_FACE}／{@link #BASELINE_SITES} 并在台账留痕。</p>
 *
 * <p><b>当前面状态（#142/B4-3e-59 建门时实跑读数）＝零枚</b>：3e-54 已把唯一现场
 * （{@code PreconditionEngine} {@code case "hasRole"}）改名，全后端主源码面 switch 级配对复扫
 * 0 枚／0 文件 ⇒ 本门建门即零容忍，面上再出现任何一枚都直接红。</p>
 *
 * <p><b>已声明盲区</b>（随 <b>D-B4-72</b> 判据扩展收口，不在本门静默放行）：① subject 为条件分派
 * 形态（如 {@code switch (ok ? code.toLowerCase() : "")}）时，只要 subject 文本含
 * {@code toLowerCase} 即触发配对检查，但「没走归一分支的组合」读不出来（本形态现读面不存在，登记
 * 不豁免）；② subject 为<b>已预先折叠好的局部变量</b>（方法外 {@code String v = s.toLowerCase();}
 * 再 {@code switch (v)}）⇒ 文本面读不到折叠点，需另案数据流判据；③ 本门只数「大写字面 × 已折叠
 * subject」的永死形，不裁决土耳其 i 类 locale 语义差异；④ 枚举/静态常量 {@code case}（无引号
 * 字面量）不在本判据内。</p>
 */
@Timeout(180)
class SwitchCaseUppercaseDeadFaceRatchetTest {

    /** {@code switch} 头（keyword 后紧跟左括号，前后留白容忍）。 */
    private static final Pattern SWITCH_HEAD = Pattern.compile("\\bswitch\\s*\\(");

    /** subject 文本内的归一调用（裸调用或带参，如 {@code Locale.ROOT}）。 */
    private static final Pattern TO_LOWER_IN_SUBJECT =
            Pattern.compile("toLowerCase", Pattern.CASE_INSENSITIVE);

    /** 死面标签：case 后跟含任一 A–Z 大写的字符串字面量。 */
    private static final Pattern UPPERCASE_CASE_LABEL =
            Pattern.compile("case\\s*\"[^\"]*[A-Z][^\"]*\"");

    /** 反面口径（取证用）：整文件裸扫大写 case 字面量，不看 subject。 */
    private static final Pattern COARSE_LABEL =
            Pattern.compile("case\\s*\"[^\"]*[A-Z][^\"]*\"");

    /** 扫描面阈值：main 面实扫 java 文件数下限（读不到／路径变更 ≠ 零违例）。 */
    private static final int MIN_SCANNED_MAIN_FILES = 2000;

    /** 锚点件：ai 域名词严重度加权 {@code switch (severity.toLowerCase())}（3e-54 粗筛幸存文件）。 */
    private static final String ANCHOR_TOOL_EXECUTOR_SERVICE =
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/"
            + "com/chinacreator/gzcm/engine/ai/service/ToolExecutorService.java";

    /**
     * 面集基线（#142/B4-3e-59 建门时实跑读数＝<b>0 枚／0 文件</b>；键＝仓根相对路径，值＝该文件内
     * 「subject 含 toLowerCase × 含大写字面 case」的 switch 枚数。本表为空＝零容忍态：任何对着已
     * 折叠 subject 新写的大写字面标签都直接红）。
     *
     * <p><b>基线沿革（逐批读数，禁凭记忆改数）</b>：</p>
     * <ul>
     *   <li>#142/B4-3e-54（D-B4-89 登记批）：粗筛口径（文件级交集）全后端主源码面＝7 文件 16 枚
     *       含大写 case 标签，其中唯一与 {@code switch (…toLowerCase())} 同文件的 ai
     *       {@code ToolExecutorService} 第 1555 行逐枚标签实读<b>全小写</b> ⇒ 粗筛余面
     *       <b>0 枚</b>；盲区声明＝粗筛非 switch 级配对，配对判据拆到本批。</li>
     *   <li>#142/B4-3e-59（本门建门批）：switch 级配对判据对<b>同一主源码面</b>复扫＝0 枚／0 文件
     *       ⇒ 本表建门即零容忍，与 3e-54 粗筛读数两头同号，配对判据未查出粗筛漏判。</li>
     * </ul>
     */
    private static final Map<String, Integer> BASELINE_FACE = new TreeMap<>();

    /** 面集总数基线（必须与 {@link #BASELINE_FACE} 各值之和同号，防拆细后总数对不上）。 */
    private static final int BASELINE_SITES = 0;

    // ── 1. 面集棘轮 ─────────────────────────────────

    @Test
    @DisplayName("大写 case × toLowerCase subject 死面按实跑基线只减不增，面集整张比对且基线陈旧同样变红")
    void switchCaseArticle0FaceIsRatcheted() throws IOException {
        Scan scan = scanMainFace();
        assertTrue(scan.scannedFiles >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + scan.scannedFiles + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无违例）");

        if (scan.totalSites() > BASELINE_SITES) {
            fail("大写 case × toLowerCase subject 死面上升：" + scan.totalSites()
                    + " > 基线 " + BASELINE_SITES + "，面集=" + scan.face
                    + "；subject 已折叠小写的 switch 里写大写字面标签＝永不可达死面（D-B4-89），"
                    + "标签必须与 subject 归一口径同号");
        }
        assertEquals(BASELINE_FACE, scan.face,
                "死面面集与基线不符。新增文件＝新写了永不可达的大写标签；文件消失或枚数减少＝基线陈旧，"
                        + "收口批必须同批下调 BASELINE_FACE/BASELINE_SITES 并在台账留痕。实跑面集="
                        + scan.face);

        int sum = scan.face.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(BASELINE_SITES, sum,
                "面集合计必须等于总数基线，否则说明拆分口径与扫描口径不一致");
    }

    // ── 2. 锚点件（3e-54 粗筛幸存文件）────────────────

    @Test
    @DisplayName("锚点：ToolExecutorService severity switch 的标签必须仍全小写（回退即红）")
    void anchorSeveritySwitchLabelsStayLowercase() throws IOException {
        Path file = locateRepoRoot().resolve(ANCHOR_TOOL_EXECUTOR_SERVICE);
        assertTrue(Files.exists(file), "锚点文件不在位：" + file);
        String src = SourceCommentStripper.strip(Files.readString(file, StandardCharsets.UTF_8));
        Matcher head = Pattern.compile("switch\\s*\\(\\s*severity\\.toLowerCase\\(\\s*\\)\\s*\\)")
                .matcher(src);
        assertTrue(head.find(), "锚点 switch (severity.toLowerCase()) 不在位，判据锚点失效");
        int open = src.indexOf('{', head.end());
        int close = matchingBrace(src, open);
        assertTrue(close > open, "severity switch 体花括号不闭合，判据不可信");
        String body = src.substring(open, close + 1);
        // 回退即红：subject 已 toLowerCase，任何大写字面标签都只能永落 default。
        assertFalse(UPPERCASE_CASE_LABEL.matcher(body).find(),
                "锚点回退：severity switch 再现大写字面标签（应全落 default 的死面）");
        // 锚不变量：四枚全小写标签（critical/high/medium/low）必须仍全在，
        // 防止整枚 switch 被删后判据失去锚而假绿。
        int labelCount = 0;
        Matcher lb = Pattern.compile("case\\s*\"[^\"]*\":").matcher(body);
        while (lb.find()) {
            labelCount++;
        }
        assertEquals(4, labelCount,
                "severity switch 的 case 标签数与凭证不符（应为 critical/high/medium/low 四枚，"
                        + "防「整枚 switch 删掉后判据空转」）：实跑 " + labelCount);
    }

    // ── 3. 判据自证（能变红 + 不许改过头）───────────

    @Test
    @DisplayName("合成夹具取证：配对口径只数「大写标签 × 折叠 subject」，未折叠 subject 与注释不计")
    void fixtureSeparatesPairingFromLabelOnly() {
        String src = """
                class Fixture {
                    void a() {
                        switch (s.toLowerCase()) {
                            case "X":  x(); break;
                            case "x":  x(); break;
                            default:   x(); break;
                        }
                    }
                    void b() {
                        switch (s.toLowerCase(Locale.ROOT)) {
                            case "A":  a(); break;
                            case "B":  b(); break;
                            default:   b(); break;
                        }
                    }
                    void c() {
                        switch (label) {
                            case "LIVE": live(); break;
                            default:     live(); break;
                        }
                    }
                    void d() {
                        // subject 带内层括号的归一：「第一个 )」截断口径会误读 subject 而漏判本枚。
                        switch (normalize(v).toLowerCase()) {
                            case "XU": xu(v); break;
                            default:   xu(v); break;
                        }
                    }
                }
                // case "MIXED": 注释内的字面形态，剥注释后不数
                """;
        String stripped = SourceCommentStripper.strip(src);
        List<Integer> lines = deadCaseLines(stripped);
        assertEquals(4, lines.size(),
                "判据须恰好数到 a() 的 case \"X\"、b() 的 case \"A\"/\"B\"（带参归一也算）、"
                        + "d() 的 case \"XU\"（带内层括号的归一链也算）共四枚，并排除 c() 未折叠 "
                        + "subject 的 case \"LIVE\" 与注释内字面；实跑行号=" + lines);

        // 反面取证一：裸标签口径（不看 subject）会数到 5 枚（多 c() 的 case "LIVE" 假阳）：
        // 钉死本门「switch 级配对 vs 标签裸扫」归因的必要证据，夹具被改动即红。
        int coarse = 0;
        Matcher m = COARSE_LABEL.matcher(stripped);
        while (m.find()) {
            coarse++;
        }
        assertEquals(5, coarse,
                "反面证据失效：裸标签口径已不再数到 5 枚，说明夹具或 COARSE_LABEL 判据被改动，"
                        + "本门配对口径归因失去依据");

        // 反面取证二：「第一个 ) 截断 subject」口径——它会(a)截在 d() 的
        // normalize(v 处、误判 d 为 subject 未折叠；(b)截在 b() 的 toLowerCase(Locale.ROOT
        // 处，碰巧仍含 toLowerCase 字样而正确判出（但这是巧合不是能力）。
        // 配对口径读到「已折叠 switch」＝a / b / d 三枚；截断口径＝a / b 两枚。
        int pairedLowerSwitches = 0;
        Matcher head = SWITCH_HEAD.matcher(stripped);
        while (head.find()) {
            int openParen = head.end() - 1;
            int closeParen = matchingParen(stripped, openParen);
            if (closeParen < 0) {
                continue;
            }
            if (TO_LOWER_IN_SUBJECT.matcher(stripped.substring(openParen + 1, closeParen)).find()) {
                pairedLowerSwitches++;
            }
        }
        int cutLowerSwitches = 0;
        Matcher head2 = SWITCH_HEAD.matcher(stripped);
        while (head2.find()) {
            int firstClose = stripped.indexOf(')', head2.end());
            if (firstClose < 0) {
                continue;
            }
            if (TO_LOWER_IN_SUBJECT.matcher(stripped.substring(head2.end(), firstClose)).find()) {
                cutLowerSwitches++;
            }
        }
        assertEquals(pairedLowerSwitches - 1, cutLowerSwitches,
                "反面证据失效：「第一个 )」截断口径应比配对配出口恰少 1 枚（漏 d() 的 "
                        + "normalize(v).toLowerCase()），此刻同号＝夹具或 SWITCH_HEAD/TO_LOWER "
                        + "判据被改动，本门「括号配对 vs 截断」的取舍失去依据。实跑配对口径="
                        + pairedLowerSwitches + "，截断口径=" + cutLowerSwitches);

        // 配对口径漏一处 = 该处的大写标签也整枚漏判：把截断口径的 d 判定 + 体范围纳进去，
        // 得到截断口径的总数；必比配对口径 3 枚少 1 枚（少 d 的 case "XU"）。
        int naiveTotalLabels = 0;
        Matcher head3 = SWITCH_HEAD.matcher(stripped);
        while (head3.find()) {
            int firstClose = stripped.indexOf(')', head3.end());
            if (firstClose < 0) {
                continue;
            }
            if (!TO_LOWER_IN_SUBJECT
                    .matcher(stripped.substring(head3.end(), firstClose)).find()) {
                continue;
            }
            int openBrace = stripped.indexOf('{', firstClose);
            if (openBrace < 0) {
                continue;
            }
            int closeBrace = matchingBrace(stripped, openBrace);
            if (closeBrace < 0) {
                continue;
            }
            Matcher labelInBody = UPPERCASE_CASE_LABEL
                    .matcher(stripped.substring(openBrace, closeBrace));
            while (labelInBody.find()) {
                naiveTotalLabels++;
            }
        }
        assertEquals(lines.size() - 1, naiveTotalLabels,
                "反面证据失效：截断口径的标签总数应恰比配对口径少 1 枚（漏 d 的 case \"XU\"），"
                        + "此刻同号＝漏补验证点被改动，取据失去依据。实跑 naive=" + naiveTotalLabels
                        + "，配对=" + lines.size());
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
            List<Integer> hits = deadCaseLines(stripped);
            if (!hits.isEmpty()) {
                scan.face.put(rel, hits.size());
            }
        }
        scan.scannedFiles = files.size();
        return scan;
    }

    /**
     * 返回每个「subject 含 {@code toLowerCase} 的 switch 体内、含大写字母字面量的 {@code case}」
     * 行号（1 起，与剥注释后文本一致），每枚标签计一次。
     */
    static List<Integer> deadCaseLines(String strippedSrc) {
        List<Integer> lines = new ArrayList<>();
        Matcher head = SWITCH_HEAD.matcher(strippedSrc);
        while (head.find()) {
            int openParen = head.end() - 1;
            int closeParen = matchingParen(strippedSrc, openParen);
            if (closeParen < 0) {
                continue;
            }
            String subject = strippedSrc.substring(openParen + 1, closeParen);
            if (!TO_LOWER_IN_SUBJECT.matcher(subject).find()) {
                continue;
            }
            int openBrace = strippedSrc.indexOf('{', closeParen);
            if (openBrace < 0) {
                continue;
            }
            int closeBrace = matchingBrace(strippedSrc, openBrace);
            if (closeBrace < 0) {
                continue;
            }
            String body = strippedSrc.substring(openBrace, closeBrace);
            Matcher label = UPPERCASE_CASE_LABEL.matcher(body);
            while (label.find()) {
                lines.add(1 + countNewlines(strippedSrc, openBrace + label.start()));
            }
        }
        return lines;
    }

    /** 从开括号起做字面量感知的圆括号配对；不闭合返回 -1。 */
    private static int matchingParen(String src, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(src, i);
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** 从开括号起做字面量感知的花括号配对；不闭合返回 -1。 */
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

    /** 返回字面量结束引号的下标（含文本块），调用方以 {@code i = end} 继续推进。 */
    private static int skipLiteral(String src, int quoteIndex) {
        char q = src.charAt(quoteIndex);
        if (q == '"' && quoteIndex + 2 < src.length()
                && src.charAt(quoteIndex + 1) == '"'
                && src.charAt(quoteIndex + 2) == '"') {
            int i = quoteIndex + 3;
            int n = src.length();
            while (i < n) {
                if (src.charAt(i) == '\\') {
                    i += 2;
                    continue;
                }
                if (i + 2 < n && src.charAt(i) == '"' && src.charAt(i + 1) == '"'
                        && src.charAt(i + 2) == '"') {
                    return i + 2;
                }
                i++;
            }
            return n - 1;
        }
        int i = quoteIndex + 1;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == q) {
                return i;
            }
            i++;
        }
        return n - 1;
    }

    private static int countNewlines(String src, int upToExclusive) {
        int n = 0;
        for (int i = 0; i < upToExclusive && i < src.length(); i++) {
            if (src.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    /** 仓根定位：user.dir 向上找含 {@code ecos_backend/} 的目录。 */
    private static Path locateRepoRoot() {
        Path cur = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path p = cur; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("ecos_backend"))) {
                return p;
            }
        }
        throw new IllegalStateException("无法从 user.dir=" + cur + " 向上定位仓根（缺 ecos_backend/）");
    }

    /** 收集主源码面 java 文件（{@code src/main/java/}，排除 {@code target/} 与 {@code archive/}）。 */
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

}
