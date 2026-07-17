package com.chinacreator.gzcm.services.agent.runtime.approval;

import com.chinacreator.gzcm.services.agent.runtime.model.ApprovalRequest;
import com.chinacreator.gzcm.services.agent.runtime.model.ApprovalResult;
import com.chinacreator.gzcm.services.agent.runtime.model.ExecutionTask;
import com.chinacreator.gzcm.services.agent.runtime.model.RiskLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ApprovalServiceImpl implements ApprovalService {
    private static final Logger log = LoggerFactory.getLogger(ApprovalServiceImpl.class);
    private final ConcurrentHashMap<String, ApprovalRequest> pending = new ConcurrentHashMap<>();

    @Override
    public ApprovalRequest requestApproval(ExecutionTask task, RiskLevel riskLevel) {
        log.info("Approval requested for task {} risk {}", task.getId(), riskLevel);
        ApprovalRequest req = new ApprovalRequest();
        req.setId(UUID.randomUUID().toString());
        req.setTaskId(task.getId());
        req.setRiskLevel(riskLevel);
        req.setRequestedAt(Instant.now());
        if (riskLevel == RiskLevel.L1) {
            req.setStatus("AUTO_APPROVED");
        } else {
            req.setStatus("PENDING");
            pending.put(req.getId(), req);
        }
        return req;
    }

    @Override
    public ApprovalResult processApproval(String approvalId, boolean approved, String comment) {
        log.info("Processing approval {} approved={}", approvalId, approved);
        ApprovalRequest req = pending.remove(approvalId);
        if (req != null) {
            req.setStatus(approved ? "APPROVED" : "REJECTED");
        }
        return new ApprovalResult(approvalId, approved, comment);
    }
}
