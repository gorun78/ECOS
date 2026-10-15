package com.chinacreator.gzcm.common.security.guardrail;

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
 * 本类只定义"叫什么名、应答里哪个键、状态有哪四值"，不含任何判定逻辑。</p>
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
}
