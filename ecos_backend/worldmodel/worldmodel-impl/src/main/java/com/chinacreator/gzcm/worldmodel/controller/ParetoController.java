package com.chinacreator.gzcm.worldmodel.controller;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.worldmodel.pareto.Individual;
import com.chinacreator.gzcm.worldmodel.pareto.NSGA2Engine;
import com.chinacreator.gzcm.worldmodel.pareto.ObjectiveRegistry;
import com.chinacreator.gzcm.worldmodel.pareto.OptimizationProblem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * NSGA-II 帕累托寻优 REST API。
 *
 * <pre>
 * POST   /api/pareto/optimize       — 执行优化
 * GET    /api/pareto/problems       — 已保存问题列表
 * GET    /api/pareto/result/{id}    — 查询指定问题的结果
 * POST   /api/pareto/from-scenario  — 从 WorldModel 场景生成优化问题 (demo)
 * </pre>
 */
@RestController
@RequestMapping("/api/pareto")
@Tag(name = "Pareto", description = "NSGA-II 帕累托寻优 — 多目标优化算法")
public class ParetoController {

    private static final Logger log = LoggerFactory.getLogger(ParetoController.class);

    /**
     * B4: 线程安全的结果存储 — ConcurrentHashMap + 简单 LRU (最大 100 条)。
     * 避免 LinkedHashMap 无限增长和非线程安全的问题。
     */
    private static final int MAX_RESULTS = 100;
    private final Map<String, Map<String, Object>> results = new ConcurrentHashMap<>();
    /** 保存的问题定义 (同样改为 ConcurrentHashMap) */
    private final Map<String, Map<String, Object>> problems = new ConcurrentHashMap<>();

    // ═══════════════ POST /api/pareto/optimize ═══════════════════

    @Operation(summary = "执行优化", description = "执行 NSGA-II 多目标优化算法")
    @PostMapping("/optimize")
    public ApiResponse<Map<String, Object>> optimize(@RequestBody Map<String, Object> body) {
        long start = System.currentTimeMillis();

        try {
            // 解析请求
            OptimizationProblem problem = parseProblem(body);
            int popSize = getInt(body, "populationSize", 100);
            int generations = getInt(body, "generations", 200);

            log.info("NSGA-II optimize: problem={}, pop={}, gen={}", problem.getName(), popSize, generations);

            // 执行 NSGA-II
            NSGA2Engine engine = new NSGA2Engine();
            List<Individual> front = engine.evolve(problem, popSize, generations);

            long elapsed = System.currentTimeMillis() - start;

            // 构建响应
            List<Map<String, Object>> solutions = new ArrayList<>();
            for (Individual ind : front) {
                Map<String, Object> sol = new LinkedHashMap<>();
                sol.put("variables", new LinkedHashMap<>(ind.getVariables()));
                sol.put("objectives", new LinkedHashMap<>(ind.getObjectives()));
                sol.put("rank", ind.getRank());
                solutions.add(sol);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("problemId", problem.getProblemId());
            result.put("problemName", problem.getName());
            result.put("frontSize", front.size());
            result.put("solutions", solutions);
            result.put("elapsed_ms", elapsed);
            result.put("populationSize", popSize);
            result.put("generations", generations);

            // 缓存 — LRU 淘汰: 超限时移除最早条目
            results.put(problem.getProblemId(), result);
            problems.put(problem.getProblemId(), body);
            if (results.size() > MAX_RESULTS) {
                // 简单 LRU: 移除第一个 key (ConcurrentHashMap 不保证顺序，但作为一个近似策略)
                String oldestKey = results.keySet().iterator().next();
                results.remove(oldestKey);
                problems.remove(oldestKey);
                log.debug("LRU evicted pareto result: problemId={}", oldestKey);
            }

            log.info("NSGA-II done: problemId={}, frontSize={}, elapsed={}ms",
                     problem.getProblemId(), front.size(), elapsed);

            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("NSGA-II optimize failed", e);
            return ApiResponse.internalError("优化失败: " + e.getMessage());
        }
    }

    // ═══════════════ GET /api/pareto/problems ═══════════════════

    @GetMapping("/problems")
    public ApiResponse<Map<String, Object>> listProblems() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : problems.entrySet()) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("problemId", e.getKey());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = e.getValue();
            summary.put("problemName", body.getOrDefault("problemName", "unknown"));
            summary.put("objectives", body.getOrDefault("objectives", Collections.emptyList()));
            summary.put("variables", body.getOrDefault("variables", Collections.emptyList()));

