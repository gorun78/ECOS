package com.chinacreator.gzcm.engine.cognitive.api.mind;

import java.util.List;

/**
 * 场景↔Mind 绑定读面 VO（P0-2 修复；2026-11-07 验证报告 A-2）。
 *
 * <p>读源 = {@code ecos_cognitive.ecos_cognitive_scenario_mind} JOIN
 * {@code ecos_cognitive.ecos_cognitive_mind}（V187/V188）。字段语义：</p>
 * <ul>
 *   <li>{@code mindId} — 认知 Mind 主键（32 位 UUID），即 workspace VO 的 {@code mindRef}；</li>
 *   <li>{@code mindKey} — 业务键（{@code mind_<scenarioId>_<label>} 约定）；</li>
 *   <li>{@code name} — 显示名（workspace 注册时即 label）；</li>
 *   <li>{@code capabilityMask}/{@code closedLoopBounds} — 能力配置事实（非结论、非金额）；</li>
 *   <li>{@code active} — Mind 激活位；{@code bindingRole}/{@code priority} — 绑定事实。</li>
 * </ul>
 *
 * <p>该记录挂在 api 模块供 cognitive（产）与 workspace（REST 消费）共用契约形状，
 * 禁控制器返回 {@code Map}（DI-2 棘轮）。</p>
 */
public record ScenarioMindBindingVO(
        String mindId,
        String mindKey,
        String name,
        String description,
        List<String> capabilityMask,
        List<String> closedLoopBounds,
        boolean active,
        String bindingRole,
        int priority,
        String createTime,
        String updateTime) {
}
