package com.chinacreator.gzcm.common.security.cls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 详设-01 C.2.3 / §4.3.3 — CLS <b>列面应答</b>的单源读取契约（#142/B4-3d-1，差距 D-B4-25）。
 *
 * <p><b>为什么必须有这个类（缺陷本质）</b>：生产者
 * {@code ColumnLevelSecurityServiceImpl#getColumns} 的 {@code data} 段发的是
 * {@code visibleColumns}（{@code ClsController} 的 POST/GET 两条路径同源），而三个消费者
 * （workspace {@code WorkspaceSecurityEngineClient#allowedColumns}、
 * ontology {@code SecurityEngineClient#filterColumns}、
 * ai {@code AiSecurityEngineClient#allowedColumns}）都按 legacy 契约册的
 * {@code allowedColumns} 取数。该键在生产侧<b>从未存在</b>
 * （仓根实证：{@code grep -rn '"allowedColumns"' ecos_backend --include=*.java} 只命中
 * 三处消费位与测试，生产侧零写入点），于是三处都拿到 {@code null} 后返回空集 ——
 * 页面/本体/AI 三通道的列级安全<b>结构性恒等于全拒绝</b>。</p>
 *
 * <p>这与 D-B4-17（{@code whereClause} 键漂移）同族，只是方向相反：那边"读不到"被折叠成
 * 空串＝不过滤（fail-open），这边"读不到"被折叠成空集＝DENY（fail-closed 但功能死面）。
 * 两者的共同根因是<b>键名两侧各写各的、没有单源读取面</b>，所以修法不是把三处字面量各自
 * 改对，而是把"怎么读列面"收敛成一个类，三处只做委托。</p>
 *
 * <p><b>三态分立（与 RLS 执行契约同纪律）</b>：</p>
 * <ul>
 *   <li>不存在"恒真放行"这一态（{@code RlsFilter} 才有 {@code 1=1}）—— CLS 的"不限制"由非空
 *       可读列集表达；</li>
 *   <li>可读列集为<b>空表</b>＝策略明确拒绝全部列（{@code denyIfEmpty}，§4.3.3
 *       {@code ECOS-SEC-402}）；</li>
 *   <li>两个键<b>都读不到</b>＝形态漂移（{@link Reading#drifted()}），不是"无策略"，
 *       消费方必须拒绝并在日志里带上 {@link Reading#reason()}，禁静默空集冒充策略裁决。</li>
 * </ul>
 *
 * <p>legacy 别名 {@link #KEY_ALLOWED_COLUMNS_LEGACY} 仅在权威键缺失时才生效，且生效时
 * {@link Reading#legacyKeyUsed()} 为真 —— 留这条是因为停写侧仍可能有旧应答形态，
 * 但<b>不得</b>反过来把它当主键（那等于恢复缺陷）。</p>
 */
public final class ColumnSurfaceContract {

    /** 生产者单源键（{@code ColumnLevelSecurityServiceImpl} 唯一写出形态）。 */
    public static final String KEY_VISIBLE_COLUMNS = "visibleColumns";

    /** 生产者同源的被阻止列（消费者用于展示"哪些列被策略隐藏"，不得当成可读集）。 */
    public static final String KEY_BLOCKED_COLUMNS = "blockedColumns";

    /** legacy 契约册键名（{@code docs/30-设计/legacy-plans/api-contract.md}）；只作兜底别名。 */
    public static final String KEY_ALLOWED_COLUMNS_LEGACY = "allowedColumns";

    /** 键面漂移（两键皆无或形态非列表）—— 拒绝态的理由前缀。 */
    public static final String DRIFT_ABSENT = "CLS_COLUMN_SURFACE_ABSENT";

    /** 应答 {@code data} 段整体缺失（无 data / 非 Map）。 */
    public static final String DRIFT_NO_DATA = "CLS_RESPONSE_HAS_NO_DATA_SEGMENT";

    // ── #142/B4-3d-2 只增：裁决束列面的「证据键 + 理由词表」单源（差距 D-B4-16） ──
    //
    // 动机：{@code /cls/columns} 的列面只回答"哪些列可读"，而三通道唯一裁决入口
    // {@code /decision} 的 {@code columns} 段还要回答"这个结论是怎么来的"。此前 bundle
    // 只有 {@code mode}/{@code names} 两个键，且 {@code mode} 由生产者恒写 "allow"（零信息量），
    // 于是「策略拒绝全部列」「列全集(universe)没给」「CLS 源抛异常」三态在消费侧全是
    // {@code names=[]} 一副面孔 —— 消费方无法按 §4.5.4「不可用 ≠ 无权限」分态，
    // 只能一律当"无权限"，或更糟：当成"没限制"。这三个键与三个理由值就是为了让
    // 三态在**线上形态**上可区分，且生产者/消费者共用同一份字面量（禁各写一套 ——
    // 那正是 D-B4-25 的根因）。

    /** 列全集出处（{@link #UNIVERSE_REQUEST} / {@link #UNIVERSE_NONE}）。 */
    public static final String KEY_COLUMN_UNIVERSE_SOURCE = "columnUniverseSource";

    /** 列全集是否解析得出来（{@code false}＝未解析，此时 {@code visibleColumns=[]} 不是策略结论）。 */
    public static final String KEY_COLUMN_UNIVERSE_RESOLVED = "columnUniverseResolved";

    /** 列面不可用时的机读理由（可用时该键缺席——禁写空串冒充"无理由"）。 */
    public static final String KEY_CLS_DENY_REASON = "clsDenyReason";

    /** 列全集来自调用方显式请求（页面通道＝结果行的实际列键，唯一常态）。 */
    public static final String UNIVERSE_REQUEST = "REQUEST";

    /** 调用方未给列全集 ⇒ 列面不可判定（生产者禁自造宇宙源，见类内 {@link #DENY_UNIVERSE_UNRESOLVED}）。 */
    public static final String UNIVERSE_NONE = "NONE";

    /** 列全集在位、CLS 策略把可读集清空 ⇒ 真正的"策略拒绝全部列"（§4.3.3 {@code ECOS-SEC-402}）。 */
    public static final String DENY_POLICY = "CLS_POLICY_DENY";

    /**
     * 列全集未解析 ⇒ 列面**未判定**，不是"用户无权限"。
     *
     * <p>本批刻意不给 universe 兜底一个"CATALOG"源：安全域资产目录 {@code ecos_security_asset}
     * 在 dev 实存 0 行（D-B4-19），拿它当宇宙等于用一条不可测分支冒充单源。裁决登记见
     * 差距文档 v1.0.19 行。</p>
     */
    public static final String DENY_UNIVERSE_UNRESOLVED = "COLUMN_UNIVERSE_UNRESOLVED";

    /** CLS 源自身抛异常（{@code SecurityDecisionService#safeCls} 兜底态）⇒ 属可用性/缺陷态。 */
    public static final String DENY_SOURCE_ERROR = "CLS_SOURCE_ERROR";

    // ── #142/B4-3d-6 只增：裁决束列面的「消费侧四态分类」单源（差距 D-B4-28） ──
    //
    // 动机：{@link #read(Object)} 只回答"键面读出什么"，而消费方要做的是"这个结论该怎么处置"——
    // 后者在过去只有一份手抄实现（workspace {@code AbacQueryFilter#filterColumns} 里的
    // read→drifted→universeUnresolved→visible.isEmpty 四段 if）。AI 通道列面消费腿
    // （D-B4-28：{@code allowedColumns} 零调用方）一旦接线，若照抄那四段，就是本批次一路在关的
    // 「第 N 套手抄解析」缺陷族的新成员（D-B4-24/25 同族）。因此把**优先级顺序**本身收进单源：
    // 源异常 > 宇宙未解析 > 键面漂移 > 策略拒绝（与生产侧 {@code SecurityDecisionService}
    // 写理由码的顺序一致），消费侧只允许按 {@link Surface#state()} 分派。
    //
    // 生产侧（security-engine）的 {@code DecisionBundle} 继续用它自己的内存对象视图
    // （它拿的是裁决现场，不是 REST 应答形态），两者共用同一批理由码；
    // 等价性由 {@code ColumnSurfaceContractTest} 的逐态断言 +
    // {@code ClsColumnSurfaceContractGateTest} 的跨模块清单钉住，禁第三份顺序实现。

    /**
     * 裁决束列面在消费侧的分类态（详设-01 C.2.3 + §4.5.4「不可用 ≠ 无权限」）。
     *
     * <p>只有 {@link #TRIMMED} 允许裁剪后放行；{@link #POLICY_DENY} 是<b>策略结论</b>
     * （对外 403 面）；其余三态都是缺陷/形态/可用性态（对外 503 面），
     * 禁与策略拒绝折叠成同一条"无可见列"（那正是 D-B4-16 的病灶）。</p>
     */
    public enum SurfaceState {
        /** 列面已判定且可读集非空 ⇒ 按 {@link Surface#visible()} 裁剪。 */
        TRIMMED,
        /** 列全集在位、策略把可读集清空 ⇒ 明确的"无列权限"（{@link #DENY_POLICY}）。 */
        POLICY_DENY,
        /** CLS 源异常 ⇒ 裁决没得出结论，属不可用态（{@link #DENY_SOURCE_ERROR}）。 */
        SOURCE_ERROR,
        /** 调用方未给列全集 ⇒ 列面<b>未判定</b>，不是"用户无权限"。 */
        UNIVERSE_UNRESOLVED,
        /** 应答列面键面漂移（权威键与别名皆不可读）⇒ 形态态。 */
        SURFACE_DRIFT,
        /** 裁决束/列面段整体缺失（无 data 段或列面非 Map）⇒ 无裁决出处。 */
        SEGMENT_ABSENT
    }

    /**
     * 列面分类结果（不可变；由 {@link #decide(Object)} 单点构造）。
     *
     * <p>不变量：{@link #state()} 非 {@link SurfaceState#TRIMMED} 时
     * {@link #visible()} 恒为空表 —— 消费方拿不到"已拒绝但仍带可读集"的混合态，
     * 也就无法在折叠分支里顺手放行。</p>
     */
    public static final class Surface {

        private final SurfaceState state;
        private final List<String> visible;
        private final List<String> blocked;
        private final boolean legacyKeyUsed;
        private final String reason;

        private Surface(SurfaceState state, List<String> visible, List<String> blocked,
                        boolean legacyKeyUsed, String reason) {
            this.state = state;
            this.visible = state == SurfaceState.TRIMMED
                    ? Collections.unmodifiableList(visible)
                    : Collections.emptyList();
            this.blocked = Collections.unmodifiableList(blocked);
            this.legacyKeyUsed = state == SurfaceState.TRIMMED ? legacyKeyUsed : false;
            this.reason = reason;
        }

        /** 分类态（消费侧唯一分派位）。 */
        public SurfaceState state()        { return state; }

        /** 可读列集；仅 {@link SurfaceState#TRIMMED} 时可能非空。 */
        public List<String> visible()      { return visible; }

        /** 被策略显式隐藏的列（展示/审计用，禁并入 {@link #visible()}）。 */
        public List<String> blocked()      { return blocked; }

        /** 是否走了 legacy 别名键（只作观测位，禁当成功标志）。 */
        public boolean legacyKeyUsed()     { return legacyKeyUsed; }

        /**
         * 机读理由（生产侧写的 {@link #KEY_CLS_DENY_REASON} 原文优先；
         * 缺失时由本单源按态补对应的 {@code DENY_*}/{@code DRIFT_*} 常量，
         * 禁消费方各自拼自由文案 —— 那会让审计侧无法按码分派）。
         */
        public String reason()             { return reason; }

        /** 唯一"可以按可读集裁剪"的判据。 */
        public boolean usable()            { return state == SurfaceState.TRIMMED; }

        /** 是否为策略结论（对外 403 面），与不可用态分派用。 */
        public boolean policyDenied()      { return state == SurfaceState.POLICY_DENY; }

        /** 是否为缺陷/形态/可用性态（对外 503 面）。 */
        public boolean failClosed() {
            return state == SurfaceState.SOURCE_ERROR
                    || state == SurfaceState.UNIVERSE_UNRESOLVED
                    || state == SurfaceState.SURFACE_DRIFT
                    || state == SurfaceState.SEGMENT_ABSENT;
        }
    }

    /**
     * 把裁决束的 {@code columns} 段分类成消费侧四态（单源优先级，禁再手抄一遍 if 链）。
     *
     * <p>优先级：列面段缺失 &gt; 键面漂移 &gt; 源异常 &gt; 宇宙未解析 &gt; 策略拒绝 &gt; 裁剪。
     * 前两项先判是因为它们说明"这份应答根本不是列面"，此时读到的任何
     * {@code clsDenyReason} 都不可信；源异常排在宇宙/策略之前，是因为生产者用同一份
     * 空 {@code visibleColumns} 表达三态，只有理由码能区分（D-B4-16）。</p>
     *
     * @param columnsSegment 应答/裁决束里的 {@code columns} 段原值（可为 {@code null}）
     * @return 永不为 {@code null}；拒绝态带机读理由，禁调用方再自造默认值
     */
    public static Surface decide(Object columnsSegment) {
        Reading reading = read(columnsSegment);
        if (!(columnsSegment instanceof Map<?, ?> data)) {
            return new Surface(SurfaceState.SEGMENT_ABSENT, reading.visible(), reading.blocked(),
                    false, reading.drifted() ? reading.reason() : DRIFT_NO_DATA);
        }
        if (reading.drifted()) {
            return new Surface(SurfaceState.SURFACE_DRIFT, reading.visible(), reading.blocked(),
                    false, reading.reason());
        }
        String written = denyReasonOf(data);
        if (DENY_SOURCE_ERROR.equals(written)) {
            return new Surface(SurfaceState.SOURCE_ERROR, reading.visible(), reading.blocked(),
                    reading.legacyKeyUsed(), DENY_SOURCE_ERROR);
        }
        if (universeUnresolved(data)) {
            return new Surface(SurfaceState.UNIVERSE_UNRESOLVED, reading.visible(), reading.blocked(),
                    reading.legacyKeyUsed(),
                    written != null ? written : DENY_UNIVERSE_UNRESOLVED);
        }
        if (reading.visible().isEmpty()) {
            return new Surface(SurfaceState.POLICY_DENY, reading.visible(), reading.blocked(),
                    reading.legacyKeyUsed(), written != null ? written : DENY_POLICY);
        }
        return new Surface(SurfaceState.TRIMMED, reading.visible(), reading.blocked(),
                reading.legacyKeyUsed(), written);
    }

    // ── #142/B4-3d-6 只增：未判定列面的显式证据面（差距 D-B4-28 的 OAG 半边） ──
    //
    // OAG 管道（{@code SecurityCheckerNode} → {@code ReasoningEngineNode}）在这批之前把
    // "整条链路没有数据行、因此没有列全集"写成 {@code new LinkedHashMap<>()} ——
    // 空 Map 既不是"策略允许零列"也不是"已判定"，但下游 {@code !clsColumns.isEmpty()}
    // 的真值判断恰好把两种意思混在一起：把它当"没做 CLS"（现状：不提过滤）是巧合，
    // 而任何接线后写入的非空占位都会让它宣称"返回列已由 CLS 策略过滤"（假话）。
    // 修法不是猜意图，而是把"判定与否"变成线上可读的显式标记。

    /** 列面证据面的状态键（消费者内部上下文，非 REST 契约面）。 */
    public static final String KEY_FACE_STATE = "clsFaceState";

    /** 证据面的状态理由键（与 {@link #KEY_FACE_STATE} 同处，供审计与日志引用）。 */
    public static final String KEY_FACE_REASON = "clsFaceReason";

    /** 列面已由 security-engine 裁决（消费者确实按 {@link #decide(Object)} 取过数）。 */
    public static final String FACE_STATE_EVALUATED = "CLS_FACE_EVALUATED";

    /** 列面<b>未判定</b>（本链路没有可裁决的列全集）—— 禁据此宣称"已按 CLS 过滤"。 */
    public static final String FACE_STATE_NOT_EVALUATED = "CLS_FACE_NOT_EVALUATED";

    /**
     * 构造"未判定"列面证据面：状态 + 理由成对写出，禁空集合冒充结论。
     *
     * @param reason 机读理由（如 {@code OAG_NO_COLUMN_UNIVERSE}）；空白时按单源常量兜底
     */
    public static Map<String, Object> notEvaluatedFace(String reason) {
        Map<String, Object> face = new java.util.LinkedHashMap<>();
        face.put(KEY_FACE_STATE, FACE_STATE_NOT_EVALUATED);
        face.put(KEY_FACE_REASON, reason == null || reason.isBlank()
                ? DENY_UNIVERSE_UNRESOLVED : reason);
        return face;
    }

    /**
     * 证据面是否代表"列面已判定"。
     *
     * <p>只在显式写了 {@link #FACE_STATE_EVALUATED} 时为真。缺失/非 Map/写了别的值一律为假 ——
     * 有意把默认值放在"不宣称过滤过"这一侧：默认放行这条宣称，等于让任何一个新写入方
     * 忘了挂标记时就说出假话（本批次要关的正是这类"缺省即虚报"）。</p>
     */
    public static boolean isEvaluatedFace(Object face) {
        return face instanceof Map<?, ?> m
                && FACE_STATE_EVALUATED.equals(m.get(KEY_FACE_STATE));
    }

    private ColumnSurfaceContract() {
    }

    /**
     * 列面读取结果（不可变；由 {@link #read(Object)} 单点构造）。
     *
     * <p>不变量：{@code drifted == true} 时 {@code visible} 恒为空且
     * {@code reason != null} —— 消费者无论走哪个分支，都拿不到"可读集非空但已漂移"
     * 这种既能放行又被标记为拒绝的混合态。</p>
     */
    public static final class Reading {

        private final List<String> visible;
        private final List<String> blocked;
        private final boolean drifted;
        private final boolean legacyKeyUsed;
        private final String reason;

        private Reading(List<String> visible, List<String> blocked, boolean drifted,
                        boolean legacyKeyUsed, String reason) {
            this.visible = Collections.unmodifiableList(visible);
            this.blocked = Collections.unmodifiableList(blocked);
            this.drifted = drifted;
            this.legacyKeyUsed = drifted ? false : legacyKeyUsed;
            this.reason = drifted ? reason : null;
        }

        static Reading of(List<String> visible, List<String> blocked, boolean legacyKeyUsed) {
            return new Reading(visible, blocked, false, legacyKeyUsed, null);
        }

        static Reading drift(String reason) {
            return new Reading(new ArrayList<>(), new ArrayList<>(), true, false, reason);
        }

        /** 可读列集；{@code drifted} 或策略 DENY 时为空表（消费者按空集＝拒绝处理）。 */
        public List<String> visible()       { return visible; }

        /** 被策略显式隐藏的列（可空表；展示与审计用，禁并入 {@link #visible()}）。 */
        public List<String> blocked()       { return blocked; }

        /** 是否形态漂移（两键皆读不到）—— 与"策略拒绝全部列"分态。 */
        public boolean drifted()            { return drifted; }

        /** 是否走的是 legacy 别名键（用于日志与门禁，禁当成功标志）。 */
        public boolean legacyKeyUsed()      { return legacyKeyUsed; }

        /** 漂移理由（仅 {@link #drifted()} 时非空）。 */
        public String reason()              { return reason; }

        /** 消费者唯一"可以用可读集做裁剪"的判据：未漂移且非空。 */
        public boolean usable()             { return !drifted && !visible.isEmpty(); }
    }

    /**
     * 从 REST 应答的 {@code data} 段读取列面。
     *
     * @param dataSegment {@code resp.get("data")} 的原值（可为 {@code null}）
     * @return 永不为 {@code null} 的 {@link Reading}；漂移态带理由，禁调用方再自造默认值
     */
    public static Reading read(Object dataSegment) {
        if (!(dataSegment instanceof Map<?, ?> data)) {
            return Reading.drift(DRIFT_NO_DATA);
        }
        Object authoritative = data.get(KEY_VISIBLE_COLUMNS);
        if (authoritative instanceof List<?> list) {
            return Reading.of(strings(list), strings(data.get(KEY_BLOCKED_COLUMNS)), false);
        }
        // 兜底别名：只有权威键不存在（不是"存在但为 null"）时才允许生效
        if (authoritative == null && data.containsKey(KEY_ALLOWED_COLUMNS_LEGACY)
                && data.get(KEY_ALLOWED_COLUMNS_LEGACY) instanceof List<?> legacyList) {
            return Reading.of(strings(legacyList), strings(data.get(KEY_BLOCKED_COLUMNS)), true);
        }
        return Reading.drift(DRIFT_ABSENT
                + "（权威键 " + KEY_VISIBLE_COLUMNS + " 与别名键 " + KEY_ALLOWED_COLUMNS_LEGACY
                + " 均不可读，非「无策略」）");
    }

    /**
     * 列全集是否<b>显式</b>判不可解析。
     *
     * <p>只在 {@code columnUniverseResolved == false} 时为真。键缺失时为假 —— 这是有意的：
     * {@code /cls/columns} 的应答从来没有该键（它不是裁决束），把它当成"不可解析"会让
     * 三通道里唯一没有证据段的应答面凭空变红。裁决束侧的生产者必须写该键，
     * 由 {@code BundleColumnSurfaceTest} 与静态门禁钉住，不靠本方法的默认值兜。</p>
     */
    public static boolean universeUnresolved(Object segment) {
        if (!(segment instanceof Map<?, ?> data)) return false;
        return Boolean.FALSE.equals(data.get(KEY_COLUMN_UNIVERSE_RESOLVED));
    }

    /**
     * 列面不可用的机读理由（键缺失/非字符串形态 ⇒ {@code null}，禁由消费者自造默认理由）。
     *
     * <p>只对 {@code String} 形态生效：与 {@link #read(Object)} 「非列表不猜类型」同一纪律。
     * 若把数字/布尔用 {@code String.valueOf} 折成理由，消费侧的
     * {@code clsDenyReason() equals DENY_*} 判定会在生产侧写错类型时静默变成"有理由的拒绝"，
     * 把形态漂移伪装成策略结论（D-B4-16 要消除的正是这种折叠）。</p>
     */
    public static String denyReasonOf(Object segment) {
        if (!(segment instanceof Map<?, ?> data)) return null;
        if (!(data.get(KEY_CLS_DENY_REASON) instanceof String raw)) return null;
        return raw.isBlank() ? null : raw;
    }

    private static List<String> strings(Object raw) {
        List<String> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return out;
        for (Object o : list) {
            if (o != null) out.add(String.valueOf(o));
        }
        return out;
    }
}
