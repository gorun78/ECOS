package com.chinacreator.gzcm.workspace.security;

import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 导出通道裁决结果 —— 三通道唯一裁决入口（{@code POST /api/v1/security/decision}，
 * {@code purpose=export}）在 workspace 侧的消费形态（#142/B4-3b）。
 *
 * <p><b>为什么需要这个类型</b>：详设-01 C.1 [3] 把「绕过其中一条通道在架构上不可表达」
 * 定为结构性目标，而导出件此前用<b>硬编码常量</b>自称权限范围
 * （{@code "rls+cls:forecast_run(same-pipeline-as-query, SEC-01)"}），
 * 审计时无法反查"这份 CSV 到底是在哪次裁决、哪些策略下产出的"（差距文档 D-B4-10/D-B4-11）。
 * 本类只承载<b>裁决证据</b>：decisionId、命中的 RLS/CLS 策略名、水印义务，
 * 字段名与 security-engine 侧 {@code DecisionBundle} 一一对应，
 * 由 {@code ExportDecisionContractTest} 做跨模块契约钉。</p>
 *
 * <p><b>无证据不冒充</b>：bundle 未携带 {@code policyRefs}（security 侧旧版本/字段漂移）时，
 * {@link #permissionScope} 返回 {@link #SCOPE_NOT_WIRED} 而非任何看似合理的默认值；
 * OPA 未签发水印义务时 {@link #watermark} 返回 {@link #WATERMARK_NOT_ISSUED}。
 * 两个标记都是<b>缺陷在册的可见痕迹</b>，导出件里出现它们即说明收口未完成，
 * 禁后续批次把它们替换成常量。</p>
 *
 * <p><b>#142/B4-3d-7 收口（差距 D-B4-24b）——分态取面订正</b>：B4-3d-5 把键名收成了单源常量，
 * 但本类当时仍从 bundle 的<b>表达面</b> {@code predicateTemplate} 分态。这不是中性的写法差异：
 * 表达面不含 {@code RlsPredicateRenderer} 的拒绝归一，「一条策略都没命中」在表达面是
 * {@code 1=1}（{@code mergePolicies} 的空段兜底文本），在执行契约面才是
 * {@code 1=0} + {@code RLS_NO_POLICY_MATCHED}；上下文未解析、词法被拒同样只在执行面可见。
 * 于是"读表达面分态"必须靠 {@code policyRefs} 是否为空去反推裁决结论（键漂移即静默放行），
 * 并且把「应答根本没发执行契约」这一漂移形态读成"空谓词⇒放行"。
 * 现读数改经 {@link RlsExecutionContract#read}，四态由单源一次给全，本类不再自选键子集；
 * 后果是更严（缺片段/需绑参一律拒绝），不是更宽。</p>
 */
public final class ExportDecision {

    /** 范围证据缺失标记（bundle 无 {@code rls.policyRefs} ⇒ 无法反查命中策略；D-B4-10 在册痕迹）。 */
    public static final String SCOPE_NOT_WIRED = "scope=NOT_WIRED(裁决未返回 policyRefs，禁以常量冒充范围)";

    /**
     * 水印义务未签发标记（OPA obligations 无 watermark 项）。
     *
     * <p>B4-3c 后 {@code data_access.rego} 已落仓（{@code ecos-docker/opa-policies/data_access.rego}，
     * 导出放行时签发 watermark 义务），因此本标记不再表示"策略文件不存在"（那是 D-B4-12 的旧根因），
     * 而表示<b>本次应答的义务集里没有 watermark 项</b> —— 常见成因是护栏未被评估
     * （{@code POLICY_UNDEFINED}＝dev OPA 未挂 bundle），该态在 {@code ScenarioReadGuard}
     * 已先转 503，正常走不到这里；走到即说明义务漂移，须留痕而非冒充"已加水印"。</p>
     */
    public static final String WATERMARK_NOT_ISSUED =
            "NOT_ISSUED(OPA 未签发 watermark 义务：data_access 应答义务集缺项，见差距文档 D-B4-12)";

    private final String entityCode;
    private final String decisionId;
    private final String permissionScope;
    private final String watermark;
    private final RlsExecutionContract.Execution rlsExecution;
    private final boolean opaAllowed;
    private final String opaPolicyState;
    private final String opaState;
    private final List<String> rlsPolicyRefs;
    private final List<String> clsPolicyRefs;

    private ExportDecision(String entityCode, String decisionId, String permissionScope, String watermark,
                           RlsExecutionContract.Execution rlsExecution, boolean opaAllowed,
                           String opaPolicyState, String opaState,
                           List<String> rlsPolicyRefs, List<String> clsPolicyRefs) {
        this.entityCode = entityCode;
        this.decisionId = decisionId;
        this.permissionScope = permissionScope;
        this.watermark = watermark;
        this.rlsExecution = rlsExecution;
        this.opaAllowed = opaAllowed;
        this.opaPolicyState = opaPolicyState;
        this.opaState = opaState;
        this.rlsPolicyRefs = rlsPolicyRefs;
        this.clsPolicyRefs = clsPolicyRefs;
    }

    /**
     * 从 security-engine 的 REST 应答（{@code ApiResponse.data = DecisionBundle.toMap()}）构造。
     *
     * @param bundle {@link WorkspaceSecurityEngineClient#decide} 的返回；null 由调用方按
     *               fail-closed 处理（本工厂不接受 null，禁把"没有裁决"渲染成"裁决通过"）
     */
    public static ExportDecision fromBundle(String entityCode, Map<String, Object> bundle) {
        if (bundle == null) {
            throw new IllegalArgumentException("ExportDecision.fromBundle: bundle 不能为 null（无裁决证据须走拒绝分支）");
        }
        Map<String, Object> rls = asMap(bundle.get("rls"));
        Map<String, Object> columns = asMap(bundle.get("columns"));
        Map<String, Object> opa = asMap(bundle.get("opa"));
        String decisionId = bundle.get("decisionId") == null ? null : String.valueOf(bundle.get("decisionId"));

        List<String> rlsRefs = names(rls.get("policyRefs"));
        List<String> clsRefs = names(columns.get("policyRefs"));
        // 键名与不变量单源（#142/B4-3d-5，差距 D-B4-24）+ 分态取面单源
        // （#142/B4-3d-7，差距 D-B4-24b）：bundle 的 rls 段同时带表达面与执行契约面，
        // 本类只经 RlsExecutionContract.read 读执行契约面（四态 + 缺片段/arity/null 参数/
        // :name 残留四道不变量都在单源一次做完），禁再按 predicateTemplate 自选键分态。
        RlsExecutionContract.Execution rlsExecution =
                RlsExecutionContract.read(bundle.get("rls"), "entity=" + entityCode);
        // B4-3c：护栏态串优先（policyState 是「策略拒绝 / 护栏没说话」分态的唯一依据，
        // 词表单源在 runtime 底座 GuardrailDecisionContract，禁本类内联比对字符串）
        String policyState = GuardrailDecisionContract.stateOf(opa);
        boolean opaAllow = GuardrailDecisionContract.STATE_ALLOW.equals(policyState)
                || (policyState == null && Boolean.TRUE.equals(opa.get(GuardrailDecisionContract.KEY_ALLOW)));
        String opaState = opaState(opa, opaAllow, policyState);

        // 范围串带 rlsState：恒真（不限制行）与非平凡谓词（已放行但被载体挡住）在审计上
        // 必须能分开读，只给片段原文会让 "(1=1)" 与需绑参的片段在反查时同形。
        String scope = rlsRefs.isEmpty() && clsRefs.isEmpty()
                ? SCOPE_NOT_WIRED
                : "decision=" + decisionId + " purpose=export"
                        + " rls=[" + (rlsRefs.isEmpty() ? "NONE" : String.join("+", rlsRefs)) + "]"
                        + " cls=[" + (clsRefs.isEmpty() ? "NONE" : String.join("+", clsRefs)) + "]"
                        + " rlsState=" + rlsExecution.state()
                        + " predicate=" + rlsExecution.clause()
                        + " opa=" + opaState;

        return new ExportDecision(entityCode, decisionId, scope, watermarkOf(opa), rlsExecution,
                opaAllow, policyState, opaState, rlsRefs, clsRefs);
    }

    /**
     * OPA 护栏态串（范围证据的组成部分，禁只写 allow/deny 把"未咨询"和"被拒绝"混成一态）。
     *
     * <p>B4-3c 起优先呈现 {@code policyState} 四态（词表单源
     * {@link GuardrailDecisionContract}）：</p>
     * <ul>
     *   <li>{@code EVALUATED_DENY:〈deniedReason〉} —— 真实策略拒绝，可映射 403；</li>
     *   <li>{@code POLICY_UNDEFINED} / {@code OPA_UNAVAILABLE} —— 护栏没说话，映射 503；</li>
     *   <li>ALLOW 态渲染成 {@code allow}（范围串是给审计读的，不必重复全名）。</li>
     * </ul>
     *
     * <p>{@code policyState} 键缺失＝security 侧旧形态/键漂移，此时退回旧的推断口径
     * （{@code allow} / {@code deny:〈deniedReason〉} / {@code ABSENT}），并由
     * {@link #guardrailUnevaluated()} 一律按"未评估"处理（缺状态＝无法证明护栏说过话，
     * 宁 503 不 403、更不放行）。</p>
     */
    private static String opaState(Map<String, Object> opa, boolean allow, String policyState) {
        if (opa.isEmpty()) return "ABSENT";
        if (policyState != null) {
            if (GuardrailDecisionContract.STATE_ALLOW.equals(policyState)) return "allow";
            if (GuardrailDecisionContract.STATE_DENY.equals(policyState)) {
                String reason = deniedReasonOf(opa);
                return reason == null ? "deny" : "deny:" + reason;
            }
            return policyState;
        }
        if (allow) return "allow";
        String reason = deniedReasonOf(opa);
        return reason == null ? "deny" : "deny:" + reason;
    }

    /** obligations 首条 {@code deniedReason}（无则 null）。 */
    private static String deniedReasonOf(Map<String, Object> opa) {
        Object obligations = opa.get(GuardrailDecisionContract.KEY_OBLIGATIONS);
        if (obligations instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m
                        && m.get(GuardrailDecisionContract.KEY_DENIED_REASON) != null) {
                    return String.valueOf(m.get(GuardrailDecisionContract.KEY_DENIED_REASON));
                }
            }
        }
        return null;
    }

    /**
     * OPA 护栏态串（范围证据的组成部分，禁只写 allow/deny 把"未咨询"和"被拒绝"混成一态）。
     *
     * <ul>
     *   <li>{@code allow} —— 护栏明确放行；</li>
     *   <li>{@code deny:<deniedReason>} —— 明确拒绝，附拒绝原因（真实策略拒绝 /
     *       {@code OPA_ERROR_FAIL_CLOSED}＝护栏源根本没被咨询成功）；</li>
     *   <li>{@code ABSENT} —— bundle 无 opa 段（裁决形态漂移）。</li>
     * </ul>
     */
    private static String opaState(Map<String, Object> opa, boolean allow) {
        if (opa.isEmpty()) return "ABSENT";
        if (allow) return "allow";
        Object obligations = opa.get(GuardrailDecisionContract.KEY_OBLIGATIONS);
        if (obligations instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m && m.get(GuardrailDecisionContract.KEY_DENIED_REASON) != null) {
                    return "deny:" + m.get(GuardrailDecisionContract.KEY_DENIED_REASON);
                }
            }
        }
        return "deny";
    }

    /** OPA obligations 中的水印项（三种书写形态兼容；无 ⇒ {@link #WATERMARK_NOT_ISSUED}）。 */
    private static String watermarkOf(Map<String, Object> opa) {
        Object raw = opa.get(GuardrailDecisionContract.KEY_OBLIGATIONS);
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Object wm = m.get(GuardrailDecisionContract.OBLIGATION_WATERMARK);
                    if (wm != null && !String.valueOf(wm).isBlank()) return String.valueOf(wm);
                    if (GuardrailDecisionContract.OBLIGATION_WATERMARK
                            .equals(String.valueOf(m.get(GuardrailDecisionContract.KEY_OBLIGATION)))) {
                        Object value = m.get("value");
                        if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
                    }
                } else if (item instanceof String s
                        && s.toLowerCase().startsWith(GuardrailDecisionContract.OBLIGATION_WATERMARK)) {
                    return s;
                }
            }
        }
        return WATERMARK_NOT_ISSUED;
    }

    public String entityCode() { return entityCode; }
    public String decisionId() { return decisionId; }
    public String permissionScope() { return permissionScope; }
    public String watermark() { return watermark; }
    /** RLS 执行契约读数（四态载体；导出咽喉的分支判据全部取自这里，禁另立口径）。 */
    public RlsExecutionContract.Execution rlsExecution() { return rlsExecution; }
    /** 执行面片段原文（恒真为 {@code (1=1)} 一类形态、拒绝为 {@code 1=0}；只作证据呈现）。 */
    public String rlsClause() { return rlsExecution.clause(); }
    /** 裁决状态（{@link RlsExecutionContract.State} 四态之一）。 */
    public RlsExecutionContract.State rlsState() { return rlsExecution.state(); }
    /** 空谓词默认拒绝开关（应答缺键时单源按 {@code true} 保守处理）。 */
    public boolean rlsDenyIfEmpty() { return rlsExecution.denyIfEmpty(); }
    public boolean opaAllowed() { return opaAllowed; }
    public String opaState() { return opaState; }
    /** OPA 护栏态原始串（{@link GuardrailDecisionContract} 四值之一；缺键＝漂移，返回 null）。 */
    public String opaPolicyState() { return opaPolicyState; }
    public List<String> rlsPolicyRefs() { return rlsPolicyRefs; }
    public List<String> clsPolicyRefs() { return clsPolicyRefs; }

    /**
     * 护栏是否<b>没有给出</b>策略裁决（B4-3c 起为导出咽喉的 503 硬门判据）。
     *
     * <p>命中态：opa 段缺失（{@code ABSENT}）、{@code POLICY_UNDEFINED}（OPA 可达但策略未加载）、
     * {@code OPA_UNAVAILABLE}（不可达/超时/非 2xx/不可解析），以及 {@code policyState} 键漂移
     * （旧形态 —— 无法证明护栏说过话，宁 503 不 403）。</p>
     *
     * <p>存在意义（差距文档 D-B4-14/D-B4-18/D-B4-20/D-B4-21）：旧形态只有 {@code allow=false}，
     * 把"护栏没接上"伪装成"策略拒绝"。B4-3c 补齐名合规的 {@code data_access} rego 后，
     * 本判据即硬门开关：未评估态一律 503 {@code ECOS-SEC-501}（可告警、可重试），
     * 真拒绝才 403。</p>
     */
    public boolean guardrailUnevaluated() {
        if (opaPolicyState() == null) return true;
        return GuardrailDecisionContract.isUnevaluated(opaPolicyState());
    }

    /** 护栏是否给出<b>真实策略拒绝</b>（仅 {@code EVALUATED_DENY} 态 ⇒ 403）。 */
    public boolean guardrailDenied() {
        return GuardrailDecisionContract.STATE_DENY.equals(opaPolicyState);
    }

    /**
     * 是否处于「无命中策略」态（导出通道叠加 {@code denyIfEmpty} 后据此 DENY）。
     *
     * <p>两路取「或」，宁严不宽：执行契约的 {@code noPolicyMatched} 是生产者给的结论，
     * {@code policyRefs} 读空同时覆盖"应答没发出处键"这一漂移形态（读不到出处不能当成有策略）。</p>
     *
     * <p><b>#142/B4-3d-7 定位订正</b>：本判据此前是「无命中策略」的<b>唯一</b>依据——
     * 因为分态读的是表达面，而表达面对"零命中"给的兜底文本恰是 {@code 1=1}（看起来像放行）。
     * 改读执行契约面后，零命中由生产者渲染成 {@code 1=0} 并已落 {@link #rlsDenied()}，
     * 本判据降为第二道：专门挡住"发恒真片段却自报未命中"这类异常配对，以及出处键漂移形态。</p>
     */
    public boolean noPolicyMatched() {
        return rlsExecution.noPolicyMatched() || rlsPolicyRefs.isEmpty();
    }

    /** 拒绝态（生产者 {@code 1=0}／零命中／应答未发执行契约／四道不变量不过 ⇒ 403）。 */
    public boolean rlsDenied() {
        return rlsExecution.denied();
    }

    /** 恒真态（{@code 1=1}＝裁决明确"不限制行"）：导出通道唯一可放行的 RLS 形态。 */
    public boolean rlsAllowAll() {
        return rlsExecution.allowAll();
    }

    /**
     * 非平凡谓词态：裁决要过滤行，而导出件载体没有参数绑定通道。
     *
     * <p>含 {@code ?} 的片段无参可绑、裸文本片段无法证明与客户端值无关，
     * 两者一律按"不可下推"拒绝（D-B4-13 同源：宁 DENY 也不"全量捞回内存再滤"）。
     * 单源的 {@code rejectForUnbindableChannel} 只把<b>需绑参</b>的片段转拒绝、
     * 允许裸文本原样下推——那是响应侧过滤通道的口径，导出通道的承诺更严，
     * 故此处按状态而非按"是否需绑参"分流，禁照搬。</p>
     */
    public boolean rlsFiltered() {
        return rlsExecution.filtered();
    }

    /** 不可用态（{@code security} 进程不可达）。导出通道正常拿不到：传输层由客户端单点转 503。 */
    public boolean rlsUnavailable() {
        return rlsExecution.unavailable();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Collections.emptyMap();
    }

    private static List<String> names(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                Object name = m.get("name") != null ? m.get("name") : m.get("policy_name");
                if (name != null && !String.valueOf(name).isBlank()) out.add(String.valueOf(name));
            } else if (o != null && !String.valueOf(o).isBlank()) {
                out.add(String.valueOf(o));
            }
        }
        return List.copyOf(out);
    }
}
