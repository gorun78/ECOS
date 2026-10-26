package com.chinacreator.gzcm.sysman.security.clearance.model;

import java.util.Collections;
import java.util.List;

/**
 * 主体密级（{@code clearanceLevel}）的<b>属主侧如实交付形态</b>（#142/B4-3e-81，差距 D-B4-23 余项腿）。
 *
 * <p><b>为什么不是一个 {@code Integer}</b>：五档链的读结果有三种性质完全不同的形态，过去锁在
 * {@code ClearanceInterceptor} 私有方法里时被压成一个数字——查询报错的档被 fail-soft 的
 * {@code catch} 吞掉继续下探，最后 {@code return 1} 把「没有任何档案」冒充成「L1 内部真值」。
 * 这正是 §4.7.2「不可用 ≠ 无权限 ≠ 空结果」折叠族在密级档上的形态（同族收口见 D-B4-83／D-B4-84）。
 * 本对象把三态拆开交付，<b>由谁兜底、兜不兜是消费方的裁决</b>，属主侧不再替任何人决定。</p>
 *
 * <ul>
 *   <li><b>真值</b>：某一档确实读到 {@code clearance_level} ⇒ {@link #level()} 非空、
 *       {@link #tier()} 指明命中档；</li>
 *   <li><b>未解析</b>：五档全部查过且都没有档案行 ⇒ {@link #level()} 为 {@code null}、
 *       {@link #tier()} ＝ {@link #TIER_NONE}、{@link #degradedTiers()} 为空。
 *       这是「事实就是没有」，<b>不是</b>任何档位；</li>
 *   <li><b>不可用</b>：任一档取数抛错（表不可达／SQL 失败／契约破坏）⇒ 该档进
 *       {@link #degradedTiers()}。此时即便更低档读到一个数字，也不能宣称主体的密级就是它——
 *       高优先档的事实是「断了」不是「没有」，取值链已经不可信。消费方按自身语义处置：
 *       准入面（拦截器）沿用既有兜底，策略面（security 引擎）转不可用态。</li>
 * </ul>
 *
 * <p><b>档位口径分裂（本批只做单源，不改裁决）</b>：{@link #level()} 的原样透传属主列值，
 * 不做任何归一。现网两套口径并存——{@code td_user_security_profile.clearance_level} 实测取值域
 * 是 {@code 0..4}，而字典种子（{@code V12__ecos_sys_dict.sql}／{@code V40__ecos_security_seed.sql}）
 * 的密级枚举是 {@code 1..5}；{@code ClearanceInterceptor} 的 {@code LEVEL_NAMES} 又是 {@code 0..4}。
 * 统一口径要改的是准入语义，属裁决事项（台账新登记），代码侧不偷改。</p>
 */
public final class SubjectClearanceFact {

    /** 命中档：用户级档案（{@code td_user_security_profile.user_id}）。 */
    public static final String TIER_USER = "USER";
    /** 命中档：角色级档案取最大值。 */
    public static final String TIER_ROLE = "ROLE";
    /** 命中档：机构级档案（{@code scope_type = 'ORG'}）。 */
    public static final String TIER_ORG = "ORG";
    /** 命中档：租户级档案（{@code scope_type = 'TENANT'}）。 */
    public static final String TIER_TENANT = "TENANT";
    /** 命中档：全局默认档案（{@code is_default = TRUE}）。 */
    public static final String TIER_GLOBAL = "GLOBAL";
    /** 五档全部查过且无档案行＝未解析（禁与任何档位混写）。 */
    public static final String TIER_NONE = "NONE";

    private final Integer level;
    private final String tier;
    private final List<String> degradedTiers;

    private SubjectClearanceFact(Integer level, String tier, List<String> degradedTiers) {
        this.level = level;
        this.tier = tier == null ? TIER_NONE : tier;
        this.degradedTiers = degradedTiers == null || degradedTiers.isEmpty()
                ? Collections.emptyList()
                : List.copyOf(degradedTiers);
    }

    /** 某档读到真值（同时可携带其它档的降级记录，交由消费方判「不可用」）。 */
    public static SubjectClearanceFact resolved(String tier, Integer level, List<String> degradedTiers) {
        return new SubjectClearanceFact(level, tier, degradedTiers);
    }

    /** 五档查尽无档案＝如实未解析。 */
    public static SubjectClearanceFact unresolved(List<String> degradedTiers) {
        return new SubjectClearanceFact(null, TIER_NONE, degradedTiers);
    }

    /** 主体 id 都拿不到时没有任何事实可谈：未解析、且不算降级（没有发起过取数）。 */
    public static SubjectClearanceFact noSubject() {
        return new SubjectClearanceFact(null, TIER_NONE, null);
    }

    /** 命中档真值；未解析为 {@code null}——禁在消费方写成 {@code level == null ? 1 : level}。 */
    public Integer level() {
        return level;
    }

    /** 命中档名称；未解析时为 {@link #TIER_NONE}，恒非 {@code null} 便于日志与取证分组。 */
    public String tier() {
        return tier;
    }

    /** 取数失败的档（按五档顺序）；空集＝整链可信。 */
    public List<String> degradedTiers() {
        return degradedTiers;
    }

    public boolean isResolved() {
        return level != null;
    }

    /** 整链是否含断腿（含断腿时 {@link #level()} 即便非空也不构成可信密级）。 */
    public boolean isDegraded() {
        return !degradedTiers.isEmpty();
    }

    @Override
    public String toString() {
        return "SubjectClearanceFact{level=" + level + ", tier=" + tier
                + ", degradedTiers=" + degradedTiers + "}";
    }
}
