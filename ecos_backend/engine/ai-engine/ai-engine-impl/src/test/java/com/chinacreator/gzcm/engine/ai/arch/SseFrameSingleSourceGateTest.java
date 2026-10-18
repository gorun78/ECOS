package com.chinacreator.gzcm.engine.ai.arch;

import com.chinacreator.gzcm.common.stream.OagSseFrameContract;
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
 * 会被当成违例——那是假红，会让后来者把注释删掉来过关。</p>
 *
 * <p><b>#143/B5-5a 的判据换挡（照登，不留隐性双轨）</b>：改前 OAG 通道是内联发射形态，
 * 因此本守卫拿它当"真实载体正控制"（同一谓词必须抓到实测 7/4 处）。收口后该真实载体消失，
 * 全部扫描面归零 ⇒ 正控制改由<b>合成样本</b>承担（{@link #zeroValuePredicatesStillBiteOnThePreFixShape}）：
 * 把改前缺陷原形喂给同一谓词，抓不到即判据失效，"零违例"不可信。
 * 原基线数字 7/4 不删、留在注释与本段作为收口前凭证。</p>
 *
 * <p><b>本守卫管不到的另一面（照登，不假装全覆盖）</b>：{@code OagController#toStepEvent}
 * 读的是 {@code OagPipelineEngine} 内部事件 Map 的键（{@code node/intent/nodeElapsedMs/
 * securityPassed}），那是引擎侧契约、不是 SSE 事件名，静态词表管不到它。该面的漂移由入口驱动的
 * {@code OagSseChannelSequenceTest#completedLegWritesResponseThenTerminalDone} 以"节点进度键必须落到载荷"
 * 的功能断言兜住——键改名即断言红，不依赖本文件的计数。</p>
 */
class SseFrameSingleSourceGateTest {

    /** 七帧词面（与 {@link SseFrameContract#AGENT_LOOP_FRAME_ORDER} 同集合，本处只作字面量探针）。 */
    private static final Pattern FRAME_WORD_LITERAL =
            Pattern.compile("\"(meta|step|token|delta|citation|error|done)\"");

    /** 第二词表声明面：任何 {@code String FRAME_xxx =} 都构成另一份帧名词表真值。 */
    private static final Pattern FRAME_VOCABULARY_DECLARATION =
            Pattern.compile("String\\s+FRAME_\\w+\\s*=");

    /**
     * OAG 通道词表声明面：任何 {@code String EVENT_xxx = "<OAG 事件名>"} 都构成第二份事件名真值。
     *
     * <p>词形由单源 {@link OagSseFrameContract#events()} 反拼，禁在本门禁里另抄一份字面量。
     * 不加"值必须是词表内事件名"这层约束会误伤
     * {@code WAgentEventPublisher.EVENT_SOURCE = "ai-engine:wagent"}——那是事件源标识、
     * 不是 SSE 事件名，计入即假红。</p>
     */
    private static final Pattern OAG_EVENT_VOCABULARY_DECLARATION = Pattern.compile(
            "String\\s+EVENT_\\w+\\s*=\\s*\"(" + String.join("|", OagSseFrameContract.events()) + ")\"");

    /** 帧名自拼面：Spring SSE BuilderAPI 的 {@code event().name("...")} 形态。 */
    private static final Pattern EVENT_NAME_CALL_SITE = Pattern.compile("\\.name\\(\\s*\"");

    /** 承流面自发射形态（OAG 通道改前形状），捕获事件名用于分类计数。 */
    private static final Pattern SEND_EVENT_WITH_NAME =
            Pattern.compile("sendEvent\\(\\s*emitter,\\s*\"([^\"]+)\"");

    /** agent-loop 写出面 = 控制器 + 帧写出器（这两处只允许引用单源，不许自带词表）。 */
    private static final List<String> WRITE_SURFACE = List.of(
            "controller/AgentLoopController.java",
            "stream/AgentLoopSseFrames.java");

    /** OAG 写出面（B5-5a 起与 agent-loop 面同形：只引用单源，零内联发射）。 */
    private static final List<String> OAG_WRITE_SURFACE = List.of(
            "oag/OagController.java",
            "oag/dto/OagStepEvent.java",
            "stream/OagSseFrames.java");

    /** 单源词表声明数（七帧各一枚）。 */
    private static final int VOCABULARY_SIZE = 7;
    /** OAG 通道词表声明数（node/response/blocked/error/done 各一枚，2026-10-18 实测）。 */
    private static final int OAG_EVENT_VOCABULARY_SIZE = 5;

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

    // ═══ 2) 写出面：零内联帧词（agent-loop 与 OAG 两通道同判据） ═══

    @Test
    @DisplayName("§9.3.3 写出面：agent-loop 与 OAG 四条写出面零内联七帧词")
    void writeSurfaceNeverInlinesFrameWords() throws Exception {
        List<String> surfaces = new ArrayList<>(WRITE_SURFACE);
        surfaces.addAll(OAG_WRITE_SURFACE);
        for (String relative : surfaces) {
            Source source = aiSource(relative);
            List<String> inlined = matchedLines(source, FRAME_WORD_LITERAL).collect(Collectors.toList());
            assertTrue(inlined.isEmpty(),
                    "写出面内联七帧词字面量（必须经单源常量引用）: "
                            + relative + "\n" + String.join("\n", inlined));
        }
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

    // ═══ 4) 收口后判据：内联发射面归零（改前基线 7 处，凭证留档不删） ═══

    @Test
    @DisplayName("§1.9.2 收口：ai 全域 sendEvent(emitter, \"<字面量>\") 归零（改前实测 7 处，D-B5-7 已闭）")
    void inlineEmitterSendSurfaceIsNowZeroAcrossAiDomain() throws Exception {
        List<Source> aiMain = aiMainSources();
        List<String> hits = aiMain.stream()
                .flatMap(source -> matchedLines(source, SEND_EVENT_WITH_NAME)
                        .map(text -> source.file().getFileName() + ": " + text.trim()))
                .collect(Collectors.toList());
        assertTrue(hits.isEmpty(),
                "内联 sendEvent(emitter, \"<字面量>\") 面复现（2026-10-18 收口前实测基线 = 7 处且全在 OagController，"
                        + "收口后必须为 0）。新增即多开一个未合流的发射点，须改码合流、不许改本判据放行:\n"
                        + String.join("\n", hits));
    }

    // ═══ 4b) OAG 事件名声明面：ai 域零枚，唯一真值在 common-api 单源 ═══

    @Test
    @DisplayName("§1.9.2 OAG 单源：ai 引擎零 EVENT_* 事件名声明，同一谓词在 common-api 抓到恰 5 枚")
    void oagEventVocabularyIsDeclaredOnlyInTheCommonApiSurface() throws Exception {
        List<String> secondVocabulary = aiMainSources().stream()
                .flatMap(source -> matchedLines(source, OAG_EVENT_VOCABULARY_DECLARATION)
                        .map(text -> source.file().getFileName() + ": " + text.trim()))
                .collect(Collectors.toList());
        assertTrue(secondVocabulary.isEmpty(),
                "ai 引擎内出现第二份 OAG 事件名声明（唯一登记处 = common-api OagSseFrameContract）:\n"
                        + String.join("\n", secondVocabulary));

        List<String> canonical =
                matchedLines(oagSingleSourceContract(), OAG_EVENT_VOCABULARY_DECLARATION)
                        .collect(Collectors.toList());
        assertEquals(OAG_EVENT_VOCABULARY_SIZE, canonical.size(),
                "OAG 单源事件名声明数不为 5 —— 词表或扫描面已变化，需重新登记；实测 " + canonical);

        long consumeRefs = matchedLines(aiSource("oag/dto/OagStepEvent.java"),
                Pattern.compile("OagSseFrameContract\\.\\w+")).count();
        assertTrue(consumeRefs >= OAG_EVENT_VOCABULARY_SIZE,
                "载荷装配面对 OAG 单源的引用少于 5 —— 若它既不内联又不引用，则事件名来自第三处，"
                        + "本守卫的'零'是假的；实测 " + consumeRefs);
    }

    // ═══ 4c) 零值判据的自证：合成样本必须能被同一谓词抓到 ═══

    @Test
    @DisplayName("自检：收口后各零值判据仍能咬住改前缺陷原形（合成样本，非源码面）")
    void zeroValuePredicatesStillBiteOnThePreFixShape() {
        String preFixShape = String.join("\n",
                "    sendEvent(emitter, \"error\", payload);",
                "    sendEvent(emitter, \"done\", payload);",
                "    emitter.send(SseEmitter.event().name(\"node\").data(payload));",
                "    private static final String EVENT_NODE = \"node\";",
                "    private static final String FRAME_DELTA = \"delta\";");
        assertEquals(2L, countInText(preFixShape, SEND_EVENT_WITH_NAME),
                "内联发射探针失效 —— 源码面的'0 处'不可信");
        assertEquals(1L, countInText(preFixShape, EVENT_NAME_CALL_SITE),
                "自拼帧名探针失效 —— 源码面的'0 处'不可信");
        assertEquals(1L, countInText(preFixShape, OAG_EVENT_VOCABULARY_DECLARATION),
                "OAG 词表声明探针失效");
        assertEquals(3L, countInText(preFixShape, FRAME_WORD_LITERAL),
                "七帧词探针失效（前两条 + 末条 FRAME_DELTA）");
        assertEquals(1L, countInText(preFixShape, FRAME_VOCABULARY_DECLARATION),
                "七帧词表声明探针失效");
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

    /** OAG 通道事件名单源（跨模块只读该单文件，与七帧单源分表登记）。 */
    private static Source oagSingleSourceContract() {
        List<String> candidates = List.of(
                "../../../runtime/common-api/src/main/java/com/chinacreator/gzcm/common/stream/OagSseFrameContract.java",
                "runtime/common-api/src/main/java/com/chinacreator/gzcm/common/stream/OagSseFrameContract.java",
                "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/stream/OagSseFrameContract.java");
        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (Files.isRegularFile(path)) {
                return readSource(path);
            }
        }
        throw new IllegalStateException("OagSseFrameContract 源文件缺失，cwd=" + Path.of("").toAbsolutePath()
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
