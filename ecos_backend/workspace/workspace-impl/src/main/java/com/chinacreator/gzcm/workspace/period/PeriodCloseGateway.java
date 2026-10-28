package com.chinacreator.gzcm.workspace.period;

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

import com.chinacreator.gzcm.workspace.exception.PeriodCloseForwardException;
import com.chinacreator.gzcm.workspace.security.CallerCredentialRelay;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;

/**
 * 期间关账承流网关（#146y，附录A 第 34 条 / 详设 line 8268 {@code closePeriod}）。
 *
 * <p><b>本层的定位＝薄承流，不是第二属主</b>：关账的真身（OPA 角色门槛、幂等判定、
 * 状态行落库、{@code ecos.audit} 事件）全部在 data-engine 属主面
 * {@code POST /api/v1/datanet/facts/periods/{yyyMM}/close}（{@code PeriodCloseController}
 * + {@code PeriodCloseService}，详设-09 §十一 H-3）。workspace 侧只是把契约路径
 * {@code /api/v1/workspace/periods/{yyyMM}/close} 接进属主面，<b>不</b>复制任何判定逻辑，
 * 也不在本地造权限表 —— 铁律 §0.3.1「单一属主」与 §3.1「跨引擎不操作对方 schema」
 * 在本批的落点就是「只搬不改」。</p>
 *
 * <p><b>形状口径</b>：出入参走 {@code Map}/{@code JsonNode} 原样搬运，不引入
 * {@code data-engine-api} 的 {@code PeriodCloseRequestVO}/{@code PeriodCloseResultVO}
 * 编译期依赖 —— 一是 {@code WorkspaceLayeringArchTest} 禁 workspace 源码
 * import 引擎包，二是本类实装的就是「透传」，绑定 VO 反而会把属主字段面锁进场景层。</p>
 *
 * <p><b>凭证</b>：出站逐字中继入口 {@code Authorization}（{@link CallerCredentialRelay}）。
 * 寻址取 MONOLITH 形态（见 {@code WorkspaceRestClientConfig#workspaceDatanetMonolithRest}
 * 的理由注释）：属主 {@code PeriodCloseService#currentRoleClaims()} 要读 {@code UserContext}，
 * 而该上下文只由 sysman JWT filter 写入，datanet 独立进程没有该 filter ⇒ 直连 :18082
 * 会让已登录用户被结构性判 403（假绿）。gateway :8080 同进程持有 filter 与 datanet 面，
 * 是关账链唯一能把身份带到裁决点的形态。</p>
 */
@Component
public class PeriodCloseGateway {

    private static final Logger log = LoggerFactory.getLogger(PeriodCloseGateway.class);

    /**
     * 属主侧相对路径（Bean 已把 {@code /api/v1} 预置为根，故此处写根下相对段）。
     *
     * <p>前导斜杠拼接与仓内既有口径一致（{@code KnowledgeConfigAggregator#exchange} 传
     * {@code "/knowledge/settings"}、{@code WorkspaceSecurityEngineClient} 传 {@code "/api/v1/..."}）：
     * {@code DefaultUriBuilderFactory} 只对「以斜杠起始的相对段」做 {@code base + path} 拼接，
     * 不带前导斜杠的模板会被当作绝对 URI 处理 ⇒ 本段必须以 {@code /} 起始，
     * 最终出站 URI 为 {@code <monolith>/api/v1/datanet/facts/periods/{yyyMM}/close}。
     * 该拼接由 {@code PeriodCloseContractTest#successLegForwardsBodyAndPathVariable} 的
     * {@code requestTo} 断言钉住，不是凭注释成立。</p>
     */
    private static final String OWNER_PATH = "/datanet/facts/periods/{yyyMM}/close";

    /** 大数以 BigDecimal 承载（与 {@code CognitiveForecastRunGateway} 同口径，禁静默降 double）。 */
    private static final ObjectMapper RAW_JSON = com.fasterxml.jackson.databind.json.JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .nodeFactory(JsonNodeFactory.withExactBigDecimals(true))
            .build();

    private final RestTemplate restTemplate;

