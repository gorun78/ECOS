package com.chinacreator.gzcm.workspace.scenario;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 动作五必填服务端校验（卷07 F07-10-2/3，前端只做即时提示）。
 * <p>负责人/截止日/预期影响/控制指标取自请求体；<b>审批人取自主体（JWT）而非请求体</b>（禁前端传 userId）。
 * 任一缺失 → {@link ActionValidationException}（400，逐字段）。</p>
 */
@Component
public class ActionProposalValidator {

    public void validate(ActionProposal proposal, String approverSubject) {
        List<String> missing = new ArrayList<>();
        if (proposal == null) {
            missing.add("owner");
            missing.add("dueDate");
            missing.add("approver");
            missing.add("expectedImpact");
            missing.add("controlMetric");
            throw new ActionValidationException(missing);
        }
        if (isBlank(proposal.getOwnerUserId())) {
            missing.add("owner");
        }
        if (proposal.getDueDate() == null) {
            missing.add("dueDate");
        }
        if (isBlank(approverSubject)) {
            missing.add("approver");
        }
        if (proposal.getExpectedImpact() == null) {
            missing.add("expectedImpact");
        }
        if (isBlank(proposal.getControlMetric())) {
            missing.add("controlMetric");
        }
        if (!missing.isEmpty()) {
            throw new ActionValidationException(missing);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
