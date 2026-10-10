package com.chinacreator.gzcm.workspace.blueprint;

import java.util.Optional;

/**
 * 场景蓝图状态词表单源（补充设计 v1.0 §6.3 / DDL V271 注释词表一致）。
 *
 * <p>11 个主链态 + 5 个异常/回退态。HTTP 侧非法迁移一律 409（SUP-BP-01），
 * {@link ScenarioBlueprintStateMachine} 是唯一迁移规则持有者。</p>
 */
public enum BlueprintStatus {

    /** 草稿（创建初始态）。 */
    DRAFT,
    /** W Agent 目标理解 / 资产盘点中。 */
    DISCOVERING,
    /** 缺口分析（Gap 清单生成）。 */
    GAP_ANALYSIS,
    /** 蓝图候选就绪（草案可评审）。 */
    BLUEPRINT_READY,
    /** 静态校验 + 回放/测试（§6.4 步骤 6~7）。 */
    VALIDATING,
    /** 验收门槛全过，待审批。 */
    READY_FOR_APPROVAL,
    /** 审批通过（approval_ref 已回填）。 */
    APPROVED,
    /** 已发布（不可变：content/artifact 冻结，更新 = version+1 新行）。 */
    RELEASED,
    /** 已激活（场景开始承载运行）。 */
    ACTIVE,
    /** 运行中（每次运行回链本版本 + context_digest）。 */
    RUNNING,
    /** 完成（终态，不可再迁移）。 */
    COMPLETED,
    /** 业务目标/口径/权威来源不明确，需澄清。 */
    NEEDS_CLARIFICATION,
    /** 权限/必需数据/能力/依赖服务/安全策略不满足。 */
    BLOCKED,
    /** 测试或回放未通过。 */
    VALIDATION_FAILED,
    /** 预算耗尽/人工干预/运行风险触发。 */
    PAUSED,
    /** 被新版本替代（旧发布行，保留只读可追溯，不可再变更）。 */
    DEPRECATED;

    /** 容错解析；未知/空返回 empty（调用方按非法态 409 处理，禁默认值伪装）。 */
    public static Optional<BlueprintStatus> fromNullable(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        for (BlueprintStatus s : values()) {
            if (s.name().equals(trimmed)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}
