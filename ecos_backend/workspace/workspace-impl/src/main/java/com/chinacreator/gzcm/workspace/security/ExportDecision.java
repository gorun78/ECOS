package com.chinacreator.gzcm.workspace.security;

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
 */
public final class ExportDecision {

    /** 范围证据缺失标记（bundle 无 {@code rls.policyRefs} ⇒ 无法反查命中策略；D-B4-10 在册痕迹）。 */
    public static final String SCOPE_NOT_WIRED = "scope=NOT_WIRED(裁决未返回 policyRefs，禁以常量冒充范围)";

    /** 水印义务未签发标记（OPA obligations 无 watermark 项；D-B4-12 在册痕迹）。 */
    public static final String WATERMARK_NOT_ISSUED =
            "NOT_ISSUED(OPA 未签发 watermark 义务：data.access 策略缺失，见差距文档 D-B4-12)";

    /** bundle.rls.predicateTemplate 缺段时的取值（空串=未给谓词）。 */
    private static final String NO_TEMPLATE = "";

    private final String entityCode;
    private final String decisionId;
    private final String permissionScope;
    private final String watermark;
    private final String rlsPredicateTemplate;
    private final boolean rlsDenyIfEmpty;
    private final boolean opaAllowed;
    private final String opaState;
    private final List<String> rlsPolicyRefs;
    private final List<String> clsPolicyRefs;

    private ExportDecision(String entityCode, String decisionId, String permissionScope, String watermark,
                           String rlsPredicateTemplate, boolean rlsDenyIfEmpty, boolean opaAllowed,
                           String opaState, List<String> rlsPolicyRefs, List<String> clsPolicyRefs) {
        this.entityCode = entityCode;
        this.decisionId = decisionId;
        this.permissionScope = permissionScope;
        this.watermark = watermark;
        this.rlsPredicateTemplate = rlsPredicateTemplate;
        this.rlsDenyIfEmpty = rlsDenyIfEmpty;
        this.opaAllowed = opaAllowed;
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
        String template = rls.get("predicateTemplate") == null ? NO_TEMPLATE
                : String.valueOf(rls.get("predicateTemplate"));
        // denyIfEmpty 缺省按 true：security 侧 RlsPredicate 恒置 true，字段缺失只可能是漂移，
        // 漂移时宁可拒绝也不放行（与 namesOf 的"读不到出处不当成放行"同一口径）
        boolean denyIfEmpty = rls.get("denyIfEmpty") == null
                || Boolean.TRUE.equals(rls.get("denyIfEmpty"));
        boolean opaAllow = Boolean.TRUE.equals(opa.get("allow"));
        String opaState = opaState(opa, opaAllow);

        String scope = rlsRefs.isEmpty() && clsRefs.isEmpty()
                ? SCOPE_NOT_WIRED
                : "decision=" + decisionId + " purpose=export"
                        + " rls=[" + (rlsRefs.isEmpty() ? "NONE" : String.join("+", rlsRefs)) + "]"
                        + " cls=[" + (clsRefs.isEmpty() ? "NONE" : String.join("+", clsRefs)) + "]"
                        + " predicate=" + template
                        + " opa=" + opaState;

        return new ExportDecision(entityCode, decisionId, scope, watermarkOf(opa), template,
                denyIfEmpty, opaAllow, opaState, rlsRefs, clsRefs);
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
        Object obligations = opa.get("obligations");
        if (obligations instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m && m.get("deniedReason") != null) {
                    return "deny:" + m.get("deniedReason");
                }
            }
        }
        return "deny";
    }

    /** OPA obligations 中的水印项（三种书写形态兼容；无 ⇒ {@link #WATERMARK_NOT_ISSUED}）。 */
    private static String watermarkOf(Map<String, Object> opa) {
        Object raw = opa.get("obligations");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Object wm = m.get("watermark");
                    if (wm != null && !String.valueOf(wm).isBlank()) return String.valueOf(wm);
                    if ("watermark".equals(String.valueOf(m.get("obligation")))) {
                        Object value = m.get("value");
                        if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
                    }
                } else if (item instanceof String s && s.toLowerCase().startsWith("watermark")) {
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
    public String rlsPredicateTemplate() { return rlsPredicateTemplate; }
    public boolean rlsDenyIfEmpty() { return rlsDenyIfEmpty; }
    public boolean opaAllowed() { return opaAllowed; }
    public String opaState() { return opaState; }
    public List<String> rlsPolicyRefs() { return rlsPolicyRefs; }
    public List<String> clsPolicyRefs() { return clsPolicyRefs; }

    /**
     * 护栏源是否<b>根本没被成功咨询</b>（bundle opa 段缺失，或拒绝原因取自
     * fail-close 兜底态）。
     *
     * <p>存在意义（差距文档 D-B4-14/D-B4-18）：{@code SecurityDecisionService.safeOpa}
     * 用策略名 {@code "data.access"} 调 {@code OpaPolicyService.evaluate}，而该服务的
     * {@code SAFE_NAME} 正则不允许点号 ⇒ 每次都走异常兜底 {@code allow=false +
     * OPA_ERROR_FAIL_CLOSED}。把这种"护栏没接上"伪装成 403 策略拒绝（或据此把功能打死）
     * 都是误导：导出咽喉据此把护栏态<b>如实渲染进范围证据</b>，硬门开关等
     * B4-3c 补 {@code data.access} rego 后由配套用例打开。</p>
     */
    public boolean guardrailUnconsulted() {
        return "ABSENT".equals(opaState)
                || opaState.contains("OPA_ERROR_FAIL_CLOSED")
                || opaState.contains("OPA_UNAVAILABLE_FAIL_CLOSED");
    }

    /** 是否处于「无命中策略」态（导出通道叠加 {@code denyIfEmpty} 后据此 DENY）。 */
    public boolean noPolicyMatched() { return rlsPolicyRefs.isEmpty(); }

    /**
     * 谓词是否恒拒绝（{@code 1=0}）。
     *
     * <p>归一化必须剥掉 {@code mergePolicies} 给每个策略段加的括号与空白，
     * 否则 security 返回的 {@code "(1=0)"} 会被误判成"非平凡谓词"而走错分支。</p>
     */
    public boolean rlsPredicateIsDeny() {
        return "1=0".equals(normalize(rlsPredicateTemplate));
    }

    /** 谓词是否恒真（{@code 1=1} = 无行级限制；财务负责人行的 {@code rls-finance-all} 形态）。 */
    public boolean rlsPredicateIsAllowAll() {
        return "1=1".equals(normalize(rlsPredicateTemplate));
    }

    /** 是否未给谓词（空/缺失）——与「恒真」不同态：空谓词=裁决没说话，恒真=裁决明确说不限制。 */
    public boolean rlsPredicateAbsent() {
        return rlsPredicateTemplate == null || rlsPredicateTemplate.isBlank();
    }

    private static String normalize(String predicate) {
        if (predicate == null) return "";
        StringBuilder sb = new StringBuilder(predicate.length());
        for (int i = 0; i < predicate.length(); i++) {
            char c = predicate.charAt(i);
            if (c != '(' && c != ')' && !Character.isWhitespace(c)) sb.append(c);
        }
        return sb.toString();
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
