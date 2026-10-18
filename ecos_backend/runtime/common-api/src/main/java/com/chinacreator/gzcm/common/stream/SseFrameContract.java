package com.chinacreator.gzcm.common.stream;

import com.chinacreator.gzcm.common.trace.AgentLoopTrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SSE 承流帧序单源（详细设计-06 §9.3.3 帧契约 + §1.9.2「帧序固定七帧、每帧带 traceId」）。
 *
 * <p>本类只定义<b>帧名、帧序与键面</b>，不做任何裁决：数值口径、引用完整性、护栏放行
 * 仍由 ai-engine 的引用后校验与安全通道判定，承流面因此不会成为第二真值面。</p>
 *
 * <p>为什么要单源：帧名与键面一旦由各发射侧各自抄字面量，就会出现同一通道两种词形
 * （{@code token} 既当用量帧又当正文帧即为本差距的实测形态），前端按序假设的解析器
 * 无从对齐。故七帧名、逐帧键名、缺失标记一律以本类为唯一登记处。</p>
 *
 * <p>缺失标记复用 {@link AgentLoopTrace#UNKNOWN} 与 {@link AgentLoopTrace#ABSENT}：
 * 「无值写 -1、禁 0 冒充」在日志面与承流面必须是同一个常量，否则回放侧与前端侧
 * 会把"未上报"读成"实测为零"。</p>
 */
public final class SseFrameContract {

    /** 帧 1：首帧必发，建立通道上下文。 */
    public static final String FRAME_META = "meta";
    /** 帧 2：plan / tool_call / tool_result / llm_call / final 各一条，{@code op} 取 {@link AgentLoopTrace.Op}。 */
    public static final String FRAME_STEP = "step";
    /** 帧 3：llm_call 之后的用量帧（不是正文帧）。 */
    public static final String FRAME_TOKEN = "token";
    /** 帧 4：模型增量正文。 */
    public static final String FRAME_DELTA = "delta";
    /** 帧 5：有据数值段落的引用帧，字段只从工具返回体取。 */
    public static final String FRAME_CITATION = "citation";
    /**
     * WAgent 帧 6：降级帧（详设-10 §10.3.6 表第 6 行；前端降级横幅单源读此帧）。
     *
     * <p><b>为何 agent-loop 七帧里没有它</b>：§9.3.3 的七帧闭合词表不给降级留位。
     * 本常量属 W 通道词表，{@link #AGENT_LOOP_FRAME_ORDER} 刻意不含，
     * 于是 {@link #validateSequence(List)} 会把 agent-loop 流里的 degrade 判为违例——
     * 这正是"两通道分表登记、不许互相冒充"的可执行形式。</p>
     */
    public static final String FRAME_DEGRADE = "degrade";
    /** 帧 6：任一红线/故障分支，永不携带异常原文。 */
    public static final String FRAME_ERROR = "error";
    /** 帧 7：末帧。 */
    public static final String FRAME_DONE = "done";
    /**
     * W 通道补充帧：Run 生命周期里程碑（{@code PlanGenerated}、{@code DecisionOptionsReady} 等）。
     *
     * <p><b>它不在 §10.3.6 的八帧里</b>：V235 事件表由编排器写入 11 类事件，其中 8 类是
     * Run 级生命周期里程碑，八帧无对应帧位。丢掉它们会让只读回放流失去进度可观察性，
     * 把它们塞进 {@code step} 则是把非步骤事件冒充为步骤（假真理）。故显式另立一帧并
     * 登记为<b>设计侧待补帧位</b>的缺陷（差距分析 D-B5-18）。
     * 本帧<b>永不</b>进入 {@link #WAGENT_FRAME_ORDER}，八帧序保持 §10.3.6 原样。</p>
     */
    public static final String FRAME_MILESTONE = "milestone";

    /** 七帧固定序（§9.3.3 表首列 1..7，前端解析器按此序假设）。 */
    public static final List<String> AGENT_LOOP_FRAME_ORDER = List.of(
            FRAME_META, FRAME_STEP, FRAME_TOKEN, FRAME_DELTA, FRAME_CITATION, FRAME_ERROR, FRAME_DONE);

    /**
     * WAgent run 流八帧固定序（详设-10 §10.3.6 表首列 1..8）。
     *
     * <p>与七帧的唯一词面差异是第 6 帧 {@code degrade}；其余七词同形不同册——
     * 键面见 {@link #WAGENT_FRAME_KEYS}，两套键面不共用，禁以"名字一样"推断"载荷一样"。</p>
     */
    public static final List<String> WAGENT_FRAME_ORDER = List.of(
            FRAME_META, FRAME_STEP, FRAME_TOKEN, FRAME_DELTA, FRAME_CITATION,
            FRAME_DEGRADE, FRAME_ERROR, FRAME_DONE);

    // ── 键面 ──

    public static final String KEY_TRACE_ID = "traceId";
    public static final String KEY_SESSION_ID = "sessionId";
    public static final String KEY_MODEL = "model";
    public static final String KEY_CONTEXT_LIMIT = "contextLimit";
    public static final String KEY_ESTIMATED_TOKENS = "estimatedTokens";
    public static final String KEY_OP = "op";
    public static final String KEY_STEP = "step";
    public static final String KEY_TOOL = "tool";
    public static final String KEY_DECISION = "decision";
    public static final String KEY_PROMPT_TOKENS = "promptTokens";
    public static final String KEY_COMPLETION_TOKENS = "completionTokens";
    public static final String KEY_ESTIMATED = "estimated";
    public static final String KEY_TEXT = "text";
    public static final String KEY_FORECAST_RUN_ID = "forecastRunId";
    public static final String KEY_CALIBER_ID = "caliberId";
    public static final String KEY_VERSION = "version";
    public static final String KEY_AS_OF_TIME = "asOfTime";
    public static final String KEY_LINKS = "links";
    public static final String KEY_ERROR_CODE = "errorCode";
    public static final String KEY_CLASS = "class";
    public static final String KEY_MESSAGE = "message";
    public static final String KEY_RETRYABLE = "retryable";
    public static final String KEY_TOTAL_TOKENS = "totalTokens";
    public static final String KEY_COMPRESSION_MODE = "compressionMode";

    /** 引用帧四要素齐备才算可用（与 ai-engine 侧引用块模板同口径，缺任一项即不发帧）。 */
    public static final List<String> CITATION_REQUIRED_KEYS = List.of(
            KEY_FORECAST_RUN_ID, KEY_CALIBER_ID, KEY_VERSION, KEY_AS_OF_TIME);

    // ── WAgent run 流键面（详设-10 §10.3.6 逐帧 data 列，camelCase 与七帧同形但载荷独立）──

    public static final String KEY_RUN_ID = "runId";
    /** 行级续传/对账关联位（V235 run_event 行 id；只有带它的帧可被 {@code Last-Event-ID} 锚定）。 */
    public static final String KEY_EVENT_ID = "eventId";
    public static final String KEY_PLAN_SOURCE = "planSource";
    public static final String KEY_BUDGET = "budget";
    /** 流形态标注（P-4 事件总线未接线时如实标 table-polling-replay，禁冒充实时推送）。 */
    public static final String KEY_STREAM_MODE = "streamMode";
    /** 载荷形态标注（§6.3 引用型；实流若含内联 JSON 必须显式点名，禁由列名反推）。 */
    public static final String KEY_PAYLOAD_KIND = "payloadKind";
    public static final String KEY_STEP_KEY = "stepKey";
    public static final String KEY_STEP_TYPE = "stepType";
    public static final String KEY_ATTEMPT = "attempt";
    public static final String KEY_POLICY_EFFECT = "policyEffect";
    public static final String KEY_STATUS = "status";
    public static final String KEY_GRADE = "grade";
    public static final String KEY_DEGRADATIONS = "degradations";
    public static final String KEY_DG_CODE = "dgCode";
    public static final String KEY_APPLIES_TO = "appliesTo";
    public static final String KEY_REQUIRES_ACK = "requiresAck";
    public static final String KEY_NOTE = "note";
    public static final String KEY_EVENT_TYPE = "eventType";
    public static final String KEY_OCCUR_TIME = "occurTime";
    public static final String KEY_CLAIM_ID = "claimId";
    public static final String KEY_EVIDENCE_ID = "evidenceId";
    public static final String KEY_SOURCE_ENGINE = "sourceEngine";
    public static final String KEY_SOURCE_OBJECT_ID = "sourceObjectId";
    public static final String KEY_AS_OF = "asOf";

    /**
     * 八帧逐帧键面（§10.3.6 data 列逐字登记，不含本通道自加键）。
     *
     * <p>发射侧只能用这里的键 + {@link #WAGENT_CHANNEL_EXTRA_KEYS} 两枚白名单，禁自拼第三套键名。
     * 本表刻意保持"设计原文有什么就是什么"：设计缺位的键（如 milestone）不进此表，
     * 免得登记表被写成实现自证的清单。</p>
     */
    public static final Map<String, List<String>> WAGENT_FRAME_KEYS = wagentFrameKeys();

    /**
     * 本通道在八帧键面之外<b>白名单自加</b>的键（只许这三枚）。
     *
     * <p>存在理由：{@code eventId} 是 {@code Last-Event-ID} 续传与服务端对账的关联位，
     * {@code streamMode}/{@code payloadKind} 是 F10-16（P-4 未接线）与 §6.3（引用型载荷）
     * 的诚实标注位。三者都不在 §10.3.6 里，因此单列登记，不与设计键混成一张表。</p>
     */
    public static final List<String> WAGENT_CHANNEL_EXTRA_KEYS = List.of(
            KEY_EVENT_ID, KEY_STREAM_MODE, KEY_PAYLOAD_KIND);

    private static Map<String, List<String>> wagentFrameKeys() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put(FRAME_META, List.of(KEY_TRACE_ID, KEY_RUN_ID, KEY_PLAN_SOURCE, KEY_BUDGET));
        m.put(FRAME_STEP, List.of(KEY_TRACE_ID, KEY_STEP_KEY, KEY_STEP_TYPE, KEY_TOOL,
                KEY_ATTEMPT, KEY_STATUS, KEY_POLICY_EFFECT));
        m.put(FRAME_TOKEN, List.of(KEY_TRACE_ID, KEY_PROMPT_TOKENS, KEY_COMPLETION_TOKENS, KEY_ESTIMATED));
        m.put(FRAME_DELTA, List.of(KEY_TRACE_ID, KEY_TEXT));
        m.put(FRAME_CITATION, List.of(KEY_TRACE_ID, KEY_CLAIM_ID, KEY_EVIDENCE_ID, KEY_SOURCE_ENGINE,
                KEY_SOURCE_OBJECT_ID, KEY_VERSION, KEY_AS_OF));
        m.put(FRAME_DEGRADE, List.of(KEY_TRACE_ID, KEY_DG_CODE, KEY_APPLIES_TO, KEY_REQUIRES_ACK, KEY_NOTE));
        m.put(FRAME_ERROR, List.of(KEY_TRACE_ID, KEY_ERROR_CODE, KEY_MESSAGE, KEY_RETRYABLE));
        m.put(FRAME_DONE, List.of(KEY_TRACE_ID, KEY_RUN_ID, KEY_STATUS, KEY_GRADE, KEY_DEGRADATIONS));
        return Collections.unmodifiableMap(m);
    }

    /** {@code milestone} 补充帧键面（不在八帧序内，故单独登记，禁与 step 载荷混用）。 */
    public static final List<String> MILESTONE_FRAME_KEYS = List.of(
            KEY_TRACE_ID, KEY_EVENT_ID, KEY_EVENT_TYPE, KEY_OCCUR_TIME, KEY_PAYLOAD_KIND);

    private SseFrameContract() {
        throw new UnsupportedOperationException("常量/校验类，禁止实例化");
    }

    /** 该事件名是否属于七帧词表。 */
    public static boolean isKnownFrame(String event) {
        return event != null && AGENT_LOOP_FRAME_ORDER.contains(event);
    }

    /** 帧序位（1 起）；未知帧名返回 {@link AgentLoopTrace#UNKNOWN}。 */
    public static int frameIndex(String event) {
        int at = event == null ? -1 : AGENT_LOOP_FRAME_ORDER.indexOf(event);
        return at < 0 ? (int) AgentLoopTrace.UNKNOWN : at + 1;
    }

    /** 末帧判定（前端据此收尾、断线重连以 {@code Last-Event-ID} 续传的前提是末帧恒在）。 */
    public static boolean isTerminal(String event) {
        return FRAME_DONE.equals(event);
    }

    /**
     * 组装一帧 data：先落 {@code traceId}（每帧必带），再按入参顺序落业务键。
     *
     * @throws IllegalArgumentException 帧名不在七帧词表内（发射侧的编程缺陷，fail-loud）
     */
    public static Map<String, Object> frame(String event, String traceId, Map<String, ?> fields) {
        if (!isKnownFrame(event)) {
            throw new IllegalArgumentException("未知 SSE 帧名: " + event + "（词表单源=SseFrameContract）");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_TRACE_ID, textOrAbsent(traceId));
        if (fields != null) {
            for (Map.Entry<String, ?> e : fields.entrySet()) {
                if (KEY_TRACE_ID.equals(e.getKey())) {
                    continue;
                }
                if (e.getValue() != null) {
                    data.put(e.getKey(), e.getValue());
                }
            }
        }
        return Collections.unmodifiableMap(data);
    }

    /**
     * 用量帧：分项缺失写 {@code -1}，禁以 0 冒充"实测为零"；{@code estimated} 标明该值是否为估算。
     */
    public static Map<String, Object> usageFrame(String traceId, long promptTokens, long completionTokens,
                                                 boolean estimated) {        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(KEY_PROMPT_TOKENS, promptTokens);
        fields.put(KEY_COMPLETION_TOKENS, completionTokens);
        fields.put(KEY_ESTIMATED, estimated);
        return frame(FRAME_TOKEN, traceId, fields);
    }

    /**
     * 引用帧：四要素不齐一律返回空 ⇒ 调用方<b>不发帧</b>。
     *
     * <p>宁缺毋造：引用字段只能从工具返回体取，缺任一要素时发帧即等于伪造引用
     * （F06-11 要点 4 的上游已判 {@code CITATION_MISSING}，承流侧不得补默认值）。</p>
     */
    public static Optional<Map<String, Object>> citationFrame(String traceId, Map<String, ?> citationFields) {
        if (citationFields == null) {
            return Optional.empty();
        }
        List<String> missing = new ArrayList<>();
        for (String key : CITATION_REQUIRED_KEYS) {
            if (!isText(citationFields.get(key))) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        for (String key : CITATION_REQUIRED_KEYS) {
            fields.put(key, String.valueOf(citationFields.get(key)));
        }
        Object links = citationFields.get(KEY_LINKS);
        fields.put(KEY_LINKS, links == null ? List.of() : links);
        return Optional.of(frame(FRAME_CITATION, traceId, fields));
    }

    /**
     * 故障帧：在 {@code AiError#toSseErrorFrame()} 的四键之上补 {@code traceId}。
     *
     * <p>本方法只搬运已渲染的模板消息，不接收 {@code Throwable} —— 异常原文外泄面
     * 由类型形态挡住，而不是靠调用方自觉。</p>
     */
    public static Map<String, Object> errorFrame(String traceId, Map<String, ?> sseErrorFrameFromAiError) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (sseErrorFrameFromAiError != null) {
            fields.put(KEY_ERROR_CODE, sseErrorFrameFromAiError.get(KEY_ERROR_CODE));
            fields.put(KEY_CLASS, sseErrorFrameFromAiError.get(KEY_CLASS));
            fields.put(KEY_MESSAGE, sseErrorFrameFromAiError.get(KEY_MESSAGE));
            fields.put(KEY_RETRYABLE, sseErrorFrameFromAiError.get(KEY_RETRYABLE));
        }
        return frame(FRAME_ERROR, traceId, fields);
    }

    /**
     * 帧序校验（§9.3.3 + §10.3.6「error 发出后紧接 done」的可静态断言部分）。
     *
     * <p>规则：非空、首帧 {@code meta}、末帧 {@code done}、帧名全在词表内、
     * {@code error} 至多一条且必须紧邻末帧之前。</p>
     *
     * <p><b>为什么不校验"序位单调不减"</b>：多轮循环里 {@code step(llm_call)} 与
     * {@code token} 会在首批 {@code delta} 之后再出现一轮，§13.4.5 的「按此序假设」
     * 指的是分组的相对位置而非全局单调；把单调写成硬闸会把一条合规的多轮流判成违例，
     * 属于自造判据。引用与故障的尾部次序另有条款守住（{@code error} 紧邻 {@code done}
     * 之前 ⇒ 引用不可能落在故障之后）。</p>
     *
     * @return 违例描述；合法序列返回空
     */
    public static Optional<String> validateSequence(List<String> events) {
        if (events == null || events.isEmpty()) {
            return Optional.of("空序列：承流必须至少含 meta 与 done 两帧");
        }
        for (String event : events) {
            if (!isKnownFrame(event)) {
                return Optional.of("未知帧名: " + event + "（词表单源=SseFrameContract）");
            }
        }
        if (!FRAME_META.equals(events.get(0))) {
            return Optional.of("首帧必须为 " + FRAME_META + "，实得 " + events.get(0));
        }
        String last = events.get(events.size() - 1);
        if (!FRAME_DONE.equals(last)) {
            return Optional.of("末帧必须为 " + FRAME_DONE + "，实得 " + last);
        }
        int errorCount = 0;
        int errorAt = -1;
        for (int i = 0; i < events.size(); i++) {
            if (FRAME_ERROR.equals(events.get(i))) {
                errorCount++;
                errorAt = i;
            }
        }
        if (errorCount > 1) {
            return Optional.of("error 帧至多一条，实得 " + errorCount + " 条");
        }
        if (errorCount == 1 && errorAt != events.size() - 2) {
            return Optional.of("error 帧必须紧接 done 之前，实得下标 " + errorAt + "/" + (events.size() - 1));
        }
        return Optional.empty();
    }

    /**
     * WAgent 通道装配一帧（八帧 ∪ {@code milestone}）。
     *
     * <p>与 {@link #frame(String, String, Map)} 分途而非合并：合并后任一通道都能借用另一通道的
     * 帧名（degrade 混进 agent-loop、milestone 混进 done 之前），而这类漂移正是本册要收的病灶。
     * 键面同样收口：非白名单键直接抛，让"自加第四个键"在写出前就可见。</p>
     *
     * @throws IllegalArgumentException 帧名不在 W 词表内，或键不在该帧登记表内
     */
    public static Map<String, Object> wagentFrame(String event, String traceId, Map<String, ?> fields) {
        List<String> allowed = wagentFrameKeys(event);
        if (allowed == null) {
            throw new IllegalArgumentException("未知 WAgent 帧名: " + event + "（词表单源=SseFrameContract）");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_TRACE_ID, textOrAbsent(traceId));
        if (fields != null) {
            for (Map.Entry<String, ?> e : fields.entrySet()) {
                String key = e.getKey();
                if (KEY_TRACE_ID.equals(key) || e.getValue() == null) {
                    continue;
                }
                if (!allowed.contains(key) && !WAGENT_CHANNEL_EXTRA_KEYS.contains(key)) {
                    throw new IllegalArgumentException("帧 " + event + " 的键 " + key
                            + " 不在 §10.3.6 登记表内（W 键面单源=SseFrameContract.WAGENT_FRAME_KEYS）");
                }
                data.put(key, e.getValue());
            }
        }
        return Collections.unmodifiableMap(data);
    }

    /**
     * 降级帧载荷四键（§10.3.6 帧 6 {@code {dgCode,appliesTo,requiresAck,note}}）。
     *
     * <p>本方法不校验 DG 码白名单——白名单在 W 域（{@code WAgentEnums.DegradeCode} 8 值，
     * F10-10「配置可关不可开新」）。common-api 若自抄一份码集就成了第二真值面。</p>
     *
     * @throws IllegalArgumentException dgCode 或 note 为空装（无码即无降级，禁发空帧冒充）
     */
    public static Map<String, Object> degradeFields(String dgCode, String appliesTo,
                                                    boolean requiresAck, String note) {
        if (!isText(dgCode) || !isText(note)) {
            throw new IllegalArgumentException("degrade 帧必须同时带 dgCode 与 note（空码/空说明即无真值，禁发帧）");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(KEY_DG_CODE, dgCode.trim());
        fields.put(KEY_APPLIES_TO, textOrAbsent(appliesTo));
        fields.put(KEY_REQUIRES_ACK, requiresAck);
        fields.put(KEY_NOTE, note);
        return fields;
    }

    /**
     * W 通道帧序校验（§10.3.6 的可静态断言部分）。
     *
     * <p>规则与七帧面同形：非空、首帧 {@code meta}、末帧 {@code done}、帧名全在 W 词表内、
     * {@code error} 至多一条且必须紧邻末帧之前。多出的 {@code milestone} 只允许出现在
     * 首末帧之间（不是生命周期帧冒充首末帧）。同样<b>不校验全局序位单调</b>，
     * 理由见 {@link #validateSequence(List)} 的注释。</p>
     */
    public static Optional<String> validateWagentSequence(List<String> events) {
        if (events == null || events.isEmpty()) {
            return Optional.of("空序列：W 通道必须至少含 meta 与 done 两帧");
        }
        for (String event : events) {
            if (!isKnownWagentFrame(event)) {
                return Optional.of("未知 WAgent 帧名: " + event + "（词表单源=SseFrameContract）");
            }
        }
        if (!FRAME_META.equals(events.get(0))) {
            return Optional.of("首帧必须为 " + FRAME_META + "，实得 " + events.get(0));
        }
        String last = events.get(events.size() - 1);
        if (!FRAME_DONE.equals(last)) {
            return Optional.of("末帧必须为 " + FRAME_DONE + "，实得 " + last);
        }
        int errorCount = 0;
        int errorAt = -1;
        for (int i = 0; i < events.size(); i++) {
            if (FRAME_ERROR.equals(events.get(i))) {
                errorCount++;
                errorAt = i;
            }
        }
        if (errorCount > 1) {
            return Optional.of("error 帧至多一条，实得 " + errorCount + " 条");
        }
        if (errorCount == 1 && errorAt != events.size() - 2) {
            return Optional.of("error 帧必须紧接 done 之前，实得下标 " + errorAt + "/" + (events.size() - 1));
        }
        return Optional.empty();
    }

    /** 该事件名是否属于 W 通道词表（八帧 + milestone 补充帧）。 */
    public static boolean isKnownWagentFrame(String event) {
        return event != null
                && (WAGENT_FRAME_ORDER.contains(event) || FRAME_MILESTONE.equals(event));
    }

    /** W 帧序位（1 起，按 §10.3.6 表首列）；{@code milestone} 与未知帧名同取 {@link AgentLoopTrace#UNKNOWN}。 */
    public static int wagentFrameIndex(String event) {
        int at = event == null ? -1 : WAGENT_FRAME_ORDER.indexOf(event);
        return at < 0 ? (int) AgentLoopTrace.UNKNOWN : at + 1;
    }

    /** 该帧的键面登记表；{@code milestone} 走补充表，未知帧名返回 {@code null}（调用方 fail-loud）。 */
    private static List<String> wagentFrameKeys(String event) {
        if (FRAME_MILESTONE.equals(event)) {
            return MILESTONE_FRAME_KEYS;
        }
        return WAGENT_FRAME_KEYS.get(event);
    }

    /** W 通道词表（八帧 + milestone）；供门禁与前端契约对账，禁在调用侧另抄一份。 */
    public static List<String> wagentVocabulary() {
        List<String> all = new ArrayList<>(WAGENT_FRAME_ORDER);
        all.add(FRAME_MILESTONE);
        return List.copyOf(all);
    }

    /** 字符串缺失标记（复用日志面单源常量，禁在此另写 "-"）。 */
    public static String textOrAbsent(String value) {
        return isText(value) ? value : AgentLoopTrace.ABSENT;
    }

    /** 数值缺失标记（同 {@link AgentLoopTrace#UNKNOWN}，禁 0 冒充）。 */
    public static long unknownToken() {
        return AgentLoopTrace.UNKNOWN;
    }

    private static boolean isText(Object value) {
        return value instanceof String s && !s.isBlank();
    }
}
