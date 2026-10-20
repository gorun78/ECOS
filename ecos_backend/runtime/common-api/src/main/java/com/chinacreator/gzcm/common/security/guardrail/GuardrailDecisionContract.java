package com.chinacreator.gzcm.common.security.guardrail;

import java.util.List;
import java.util.Map;

/**
 * 数据访问护栏（OPA）裁决的<b>跨引擎线契约单源</b>（#142/B4-3c）。
 *
 * <p><b>为什么放在 common-api</b>：策略名与护栏态串同时被三个消费方读取 ——
 * security-engine（生产侧写入）、ai-engine 工具裁决闸（DENY / FAIL_CLOSED 分态）、
 * workspace 导出咽喉（403 / 503 分态）。这三个模块互不依赖（ai-engine 与 workspace
 * 都不引 security-engine-impl），旧形态是各自内联字面量：
 * security 侧写 {@code "data.access"}（含点号，过不了 {@code OpaPolicyService}
 * 的名称白名单 ⇒ OPA 从未被咨询，差距文档 D-B4-14/D-B4-18），
 * ai 侧写 {@code "rbac"}（与页面/导出通道不同源 ⇒ 「三通道同源」在策略面上是假的，
 * D-B4-22），workspace 侧靠 {@code opaState.contains("OPA_ERROR_FAIL_CLOSED")}
 * 字符串反推状态。任何一处改名，其余两处静默失配。本类把这套词表收敛到
 * 三方都必然依赖的 runtime 底座（与既有的
 * {@code com.chinacreator.gzcm.common.security.anonymous.AnonymousEndpointRegistry}
 * 同一分层口径）。</p>
 *
 * <p>策略本体（rego 源）在 {@code ecos-docker/opa-policies/data_access.rego}；
 * 本类只定义"叫什么名、应答里哪个键、状态有哪四值、义务怎么读"，不含任何判定逻辑
 * （读数≠判定：{@link #readRateLimitDeclaration} 只把策略给的义务形态翻译成显式三态，
 * 放行/拒绝的结论永远来自 {@code allow} 段，不在本类产生）。</p>
 */
public final class GuardrailDecisionContract {

    private GuardrailDecisionContract() {
    }

    /**
     * 数据访问护栏策略名 —— 三通道唯一合法取值。
     *
     * <p>必须无点号：{@code OpaPolicyService.SAFE_NAME = ^[a-zA-Z0-9_-]{1,64}$}，
     * 非法名在 HTTP 之前抛 {@code IllegalArgumentException}，护栏看起来在拒绝、
     * 实则从未说话（D-B4-14/D-B4-18）。</p>
     */
    public static final String POLICY_DATA_ACCESS = "data_access";

    /** OPA 已咨询且明确放行。 */
    public static final String STATE_ALLOW = "EVALUATED_ALLOW";
    /** OPA 已咨询且明确拒绝（真实策略判定 ⇒ 403 / GUARDRAIL_DENIED）。 */
    public static final String STATE_DENY = "EVALUATED_DENY";
    /** OPA 可达但本策略未定义/未加载（应答缺 result 或 result 无 allow）⇒ 部署缺陷态，fail-closed。 */
    public static final String STATE_POLICY_UNDEFINED = "POLICY_UNDEFINED";
    /** OPA 不可达 / 超时 / 非 2xx / 应答不可解析 ⇒ 不可用态，fail-closed（禁伪装成策略拒绝）。 */
    public static final String STATE_UNAVAILABLE = "OPA_UNAVAILABLE";

    // ── 应答字段名（security → 消费方的 JSON 键，单点定义防键名漂移） ──

    /** 布尔裁决键。 */
    public static final String KEY_ALLOW = "allow";
    /** 护栏态键（四值之一；缺失＝旧形态漂移，消费方按不可识别处理）。 */
    public static final String KEY_POLICY_STATE = "policyState";
    /** 义务集键（deniedReason / mask / watermark / rateLimit 四类，详设 C.2.5 ③）。 */
    public static final String KEY_OBLIGATIONS = "obligations";
    /** 被查文档引用（{@code ecos.<policy>}）。 */
    public static final String KEY_POLICY_REF = "policyRef";
    /** 拒绝原因键（义务项内）。 */
    public static final String KEY_DENIED_REASON = "deniedReason";
    /** 义务类型键（义务项内）。 */
    public static final String KEY_OBLIGATION = "obligation";

