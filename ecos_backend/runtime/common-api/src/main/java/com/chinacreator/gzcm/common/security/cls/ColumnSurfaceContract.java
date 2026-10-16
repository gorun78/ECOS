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
