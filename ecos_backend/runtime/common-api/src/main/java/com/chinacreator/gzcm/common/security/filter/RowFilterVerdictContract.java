package com.chinacreator.gzcm.common.security.filter;

import com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract;
import com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract.Verdict;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * security-engine {@code POST /api/security/evaluate-filter}（行过滤）应答的<b>键面与裁决读数单源</b>
 * （#142/B4-3e-7，收口差距 <b>D-B4-42</b>）。
 *
 * <p><b>为什么必须存在</b>：这条应答的键面从来没有属主 —— 生产侧（security-engine 与 gateway
 * 两个宿主）内联写 {@code success}/{@code allowed}/{@code engine}/{@code expression}/{@code error}，
 * 消费侧（ai-engine 两支腿）各自内联读。其中 {@code ToolObligationApplier#rowAllowed}
 * 更是<b>自造了一套键面</b>（{@code allowed} → {@code pass} → {@code result} 三级别名）并且
 * <b>完全不读 {@code success}</b>。后果不是"读不到键"而是"读到的键含义被换掉"：
 * 引擎自己抛异常时写的是 {@code success=false}＋{@code allowed=false}，
 * 消费侧看到 {@code allowed=false} 就当成"策略判了这一行拒绝"⇒ 静默丢行、
 * 结果照样 200；反射腿同样把 {@code allowed=false} 折成 403 {@code GUARDRAIL_DENIED}。
 * 这与 <b>D-B4-41</b> 是同一个形状，只是发生在行过滤面而非 ABAC evaluate 面
 * （§4.7.2「不可用 ≠ 无权限」）。</p>
 *
 * <p><b>与 {@link AbacEvaluateResponseContract} 的分工</b>：那一份管
 * {@code policy-engine/evaluate} 的 envelope（顶层／嵌套 {@code result} 两形态、
 * {@code policyState} 四态、兜底来源标记）；本份管 {@code evaluate-filter} 的 envelope。
 * 两份<b>共用同一套裁决词汇与词表</b>——{@link Verdict} 直接引用，布尔键的字符串形态
 * 词表引用 {@link AbacEvaluateResponseContract} 的常量，禁另立第二套"看起来等价"的 vocabulary。</p>
 *
 * <p><b>本契约刻意不提供布尔 helper</b>：{@link AbacEvaluateResponseContract#isExplicitAllow}
 * 是 D-B4-41 的发生位置（对 DENY 与 UNEVALUATED 同值）。这里连便捷方法都不给，
 * 消费方必须对 {@link #verdictOf} 的三态分别处置：ALLOW＝留行／放行，
 * DENY＝真实策略判定（丢行或 403），UNEVALUATED＝按不可用上报（503／FAIL_CLOSED）。
 * 读形态门禁 {@code RowFilterVerdictSingleSourceTest} 同时钉住"不得新增布尔结论腿"。</p>
 *
 * <p><b>不设别名键</b>：收口前消费侧读的 {@code pass}／{@code result} 两个别名，
 * 实测在两个生产宿主写面上<b>零写点</b>（全仓 main 面 {@code put("allowed")} 4 处、
 * {@code put("pass")}／{@code put("result")} 于本 envelope 0 处）。保留别名只会复制
 * D-B4-39 的镜像缺陷（"只认历史别名的读侧永远读到 null"）。因此本契约只认现行键面，
 * 别名读一律 {@link Verdict#UNEVALUATED}。</p>
 *
 * <p><b>写面外形不变</b>：{@link #evaluationFailed(String)} 仍然写出
 * {@code allowed=false}（API 只增不改：既有应答的字段集合与取值形态保持），
 * 但读侧凭 {@code success=false} 判 {@link Verdict#UNEVALUATED} —— 折叠的消除发生在读数上，
 * 不靠改响应外形。</p>
 */
public final class RowFilterVerdictContract {

    private RowFilterVerdictContract() {
    }

    // ── 应答键面（单点定义；消费侧裸读、生产侧裸写都由静态门禁钉住） ──

    /** 引擎是否完成了本次判定 —— 区分"策略判了拒绝"与"引擎没答"的唯一依据。 */
    public static final String KEY_SUCCESS = "success";

    /** 行级放行布尔键（本 envelope 的裁决键；与 ABAC 的 {@code allow} 不是同一个键）。 */
    public static final String KEY_ALLOWED = "allowed";

    /** 引擎故障说明键（存在即 {@link Verdict#UNEVALUATED}）。 */
    public static final String KEY_ERROR = "error";

    /** 判定引擎标记键（仅供日志／审计，不参与读数）。 */
    public static final String KEY_ENGINE = "engine";

    /** 回显表达式键（仅供日志／审计，不参与读数）。 */
    public static final String KEY_EXPRESSION = "expression";

    // ── 请求键面（生产侧读请求、传输腿写请求，同一套常量） ──

    // {@code expression} 与应答回显同名，复用 {@link #KEY_EXPRESSION}。
    /** 请求体行数据键。 */
    public static final String REQ_KEY_ROW_DATA = "rowData";
    /** 请求体用户角色键。 */
    public static final String REQ_KEY_USER_ROLE = "userRole";
    /** {@link #REQ_KEY_USER_ROLE} 缺省角色（无主体时按最低权限判定，禁缺省成 admin）。 */
    public static final String VALUE_DEFAULT_ROLE = "viewer";

    // ── 引擎标记取值（写面单源；两个宿主都写这两个串） ──

    /** 正常判定路径的引擎标记。 */
    public static final String VALUE_ENGINE_RHAI = "Rust rhai v1.x (via Java expression parser)";
    /** 兜底失败路径的引擎标记 —— 与 {@link #KEY_SUCCESS} 为假同时出现。 */
    public static final String VALUE_ENGINE_FALLBACK_FAILED = "Java-fallback-failed";

    /**
     * 组装"引擎完成判定"的应答段：{@code success=true}＋裁决＋引擎标记＋表达式回显。
     *
     * @param allowed    行级放行结论（真实谓词结果，{@code false} 即策略拒绝）
     * @param expression 被判定的过滤表达式（回显用，不参与读数）
     */
    public static Map<String, Object> evaluated(boolean allowed, String expression) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_SUCCESS, true);
        data.put(KEY_ALLOWED, allowed);
        data.put(KEY_ENGINE, VALUE_ENGINE_RHAI);
        data.put(KEY_EXPRESSION, expression);
        return data;
    }

    /**
     * 组装"引擎没能完成判定"的应答段。
     *
     * <p>仍然写 {@code allowed=false} 以保持既有外形（消费方升级前的旧版本也不会读到 {@code null}
     * 而抛 NPE），但 {@code success=false}＋{@link #KEY_ERROR} 让 {@link #verdictOf} 归到
     * {@link Verdict#UNEVALUATED}：这是"没答"，不是"答了拒绝"。</p>
     */
    public static Map<String, Object> evaluationFailed(String error) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_SUCCESS, false);
        data.put(KEY_ALLOWED, false);
        data.put(KEY_ERROR, error);
        data.put(KEY_ENGINE, VALUE_ENGINE_FALLBACK_FAILED);
        return data;
    }

    /**
     * 读行过滤裁决三态 —— 所有 {@code evaluate-filter} 应答读侧唯一允许的读数入口。
     *
     * <p>判定顺序固定：
     * <ol>
     *   <li>null／非 Map ⇒ {@link Verdict#UNEVALUATED}；</li>
     *   <li>带 {@code error} 键 ⇒ UNEVALUATED（引擎自己报了故障）；</li>
     *   <li>{@code success} 不为真 ⇒ UNEVALUATED（收口前消费侧完全不看这个键，
     *       故障形态在此与真拒绝分离 —— D-B4-42 的正身）；</li>
     *   <li>{@code success} 键缺失 ⇒ UNEVALUATED（形态不属于本契约，禁猜）；</li>
     *   <li>布尔键 {@code allowed}：布尔 ⇒ ALLOW／DENY；字符串 ⇒ 共用 ABAC 词表，
     *       命中即 ALLOW／DENY，未命中 ⇒ UNEVALUATED；</li>
     *   <li>其余（缺失／数值／其它类型）⇒ UNEVALUATED，<b>禁默认放行也禁默认拒绝冒充策略判定</b>。</li>
     * </ol>
     *
     * @param dataSegment 应答的 {@code data} 段原值（可为 {@code null}）
     */
    public static Verdict verdictOf(Object dataSegment) {
        if (!(dataSegment instanceof Map<?, ?> outer)) {
            return Verdict.UNEVALUATED;
        }
        Map<?, ?> data = outer;
        if (data.containsKey(KEY_ERROR)) {
            return Verdict.UNEVALUATED;
        }
        Object success = data.get(KEY_SUCCESS);
        if (success == null) {
            return Verdict.UNEVALUATED;
        }
        if (!truthy(success)) {
            return Verdict.UNEVALUATED;
        }
        Object flag = data.get(KEY_ALLOWED);
        if (flag instanceof Boolean b) {
            return b ? Verdict.ALLOW : Verdict.DENY;
        }
        if (flag instanceof String s) {
            String v = normalized(s);
            if (AbacEvaluateResponseContract.TOKEN_TRUE.equals(v)
                    || AbacEvaluateResponseContract.TOKEN_ALLOW.equals(v)
                    || AbacEvaluateResponseContract.TOKEN_PERMIT.equals(v)) {
                return Verdict.ALLOW;
            }
            if (AbacEvaluateResponseContract.TOKEN_FALSE.equals(v)
                    || AbacEvaluateResponseContract.TOKEN_DENY.equals(v)
                    || AbacEvaluateResponseContract.TOKEN_DENIED.equals(v)) {
                return Verdict.DENY;
            }
        }
        return Verdict.UNEVALUATED;
    }

    /**
     * 不可用形态的诊断摘要 —— 供消费方组装 fail-closed 理由串。
     *
     * <p>存在的理由就是纪律本身：消费方要打印"引擎为什么没答"时，必须调本方法而不是
     * 再去 {@code .get("success")} 摸一次键（那正是本批消除的形态）。</p>
     */
    public static String diagnose(Object dataSegment) {
        if (!(dataSegment instanceof Map<?, ?> data)) {
            return "响应缺失或非对象形态（type=" + (dataSegment == null ? "null"
                    : dataSegment.getClass().getSimpleName()) + ")";
        }
        StringBuilder sb = new StringBuilder("evaluate-filter 应答无可识别裁决: ");
        sb.append(KEY_SUCCESS).append('=').append(data.get(KEY_SUCCESS));
        sb.append(", ").append(KEY_ALLOWED).append('=').append(data.get(KEY_ALLOWED));
        if (data.containsKey(KEY_ERROR)) {
            sb.append(", ").append(KEY_ERROR).append('=').append(data.get(KEY_ERROR));
        }
        if (data.containsKey(KEY_ENGINE)) {
            sb.append(", ").append(KEY_ENGINE).append('=').append(data.get(KEY_ENGINE));
        }
        return sb.toString();
    }

    /** {@code success} 的合法真值形态：布尔真，或词表 {@code true} 的字符串形态。 */
    private static boolean truthy(Object success) {
        if (success instanceof Boolean b) {
            return b;
        }
        return success instanceof String s
                && AbacEvaluateResponseContract.TOKEN_TRUE.equals(normalized(s));
    }

    /** 大小写归一 + trim（与 {@link AbacEvaluateResponseContract} 的读数口径一致）。 */
    private static String normalized(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
