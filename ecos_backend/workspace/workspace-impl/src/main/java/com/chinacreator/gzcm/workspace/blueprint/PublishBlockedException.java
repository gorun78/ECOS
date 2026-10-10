package com.chinacreator.gzcm.workspace.blueprint;

import java.util.List;
import java.util.Map;

/** 发布/审批前置门槛未满足（§6.6 验收门槛 / §13.1，HTTP 409）。 */
public class PublishBlockedException extends BlueprintDomainException {

    public PublishBlockedException(String code, String message) {
        super(409, code, message);
    }

    public PublishBlockedException(String code, String message, List<String> reasons, Map<String, Object> details) {
        super(409, code, message);
        if (reasons != null) {
            putDetail("rejectedReasons", reasons);
        }
        if (details != null) {
            details.forEach(this::putDetail);
        }
    }

    /** 验收门槛未通过（§6.6 四道静态门禁）。 */
    public static PublishBlockedException validationFailed(List<String> reasons, Map<String, Object> details) {
        return new PublishBlockedException("BLUEPRINT_VALIDATION_FAILED",
                "蓝图未通过发布前验收门槛，禁止发布（§6.6）", reasons, details);
    }

    /** 审批引用缺失/审批链不齐（approval_required=1 且 approval_ref 为空，或 approve 时缺 ref）。 */
    public static PublishBlockedException missingApproval(String detail) {
        return new PublishBlockedException("BLUEPRINT_APPROVAL_MISSING",
                "审批链不齐: " + detail, null, null);
    }

    /** 重复发布（已 RELEASED/ACTIVE/... 的行不可再发布，版本不可变 §6.6）。 */
    public static PublishBlockedException alreadyImmutable(String status) {
        return new PublishBlockedException("BLUEPRINT_ALREADY_RELEASED",
                "该蓝图版本已 " + status + "，发布版本不可变（§6.6），修订请生成 version+1 新行", null, null);
    }
}
