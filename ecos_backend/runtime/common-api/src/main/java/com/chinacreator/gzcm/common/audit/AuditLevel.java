package com.chinacreator.gzcm.common.audit;

/**
 * 审计分级 L1~L4（《补充详设 R7~R11》§2.9.4④ / DQ-1；#147J）。
 *
 * <p><b>裁决口径</b>：维持四级（L1 常规 / L2 受控 / L3 高风险 / L4 关键）；
 * "简化为三级"只是<b>降级选项</b>，非默认——{@link #degradedToThreeLevels()} 明确返回
 * {@code false}，任何想启用的部署须显式改常量并留裁决记录。</p>
 *
 * <p><b>与既有等级的关系（严禁替换）</b>：{@code clearance_level}（L0 公开~L4 绝密）与
 * {@code audit_mode}（basic/detailed/full）是既有安全体系；本枚举只表达"这次变更要多少审计强度"，
 * 并通过 {@link #minimumClearance()} / {@link #requiredAuditMode()} 与之核对。
 * 审计等级不提高也不降低对象的访问密级。</p>
 *
 * @author ECOS Control Domain #147J
 */
public enum AuditLevel {

    /** L1 常规：非敏感描述、展示配置、低风险标签。 */
    L1_REGULAR(1, 0, "basic", false),
    /** L2 受控：数据接入元数据、Pipeline、质量规则、知识内容。 */
    L2_CONTROLLED(2, 1, "detailed", false),
    /** L3 高风险：组织用户关系、权限策略、本体结构、认知规则、心智模型、Agent 协同流程。 */
    L3_HIGH_RISK(3, 2, "full", false),
    /** L4 关键：管理员授权、关键安全策略、跨租户边界、可致大范围暴露/执行的变更。 */
    L4_CRITICAL(4, 3, "full", true);

    private final int level;
    private final int minimumClearance;
    private final String requiredAuditMode;
    private final boolean dualApprovalRequired;

    AuditLevel(int level, int minimumClearance, String requiredAuditMode, boolean dualApprovalRequired) {
        this.level = level;
        this.minimumClearance = minimumClearance;
        this.requiredAuditMode = requiredAuditMode;
        this.dualApprovalRequired = dualApprovalRequired;
    }

    public int level() {
        return level;
    }

    /** 执行/查看该级变更所需的最低 clearance_level（L0=0 … L4=4；不修改既有定义）。 */
    public int minimumClearance() {
        return minimumClearance;
    }

    /** 该级要求的最低 audit_mode（basic &lt; detailed &lt; full）。 */
    public String requiredAuditMode() {
        return requiredAuditMode;
    }

    /** L4 关键变更须双人或职责分离审批（§2.9.4④）。 */
    public boolean dualApprovalRequired() {
        return dualApprovalRequired;
    }

    /** 是否允许简化为三级——裁决为"降级选项，非默认"，故恒为 false。 */
    public boolean degradedToThreeLevels() {
        return false;
    }

    /** 由数字解析（1~4）；越界返回 {@code null}（禁静默默认低等级）。 */
    public static AuditLevel fromLevel(int level) {
        for (AuditLevel v : values()) {
            if (v.level == level) {
                return v;
            }
        }
        return null;
    }

    /** audit_mode 强度序（basic=1 / detailed=2 / full=3）；未知模式返回 0。 */
    public static int auditModeRank(String auditMode) {
        if (auditMode == null) {
            return 0;
        }
        return switch (auditMode.trim().toLowerCase()) {
            case "basic" -> 1;
            case "detailed" -> 2;
            case "full" -> 3;
            default -> 0;
        };
    }
}
