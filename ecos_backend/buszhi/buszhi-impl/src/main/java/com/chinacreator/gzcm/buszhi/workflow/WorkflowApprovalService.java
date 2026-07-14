package com.chinacreator.gzcm.buszhi.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 审批服务 — approve/reject/transfer/addSign。
 */
@Service
public class WorkflowApprovalService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowApprovalService.class);
    private static final AtomicInteger ID_SEQ = new AtomicInteger(4000);

    private final WorkflowApprovalRepository approvalRepo;
    private final WorkflowTaskRepository taskRepo;
    private final WorkflowLogRepository logRepo;
    private final ObjectMapper objectMapper;

    public WorkflowApprovalService(WorkflowApprovalRepository approvalRepo,
                                    WorkflowTaskRepository taskRepo,
                                    WorkflowLogRepository logRepo,
                                    ObjectMapper objectMapper) {
        this.approvalRepo = approvalRepo;
        this.taskRepo = taskRepo;
        this.logRepo = logRepo;
        this.objectMapper = objectMapper;
    }

    private String nextId() { return "appr" + ID_SEQ.incrementAndGet(); }

    /**
     * 审批通过。
     */
    public Map<String, Object> approve(String taskId, Map<String, Object> body) {
        Optional<WorkflowTaskEntity> taskOpt = taskRepo.findById(taskId);
        if (taskOpt.isEmpty()) throw new RuntimeException("WF-009: 任务不存在: " + taskId);

        WorkflowTaskEntity task = taskOpt.get();
        String approver = body != null && body.containsKey("userId")
            ? String.valueOf(body.get("userId")) : task.getAssignee();
        String opinion = body != null && body.containsKey("opinion")
            ? String.valueOf(body.get("opinion")) : "";

        WorkflowApprovalEntity approval = new WorkflowApprovalEntity();
        approval.setId(nextId());
        approval.setTaskId(taskId);
        approval.setInstanceId(task.getInstanceId());
        approval.setApprover(approver);
        approval.setDecision("Approved");
        approval.setOpinion(opinion);
        approval.setFormData(body != null && body.containsKey("formData")
            ? toJson(body.get("formData")) : null);
        approvalRepo.insert(approval);

        // 完成关联任务
        taskRepo.complete(taskId, toJson(Map.of("decision", "Approved", "opinion", opinion)), approver);

        logRepo.log(task.getInstanceId(), task.getNodeId(), "userTask",
            "ApprovalApproved", "审批通过: " + approver, null, null, null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("approvalId", approval.getId());
        result.put("decision", "Approved");
        return result;
    }

    /**
     * 审批驳回。
     */
    public Map<String, Object> reject(String taskId, Map<String, Object> body) {
        Optional<WorkflowTaskEntity> taskOpt = taskRepo.findById(taskId);
        if (taskOpt.isEmpty()) throw new RuntimeException("WF-009: 任务不存在: " + taskId);

        WorkflowTaskEntity task = taskOpt.get();
        String approver = body != null && body.containsKey("userId")
            ? String.valueOf(body.get("userId")) : task.getAssignee();
        String opinion = body != null && body.containsKey("opinion")
            ? String.valueOf(body.get("opinion")) : "";

        WorkflowApprovalEntity approval = new WorkflowApprovalEntity();
        approval.setId(nextId());
        approval.setTaskId(taskId);
        approval.setInstanceId(task.getInstanceId());
        approval.setApprover(approver);
        approval.setDecision("Rejected");
        approval.setOpinion(opinion);
        approvalRepo.insert(approval);

        taskRepo.reject(taskId, toJson(Map.of("decision", "Rejected", "opinion", opinion)));

        logRepo.log(task.getInstanceId(), task.getNodeId(), "userTask",
            "ApprovalRejected", "审批驳回: " + approver, null, null, null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("approvalId", approval.getId());
        result.put("decision", "Rejected");
        return result;
    }

    /**
     * 审批转签。
     */
    public Map<String, Object> transfer(String taskId, Map<String, Object> body) {
        Optional<WorkflowTaskEntity> taskOpt = taskRepo.findById(taskId);
        if (taskOpt.isEmpty()) throw new RuntimeException("WF-009: 任务不存在: " + taskId);

        WorkflowTaskEntity task = taskOpt.get();
        String newAssignee = body != null ? String.valueOf(body.getOrDefault("targetUserId", "")) : "";

        taskRepo.transfer(taskId, newAssignee);

        logRepo.log(task.getInstanceId(), task.getNodeId(), "userTask",
            "ApprovalTransferred", "审批转签 → " + newAssignee, null, null, null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("decision", "Transferred");
        result.put("newAssignee", newAssignee);
        return result;
    }

    /**
     * 加签。
     */
    public Map<String, Object> addSign(String taskId, Map<String, Object> body) {
        Optional<WorkflowTaskEntity> taskOpt = taskRepo.findById(taskId);
        if (taskOpt.isEmpty()) throw new RuntimeException("WF-009: 任务不存在: " + taskId);

        // 简化实现：记录加签
        logRepo.log(taskOpt.get().getInstanceId(), taskOpt.get().getNodeId(), "userTask",
            "AddSign", "加签请求: " + body, null, null, null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("status", "AddSignRequested");
        return result;
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return String.valueOf(obj); }
    }
}
