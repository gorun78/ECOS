package com.chinacreator.gzcm.common.security.abac;

import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;

import java.util.Locale;
import java.util.Map;

/**
 * security-engine {@code POST /api/v1/security/policy-engine/evaluate} 应答的<b>裁决读数单源</b>
 * （#142/B4-3e-4，差距 D-B4-37）。
 *
 * <p><b>为什么必须存在</b>：同一份应答的裁决键曾被七个读侧文件（分属七个模块）各自内联比对
 * 共 30 处（{@code "allow"} / 历史别名 {@code "result"} / {@code "decision"} / {@code "verdict"}
 * 及 {@code "true"|"allow"|"permit"} 词表；HEAD 实测口径见
 * {@code AbacEvaluateResponseSingleSourceTest} 类注释）。键面一改，读侧不会报错、只会<b>静默恒判</b>：
 * 只认 {@code result} 的三个消费者（kb 导航写闸、本体操作授权、workspace 导出咽喉）
 * 实测在现网应答形态下永远读到 {@code null} ⇒ 无论策略怎么判都 DENY
 * （D-B4-39）。这类缺陷无法靠"fail-closed 所以安全"免责：护栏明明说了放行，
 * 读侧把它读成拒绝，功能面永久失效，而单测用自造的历史形态夹具照样绿。</p>
 *
 * <p><b>与 {@link GuardrailDecisionContract} 的分工</b>：那一份是<b>义务词表与护栏态串</b>的单源
 * （策略名、{@code obligations} 四类义务、四态状态串）；本份是<b>应答 envelope 的裁决读数</b>，
 * 只在它之上加三件事：①顶层/嵌套 {@code result} 两种 envelope 形态的展开，
 * ②历史别名与显式 {@code decision}/{@code verdict} 词表，③"读不到＝护栏没说话"的
 * 第三态归一。本类不复定义任何护栏单源已有的键（{@link #KEY_ALLOW} 直接引用）。</p>
 *
 * <p><b>读数≠判定</b>：本类只把应答翻译成三态，不产生业务结论；
 * {@link Verdict#UNEVALUATED} 的含义是「应答里没有可识别裁决」，调用方必须报
 * 503／FAIL_CLOSED，禁折叠成 403 策略拒绝（§4.7.2「不可用 ≠ 无权限」、
 * {@link GuardrailDecisionContract#isUnevaluated}）。</p>
 */
public final class AbacEvaluateResponseContract {

    private AbacEvaluateResponseContract() {
    }

    /**
     * 应答裁决三态。
     *
     * <ul>
     *   <li>{@link #ALLOW} —— 应答明确放行（显式词表命中或布尔真）；</li>
     *   <li>{@link #DENY} —— 应答明确拒绝，属真实策略判定 ⇒ 可映射 403；</li>
     *   <li>{@link #UNEVALUATED} —— 护栏没说话：策略未加载／OPA 不可达兜底态／
     *       键缺失／值形态不可识别 ⇒ 只能 fail-closed 且按不可用上报。</li>
     * </ul>
     */
    public enum Verdict {
        /** 明确放行。 */
        ALLOW,
        /** 明确拒绝（真实策略判定）。 */
        DENY,
        /** 无可识别裁决（护栏未说话，禁冒充策略拒绝）。 */
        UNEVALUATED
    }

    // ── 应答键面（单点定义；禁在消费者处内联字面量，由静态门禁钉住） ──

    /** 布尔裁决键 —— 引用护栏单源，禁在此重新定义字面量。 */
    public static final String KEY_ALLOW = GuardrailDecisionContract.KEY_ALLOW;

    /**
     * 历史别名裁决键。
     *
     * <p><b>实测（#142/B4-3e-4）现契约不使用该键</b>：
     * {@code PolicyEngineController#evaluate} 的应答 {@code data} 段由
     * {@code OpaPolicyService} 组装，只写 {@code allow}＋{@code policyState}＋
     * {@code obligations} 等，从不写布尔 {@code result}。保留本读数分支只为兼容
     * 另一 envelope 形态（{@code SecurityPolicyController#evaluate} 把整份裁决塞进
     * {@code result} 对象，见 {@link #unwrap}）与仍按旧形态应答的桩，
     * <b>不</b>表示"两个键都是现行契约"。</p>
     */
    public static final String KEY_RESULT_LEGACY = "result";

