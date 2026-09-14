package com.chinacreator.gzcm.workspace.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * dccheng(cognitive 宿主) 客户端 — 场景运行编排专用（PMO-52 T2）。
 *
 * <p>跨服务调用统一走 cognitive 对外端点，默认经 gateway :8080（单体双跑等价：
 * workspace :18090 → gateway :8080 → 同 JVM cognitive controller），
 * 生产微服务态由 {@code ecos.cognitive-base} 指向 gateway 或内网 dccheng 端口。
 * 30s 硬超时对齐认知引擎"推理不能 >30s"红线。</p>
 */
@Service
public class DcchengClient {

    private static final Logger log = LoggerFactory.getLogger(DcchengClient.class);

    private final RestTemplate restTemplate;
    private final String base;

    public DcchengClient(@Value("${ecos.cognitive-base:http://localhost:8080/api/v1}") String base) {
        this.base = base;
        this.restTemplate = new RestTemplate(newTimeoutFactory(Duration.ofSeconds(30)));
    }

    /** 因果诊断 POST {base}/cognitive/diagnose */
    public Map<String, Object> diagnose(Map<String, Object> payload) {
        return post("/cognitive/diagnose", payload);
    }

    /** 时序预测 POST {base}/cognitive/forecast */
    public Map<String, Object> forecast(Map<String, Object> payload) {
        return post("/cognitive/forecast", payload);
    }

    /** 仿真情景推演 POST {base}/cognitive/scenario/simulate */
    public Map<String, Object> simulate(Map<String, Object> payload) {
        return post("/cognitive/scenario/simulate", payload);
    }

    /** 跨引擎计划 POST {base}/cognitive/plan（策略/多 Agent 计划） */
    public Map<String, Object> plan(Map<String, Object> payload) {
        return post("/cognitive/plan", payload);
    }

    /** 原始 JSON 通道专用解析器（与 Web 层 MessageConverter 配置隔离，保结构语义）。 */
    private static final ObjectMapper RAW_JSON = new ObjectMapper();

    /**
     * 反事实推演 POST {base}/cognitive/counterfactual（PMO-59 P3b SAFEGUARD）。
     *
     * <p>payload = CounterfactualRequest 契约（domain/variableName/interventions/sampleCount/seed/
     * baseline.outcomeValues）；返回 CounterfactualResult 的<b>原始 JSON 树</b>（不做 Map 双重序列化——
     * 实证 {@code Map.class} 往返会数值转字符串且破坏 sensitivityTop3/volatilityRange/
     * excludedAssumptions 数组结构，污染落库 JSONB 与失效作废联动的 assumptionRefs 关联键）。
     * 四指标/assumptionRefs 保持 gateway 原始 JSON 语义，落 {@code ecos_scenario_run.simulation_result} 留痕。</p>
     */
    public JsonNode counterfactual(Map<String, Object> payload) {
        return postJson("/cognitive/counterfactual", payload);
    }

    /**
     * 原始 JSON 通道（仅新端点使用，0 触碰既有 4 方法行为）：
     * body Map → JSON 文本发送；响应收<b>原始 JSON 文本</b>（StringHttpMessageConverter 直传，
     * 0 结构解析），再经独立 {@link #RAW_JSON} 解析为 JsonNode 树（保数字/数组/嵌套原语义 +
     * 稳态返回树供调用方 toJson 落库，结构零失真）。
     */
    private JsonNode postJson(String path, Map<String, Object> payload) {
        String url = base + path;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // 显式 JSON Accept — 缺省时 String.class 目标会让 RestTemplate 发 text/plain, */*，
            // 网关侧按 Accept 选 converter 时编缀 XML 系 converter（实证：返回 <ApiResponse> 伪 XML 文本）
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            String body = RAW_JSON.writeValueAsString(payload);
            String resp = restTemplate.postForObject(url,
                    new HttpEntity<>(body, headers), String.class);
            if (resp == null || resp.isBlank()) {
                throw new IllegalStateException("dccheng 返回空: " + url);
            }
            JsonNode root = RAW_JSON.readTree(resp);
            JsonNode code = root.path("code");
            if (code.isNumber() && code.asInt() != 0) {
                throw new IllegalStateException("dccheng 业务错误 code=" + code.asInt()
                        + " msg=" + root.path("message").asText());
            }
            JsonNode data = root.path("data");
            return data.isMissingNode() || data.isNull() ? root : data;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("dccheng 调用失败 {}: {}", url, e.getMessage());
            throw new IllegalStateException("cognitive 服务不可用: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> post(String path, Map<String, Object> payload) {
        String url = base + path;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> resp = restTemplate.postForObject(url,
                    new HttpEntity<>(payload, headers), Map.class);
            if (resp == null) {
                throw new IllegalStateException("dccheng 返回空: " + url);
            }
            // ApiResponse 统一体: code=0 成功
            Object code = resp.get("code");
            if (code instanceof Number && ((Number) code).intValue() != 0) {
                throw new IllegalStateException("dccheng 业务错误 code=" + code + " msg=" + resp.get("message"));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = resp.get("data") instanceof Map
                    ? (Map<String, Object>) resp.get("data") : resp;
            return data;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("dccheng 调用失败 {}: {}", url, e.getMessage());
            throw new IllegalStateException("cognitive 服务不可用: " + e.getMessage(), e);
        }
    }

    private static SimpleClientHttpRequestFactory newTimeoutFactory(Duration d) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) d.toMillis());
        f.setReadTimeout((int) d.toMillis());
        return f;
    }
}
