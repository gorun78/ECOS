package com.chinacreator.gzcm.services.agent.runtime.approval;

import com.chinacreator.gzcm.services.agent.runtime.model.ApprovalRequest;
import com.chinacreator.gzcm.services.agent.runtime.model.ApprovalResult;
import com.chinacreator.gzcm.services.agent.runtime.model.ExecutionTask;
import com.chinacreator.gzcm.services.agent.runtime.model.RiskLevel;

public interface ApprovalService {
    ApprovalRequest requestApproval(ExecutionTask task, RiskLevel riskLevel);
    ApprovalResult processApproval(String approvalId, boolean approved, String comment);
}
