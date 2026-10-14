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
        return submit(artifact, null, scenarioId, runType, traceId, null, null);
    }

    /**
     * 提交一次确定性产物（<b>全要素形态</b>，B2-② 批新增，旧 4 参签名保留委派 ⇒ API 只增不改）。
     *
     * <p><b>本重载修的是一处真实缺陷</b>：{@link #WHERE_COLUMNS} 声明以
     * {@code forecast_run_id/caliber_id/caliber_version/as_of_time} 四列做 UPSERT 定位，
     * 而原 {@code headerRow} 只写 5 列、<b>从不带</b> caliber_id/caliber_version/as_of_time
     * ⇒ 通道按 NULL 列匹配，"同键重放不产生第二行"在数据层无从命中（V197 列一直存在，
     * 是写侧漏填）。补齐后六要素锚定列与 WHERE 列一致。</p>
     *
     * <p>幂等/指纹两列（V287）随产物一并落库：{@code idempotency_key} 供 data-engine
     * 幂等键读端点定位既有 run，{@code content_hash} 供编排面判"同键异指纹 ⇒ 409 冲突"。
     * 二者均为存值真值，禁在读侧回推伪造。</p>
     *
     * @param artifact      {@link DeterministicForecastCalculator#compute} 装配的产物
     * @param factors       六要素（可空 = 沿用旧形态只写 5 列；非空时补齐口径/时点三列）
     * @param scenarioId    场景业务键（必填，V197 NOT NULL 列）
     * @param runType       运行类型（DIAGNOSE/FORECAST/SIMULATE/STRATEGY/FULL）
     * @param traceId       全链路追踪 ID（必填，写通道四必带项之一）
     * @param snapshotId    输入快照键（V277 列，可空 = 历史形态无快照锚定，禁伪造）
     * @param baselineRunId 反事实/情景重算基准运行键（V197 列，可空）
     */
    public WriteAck submit(DeterministicForecastCalculator.Artifact artifact,
                           DeterministicForecastCalculator.SixFactors factors,
                           String scenarioId,
                           String runType,
                           String traceId,
                           String snapshotId,
                           String baselineRunId) {
        return submit(artifact, factors, scenarioId, runType, traceId, snapshotId, baselineRunId, null, null);
    }

    /**
     * 提交一次确定性产物（<b>幂等锚外置形态</b>，B2-② 批追加，上一 7 参签名保留委派 ⇒ API 只增不改）。
     *
     * <p><b>裁决 D-B2②-1（幂等键/内容指纹属主 = 调用方）</b>：{@code DeterministicForecastCalculator}
     * 自算的 {@code artifact.idempotencyKey()} 是「口径域:六要素指纹」，含 {@code forecastRunId} ⇒
     * 每次重试必不同；而编排契约（{@code ForecastRunRequestValidator} 必填的
     * {@code idempotencyKey}/{@code contentHash}）表达的是「同一请求重放」语义。
     * 若库里只落引擎派生键，编排面按调用方键查 {@code findByIdempotencyKey} 将<b>永远查不到</b>
     * ⇒ 幂等复用与"同键异指纹 409"双双形同虚设（真断点，非风格问题）。
     * 故本重载允许调用方传入外置锚：非空 ⇒ 落 {@code idempotency_key}/{@code content_hash} 两列，
     * 为空 ⇒ 沿用产物派生值（旧 7 参行为零变化）。产物自算哈希仍在 canonicalBytes 内可复核。</p>
     *
     * @param requestIdempotencyKey 调用方幂等键（可空 = 沿用产物派生键）
     * @param requestContentHash    调用方内容指纹（可空 = 沿用产物指纹）
     */
    public WriteAck submit(DeterministicForecastCalculator.Artifact artifact,
                           DeterministicForecastCalculator.SixFactors factors,
                           String scenarioId,
                           String runType,
                           String traceId,
                           String snapshotId,
                           String baselineRunId,
                           String requestIdempotencyKey,
                           String requestContentHash) {
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
        body.put("rows", headerRow(artifact, factors, scenarioId, runType, snapshotId, baselineRunId,
                requestIdempotencyKey, requestContentHash));

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
                                          DeterministicForecastCalculator.SixFactors factors,
                                          String scenarioId,
                                          String runType,
                                          String snapshotId,
                                          String baselineRunId,
                                          String requestIdempotencyKey,
                                          String requestContentHash) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("forecast_run_id", artifact.forecastRunId());
        row.put("scenario_id", scenarioId);
        row.put("run_type", runType == null ? "FORECAST" : runType);
        row.put("status", "SUCCEEDED");
        row.put("forecast_result_json", new String(artifact.canonicalBytes(), java.nio.charset.StandardCharsets.UTF_8));
        // 幂等锚（V287）：调用方外置锚优先（裁决 D-B2②-1），缺省沿用产物派生真值，禁在读侧回推伪造
        row.put("idempotency_key", orKeep(requestIdempotencyKey, artifact.idempotencyKey()));
        row.put("content_hash", orKeep(requestContentHash, artifact.contentHash()));
        if (factors != null) {
            // WHERE 定位三列补齐（修复"声明参与 WHERE 却从不写值"的漏填）
            row.put("caliber_id", factors.caliberId());
            row.put("caliber_version", factors.caliberVersion());
            row.put("as_of_time", factors.asOfTime());
        }
        if (snapshotId != null && !snapshotId.isBlank()) {
            row.put("snapshot_id", snapshotId);      // V277 列，缺位不写（禁空串冒充快照锚定）
        }
        if (baselineRunId != null && !baselineRunId.isBlank()) {
            row.put("baseline_run_id", baselineRunId);
        }
        return row;
    }

    /** 外置锚优先、缺省沿用产物派生值（两值均为存值真值，禁造默认占位）。 */
    private static String orKeep(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
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
