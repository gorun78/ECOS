package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive2.dao.ParetoProblemDao;
import com.chinacreator.gzcm.engine.cognitive2.model.ParetoOptimizeResult;
import com.chinacreator.gzcm.engine.cognitive2.model.ParetoProblemPage;
import com.chinacreator.gzcm.engine.cognitive2.model.ParetoProblemSnapshot;
import com.chinacreator.gzcm.engine.cognitive2.model.ParetoProblemSummary;
import com.chinacreator.gzcm.engine.cognitive2.model.ParetoSolution;
import com.chinacreator.gzcm.engine.cognitive2.service.Nsga2Optimizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pareto 多目标优化端点（D106；前端 {@code ParetoTab} 的四个调用腿）。
 *
 * <p><b>缺陷背景（2026-11-15 活体验证）</b>：前端 {@code monitorClient.ts} 的
 * {@code PARETO_BASE = /api/v1/pareto}，调 {@code /optimize}、{@code /problems}、
 * {@code /result/{id}}、{@code /from-scenario}；全仓<b>零 Controller 实现</b>，
 * 四端点经网关恒 <b>404</b>（认知侧 {@code ParetoOptimizerServiceImpl} 只有 24 行桩、
 * 固定返回 {@code expectedImpact=0.2}，且签名 {@code optimize(String, List&lt;String&gt;)}
 * 与前端 {@code {numObjectives,...}} 完全不同）。世界模型第 7 个 Tab 因此恒空。</p>
 *
 * <p><b>目标函数口径</b>：请求体只带<b>计数</b>（目标数/变量数/种群/代数），不带目标函数表达式，
 * 故按 {@link Nsga2Optimizer} 的内置 ZDT-1 型基准族求解；返回体里
 * {@code variables} 是 {@code x1..xn}、{@code objectives} 是 {@code f1..fm}，
 * 与前端渲染口径（按键名排序取值）一致。接入真实业务目标时替换
 * {@link Nsga2Optimizer#evaluate} 即可，端点契约不变。</p>
 *
 * <p><b>可复现</b>：种子由 problemId 的哈希派生 —— 同一次优化重跑
 * {@code /result/{problemId}} 读到的是<b>同一份快照</b>（不重算），前沿不会因刷新而漂移。</p>
 */
@RestController
@RequestMapping("/api/v1/pareto")
public class ParetoController {

    private static final Logger log = LoggerFactory.getLogger(ParetoController.class);

    /** 上限护栏：防止被一个巨大种群/代数请求拖死进程。 */
    private static final int MAX_POPULATION = 2000;
    private static final int MAX_GENERATIONS = 500;
    private static final int MAX_OBJECTIVES = 3;
    private static final int MAX_VARIABLES = 5;
    private static final int MAX_HISTORY = 100;

    private final Nsga2Optimizer optimizer;
    private final ParetoProblemDao problemDao;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ParetoController(Nsga2Optimizer optimizer, ParetoProblemDao problemDao) {
        this.optimizer = optimizer;
        this.problemDao = problemDao;
    }

    /** 请求体（前端 paretoOptimize 的四个计数；problemName 可选）。 */
    public record OptimizeRequest(Integer numObjectives, Integer numVariables,
                                  Integer populationSize, Integer generations,
                                  String problemName) {
    }

    /** 前端 {@code paretoFromScenario} 的请求体。 */
    public record FromScenarioRequest(String scenarioId) {
    }

    /**
     * 运行一次多目标优化并落库。
     *
     * <p>越界计数按 400 拒绝，<b>不静默夹到上限</b> ——「请求了 100000 代，实际跑了 500 代
     * 却报告成功」属于另一种假成功。</p>
     */
    @PostMapping("/optimize")
    public ApiResponse<?> optimize(@RequestBody OptimizeRequest request) {
        Integer m = request.numObjectives();
        Integer n = request.numVariables();
        Integer pop = request.populationSize();
        Integer gens = request.generations();
        if (m == null || n == null || pop == null || gens == null) {
            return ApiResponse.badRequest("numObjectives/numVariables/populationSize/generations 均为必填");
        }
        if (m < 2 || m > MAX_OBJECTIVES) {
            return ApiResponse.badRequest("numObjectives 必须在 2 ~ " + MAX_OBJECTIVES + " 之间");
        }
        if (n < 2 || n > MAX_VARIABLES) {
            return ApiResponse.badRequest("numVariables 必须在 2 ~ " + MAX_VARIABLES + " 之间");
        }
        if (pop < 4 || pop > MAX_POPULATION) {
            return ApiResponse.badRequest("populationSize 必须在 4 ~ " + MAX_POPULATION + " 之间");
        }
        if (gens < 1 || gens > MAX_GENERATIONS) {
            return ApiResponse.badRequest("generations 必须在 1 ~ " + MAX_GENERATIONS + " 之间");
        }

        try {
            String problemId = UUID.randomUUID().toString().replace("-", "");
            String problemName = request.problemName() == null || request.problemName().isBlank()
                    ? "Pareto " + m + " 目标 / " + n + " 变量"
                    : request.problemName();
            long seed = problemId.hashCode();

            long start = System.nanoTime();
            List<Nsga2Optimizer.Individual> front = optimizer.run(m, n, pop, gens, seed);
            long elapsedMs = Math.max(0L, (System.nanoTime() - start) / 1_000_000L);

            List<ParetoSolution> solutions = new ArrayList<>(front.size());
            for (Nsga2Optimizer.Individual ind : front) {
                Map<String, Double> variables = new LinkedHashMap<>();
                for (int i = 0; i < ind.variables().length; i++) {
                    variables.put("x" + (i + 1), round(ind.variables()[i]));
                }
                Map<String, Double> objectives = new LinkedHashMap<>();
                for (int i = 0; i < ind.objectives().length; i++) {
                    objectives.put("f" + (i + 1), round(ind.objectives()[i]));
                }
                solutions.add(new ParetoSolution(variables, objectives));
            }

            String solutionsJson = objectMapper.writeValueAsString(solutions);
            problemDao.insert(problemId, problemName, m, n, pop, gens,
                    front.size(), solutionsJson, elapsedMs);

            log.info("Pareto 优化完成: problemId={}, m={}, n={}, pop={}, gens={}, frontSize={}, elapsed={}ms",
                    problemId, m, n, pop, gens, front.size(), elapsedMs);
            return ApiResponse.success(
                    new ParetoOptimizeResult(problemId, front.size(), solutions, elapsedMs));
        } catch (Exception e) {
            log.error("Pareto 优化失败", e);
            return ApiResponse.internalError("优化执行失败");
        }
    }

    /** 历史列表（按提交时间倒序）。 */
    @GetMapping("/problems")
    public ApiResponse<?> problems(@RequestParam(defaultValue = "50") int limit) {
        try {
            int capped = Math.max(1, Math.min(limit, MAX_HISTORY));
            List<ParetoProblemSummary> rows = problemDao.listProblems(capped);
            return ApiResponse.success(new ParetoProblemPage(rows, rows.size()));
        } catch (Exception e) {
            log.error("Pareto 历史查询失败", e);
            return ApiResponse.internalError("历史查询失败");
        }
    }

    /** 回取某次结果（读快照，不重算 —— 同一 problemId 恒定同一前沿）。 */
    @GetMapping("/result/{problemId}")
    public ApiResponse<?> result(@PathVariable String problemId) {
        try {
            ParetoProblemSnapshot snapshot = problemDao.findById(problemId);
            if (snapshot == null) {
                return ApiResponse.notFound("优化问题不存在: " + problemId);
            }
            List<ParetoSolution> solutions = deserialize(snapshot.solutionsJson());
            return ApiResponse.success(new ParetoOptimizeResult(
                    snapshot.problemId(), snapshot.frontSize(), solutions, snapshot.elapsedMs()));
        } catch (Exception e) {
            log.error("Pareto 结果回取失败, problemId={}", problemId, e);
            return ApiResponse.internalError("结果回取失败");
        }
    }

    /**
     * 由场景生成一次优化（前端 {@code paretoFromScenario}）。
     *
     * <p>场景 id 只作为 problemName 的来源与日志关联；场景本身不带目标函数表达式，
     * 故按默认维度求解。目标：让「从场景出发看权衡」这条腿真实可用，
     * 而不是返回 404 让按钮永远无效。</p>
     */
    @PostMapping("/from-scenario")
    public ApiResponse<?> fromScenario(@RequestBody(required = false) FromScenarioRequest body) {
        String scenarioId = body == null ? null : body.scenarioId();
        if (scenarioId == null || scenarioId.isBlank()) {
            return ApiResponse.badRequest("scenarioId 必填");
        }
        return optimize(new OptimizeRequest(
                2, 3, 100, 50, "场景 " + scenarioId + " 的权衡前沿"));
    }

    /**
     * 反序列化落库的前沿快照。
     *
     * <p>用 {@code TypeReference} 保留 {variables,objectives} 结构 —— 退化成
     * {@code List.class} 会得到 {@code List<LinkedHashMap>}，JSON 面虽同形，
     * 但类型面把「解」这层结构丢了。</p>
     */
    private List<ParetoSolution> deserialize(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return objectMapper.readValue(json,
                new com.fasterxml.jackson.core.type.TypeReference<List<ParetoSolution>>() { });
    }

    private static double round(double v) {
        return Math.round(v * 1_000_000d) / 1_000_000d;
    }
}
