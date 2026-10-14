package com.chinacreator.gzcm.workspace.forecast;

import java.util.ArrayList;
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
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.common.trace.EcosTraceContext;
import com.chinacreator.gzcm.workspace.scenario.ActionValidationException;
import com.chinacreator.gzcm.workspace.scenario.CognitiveEngineUnavailableException;
import com.chinacreator.gzcm.workspace.scenario.ForbiddenOperationException;
import com.chinacreator.gzcm.workspace.security.CallerCredentialRelay;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * {@link ForecastRunGateway} 的生产实现（认知域 REST 适配器，实现差距分析 P0-4 收口）。
 *
 * <p><b>为什么是 REST 适配器而不是 Local bean</b>：cognitive 的 run 计算 bean 无法注入 workspace ——
 * {@code :18090} 的扫描面由 {@code ComponentScanNarrowingTest.FROZEN_SCAN_BASELINE} 冻结且
 * <b>只允许收缩</b>（不含 cognitive 包），且 {@code WorkspaceLayeringArchTest} 禁 workspace 源码
 * import 引擎实现包。故与 {@code DcchengClient}/{@code WorkspaceSecurityEngineClient} 同形：
 * 只经 HTTP 调 cognitive 端点族 {@code /api/v1/cognitive/forecast-runs/**}。</p>
 *
 * <p><b>底座纪律</b>（F07-17 / X-35）：RestTemplate 一律注入
 * {@code WorkspaceRestClientConfig#workspaceCognitiveInvokeRest} 共享 Bean（基址经 runtime-access
 * 两态寻址，aiming 未切流时回落 gateway :8080），本类只传相对路径、不自建客户端、不写死端口；
 * 出站凭证经 {@link CallerCredentialRelay} 中继（无请求上下文时不伪造，保持 fail-closed）。</p>
 *
 * <p><b>假阳性红线</b>（X-44/X-50/DG-9）：本类<b>不产生任何数值</b>，只做协议搬运 + 错误语义翻译。
 * 引擎回 501/stub ⇒ {@link ForecastRunNotLiveException}；回 404 ⇒ {@link ForecastRunNotFoundException}；
 * 回 400/401/409/422 ⇒ {@link ForecastRunRejectedException}（状态码原样）；回 403 ⇒
 * {@code ForbiddenOperationException}；连不上 ⇒ {@link CognitiveEngineUnavailableException}（503）。
 * 区间三值原样透传（缺位 null，R-62 ①）。
 * 未上线的 {@code copyScenario} 显式 501，禁伪回执。</p>
 */
@Component
public class CognitiveForecastRunGateway implements ForecastRunGateway {

    private static final Logger log = LoggerFactory.getLogger(CognitiveForecastRunGateway.class);

    /** 认知域预测 run 端点族（相对 {@code workspaceCognitiveInvokeRest} 的 {@code {aimingBase}/api/v1} 根）。 */
    static final String COMPUTE_PATH = "/cognitive/forecast-runs/compute";
    static final String RUN_PATH = "/cognitive/forecast-runs/{forecastRunId}";
    static final String BY_IDEMPOTENCY_KEY_PATH = "/cognitive/forecast-runs/by-idempotency-key/{idempotencyKey}";
    static final String RESULTS_PATH = RUN_PATH + "/results";
    static final String EVIDENCE_PATH = RUN_PATH + "/evidence";

    private static final String CAPABILITY = "workspace/forecast-run";
    /**
     * 响应体按原始 JSON 文本解析（对齐 DcchengClient 教训：缺省 Accept 会被 converter 编缀成伪 XML 文本）。
     *
     * <p><b>两处金额保真配置，缺一不可</b>（B-2 金额唯一来源铁律：适配器无权改动数值的一个比特）：</p>
     * <ul>
     *   <li>{@code USE_BIG_DECIMAL_FOR_FLOATS} —— 缺省把 {@code 1060000.0000} 落成 DoubleNode，
     *       {@code decimalValue()} 经 double 回映射后 scale 漂移；</li>
     *   <li>{@code JsonNodeFactory.withExactBigDecimals(true)} —— 缺省的 DecimalNode 会
     *       {@code stripTrailingZeros()}，实测把 {@code 1060000.0000} 变成 {@code 1.06E+6}
     *       （数值未变但字面形态被改，落库/回读与台账原文不一致）。</li>
     * </ul>
     */
    private static final ObjectMapper RAW_JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .nodeFactory(JsonNodeFactory.withExactBigDecimals(true))
            .build();

    private final RestTemplate restTemplate;

    public CognitiveForecastRunGateway(@Qualifier("workspaceCognitiveInvokeRest") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public ForecastRunView submit(ForecastRunCommand command) {
        JsonNode data = exchange(HttpMethod.POST, COMPUTE_PATH, computeBody(command));
        ForecastRunView view = toView(data);
        if (view == null || view.getForecastRunId() == null || view.getForecastRunId().isBlank()) {
            // 引擎回执无 run 身份 ⇒ 落点未确认，禁按"命令里的键"造一个视图冒充成功
            throw new CognitiveEngineUnavailableException(
                    "cognitive forecast-runs/compute 回执缺 forecastRunId，run 落点未确认");
        }
        log.info("预测 run 经认知域落库 runId={} status={} degraded={}",
                view.getForecastRunId(), view.getStatus(), view.isDegraded());
        return view;
    }

    @Override
    public ForecastRunView findRun(String forecastRunId) {
        Map<String, String> vars = vars("forecastRunId", forecastRunId);
        JsonNode data = exchange(HttpMethod.GET, RUN_PATH, null, vars);
        return data == null || data.isNull() ? null : toView(data);
    }

    /**
     * 幂等定位：引擎回 {@code data=null} ⇒ 返回 null（"首次运行"正常分支，非资源缺失）；
     * 引擎回 404 ⇒ 同样按 null 处理（本方法语义是"未命中"，禁把 404 当 run 不存在上报）。
     */
    @Override
    public ForecastRunView findByIdempotencyKey(String idempotencyKey) {
        Map<String, String> vars = vars("idempotencyKey", idempotencyKey);
        JsonNode data;
        try {
            data = exchange(HttpMethod.GET, BY_IDEMPOTENCY_KEY_PATH, null, vars);
        } catch (ForecastRunNotFoundException notFound) {
            return null;
        }
        return data == null || data.isNull() ? null : toView(data);
    }

    /** 结果明细原样透传（含 p10/p50/p90 的真值或缺位 null，编排面不插值、不改写，R-62 ①）。 */
    @Override
    public Map<String, Object> readResults(String forecastRunId) {
        JsonNode data = exchange(HttpMethod.GET, RESULTS_PATH, null, vars("forecastRunId", forecastRunId));
        return toFlatMap(data);
    }

    @Override
    public ForecastRunEvidence readEvidence(String forecastRunId) {
        JsonNode data = exchange(HttpMethod.GET, EVIDENCE_PATH, null, vars("forecastRunId", forecastRunId));
        if (data == null || data.isNull()) {
            // 引擎可达但无证据载荷：交回 null 由编排面转"未上线"语义，禁本层造空证据冒充有证据
            return null;
        }
        return new ForecastRunEvidence(
                text(data, "forecastRunId"),
                text(data, "caliberId"),
                text(data, "caliberVersion"),
                text(data, "asOfTime"),
                text(data, "snapshotId"),
                text(data, "formulaVersion"),
                textList(data.path("evidenceRefs")),
                mapList(data.path("evidenceItems")));
    }

    /**
     * 情景复制：<b>本批未上线 ⇒ 诚实 501</b>（X-44 禁假回执）。
     *
     * <p>属主登记：确定性重算端点 {@code /api/v1/datanet/forecast-runs/{runId}/scenario-copy}
     * 全仓 0 实现（详设-09 §十一 H-5 / 09 册 FC-03），认知侧 {@code copyScenario} 亦回 501。
     * 编排面的复制语义当前由 {@code ForecastRunOrchestrationService#scenarioCopy}
     * 走「以源 run 六要素派生新 run 并 submit」闭环，不经本方法；本方法保留是为 SPI 完整性，
     * 待引擎侧真重算上线后改接。</p>
     */
    @Override
    public String copyScenario(String sourceRunId, Map<String, Object> overrides) {
        throw new ForecastRunNotLiveException(CAPABILITY + "/scenario-copy",
                "09册FC-03 确定性重算端点未上线（sourceRunId=" + sourceRunId
                        + "，属主详设-09 §十一 H-5 认知/数据侧）");
    }

    // ── 协议搬运与错误语义翻译 ──

    private Map<String, Object> computeBody(ForecastRunCommand command) {
        Map<String, Object> body = new LinkedHashMap<>();
        // forecastRunId 缺省：run 身份由生产方（认知域）生成，编排面只编排不生产（R-14 ①）
        body.put("scenarioId", command.getScenarioId());
        body.put("runType", command.getRunType());
        body.put("caliberId", command.getCaliberId());
        body.put("caliberVersion", command.getCaliberVersion());
        body.put("asOfTime", command.getAsOfTime());
        body.put("snapshotId", command.getSnapshotId());
        body.put("formulaVersion", command.getFormulaVersion());
        // 幂等锚：编排契约必填项原样下传，落 V287 两列并原样回读（裁决 D-B2②-1）
        body.put("idempotencyKey", command.getIdempotencyKey());
        body.put("contentHash", command.getContentHash());
        body.put("baselineRunId", command.getBaselineRunId());
        body.put("traceId", EcosTraceContext.currentTraceId());
        // 范围要素（projectIds/periods/metric）在 ForecastRunCommand 面无字段 ⇒ 缺省 = 全域装配，
        // 已登记为剩余项（H-5 范围入参面），本层不臆造范围
        return body;
    }

    private JsonNode exchange(HttpMethod method, String path, Map<String, Object> body) {
        return exchange(method, path, body, Map.of());
    }

    private JsonNode exchange(HttpMethod method, String path, Map<String, Object> body,
                              Map<String, String> uriVariables) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        CallerCredentialRelay.applyTo(headers);
        HttpEntity<String> entity;
        try {
            entity = new HttpEntity<>(body == null ? null : RAW_JSON.writeValueAsString(body), headers);
        } catch (Exception e) {
            throw new ForecastRunRejectedException(400, ForecastRunRejectedException.CODE_REJECTED,
                    "预测 run 请求体序列化失败（未出网，禁半截请求冒充提交）: " + e.getMessage());
        }
        ResponseEntity<String> resp;
        try {
            resp = restTemplate.exchange(path, method, entity, String.class, uriVariables);
        } catch (RestClientResponseException e) {
            throw translate(e, path);
        } catch (ResourceAccessException e) {
            throw new CognitiveEngineUnavailableException(
                    "cognitive forecast-runs 通道不可达: " + path + " err=" + e.getMessage());
        } catch (RestClientException e) {
            throw new CognitiveEngineUnavailableException(
                    "cognitive forecast-runs 调用失败: " + path + " err=" + e.getMessage());
        }
        return unwrap(resp.getBody(), method, path);
    }

    /** 统一响应体拆封：{@code code != 0} 一律 fail-loud，禁把业务失败当成功搬运。 */
    private JsonNode unwrap(String rawBody, HttpMethod method, String path) {
        if (rawBody == null || rawBody.isBlank()) {
            throw new CognitiveEngineUnavailableException(
                    "cognitive forecast-runs 空响应体（不可判定，禁按空数据成功）: " + path);
        }
        JsonNode root;
        try {
            root = RAW_JSON.readTree(rawBody);
        } catch (Exception e) {
            throw new CognitiveEngineUnavailableException(
                    "cognitive forecast-runs 响应非 JSON: " + path + " err=" + e.getMessage());
        }
        JsonNode codeNode = root.path("code");
        int code = codeNode.isNumber() ? codeNode.asInt() : -1;
        if (code != 0) {
            throw new CognitiveEngineUnavailableException("cognitive forecast-runs 裁决失败 code=" + code
                    + " message=" + root.path("message").asText("") + " path=" + path);
        }
        JsonNode data = root.path("data");
        log.debug("forecast-runs {} {} ⇒ code=0 dataMissing={}", method, path, data.isMissingNode());
        return data.isMissingNode() ? null : data;
    }

    /**
     * HTTP 4xx/5xx → 领域异常（保住引擎给的真实语义，禁折叠成 200 或泛化 500）。
     *
     * <p>引擎侧矩阵（{@code CognitiveGuardException.httpOf}）：400 缺参/无事实、404 run 不存在、
     * 409 版本过期、501（ECOS-COG-503 + {@code data.stub=true}）能力未上线、503（502/504 族）依赖不可达。</p>
     *
     * <p><b>载体一律取 {@code ScenarioDomainException} 族</b>（404/501 用既有专名异常，400/401/409 用
     * {@link ForecastRunRejectedException}，403 用既有 {@code ForbiddenOperationException}）：
     * {@code BusinessException} 的 HTTP 状态恒 400 且 {@code WorkspaceExceptionHandler} 无其专项映射
     * （落 {@code handleUnexpected} 变 500），用它承载引擎语义会把"409 幂等冲突"和"422 口径不匹配"
     * 洗成同一个 500 —— 正是 X-44 要禁的假语义。</p>
     */
    private RuntimeException translate(RestClientResponseException e, String path) {
        int status = e.getRawStatusCode();
        String errorCode = null;
        String missing = null;
        boolean stub = false;
        try {
            JsonNode root = RAW_JSON.readTree(e.getResponseBodyAsString());
            errorCode = text(root, "errorCode");
            missing = text(root.path("data"), "missing");
            stub = root.path("data").path("stub").asBoolean(false);
        } catch (Exception ignored) {
            // 响应体不可解析：仅按 HTTP 状态归类，禁臆造业务码
        }
        String reason = "cognitive forecast-runs HTTP " + status + " path=" + path
                + (errorCode == null ? "" : " errorCode=" + errorCode)
                + (missing == null ? "" : " missing=" + missing);
        if (status == 404) {
            return new ForecastRunNotFoundException(missing == null ? path : missing);
        }
        if (status == 501 || stub || "ECOS-COG-503".equals(errorCode)) {
            return new ForecastRunNotLiveException(CAPABILITY, reason);
        }
        if (status == 400 || status == 422) {
            return new ForecastRunRejectedException(status, ForecastRunRejectedException.CODE_REJECTED,
                    "认知域拒绝预测 run 请求: " + engineMessage(e)).withEngineDetail(errorCode, missing, path);
        }
        if (status == 401) {
            return new ForecastRunRejectedException(401, ForecastRunRejectedException.CODE_UNAUTHENTICATED,
                    "认知域判定出站凭证缺失或失效（已中继入口凭证）: " + engineMessage(e))
                    .withEngineDetail(errorCode, missing, path);
        }
        if (status == 403) {
            // 403 走既有单源载体（code=PERMISSION_DENIED），不另立第二套权限码
            return new ForbiddenOperationException(CAPABILITY + "（认知域准入裁决拒绝，已中继入口凭证）",
                    Map.of("engineHttpStatus", status, "engineErrorCode", errorCode == null ? "" : errorCode));
        }
        if (status == 409) {
            return new ForecastRunRejectedException(409, ForecastRunRejectedException.CODE_CONFLICT,
                    "认知域判定幂等键/状态冲突: " + engineMessage(e)).withEngineDetail(errorCode, missing, path);
        }
        return new CognitiveEngineUnavailableException(reason);
    }

    /** 引擎响应体的 message（不可解析时回落原文，禁把原文吞成空串冒充"无原因"）。 */
    private static String engineMessage(RestClientResponseException e) {
        String raw = e.getResponseBodyAsString();
        try {
            String message = RAW_JSON.readTree(raw).path("message").asText("");
            return message.isBlank() ? raw : message;
        } catch (Exception ignored) {
            return raw;
        }
    }

    /** 引擎 run 视图 → {@link ForecastRunView}（缺位字段原样 null，禁默认值冒充）。 */
    private static ForecastRunView toView(JsonNode data) {
        if (data == null || data.isNull()) {
            return null;
        }
        String forecastRunId = text(data, "forecastRunId");
        if (forecastRunId == null || forecastRunId.isBlank()) {
            return null;
        }
        JsonNode quantile = data.path("quantile");
        QuantileTriple triple = quantile.isObject()
                ? new QuantileTriple(decimal(quantile, "p10"), decimal(quantile, "p50"),
                        decimal(quantile, "p90"))
                : new QuantileTriple(null, null, null);
        return ForecastRunView.builder(forecastRunId)
                .scenarioId(text(data, "scenarioId"))
                .runType(text(data, "runType"))
                .status(ForecastRunStatus.fromNullable(text(data, "status")))
                .caliberId(text(data, "caliberId"))
                .caliberVersion(text(data, "caliberVersion"))
                .asOfTime(text(data, "asOfTime"))
                .snapshotId(text(data, "snapshotId"))
                .formulaVersion(text(data, "formulaVersion"))
                .idempotencyKey(text(data, "idempotencyKey"))
                .contentHash(text(data, "contentHash"))
                .baselineRunId(text(data, "baselineRunId"))
                .reusedFromRunId(text(data, "reusedFromRunId"))
                .degraded(data.path("degraded").asBoolean(false))
                .evidenceRefs(textList(data.path("evidenceRefs")))
                .quantile(triple)
                .taskId(text(data, "taskId"))
                .build();
    }

    /** 明细透传：引擎 data 的一级字段原样转 Map（数值不经 double，保 BigDecimal 原文）。 */
    private static Map<String, Object> toFlatMap(JsonNode data) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (data == null || !data.isObject()) {
            return out;
        }
        data.fields().forEachRemaining(entry -> out.put(entry.getKey(), plain(entry.getValue())));
        return out;
    }

    private static Object plain(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isBigDecimal() || node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(item -> list.add(plain(item)));
            return list;
        }
        if (node.isObject()) {
            return toFlatMap(node);
        }
        return node.asText();
    }

    private static List<String> textList(JsonNode node) {
        List<String> refs = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                if (item.isTextual() && !item.asText().isBlank()) {
                    refs.add(item.asText());
                }
            });
        }
        return refs;
    }

    private static List<Map<String, Object>> mapList(JsonNode node) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                if (item.isObject()) {
                    items.add(toFlatMap(item));
                }
            });
        }
        return items;
    }

    private static Map<String, String> vars(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new ActionValidationException(List.of(key));
        }
        return Map.of(key, value.trim());
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.path(field);
        return v.isValueNode() && !v.isNull() ? v.asText() : null;
    }

    private static java.math.BigDecimal decimal(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isNumber()) {
            return v.decimalValue();
        }
        if (v.isTextual() && !v.asText().isBlank()) {
            try {
                return new java.math.BigDecimal(v.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
