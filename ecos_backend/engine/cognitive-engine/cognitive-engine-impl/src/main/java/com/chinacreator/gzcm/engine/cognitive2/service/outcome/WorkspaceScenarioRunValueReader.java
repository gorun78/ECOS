package com.chinacreator.gzcm.engine.cognitive2.service.outcome;

import com.chinacreator.gzcm.engine.cognitive.api.context.CalculationResultRef;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 结果评估数值接缝实现（C-2；2026-11-07 验证报告）。
 *
 * <p><b>数值主权</b>：预测值只从场景运行已落库的确定性产物
 * {@code ecos_ai.ecos_scenario_run.forecast_result} 读取（由 workspace
 * {@code GET /api/v1/workspace/scenarios/{id}/runs} 只读回放），认知侧<b>不自算、不插值、
 * 不让 LLM 生成</b>（§0.3 红线 6 / DG-6）；实际值侧当前无权威资产投影通道，
 * <b>逐指标返回 unavailableReason，禁以 0 或伪造值填充</b>。</p>
 *
 * <p><b>为什么走 REST 而不是直连 DB</b>：跨域读只经属主只读接口（ST08 同纪律）；
 * 基址经 runtime-access {@link ServiceEndpointResolver} 两态寻址，HTTP 客户端复用
 * {@code cognitiveRestTemplate}（含调用方凭据中继），禁自拼端口/自建客户端。</p>
 *
 * <p><b>诚实边界</b>：① 运行不存在/读通道失败 ⇒ 该指标 unavailableReason 明示（不抛断整次评估）；
 * ② 指标名与运行 metric 不匹配 ⇒ 明示 mismatch，不拿别的指标值顶替；
 * ③ 预测点与评估窗口的 horizon 对齐尚无权威映射表 ⇒ 采用运行产物首个点，
 * 在 calculationRef 上带 runId 供回查（该映射缺口登记在报告 C-2 遗留项）。</p>
 */
