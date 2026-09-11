package com.chinacreator.gzcm.sysman.security;

import java.lang.annotation.*;

/**
 * 准入等级注解 — 标注在 Controller 方法上，指定访问该接口所需的最低准入等级。
 * <p>
 * 等级设计：
 * <ul>
 *   <li>L0 — 公开：只读公开数据</li>
 *   <li>L1 — 内部：读内部数据 + 提交任务</li>
 *   <li>L2 — 保密：读保密数据 + 管理任务 + 查看审计</li>
 *   <li>L3 — 机密：全量读 + 管理用户/角色 + 修改配置</li>
 *   <li>L4 — 绝密：系统管理 + 安全配置 + 物理工作站绑定</li>
 * </ul>
 * <p>
 * 默认规则（未标注时按路径前缀自动推断）：
 * <ul>
 *   <li>{@code /api/v1/security/**} → L2</li>
 *   <li>{@code /api/v1/system/**}   → L3</li>
 *   <li>其余 {@code /api/**}      → L1</li>
 * </ul>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MinimumClearance {

    /**
     * 所需的最低准入等级 (0-4)。
     * 当用户 clearanceLevel >= level 时放行。
     */
    int level() default 0;
}
