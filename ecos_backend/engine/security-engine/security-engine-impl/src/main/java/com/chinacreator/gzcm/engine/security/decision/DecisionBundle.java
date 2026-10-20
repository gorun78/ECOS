package com.chinacreator.gzcm.engine.security.decision;

import com.chinacreator.gzcm.common.security.cls.ColumnSurfaceContract;
import com.chinacreator.gzcm.common.security.decision.DecisionChannelContract;
import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 详细设计-01 C.1 [3] / D 章 — 三通道唯一裁决返回体（一次 {@code decide} 拿全）。
 *
 * <p>结构（D 章 {@code DecisionBundle}）：</p>
 * <pre>{@code
 * {
 *   rls:     {predicateTemplate, bindings[], combine, denyIfEmpty, policyRefs[],
 *             whereClause, whereParams, unresolvedBindings, noPolicyMatched, rlsDenyReason},
 *   columns: {mode: allow|deny, names: [...]（兼容别名，与 visibleColumns 同值）,
 *             visibleColumns: [...], blockedColumns: [...], policyRefs[],
 *             columnUniverseSource: REQUEST|NONE, columnUniverseResolved: bool,
 *             clsDenyReason?: CLS_POLICY_DENY|COLUMN_UNIVERSE_UNRESOLVED|CLS_SOURCE_ERROR|漂移串},
 *   masks:   [{column, strategy}],
 *   opa:     {allow, policyState, policyRef, obligations[]},
 *   decisionId, traceId
 * }
 * }</pre>
 *
 * <p>{@code columns} 段的三键证据面（#142/B4-3d-2 只增，差距 D-B4-16）：列面不能只用
 * {@code names} 是否为空来表达 —— 空集同时是"策略拒绝全部列""列全集没给""CLS 源抛错"
 * "键面漂移"四种事实的写法。消费方唯一合法读面是 {@link #columnsReading()} /
 * {@link #clsDenyReason()} / {@link #columnsUndecidable()}，禁再自行 {@code get("names")}
 * 判空（那是第二套真值，D-B4-25 的成因）。</p>
 *
 * <p>{@code opa.policyState}（#142/B4-3c 只增）＝ {@link GuardrailDecisionContract} 的四态之一，
 * 是消费方分「策略拒绝（403 / GUARDRAIL_DENIED）」与「护栏没说话（503 / FAIL_CLOSED）」
 * 两态的唯一依据。存在理由（差距文档 D-B4-14/D-B4-18/D-B4-20/D-B4-21）：只看 {@code allow}
 * 布尔时，策略未加载、OPA 不可达、真策略拒绝三态全部塌缩成 {@code false}，
 * 引擎缺陷被伪装成"用户无权限"。</p>
 *
 * <p>{@code policyRefs[]}（#142/B4-3b 只增）= 该源在本主体下<b>实际命中</b>的策略名清单，
 * 是导出件「权限范围」与「水印」字段的唯一合法出处；旧形态只有谓词没有出处，
 * 消费方只能写常量自证（差距文档 D-B4-10/D-B4-11）。</p>
 *
 * <p>结构性意义：把 RLS/CLS/mask/OPA 合并成<b>单入口</b>后，"绕过其中一条"在架构上
 * 不可表达（PRD-07 §1.5-3 "导出旁路 grep = 0" 的结构性实现，而非人工审查）。</p>
 */
public final class DecisionBundle {

    /** 通道语义（C.1 Purpose）。 */
    public enum Purpose { PAGE, EXPORT, AI }

    private final Map<String, Object> rls;
    private final Map<String, Object> columns;
    private final List<Map<String, Object>> masks;
    private final Map<String, Object> opa;
    private final String decisionId;
    private final String traceId;

    public DecisionBundle(Map<String, Object> rls, Map<String, Object> columns,
                          List<Map<String, Object>> masks, Map<String, Object> opa,
                          String decisionId, String traceId) {
        this.rls = rls;
        this.columns = columns;
        this.masks = masks == null ? new ArrayList<>() : masks;
        this.opa = opa;
        this.decisionId = decisionId;
        this.traceId = traceId;
    }

    public Map<String, Object> rls() { return rls; }
    public Map<String, Object> columns() { return columns; }
    public List<Map<String, Object>> masks() { return masks; }
    public Map<String, Object> opa() { return opa; }
    public String decisionId() { return decisionId; }
    public String traceId() { return traceId; }

    /**
     * CLS 是否拒绝全部列 —— 三态归一后的唯一判据（#142/B4-3d-2 语义订正，D-B4-16）。
     *
     * <p>旧形态读 {@code mode}/{@code names}：{@code "allow".equals(mode) && names 空}。
     * 这有两个洞：</p>
     * <ul>
     *   <li>{@code mode} 由生产者恒写 {@code "allow"}，判据实际只剩"names 是否为空"，
     *       而空集在线上同时表达<b>四种</b>事实（策略清空 / 列全集没给 / CLS 源抛错 / 键面漂移），
     *       把它们一律当成"该用户无列权限"就是缺陷本身；</li>
     *   <li>{@code names} 是列面的第三种叫法，与 {@code visibleColumns} 分裂 —— 键名两侧各写各的
     *       正是 D-B4-25 的根因。</li>
     * </ul>
     *
     * <p>现委托 {@link ColumnSurfaceContract#read(Object)}（与三消费者、生产者同一份读取面）：
     * 可读集不可用（漂移或空）即为拒绝。<b>注意本方法语义收紧</b>：
     * "键面漂移"过去会被当成"非 allow 模式所以没拒绝"，现在明确计入拒绝 ——
     * 读不到列面就不给列，fail-closed。区分具体理由请用 {@link #clsDenyReason()}，
     * 区分"未判定"与"策略拒绝"请用 {@link #columnsUndecidable()}。</p>
     */
    public boolean columnsDenied() {
        return !columnsReading().usable();
    }

    /**
     * 列面读取结果（唯一读取出口，#142/B4-3d-2 只增）。
     *
     * <p>{@code columns} 段整体为 {@code null} 时按"应答无 data 段"漂移态处理（{@code read(null)}），
     * 不再 NPE —— 消费方拿到的是可解释的拒绝而非 500 堆栈。</p>
     */
    public ColumnSurfaceContract.Reading columnsReading() {
        return ColumnSurfaceContract.read(columns);
    }

    /** 可读列集（漂移/DENY 时为空表；禁据此判"全放开"）。 */
    public List<String> columnsVisible() {
        return columnsReading().visible();
    }

    /** 被策略显式隐藏的列（展示与审计用，禁并入可读集）。 */
    public List<String> columnsBlocked() {
        return columnsReading().blocked();
    }

    /** 列面键面是否漂移（两键皆不可读）—— 与"策略拒绝全部列"分态。 */
    public boolean columnsSurfaceDrifted() {
        return columnsReading().drifted();
    }

    /** 列全集出处（{@code REQUEST} / {@code NONE}）；键缺失 ⇒ {@code null}（＝生产者未写证据，禁当 REQUEST）。 */
    public String columnUniverseSource() {
        Object raw = columns == null ? null : columns.get(ColumnSurfaceContract.KEY_COLUMN_UNIVERSE_SOURCE);
        return raw == null ? null : String.valueOf(raw);
    }

    /** 列全集是否解析得出来（仅在显式 {@code false} 时为假；键缺失时按未判定处理，见 {@link #columnsUndecidable()}）。 */
    public boolean columnUniverseResolved() {
        return columns != null
                && !ColumnSurfaceContract.universeUnresolved(columns)
                && !columnUniverseSourceIsNull();
    }

    /** 列面不可用的机读理由（可读集非空时为 {@code null}）。 */
    public String clsDenyReason() {
        if (columns == null) return ColumnSurfaceContract.DRIFT_NO_DATA;
        String reason = ColumnSurfaceContract.denyReasonOf(columns);
        if (reason != null) return reason;
        ColumnSurfaceContract.Reading reading = columnsReading();
        if (!reading.visible().isEmpty()) return null;
        if (reading.drifted()) return reading.reason();
        return ColumnSurfaceContract.DENY_POLICY;
    }

    /**
     * 列面是否被<b>策略</b>明确拒绝（{@code CLS_POLICY_DENY}）—— 消费方报 403 的唯一合法依据。
     *
     * <p>与 {@link #columnsUndecidable()} 互斥：后者属"裁决没给结论"，报 503/告警，
     * 禁伪装成"该主体无权限"（§4.5.4）。</p>
     */
    public boolean columnsPolicyDenied() {
        return ColumnSurfaceContract.DENY_POLICY.equals(clsDenyReason());
    }

    /**
     * 列面是否<b>未判定</b>（源异常 / 列全集未解析 / 键面漂移 / 证据段整体缺失）。
     *
     * <p>存在理由：三态此前在 bundle 上全是 {@code names=[]}，消费方只能一律 403，
     * 把引擎缺陷伪装成用户无权限（与 D-B4-14「OPA 从未被咨询」同一类）。
     * 门禁 {@code BundleColumnSurfaceTest} 钉住四态的可区分性。</p>
     */
    public boolean columnsUndecidable() {
        String reason = clsDenyReason();
        if (reason == null) return false;
        return !ColumnSurfaceContract.DENY_POLICY.equals(reason);
    }

    /** columns 段是否带 universe 证据键（缺失＝生产者未落证据，属形态漂移）。 */
    private boolean columnUniverseSourceIsNull() {
        return columnUniverseSource() == null;
    }

    // ── #142/B4-3b 只增：范围证据与水印义务 ──────────────────────────────
    //
    // 动机（差距文档 D-B4-10/D-B4-11）：导出件的「权限范围自证」此前是调用方硬编码的
    // 常量串（写什么就是什么），范围没有可核对出处。本组访问器把「命中了哪条策略」
    // 升为 bundle 的一等信息（{@code rls.policyRefs} / {@code columns.policyRefs}，
    // 由 {@link SecurityDecisionService} 从各源原始返回单点写入），消费方只能从
    // 这里取，不得再自行拼常量 —— 常量与真值的区别就是能不能被审计反查。

    /**
     * RLS 命中策略名（单源 = {@code RowLevelSecurityServiceImpl#apply} 的 policies 清单）。
     *
     * <p>#142/B4-3d-2 只加固不改性：{@code rls}/{@code columns} 段整体缺失时返回空表而非 NPE。
     * 理由与 {@link #columnsReading()} 同一口径 —— 证据段缺失必须是一条<b>可解释的拒绝</b>
     * （{@code clsDenyReason} 给出漂移码），不能变成 500 堆栈（§4.5.4 分态纪律）。</p>
     */
    public List<String> rlsPolicyRefs() {
        return rls == null ? List.of() : policyNames(rls.get("policyRefs"));
    }

    /** CLS 命中策略名（单源 = {@code ColumnLevelSecurityServiceImpl#getColumns} 的 policies 清单）。 */
    public List<String> clsPolicyRefs() {
        return columns == null ? List.of() : policyNames(columns.get("policyRefs"));
    }

    /** 合并谓词模板（{@code 1=1} = 无限制；{@code 1=0} = 拒绝；其余 = 需下推）。
     *
     * <p>#142/B4-3d-5（差距 D-B4-24）：键名取 {@link RlsExecutionContract} 常量——
     * bundle 的 rls 段与 {@code /rls/apply} 的 data 段同键同形，两侧共用一份键面。
     * 注意本方法是<b>表达面</b>读数；执行面（可直接绑参的片段）由消费端经
     * {@code RlsExecutionContract.read} 取得，禁以本值拼 SQL。</p>
     */
    public String rlsPredicateTemplate() {
        Object t = rls == null ? null : rls.get(RlsExecutionContract.KEY_PREDICATE_TEMPLATE);
        return t == null ? null : String.valueOf(t);
    }

    /** 无匹配策略时是否拒绝（{@code denyIfEmpty}，导出通道必须消费）。 */
    public boolean rlsDenyIfEmpty() {
        return rls != null && Boolean.TRUE.equals(rls.get(RlsExecutionContract.KEY_DENY_IF_EMPTY));
    }

    /**
     * 范围证据串（导出件「权限范围」字段的唯一合法出处，C.1 [3] auditSeed 同源）。
     *
     * <p>形如 {@code decision=<decisionId> purpose=export rls=[rls-finance-all] cls=[NONE]}：
     * 每个组成部分都可回查 {@code ecos_rls_policy.policy_name} / {@code ecos_cls_policy.policy_name}，
     * 因此「scope=finance-all」这类断言不再是自述。</p>
     */
    public String scopeEvidence(DecisionBundle.Purpose purpose) {
        // 通道词单源（#142/B4-3d-2，D-B4-27）：证据串里的 purpose 必须与线上入参词、
        // OPA 载荷词同一份词表。枚举名未命中词表＝生产侧漂移，宁可抛错也不写出"看起来对"的证据串。
        String purposeWord = DecisionChannelContract.normalize(
                purpose == null ? Purpose.PAGE.name() : purpose.name());
        if (purposeWord == null) {
            throw new IllegalArgumentException("DecisionBundle.Purpose 与通道词表失配: " + purpose);
        }
        return "decision=" + decisionId
                + " purpose=" + purposeWord
                + " rls=[" + (rlsPolicyRefs().isEmpty() ? "NONE" : String.join("+", rlsPolicyRefs())) + "]"
                + " cls=[" + (clsPolicyRefs().isEmpty() ? "NONE" : String.join("+", clsPolicyRefs())) + "]";
    }

    /**
     * OPA 水印义务文本（§2.10.2 五件套之四 / 从零实施版 line 2542：
     * {@code obligations.watermark} → 导出文件页眉页脚）。
     *
     * <p>兼容的三种义务书写形态收敛到单源读数
     * {@link GuardrailDecisionContract#readWatermark(Object)}（D-B4-31）——
     * 键名字面量只允许存在于底座。<b>无义务时返回 null</b>
     * —— 消费方须原样落 {@code NOT_ISSUED} 标记，禁把"没有水印义务"渲染成"已加水印"。</p>
     */
    public String watermarkObligation() {
        return GuardrailDecisionContract.readWatermark(
                GuardrailDecisionContract.obligationsOf(opa));
    }

    // ── #142/B4-3c 只增：OPA 护栏四态消费面 ─────────────────────────────
    //
    // 「不可用 ≠ 无权限」（详设-01 §4.7.2）此前在 bundle 上无法表达：opa 段只有 allow 布尔，
    // 策略未加载 / OPA 不可达 / 真策略拒绝三态都是 allow=false。四态串由
    // SecurityDecisionService#safeOpa 单点写入 opa.policyState，本组访问器是唯一读面
    // —— 消费方据此把「护栏没说话」报 503/FAIL_CLOSED，把「策略拒绝」报 403/DENY。

    /** OPA 护栏态串（四态之一）；opa 段缺失或键漂移时返回 null（＝未评估，禁当成放行）。 */
    public String opaPolicyState() {
        return GuardrailDecisionContract.stateOf(opa);
    }

    /** 护栏是否明确放行（{@code allow=true} 且状态为 ALLOW；仅布尔为真不算）。 */
    public boolean opaAllowed() {
        return GuardrailDecisionContract.STATE_ALLOW.equals(opaPolicyState())
                || (opaPolicyState() == null && Boolean.TRUE.equals(opa == null ? null
                        : opa.get(GuardrailDecisionContract.KEY_ALLOW)));
    }

    /** 护栏是否给出了策略裁决（ALLOW / DENY）。false ⇒ 部署缺陷或不可用态，走 503 分支。 */
    public boolean guardrailEvaluated() {
        return GuardrailDecisionContract.isEvaluated(opaPolicyState());
    }

    /**
     * 护栏是否<b>没有给出</b>策略裁决（{@code POLICY_UNDEFINED} / {@code OPA_UNAVAILABLE} /
     * opa 段缺失 / 状态键漂移）。
     *
     * <p>与 {@link #guardrailEvaluated()} 互补；单独成名是因为消费方的 503 分支必须
     * "确实知道护栏没说话"，而不是"allow 不为真"的后验推断。</p>
     */
    public boolean guardrailUnevaluated() {
        String state = opaPolicyState();
        return state == null || GuardrailDecisionContract.isUnevaluated(state);
    }

    /** OPA 拒绝原因（义务集里第一条 {@code deniedReason}；无则 null，读数单源见底座）。 */
    public String opaDeniedReason() {
        return GuardrailDecisionContract.readDeniedReason(
                GuardrailDecisionContract.obligationsOf(opa));
    }

    private static List<String> policyNames(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                Object name = m.get("name");
                if (name != null) out.add(String.valueOf(name));
            } else if (o != null) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rls", rls);
        // 段键单源（#142/B4-3d-6）：消费侧（workspace AbacQueryFilter / AI 列面闸）读的是
        // DecisionChannelContract.KEY_BUNDLE_COLUMNS，写侧必须同源，否则又是一次键面漂移。
        m.put(com.chinacreator.gzcm.common.security.decision.DecisionChannelContract
                .KEY_BUNDLE_COLUMNS, columns);
        m.put("masks", masks);
        m.put("opa", opa);
        m.put("decisionId", decisionId);
        m.put("traceId", traceId);
        return m;
    }
}
