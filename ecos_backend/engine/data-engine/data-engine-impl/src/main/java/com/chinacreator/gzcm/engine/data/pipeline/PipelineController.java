package com.chinacreator.gzcm.engine.data.pipeline;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pipeline Controller — Pipeline 定义管理与执行。
 * <p>
 * 8 端点出入参全部强类型（XxxSaveDTO / XxxVO / XxxQuery 命名），统一 {@code ApiResponse<T>}。
 * 执行入口走 runtime-task（ITaskManagementService.submitTask + executeTask 分步调用），
 * 返回 taskId 供前端轮询 getTaskStatus(taskId)；执行前置 ABAC 裁决（架构铁律 §2.4）。
 *
 * @author DataBridge Datanet Team
 */
@RestController
@RequestMapping("/api/v1/pipeline")
public class PipelineController {

    private static final Logger log = LoggerFactory.getLogger(PipelineController.class);
    private final PipelineService pipelineService;
    private final PipelineRepository repository;
    private final ITaskManagementService taskManagementService;
    private final PipelineSecurityService securityService;

    public PipelineController(PipelineService pipelineService,
                              PipelineRepository repository,
                              ITaskManagementService taskManagementService,
                              PipelineSecurityService securityService) {
        this.pipelineService = pipelineService;
        this.repository = repository;
        this.taskManagementService = taskManagementService;
        this.securityService = securityService;
    }

    // ── 1. 创建 Pipeline 定义 ──────────────────────────

    @PostMapping("/definitions")
    public ApiResponse<PipelineVO> createDefinition(@RequestBody PipelineSaveDTO dto) {
        PipelineDefinition def = pipelineService.createDefinition(dto);
        PipelineVO vo = pipelineService.toVO(def, true);
        return ApiResponse.success("Pipeline 定义创建成功", vo);
    }

    // ── 2. Pipeline 定义列表（摘要，不含 nodes） ──────────

    @GetMapping("/definitions")
    public ApiResponse<List<PipelineVO>> listDefinitions() {
        List<PipelineVO> result = pipelineService.listDefinitions().stream()
                .map(def -> pipelineService.toVO(def, false))
                .collect(Collectors.toList());
        return ApiResponse.success("查询成功", result);
    }

    // ── 3. Pipeline 定义详情（全量 nodes，供前端画布渲染） ──

    @GetMapping("/definitions/{id}")
    public ApiResponse<PipelineVO> getDefinition(@PathVariable String id) {
        PipelineDefinition def = pipelineService.getDefinition(id);
        return ApiResponse.success("查询成功", pipelineService.toVO(def, true));
    }

    // ── 4. 更新 Pipeline 定义 ────────────────────────────

    @PutMapping("/definitions/{id}")
    public ApiResponse<PipelineVO> updateDefinition(@PathVariable String id,
                                                    @RequestBody PipelineSaveDTO dto) {
        PipelineDefinition def = pipelineService.updateDefinition(id, dto);
        return ApiResponse.success("Pipeline 定义更新成功", pipelineService.toVO(def, true));
    }

    // ── 5. 删除 Pipeline 定义（逻辑删除 → ARCHIVED） ──────

    @DeleteMapping("/definitions/{id}")
    public ApiResponse<Void> deleteDefinition(@PathVariable String id) {
        pipelineService.deleteDefinition(id);
        return ApiResponse.success("Pipeline 定义已删除", null);
    }

    // ── 6. 执行 Pipeline（走 runtime-task 全闭环） ──────