    // ── 义务词表（详设 C.2.5 ③ 四类；义务项 {@code obligation} 的取值面） ──
    //
    // 与策略源 {@code ecos-docker/opa-policies/data_access.rego} 的
    // {@code deny/mask/watermark/rate_obligations} 四组规则逐字对齐，
    // 由 {@code GuardrailRateLimitVocabularyTest} 做 rego 交叉核对。
    // <b>#142/B4-3e-2（D-B4-31）起四类义务词全部收口</b>：deniedReason / mask /
    // watermark / rateLimit 及其项内键在生产侧（security-engine）与三侧消费方
    // （ai-engine 裁决闸、cognitive 复核腿、workspace 导出咽喉）一律引用本类常量，
    // 由 {@code GuardrailObligationVocabularySingleSourceTest} 静态门禁钉住
    // 「main 源码禁裸义务词字面量」（含一条能变红的反向 fixture）。

    /** 义务类型值：明确拒绝（项内带 {@link #KEY_DENIED_REASON}）。 */
    public static final String OBLIGATION_DENIED = "DENIED";
    /** 义务类型值：列脱敏（项内带 column/strategy）。 */
    public static final String OBLIGATION_MASK = "mask";
    /** 义务类型值：导出水印（项内带 watermark）。 */
    public static final String OBLIGATION_WATERMARK = "watermark";
    /**
     * 义务类型值：AI 工具调用限流。
     *
     * <p>策略侧只在 {@code purpose == "ai" && count(violation) == 0} 时签发，
     * 因此页面/导出通道永不含该义务 —— 消费方落在 AI 工具咽喉
     * （{@code ToolExecutorService} 阶段 2.5），不是传输层限流。</p>
     */
    public static final String OBLIGATION_RATE_LIMIT = "rateLimit";

    /** 限流键名（义务项内，值形如 {@code ai.tool.<subjectId>}）。 */
    public static final String KEY_RATE_LIMIT_KEY = "rateLimitKey";
    /**
     * 限流键前缀 —— 与策略侧 {@code sprintf("ai.tool.%s",[subject_id])} 逐字一致。
     *
     * <p>消费方按本前缀做键面校验：不匹配即<b>键面漂移</b>，属"护栏给的形态不合契约"，
     * 必须报 FAIL_CLOSED 而不是按自造键计数（后者会把拒绝态洗成放行态）。</p>
     */
    public static final String RATE_LIMIT_KEY_PREFIX = "ai.tool.";

    // ── 义务项内承载键（D-B4-31 收口批新增；与 rego 逐字对齐） ──

    /**
     * 水印文本键（{@code obligation == "watermark"} 的项内承载键）。
     *
     * <p>与 {@link #OBLIGATION_WATERMARK} <b>同值但不同职</b>：一个是义务类型取值，
     * 一个是该项里放水水印串本身的键。两者分开定义，读数点才不必靠位置推断。</p>
     */
    public static final String KEY_WATERMARK = "watermark";
    /** 脱敏列键（{@code obligation == "mask"} 的项内列名，rego {@code "column"}）。 */
    public static final String KEY_MASK_COLUMN = "column";
    /** 脱敏列键的驼峰外别名（旧书写 {@code "field"}，消费侧兼容读）。 */
    public static final String KEY_MASK_COLUMN_ALT = "field";
    /** 脱敏规则键（rego {@code "strategy"}）。 */
    public static final String KEY_MASK_STRATEGY = "strategy";
    /** 脱敏规则键的别名（旧书写 {@code "rule"}，消费侧兼容读）。 */
    public static final String KEY_MASK_RULE_ALT = "rule";
    /**
     * 义务集容器键的旧书写别名（{@code obligations.maskHints}）。
     *
     * <p>策略现形已改用 {@code obligation == "mask"} 的对象数组；本键仅存于消费侧
     * 的兼容读数（security 侧 {@code OpaPolicyService} 注释仍以此称呼 mask 义务），
     * 保留常量而非删除，是为了让「兼容哪一代书写」在单源上可见、可被门禁核对。</p>
     */
    public static final String KEY_MASK_HINTS = "maskHints";
    /**
     * 行过滤声明键（义务集里的行过滤书写，值＝RLS 表名/条件引用）。
     *
     * <p><b>当前 rego 不签发该义务</b>（{@code data_access.rego} 只有 deniedReason /
     * mask / watermark / rateLimit 四组）—— 本常量是给消费侧的兼容读数定名，
     * 不表示策略契约里存在第五类义务；把它当成已生效的义务会造出假绿。</p>
     */
    public static final String KEY_ROW_FILTER = "rowFilter";
    /** 行过滤声明键的蛇形别名（旧书写 {@code "row_filter"}，消费侧兼容读）。 */
    public static final String KEY_ROW_FILTER_ALT = "row_filter";
    /** 水印义务的别名承载键（旧书写 {@code {obligation:"watermark", value:"…"}}）。 */
    public static final String KEY_WATERMARK_VALUE_ALT = "value";

