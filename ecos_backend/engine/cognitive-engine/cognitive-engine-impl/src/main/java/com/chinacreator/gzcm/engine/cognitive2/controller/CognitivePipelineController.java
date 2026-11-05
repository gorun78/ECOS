package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive2.dto.CognitivePipelineCreateDTO;
import com.chinacreator.gzcm.engine.cognitive2.dto.CognitivePipelineCreateVO;
import com.chinacreator.gzcm.engine.cognitive2.dto.CognitivePipelineDetailVO;
import com.chinacreator.gzcm.engine.cognitive2.dto.CognitivePipelineExecutionStatusVO;
import com.chinacreator.gzcm.engine.cognitive2.dto.CognitivePipelineExecutionVO;
import com.chinacreator.gzcm.engine.cognitive2.dto.CognitivePipelineSummaryVO;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 认知管线编排 REST API — 前缀 /api/v1/cognitive/pipeline
 *
 * <p>六端点：创建 / 列表 / 详情 / 删除 / 执行 / 查询执行状态</p>
 *
 * <p>管线定义已持久化到 PostgreSQL（kb_cognitive_pipeline 表），
 * 由 {@link CognitivePipelineRepository} 管理 CRUD。
 * 执行记录（PipelineExecution）仍为进程内状态（实时状态机，不持久化）。</p>
 *
 * <p>DI-2 #147EJ：五个响应面与创建请求体由泛型 Map 载荷改为强类型 VO/DTO
 * （wire 键序与条件键由 {@code CognitivePipelineWireShapeTest} 锁定）。</p>
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
    public ApiResponse<CognitivePipelineCreateVO> create(@RequestBody CognitivePipelineCreateDTO body) {
        try {
            String name = body.getName();
            String description = body.getDescription() != null ? body.getDescription() : "";

            if (name == null || name.isEmpty()) {
                return ApiResponse.badRequest("name is required");
            }

            List<CognitivePipelineCreateDTO.NodeSpec> nodeMaps = body.getNodes();
            if (nodeMaps == null || nodeMaps.isEmpty()) {
                return ApiResponse.badRequest("nodes is required and must not be empty");
            }

            List<CognitivePipelineNode> nodes = new ArrayList<>();
            for (CognitivePipelineCreateDTO.NodeSpec nm : nodeMaps) {
                String nodeId = nm.getNodeId();
                String nodeTypeStr = nm.getNodeType();
                NodeType nodeType = NodeType.fromString(nodeTypeStr);

                if (nodeId == null || nodeType == null) {
                    return ApiResponse.badRequest("Invalid node: nodeId=" + nodeId + " nodeType=" + nodeTypeStr);
                }

                Object configObj = nm.getConfig();
                String config = configObj != null ? configObj.toString() : "{}";

                List<String> dependsOn = nm.getDependsOn();

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

            return ApiResponse.success(
                    new CognitivePipelineCreateVO(pipelineId, name, nodes.size()));
        } catch (Exception e) {
            log.error("Failed to create pipeline", e);
            return ApiResponse.internalError("Failed to create pipeline");
        }
    }

    /** GET / — 列管线定义（从 PostgreSQL 查询） */
    @GetMapping
    public ApiResponse<List<CognitivePipelineSummaryVO>> list() {
        List<CognitivePipeline> pipelines = pipelineRepository.findAll();
        List<CognitivePipelineSummaryVO> result = new ArrayList<>();
        for (CognitivePipeline p : pipelines) {
            result.add(new CognitivePipelineSummaryVO(p.getId(), p.getName(), p.getDescription(),
                    p.getNodes() != null ? p.getNodes().size() : 0, p.getCreatedAt()));
        }
        return ApiResponse.success(result);
    }

    /** GET /{id} — 查询管线详情（从 PostgreSQL 查询） */
    @GetMapping("/{id}")
    public ApiResponse<CognitivePipelineDetailVO> get(@PathVariable String id) {
        try {
            CognitivePipeline pipeline = pipelineRepository.findById(id).orElse(null);
            if (pipeline == null) {
                return ApiResponse.notFound("Pipeline not found: " + id);
            }

            return ApiResponse.success(new CognitivePipelineDetailVO(pipeline.getId(), pipeline.getName(),
                    pipeline.getDescription(), pipeline.getStatus(), pipeline.getCreatedBy(),
                    pipeline.getNodes(), pipeline.getCreatedAt()));
        } catch (Exception e) {
            log.error("Failed to get pipeline: {}", id, e);
            return ApiResponse.internalError("Failed to get pipeline");
        }
    }

    /** POST /{id}/execute — 执行管线（从 PostgreSQL 加载管线定义） */
    @PostMapping("/{id}/execute")
    public ApiResponse<CognitivePipelineExecutionVO> execute(@PathVariable String id) {
        try {
            CognitivePipeline pipeline = pipelineRepository.findById(id).orElse(null);
            if (pipeline == null) {
                return ApiResponse.notFound("Pipeline not found: " + id);
            }

            PipelineExecution exec = executor.execute(pipeline);

            return ApiResponse.success(new CognitivePipelineExecutionVO(exec.getExecId(),
                    exec.getPipelineId(), exec.getStatus(), exec.getStartTime()));
        } catch (Exception e) {
            log.error("Failed to execute pipeline", e);
            return ApiResponse.internalError("Failed to execute");
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
    public ApiResponse<CognitivePipelineExecutionStatusVO> getExecution(@PathVariable String id,
                                                                        @PathVariable String execId) {
        PipelineExecution exec = executor.getExecution(execId);
        if (exec == null) {
            return ApiResponse.notFound("Execution not found: " + execId);
        }

        return ApiResponse.success(new CognitivePipelineExecutionStatusVO(exec.getExecId(),
                exec.getPipelineId(), exec.getStatus(), exec.getNodeStatuses(),
                exec.getStartTime(), exec.getEndTime(), exec.getLogs()));
    }
}
