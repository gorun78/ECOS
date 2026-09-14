package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.controller.model.ScenarioEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ECOS Phase 1 P1-3: World Model — 场景控制器。
 * <p>
 * MVP 阶段使用内存 {@code ConcurrentHashMap} 存储，无 JDBC 依赖。
 * 提供场景的 CRUD 操作：
 * </p>
 * <pre>
 * GET    /api/v1/ecos/world-model/scenarios      — 场景列表
 * POST   /api/v1/ecos/world-model/scenarios      — 创建场景
 * PUT    /api/v1/ecos/world-model/scenarios/{id} — 更新场景
 * DELETE /api/v1/ecos/world-model/scenarios/{id} — 删除场景
 * </pre>
 */
@RestController("sysmanScenarioController")
@RequestMapping("/api/v1/ecos/world-model-graph/scenarios")
public class ScenarioController {

    private static final Logger log = LoggerFactory.getLogger(ScenarioController.class);

    /** 内存存储：scenarioId → ScenarioEntity */
    private final ConcurrentHashMap<String, ScenarioEntity> store = new ConcurrentHashMap<>();

    /**
     * 初始化默认场景数据。
     */
    public ScenarioController() {
        ScenarioEntity defaultScenario = new ScenarioEntity();
        defaultScenario.setId("default-scenario");
        defaultScenario.setName("默认场景");
        defaultScenario.setDescription("系统默认业务场景");
        defaultScenario.setRelatedGoalIds(new ArrayList<>());
        defaultScenario.setStatus("draft");
        defaultScenario.setCreatedAt(System.currentTimeMillis());
        defaultScenario.setUpdatedAt(System.currentTimeMillis());
        store.put("default-scenario", defaultScenario);
        log.info("ScenarioController 初始化完成，已加载默认场景");
    }

    // ────────────────────────────────────────────────
    // 查询
    // ────────────────────────────────────────────────

    /**
     * 获取所有场景列表。
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> list() {
        try {
            List<ScenarioEntity> all = new ArrayList<>(store.values());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", all);
            result.put("total", all.size());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询场景列表失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取单个场景详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable String id) {
        try {
            ScenarioEntity scenario = store.get(id);
            if (scenario == null) {
                return ApiResponse.notFound("场景不存在: " + id);
            }
            return ApiResponse.success(scenario);
        } catch (Exception e) {
            log.error("查询场景失败, id={}", id, e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────
    // 新增
    // ────────────────────────────────────────────────

    /**
     * 创建场景。
     */
    @PostMapping
    public ApiResponse<?> create(@RequestBody ScenarioEntity scenario) {
        try {
            String id = UUID.randomUUID().toString().replace("-", "");
            scenario.setId(id);
            scenario.setCreatedAt(System.currentTimeMillis());
            scenario.setUpdatedAt(System.currentTimeMillis());

            // 名称校验
            if (scenario.getName() == null || scenario.getName().isBlank()) {
                scenario.setName("场景 " + id.substring(0, 8));
            }
            // 状态默认值
            if (scenario.getStatus() == null || scenario.getStatus().isBlank()) {
                scenario.setStatus("draft");
            }
            // relatedGoalIds 默认值
            if (scenario.getRelatedGoalIds() == null) {
                scenario.setRelatedGoalIds(new ArrayList<>());
            }

            store.put(id, scenario);
            log.info("场景创建成功, id={}, name={}", id, scenario.getName());
            return ApiResponse.success(scenario);
        } catch (Exception e) {
            log.error("创建场景失败", e);
            return ApiResponse.internalError("创建失败: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────
    // 更新
    // ────────────────────────────────────────────────

    /**
     * 更新场景。
     */
    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody ScenarioEntity scenario) {
        try {
            ScenarioEntity existing = store.get(id);
            if (existing == null) {
                return ApiResponse.notFound("场景不存在: " + id);
            }

            // 保护字段
            scenario.setId(id);
            scenario.setCreatedAt(existing.getCreatedAt());
            scenario.setUpdatedAt(System.currentTimeMillis());

            // 保留未传入的字段
            if (scenario.getName() == null) scenario.setName(existing.getName());
            if (scenario.getDescription() == null) scenario.setDescription(existing.getDescription());
            if (scenario.getRelatedGoalIds() == null) scenario.setRelatedGoalIds(existing.getRelatedGoalIds());
            if (scenario.getStatus() == null) scenario.setStatus(existing.getStatus());

            store.put(id, scenario);
            log.info("场景更新成功, id={}, name={}", id, scenario.getName());
            return ApiResponse.success(scenario);
        } catch (Exception e) {
            log.error("更新场景失败, id={}", id, e);
            return ApiResponse.internalError("更新失败: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────
    // 删除
    // ────────────────────────────────────────────────

    /**
     * 删除场景。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String id) {
        try {
            ScenarioEntity removed = store.remove(id);
            if (removed == null) {
                return ApiResponse.notFound("场景不存在: " + id);
            }
            log.info("场景删除成功, id={}, name={}", id, removed.getName());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("id", id);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("删除场景失败, id={}", id, e);
            return ApiResponse.internalError("删除失败: " + e.getMessage());
        }
    }
}
