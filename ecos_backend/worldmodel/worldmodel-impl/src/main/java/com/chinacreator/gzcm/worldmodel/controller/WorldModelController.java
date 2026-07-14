package com.chinacreator.gzcm.worldmodel.controller;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.worldmodel.WorldModelService;

/**
 * World Model REST API — 目标/场景/因果链 CRUD + 因果图 + 目标树 + 场景对比。
 *
 * <pre>
 * GET    /api/v1/ecos/world-model/goals          — 目标列表
 * GET    /api/v1/ecos/world-model/goals/tree     — 目标树
 * GET    /api/v1/ecos/world-model/scenarios      — 场景列表
 * GET    /api/v1/ecos/world-model/causal-links   — 因果链列表
 * GET    /api/v1/ecos/world-model/causal-graph   — 因果图(节点+边)
 * POST   /api/v1/ecos/world-model/{type}         — 创建 (goals/scenarios/causal-links)
 * PUT    /api/v1/ecos/world-model/{type}/{id}    — 更新
 * DELETE /api/v1/ecos/world-model/{type}/{id}    — 删除
 * POST   /api/v1/ecos/world-model/compare        — 场景对比
 * </pre>
 *
 * <p>现使用 {@link WorldModelService} + JdbcTemplate 持久化到 PostgreSQL，
 * 替代原有的 ConcurrentHashMap 内存存储。</p>
 */
@RestController
@RequestMapping({"/api/v1/ecos/world-model", "/api/v1/worldmodel"})
public class WorldModelController {

    private static final Logger log = LoggerFactory.getLogger(WorldModelController.class);

    private final WorldModelService worldModelService;

    public WorldModelController(WorldModelService worldModelService) {
        this.worldModelService = worldModelService;
    }

    // ═══════════════ 查询 ═══════════════════

    @GetMapping("/goals")
    public ApiResponse<Map<String, Object>> listGoals() {
        List<Map<String, Object>> data = worldModelService.listGoals();
        return ok(data, data.size());
    }

    @GetMapping("/goals/tree")
    public ApiResponse<List<Map<String, Object>>> goalTree() {
        return ApiResponse.success(worldModelService.goalTree());
    }

    @GetMapping("/scenarios")
    public ApiResponse<Map<String, Object>> listScenarios() {
        List<Map<String, Object>> data = worldModelService.listScenarios();
        return ok(data, data.size());
    }

    @GetMapping("/causal-links")
    public ApiResponse<Map<String, Object>> listCausalLinks() {
        List<Map<String, Object>> data = worldModelService.listCausalLinks();
        return ok(data, data.size());
    }

    @GetMapping("/causal-graph")
    public ApiResponse<Map<String, Object>> causalGraph() {
        return ApiResponse.success(worldModelService.causalGraph());
    }

    /** 自动生成下一个目标编码 */
    @GetMapping("/goals/next-code")
    public ApiResponse<String> nextCode() {
        String code = worldModelService.nextGoalCode();
        return ApiResponse.success(code);
    }

    // ═══════════════ 创建/更新/删除 ═══════════════════

    @PostMapping("/goals")
    public ApiResponse<Map<String, Object>> createGoal(@RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> result = worldModelService.createGoal(body);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("createGoal failed: {}", e.getMessage(), e);
            return ApiResponse.error(-1, "创建失败: " + e.getMessage());
        }
    }

    @PostMapping("/scenarios")
    public ApiResponse<Map<String, Object>> createScenario(@RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success(worldModelService.createScenario(body));
        } catch (Exception e) {
            log.error("createScenario failed: {}", e.getMessage(), e);
            return ApiResponse.error(-1, "创建失败: " + e.getMessage());
        }
    }

    @PostMapping("/causal-links")
    public ApiResponse<Map<String, Object>> createCausalLink(@RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success(worldModelService.createCausalLink(body));
        } catch (Exception e) {
            log.error("createCausalLink failed: {}", e.getMessage(), e);
            return ApiResponse.error(-1, "创建失败: " + e.getMessage());
        }
    }

    @PutMapping("/{type}/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String type,
                                                    @PathVariable Long id,
                                                    @RequestBody Map<String, Object> body) {
        Optional<Map<String, Object>> result = switch (type) {
            case "goals" -> worldModelService.updateGoal(id, body);
            case "scenarios" -> worldModelService.updateScenario(id, body);
            case "causal-links" -> worldModelService.updateCausalLink(id, body);
            default -> {
                log.warn("PUT not supported for type={}", type);
                yield Optional.empty();
            }
        };
        return result
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound(type + " " + id + " 不存在"));
    }

    @DeleteMapping("/{type}/{id}")
    public ApiResponse<String> delete(@PathVariable String type, @PathVariable Long id) {
        try {
            boolean removed = switch (type) {
                case "goals" -> worldModelService.deleteGoal(id);
                case "scenarios" -> worldModelService.deleteScenario(id);
                case "causal-links" -> worldModelService.deleteCausalLink(id);
                default -> false;
            };
            if (!removed) return ApiResponse.notFound(type + " " + id + " 不存在");
            return ApiResponse.success(type + " " + id + " 已删除");
        } catch (Exception e) {
            log.error("delete failed for {} {}: {}", type, id, e.getMessage(), e);
            return ApiResponse.error(-1, "删除失败: " + e.getMessage());
        }
    }

    // ═══════════════ 对比 ═══════════════════

    @PostMapping("/compare")
    public ApiResponse<List<Map<String, Object>>> compare(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> rawIds = (List<Number>) body.getOrDefault("scenarioIds", List.of());
        List<Long> scenarioIds = rawIds.stream()
            .map(Number::longValue)
            .toList();
        List<Map<String, Object>> result = worldModelService.compareScenarios(scenarioIds);
        return ApiResponse.success(result);
    }

    // ═══════════════ 内部辅助 ═══════════════════

    private ApiResponse<Map<String, Object>> ok(List<Map<String, Object>> data, int total) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("data", data);
        r.put("total", total);
        return ApiResponse.success(r);
    }
}
