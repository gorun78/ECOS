package com.chinacreator.gzcm.workspace.dto;

import java.util.Map;

/**
 * 状态机转换规则（{@code GET /ecos/state-machine/{entityCode}} 的 transitions[]）。
 *
 * <p>键序：{@code from/to/transitionCode/transitionName/requireRole/guardRule/sideEffect/sortOrder}；
 * 后三列由定义表可为空，{@code sortOrder} 保持 DB 原值类型（{@code Object}，通常 Integer）。</p>
 */
public record StateTransitionVO(
                String from,
        String to,
        String transitionCode,
        String transitionName,
        String requireRole,
        String guardRule,
        String sideEffect,
        Object sortOrder) {

    public static StateTransitionVO of(Map<String, Object> def) {
        return new StateTransitionVO(
                str(def.get("from_status")),
                str(def.get("to_status")),
                str(def.get("transition_code")),
                str(def.get("transition_name")),
                str(def.get("require_role")),
                str(def.get("guard_rule")),
                str(def.get("side_effect")),
                def.get("sort_order"));
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
