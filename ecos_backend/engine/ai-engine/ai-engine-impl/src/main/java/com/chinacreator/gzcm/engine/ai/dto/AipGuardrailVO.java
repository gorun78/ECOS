package com.chinacreator.gzcm.engine.ai.dto;

import java.util.Map;

/**
 * AIP 护栏规则行（内存 store 的唯一行模型）。
 *
 * <p>键序：{@code id/name/type/rule/action/priority/enabled/description/createdAt/updatedAt}。
 * {@code rule/priority/enabled} 保持 {@code Object}：建/改两腿都是"请求体原值存取"
 * （旧 Map 直接 {@code body.get(...)} 透传，强转即改 wire）。</p>
 */
public record AipGuardrailVO(
        String id,
        String name,
        String type,
        Object rule,
        String action,
        Object priority,
        Object enabled,
        String description,
        String createdAt,
        String updatedAt) {

    /** 部分更新：仅覆盖出现的键（语义与旧 Map 拷贝+覆盖一致）。 */
    public AipGuardrailVO withUpdated(String newName, String newType, Object newRule,
                                      String newAction, Object newPriority, Object newEnabled,
                                      String newDescription, String newUpdatedAt) {
        return new AipGuardrailVO(id,
                newName != null ? newName : name,
                newType != null ? newType : type,
                newRule != null ? newRule : rule,
                newAction != null ? newAction : action,
                newPriority != null ? newPriority : priority,
                newEnabled != null ? newEnabled : enabled,
                newDescription != null ? newDescription : description,
                createdAt, newUpdatedAt);
    }
}
