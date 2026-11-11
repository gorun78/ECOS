package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.gateway.dao.CliPreflightProbeDao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * CLI 预检（preflight）11 步 —— 详设-10 §10.3.5 / 详设-16 §16.7（CLI 生产化批 E）。
 *
 * <p><b>只报告，不创建 Run</b>：不调用任何写通道，只对既有读端点做「可达 + 语义正确」探测，
 * 并对审批凭证存储做一次只读连通检查（SQL 住 DAO 层，见 {@link CliPreflightProbeDao}）。</p>
 *
 * <p><b>状态四值</b>：{@code PASS} 仅当探测真的返回成功信封；依赖不可达 ⇒ {@code BLOCKED}；
 * 无法判定（无信封/非 JSON/语义不明）⇒ {@code NOT_VERIFIED}；明确失败 ⇒ {@code FAIL}。
 * <b>禁</b>把「不知道」写 PASS。</p>
 *
 * <p><b>载荷形态</b>：返回强类型 {@link PreflightReport}（controller 不产出无类型 Map 载荷）。</p>
 */
@RestController
@RequestMapping("/api/v1/cli")
public class CliPreflightController {

    private static final Logger log = LoggerFactory.getLogger(CliPreflightController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 单步报告（详设-16 §16.7 字段）。 */
    public record StepReport(String step, String status, String source, String objectId, String version,
                             String traceId, String reason, String nextAction) {

        StepReport withStatus(String newStatus, String newReason) {
            return new StepReport(step, newStatus, source, objectId, version, traceId, newReason, nextAction);
        }

        StepReport withTrace(String newTrace) {
            return new StepReport(step, status, source, objectId, version, newTrace, reason, nextAction);
        }
    }

    /** 汇总报告。 */
    public record PreflightReport(int stepCount, int pass, int fail, int blocked, int notVerified,
                                  boolean createsRun, List<StepReport> steps) {
    }

    /** 探测步骤：名字 + 方法 + 路径 + 目的 + 下一步指引 + 是否带租户参数。 */
    private record Probe(String step, String method, String path, String purpose, String nextAction,
                         boolean tenantScoped) {
    }

    private static final List<Probe> PROBES = List.of(
            new Probe("gateway.health", "GET", "/actuator/health", "网关进程存活", "检查 gateway 进程与端口", false),
            new Probe("identity.profile", "GET", "/api/v1/system/users/me/profile",
                    "凭据有效性与主体身份", "重新登录获取 JWT；确认 token 未过期", false),
            new Probe("monitor.health", "GET", "/api/monitor/health",
                    "监控聚合面可达", "启动 runtime-monitor 或检查 gateway 加载", false),
            new Probe("cognitive.models", "GET", "/api/v1/cognitive/models",
                    "认知模型注册表可读", "启动 cognitive-engine 或装载对应 Controller", false),
            new Probe("wagent.tools", "GET", "/api/v1/wagent/tools",
                    "工具契约读面可读", "检查 ai-engine 装载与 ecos_ai 工具契约种子", false),
            new Probe("wagent.candidates", "GET", "/api/v1/wagent/candidates",
                    "候选读面可读（发布链上游）", "检查 ai-engine wagent 模块与候选表", false),
            new Probe("ontology.candidates", "GET", "/api/v1/ontology/candidates",
                    "本体候选读面可读（ontology validate 上游）", "启动 ontology-engine", true),
            new Probe("data.governance.candidates", "GET", "/api/v1/data/governance/candidates",
                    "数据治理候选读面可读", "启动 data-engine", false),
            new Probe("ai.models", "GET", "/api/v1/ai/models",
                    "LLM 模型目录可读（model list 上游）", "装配 LLMGateway 或检查 models 目录", false),
            new Probe("ai.capabilities", "GET", "/api/v1/ai/capabilities",
                    "能力目录可读", "检查 ai-engine capability 模块", false));

    private final RestTemplate restTemplate;
    private final CliPreflightProbeDao probeDao;
    private final String selfBaseUrl;

    public CliPreflightController(RestTemplate restTemplate,
                                  CliPreflightProbeDao probeDao,
                                  @Value("${ecos.cli.preflight.base-url:http://127.0.0.1:8080}")
                                  String selfBaseUrl) {
        this.restTemplate = restTemplate;
        this.probeDao = probeDao;
        this.selfBaseUrl = selfBaseUrl.endsWith("/")
                ? selfBaseUrl.substring(0, selfBaseUrl.length() - 1) : selfBaseUrl;
    }

    @PostMapping("/preflight")
    public ResponseEntity<ApiResponse<PreflightReport>> run(HttpServletRequest request) {
        String traceId = traceIdOf(request);
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String tenantId = tenantIdOf(authorization);

        List<StepReport> steps = new ArrayList<>();
        for (Probe probe : PROBES) {
            steps.add(probe(probe, authorization, tenantId, traceId));
        }
        steps.add(approvalStoreProbe(traceId));

        int pass = 0;
        int fail = 0;
        int blocked = 0;
        int notVerified = 0;
        for (StepReport s : steps) {
            switch (s.status()) {
                case "PASS" -> pass++;
                case "FAIL" -> fail++;
                case "BLOCKED" -> blocked++;
                default -> notVerified++;
            }
        }

        PreflightReport report = new PreflightReport(steps.size(), pass, fail, blocked, notVerified,
                false, List.copyOf(steps));

        ApiResponse<PreflightReport> resp;
        int http = 200;
        if (fail > 0) {
            resp = ApiResponse.error(500, "E-CLI-PREFLIGHT-FAIL",
                    "预检存在失败项（" + fail + "/" + steps.size() + "），逐项见 data.steps");
            http = 500;
        } else if (blocked > 0) {
            resp = ApiResponse.error(503, "E-CLI-PREFLIGHT-BLOCKED",
                    "预检依赖不可用（" + blocked + "/" + steps.size() + "），逐项见 data.steps");
            http = 503;
        } else if (notVerified > 0) {
            resp = ApiResponse.success("预检完成但存在不可判定项（" + notVerified + "）", report);
        } else {
            resp = ApiResponse.success(report);
        }
        resp.setData(report);
        resp.setTraceId(traceId);
        log.info("[cli-preflight] steps={} pass={} fail={} blocked={} notVerified={} (traceId={})",
                steps.size(), pass, fail, blocked, notVerified, traceId);
        return ResponseEntity.status(http).body(resp);
    }

    private StepReport probe(Probe probe, String authorization, String tenantId, String traceId) {
        StepReport row = baseRow(probe, traceId);
        String url = selfBaseUrl + probe.path();
        if (probe.tenantScoped() && tenantId != null && !tenantId.isBlank()) {
            url = url + (url.contains("?") ? "&" : "?") + "tenantId=" + tenantId;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            if (authorization != null && !authorization.isBlank()) {
                headers.set(HttpHeaders.AUTHORIZATION, authorization);
            }
            headers.set("Accept", "application/json");
            headers.set("X-Trace-Id", traceId);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> resp = probeTemplate().exchange(url,
                    HttpMethod.valueOf(probe.method()), entity, String.class);
            return fill(row, resp.getStatusCode().value(), resp.getBody());
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // 4xx = 请求形态/权限问题，不是「依赖不可用」——必须落 FAIL，不能拿 BLOCKED 遮掩
            return row.withStatus("FAIL", "探测被拒（HTTP " + e.getStatusCode().value() + "）");
        } catch (RestClientException e) {
            return row.withStatus("BLOCKED", "依赖不可达: " + sanitize(e.getMessage()));
        }
    }

    /** 审批凭证存储只读探测：能查即 PASS；异常 ⇒ BLOCKED（不暴露表结构细节）。 */
    private StepReport approvalStoreProbe(String traceId) {
        Probe probe = new Probe("approval.store", "SQL", "ecos_ai.agent_approval",
                "L3 审批凭证存储（V326）只读可达", "执行 V326 迁移并检查 ecos_ai schema", false);
        StepReport row = baseRow(probe, traceId);
        try {
            return probeDao.ping()
                    ? row.withStatus("PASS", "只读连通正常")
                    : row.withStatus("NOT_VERIFIED", "连接返回异常值");
        } catch (RuntimeException e) {
            return row.withStatus("BLOCKED", "凭证存储不可达: " + sanitize(e.getMessage()));
        }
    }

    private static StepReport baseRow(Probe probe, String traceId) {
        return new StepReport(probe.step(), "NOT_VERIFIED", probe.method() + " " + probe.path(),
                null, null, traceId, probe.purpose(), probe.nextAction());
    }

    /**
     * HTTP 状态 + 信封语义 → 四值状态（禁把未知写 PASS）。
     *
     * <p>状态行优先于响应体：5xx 网关族即使空体也是「依赖不可达」，不能因为没载荷就退化成
     * 「判不了」——504 无体的常见形态正是如此。</p>
     */
    private StepReport fill(StepReport row, int http, String body) {
        boolean blank = body == null || body.isBlank();
        JsonNode node = null;
        if (!blank) {
            try {
                node = MAPPER.readTree(body);
            } catch (Exception e) {
                node = null;
            }
        }
        StepReport traced = row;
        if (node != null) {
            String serverTrace = node.path("traceId").asText(null);
            if (serverTrace != null && !serverTrace.isBlank()) {
                traced = row.withTrace(serverTrace);
            }
        }
        if (http == 502 || http == 503 || http == 504) {
            return traced.withStatus("BLOCKED", "依赖不可用（HTTP " + http + "）");
        }
        if (http < 200 || http >= 300) {
            if (http == 401 || http == 403) {
                return traced.withStatus("FAIL", "认证/权限拒绝（HTTP " + http + "）");
            }
            return traced.withStatus("FAIL", "HTTP " + http);
        }
        if (node == null) {
            return traced.withStatus("NOT_VERIFIED",
                    blank ? "空响应体（HTTP " + http + "）" : "响应非 JSON（HTTP " + http + "）");
        }
        boolean hadCode = node.has("code");
        if (hadCode && node.path("code").asInt(-1) != 0) {
            // 下游引擎把「安全裁决机构不可用」包成 200 + 503/ECOS-SEC-501 信封（fail-closed），
            // 那是依赖不可达而非业务失败：判 FAIL 会把运维告警指到错误方向。
            if (RlsExecutionContract.isUnavailableEnvelope(MAPPER.convertValue(node, Map.class))) {
                return traced.withStatus("BLOCKED",
                        "依赖不可用: " + node.path("errorCode").asText("code=" + node.path("code").asText()));
            }
            return traced.withStatus("FAIL",
                    "业务码非零: " + node.path("errorCode").asText("code=" + node.path("code").asText()));
        }
        if (!hadCode && !node.has("status") && node.isEmpty()) {
            return traced.withStatus("NOT_VERIFIED", "200 但无可判定载荷");
        }
        return traced.withStatus("PASS", "探测成功");
    }

    /** 独立超时（3s 连接 / 5s 读）：预检是诊断，不能被单个慢依赖拖死。 */
    private RestTemplate probeTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(factory);
    }

