package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * 场景运行编排服务 — 场景工作台认知闭环（PMO-52 T2）。
 *
 * <p>一次场景运行 = 按 runTypes 组合调用 cognitive 4 类端点（诊断/预测/模拟/策略），
 * 每步独立 try/catch 降级（单步失败不阻断整体，记 degraded），全部结果落
 * {@code ecos_scenario_run}，并将诊断结论回写场景 {@code actual_safety_index} 与
 * {@code metrics}（闭环反馈）。</p>
 */
@Service
public class ScenarioRunService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioRunService.class);
    /** 允许的运行类型（P3b 只增 SAFEGUARD：反事实推演守卫，既有 4 类型行为零变化） */
    private static final Set<String> ALLOWED_RUN_TYPES =
        Set.of("DIAGNOSE", "FORECAST", "SIMULATE", "STRATEGY", "SAFEGUARD");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final DcchengClient dcchengClient;
    private final ScenarioService scenarioService;

    public ScenarioRunService(JdbcTemplate jdbc, ObjectMapper objectMapper,
                              DcchengClient dcchengClient, ScenarioService scenarioService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.dcchengClient = dcchengClient;
        this.scenarioService = scenarioService;
    }

    /**
     * 发起一次场景运行（同步编排，单步失败降级）。
     *
     * @param scenarioId 场景 id
     * @param param      运行入参：runTypes[]（必填）/ metric / deviation / series / simulateVariables
     * @return 运行结果 VO（含 4 类认知结果引用 + 降级标记）
     */
    @Transactional
    public Map<String, Object> run(String scenarioId, Map<String, Object> param) {
        // 校验场景存在
        try {
            scenarioService.get(scenarioId);
        } catch (NotFoundException e) {
            throw new NotFoundException("RUN-404: 场景不存在: " + scenarioId);
        }
        List<String> runTypes = normalizeRunTypes(param);
        String metric = str(param.get("metric"));
        if (metric == null) {
            metric = "安全指标"; // 默认场景关注指标
        }
        double deviation = param.get("deviation") instanceof Number n ? n.doubleValue() : 0.0;
        String operator = currentOperator();

        String runId = "run_" + UUID.randomUUID().toString().substring(0, 12);
        boolean degraded = false;

        Map<String, Object> diagnosis = null;
        Map<String, Object> forecast = null;
        // Object：SIMULATE 为 Map；SAFEGUARD 为 JsonNode（原始 JSON 树保结构，避免 Map 通道污染）
        Object simulation = null;
        Map<String, Object> strategy = null;

        // ── DIAGNOSE ──
        if (runTypes.contains("DIAGNOSE")) {
            try {
                diagnosis = dcchengClient.diagnose(Map.of(
                        "metric", metric, "deviation", deviation, "domain", "business", "maxDepth", 4));
                degraded |= isDegraded(diagnosis);
            } catch (Exception e) {
                degraded = true;
                diagnosis = errorPayload("诊断失败: " + e.getMessage());
            }
        }

        // ── FORECAST ──
        if (runTypes.contains("FORECAST")) {
            try {
                forecast = dcchengClient.forecast(buildForecastPayload(param, metric));
            } catch (Exception e) {
                degraded = true;
                forecast = errorPayload("预测失败: " + e.getMessage());
            }
        }

        // ── SIMULATE ──
        if (runTypes.contains("SIMULATE")) {
            try {
                Map<String, Object> variables = param.get("simulateVariables") instanceof Map<?, ?> m
                        ? castStringMap(m)
                        : Map.of("perturbation", deviation != 0 ? String.format("%+.0f%%", deviation) : "+10%");
                simulation = dcchengClient.simulate(Map.of(
                        "name", "场景运行 " + metric, "variables", variables, "domain", "business"));
            } catch (Exception e) {
                degraded = true;
                simulation = errorPayload("模拟失败: " + e.getMessage());
            }
        }

        // ── STRATEGY ──
        if (runTypes.contains("STRATEGY")) {
            try {
                Object suggestions = diagnosis == null ? null : diagnosis.get("suggestions");
                strategy = dcchengClient.plan(Map.of(
                        "goal", "基于 " + metric + " 诊断给出改进策略",
                        "context", suggestions == null ? Map.of() : suggestions));
            } catch (Exception e) {
                degraded = true;
                strategy = errorPayload("策略失败: " + e.getMessage());
            }
        }

        // ── SAFEGUARD（PMO-59 P3b 新增：反事实推演守卫，与 SIMULATE 并列占用 simulation_result 槽位）──
        if (runTypes.contains("SAFEGUARD")) {
            try {
                // 与 SIMULATE 同槽位互斥语义：二者并存时 SAFEGUARD 结果覆盖 simulation_result
                // （SAFEGUARD 包含 assumptionRefs 留痕，是失效作废联动关联键，语义更强）
                Map<String, Object> cfPayload = buildCounterfactualPayload(scenarioId, param);
                simulation = dcchengClient.counterfactual(cfPayload);
            } catch (Exception e) {
                degraded = true;
                simulation = errorPayload("反事实推演失败: " + e.getMessage());
            }
        }

        // ── 决策/指标回写（闭环） ──
        BigDecimal actualSafety = null;
        Map<String, Object> metricsMerge = new LinkedHashMap<>();
        if (diagnosis != null && !isError(diagnosis)) {
            // 以诊断置信度代理安全指标口径（0~1），回写场景
            Object conf = diagnosis.get("confidence");
            if (conf instanceof Number) {
                actualSafety = BigDecimal.valueOf(((Number) conf).doubleValue());
            }
            metricsMerge.put("lastDiagnosisAt", new Date().toString());
        }
        if (forecast != null && !isError(forecast)) {
            metricsMerge.put("lastForecastAt", new Date().toString());
        }

        String status = degraded ? "SUCCEEDED_DEGRADED" : "SUCCEEDED";
        jdbc.update(
            "INSERT INTO ecos_scenario_run " +
            "(id, scenario_id, run_type, status, metric, deviation, " +
            " diagnosis_result, forecast_result, simulation_result, strategy_result, degraded, create_by, update_by) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?)",
            runId, scenarioId, String.join(",", runTypes), status, metric,
            deviation == 0 ? null : BigDecimal.valueOf(deviation),
            toJson(diagnosis), toJson(forecast), toJson(simulation), toJson(strategy),
            degraded, operator, operator);

        // 回写场景指标
        if (actualSafety != null || !metricsMerge.isEmpty()) {
            scenarioService.updateMetrics(scenarioId, actualSafety, metricsMerge);
        }
        log.info("场景运行完成 runId={} scenario={} status={} runTypes={} degraded={}",
                runId, scenarioId, status, runTypes, degraded);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("runId", runId);
        resp.put("scenarioId", scenarioId);
        resp.put("status", status);
        resp.put("degraded", degraded);
        resp.put("diagnosis", diagnosis);
        resp.put("forecast", forecast);
        resp.put("simulation", simulation);
        resp.put("strategy", strategy);
        resp.put("metricsWritten", actualSafety != null ? actualSafety : metricsMerge.isEmpty() ? null : "merged");
        return resp;
    }

    /** 某场景的运行历史（倒序，limit 条）。 */
    public List<Map<String, Object>> history(String scenarioId, int limit) {
        if (limit <= 0 || limit > 100) {
            limit = 20;
        }
        return jdbc.queryForList(
            "SELECT id, scenario_id, run_type, status, metric, deviation, degraded, " +
            "       diagnosis_result, forecast_result, simulation_result, strategy_result, create_time " +
            "FROM ecos_scenario_run WHERE scenario_id = ? AND is_deleted = 0 ORDER BY create_time DESC LIMIT ?",
            scenarioId, limit);
    }

    // ── 私有辅助 ──

    private List<String> normalizeRunTypes(Map<String, Object> param) {
        Object raw = param == null ? null : param.get("runTypes");
        List<String> types = new ArrayList<>();
        if (raw instanceof Collection<?> col) {
            for (Object o : col) {
                String t = o == null ? null : o.toString().toUpperCase();
                if (t != null && ALLOWED_RUN_TYPES.contains(t)) {
                    types.add(t);
                } else if (t != null) {
                    throw new BusinessException(400, "RUN-400: 非法 runType: " + o + "（允许 " + ALLOWED_RUN_TYPES + "）");
                }
            }
        } else {
            throw new BusinessException(400, "RUN-400: runTypes 必填（数组，允许 " + ALLOWED_RUN_TYPES + "）");
        }
        if (types.isEmpty()) {
            throw new BusinessException(400, "RUN-400: runTypes 不可为空数组");
        }
        // 去重保序
        return new ArrayList<>(new LinkedHashSet<>(types));
    }

    /**
     * SAFEGUARD 反事实推演 payload 组装（PMO-59 P3b）。
     *
     * <p>param 可选键：counterfactualDomain（缺省 "aviation"，sc001 航空 AOC 贯通口径）/
     * counterfactualVariable（推演主变量，缺省由 dccheng 侧 400 拒绝）/
     * counterfactualInterventions（数组 [{variableName, op:SET/DELTA, value}]）/
     * counterfactualOutcomeValues（{variable:{outcome:value}} 数值映射）/
     * counterfactualSampleCount（缺省 1000）/ counterfactualSeed（缺省 42）。</p>
     */
    private Map<String, Object> buildCounterfactualPayload(String scenarioId, Map<String, Object> param) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scenarioId", scenarioId);
        payload.put("domain", param.get("counterfactualDomain") instanceof String s && !s.isBlank()
                ? s : "aviation");
        payload.put("variableName", param.get("counterfactualVariable"));
        if (param.get("counterfactualInterventions") instanceof List<?> list) {
            payload.put("interventions", list);
        }
        if (param.get("counterfactualOutcomeValues") instanceof Map<?, ?> m) {
            Map<String, Object> baseline = new LinkedHashMap<>();
            baseline.put("outcomeValues", m);
            payload.put("baseline", baseline);
        }
        if (param.get("counterfactualSampleCount") instanceof Number num) {
            payload.put("sampleCount", num.intValue());
        }
        if (param.get("counterfactualSeed") instanceof Number num) {
            payload.put("seed", num.longValue());
        }
        return payload;
    }

    private Map<String, Object> buildForecastPayload(Map<String, Object> param, String metric) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("metric", metric);
        payload.put("domain", "business");
        payload.put("horizon", param.get("horizon") instanceof Number n ? n.intValue() : 3);

        List<Map<String, Object>> series = new ArrayList<>();
        if (param.get("series") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("t", m.get("t") instanceof Number ? ((Number) m.get("t")).longValue() : 0);
                    p.put("value", m.get("value") instanceof Number ? ((Number) m.get("value")).doubleValue() : 0.0);
                    series.add(p);
                }
            }
        }
        if (series.size() < 2) {
            // 无真实历史序列 → 生成确定性伪序列（场景演示/占位），避免 400
            List<Map<String, Object>> synthetic = new ArrayList<>();
            double base = param.get("deviation") instanceof Number n ? (100 + n.doubleValue()) : 100.0;
            for (int i = 0; i < 6; i++) {
                synthetic.add(Map.of("t", i, "value", base * Math.pow(0.97, i)));
            }
            series = synthetic;
        }
        payload.put("series", series);
        return payload;
    }

    @SuppressWarnings("unchecked")
    private boolean isDegraded(Map<String, Object> payload) {
        return payload != null && Boolean.TRUE.equals(payload.get("degraded"));
    }

    private boolean isError(Map<String, Object> payload) {
        return payload != null && "error".equals(payload.get("kind"));
    }

    private Map<String, Object> errorPayload(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "error");
        m.put("message", msg);
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castStringMap(Map<?, ?> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        m.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }

    private String toJson(Object o) {
        try {
            if (o == null) {
                return "{}";
            }
            // JsonNode（SAFEGUARD 反事实推演结果）直取规范树文本：
            // 节点树 toString 为无损规范 JSON（数字/数组/嵌套原语义），
            // 不经注入 ObjectMapper 的 MessageConverter 配置，杜绝数值转字符串的二次污染
            if (o instanceof com.fasterxml.jackson.databind.JsonNode node) {
                return node.toString();
            }
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            log.warn("运行结果 JSON 序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    private String currentOperator() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String) {
                return (String) auth.getPrincipal();
            }
        } catch (Exception e) {
            log.debug("无法取操作人，回退 system: {}", e.getMessage());
        }
        return "system";
    }
}