    @PostMapping("/definitions/{id}/execute")
    public ApiResponse<PipelineTaskStatusVO> executeDefinition(@PathVariable String id) {
        // T3: 执行前置 ABAC 裁决（security-engine 不可用默认 DENY）
        pipelineService.checkAbacBeforeExecute(id);

        TaskDescription desc = new TaskDescription();
        desc.setTaskType("PIPELINE");
        desc.setTaskName("Pipeline-" + id);
        Map<String, Object> params = new HashMap<>();
        params.put("definitionId", id);
        desc.setParameters(params);
        desc.setAsync(false); // 同步执行返回结果，前端可改 true 异步轮询

        try {
            String taskId = taskManagementService.submitTask(desc);
            taskManagementService.executeTask(taskId);

            PipelineTaskStatusVO vo = new PipelineTaskStatusVO();
            vo.setTaskId(taskId);
            fillTaskStatus(vo, taskId);

            // 异步审计（不阻塞）
            securityService.auditWrite("PIPELINE_EXECUTE", id, "system");
            return ApiResponse.success("Pipeline execution submitted via runtime-task", vo);
        } catch (ITaskManagementService.TaskManagementException e) {
            log.error("执行 Pipeline 提交失败: id={}, error={}", id, e.getMessage(), e);
            throw new BusinessException("执行 Pipeline 失败: " + e.getMessage());
        }
    }

    // ── 7. 查询 runtime-task 状态（前端轮询） ────────────

    @GetMapping("/tasks/{taskId}/status")
    public ApiResponse<PipelineTaskStatusVO> getTaskStatus(@PathVariable String taskId) {
        TaskStatus status;
        try {
            status = taskManagementService.getTaskStatus(taskId);
        } catch (ITaskManagementService.TaskManagementException e) {
            log.warn("查询任务状态失败: taskId={}, error={}", taskId, e.getMessage());
            throw new NotFoundException("任务状态不存在: " + taskId);
        }
        if (status == null) {
            throw new NotFoundException("任务状态不存在: " + taskId);
        }
        PipelineTaskStatusVO vo = new PipelineTaskStatusVO();
        vo.setTaskId(status.getTaskId());
        fillTaskStatus(vo, taskId);
        return ApiResponse.success("查询成功", vo);
    }

    // ── 8. 查询执行状态（旧端点，按 executionId 查 ecos_pipeline_execution） ─

    @GetMapping("/executions/{id}")
    public ApiResponse<PipelineExecutionVO> getExecution(@PathVariable String id) {
        PipelineExecution exec = repository.findExecutionById(id);
        if (exec == null) {
            throw new NotFoundException("执行记录不存在: " + id);
        }
        return ApiResponse.success("查询成功", pipelineService.toExecutionVO(exec));
    }

    // ── 9. 新增：Pipeline 执行历史（分页） ────────────────

    /**
     * GET /api/v1/pipeline/definitions/{id}/executions?page=x&pageSize=y
     * 返回 ecos_pipeline_execution 分页记录（page/pageSize/total/items）。
     */
    @GetMapping("/definitions/{id}/executions")
    public ApiResponse<PipelineExecutionPageVO> listExecutions(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        // 校验定义存在（NOT_FOUND 语义），ARCHIVED 视为不存在
        PipelineDefinition def = pipelineService.getDefinition(id);
        if ("ARCHIVED".equals(def.getStatus())) {
            throw new NotFoundException("Pipeline 定义已被删除: " + id);
        }
        return ApiResponse.success("查询成功", pipelineService.listExecutions(id, page, pageSize));
    }

    // ==================== 辅助 ====================

    /**
     * 从 runtime-task 状态填充 VO（status/progress/result/ errorMessage 等）。
     */
    private void fillTaskStatus(PipelineTaskStatusVO vo, String taskId) {
        try {
            TaskStatus status = taskManagementService.getTaskStatus(taskId);
            if (status != null) {
                vo.setStatus(status.getStatus() != null ? status.getStatus().name() : "UNKNOWN");
                vo.setStatusMessage(status.getStatusMessage());
                vo.setProgress(status.getProgress());
                vo.setCurrentStepId(status.getCurrentStepId());
                vo.setStartTime(status.getStartTime() != null ? status.getStartTime().getTime() : null);
                vo.setEndTime(status.getEndTime() != null ? status.getEndTime().getTime() : null);
                vo.setResult(status.getResult());
                vo.setErrorMessage(status.getErrorMessage());
            }
        } catch (ITaskManagementService.TaskManagementException e) {
            log.warn("查询 runtime-task 状态失败: taskId={}, error={}", taskId, e.getMessage());
        }
    }
}