    /**
     * 从已认证的 JWT 载荷读 {@code tenant_id}（本请求已过认证链，这里只做声明提取，不做二次验签）。
     * 拿不到就返回 null —— 该步按 400 落 FAIL，不猜租户。
     */
    private static String tenantIdOf(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorization.substring(7).trim();
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] payload = java.util.Base64.getUrlDecoder().decode(parts[1]);
            JsonNode node = MAPPER.readTree(payload);
            String tenant = node.path("tenant_id").asText(null);
            return tenant == null || tenant.isBlank() ? null : tenant.trim();
        } catch (RuntimeException | java.io.IOException e) {
            return null;
        }
    }

    private static String traceIdOf(HttpServletRequest request) {
        String v = request.getHeader("X-Trace-Id");
        if (v == null || v.isBlank()) {
            v = request.getHeader("X-Request-Id");
        }
        if (v != null && !v.isBlank()) {
            return v.trim();
        }
        String mdc = org.slf4j.MDC.get("traceId");
        return mdc == null || mdc.isBlank() ? "-" : mdc;
    }

    /** 错误文案只留可操作信息，去掉 URL 细节里的内部地址。 */
    private static String sanitize(String message) {
        if (message == null) {
            return "-";
        }
        String m = message.replaceAll("https?://[^\\s,;]+", "<url>");
        return m.length() > 200 ? m.substring(0, 200) + "…" : m;
    }
}
