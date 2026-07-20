package com.chinacreator.gzcm.services.agent.controller;

import com.chinacreator.gzcm.services.agent.runtime.model.AgentMetricsSummary;
import com.chinacreator.gzcm.services.agent.runtime.model.ApprovalRequest;
import com.chinacreator.gzcm.services.agent.runtime.model.ApprovalResult;
import com.chinacreator.gzcm.services.agent.runtime.model.CollaborationMode;
import com.chinacreator.gzcm.services.agent.runtime.model.EvaluationScore;
import com.chinacreator.gzcm.services.agent.runtime.model.ExecutionPlan;
import com.chinacreator.gzcm.services.agent.runtime.model.ExecutionResult;
import com.chinacreator.gzcm.services.agent.runtime.model.ExecutionTask;
import com.chinacreator.gzcm.services.agent.runtime.model.Goal;
import com.chinacreator.gzcm.services.agent.runtime.model.GovernanceDecision;
import com.chinacreator.gzcm.services.agent.runtime.model.GovernancePolicy;
import com.chinacreator.gzcm.services.agent.runtime.model.MemoryContext;
import com.chinacreator.gzcm.services.agent.runtime.model.MemoryQuery;
import com.chinacreator.gzcm.services.agent.runtime.model.MemoryRecord;
import com.chinacreator.gzcm.services.agent.runtime.model.Mission;
import com.chinacreator.gzcm.services.agent.runtime.model.OrchestrationPlan;
import com.chinacreator.gzcm.services.agent.runtime.model.OrchestrationResult;
import com.chinacreator.gzcm.services.agent.runtime.model.ReflectionResult;
import com.chinacreator.gzcm.services.agent.runtime.model.RiskLevel;
import com.chinacreator.gzcm.services.agent.runtime.model.ToolDefinition;
import com.chinacreator.gzcm.services.agent.runtime.approval.ApprovalService;
import com.chinacreator.gzcm.services.agent.runtime.evaluator.EvaluatorService;
import com.chinacreator.gzcm.services.agent.runtime.executor.ExecutorService;
import com.chinacreator.gzcm.services.agent.runtime.governance.GovernanceService;
import com.chinacreator.gzcm.services.agent.runtime.memory.MemoryService;
import com.chinacreator.gzcm.services.agent.runtime.orchestration.OrchestrationService;
import com.chinacreator.gzcm.services.agent.runtime.planner.PlannerService;
import com.chinacreator.gzcm.services.agent.runtime.reflection.ReflectionService;
import com.chinacreator.gzcm.services.agent.runtime.telemetry.TelemetryService;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolRouterService;
import com.chinacreator.gzcm.common.base.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/agent-runtime")
public class AgentRuntimeController {

    @Autowired private PlannerService plannerService;
    @Autowired private ExecutorService executorService;
    @Autowired private ToolRouterService toolRouterService;
    @Autowired private MemoryService memoryService;
    @Autowired private EvaluatorService evaluatorService;
    @Autowired private ReflectionService reflectionService;
    @Autowired private GovernanceService governanceService;
    @Autowired private ApprovalService approvalService;
    @Autowired private TelemetryService telemetryService;
    @Autowired private OrchestrationService orchestrationService;

    @PostMapping("/plans")
    public ApiResponse<ExecutionPlan> createPlan(@RequestBody Goal goal) {
        return ApiResponse.success(plannerService.createPlan(goal));
    }

    @PostMapping("/plans/{planId}/decompose")
    public ApiResponse<ExecutionPlan> decompose(@PathVariable String planId, @RequestBody ExecutionPlan plan) {
        return ApiResponse.success(plannerService.decompose(plan));
    }

    @PostMapping("/tasks/{taskId}/execute")
    public ApiResponse<ExecutionResult> executeTask(@PathVariable String taskId, @RequestBody ExecutionTask task) {
        return ApiResponse.success(executorService.execute(task));
    }

