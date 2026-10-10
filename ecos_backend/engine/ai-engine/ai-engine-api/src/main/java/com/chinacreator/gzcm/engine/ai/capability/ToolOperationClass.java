package com.chinacreator.gzcm.engine.ai.capability;

/**
 * 工具契约操作类别词表（《补充设计 v1.0》§11.1 {@code operationClass} / §11.2 三态约束，
 * V270 {@code operation_class VARCHAR(10)} 同源）。
 *
 * <p>§11.2 操作类别约束：</p>
 * <ul>
 *   <li>{@link #READ}：只读，不改变正式资产；仍需权限校验；</li>
 *   <li>{@link #SUGGEST}：生成候选或提案，不修改权威资产；必须带证据、适用范围、置信类型和冲突；</li>
 *   <li>{@link #WRITE}：改变正式资产或触发有副作用任务；必须通过 security-engine 裁决，
 *       满足审批要求、幂等、审计和回滚契约（缺有效契约/非 ACTIVE/schema 不匹配 ⇒ 不入执行计划）。</li>
 * </ul>
 *
 * <p>读取端兼容默认（§11.1 首行"如已有同义字段则映射"）：存量行 operationClass 为空时
 * 按 V236 在置 {@code side_effect} 回落——无副作用即 {@link #READ}（读侧单源在查询端，
 * 禁散落在各调用方）。</p>
 */
public enum ToolOperationClass {
    /** 只读（不改变正式资产；仍需权限校验）。 */
    READ,
    /** 生成候选/提案（不修改权威资产；必带证据+适用范围+置信类型+冲突）。 */
    SUGGEST,
    /** 改变正式资产/有副作用（必走 security-engine 裁决 + 审批 + 幂等 + 审计 + 回滚）。 */
    WRITE;

    /** 解析落库值（大小写不敏感；非法值抛 IAE，禁宽容映射）。 */
    public static ToolOperationClass parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("工具契约操作类别缺失（V270 operation_class；读侧按 V236 side_effect 回落前不构造本值）");
        }
        try {
            return ToolOperationClass.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("非法操作类别(仅 READ/SUGGEST/WRITE §11.2): " + value, e);
        }
    }
}
