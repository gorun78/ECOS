package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.service.IGraphService;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyGraphVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 本体知识图谱 REST API — T16-5 强类型返回。
 *
 * <p>底层 {@code IGraphService} 位于 {@code runtime/common-api}（横切底座，
 * 接口不可改）。本控制器**不动 service 签名**，仅在 Controller 内
 * Map → {@link OntologyGraphVO} 的 Jackson 转换，API 输出契约严格等价
 * （POJO 序列化 == Map 序列化）。
 *
 * <p>T16-5: Graph 节点/边动态结构豁免 — {@code nodes / edges / tracePath}
 * 保持 Object 类型（Neo4j 节点属性随本体演进，动态 Map 集合）。
 */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(com.chinacreator.gzcm.common.service.IGraphService.class)
@RequestMapping("/api/v1/engine/ontology/graph")
public class OntologyGraphController {

    private static final Logger log = LoggerFactory.getLogger(OntologyGraphController.class);

    /**
     * Controller 层 Map → VO 转换用的 Jackson Mapper（static 单例，零开销）。
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final IGraphService graphService;

    public OntologyGraphController(IGraphService graphService) {
        this.graphService = graphService;
    }

    /**
     * 获取本体子图（节点 + 边）。
     * <p>异常分支返回 {@code nodes/edges} 空列表 + {@code message} 提示（与既有 Map 行为一致）。
     */
    @GetMapping("/{ontologyId}")
    public ApiResponse<OntologyGraphVO> getOntologyGraph(@PathVariable String ontologyId) {
        try {
            Map<String, Object> subgraph = graphService.getSubgraph(ontologyId);
            return ApiResponse.success(toVO(subgraph));
        } catch (Exception e) {
            log.warn("Graph query failed for ontology {}: {}", ontologyId, e.getMessage());
            return ApiResponse.success(emptyVO(null, "Graph service unavailable: " + e.getMessage()));
        }
    }

    /**
     * 全图查询（Cypher {@code MATCH (n) RETURN n LIMIT 100}）。
     * <p>结果节点放 {@code nodes}，{@code edges} 空列表（与既有 Map 行为一致）。
     */
    @GetMapping("/full")
    public ApiResponse<OntologyGraphVO> getFullGraph() {
        try {
            List<Map<String, Object>> results = graphService.query("MATCH (n) RETURN n LIMIT 100", Map.of());
            OntologyGraphVO vo = new OntologyGraphVO();
            // T16-5: Graph 节点动态结构豁免 — results 为 Neo4j 节点 Map 列表，保持 Object
            vo.setNodes(results);
            vo.setEdges(Collections.emptyList());
            return ApiResponse.success(vo);
        } catch (Exception e) {
            log.warn("Full graph query failed: {}", e.getMessage());
            return ApiResponse.success(emptyVO(null, "Graph service unavailable: " + e.getMessage()));
        }
    }

    /**
     * 节点追溯（以 nodeId 为中心的子图 + 追溯路径）。
     * <p>异常分支额外返回 {@code tracePath} 空列表（与既有 Map 行为一致）。
     */
    @GetMapping("/trace/{nodeId}")
    public ApiResponse<OntologyGraphVO> traceNode(@PathVariable String nodeId) {
        try {
            Map<String, Object> subgraph = graphService.getSubgraph(nodeId);
            OntologyGraphVO vo = toVO(subgraph);
            // 既有 Map 的 trace 分支不额外放 tracePath（getSubgraph 内已含），保持等价
            return ApiResponse.success(vo);
        } catch (Exception e) {
            log.warn("Node trace failed for {}: {}", nodeId, e.getMessage());
            return ApiResponse.success(emptyVO(
                Collections.emptyList(), "Graph service unavailable: " + e.getMessage()));
        }
    }

    // ═══════════════ 内部 Map → VO 转换（Jackson）═══════════════

    /** IGraphService.getSubgraph 返回的 Map 行 → OntologyGraphVO（字段名等价）。 */
    private OntologyGraphVO toVO(Map<String, Object> row) {
        if (row == null) {
            return new OntologyGraphVO();
        }
        return MAPPER.convertValue(row, OntologyGraphVO.class);
    }

    /**
     * 构造异常兜底 VO（nodes/edges 空列表 + message 提示）。
     *
     * @param tracePath   traceNode 端点额外填充（null 不输出）；其他端点传 null
     * @param message     错误提示
     */
    private OntologyGraphVO emptyVO(List<?> tracePath, String message) {
        OntologyGraphVO vo = new OntologyGraphVO();
        // T16-5: Graph 节点动态结构豁免 — 空列表以 Object 承载
        vo.setNodes(Collections.emptyList());
        vo.setEdges(Collections.emptyList());
        if (tracePath != null) {
            vo.setTracePath(tracePath);
        }
        vo.setMessage(message);
        return vo;
    }
}
