package com.chinacreator.gzcm.workspace.controller;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;

/**
 * Scenario CRUD REST API — 业务场景管理。
 *
 * <pre>
 * GET    /api/v1/workspace/scenarios           — 场景列表
 * GET    /api/v1/workspace/scenarios/{id}      — 场景详情
 * POST   /api/v1/workspace/scenarios           — 创建场景
 * PUT    /api/v1/workspace/scenarios/{id}      — 更新场景
 * DELETE /api/v1/workspace/scenarios/{id}      — 删除场景
 * GET    /api/v1/workspace/scenarios/{id}/bindings — 场景绑定关系
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/workspace/scenarios")
public class ScenarioController {

    private static final Logger log = LoggerFactory.getLogger(ScenarioController.class);
    private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    public ScenarioController() {
        // 种子数据：高速信科航空场景
        seed("sc001", "飞行运行安全", "建立覆盖飞行员、飞机、航线的全方位安全监控体系",
                "HIGH", "飞行运行部", "850万", "0.95", "0.87");
        seed("sc002", "客舱服务质量提升", "通过旅客反馈数据优化客舱服务流程",
                "MEDIUM", "客舱服务部", "320万", "0.90", "0.78");
        seed("sc003", "航空燃油效率优化", "降低燃油消耗，实现绿色飞行",
                "CRITICAL", "运行控制中心", "1200万", "0.92", "0.83");
    }

    private void seed(String id, String name, String desc, String priority,
                      String dept, String budget, String safetyTarget, String actual) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", id);
        s.put("name", name);
        s.put("description", desc);
        s.put("businessGoal", desc);
        s.put("department", dept);
        s.put("priority", priority);
        s.put("status", "ACTIVE");
        s.put("budget", budget);
        s.put("safetyIndexTarget", safetyTarget);
        s.put("actualSafetyIndex", actual);
        s.put("createdAt", LocalDateTime.now().minusDays(new Random().nextInt(30)).toString());
        s.put("bindings", Map.of(
            "datasets", List.of("ds_flight_schedules", "ds_passenger_feedback"),
            "objectTypes", List.of("flight", "aircraft", "pilot"),
            "knowledgeBases", List.of("sop_maintenance_v2"),
            "aiAgents", List.of("agent_safety_monitor"),
            "securityPolicies", List.of("pol_gdpr_compliance"),
            "interfaces", List.of()
        ));
        s.put("metrics", Map.of(
            "integrityScore", 92,
            "mappingCompleteness", 78,
            "dataQualityScore", 85,
            "complianceRate", 95
        ));
        store.put(id, s);
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.success(new ArrayList<>(store.values()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String id) {
        Map<String, Object> s = store.get(id);
        if (s == null) return ApiResponse.error(404, "场景不存在: " + id);
        return ApiResponse.success(s);
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String id = Optional.ofNullable(body.get("id"))
                .map(Object::toString)
                .orElse("sc_" + UUID.randomUUID().toString().substring(0, 6));
        if (store.containsKey(id)) return ApiResponse.error(409, "场景ID已存在: " + id);

        Map<String, Object> s = new LinkedHashMap<>(body);
        s.putIfAbsent("id", id);
        s.putIfAbsent("status", "DRAFT");
        s.putIfAbsent("createdAt", LocalDateTime.now().toString());
        s.putIfAbsent("bindings", Map.of());
        s.putIfAbsent("metrics", Map.of());
        store.put(id, s);
        log.info("创建场景: {}", id);
        return ApiResponse.success(s);
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String id,
                                                   @RequestBody Map<String, Object> body) {
        Map<String, Object> s = store.get(id);
        if (s == null) return ApiResponse.error(404, "场景不存在: " + id);
        s.putAll(body);
        s.put("id", id); // 防止覆盖
        log.info("更新场景: {}", id);
        return ApiResponse.success(s);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        if (store.remove(id) == null) return ApiResponse.error(404, "场景不存在: " + id);
        log.info("删除场景: {}", id);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/bindings")
    public ApiResponse<Map<String, Object>> bindings(@PathVariable String id) {
        Map<String, Object> s = store.get(id);
        if (s == null) return ApiResponse.error(404, "场景不存在: " + id);
        return ApiResponse.success((Map<String, Object>) s.getOrDefault("bindings", Map.of()));
    }
}
