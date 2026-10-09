package com.chinacreator.gzcm.workspace.scenario;

/**
 * 迁移权限裁决抽象（卷07 F07-07-3 / F07-18）。
 * <p>权限判定一律走 security-engine（默认 DENY），禁本地角色字符串比较。本接口便于单测桩替换。</p>
 */
@FunctionalInterface
public interface MigrationPermissionChecker {

    /** @return true=放行；不可用/异常一律 DENY（由实现保证） */
    boolean isAllowed(String permissionPoint, String subject);
}
