package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolDecision;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolObligations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * {@code rateLimit} 义务的<b>生产侧消费方</b> —— AI 工具咽喉的限流腿
 * （#142/B4-3d-4，收口差距文档 <b>D-B4-21</b>：策略签发限流义务、生产侧零消费方）。
 *
 * <p><b>策略侧事实</b>（{@code ecos-docker/opa-policies/data_access.rego} 的
 * {@code rate_obligations} 规则，本类不复制其语义只做消费）：只有
 * {@code purpose == "ai" && count(violation) == 0} 才签发
 * {@code {"obligation":"rateLimit","rateLimitKey":"ai.tool.<subjectId>"}}；
 * 页面/导出通道恒无该义务。因此消费方落在 AI 工具调用链，而不是"三通道通用限流器"。</p>
 *
 * <p><b>为什么不复用 gateway 的 {@code RateLimitFilter}</b>（裁决登记，差距文档 v1.0.21 ⑤b）：
 * 该过滤器是传输层按 URL 前缀的静态桶（{@code Map<前缀,{次数,窗口}>}，键＝IP+userId+前缀），
 * 既读不到 security 裁决应答里的义务，也产不出 §4.7.2 要求的 DENIED／FAIL_CLOSED 分态
 * （它只会返回 429 文本）。义务驱动限流属于护栏执行面，必须与
 * {@code policyEvaluate}/{@code obligationsApply} 同侧同分类。</p>
 *
 * <p><b>配额数值从哪来</b>：策略只签发<b>键</b>，不签发数值 —— rego 里没有窗口/次数。
 * 数值单源在本类常量（与 {@code ToolObligationApplier#MAX_FILTER_ROWS} 同族的
 * "执行侧预算常量"口径）。把数值搬进 sysman 配置面（{@code config_group} 单表分组）
 * 属策略输入变更，未裁决前禁自造配置链路，登记为后续批（差距 D-B4-32）。</p>
 *
 * <p><b>三态不折叠</b>（§4.7.2「不可用 ≠ 无权限」在执行面的延伸）：</p>
 * <ul>
 *   <li>{@link Verdict#PASS} —— 未签发限流义务（不计额、不判定），或已签发且窗口内有余量；</li>
 *   <li>{@link Verdict#DENIED} —— 义务已签发且<b>窗口内配额用尽</b>：这是策略义务未满足，
 *       映射 403 {@code GUARDRAIL_DENIED}，禁重试；</li>
 *   <li>{@link Verdict#FAIL_CLOSED} —— 义务已签发但<b>形态不合契约</b>（键缺失、
 *       键不匹配 {@link GuardrailDecisionContract#RATE_LIMIT_KEY_PREFIX} 单源前缀、
 *       主体段为空）：护栏给的形态无法消费，映射 503，禁按自造键计额后放行 ——
 *       那会把拒绝态洗成放行态。</li>
 * </ul>
 *
 * <p><b>诚实边界</b>：计数是<b>本进程内存</b>的固定窗口，多实例部署下各实例独立计额、
 * 进程重启即清零。本类因此不具备分布式配额语义，也不冒充具备；
 * 跨实例配额与窗口持久化归后续批（差距 D-B4-32）。</p>
 *
 * <p><b>拒绝态留痕</b>（差距 <b>D-B4-33</b>，#142/B4-3e-1 收口）：DENIED／FAIL_CLOSED 除经分类后的
 * {@code ToolResult} 与日志外显，还必须在咽喉处经 {@code GuardrailAuditPublisher#auditExecutionLegDenial}
 * 落一条 {@code GUARDRAIL_EVAL} 事件 + 一行 V204 {@code GuardrailDecisionLedger}，
 * 台账侧才看得见"义务已放行但执行被拒"这一态（否则 PRD-06 §1.5-1 的对账面上
 * 只有一条 ALLOW，被拒这次调用读起来像"放行且执行成功"）。两条约束由本类持有：</p>
 * <ul>
 *   <li><b>词映射</b>{@link Verdict#ledgerDecision()} —— V204 的
 *       {@code ck_agdl_decision CHECK (decision IN ('ALLOW','DENY','FAIL_CLOSED'))} 不含
 *       {@code DENIED}，直接写 {@code Verdict.DENIED.name()} 会让 INSERT 当场失败、
 *       留痕再次归零，故 {@code DENIED}→{@code DENY} 的折算必须在本类单点完成，
 *       取值一律引用 {@code ToolDecision.Verdict}（该枚举名已与检查约束逐字对齐）；
 *       {@code PASS} 返回 {@code null}＝<b>本腿不留痕</b>，闸侧已写过 ALLOW 行，
 *       再补一行会把"每次裁决一条"的对账口径变成两次；</li>
 *   <li><b>键面不外传</b> {@link RateOutcome#reasonCode()} —— 拒绝原因串按
 *       {@code 码段 + ": " + 明细} 书写，明细段可能含限流键原文（键由主体 ID 拼成），
 *       台账/事件只记码段，与"摘要不 echo 键"（{@code ToolObligations#summary}）同一条红线。</li>
 * </ul>
 */
@Component
public class AiToolRateLimitGate {

    private static final Logger log = LoggerFactory.getLogger(AiToolRateLimitGate.class);

    /** 单主体在一个窗口内允许的工具调用次数（策略签发键、执行侧定数值，见类注释）。 */
    public static final int MAX_CALLS_PER_WINDOW = 20;

    /** 窗口长度（毫秒）。固定窗口：窗口起点取该键首次计额时刻，超窗即重开。 */
    public static final long WINDOW_MILLIS = 60_000L;

    /** 过期窗口的清扫间隔（防长期运行下 map 无界增长），单位毫秒。 */
    static final long SWEEP_INTERVAL_MILLIS = 5 * 60_000L;

    /** 限流腿三态（与 {@code ToolDecision.Verdict} 同族语义，禁与护栏三态混写）。 */
    public enum Verdict {
        PASS, DENIED, FAIL_CLOSED;

        /**
         * 本态在 V204 台账／审计事件上的 {@code decision} 词（差距 D-B4-33 的词映射单点）。
         *
         * <p>取值一律引用 {@link ToolDecision.Verdict} —— 该枚举名与
         * {@code ck_agdl_decision CHECK (decision IN ('ALLOW','DENY','FAIL_CLOSED'))} 逐字对齐，
         * 本腿的 {@code DENIED} 若按 {@code name()} 直写会撞检查约束、留痕当场失效。</p>
         *
         * @return 台账词；{@code PASS} 返回 {@code null}＝本腿不留痕（闸侧已写 ALLOW 行）
         */
        public String ledgerDecision() {
            return switch (this) {
                case PASS -> null;
                case DENIED -> ToolDecision.Verdict.DENY.name();
                case FAIL_CLOSED -> ToolDecision.Verdict.FAIL_CLOSED.name();
            };
        }
    }

    /**
     * 本腿在审计面/台账面上的阶段标识（唯一持有者；咽喉与发布器只引用本常量）。
     *
     * <p>存在的理由＝V204 表没有 stage 列、也不许为它加列（IR03 只加不删 + 一次改表要过 DDL 批），
     * 而同一 traceId 下现在会有两行（闸侧 ALLOW + 本腿 DENY／FAIL_CLOSED）——
     * 没有这个串，两行在库里无法区分，收口 D-B4-33 就等于白做。</p>
     */
    public static final String AUDIT_STAGE = "rate_limit_enforce";

    /**
     * 限流判定结果。
     *
     * @param verdict  三态
     * @param reason   可读原因原文（只入日志与台账分类，禁随响应体上浮，X-49）
     * @param remaining 本窗口剩余额度；未计额（无义务/形态不合）时为 -1
     */
    public record RateOutcome(Verdict verdict, String reason, int remaining) {

        public boolean blocks() {
            return verdict != Verdict.PASS;
        }

        /**
         * 拒绝态码段（{@code 码段 + ": " + 明细} 的前半段）—— 台账/审计事件只记这一段。
         *
         * <p>明细段可能含限流键原文（键里带主体 ID），留痕面 echo 键等于把标识符搬进审计库，
         * 与 {@code ToolObligations#summary()} 的"摘要不记键原文"是同一条红线。</p>
         */
        public String reasonCode() {
            if (reason == null) return null;
            int sep = reason.indexOf(": ");
            return sep < 0 ? reason : reason.substring(0, sep);
        }

        static RateOutcome noObligation() {
            return new RateOutcome(Verdict.PASS, null, -1);
        }

        static RateOutcome pass(int remaining) {
            return new RateOutcome(Verdict.PASS, null, remaining);
        }

        static RateOutcome denied(String reason) {
            return new RateOutcome(Verdict.DENIED, reason, 0);
        }

        static RateOutcome failClosed(String reason) {
            return new RateOutcome(Verdict.FAIL_CLOSED, reason, -1);
        }
    }

    /** key = 义务原文键；value = {本窗口已用次数, 窗口起点毫秒}。 */
    private final ConcurrentHashMap<String, long[]> windows = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private volatile long lastSweep;

    public AiToolRateLimitGate() {
        this(System::currentTimeMillis);
    }

    /** 时钟注入面：窗口翻转/清扫的用例需要可控时间（生产路径走无参构造）。 */
    AiToolRateLimitGate(LongSupplier clock) {
        this.clock = clock;
        this.lastSweep = clock.getAsLong();
    }

    /**
     * 咽喉调用面：按 ALLOW 裁决签发的义务决定是否放行本次工具调用。
     *
     * <p>未签发限流义务 ⇒ 直接放行且<b>不计额</b>（页面/导出通道与"策略没说话"的
     * AI 请求都走这条路；计数只在义务存在时才有语义）。</p>
     */
    public RateOutcome enforce(ToolObligations obligations) {
        if (obligations == null || !obligations.declaresRateLimit()) {
            return RateOutcome.noObligation();
        }
        return acquire(obligations.rateLimitKey());
    }

    /**
     * 键面入口：校验键形态并按窗口计额。
     *
     * <p>键原文一律来自策略义务（经 {@link GuardrailDecisionContract} 单源读数），
     * 本方法不接收主体标识、不自行拼键 —— 否则等于在执行面重建一套键面（D-B4-21 的根因形态）。</p>
     */
    public RateOutcome acquire(String rateLimitKey) {
        if (rateLimitKey == null || rateLimitKey.isBlank()) {
            log.warn("限流义务形态不合契约，拒绝执行（fail-closed）: reason={}", REASON_KEY_MISSING);
            return RateOutcome.failClosed(REASON_KEY_MISSING);
        }
        String prefix = GuardrailDecisionContract.RATE_LIMIT_KEY_PREFIX;
        if (!rateLimitKey.startsWith(prefix)) {
            log.warn("限流键与单源前缀不符 ⇒ 键面漂移，拒绝按自造键计额: key={} prefixExpected={}",
                    rateLimitKey, prefix);
            return RateOutcome.failClosed(REASON_KEY_OFF_CONTRACT + ": " + rateLimitKey);
        }
        if (rateLimitKey.length() == prefix.length()) {
            // "ai.tool." 后面没有主体段 = 主体未解析（策略侧 subject_id 默认空串）⇒ 无法按主体配额
            log.warn("限流键主体段为空 ⇒ 无法按主体计额，拒绝执行（fail-closed）: key={}", rateLimitKey);
            return RateOutcome.failClosed(REASON_SUBJECT_SEGMENT_EMPTY);
        }

        long now = clock.getAsLong();
        sweepIfDue(now);
        long[] state = windows.compute(rateLimitKey, (key, current) -> {
            if (current == null || now - current[1] >= WINDOW_MILLIS) {
                return new long[]{1L, now};
            }
            current[0] = current[0] + 1L;
            return current;
        });
        long used = state[0];
        if (used > MAX_CALLS_PER_WINDOW) {
            log.info("限流义务配额用尽，拒绝工具调用（策略结论，不重试）: key={} used={} limit={} windowMs={}",
                    rateLimitKey, used, MAX_CALLS_PER_WINDOW, WINDOW_MILLIS);
            return RateOutcome.denied(REASON_QUOTA_EXCEEDED + ": " + used + "/" + MAX_CALLS_PER_WINDOW);
        }
        return RateOutcome.pass(MAX_CALLS_PER_WINDOW - (int) used);
    }

    // ── 已知拒绝态串（单点定义，供分态断言与台账分类引用） ──

    /** 义务声明了限流但键缺失/空白 ⇒ 形态不合契约。 */
    public static final String REASON_KEY_MISSING = "AI_TOOL_RATE_LIMIT_KEY_MISSING";
    /** 键不匹配单源前缀 ⇒ 键面漂移。 */
    public static final String REASON_KEY_OFF_CONTRACT = "AI_TOOL_RATE_LIMIT_KEY_OFF_CONTRACT";
    /** 键形如 {@code ai.tool.}（主体段为空）⇒ 无法按主体计额。 */
    public static final String REASON_SUBJECT_SEGMENT_EMPTY = "AI_TOOL_RATE_LIMIT_SUBJECT_EMPTY";
    /** 窗口内配额用尽 ⇒ 义务未满足（策略结论，非不可用）。 */
    public static final String REASON_QUOTA_EXCEEDED = "AI_TOOL_RATE_LIMIT_EXCEEDED";
    /**
     * 消费方（本类）未装配 ⇒ 义务存在却无人计额（咽喉侧使用，D-B4-21 的原形态标记）。
     *
     * <p>本类是它的唯一持有者：咽喉文案若内联同一串字面量，就有两处真值可各自漂移
     * （静态门 {@code RateLimitObligationConsumerGateTest} 按"主源码内单持有者"钉住）。</p>
     */
    public static final String REASON_GATE_UNWIRED = "AI_TOOL_RATE_LIMIT_GATE_UNWIRED";

    /** 只读观测面：某键本窗口已用次数（无记录＝0）。 */
    public int usedCalls(String rateLimitKey) {
        long[] state = windows.get(rateLimitKey);
        return state == null ? 0 : (int) state[0];
    }

    /** 只读观测面：当前窗口表快照（键 → 已用次数），用例与告警侧对账用。 */
    public Map<String, Integer> windowSnapshot() {
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        windows.forEach((key, state) -> out.put(key, (int) state[0]));
        return out;
    }

    /** 测试/运维面：清空计额窗口。生产路径不在调用链上（调用即计额）。 */
    public void reset() {
        windows.clear();
    }

    private void sweepIfDue(long now) {
        if (now - lastSweep < SWEEP_INTERVAL_MILLIS) {
            return;
        }
        lastSweep = now;
        List<String> expired = new ArrayList<>();
        windows.forEach((key, state) -> {
            if (now - state[1] >= WINDOW_MILLIS) {
                expired.add(key);
            }
        });
        expired.forEach(windows::remove);
    }
}
