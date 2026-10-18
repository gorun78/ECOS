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
 * <p><b>#143/B5-5b 把第三条通道（WAgent run 流）纳入同一判据</b>：改前该流是
 * {@code send(emitter, "<自有词>")} + {@code event().name(变量)} 的内联形态（D-B5-8 病灶，
 * 词形 {@code stream_ready/run_terminal/stream_timeout} 与 §10.3.6 八帧不同名）。收口后该形状
 * 归零，故本文件新增两枚探针：{@link #INLINE_SEND_WITH_NAME}（整体扫描面归零 + 合成样本必须抓到）
 * 与 {@link #W_FRAME_WORD_LITERAL}（W 写出面零内联八帧词）。词形一律由单源
 * {@link SseFrameContract#wagentVocabulary()} 反拼，禁在本门禁里另抄一份八帧字面量。</p>
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

    /**
     * W 通道改前自发射形态（D-B5-8 病灶）：{@code send(emitter, "<字面量>", ...)}。
     *
     * <p>与 {@link #SEND_EVENT_WITH_NAME} 分列而非合并：OAG 侧用的是 {@code sendEvent(...)} 私有方法，
     * 一条正则覆盖两种写法会把两通道的基线数字混成一个数，改前凭证（OAG 7 处 / W 3 处）就不可对账。
     * 本探针<b>不限词表内事件名</b>——改前 W 流的帧名（{@code stream_ready} 等）恰恰不在八帧里，
     * 若只认词表内名字，这条判据对缺陷原形是瞎的。</p>
     */
    private static final Pattern INLINE_SEND_WITH_NAME =
            Pattern.compile("send\\(\\s*emitter,\\s*\"([^\"]+)\"");

    /** W 通道帧词字面量（词形由单源反拼 = 八帧 ∪ milestone；本处只作探针，不作第二词表）。 */
    private static final Pattern W_FRAME_WORD_LITERAL = Pattern.compile(
            "\"(" + String.join("|", SseFrameContract.wagentVocabulary()) + ")\"");

    /** agent-loop 写出面 = 控制器 + 帧写出器（这两处只允许引用单源，不许自带词表）。 */
    private static final List<String> WRITE_SURFACE = List.of(
            "controller/AgentLoopController.java",
            "stream/AgentLoopSseFrames.java");

    /** OAG 写出面（B5-5a 起与 agent-loop 面同形：只引用单源，零内联发射）。 */
    private static final List<String> OAG_WRITE_SURFACE = List.of(
            "oag/OagController.java",
            "oag/dto/OagStepEvent.java",
            "stream/OagSseFrames.java");

    /**
     * WAgent 写出面（B5-5b 起与另两条通道同形）。
     *
     * <p>控制器在列是必要的而非冗余：它是唯一碰 Spring SSE BuilderAPI 的地方
     * （{@code sinkOf} 把帧名与 {@code id:} 位落成线格式），帧词若被内联回这里，写出器的单源就绕过了。</p>
     */
    private static final List<String> WAGENT_WRITE_SURFACE = List.of(
            "wagent/controller/WAgentRunEventStreamController.java",
            "wagent/stream/WAgentSseFrames.java");

    /** 七帧固定序枚数（§9.3.3 表首列）。 */
    private static final int VOCABULARY_SIZE = 7;
    /**
     * 单源帧名声明枚数（七帧 + {@code degrade} + {@code milestone} = 9，2026-10-18 实测）。
     *
     * <p>与 {@link #VOCABULARY_SIZE} 分列：前者是 agent-loop 词表闭合性（七帧），后者是声明面计数。
     * 混用一个数会让"agent-loop 里冒出 degrade"这种违例在计数判据上看起来是正常的。</p>
     */
    private static final int FRAME_DECLARATION_SIZE = 9;
    /** OAG 通道词表声明数（node/response/blocked/error/done 各一枚，2026-10-18 实测）。 */
    private static final int OAG_EVENT_VOCABULARY_SIZE = 5;
    /** W 通道八帧枚数（§10.3.6 表首列 1..8；{@code milestone} 不在内）。 */
    private static final int WAGENT_FRAME_ORDER_SIZE = 8;
    /** W 写出器实际有真载体的帧枚数（meta/step/degrade/milestone/error/done；token/delta/citation 零生产者）。 */
    private static final int WAGENT_WRITER_FRAME_REFS_FLOOR = 6;

    // ═══ 1) 词表声明面：全局单文件 ═══

    @Test
    @DisplayName("§1.9.2 单源：ai 引擎内零第二帧词表声明，同一谓词在 common-api 单源抓到 9 枚")
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

        // 正控制：同一谓词必须能抓到真单源里的 9 枚声明，抓不到说明正则/读文件面坏了
        List<String> canonical = matchedLines(singleSourceContract(), FRAME_VOCABULARY_DECLARATION)
                .collect(Collectors.toList());
        assertEquals(FRAME_DECLARATION_SIZE, canonical.size(),
                "单源 SseFrameContract 的帧名声明数不为 9（agent-loop 七帧 + degrade + milestone）"
                        + " —— 扫描面或词表已变化，需重新登记; 实测 " + canonical);
    }

    // ═══ 2) 写出面：零内联帧词（三条通道同判据） ═══

    @Test
    @DisplayName("§9.3.3 写出面：agent-loop、OAG、WAgent 六条写出面零内联七帧词")
    void writeSurfaceNeverInlinesFrameWords() throws Exception {
        List<String> surfaces = new ArrayList<>(WRITE_SURFACE);
        surfaces.addAll(OAG_WRITE_SURFACE);
        surfaces.addAll(WAGENT_WRITE_SURFACE);
        for (String relative : surfaces) {
            Source source = aiSource(relative);
            List<String> inlined = matchedLines(source, FRAME_WORD_LITERAL).collect(Collectors.toList());
            assertTrue(inlined.isEmpty(),
                    "写出面内联七帧词字面量（必须经单源常量引用）: "
                            + relative + "\n" + String.join("\n", inlined));
        }
    }

    @Test
    @DisplayName("§10.3.6 W 写出面：零内联八帧词（含 degrade/milestone，探针词形取自单源）")
    void wagentWriteSurfaceNeverInlinesWFrameWords() throws Exception {
        for (String relative : WAGENT_WRITE_SURFACE) {
            Source source = aiSource(relative);
            List<String> inlined = matchedLines(source, W_FRAME_WORD_LITERAL).collect(Collectors.toList());
            assertTrue(inlined.isEmpty(),
                    "W 写出面内联八帧词字面量（必须经单源常量引用；此探针比七帧面多出 degrade/milestone 两词，"
                            + "只用七帧词面会放行这两词的自拼）: " + relative + "\n"
                            + String.join("\n", inlined));
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

    // ═══ 4) 收口后判据：内联发射面归零（改前基线 OAG 7 处 / W 4 处，凭证留档不删） ═══

    @Test
    @DisplayName("§1.9.2 收口：ai 全域内联发射（sendEvent/send 两形）归零（改前 OAG 7 处、W 4 处）")
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

        // W 通道（D-B5-8）改前形状：send(emitter, "stream_ready"…"stream_error")，实测 4 处，收口后必须为 0
        List<String> wagentHits = aiMain.stream()
                .flatMap(source -> matchedLines(source, INLINE_SEND_WITH_NAME)
                        .map(text -> source.file().getFileName() + ": " + text.trim()))
                .collect(Collectors.toList());
        assertTrue(wagentHits.isEmpty(),
                "W 通道内联 send(emitter, \"<字面量>\") 面复现（2026-10-18 收口前实测基线 = 4 处："
                        + "stream_ready/run_terminal/stream_timeout/stream_error，均不在 §10.3.6 八帧词表内，"
                        + "收口后必须为 0）。此判据不限词表内名字——八帧外的自拼帧名恰是缺陷原形:\n"
                        + String.join("\n", wagentHits));
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
                "    private static final String FRAME_DELTA = \"delta\";",
                "    send(emitter, \"stream_ready\", Map.of());",
                "    send(emitter, \"run_terminal\", Map.of());",
                "    send(emitter, \"stream_timeout\", Map.of());",
                "    send(emitter, \"stream_error\", Map.of());",
                "    String degradeFrameName = \"degrade\";");
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
        assertEquals(4L, countInText(preFixShape, INLINE_SEND_WITH_NAME),
                "W 通道内联发射探针失效（改前 stream_ready/run_terminal/stream_timeout/stream_error 四形）"
                        + " —— 源码面的'0 处'不可信");
        assertEquals(4L, countInText(preFixShape, W_FRAME_WORD_LITERAL),
                "八帧词探针失效（error/done/delta/degrade 四处）；它与七帧探针的差集正是 degrade —— "
                        + "只留七帧探针会让 degrade/milestone 的自拼漏检");
    }

    // ═══ 5) 七帧 ≠ 八帧：两通道词表分册，degrade 只在 W 册 ═══

    @Test
    @DisplayName("§9.3.3/§10.3.6 词表面：agent-loop 七帧恒不含 degrade；WAgent 八帧已登记且 degrade 载荷四键同批")
    void agentLoopStaysDegradeFreeWhileWagentEightFramesAreRegistered() {
        assertEquals(List.of("meta", "step", "token", "delta", "citation", "error", "done"),
                SseFrameContract.AGENT_LOOP_FRAME_ORDER,
                "七帧固定序与详设-06 §9.3.3 表首列不一致（前端 §13.4.5 解析器按此序假设）");

        List<String> declaredValues = frameVocabularyValues();
        assertEquals(FRAME_DECLARATION_SIZE, declaredValues.size(),
                "单源帧名声明枚数不为 9（七帧 + degrade + milestone）: " + declaredValues);
        assertTrue(declaredValues.contains(SseFrameContract.FRAME_DEGRADE),
                "degrade 帧名已从单源消失 —— W 通道降级横幅将无帧可读: " + declaredValues);

        // 两通道分册：degrade 属 W 册，agent-loop 面既不认它、也不许把它收进七帧序
        assertFalse(SseFrameContract.AGENT_LOOP_FRAME_ORDER.contains(SseFrameContract.FRAME_DEGRADE),
                "agent-loop 七帧序里混入了 degrade —— §9.3.3 的闭合词表不给降级留位，"
                        + "两通道词表一旦互冒充，前端解析器与审计对账就各有其一套帧名");
        assertFalse(SseFrameContract.isKnownFrame(SseFrameContract.FRAME_DEGRADE),
                "agent-loop 装配入口 frame() 已认 degrade 帧 = 两通道词表合流的第一个信号，须回退");
        assertTrue(SseFrameContract.validateSequence(
                        List.of(SseFrameContract.FRAME_META, SseFrameContract.FRAME_DEGRADE,
                                SseFrameContract.FRAME_DONE)).isPresent(),
                "七帧帧序校验放行了 degrade（通道互斥的判据失效）");

        // WAgent 八帧序 = §10.3.6 表首列 1..8；degrade 载荷四键必须同批在位
        assertEquals(List.of("meta", "step", "token", "delta", "citation", "degrade", "error", "done"),
                SseFrameContract.WAGENT_FRAME_ORDER,
                "WAgent 八帧固定序与详设-10 §10.3.6 表首列不一致");
        assertEquals(WAGENT_FRAME_ORDER_SIZE, SseFrameContract.WAGENT_FRAME_ORDER.size(),
                "WAgent 八帧枚数不为 8");
        assertEquals(List.of(SseFrameContract.KEY_TRACE_ID, SseFrameContract.KEY_DG_CODE,
                        SseFrameContract.KEY_APPLIES_TO, SseFrameContract.KEY_REQUIRES_ACK,
                        SseFrameContract.KEY_NOTE),
                SseFrameContract.WAGENT_FRAME_KEYS.get(SseFrameContract.FRAME_DEGRADE),
                "degrade 帧的载荷键面与 §10.3.6 帧 6 的 {dgCode,appliesTo,requiresAck,note} 不一致"
                        + "（帧名与载荷必须同批登记，只补帧名等于前端拿到空降级）");
        assertTrue(SseFrameContract.isKnownWagentFrame(SseFrameContract.FRAME_DEGRADE),
                "W 通道装配入口不认 degrade —— 降级帧无法经单源装配");

        // milestone 是通道补充帧：词表可认，但永不进八帧序（进了即冒充 §10.3.6 设计原文）
        assertTrue(declaredValues.contains(SseFrameContract.FRAME_MILESTONE),
                "milestone 补充帧名已从单源消失: " + declaredValues);
        assertFalse(SseFrameContract.WAGENT_FRAME_ORDER.contains(SseFrameContract.FRAME_MILESTONE),
                "milestone 被塞进 WAGENT_FRAME_ORDER = 把通道自加帧冒充成 §10.3.6 八帧设计原文");
        assertFalse(SseFrameContract.AGENT_LOOP_FRAME_ORDER.contains(SseFrameContract.FRAME_MILESTONE),
                "milestone 泄漏进 agent-loop 七帧序");
    }

    // ═══ 6) 写出面确实经单源消费（防空断言） ═══

    @Test
    @DisplayName("消费面：三条通道写出器均逐帧引用 SseFrameContract，控制器逐键引用 KEY_*")
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

        // W 通道：有真载体的六帧（meta/step/degrade/milestone/error/done）逐一经单源帧名常量装配
        long wFrameRefs = matchedLines(aiSource("wagent/stream/WAgentSseFrames.java"),
                Pattern.compile("SseFrameContract\\.FRAME_\\w+")).count();
        assertTrue(wFrameRefs >= WAGENT_WRITER_FRAME_REFS_FLOOR,
                "W 写出器对单源帧名的引用少于 6 —— token/delta/citation 三帧因零生产者不发（差距册 D-B5-19），"
                        + "其余六帧若不引用单源，帧名就来自第三处；实测 " + wFrameRefs);
        long wKeyRefs = matchedLines(aiSource("wagent/stream/WAgentSseFrames.java"),
                Pattern.compile("SseFrameContract\\.KEY_\\w+")).count();
        assertTrue(wKeyRefs >= WAGENT_FRAME_ORDER_SIZE,
                "W 写出器对单源键名的引用少于 8 —— 键面若既不引用又不内联，则第四套键名藏在别处；实测 " + wKeyRefs);
        assertTrue(matchedLines(aiSource("wagent/stream/WAgentSseFrames.java"),
                        Pattern.compile("SseFrameContract\\.wagentFrame\\(")).findAny().isPresent(),
                "W 写出器没走 wagentFrame() 装配入口 —— 键面白名单（非登记键即抛）被绕过");
        assertTrue(matchedLines(aiSource("wagent/stream/WAgentSseFrames.java"),
                        Pattern.compile("SseFrameContract\\.validateWagentSequence\\(")).findAny().isPresent(),
                "W 写出器末帧前未复验帧序 —— 单源里的尾序校验成了没人调用的死码");
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
