package com.chinacreator.gzcm.runtime.access.git.publish;

/**
 * 发布冻结闸（§2.9.9 G7「无发布冻结」收口；#147G7）。
 *
 * <p><b>语义</b>：部署级开关 {@code ecos.git.publish.freeze}（或环境变量
 * {@code ECOS_GIT_PUBLISH_FREEZE}）为真时，<b>一切发布/归档写动作在此处被拒</b>——
 * 用于变更窗口、事故处置、审计取证期间的「只读期」。冻结是<b>拒绝写</b>，不是隐藏读：
 * 读面不受影响，避免把冻结做成"页面变空"的假象。</p>
 *
 * <p><b>为什么读 System 属性</b>：runtime 底座不读 {@code sys_config}（铁律「配置只经 sysman 门面」），
 * 且冻结是<b>部署级应急开关</b>——运维需在不依赖 DB/配置缓存可用的前提下即时生效。</p>
 *
 * @author ECOS Runtime #147G7
 */
public final class PublishFreezeGuard {

    /** 冻结开关（System 属性 / 环境变量二选一）。 */
    public static final String PROP_FREEZE = "ecos.git.publish.freeze";
    public static final String ENV_FREEZE = "ECOS_GIT_PUBLISH_FREEZE";

    /** 拒绝机读码。 */
    public static final String CODE_FROZEN = "PUBLISH_FROZEN";

    private PublishFreezeGuard() {
    }

    /** 当前是否处于发布冻结期。 */
    public static boolean isFrozen() {
        String sys = System.getProperty(PROP_FREEZE);
        if (sys != null && !sys.isBlank()) {
            return "true".equalsIgnoreCase(sys.trim()) || "1".equals(sys.trim());
        }
        String env = System.getenv(ENV_FREEZE);
        return env != null && ("true".equalsIgnoreCase(env.trim()) || "1".equals(env.trim()));
    }

    /**
     * 冻结期直接拒绝；未冻结为幂等空操作。
     *
     * @throws IllegalStateException 消息以 {@link #CODE_FROZEN} 开头（机读码先行，便于上层转业务码）
     */
    public static void assertNotFrozen(String action) {
        if (isFrozen()) {
            throw new IllegalStateException(CODE_FROZEN + ": 发布冻结期，禁止 " + action
                    + "（读面不受影响；解冻请清除 " + PROP_FREEZE + " / " + ENV_FREEZE + "）");
        }
    }
}