    /** 显式裁决串键（优先于布尔键读数）。 */
    public static final String KEY_DECISION = "decision";
    /** 显式裁决串键的第二书写（旧一代）。 */
    public static final String KEY_VERDICT = "verdict";

    /** 应答来源键（值 {@code fallback} ＝ security 自身兜底而非策略裁决）。 */
    public static final String KEY_SOURCE = "source";
    /** {@link #KEY_SOURCE} 的正常态取值：OPA 真给了裁决（生产侧写面同样取本常量）。 */
    public static final String VALUE_SOURCE_OPA = "opa";
    /** {@link #KEY_SOURCE} 的兜底态取值 —— 属不可用，不属拒绝。 */
    public static final String VALUE_SOURCE_FALLBACK = "fallback";
    /**
     * 兜底理由键（不可用形态应答里写 {@link #VALUE_FALLBACK_DENY_OPA_DOWN}）。
     *
     * <p>消费方常把它当日志／审计理由原文展示；本键存在即佐证"护栏没说话"，
     * 读数结果一律 {@link Verdict#UNEVALUATED}。</p>
     */
    public static final String KEY_FALLBACK = "fallback";
    /** {@link #KEY_FALLBACK} 的现行取值：OPA 宕机 → security 侧 fail-close 兜底。 */
    public static final String VALUE_FALLBACK_DENY_OPA_DOWN = "DENY_OPA_DOWN";
    /** OPA 错误标记键（存在即不可用态）。 */
    public static final String KEY_OPA_ERROR = "opaError";

    // ── 裁决词表（大小写各一支，读数点统一做 trim + 归一） ──

    /** 放行词（布尔键的字符串形态与显式裁决键共用一套小写归一词表）。 */
    public static final String TOKEN_ALLOW = "allow";
    /** 放行词（同义书写）。 */
    public static final String TOKEN_PERMIT = "permit";
    /** 布尔真字的字符串形态。 */
    public static final String TOKEN_TRUE = "true";
    /** 拒绝词。 */
    public static final String TOKEN_DENY = "deny";
    /** 拒绝词（同义书写）。 */
    public static final String TOKEN_DENIED = "denied";
    /** 拒绝词（同义书写，仅显式裁决键认；布尔键不认，避免把 {@code reject:true} 读成放行）。 */
    public static final String TOKEN_REJECT = "reject";
    /** 布尔假字的字符串形态。 */
    public static final String TOKEN_FALSE = "false";

