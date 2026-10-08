package com.chinacreator.gzcm.engine.cognitive2.service.forecast;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.objects.CognitiveObjectClass;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.DeterministicForecastCalculator;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 确定性产物提交客户端（PRD-05 §6.4 REQ-COG-09 规格 2 / ADR-14 / R-17 ①）。
 *
 * <p><b>唯一写通道</b>：认知域产物一律经 data-engine 的业务域唯一写通道
 * （{@code POST /api/v1/datanet/write-channel}，F02-05 收口）提交，落
 * {@code ecos_dw.ecos_application_forecast_run}（{@link CognitiveObjectClass#PREDICTION} 的权威载体）。
 * 认知侧<b>禁止</b>直连 {@code ecos_dw}、禁止在引擎控制 schema（{@code ecos_cognitive} /
 * {@code ecos_knowledge}）建金额结果表（ST08 / 铁律 §0.6）。</p>
 *
 * <p><b>fail-loud</b>：route-manifest 未登记 {@code datanet}、通道不可达或通道裁决失败
 * ⇒ 抛 {@link CognitiveGuardException}（{@code ECOS-COG-504}），禁 "log.warn + 视为成功"。
 * MC02：明细走 {@code *_json} TEXT 列，该列<b>不进</b> {@code whereColumns}
 * （data-engine 侧对参与 WHERE 的裸 JSON 列直接拒绝）。</p>
 */
@Component
public class ForecastArtifactWriteChannel {

    private static final Logger log = LoggerFactory.getLogger(ForecastArtifactWriteChannel.class);

    /** 业务域唯一写通道路径（owner = datanet，route-manifest 单源）。 */
    private static final String WRITE_CHANNEL_PATH = "/api/v1/datanet/write-channel";
    private static final String DATA_OWNER_SERVICE = "datanet";
    private static final String LAYER_APPLICATION = "APPLICATION";
    /** 六要素定位列（参与 WHERE，均为标量列，无裸 JSON）。 */
    private static final List<String> WHERE_COLUMNS =
            List.of("forecast_run_id", "caliber_id", "caliber_version", "as_of_time");

    private final RestTemplate restTemplate;
    private final String writeChannelUrl;

    public ForecastArtifactWriteChannel(@Qualifier("cognitiveRestTemplate") RestTemplate restTemplate,
                                        ServiceEndpointResolver endpointResolver) {
        this.restTemplate = restTemplate;
        // 与 EntityLinker 同纪律：基址经 runtime-access 两态寻址解析，禁硬编码 localhost（X-35）
        String base = endpointResolver
                .resolve(DATA_OWNER_SERVICE, ServiceEndpointResolver.RouteMode.MONOLITH)
                .orElseThrow(() -> new CognitiveGuardException(CognitiveErrorCode.E504_INTERNAL,
                        CognitiveErrorCode.E504_INTERNAL + ": route-manifest 未登记服务 "
                                + DATA_OWNER_SERVICE + "，data-engine 唯一写通道基址无法解析（fail-loud）"));
        this.writeChannelUrl = base + WRITE_CHANNEL_PATH;
    }

    /** 通道回执（writeId / declared / idempotentReplay 由 data-engine 侧判定）。 */
    public record WriteAck(String writeId, boolean declared, boolean idempotentReplay) {}

    /**
     * 提交一次确定性产物（同 {@code artifact.idempotencyKey()} 重放不产生第二行）。
     *
     * @param artifact  由 {@link DeterministicForecastCalculator#compute} 装配的产物
     * @param scenarioId 场景业务键（必填，V197 NOT NULL 列）
     * @param runType   运行类型（DIAGNOSE/FORECAST/SIMULATE/STRATEGY/FULL）
     * @param traceId   全链路追踪 ID（必填，与响应 {@code data.traceId} 同源）
     */
    public WriteAck submit(DeterministicForecastCalculator.Artifact artifact,
                           String scenarioId,
                           String runType,
                           String traceId) {
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.E400_INVALID_PARAM + ": scenarioId 必填（写通道 carrier 定位要素残缺）");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.E400_INVALID_PARAM + ": traceId 必填（写通道四必带项之一）");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("layer", LAYER_APPLICATION);
        body.put("carrierRef", CognitiveObjectClass.PREDICTION.carrier().orElseThrow(
                () -> new IllegalStateException("PREDICTION 载体缺失（目录不变量被破坏）")));
        body.put("traceId", traceId);
        body.put("idempotencyKey", artifact.idempotencyKey());
        body.put("whereColumns", WHERE_COLUMNS);
        body.put("rows", headerRow(artifact, scenarioId, runType));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> resp;
        try {
            resp = restTemplate.postForObject(writeChannelUrl, new HttpEntity<>(body, headers), Map.class);
        } catch (RestClientException e) {
            log.error("data-engine 唯一写通道不可达: carrier={} err={}",
                    body.get("carrierRef"), e.getMessage());
            throw new CognitiveGuardException(CognitiveErrorCode.E504_INTERNAL,
                    CognitiveErrorCode.E504_INTERNAL + ": 确定性产物提交失败（写通道不可达），"
                            + "禁回落直写 ecos_dw（ST08）", "datanet.write-channel");
        }
        if (resp == null) {
            throw new CognitiveGuardException(CognitiveErrorCode.E504_INTERNAL,
                    CognitiveErrorCode.E504_INTERNAL + ": 写通道返回空响应，产物落点未确认");
        }
        Object data = resp.get("data");
        boolean ok = Integer.valueOf(0).equals(asInt(resp.get("code")));
        if (!ok || !(data instanceof Map)) {
            throw new CognitiveGuardException(CognitiveErrorCode.E504_INTERNAL,
                    CognitiveErrorCode.E504_INTERNAL + ": 写通道裁决失败 code=" + resp.get("code")
                            + " message=" + resp.get("message"));
        }
        Map<String, Object> d = (Map<String, Object>) data;
        return new WriteAck(String.valueOf(d.get("writeId")),
                Boolean.TRUE.equals(d.get("declared")),
                Boolean.TRUE.equals(d.get("idempotentReplay")));
    }

    /** 运行头行（明细走 forecast_result_json TEXT，MC02 不建索引、不参与 WHERE）。 */
    private Map<String, Object> headerRow(DeterministicForecastCalculator.Artifact artifact,
                                          String scenarioId,
                                          String runType) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("forecast_run_id", artifact.forecastRunId());
        row.put("scenario_id", scenarioId);
        row.put("run_type", runType == null ? "FORECAST" : runType);
        row.put("status", "SUCCEEDED");
        row.put("forecast_result_json", new String(artifact.canonicalBytes(), java.nio.charset.StandardCharsets.UTF_8));
        return row;
    }

    private static Integer asInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