    // ── 状态判定（唯一实现点，禁各消费方各自字符串比对） ──

    /** 从裁决应答读护栏态串；缺键／空串返回 null（＝旧形态漂移，非某一态）。 */
    public static String stateOf(Map<String, Object> data) {
        if (data == null) return null;
        Object raw = data.get(KEY_POLICY_STATE);
        if (raw == null) return null;
        String s = String.valueOf(raw).trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * 「护栏没有给出策略裁决」＝ {@link #STATE_POLICY_UNDEFINED} 或 {@link #STATE_UNAVAILABLE}。
     *
     * <p>这两态必须报 503 / FAIL_CLOSED（可告警、可重试），禁折叠成 403 策略拒绝 ——
     * §4.7.2「不可用 ≠ 无权限」。</p>
     */
    public static boolean isUnevaluated(String state) {
        return STATE_POLICY_UNDEFINED.equals(state) || STATE_UNAVAILABLE.equals(state);
    }

    /** 「护栏已给出策略裁决」＝ ALLOW 或 DENY（仅此两态可映射 403 / 放行语义）。 */
    public static boolean isEvaluated(String state) {
        return STATE_ALLOW.equals(state) || STATE_DENY.equals(state);
    }

    // ── 义务读数（唯一读数点；消费方禁各自遍历义务数组比对字面量） ──

    /**
     * {@code rateLimit} 义务的声明形态读数。
     *
     * <p>必须区分三件事，否则 §4.7.2 的「策略没说」与「策略说了但形态不合」会被折叠：
     * <ol>
     *   <li>{@code declared=false} —— 义务集里没有 rateLimit 项（页面/导出通道恒如此）＝本层无义务；</li>
     *   <li>{@code declared=true, key=null/空} —— 策略签发了限流义务但键缺失＝形态不合契约；</li>
     *   <li>{@code declared=true, key="ai.tool.x"} —— 可用键。</li>
     * </ol>
     * 因此 {@link #key()} 只可能是 null 或原文，"有没有义务"一律看 {@link #declared()}。</p>
     */
    public record RateLimitDeclaration(boolean declared, String key) {

        /** 未声明限流义务（不是缺陷，也不是放行判定 —— 放行权在 allow 段）。 */
        public static RateLimitDeclaration undeclared() {
            return new RateLimitDeclaration(false, null);
        }
    }

    /**
     * 从应答的 {@code obligations} 段读限流义务声明，兼容策略侧的两种书写：
     * 对象数组（rego {@code obligations} 规则的标准形态）与单对象（简写）。
     *
     * <p>多条 rateLimit 项取<b>首条</b>并在遇到时即返回 —— 策略侧按主体只签发一条，
     * 出现多条属形态异常，由消费方的键面前缀校验兜住，不在本读数点做去重或合并。</p>
     */
    public static RateLimitDeclaration readRateLimitDeclaration(Object obligationsRaw) {
        if (obligationsRaw == null) return RateLimitDeclaration.undeclared();
        if (obligationsRaw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> item) {
                    RateLimitDeclaration d = declarationOfItem(asMap(item));
                    if (d.declared()) return d;
                }
            }
            return RateLimitDeclaration.undeclared();
        }
        if (obligationsRaw instanceof Map<?, ?> single) {
            return declarationOfItem(asMap(single));
        }
        return RateLimitDeclaration.undeclared();
    }