    @PostMapping("/tasks/{taskId}/execute-with-tools")
    public ApiResponse<ExecutionResult> executeWithTools(@PathVariable String taskId, @RequestBody ExecutionTask task, @RequestParam List<String> toolIds) {
        return ApiResponse.success(executorService.executeWithTools(task, toolIds));
    }

    @GetMapping("/tools")
    public ApiResponse<List<ToolDefinition>> getAvailableTools(@RequestParam String agentId) {
        return ApiResponse.success(toolRouterService.getAvailableTools(agentId));
    }

    @PostMapping("/tools/resolve")
    public ApiResponse<ToolDefinition> resolveTool(@RequestParam String actionType, @RequestBody Map<String, Object> params) {
        return ApiResponse.success(toolRouterService.resolveTool(actionType, params));
    }

    @PostMapping("/memory")
    public ApiResponse<Void> storeMemory(@RequestBody MemoryRecord record) {
        memoryService.store(record);
        return ApiResponse.success(null);
    }

    @PostMapping("/memory/retrieve")
    public ApiResponse<List<MemoryRecord>> retrieveMemory(@RequestParam String agentId, @RequestBody MemoryQuery query) {
        return ApiResponse.success(memoryService.retrieve(agentId, query));
    }

    @GetMapping("/memory/context")
    public ApiResponse<MemoryContext> buildContext(@RequestParam String agentId, @RequestParam String sessionId) {
        return ApiResponse.success(memoryService.buildContext(agentId, sessionId));
    }

    @PostMapping("/evaluate")
    public ApiResponse<EvaluationScore> evaluate(@RequestBody ExecutionResult result, @RequestParam String goalId) {
        Goal goal = new Goal(goalId, "", 0);
        return ApiResponse.success(evaluatorService.evaluate(result, goal));
    }

    @PostMapping("/reflect")
    public ApiResponse<ReflectionResult> reflect(@RequestBody ExecutionResult result, @RequestBody EvaluationScore score) {
        return ApiResponse.success(reflectionService.reflect(result, score));
    }

    @PostMapping("/governance/check")
    public ApiResponse<GovernanceDecision> checkGovernance(@RequestBody GovernancePolicy policy, @RequestBody ExecutionTask task) {
        return ApiResponse.success(governanceService.check(policy, task));
    }

    @GetMapping("/governance/policies")
    public ApiResponse<List<GovernancePolicy>> getPolicies(@RequestParam String agentId) {
        return ApiResponse.success(governanceService.getPolicies(agentId));
    }

    @PostMapping("/approval/request")
    public ApiResponse<ApprovalRequest> requestApproval(@RequestBody ExecutionTask task, @RequestParam RiskLevel riskLevel) {
        return ApiResponse.success(approvalService.requestApproval(task, riskLevel));
    }

    @PostMapping("/approval/{approvalId}/process")
    public ApiResponse<ApprovalResult> processApproval(@PathVariable String approvalId, @RequestParam boolean approved, @RequestParam String comment) {
        return ApiResponse.success(approvalService.processApproval(approvalId, approved, comment));
    }

    @GetMapping("/telemetry/{agentId}")
    public ApiResponse<AgentMetricsSummary> getMetrics(@PathVariable String agentId) {
        return ApiResponse.success(telemetryService.getMetrics(agentId));
    }

    @PostMapping("/orchestration/plan")
    public ApiResponse<OrchestrationPlan> planOrchestration(@RequestBody Mission mission, @RequestParam CollaborationMode mode) {
        return ApiResponse.success(orchestrationService.plan(mission, mode));
    }

    @PostMapping("/orchestration/execute")
    public ApiResponse<OrchestrationResult> executeOrchestration(@RequestBody OrchestrationPlan plan) {
        return ApiResponse.success(orchestrationService.execute(plan));
    }

    @PostMapping("/tools/{code}/execute")
    public ApiResponse<Map<String, Object>> executeTool(@PathVariable String code, @RequestBody Map<String, Object> params) {
        Object result = toolRouterService.executeTool(code, params);
        return ApiResponse.success(Map.of("result", result, "toolCode", code));
    }
}
