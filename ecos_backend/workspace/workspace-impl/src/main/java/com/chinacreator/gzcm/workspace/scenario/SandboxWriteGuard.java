package com.chinacreator.gzcm.workspace.scenario;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 演练写白名单守卫（卷07 F07-08-2，写通道唯一断言点）。
 * <p>SANDBOX 运行上下文只准写 run 表（{@code ecos_scenario_execution}）与画布布局表
 * （{@code ecos_scenario_canvas_layout}）；对任何 D 层五事实表 / {@code ecos_biz_action_outcome} /
 * {@code ecos_decision_record} 的写在入口断言 runMode==FORMAL，否则内部防御异常（正常路径不可达）。</p>
 */
@Component
public class SandboxWriteGuard {

    private static final Set<String> SANDBOX_WHITELIST = Set.of(
            "ecos_scenario_execution",
            "ecos_scenario_canvas_layout");

    /** 白名单（供 ArchUnit / 对账测试引用同一份，禁散落）。 */
    public Set<String> sandboxWriteWhitelist() {
        return SANDBOX_WHITELIST;
    }

    public boolean isWhitelisted(String targetTable) {
        return targetTable != null && SANDBOX_WHITELIST.contains(normalize(targetTable));
    }

    /** 写入口断言：FORMAL 全放行；SANDBOX 仅白名单。 */
    public void assertAllowed(RunMode mode, String targetTable) {
        if (mode == RunMode.SANDBOX && !isWhitelisted(targetTable)) {
            throw new SandboxWriteForbiddenException(targetTable);
        }
    }

    private String normalize(String table) {
        return table.trim().toLowerCase();
    }
}
