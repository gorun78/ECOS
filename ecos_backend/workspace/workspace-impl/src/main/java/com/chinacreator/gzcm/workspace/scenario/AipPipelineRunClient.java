package com.chinacreator.gzcm.workspace.scenario;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.common.security.caller.CallerCredentialSource;

/**
 * 逻辑编排执行客户端（2026-11-23 场景智能逻辑承接批）。
 *
 * <p>场景运行 {@code analysisType=PIPELINE} 时，把绑定的编排 id 交给属主 ai-engine：
 * {@code POST /api/v1/aip/pipelines/{id}/execute}（行为语义见 ai-engine
 * {@code AipPipelineExecutionService}：图取自 definition_json、六类节点真实执行、同步等终态）。</p>
 *
 * <p><b>真值纪律</b>：属主不可达 / 应答缺 {@code data} / 业务码非 0 三种情况一律抛
 * {@link AipPipelineUnavailableException}，由调用方折算成 503（禁合成"执行成功"）；
 * 编排自身的执行失败（节点 failed / 超时）是<b>正常返回</b>，由 {@code degraded} 标记，
 * 不抛——那是业务结果，不是通道故障。</p>
 */
@Service
public class AipPipelineRunClient {

    private static final Logger log = LoggerFactory.getLogger(AipPipelineRunClient.class);

    private final RestTemplate restTemplate;

    public AipPipelineRunClient(@Qualifier("workspaceAipPipelineRest") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 执行一条编排。
     *
     * @param pipelineId 编排 id（场景绑定 target_id）
     * @param inputs     运行入参（可空）
     * @return 属主 data 段（含 status/degraded/nodeTraces/outputs 等）
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, Object> execute(String pipelineId, Map<String, Object> inputs) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        // 跨服务必须中继调用方凭证：ai-engine 承流面（aiming :18084）默认 DENY 裸调
        CallerCredentialSource.applyTo(headers);
        Map<String, Object> body = inputs == null ? Map.of() : inputs;
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    "/aip/pipelines/" + encode(pipelineId) + "/execute",
                    HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
            Map<String, Object> envelope = resp.getBody();
            if (envelope == null) {
                throw new AipPipelineUnavailableException("编排执行应答为空体: " + pipelineId);
            }
            Object code = envelope.get("code");
            if (code instanceof Number n && n.intValue() != 0) {
                throw new AipPipelineUnavailableException(
                        "编排执行业务错误 code=" + n.intValue() + " " + envelope.get("message"));
            }
            Object data = envelope.get("data");
            if (!(data instanceof Map<?, ?> dm)) {
                throw new AipPipelineUnavailableException("编排执行应答缺 data 段: " + pipelineId);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            ((Map<String, Object>) dm).forEach(out::put);
            log.info("编排执行 pipeline={} status={} degraded={}",
                    pipelineId, out.get("status"), out.get("degraded"));
            return out;
        } catch (AipPipelineUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("编排执行通道不可用 pipeline={} err={}", pipelineId, e.getMessage());
            throw new AipPipelineUnavailableException("ai-engine 编排执行不可用: " + e.getMessage(), e);
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
