package com.chinacreator.gzcm.workspace.scenario;

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
