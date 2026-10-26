package com.chinacreator.gzcm.sysman.security.clearance.model;

/**
 * 密级档位视图口径的属主单源（D-B4-99 c) 收录）。
 *
 * <p>此前实跑实测三处各自硬编码的同名词表：
 * <ul>
 *   <li>{@code sysman-impl/ClearanceInterceptor.LEVEL_NAMES}（0..4）</li>
 *   <li>{@code sysman-impl/SecurityProfileQueryDao.LEVEL_NAMES}（0..4）</li>
 *   <li>security-engine 旧 {@code SecurityConfigDao.LEVEL_NAMES}（0..4，3e-84 已整腿删除）</li>
 * </ul>
 * 而 DDL V24 列注释权威口径正是「0-4：L0公开 L1内部 L2保密 L3机密 L4绝密」，
 * 本枚 {@link #NAMES} 归字面到该权威口径。字典 {@code sys_dict} V12／V40 的
 * 1..5 编码是 UI 展示标签体系，与列值 0-based 之间体系差由 V291 补 code 0 关账
 * （具体文献见台账 D-B4-99 c) 登记原文），本类不再管字典扩缩，只管列值域的命名映射。
 *
 * <p>「无档案兜底值」收拢至 {@link #NO_PROFILE_FALLBACK}：
 * <ul>
 *   <li>准入面 {@code ClearanceInterceptor#resolveUserClearanceLevel} 五档全断时
 *       返值（登记原文「收紧方向的历史既定值」，载本常量＝上收单源不改值）；</li>
 *   <li>档案缺省回填面（{@code SecurityConfigService#populateProfileResult}、
 *       {@code SecurityProfileController#populateProfileResult}）的 profile 为 null 时
 *       回填的 clearanceLevel／level／levelName 三键，也取本常量＝生产者单源。</li>
 * </ul>
 * 改本常量＝改准入语义＋改 wire 双清单生效，不在目录性批次做。
 */
public final class ClearanceLevel {

    /**
     * 五档闭表（0-based，供 DDL V24 clearance_level 列域使用）。
     * 越界在 {@link #nameOf(int)} 内以 {@code "L" + level} 兜底（与旧三处越界兜底一致，wire 不变）。
     */
    public static final String[] NAMES = {"L0公开", "L1内部", "L2保密", "L3机密", "L4绝密"};

    /**
     * 「无档案」时候选回填的档位值＝ L1 内部。
     * 单点定义，授权面与展示面同源；本常量中档＝1，任何迁移／扩档都必须改本常量（单点裁决）。
     */
    public static final int NO_PROFILE_FALLBACK = 1;

    private ClearanceLevel() {}

    /**
     * 密级档位值 → 展示名。
     * 0..4 命中闭表；越界返回 {@code "L"+level}（与旧三处 {@code LEVEL_NAMES}
     * 越界兼单一致，wire 零漂移）。
     */
    public static String nameOf(int level) {
        if (level >= 0 && level < NAMES.length) {
            return NAMES[level];
        }
        return "L" + level;
    }
}
