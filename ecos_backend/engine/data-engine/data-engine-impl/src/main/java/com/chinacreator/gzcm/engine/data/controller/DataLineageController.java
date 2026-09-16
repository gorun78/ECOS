package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.service.DataLineageService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 数据血缘 REST API — 表级/字段级 SQL 血缘解析与持久化拓扑。
 * <p>
 * 端点：
 * <ul>
 *   <li>GET  /api/v1/engine/data/lineage                      — 按表名查询单表字段级血缘（实时解析）</li>
 *   <li>GET  /api/v1/engine/data/lineage/pipeline/{taskId}    — 按管道任务查询血缘（实时解析）</li>
 *   <li>GET  /api/v1/engine/data/lineage/topology             — 查询持久化拓扑（前端"重新生成"后秒开）</li>
 *   <li>GET  /api/v1/engine/data/lineage/topology/rebuild?limit=N — 重建并持久化全量血缘（同步执行，限默认 0 = 全量）</li>
 * </ul>
 *
 * <p>历史端点 {@code /nodes} {@code /edges} 查询的是从不写入的空表，现已移除（架构铁律 §5.1 低冗余原则）。</p>
 */
@RestController
@RequestMapping("/api/v1/engine/data/lineage")
public class DataLineageController {

    private final DataLineageService lineageService;

    public DataLineageController(DataLineageService lineageService) {
        this.lineageService = lineageService;
    }

    /** 按管道任务 ID 查询血缘（实时解析）。 */
    @GetMapping("/pipeline/{taskId}")
    public ApiResponse<Map<String, Object>> pipelineLineage(@PathVariable String taskId) {
        try {
            return ApiResponse.success(lineageService.getPipelineLineage(taskId));
        } catch (Exception e) {
            return ApiResponse.notFound("Pipeline " + taskId + " 不存在或解析失败: " + e.getMessage());
        }
    }

    /** 按表名查询血缘（实时解析）。tableName 必填，留空返回空结果。 */
    @GetMapping
    public ApiResponse<Map<String, Object>> getLineage(
            @RequestParam(required = false) String datasourceId,
            @RequestParam(defaultValue = "") String tableName) {
        if (tableName == null || tableName.isBlank()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("nodes", List.of());
            empty.put("edges", List.of());
            empty.put("total_nodes", 0);
            empty.put("total_edges", 0);
            return ApiResponse.success("输入表名以查询单表血缘，或使用拓扑视图", empty);
        }
        try {
            Map<String, Object> result = lineageService.getLineage(datasourceId, tableName);
            int total = (int) result.getOrDefault("total_nodes", 0);
            if (total == 0) {
                return ApiResponse.success("未找到表 " + tableName + " 的血缘关系", result);
            }
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.badRequest("血缘解析失败: " + e.getMessage());
        }
    }

    /**
     * 查询持久化拓扑（前端"重新生成"后秒开）。
     * <p>优先读 DB 持久化表；如表为空（首次使用），返回空 topology + {@code from_db=false}，前端据此显示"重新生成"引导。</p>
     */
    @GetMapping("/topology")
    public ApiResponse<Map<String, Object>> getTopology() {
        return ApiResponse.success(lineageService.getTopologyFromDb(null));
    }

    /**
     * 重建血缘并持久化（同步执行，默认全量，limit 参数限制最多处理的任务数）。
     * <p>前端"重新生成"按钮调用此端点；重建前会清空旧数据，保证幂等。</p>
     */
    @PostMapping("/topology/rebuild")
    public ApiResponse<Map<String, Object>> rebuildTopology(@RequestParam(defaultValue = "0") int limit) {
        try {
            lineageService.clearLineageData();
            Map<String, Object> result = lineageService.rebuildAndPersist(limit);
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.badRequest("重建血缘失败: " + e.getMessage());
        }
    }

    /**
     * 影响度分析（P2）：从指定节点出发做双向 N 层 BFS，返回 downstream / upstream
     * 可达节点与 severity 评级。前端选中节点后右侧面板调用。
     *
     * @param startNode 起点节点 ID（表名 / 字段名，允许 schema.qualified 形式）
     * @param depth     追溯层数（默认 3，上限 8）
     */
    @GetMapping("/impact")
    public ApiResponse<Map<String, Object>> getImpact(
            @RequestParam String startNode,
            @RequestParam(defaultValue = "3") int depth) {
        if (startNode == null || startNode.isBlank()) {
            return ApiResponse.badRequest("startNode 不能为空");
        }
        try {
            return ApiResponse.success(lineageService.getImpactOverview(startNode, depth));
        } catch (Exception e) {
            return ApiResponse.badRequest("影响度分析失败: " + e.getMessage());
        }
    }
}