    /** 单个义务项的限流读数（非限流项＝未声明）。 */
    private static RateLimitDeclaration declarationOfItem(Map<String, Object> item) {
        if (!OBLIGATION_RATE_LIMIT.equals(item.get(KEY_OBLIGATION))) {
            return RateLimitDeclaration.undeclared();
        }
        Object raw = item.get(KEY_RATE_LIMIT_KEY);
        return new RateLimitDeclaration(true, raw == null ? null : String.valueOf(raw));
    }

    /**
     * 从义务集读<b>水印文本</b>（D-B4-31 收口：此前 workspace/导出侧各自遍历义务数组
     * 比对 {@code "watermark"} 字面量，键名一改即静默失去水印）。
     *
     * <p>兼容策略侧三种书写，取<b>首个非空</b>：
     * ①{@code {obligation:"watermark", watermark:"…"}}（rego 现形）；
     * ②{@code {watermark:"…"}} 或 {@code {obligation:"watermark", value:"…"}}（简写/别名承载键）；
     * ③{@code "watermark…"} 字符串项（一代前的字符串数组形态）。</p>
     *
     * <p>无义务时返回 {@code null} —— 消费方须原样落「未签发」标记，
     * 禁把「没有水印义务」渲染成「已加水印」。</p>
     */
    public static String readWatermark(Object obligationsRaw) {
        if (obligationsRaw instanceof List<?> list) {
            for (Object item : list) {
                String wm = watermarkOfItem(item);
                if (wm != null) return wm;
            }
            return null;
        }
        return watermarkOfItem(obligationsRaw);
    }

    /** 单个义务项的水印读数（非水印项／无承载值＝ null）。 */
    private static String watermarkOfItem(Object item) {
        if (item instanceof Map<?, ?> m) {
            Map<String, Object> mm = asMap(m);
            String direct = nonBlank(mm.get(KEY_WATERMARK));
            if (direct != null) return direct;
            if (OBLIGATION_WATERMARK.equals(String.valueOf(mm.get(KEY_OBLIGATION)))) {
                return nonBlank(mm.get(KEY_WATERMARK_VALUE_ALT));
            }
            return null;
        }
        if (item instanceof String s && s.toLowerCase(java.util.Locale.ROOT).startsWith(OBLIGATION_WATERMARK)) {
            return s;
        }
        return null;
    }

    /**
     * 从义务集读<b>拒绝原因</b>（{@code obligation == "DENIED"} 项的
     * {@link #KEY_DENIED_REASON}），兼容对象数组与单对象两种容器形态。
     *
     * <p>多条拒绝项取首个非空 —— 生产侧（{@code OpaPolicyService}）恒按单条签发，
     * 多条属形态异常，交由消费方按原文展示，不在读数点做合并。</p>
     */
    public static String readDeniedReason(Object obligationsRaw) {
        if (obligationsRaw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    String r = nonBlank(asMap(m).get(KEY_DENIED_REASON));
                    if (r != null) return r;
                }
            }
            return null;
        }
        if (obligationsRaw instanceof Map<?, ?> single) {
            return nonBlank(asMap(single).get(KEY_DENIED_REASON));
        }
        return null;
    }

    /** 义务容器读数：把应答的 {@code obligations} 段原样交给读数方法，禁各自键名遍历。 */
    public static Object obligationsOf(Map<String, Object> data) {
        return data == null ? null : data.get(KEY_OBLIGATIONS);
    }

    /** 空串／空白视同缺键（返回 null），避免"键在值空"被当成有义务。 */
    private static String nonBlank(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw);
        return s.isBlank() ? null : s;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }
}