@Component
public class WorkspaceScenarioRunValueReader implements OutcomeMetricValueReader {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceScenarioRunValueReader.class);

    static final String RUNS_PATH = "/api/v1/workspace/scenarios/%s/runs?limit=50";
    static final String REASON_ACTUAL_ABSENT = "actual value ref absent (no authoritative actual projection)";
    static final String REASON_ACTUAL_NOT_DEPLOYED =
            "actual value projection channel not deployed (asset-level actuals unavailable)";

    private final RestTemplate restTemplate;
    private final ServiceEndpointResolver endpointResolver;

    public WorkspaceScenarioRunValueReader(
            @Qualifier("cognitiveRestTemplate") RestTemplate restTemplate,
            ServiceEndpointResolver endpointResolver) {
        this.restTemplate = restTemplate;
        this.endpointResolver = endpointResolver;
    }

    @Override
    public List<ResolvedValue> resolve(ResolveQuery query) {
        List<ResolvedValue> out = new ArrayList<>();
        if (query == null || query.pairs() == null || query.pairs().isEmpty()) {
            return out;
        }
        Map<String, Object> run = findRun(query);
        for (MetricRefPair pair : query.pairs()) {
            out.add(resolveOne(query, run, pair));
        }
        return out;
    }

    private ResolvedValue resolveOne(ResolveQuery query, Map<String, Object> run, MetricRefPair pair) {
        String metricRef = pair.metricRef();
        String actualReason = pair.actualRef() == null ? REASON_ACTUAL_ABSENT : REASON_ACTUAL_NOT_DEPLOYED;

        if (run == null) {
            return new ResolvedValue(metricRef, null, null, runIdOf(query, pair),
                    Map.of(), "forecast run not found or read channel unavailable");
        }
        String runMetric = text(run.get("metric"));
        if (metricRef != null && runMetric != null && !metricRef.isBlank()
                && !runMetric.equalsIgnoreCase(metricRef)) {
            return new ResolvedValue(metricRef, null, null, text(run.get("id")),
                    Map.of(), "metric mismatch: run metric=" + runMetric);
        }
        BigDecimal predicted = firstPointValue(run.get("forecast_result"));
        if (predicted == null) {
            Object raw = run.get("forecast_result");
            String sample = raw == null ? "null" : String.valueOf(raw);
            log.warn("结果评估数值接缝：运行 {} 的 forecast_result 无法解析 class={} sample={}",
                    run.get("id"), raw == null ? "null" : raw.getClass().getSimpleName(),
                    sample.substring(0, Math.min(240, sample.length())));
            return new ResolvedValue(metricRef, null, null, text(run.get("id")),
                    Map.of(), "forecast result has no point value (run produced no deterministic number)");
        }
        return new ResolvedValue(metricRef, predicted, null, text(run.get("id")),
                Map.of(), actualReason);
    }

    private String runIdOf(ResolveQuery query, MetricRefPair pair) {
        CalculationResultRef ref = pair.forecastRef();
        if (ref != null && ref.getCalculationId() != null && !ref.getCalculationId().isBlank()) {
            return ref.getCalculationId();
        }
        return query.forecastRunId();
    }

    /** 读运行历史并按 runId 命中；任何通道失败都返回 null（由调用侧逐指标写明原因，禁折叠成 0）。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> findRun(ResolveQuery query) {
        String wanted = firstNonBlank(
                query.forecastRunId(),
                firstForecastCalculationId(query.pairs()));
        if (wanted == null || query.scenarioId() == null || query.scenarioId().isBlank()) {
            return null;
        }
        String base = endpointResolver
                .resolve("workspace", ServiceEndpointResolver.RouteMode.MONOLITH)
                .orElse(null);
        if (base == null) {
            log.warn("结果评估数值接缝：route-manifest 未登记 workspace，按不可用处置");
            return null;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        String url = base + String.format(RUNS_PATH, query.scenarioId());
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers), Map.class);
            Map<String, Object> body = resp.getBody();
            if (body == null) {
                return null;
            }
            Object code = body.get("code");
            if (code instanceof Number n && n.intValue() != 0) {
                log.warn("结果评估数值接缝：运行历史业务错误 code={}", n);
                return null;
            }
            Object data = body.get("data");
            if (!(data instanceof List<?> rows)) {
                return null;
            }
            for (Object o : rows) {
                if (o instanceof Map<?, ?> row && wanted.equals(text(row.get("id")))) {
                    return (Map<String, Object>) row;
                }
            }
            return null;
        } catch (RestClientException | ClassCastException e) {
            log.warn("结果评估数值接缝：运行历史读取失败 {}", e.getMessage());
            return null;
        }
    }

    private static String firstForecastCalculationId(List<MetricRefPair> pairs) {
        for (MetricRefPair p : pairs) {
            CalculationResultRef ref = p.forecastRef();
            if (ref != null && ref.getCalculationId() != null && !ref.getCalculationId().isBlank()) {
                return ref.getCalculationId();
            }
        }
        return null;
    }

    /** 运行产物首点值：支持 Map（Jackson 解析态）与 JSON 字符串两种承载。 */
    static BigDecimal firstPointValue(Object forecastResult) {
        Object node = forecastResult;
        if (node instanceof String s) {
            if (s.isBlank()) return null;
            try {
                node = new com.fasterxml.jackson.databind.ObjectMapper().readValue(s, Map.class);
            } catch (Exception e) {
                return null;
            }
        }
        if (!(node instanceof Map<?, ?> map)) {
            return null;
        }
        // workspace 运行历史经 JDBC 返回 PGobject，Jackson 序列化为 {type:"jsonb", value:"<json>"}；
        // 先解包再取点值（2026-11-08 活体实证）。
        if ("jsonb".equals(String.valueOf(map.get("type"))) && map.get("value") instanceof String rawJson) {
            return firstPointValue(rawJson);
        }
        Object points = map.get("points");
        if (points instanceof Map<?, ?> pm) {
            points = pm.get("points");
        }
        if (points instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            return decimal(first.get("value"));
        }
        return decimal(map.get("value"));
    }

    private static BigDecimal decimal(Object v) {
        if (v == null) return null;
        try {
            return new BigDecimal(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b != null && !b.isBlank() ? b : null;
    }

    /** 供测试与诊断：解析结果的稳定键序（本类不缓存，零状态）。 */
    static Map<String, Object> describe(ResolvedValue v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("metricRef", v.metricRef());
        m.put("predicted", v.predicted());
        m.put("actual", v.actual());
        m.put("calculationRef", v.calculationRef());
        m.put("unavailableReason", v.unavailableReason());
        return m;
    }
}
