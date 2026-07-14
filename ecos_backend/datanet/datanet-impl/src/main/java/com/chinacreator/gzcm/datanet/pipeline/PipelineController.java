package com.chinacreator.gzcm.datanet.pipeline;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Pipeline Controller — Pipeline 定义管理与执行。
 *
 * @author DataBridge Datanet Team
 */
@RestController
@RequestMapping({"/api/v1/pipeline", "/api/pipeline"})
public class PipelineController {

    private static final Logger log = LoggerFactory.getLogger(PipelineController.class);
    private final PipelineService pipelineService;
    private final PipelineExecutionService executionService;
    private final PipelineRepository repository;

    public PipelineController(PipelineService pipelineService,
                               PipelineExecutionService executionService,
                               PipelineRepository repository) {
        this.pipelineService = pipelineService;
        this.executionService = executionService;
        this.repository = repository;
    }

    // ── 1. 创建 Pipeline 定义 ──────────────────────────

    @PostMapping("/definitions")
    public ApiResponse<Map<String, Object>> createDefinition(@RequestBody Map<String, Object> body) {
        try {
            PipelineDefinition def = pipelineService.createDefinition(body);
            List<PipelineNode> nodes;
            try {
                nodes = repository.findNodesByDefinitionId(def.getId());
            } catch (Exception e) {
                log.warn("查询 Pipeline 节点失败（表可能未初始化）: {}", e.getMessage());
                nodes = Collections.emptyList();
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", def.getId());
            result.put("name", def.getName());
            result.put("description", def.getDescription());
            result.put("status", def.getStatus());
            result.put("nodes", nodes);
            result.put("createdAt", def.getCreatedAt());
            result.put("updatedAt", def.getUpdatedAt());

            return ApiResponse.success("Pipeline 定义创建成功", result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("创建 Pipeline 定义失败", e);
            return ApiResponse.internalError("创建 Pipeline 定义失败: " + e.getMessage());
        }
    }

    // ── 2. Pipeline 定义列表 ────────────────────────────

    @GetMapping("/definitions")
    public ApiResponse<List<Map<String, Object>>> listDefinitions() {
        try {
            List<PipelineDefinition> defs = pipelineService.listDefinitions();
            List<Map<String, Object>> result = new ArrayList<>();
            for (PipelineDefinition def : defs) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", def.getId());
                m.put("name", def.getName());
                m.put("description", def.getDescription());
                m.put("status", def.getStatus());
                m.put("createdAt", def.getCreatedAt());
                m.put("updatedAt", def.getUpdatedAt());
                result.add(m);
            }
            return ApiResponse.success("查询成功", result);
        } catch (Exception e) {
            log.error("查询 Pipeline 定义列表失败", e);
            return ApiResponse.internalError("查询 Pipeline 定义列表失败: " + e.getMessage());
        }
    }

    // ── 3. Pipeline 定义详情 ────────────────────────────

    @GetMapping("/definitions/{id}")
    public ApiResponse<Map<String, Object>> getDefinition(@PathVariable String id) {
        try {
            PipelineDefinition def = pipelineService.getDefinition(id);
            List<PipelineNode> nodes = repository.findNodesByDefinitionId(id);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", def.getId());
            result.put("name", def.getName());
            result.put("description", def.getDescription());
            result.put("status", def.getStatus());
            result.put("nodes", nodes);
            result.put("createdAt", def.getCreatedAt());
            result.put("updatedAt", def.getUpdatedAt());

            return ApiResponse.success("查询成功", result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (Exception e) {
            log.error("查询 Pipeline 定义详情失败: id={}", id, e);
            return ApiResponse.internalError("查询 Pipeline 定义详情失败: " + e.getMessage());
        }
    }

    // ── 4. 更新 Pipeline 定义 ────────────────────────────

    @PutMapping("/definitions/{id}")
    public ApiResponse<Map<String, Object>> updateDefinition(@PathVariable String id,
                                                              @RequestBody Map<String, Object> body) {
        try {
            PipelineDefinition def = pipelineService.updateDefinition(id, body);
            List<PipelineNode> nodes = repository.findNodesByDefinitionId(id);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", def.getId());
            result.put("name", def.getName());
            result.put("description", def.getDescription());
            result.put("status", def.getStatus());
            result.put("nodes", nodes);
            result.put("createdAt", def.getCreatedAt());
            result.put("updatedAt", def.getUpdatedAt());

            return ApiResponse.success("Pipeline 定义更新成功", result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("更新 Pipeline 定义失败: id={}", id, e);
            return ApiResponse.internalError("更新 Pipeline 定义失败: " + e.getMessage());
        }
    }

    // ── 5. 删除 Pipeline 定义 ────────────────────────────

    @DeleteMapping("/definitions/{id}")
    public ApiResponse<Map<String, Object>> deleteDefinition(@PathVariable String id) {
        try {
            pipelineService.deleteDefinition(id);
            return ApiResponse.success("Pipeline 定义已删除", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (Exception e) {
            log.error("删除 Pipeline 定义失败: id={}", id, e);
            return ApiResponse.internalError("删除 Pipeline 定义失败: " + e.getMessage());
        }
    }

    // ── 6. 执行 Pipeline ────────────────────────────────

    @PostMapping("/definitions/{id}/execute")
    public ApiResponse<Map<String, Object>> executeDefinition(@PathVariable String id) {
        try {
            PipelineExecution exec = executionService.executePipeline(id);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("executionId", exec.getId());
            result.put("status", exec.getStatus());
            result.put("startedAt", exec.getStartedAt());
            return ApiResponse.success("Pipeline 执行已启动", result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("执行 Pipeline 失败: id={}", id, e);
            return ApiResponse.internalError("执行 Pipeline 失败: " + e.getMessage());
        }
    }

    // ── 7. 查询执行状态 ─────────────────────────────────

    @GetMapping("/executions/{id}")
    public ApiResponse<PipelineExecution> getExecution(@PathVariable String id) {
        try {
            PipelineExecution exec = repository.findExecutionById(id);
            if (exec == null) {
                return ApiResponse.notFound("执行记录不存在: " + id);
            }
            return ApiResponse.success("查询成功", exec);
        } catch (Exception e) {
            log.error("查询执行状态失败: id={}", id, e);
            return ApiResponse.internalError("查询执行状态失败: " + e.getMessage());
        }
    }
}
