package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive2.model.CognitivePipeline;
import com.chinacreator.gzcm.engine.cognitive2.model.CognitivePipelineNode;
import com.chinacreator.gzcm.engine.cognitive2.model.NodeType;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitivePipelineExecutor;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitivePipelineExecutor.PipelineExecution;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitivePipelineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.util.*;

/**
 * 认知管线编排 REST API — 前缀 /api/v1/cognitive/pipeline
 *
 * <p>四端点：创建 / 列表 / 执行 / 查询执行状态</p>
 *
 * <p>管线定义已持久化到 PostgreSQL（kb_cognitive_pipeline 表），
 * 由 {@link CognitivePipelineRepository} 管理 CRUD。
 * 执行记录（PipelineExecution）仍为进程内状态（实时状态机，不持久化）。</p>
 */
@RestController
@RequestMapping("/api/v1/cognitive/pipeline")
public class CognitivePipelineController {

    private static final Logger log = LoggerFactory.getLogger(CognitivePipelineController.class);

    @Autowired
    private CognitivePipelineExecutor executor;

    @Autowired
    private CognitivePipelineRepository pipelineRepository;

    /** POST / — 创建认知管线定义 */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        try {
            String name = (String) body.get("name");
            String description = (String) body.getOrDefault("description", "");

            if (name == null || name.isEmpty()) {
                return ApiResponse.badRequest("name is required");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodeMaps = (List<Map<String, Object>>) body.get("nodes");
            if (nodeMaps == null || nodeMaps.isEmpty()) {
                return ApiResponse.badRequest("nodes is required and must not be empty");
            }

            List<CognitivePipelineNode> nodes = new ArrayList<>();
            for (Map<String, Object> nm : nodeMaps) {
                String nodeId = (String) nm.get("nodeId");
                String nodeTypeStr = (String) nm.get("nodeType");
                NodeType nodeType = NodeType.fromString(nodeTypeStr);

                if (nodeId == null || nodeType == null) {
                    return ApiResponse.badRequest("Invalid node: nodeId=" + nodeId + " nodeType=" + nodeTypeStr);
                }

                Object configObj = nm.get("config");
                String config = configObj != null ? configObj.toString() : "{}";

                @SuppressWarnings("unchecked")
                List<String> dependsOn = (List<String>) nm.get("dependsOn");

                nodes.add(new CognitivePipelineNode(nodeId, nodeType, config, dependsOn));
            }

            String pipelineId = UUID.randomUUID().toString().replace("-", "");
            CognitivePipeline pipeline = new CognitivePipeline();
            pipeline.setId(pipelineId);
            pipeline.setName(name);
            pipeline.setDescription(description);
            pipeline.setStatus("DRAFT");
            pipeline.setNodes(nodes);
            pipeline.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            pipeline.setCreatedBy("system");

            // 持久化到 PostgreSQL（替代原 ConcurrentHashMap 内存存储）
            pipelineRepository.save(pipeline);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("pipelineId", pipelineId);
            result.put("name", name);
            result.put("nodeCount", nodes.size());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to create pipeline", e);
            return ApiResponse.internalError("Failed to create pipeline: " + e.getMessage());
        }
    }

    /** GET / — 列管线定义（从 PostgreSQL 查询） */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        List<CognitivePipeline> pipelines = pipelineRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (CognitivePipeline p : pipelines) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("description", p.getDescription());
            m.put("nodeCount", p.getNodes() != null ? p.getNodes().size() : 0);
            m.put("createdAt", p.getCreatedAt());
            result.add(m);
        }
        return ApiResponse.success(result);
    }

    /** POST /{id}/execute — 执行管线（从 PostgreSQL 加载管线定义） */
    @PostMapping("/{id}/execute")
    public ApiResponse<Map<String, Object>> execute(@PathVariable String id) {
        try {
            CognitivePipeline pipeline = pipelineRepository.findById(id).orElse(null);
            if (pipeline == null) {
                return ApiResponse.notFound("Pipeline not found: " + id);
            }

            PipelineExecution exec = executor.execute(pipeline);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("execId", exec.getExecId());
            result.put("pipelineId", exec.getPipelineId());
            result.put("status", exec.getStatus());
            result.put("startTime", exec.getStartTime());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to execute pipeline", e);
            return ApiResponse.internalError("Failed to execute: " + e.getMessage());
        }
    }

    /**
     * DELETE /{id} — 逻辑删除管线定义（is_deleted=1，铁律 4.8）。
     * <p>调用方零改：原无 DELETE 端点，此为新增端点。</p>
     */
    @DeleteMapping("/{id}")
    public ApiResponse<String> delete(@PathVariable String id) {
        int rows = pipelineRepository.logicalDelete(id);
        if (rows > 0) {
            log.info("Pipeline logical deleted: {}", id);
            return ApiResponse.success("Pipeline " + id + " 已删除");
        }
        return ApiResponse.notFound("Pipeline not found: " + id);
    }

    /** GET /{id}/execution/{execId} — 查询执行状态 */
    @GetMapping("/{id}/execution/{execId}")
    public ApiResponse<Map<String, Object>> getExecution(@PathVariable String id,
                                                          @PathVariable String execId) {
        PipelineExecution exec = executor.getExecution(execId);
        if (exec == null) {
            return ApiResponse.notFound("Execution not found: " + execId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("execId", exec.getExecId());
        result.put("pipelineId", exec.getPipelineId());
        result.put("status", exec.getStatus());
        result.put("nodeStatuses", exec.getNodeStatuses());
        result.put("startTime", exec.getStartTime());
        result.put("endTime", exec.getEndTime());
        result.put("logs", exec.getLogs());
        return ApiResponse.success(result);
    }
}
