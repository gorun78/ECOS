package com.chinacreator.gzcm.engine.ai.arch;

import com.chinacreator.gzcm.common.stream.SseFrameContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-06 §1.9.2 / §9.3.3 承流面静态门禁（#143/B5-2）—— SSE 帧词与帧序的<b>单源</b>可证性。
 *
 * <p>存在理由：帧名一旦散在发射点里，前端解析器（§13.4.5 按七帧序假设）、审计对账与网关侧
 * 断线重放就各自读到不同的词表，且这种漂移<b>不会有任何一条测试变红</b>——运行期大家都"发得出帧"。
 * 所以本守卫只能在源码面钉：写出面零内联帧词、词表声明面全局单文件、帧名装配调用点零字面量。</p>
 *
 * <p>判据一律"剥注释后再数"（{@link #stripComments}），否则 javadoc 里对旧写法的反面举例
 * 会被当成违例——那是假红，会让后来者把注释删掉来过关。同时每条零值断言都配一条
 * <b>真实载体正控制</b>：同一谓词在 OAG 通道（现状仍内联）必须抓到实测数字，抓不到即扫描面失真，
 * 本守卫的"零违例"不可信。</p>
 */
class SseFrameSingleSourceGateTest {

    /** 七帧词面（与 {@link SseFrameContract#AGENT_LOOP_FRAME_ORDER} 同集合，本处只作字面量探针）。 */
    private static final Pattern FRAME_WORD_LITERAL =
            Pattern.compile("\"(meta|step|token|delta|citation|error|done)\"");

    /** 第二词表声明面：任何 {@code String FRAME_xxx =} 都构成另一份帧名词表真值。 */
    private static final Pattern FRAME_VOCABULARY_DECLARATION =
            Pattern.compile("String\\s+FRAME_\\w+\\s*=");

    /** 帧名自拼面：Spring SSE BuilderAPI 的 {@code event().name("...")} 形态。 */
    private static final Pattern EVENT_NAME_CALL_SITE = Pattern.compile("\\.name\\(\\s*\"");

    /** 承流面自发射形态（OAG 通道现状），捕获事件名用于分类计数。 */
    private static final Pattern SEND_EVENT_WITH_NAME =
            Pattern.compile("sendEvent\\(\\s*emitter,\\s*\"([^\"]+)\"");

    /** agent-loop 写出面 = 控制器 + 帧写出器（这两处只允许引用单源，不许自带词表）。 */
    private static final List<String> WRITE_SURFACE = List.of(
            "controller/AgentLoopController.java",
            "stream/AgentLoopSseFrames.java");

    /** 2026-10-18 实测：OAG 通道 {@code sendEvent(emitter, "<字面量>")} 共 7 处，全在 OagController。 */
    private static final int OAG_INLINE_SEND_EVENT_PIN = 7;
    /** 上述 7 处里落在七帧词表上的枚数（error×3 + done×1）；node/response/blocked 是 OAG 私有词形。 */
    private static final int OAG_FRAME_WORD_PIN = 4;
    /** 单源词表声明数（七帧各一枚）。 */
    private static final int VOCABULARY_SIZE = 7;

    // ═══ 1) 词表声明面：全局单文件 ═══

    @Test
    @DisplayName("§1.9.2 单源：ai 引擎内零第二帧词表声明，同一谓词在 common-api 单源抓到 7 枚")
    void frameVocabularyIsDeclaredInExactlyOneFile() throws Exception {
        List<Source> aiMain = aiMainSources();
        assertFalse(aiMain.isEmpty(), "ai 源码扫描面为空 —— 本守卫恒绿，无意义");
        List<String> secondVocabulary = aiMain.stream()
                .flatMap(source -> matchedLines(source, FRAME_VOCABULARY_DECLARATION)
                        .map(text -> source.file().getFileName() + ": " + text))
                .collect(Collectors.toList());
        assertTrue(secondVocabulary.isEmpty(),
                "ai 引擎内出现第二份帧名词表声明（词表唯一登记处 = common-api SseFrameContract）:\n"
                        + String.join("\n", secondVocabulary));

        // 正控制：同一谓词必须能抓到真单源里的 7 枚声明，抓不到说明正则/读文件面坏了
        List<String> canonical = matchedLines(singleSourceContract(), FRAME_VOCABULARY_DECLARATION)
                .collect(Collectors.toList());
        assertEquals(VOCABULARY_SIZE, canonical.size(),
                "单源 SseFrameContract 的帧名声明数不为 7 —— 扫描面或词表已变化，需重新登记");
    }

    // ═══ 2) 写出面：零内联帧词 ═══

    @Test
    @DisplayName("§9.3.3 写出面：agent-loop 控制器与帧写出器零内联帧词，OAG 同谓词实测 4 处（证明判据咬得住）")
    void writeSurfaceNeverInlinesFrameWords() throws Exception {
        for (String relative : WRITE_SURFACE) {
            Source source = aiSource(relative);
            List<String> inlined = matchedLines(source, FRAME_WORD_LITERAL).collect(Collectors.toList());
            assertTrue(inlined.isEmpty(),
                    "写出面内联七帧词字面量（必须经 SseFrameContract.FRAME_* 引用）: "
                            + relative + "\n" + String.join("\n", inlined));
        }

        // 真实载体正控制：OAG 通道仍是内联发射形态，同谓词必须抓到实测 4 处七帧词
        List<String> oagFrameWords =
                matchedLines(aiSource("oag/OagController.java"), FRAME_WORD_LITERAL)
                        .collect(Collectors.toList());
        assertEquals(OAG_FRAME_WORD_PIN, oagFrameWords.size(),
                "OAG 内联帧词实测基线漂移（2026-10-18 = 4：error×3 + done×1）—— "
                        + "该数为正控制，变小=判据失效，变大=又开了内联发射点；实测 "
                        + oagFrameWords);
    }

    // ═══ 3) 帧名自拼面：剥注释后为零 ═══

    @Test
    @DisplayName("§1.9.2 消灭自拼帧名：ai 全域 event().name(\"...\") 剥注释后为零，未剥时恰 1 处（在 javadoc 反例里）")
    void eventNameCallSitesAreZeroOnceCommentsAreGone() throws Exception {
        List<Source> aiMain = aiMainSources();
        List<String> stripped = aiMain.stream()
                .flatMap(source -> matchedLines(source, EVENT_NAME_CALL_SITE)
                        .map(text -> source.file().getFileName() + ": " + text))
                .collect(Collectors.toList());
        assertTrue(stripped.isEmpty(),
                "ai 引擎出现自拼帧名的发射点（SSE 帧名装配唯一入口 = AgentLoopSseFrames → SseFrameContract）:\n"
                        + String.join("\n", stripped));

        long rawHits = aiMain.stream()
                .flatMap(source -> patternsInRawSource(source, EVENT_NAME_CALL_SITE))
                .count();
        assertEquals(1L, rawHits,
                "未剥注释的 .name(\" 命中数不为 1 —— 那唯一一处是 AgentLoopSseFrames 类头对旧写法的反面举例；"
                        + "数目变化说明本守卫的'剥注释'判据与实际码面脱节");
    }

    // ═══ 4) 现状钉桩：OAG 通道词形未合流（钉现状 ≠ 认可） ═══

    @Test
    @DisplayName("现状钉桩：OAG 通道内联发射恰 7 处且全在 OagController（D-B5-8 未合流，只增不减）")
    void oagInlineEventSurfaceIsPinnedNotApproved() throws Exception {
        List<Source> aiMain = aiMainSources();
        List<String> hits = aiMain.stream()
                .flatMap(source -> matchedLines(source, SEND_EVENT_WITH_NAME)
                        .map(text -> source.file().getFileName() + ": " + text.trim()))
                .collect(Collectors.toList());
        assertEquals(OAG_INLINE_SEND_EVENT_PIN, hits.size(),
                "内联 sendEvent(emitter, \"<字面量>\") 面变化。2026-10-18 实测基线 = 7 处且全部属 OagController；"
                        + "新增即多开一个未合流的发射点（须改码合流，不许改本基线放行），"
                        + "减少则须同步订正 D-B5-8 与本注释。实测:\n" + String.join("\n", hits));
        assertTrue(hits.stream().allMatch(hit -> hit.startsWith("OagController.java")),
                "内联发射点已越出 OagController —— 本钉桩的属主假设不再成立，需重新登记:\n"
                        + String.join("\n", hits));
    }

    // ═══ 5) 七帧 ≠ 八帧：degrade 帧显式缺席（WAgent 面欠账留痕） ═══

    @Test
    @DisplayName("§9.3.3 词表面：agent-loop 七帧固定序无 degrade；WAgent 八帧（§10.3.6）在 ai 域尚未落地")
    void agentLoopVocabularyIsSevenFramesAndDegradeIsAbsent() {
        assertEquals(List.of("meta", "step", "token", "delta", "citation", "error", "done"),
                SseFrameContract.AGENT_LOOP_FRAME_ORDER,
                "七帧固定序与详设-06 §9.3.3 表首列不一致（前端 §13.4.5 解析器按此序假设）");

        List<String> declaredValues = frameVocabularyValues();
        assertEquals(VOCABULARY_SIZE, declaredValues.size(),
                "词表声明枚数与七帧不符: " + declaredValues);
        assertFalse(declaredValues.contains("degrade"),
                "ai 域词表已出现 degrade 帧但 §10.3.6 的 WAgent 八帧序尚未登记 —— "
                        + "degrade 帧属 WAgent run 流，须与 {dgCode,appliesTo,requiresAck,note} 载荷同批落地");
    }

    // ═══ 6) 写出面确实经单源消费（防空断言） ═══

    @Test
    @DisplayName("消费面：写出器逐帧引用 SseFrameContract.FRAME_*（≥7）且控制器逐键引用 KEY_*（≥7）")
    void writeSurfaceActuallyConsumesTheSingleSource() throws Exception {
        long frameRefs = matchedLines(aiSource("stream/AgentLoopSseFrames.java"),
                        Pattern.compile("SseFrameContract\\.FRAME_\\w+")).count();
        assertTrue(frameRefs >= VOCABULARY_SIZE,
                "写出器对单源帧名的引用少于 7 —— 若它既不内联又不引用，则帧名来自第三处（本守卫的'零'是假的）；实测 "
                        + frameRefs);
        long keyRefs = matchedLines(aiSource("controller/AgentLoopController.java"),
                Pattern.compile("SseFrameContract\\.KEY_\\w+")).count();
        assertTrue(keyRefs >= VOCABULARY_SIZE,
                "控制器对单源键名的引用少于 7 —— done/error 尾帧键面已脱离词表；实测 " + keyRefs);
    }

    // ═══ 7) 判据自身的误报自检 ═══

    @Test
    @DisplayName("自检：剥注释判据只去整行注释，不误删字符串载荷内的 //（防假红也防假绿）")
    void commentStripperKeepsStringPayloads() {
        String sample = String.join("\n",
                "/** 旧写法 event().name(\"delta\") 已消灭 */",
                "    // 注释里的 \"error\" 不算违例",
                "    String url = \"jdbc:postgresql://127.0.0.1:5432/sys_man\"; // 串内 // 不得截断",
                "    frames.delta(\"delta\");");
        String stripped = stripComments(sample);
        assertFalse(stripped.contains("旧写法"), "整行块注释未被剔除 —— 判据会假红");
        assertFalse(stripped.contains("注释里的"), "整行行注释未被剔除 —— 判据会假红");
        assertTrue(stripped.contains("jdbc:postgresql://"), "字符串载荷被截断 —— 扫描面失真");
        assertEquals(1L, countInText(stripped, FRAME_WORD_LITERAL),
                "码面帧词应恰好保留 1 处（注释里的两处不得计入）");
    }

    // ── 扫描装置 ────────────────────────────────────────────────────────

    private record Source(Path file, String raw, String code) {
    }

    /** 逐帧引用点：词表值由反射取，禁在本测试里另抄一份字面量。 */
    private static List<String> frameVocabularyValues() {
        List<String> values = new ArrayList<>();
        for (Field field : SseFrameContract.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                continue;
            }
            if (!field.getName().startsWith("FRAME_")) {
                continue;
            }
            try {
                values.add((String) field.get(null));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("帧名常量不可读: " + field.getName(), e);
            }
        }
        return values;
    }

    private static Stream<String> matchedLines(Source source, Pattern pattern) {
        return patternsInCode(source.code(), pattern);
    }

    private static Stream<String> patternsInRawSource(Source source, Pattern pattern) {
        return patternsInCode(source.raw(), pattern);
    }

    private static Stream<String> patternsInCode(String text, Pattern pattern) {
        List<String> out = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                out.add(line);
            }
        }
        return out.stream();
    }

    private static long countInText(String text, Pattern pattern) {
        return patternsInCode(text, pattern).count();
    }

    /**
     * 整行注释剔除（块注释行以 {@code *} 起始、行注释以 {@code //} 起始）。
     *
     * <p>只做行级剔除、不碰码行尾部注释与字符串载荷：判据要的是"注释里的反面举例不计入"，
     * 过度解析反而会让扫描面随写法漂移。局限已由 {@code commentStripperKeepsStringPayloads} 钉住。</p>
     */
    private static String stripComments(String source) {
        List<String> kept = new ArrayList<>();
        for (String line : source.split("\n", -1)) {
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) {
                continue;
            }
            kept.add(line);
        }
        return String.join("\n", kept);
    }

    private static List<Source> aiMainSources() throws IOException {
        Path root = aiSourceRoot();
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> files = paths
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .collect(Collectors.toList());
            List<Source> sources = new ArrayList<>(files.size());
            for (Path file : files) {
                sources.add(readSource(file));
            }
            return sources;
        }
    }

    private static Source aiSource(String relativeWithinAiRoot) {
        Path file = aiSourceRoot().resolve(relativeWithinAiRoot);
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("写出面文件缺失: " + file.toAbsolutePath()
                    + "（cwd=" + Path.of("").toAbsolutePath() + "）");
        }
        return readSource(file);
    }

    private static Source readSource(Path file) {
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            return new Source(file, raw, stripComments(raw));
        } catch (IOException e) {
            throw new IllegalStateException("源码读取失败: " + file.toAbsolutePath(), e);
        }
    }

    /** 帧词表唯一登记处（跨模块只读该单文件，禁把 common-api 全树纳入扫描面）。 */
    private static Source singleSourceContract() {
        List<String> candidates = List.of(
                "../../../runtime/common-api/src/main/java/com/chinacreator/gzcm/common/stream/SseFrameContract.java",
                "runtime/common-api/src/main/java/com/chinacreator/gzcm/common/stream/SseFrameContract.java",
                "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/stream/SseFrameContract.java");
        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (Files.isRegularFile(path)) {
                return readSource(path);
            }
        }
        throw new IllegalStateException("SseFrameContract 源文件缺失，cwd=" + Path.of("").toAbsolutePath()
                + " 候选=" + candidates);
    }

    /** surefire 工作目录在不同构建入口下可能是模块根或仓根，双候选 + 失败即点名 cwd。 */
    private static Path aiSourceRoot() {
        List<String> candidates = List.of(
                "src/main/java/com/chinacreator/gzcm/engine/ai",
                "engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai");
        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        throw new IllegalStateException("ai 源码扫描根缺失，cwd=" + Path.of("").toAbsolutePath()
                + " 候选=" + candidates);
    }
}
