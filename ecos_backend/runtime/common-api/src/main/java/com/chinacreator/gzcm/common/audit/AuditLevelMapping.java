package com.chinacreator.gzcm.common.audit;

import java.util.ArrayList;
import java.util.List;

/**
 * 审计等级 × 既有安全等级的映射核对（§2.9.4④ / DQ-1 / DC-5；#147J）。
 *
 * <p><b>它拒绝什么</b>：</p>
 * <ol>
 *   <li>审计等级要求的 audit_mode 强于当前配置（如 L3 要求 full 而配置 basic）；</li>
 *   <li>执行者 clearance 低于该审计等级的最低要求（L4 关键变更须 clearance ≥ L3）；</li>
 *   <li>L4 而审批人数 &lt; 2（双人/职责分离）；</li>
 *   <li>非法 clearance（不在 0~4）或 audit_mode 未知值。</li>
 * </ol>
 *
 * <p><b>不改既有体系</b>：本类只做一致性核对，不写入 {@code clearance_level}/{@code audit_mode}，
 * 也不提供"自动降级"路径（降三级须显式裁决）。</p>
 *
 * @author ECOS Control Domain #147J
 */
public final class AuditLevelMapping {

    /** 映射违例机读码前缀。 */
    public static final String CODE_AUDIT_LEVEL_MISMATCH = "SEC-AUD-170";

    private AuditLevelMapping() {
    }

    /** 核对结果：空列表 = 一致；否则逐条可读违例。 */
    public static List<String> check(AuditLevel level, int clearanceLevel, String auditMode,
                                     int approverCount) {
        List<String> violations = new ArrayList<>();
        if (level == null) {
            violations.add("audit level 为空（禁以缺省低等级放行）");
            return violations;
        }
        if (clearanceLevel < 0 || clearanceLevel > 4) {
            violations.add("clearance_level 越界（既有值域 L0~L4）：" + clearanceLevel);
        } else if (clearanceLevel < level.minimumClearance()) {
            violations.add("clearance_level=" + clearanceLevel + " 低于 " + level
                    + " 要求的最低密级 " + level.minimumClearance());
        }
        int actual = AuditLevel.auditModeRank(auditMode);
        int required = AuditLevel.auditModeRank(level.requiredAuditMode());
        if (actual == 0) {
            violations.add("audit_mode 未知或不支持：" + auditMode);
        } else if (actual < required) {
            violations.add("audit_mode=" + auditMode + " 弱于 " + level + " 要求的 "
                    + level.requiredAuditMode());
        }
        if (level.dualApprovalRequired() && approverCount < 2) {
            violations.add(level + " 须双人/职责分离审批，实际审批人 " + approverCount);
        }
        return violations;
    }

    /** 便捷判定（无违例即 true）。 */
    public static boolean isConsistent(AuditLevel level, int clearanceLevel, String auditMode,
                                       int approverCount) {
        return check(level, clearanceLevel, auditMode, approverCount).isEmpty();
    }
}