    /**
     * 读应答裁决三态 —— 所有 evaluate 应答读侧唯一允许的读数入口。
     *
     * <p>判定顺序固定（与收口前 ai-engine 侧最严格的一支逐条对齐，防止改读时放宽）：
     * <ol>
     *   <li>null／非 Map ⇒ {@link Verdict#UNEVALUATED}；</li>
     *   <li>先把两种 envelope 形态归一（{@link #unwrap}）；</li>
     *   <li>{@code policyState} 为未评估态（策略未加载／OPA 不可用）⇒ UNEVALUATED；</li>
     *   <li>{@code source=fallback} 或带 {@code opaError} ⇒ UNEVALUATED
     *       （security 自己走兜底，不等于策略判了拒绝）；</li>
     *   <li>显式 {@code decision}／{@code verdict} ⇒ 词表命中 ALLOW／DENY，
     *       未命中 ⇒ UNEVALUATED（禁默认放行兜底）；</li>
     *   <li>{@code policyState} 为 EVALUATED_DENY／EVALUATED_ALLOW ⇒ DENY／ALLOW；</li>
     *   <li>布尔裁决键 {@code allow}，缺失才退到历史别名 {@code result}；
     *       缺失／非布尔非字符串／词表未命中 ⇒ UNEVALUATED。</li>
     * </ol>
     *
     * @param dataSegment 应答的 {@code data} 段原值（可为 {@code null}）
     */
    public static Verdict verdictOf(Object dataSegment) {
        Map<String, Object> data = unwrap(dataSegment);
        if (data == null) return Verdict.UNEVALUATED;

        String policyState = GuardrailDecisionContract.stateOf(data);
        if (GuardrailDecisionContract.isUnevaluated(policyState)) return Verdict.UNEVALUATED;
        if (isFallbackSource(data)) return Verdict.UNEVALUATED;

        Object explicit = data.get(KEY_DECISION) != null ? data.get(KEY_DECISION) : data.get(KEY_VERDICT);
        if (explicit != null) {
            String s = normalized(String.valueOf(explicit));
            if (TOKEN_ALLOW.equals(s) || TOKEN_PERMIT.equals(s) || TOKEN_TRUE.equals(s)) return Verdict.ALLOW;
            if (TOKEN_DENY.equals(s) || TOKEN_DENIED.equals(s) || TOKEN_REJECT.equals(s)) return Verdict.DENY;
            return Verdict.UNEVALUATED;
        }
        if (GuardrailDecisionContract.STATE_DENY.equals(policyState)) return Verdict.DENY;
        if (GuardrailDecisionContract.STATE_ALLOW.equals(policyState)) return Verdict.ALLOW;

        Object flag = data.get(KEY_ALLOW) != null ? data.get(KEY_ALLOW) : data.get(KEY_RESULT_LEGACY);
        if (flag == null) return Verdict.UNEVALUATED;
        if (flag instanceof Boolean b) return b ? Verdict.ALLOW : Verdict.DENY;
        String s = normalized(String.valueOf(flag));
        if (TOKEN_TRUE.equals(s) || TOKEN_ALLOW.equals(s) || TOKEN_PERMIT.equals(s)) return Verdict.ALLOW;
        if (TOKEN_FALSE.equals(s) || TOKEN_DENY.equals(s) || TOKEN_DENIED.equals(s)) return Verdict.DENY;
        return Verdict.UNEVALUATED;
    }

    /** 恰为明确放行才 {@code true}；DENY 与 UNEVALUATED 一律 {@code false}（fail-closed）。 */
    public static boolean isExplicitAllow(Object dataSegment) {
        return verdictOf(dataSegment) == Verdict.ALLOW;
    }

    /**
     * 两种 envelope 形态归一。
     *
     * <p>{@code PolicyEngineController} 直接把裁决摊在 {@code data} 段；
     * {@code SecurityPolicyController} 则回 {@code {input:…, result:{整份裁决}}}。
     * 顶层没有任何可识别裁决键、而 {@code result} 是对象时进 {@code result} 再读一次；
     * {@code result} 是标量时留在顶层走别名布尔读数。只下钻一层——再嵌套即形态异常，
     * 按 UNEVALUATED 处理，不做递归放宽。</p>
     */
    private static Map<String, Object> unwrap(Object dataSegment) {
        if (!(dataSegment instanceof Map<?, ?> outer)) return null;
        Map<String, Object> data = cast(outer);
        if (hasDecisionKey(data)) return data;
        if (data.get(KEY_RESULT_LEGACY) instanceof Map<?, ?> inner) {
            Map<String, Object> nested = cast(inner);
            return hasDecisionKey(nested) ? nested : data;
        }
        return data;
    }

    /** 该层是否携带任一裁决读数键（键面判据取自本类常量，禁另立一套）。 */
    private static boolean hasDecisionKey(Map<String, Object> data) {
        return data.containsKey(KEY_ALLOW)
                || data.containsKey(KEY_DECISION)
                || data.containsKey(KEY_VERDICT)
                || data.containsKey(GuardrailDecisionContract.KEY_POLICY_STATE);
    }

    /** 兜底态识别：{@code source=fallback} 或带 {@code opaError} ⇒ 护栏未参与裁决。 */
    private static boolean isFallbackSource(Map<String, Object> data) {
        if (data.containsKey(KEY_OPA_ERROR)) return true;
        Object source = data.get(KEY_SOURCE);
        return source != null && VALUE_SOURCE_FALLBACK.equalsIgnoreCase(normalized(String.valueOf(source)));
    }

    private static String normalized(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }
}
