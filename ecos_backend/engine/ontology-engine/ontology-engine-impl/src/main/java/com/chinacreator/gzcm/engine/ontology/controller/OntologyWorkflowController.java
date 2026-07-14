package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowApprovalService;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowInstanceService;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/engine/ontology/workflow")
public class OntologyWorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowInstanceService instanceService;
    private final WorkflowApprovalService approvalService;

    public OntologyWorkflowController(WorkflowService workflowService,
                                      WorkflowInstanceService instanceService,
                                      WorkflowApprovalService approvalService) {
        this.workflowService = workflowService;
        this.instanceService = instanceService;
        this.approvalService = approvalService;
    }

    @GetMapping("/definitions")
    public ApiResponse<Map<String, Object>> listDefinitions(
            @RequestParam(defaultValue = "50") int pageSize) {
        List<Map<String, Object>> list = workflowService.listWorkflows(pageSize);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", list);
        result.put("total", workflowService.totalCount());
        return ApiResponse.success(result);
    }

    @PostMapping("/definitions")
    public ApiResponse<Map<String, Object>> createDefinition(@RequestBody Map<String, Object> body) {
        Map<String, Object> wf = workflowService.createWorkflow(body);
        return ApiResponse.success(wf);
    }

    @GetMapping("/instances")
    public ApiResponse<Map<String, Object>> listInstances(
            @RequestParam(defaultValue = "50") int limit) {
        List<Map<String, Object>> list = instanceService.listInstances(limit);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", list);
        result.put("total", list.size());
        return ApiResponse.success(result);
    }

    @PostMapping("/instances")
    public ApiResponse<Map<String, Object>> startInstance(@RequestBody Map<String, Object> body) {
        String workflowId = (String) body.get("workflowId");
        if (workflowId == null || workflowId.isBlank()) {
            return ApiResponse.badRequest("workflowId is required");
        }
        return instanceService.startInstance(workflowId, body)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.notFound("Workflow " + workflowId + " not found"));
    }

    @GetMapping("/instances/{id}")
    public ApiResponse<Map<String, Object>> getInstance(@PathVariable String id) {
        return instanceService.getInstance(id)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.notFound("Instance " + id + " not found"));
    }

    @PostMapping("/instances/{id}/approve")
    public ApiResponse<Map<String, Object>> approve(@PathVariable String id,
                                                     @RequestBody Map<String, Object> body) {
        Map<String, Object> result = approvalService.approve(id, body);
        return ApiResponse.success(result);
    }

    @PostMapping("/instances/{id}/reject")
    public ApiResponse<Map<String, Object>> reject(@PathVariable String id,
                                                    @RequestBody Map<String, Object> body) {
        Map<String, Object> result = approvalService.reject(id, body);
        return ApiResponse.success(result);
    }
}
