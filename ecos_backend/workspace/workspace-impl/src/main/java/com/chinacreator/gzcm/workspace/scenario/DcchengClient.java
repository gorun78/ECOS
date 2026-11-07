package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.common.security.caller.CallerCredentialSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * dccheng(cognitive 宿主) 客户端 — 场景运行编排专用（PMO-52 T2）。
 *
 * <p>跨服务调用统一走 cognitive 对外端点。卷07 F07-17 底座收口：base-url 不再由
 * {@code ecos.cognitive-base} 硬编码默认值决定，改注入
 * {@code WorkspaceRestClientConfig#workspaceCognitiveInvokeRest} 共享 Bean
 * （rootUri 经 runtime-access ServiceEndpointResolver 两态寻址：单体/未切流经
 * gateway :8080，切流后指向 cognitive 承流端口），本类只传相对路径。
 * 30s 硬超时对齐认知引擎"推理不能 >30s"红线，超时口径在 config 集中下发。</p>
 */
@Service
public class DcchengClient {

    private static final Logger log = LoggerFactory.getLogger(DcchengClient.class);

    private final RestTemplate restTemplate;

    public DcchengClient(@Qualifier("workspaceCognitiveInvokeRest") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 场景认知 E1 读取（B40/P1-16）：{@code GET {base}/business/scenarios/{id}/cognition/detect}。
     * 返回 {@code data} 原值（通常为 List&lt;Map&gt;；形态不符返回空表，不伪造）。
     */
    public Object detectScenarioMinds(String scenarioId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        CallerCredentialSource.applyTo(headers);
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    "/business/scenarios/" + scenarioId + "/cognition/detect",
                    org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> body = resp.getBody();
            if (body == null) {
                throw new CognitiveEngineUnavailableException("cognitive detect 应答为空体");
            }
            Object code = body.get("code");
            if (code instanceof Number n && n.intValue() != 0) {
                throw new CognitiveEngineUnavailableException("cognitive detect 业务错误 code=" + n);
            }
            return body.get("data");
        } catch (CognitiveEngineUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new CognitiveEngineUnavailableException("cognitive detect 不可用: " + e.getMessage(), e);
        }
    }

    /** B44/P1-20：认知域 Mind 注册（{@code POST {base}/cognitive/minds}，V187+V188 权威写面）。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> registerMind(Map<String, Object> payload) {
        return post("/cognitive/minds", payload);
    }

    /**
     * P0-2 读面：场景绑定 Mind 列表（{@code GET {base}/cognitive/scenarios/{id}/minds}）。
     * 返回 {@code data} 原值（数组）；空数组 = 真实无绑定（不伪造），形状异常/通道故障 = 503 载体。
     */
    @SuppressWarnings("unchecked")
    public java.util.List<Map<String, Object>> listScenarioMinds(String scenarioId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        CallerCredentialSource.applyTo(headers);
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    "/cognitive/minds/by-scenario/" + encode(scenarioId),
                    org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> body = resp.getBody();
            if (body == null) {
                throw new CognitiveEngineUnavailableException("cognitive minds 应答为空体");
            }
            Object code = body.get("code");
            if (code instanceof Number n && n.intValue() != 0) {
                throw new CognitiveEngineUnavailableException("cognitive minds 业务错误 code=" + n);
            }
            Object data = body.get("data");
            if (data == null) {
                return java.util.List.of();
            }
            if (!(data instanceof java.util.List)) {
                throw new CognitiveEngineUnavailableException("cognitive minds 应答形状异常: data 非数组");
            }
            return (java.util.List<Map<String, Object>>) data;
        } catch (CognitiveEngineUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new CognitiveEngineUnavailableException("cognitive minds 不可用: " + e.getMessage(), e);
        }
    }

    /** B44/P1-20：认知域 Mind 解绑（{@code DELETE {base}/cognitive/minds/{id}?scenarioId=}）。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> unbindMind(String mindRef, String scenarioId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        CallerCredentialSource.applyTo(headers);
        try {
            String path = "/cognitive/minds/" + mindRef + "?scenarioId=" + encode(scenarioId);
            ResponseEntity<Map> resp = restTemplate.exchange(path,
                    org.springframework.http.HttpMethod.DELETE, new HttpEntity<>(headers), Map.class);
            Map<String, Object> body = resp.getBody();
            return body == null ? Map.of() : (Map<String, Object>) body.get("data");
        } catch (Exception e) {
            throw new CognitiveEngineUnavailableException("cognitive mind 解绑不可用: " + e.getMessage(), e);
        }
    }

    /** B44/P1-20：切换认知 Mind 激活位（{@code PUT {base}/cognitive/minds/{id}/active}）。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> setMindActive(String mindRef, boolean active) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        CallerCredentialSource.applyTo(headers);
        try {
            String path = "/cognitive/minds/" + mindRef + "/active?active=" + active + "&operator=workspace";
            ResponseEntity<Map> resp = restTemplate.exchange(path,
                    org.springframework.http.HttpMethod.PUT, new HttpEntity<>(headers), Map.class);
            Map<String, Object> body = resp.getBody();
            return body == null ? Map.of() : (Map<String, Object>) body.get("data");
        } catch (Exception e) {
            throw new CognitiveEngineUnavailableException("cognitive mind 激活切换不可用: " + e.getMessage(), e);
        }
    }

    private static String encode(String raw) {
        return java.net.URLEncoder.encode(raw == null ? "" : raw, java.nio.charset.StandardCharsets.UTF_8);
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
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // 显式 JSON Accept — 缺省时 String.class 目标会让 RestTemplate 发 text/plain, */*，
            // 网关侧按 Accept 选 converter 时编缀 XML 系 converter（实证：返回 <ApiResponse> 伪 XML 文本）
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            // B32/P1-12：中继调用方凭证（Authorization + 四信任头，单源 CallerCredentialSource）。
            // 原裸调 ⇒ gateway 侧按匿名拒绝 403 ⇒ 场景运行 DIAGNOSE/FORECAST 恒降级。
            CallerCredentialSource.applyTo(headers);
            String body = RAW_JSON.writeValueAsString(payload);
            String resp = restTemplate.postForObject(path,
                    new HttpEntity<>(body, headers), String.class);
            if (resp == null || resp.isBlank()) {
                throw new IllegalStateException("dccheng 返回空: " + path);
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
            log.warn("dccheng 调用失败 {}: {}", path, e.getMessage());
            // #142/B4-3e-41（D-B4-71 属主侧第二批，workspace 域）：下游认知服务调用失败＝
            // 「服务不可用」，按 §4.7.2 落本域既有 503 载体（COGNITIVE_UNAVAILABLE），
            // 禁再以 IllegalStateException 落 advice 的 500 兜底。原文经 technicalReason 只进日志面（X-41）。
            throw new CognitiveEngineUnavailableException("cognitive 服务不可用: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> post(String path, Map<String, Object> payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // B32/P1-12：同 postJson —— 中继调用方凭证，禁裸调匿名（fail-closed 会全数 403）。
            CallerCredentialSource.applyTo(headers);
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(path,
                    new HttpEntity<>(payload, headers), Map.class);
            if (resp == null) {
                throw new IllegalStateException("dccheng 返回空: " + path);
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
            log.warn("dccheng 调用失败 {}: {}", path, e.getMessage());
            // #142/B4-3e-41（D-B4-71 属主侧第二批，workspace 域）：下游认知服务调用失败＝
            // 「服务不可用」，按 §4.7.2 落本域既有 503 载体（COGNITIVE_UNAVAILABLE），
            // 禁再以 IllegalStateException 落 advice 的 500 兜底。原文经 technicalReason 只进日志面（X-41）。
            throw new CognitiveEngineUnavailableException("cognitive 服务不可用: " + e.getMessage(), e);
        }
    }
}