            Map<String, Object> cached = results.get(e.getKey());
            if (cached != null) {
                summary.put("frontSize", cached.get("frontSize"));
                summary.put("elapsed_ms", cached.get("elapsed_ms"));
            }
            list.add(summary);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("data", list);
        r.put("total", list.size());
        return ApiResponse.success(r);
    }

    // ═══════════════ GET /api/pareto/result/{problemId} ═══════════════════

    @GetMapping("/result/{problemId}")
    public ApiResponse<Map<String, Object>> getResult(@PathVariable String problemId) {
        Map<String, Object> result = results.get(problemId);
        if (result == null) {
            return ApiResponse.notFound("优化结果不存在: " + problemId);
        }
        return ApiResponse.success(result);
    }

    // ═══════════════ POST /api/pareto/from-scenario ═══════════════════

    @PostMapping("/from-scenario")
    public ApiResponse<Map<String, Object>> fromScenario(@RequestBody Map<String, Object> body) {
        // 从场景描述自动生成一个 demo 优化问题（2 目标 × 3 变量）
        String scenarioName = (String) body.getOrDefault("scenarioName", "auto-generated");

        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("problemName", "场景优化-" + scenarioName);
        problem.put("populationSize", getInt(body, "populationSize", 100));
        problem.put("generations", getInt(body, "generations", 200));

        // 2 目标
        List<Map<String, String>> objectives = new ArrayList<>();
        Map<String, String> obj1 = new LinkedHashMap<>();
        obj1.put("name", "data-quality-score");
        obj1.put("direction", "MAX");
        objectives.add(obj1);
        Map<String, String> obj2 = new LinkedHashMap<>();
        obj2.put("name", "compute-cost");
        obj2.put("direction", "MIN");
        objectives.add(obj2);
        problem.put("objectives", objectives);

        // 3 变量
        List<Map<String, Object>> variables = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Map<String, Object> var = new LinkedHashMap<>();
            var.put("name", "param" + i);
            var.put("type", "DOUBLE");
            var.put("min", 0);
            var.put("max", 10);
            variables.add(var);
        }
        problem.put("variables", variables);

        return optimize(problem);
    }

    // ═══════════════ 内部解析 ═══════════════════

    @SuppressWarnings("unchecked")
    private OptimizationProblem parseProblem(Map<String, Object> body) {
        OptimizationProblem p = new OptimizationProblem();
        p.setName((String) body.getOrDefault("problemName", "unnamed"));

        // 解析 objectives — 支持两种格式:
        // 1) 完整数组: {objectives: [{name, direction}, ...]}
        // 2) 简化格式: {numObjectives: 3} → 自动生成 f1..fN
        List<Map<String, String>> rawObjs = (List<Map<String, String>>) body.get("objectives");
        if (rawObjs == null) {
            int n = getInt(body, "numObjectives", 0);
            rawObjs = n > 0 ? generateObjectives(n) : defaultObjectives();
        }
        for (Map<String, String> m : rawObjs) {
            OptimizationProblem.Objective obj = new OptimizationProblem.Objective();
            obj.setName(m.get("name"));
            obj.setDirection("MAX".equalsIgnoreCase(m.get("direction"))
                ? OptimizationProblem.Objective.Direction.MAX
                : OptimizationProblem.Objective.Direction.MIN);
            obj.setWeight(1.0);
            p.getObjectives().add(obj);
        }

        // 解析 variables — 支持两种格式:
        // 1) 完整数组: {variables: [{name, type, min, max}, ...]}
        // 2) 简化格式: {numVariables: 4} → 自动生成 x0..xN
        List<Map<String, Object>> rawVars = (List<Map<String, Object>>) body.get("variables");
        if (rawVars == null) {
            int n = getInt(body, "numVariables", 0);
            rawVars = n > 0 ? generateVariables(n) : defaultVariables();
        }
        for (Map<String, Object> m : rawVars) {
            OptimizationProblem.Variable v = new OptimizationProblem.Variable();
            v.setName((String) m.get("name"));
            v.setType("INTEGER".equalsIgnoreCase((String) m.get("type"))
                ? OptimizationProblem.Variable.Type.INTEGER
                : OptimizationProblem.Variable.Type.DOUBLE);
            v.setMin(toDouble(m.get("min"), 0.0));
            v.setMax(toDouble(m.get("max"), 10.0));
            p.getVariables().add(v);
        }

        return p;
    }

    private List<Map<String, String>> defaultObjectives() {
        List<Map<String, String>> list = new ArrayList<>();
        Map<String, String> o1 = new LinkedHashMap<>();
        o1.put("name", "f1");
        o1.put("direction", "MIN");
        list.add(o1);
        Map<String, String> o2 = new LinkedHashMap<>();
        o2.put("name", "f2");
        o2.put("direction", "MIN");
        list.add(o2);
        return list;
    }

    private List<Map<String, Object>> defaultVariables() {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> v1 = new LinkedHashMap<>();
        v1.put("name", "x");
        v1.put("type", "DOUBLE");
        v1.put("min", 0);
        v1.put("max", 10);
        list.add(v1);
        Map<String, Object> v2 = new LinkedHashMap<>();
        v2.put("name", "y");
        v2.put("type", "DOUBLE");
        v2.put("min", 0);
        v2.put("max", 10);
        list.add(v2);
        return list;
    }

    /** Generate N objectives from frontend numObjectives param */
    private List<Map<String, String>> generateObjectives(int n) {
        List<Map<String, String>> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Map<String, String> obj = new LinkedHashMap<>();
            obj.put("name", "f" + (i + 1));
            obj.put("direction", i % 2 == 0 ? "MIN" : "MAX");
            list.add(obj);
        }
        return list;
    }

    /** Generate N variables from frontend numVariables param */
    private List<Map<String, Object>> generateVariables(int n) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("name", "x" + i);
            v.put("type", "DOUBLE");
            v.put("min", 0);
            v.put("max", 10);
            list.add(v);
        }
        return list;
    }

    private int getInt(Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt((String) v); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private double toDouble(Object v, double def) {
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) {
            try { return Double.parseDouble((String) v); } catch (NumberFormatException ignored) {}
        }
        return def;
    }
}