    public PeriodCloseGateway(@Qualifier("workspaceDatanetMonolithRest") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 关账一次期间。
     *
     * @param yyyMM 路径变量（{@code 202501} 形态；归一与合法性由属主判定，本层不预校验以免造第二套口径）
     * @param body  可空请求体，原样搬运属主字段面（{@code reason}/{@code detailJson}）
     * @return 属主返回的关账结果（{@code period/status/closedBy/closedAt/alreadyClosed/reason}）
     */
    public Map<String, Object> close(String yyyMM, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        CallerCredentialRelay.applyTo(headers);

        HttpEntity<String> entity;
        try {
            entity = new HttpEntity<>(body == null ? null : RAW_JSON.writeValueAsString(body), headers);
        } catch (Exception e) {
            log.warn("period close body serialization failed yyyMM={} err={}", yyyMM, e.getMessage());
            throw PeriodCloseForwardException.unavailable(
                    "关账请求体序列化失败（未出网，禁半截请求冒充提交）: " + e.getMessage(), e);
        }

        ResponseEntity<String> resp;
        try {
            resp = restTemplate.exchange(OWNER_PATH, HttpMethod.POST, entity, String.class,
                    Map.of("yyyMM", yyyMM));
        } catch (RestClientResponseException e) {
            throw translate(e, yyyMM);
        } catch (ResourceAccessException e) {
            log.warn("period close channel unreachable yyyMM={} err={}", yyyMM, e.getMessage());
            throw PeriodCloseForwardException.unavailable(
                    "datanet 关账通道不可达 path=" + OWNER_PATH + " err=" + e.getMessage(), e);
        } catch (RestClientException e) {
            log.warn("period close call failed yyyMM={} err={}", yyyMM, e.getMessage());
            throw PeriodCloseForwardException.unavailable(
                    "datanet 关账调用失败 path=" + OWNER_PATH + " err=" + e.getMessage(), e);
        }
        return unwrap(resp.getBody(), yyyMM);
    }

    // ── 内部 ───────────────────────────────────────────

    /** 成功信封拆封：{@code code != 0} 与空/非 JSON 应答一律 fail-loud，禁按空数据成功。 */
    private Map<String, Object> unwrap(String rawBody, String yyyMM) {
        if (rawBody == null || rawBody.isBlank()) {
            throw PeriodCloseForwardException.unavailable(
                    "datanet 关账空响应体（不可判定，禁按成功搬运）yyyMM=" + yyyMM, null);
        }
        JsonNode root;
        try {
            root = RAW_JSON.readTree(rawBody);
        } catch (Exception e) {
            throw PeriodCloseForwardException.unavailable(
                    "datanet 关账响应非 JSON yyyMM=" + yyyMM + " err=" + e.getMessage(), e);
        }
        JsonNode codeNode = root.path("code");
        int code = codeNode.isNumber() ? codeNode.asInt() : -1;
        if (code != 0) {
            log.warn("period close rejected by owner envelope yyyMM={} code={} errorCode={}",
                    yyyMM, code, root.path("errorCode").asText(""));
            throw PeriodCloseForwardException.forwarded(503,
                    textOrNull(root, "errorCode"),
                    PeriodCloseForwardException.safeMessageFor(503),
                    "datanet 关账信封 code=" + code + " yyyMM=" + yyyMM, null);
        }
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            throw PeriodCloseForwardException.unavailable(
                    "datanet 关账应答缺 data 段（回执不可判定，禁冒充已关账）yyyMM=" + yyyMM, null);
        }
        Map<String, Object> row = new LinkedHashMap<>();
        data.fields().forEachRemaining(e -> row.put(e.getKey(), plain(e.getValue())));
        return row;
    }

    /**
     * 属主 4xx/5xx → 同状态、同机读码的领域载体（禁被 {@code handleDownstream} 折成 500）。
     *
     * <p>属主可能给出的状态档位（{@code PeriodCloseService} 实证）：400 {@code ECOS-DATA-023}
     * 期间形态非法、403 {@code ECOS-DATA-031} OPA DENY、503 {@code ECOS-SEC-501} security
     * 未评估、409 关账期封闭。本层逐字透出，不做「统一 400」之类的归一。</p>
     */
    private PeriodCloseForwardException translate(RestClientResponseException e, String yyyMM) {
        int status = e.getRawStatusCode();
        String errorCode = null;
        try {
            errorCode = textOrNull(RAW_JSON.readTree(e.getResponseBodyAsString()), "errorCode");
        } catch (Exception ignored) {
            // 应答体不可解析：只按 HTTP 状态归类，禁臆造业务码
        }
        log.warn("period close downstream rejected yyyMM={} status={} errorCode={} detail={}",
                yyyMM, status, errorCode, e.getStatusText());
        return PeriodCloseForwardException.forwarded(status, errorCode,
                PeriodCloseForwardException.safeMessageFor(status),
                "datanet 关账 HTTP " + status + " yyyMM=" + yyyMM, e);
    }

    private static String textOrNull(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    /** JsonNode → 原值（缺位字段保持 null，禁默认值冒充）。 */
    private static Object plain(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isBigDecimal() || node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isArray()) {
            return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                    .map(PeriodCloseGateway::plain).toList();
        }
        Map<String, Object> nested = new LinkedHashMap<>();
        node.fields().forEachRemaining(e -> nested.put(e.getKey(), plain(e.getValue())));
        return nested;
    }
}
