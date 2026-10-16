package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.security.cls.ColumnSurfaceContract;
import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * H10-T1 (PMO-74 N8) — ai-engine 对 security-engine 的统一访问层。
 *
 * <p>铁律 §2.4：RLS / CLS / ABAC 裁决 / 审计全部经 security-engine REST
 * （{@link KafkaTopics#AUDIT}），AI 引擎内禁止重复实现安全逻辑。</p>
 *
 * <p>详细设计-06 收口（F06-01 / F06-03）：</p>
 * <ul>
 *   <li><b>X-19 / W145</b>：ABAC 裁决超时预算由 5000ms 收口为 <b>2000ms</b>；</li>
 *   <li><b>X-20</b>：审计出口不再有 {@code kafkaTemplate} 反射兜底，也不再有
 *       "ObjectProvider 缺失即 warn 继续"——红线事件（护栏裁决）一律经
 *       {@link GuardrailAuditPublisher#auditOrThrow}，发出失败即裁决失败；
 *       本类的 {@link #auditQuietly} 只服务非红线留痕；</li>
 *   <li>新增 {@link #evaluateData} / {@link #applyMasking} / {@link #evaluateFilter}
 *       三个<b>不吞异常</b>的方法，由调用方（裁决闸 / obligations 执行器）做
 *       fail-closed 三态判定，避免"客户端把不可用折叠成 DENY"造成的语义丢失
 *       （GUARDRAIL_DENIED 与 GUARDRAIL_FAIL_CLOSED 的重试与文案不同，F06-14）。</li>
 * </ul>
 *
 * <p>出站请求统一带 {@code X-Request-Id}（本类单点写入，禁各调用点手写，F06-06 要点 2）。</p>
 */
@Component
public class AiSecurityEngineClient {

    private static final Logger log = LoggerFactory.getLogger(AiSecurityEngineClient.class);

    /** PRD-06 §1.3 / 详细设计-06 W145：ABAC 裁决超时预算 2s。 */
    public static final int POLICY_TIMEOUT_MS = 2000;

    private static final String PATH_POLICY_EVALUATE = "/api/v1/security/policy-engine/evaluate";
    private static final String PATH_MASKING_APPLY = "/api/v1/security/masking/apply";
    private static final String PATH_EVALUATE_FILTER = "/api/security/evaluate-filter";
    private static final String PATH_RLS_APPLY = "/api/v1/security/rls/apply";
    private static final String PATH_CLS_COLUMNS = "/api/v1/security/cls/columns";

    private final String baseUrl;
    private final RestTemplate restTemplate;
    private final ObjectProvider<EventBusService> eventBusProvider;
    private final int timeoutMs;

    public AiSecurityEngineClient(
            @Value("${service.security.base-url:http://localhost:18081}") String baseUrl,
            @Value("${service.security.timeout-ms:2000}") int timeoutMs,
            ObjectProvider<EventBusService> eventBusProvider) {
        this.baseUrl = baseUrl;
        this.eventBusProvider = eventBusProvider;
        this.timeoutMs = timeoutMs;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1, timeoutMs));
        factory.setReadTimeout(Math.max(1, timeoutMs));
        this.restTemplate = new RestTemplate(factory);
    }

    public int policyTimeoutMs() {
        return timeoutMs;
    }

    // ═══════════════ ABAC 裁决 ═══════════════

    /**
     * 旧签名：明确 ALLOW 才 true；不可用/异常/不可解析 → false（默认 DENY，§2.4-6）。
     *
     * <p>咽喉路径请改用 {@link #evaluateData(Map)} + {@link #verdictOf(Map)} 以区分 DENY 与
     * FAIL_CLOSED；本方法保留给非裁决类的粗粒度判定（如 OAG 前置过滤）。</p>
     */
    public boolean evaluate(String userId, String tenantId, String action, Map<String, Object> attrs) {
        try {
            return V_ALLOW.equals(verdictOf(evaluateData(buildEvaluateBody(userId, tenantId, action, attrs))));
        } catch (Exception e) {
            log.warn("AiSecurityEngineClient.evaluate DENY (security 不可用/裁决失败): action={} reason={}",
                    action, e.getMessage());
            return false;
        }
    }

    // ═══════════════ 三态判定解析（§4.2 决策树的唯一实现点） ═══════════════

    /** 裁决三态字面量（与 {@code ToolDecision.Verdict} 名一致）。 */
    public static final String V_ALLOW = "ALLOW";
    public static final String V_DENY = "DENY";
    public static final String V_FAIL_CLOSED = "FAIL_CLOSED";

    /**
     * 把 security 响应 {@code data} 段解析成三态，禁在此折叠语义：
     *
     * <ol>
     *   <li><b>{@code policyState} 优先</b>（B4-3c，#142）：显式为
     *       {@code POLICY_UNDEFINED}（OPA 可达但本策略未加载）或 {@code OPA_UNAVAILABLE}
     *       （不可达/超时/非 2xx/不可解析）⇒ <b>FAIL_CLOSED</b>。这两态的含义是"护栏没说话"，
     *       与策略拒绝（403）分态（§4.7.2「不可用 ≠ 无权限」/ F06-14）。缺该键＝旧形态，
     *       继续走下面的兼容分支；</li>
     *   <li>显式 {@code decision}/{@code verdict} 字段（ALLOW/PERMIT、DENY/DENIED，未知取值 →
     *       FAIL_CLOSED，禁默认放行兜底）；</li>
     *   <li>无显式字段时读布尔裁决键 —— 分册 01 现契约（{@code OpaPolicyService.evaluate}）用
     *       <b>{@code allow}</b>，{@code result} 为历史别名，两者都要认（只认一个即恒 DENY）；</li>
     *   <li>{@code source=fallback} 或带 {@code opaError} 时，security 自己走的是 fail-close 兜底
     *       <b>而非策略裁决</b> —— 引擎侧必须记为 FAIL_CLOSED（503、可告警），不得算 DENY（403）；</li>
     *   <li>裁决键缺失/不可识别 → FAIL_CLOSED。</li>
     * </ol>
     */
    public static String verdictOf(Map<String, Object> data) {
        if (data == null) return V_FAIL_CLOSED;
        String policyState = GuardrailDecisionContract.stateOf(data);
        if (GuardrailDecisionContract.isUnevaluated(policyState)) return V_FAIL_CLOSED;
        if (isUnavailableSource(data)) return V_FAIL_CLOSED;
        Object explicit = data.get("decision") != null ? data.get("decision") : data.get("verdict");
        if (explicit != null) {
            String s = String.valueOf(explicit).trim().toUpperCase(Locale.ROOT);
            if ("ALLOW".equals(s) || "PERMIT".equals(s) || "TRUE".equals(s)) return V_ALLOW;
            if ("DENY".equals(s) || "DENIED".equals(s) || "REJECT".equals(s)) return V_DENY;
            return V_FAIL_CLOSED;
        }
        if (GuardrailDecisionContract.STATE_DENY.equals(policyState)) return V_DENY;
        if (GuardrailDecisionContract.STATE_ALLOW.equals(policyState)) return V_ALLOW;
        Object flag = data.get("allow") != null ? data.get("allow") : data.get("result");
        if (flag == null) return V_FAIL_CLOSED;
        if (flag instanceof Boolean b) return b ? V_ALLOW : V_DENY;
        String s = String.valueOf(flag).trim().toLowerCase(Locale.ROOT);
        if ("true".equals(s) || "allow".equals(s) || "permit".equals(s)) return V_ALLOW;
        if ("false".equals(s) || "deny".equals(s) || "denied".equals(s)) return V_DENY;
        return V_FAIL_CLOSED;
    }

    /** 恰为显式 ALLOW 才 true（其余形态 false，调用方仍须用 {@link #verdictOf} 分态）。 */
    public static boolean isExplicitAllow(Map<String, Object> data) {
        return V_ALLOW.equals(verdictOf(data));
    }

    private static boolean isUnavailableSource(Map<String, Object> data) {
        if (data.containsKey("opaError")) return true;
        Object source = data.get("source");
        return source != null && "fallback".equalsIgnoreCase(String.valueOf(source).trim());
    }

    /**
     * POST {@value #PATH_POLICY_EVALUATE} —— <b>不吞异常</b>的裁决调用。
     *
     * @return 响应 {@code data} 段（含 {@code result} / {@code policyId} / {@code obligations}）
     * @throws Exception security 不可用 / 超时 / HTTP 非 2xx / 响应不可解析，由调用方转 FAIL_CLOSED
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> evaluateData(Map<String, Object> requestBody) throws Exception {
        Map<String, Object> resp = postJson(PATH_POLICY_EVALUATE, requestBody);
        if (resp == null || !(resp.get("data") instanceof Map)) {
            throw new SecurityResponseUnparsableException(
                    "policy-engine/evaluate 响应不可解析（缺 data 段）: " + summarize(resp));
        }
        return (Map<String, Object>) resp.get("data");
    }

    private Map<String, Object> buildEvaluateBody(String userId, String tenantId, String action,
                                                  Map<String, Object> attrs) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("policy", "rbac");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("action", action);
        if (attrs != null) input.putAll(attrs);
        input.put("subject", Map.of(
                "userId", userId == null ? "anonymous" : userId,
                "tenantId", tenantId == null ? "default" : tenantId,
                "role", "user"));
        body.put("input", input);
        return body;
    }

    /**
     * 四段载荷形态的裁决体（{@code ToolAdjudicationRequest#toEvaluateInput()} 直供）。
     *
     * <p><b>策略名单源改为 {@code data_access}（B4-3c，差距文档 D-B4-22）</b>：此前硬编码
     * {@code "rbac"}，于是"三通道同源"只在入口形态上成立、在策略面上是假的 —— 页面/导出通道
     * 经 {@code /decision} 查数据访问策略，AI 工具却查另一份策略。{@code data_access.rego}
     * 的输入契约（{@code purpose=ai} + {@code subject.roles} + {@code resource.dataScope}
     * + {@code environment.channel}）正是为四段载荷设计的，{@code §4.7.2} 的 AI 列
     * （项目负责人 p1 ALLOW / p2 DENY、按部门域、无权用户 GUARDRAIL_DENIED）只有查这份策略
     * 才可表达。词表取 runtime 底座 {@link GuardrailDecisionContract}，禁在本类内联字面量。</p>
     */
    public Map<String, Object> evaluateBodyForAdjudication(Map<String, Object> input) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("policy", GuardrailDecisionContract.POLICY_DATA_ACCESS);
        body.put("input", input);
        return body;
    }

    // ═══════════════ RLS（默认 DENY "1=0"） ═══════════════

    /**
     * POST {@value #PATH_RLS_APPLY} — 行级安全 WHERE 片段。
     *
     * <p><b>#142/B4-3c-2 收口（D-B4-17）</b>：应答缺 {@code whereClause} 时此前返回
     * <b>空串</b>，调用方（{@code SecurityCheckerNode}）把空串当"无策略但放行"写入上下文；
     * 加上本引擎的 RLS 面从未把片段真正下推到 SQL（差距文档登记 NOT_WIRED），
     * 空串形态等于把"应答漂移"伪装成"无行级限制"。现在缺字段/空白/需绑参（含 {@code ?}，
     * 本引擎无参数通道）一律 {@code "1=0"} = 拒绝。</p>
     *
     * @return whereClause；恒真 {@code 1=1}＝不限制行；缺字段/空白/不可绑定/不可用/DENY → {@code "1=0"}
     */
    @SuppressWarnings("unchecked")
    public String applyRls(String userId, String tenantId, String entityType,
                           String tableName, Map<String, Object> filters) {
        try {
            return requireRenderableRls(whereClauseOf(postJson(PATH_RLS_APPLY,
                    rlsRequestBody(userId, tenantId, entityType, tableName, filters)), entityType));
        } catch (Exception e) {
            log.warn("AiSecurityEngineClient.applyRls DENY: entityType={} reason={}", entityType, e.getMessage());
            return DENY_CLAUSE;
        }
    }

    /**
     * RLS 的<b>不吞异常</b>版本：obligations 执行必须区分"无策略"与"security 不可用"，
     * 后者整条结果丢弃并 FAIL_CLOSED（F06-02 要点 2，禁"先返回再说明"）。
     *
     * <p>应答缺 {@code whereClause} / 片段需绑参同样抛
     * {@link SecurityResponseUnparsableException}（而不是返回空串冒充"无行级限制"），
     * 由调用方走 FAIL_CLOSED。</p>
     */
    public String applyRlsOrThrow(String userId, String tenantId, String entityType,
                                  String tableName, Map<String, Object> filters) throws Exception {
        return requireRenderableRls(whereClauseOf(postJson(PATH_RLS_APPLY,
                rlsRequestBody(userId, tenantId, entityType, tableName, filters)), entityType), true);
    }

    /** 拒绝片段（与 security-engine {@code RlsPredicateRenderer.DENY_CLAUSE} 同值）。 */
    public static final String DENY_CLAUSE = "1=0";

    /** 恒真片段＝裁决明确"不限制行"（与非平凡谓词、与拒绝皆分态）。 */
    public static final String ALLOW_ALL_CLAUSE = "1=1";

    private Map<String, Object> rlsRequestBody(String userId, String tenantId, String entityType,
                                               String tableName, Map<String, Object> filters) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tableName", tableName);
        body.put("userId", userId == null ? "anonymous" : userId);
        body.put("entityType", entityType);
        if (tenantId != null) body.put("tenantId", tenantId);
        if (filters != null && !filters.isEmpty()) body.put("filters", filters);
        return body;
    }

    /** 取 {@code data.whereClause} 原文；应答不可解析 → {@code null}。 */
    @SuppressWarnings("unchecked")
    private static String whereClauseOf(Map<String, Object> resp, String entityType) throws Exception {
        if (resp == null || !(resp.get("data") instanceof Map)) {
            throw new SecurityResponseUnparsableException("rls/apply 响应不可解析: entityType=" + entityType);
        }
        Object whereObj = ((Map<String, Object>) resp.get("data")).get("whereClause");
        return whereObj == null ? null : String.valueOf(whereObj);
    }

    /**
     * 执行契约合规化：只有「不需要绑定参数的片段」（{@code 1=0}/{@code 1=1}/裸文本条件）
     * 才能被本引擎消费——本引擎没有把 RLS 参数带进 SQL 的通道，含 {@code ?} 的片段
     * 若放行就是超发（IR05）。
     *
     * @param strict {@code true}（OrThrow 版）时不可用形态抛异常；{@code false} 时返回 {@code "1=0"}
     */
    private String requireRenderableRls(String clause, boolean strict) throws Exception {
        if (clause == null || clause.isBlank()) {
            String reason = "RLS_WHERE_CLAUSE_ABSENT(应答未发执行契约，禁当成「无过滤」)";
            if (strict) throw new SecurityResponseUnparsableException(reason);
            log.warn("AiSecurityEngineClient.applyRls DENY: {}", reason);
            return DENY_CLAUSE;
        }
        String trimmed = clause.trim();
        if (trimmed.indexOf('?') >= 0) {
            String reason = "RLS_PARAM_UNBINDABLE(片段需绑参，AI 侧无参数通道): clause=" + trimmed;
            if (strict) throw new SecurityResponseUnparsableException(reason);
            log.warn("AiSecurityEngineClient.applyRls DENY: {}", reason);
            return DENY_CLAUSE;
        }
        return trimmed;
    }

    private String requireRenderableRls(String clause) throws Exception {
        return requireRenderableRls(clause, false);
    }

    // ═══════════════ CLS（默认 DENY 空集） ═══════════════

    /**
     * POST {@value #PATH_CLS_COLUMNS} — 列级安全可见列。
     *
     * <p><b>#142/B4-3d-1 语义订正（差距 D-B4-25）</b>：读数位此前只认
     * {@code data.allowedColumns}，而 CLS 生产者写的键是 {@code visibleColumns}
     * ⇒ AI 通道的列裁剪结构性恒空集（恒 DENY）。现委托
     * {@link ColumnSurfaceContract#read} 单源读取（与 workspace/ontology 同一份逻辑，
     * 禁在本类再写第二套键名判定）。漂移与"策略拒绝全部列"都返空集（fail-closed 不变），
     * 但漂移态会带理由落日志 —— 两者在审计上必须是不同事件，禁混为一谈。</p>
     *
     * @return 允许列；security 不可用/异常/列面漂移 → List.of()（DENY-all 语义）
     */
    public List<String> allowedColumns(String userId, String tenantId, String entityType,
                                       List<String> allColumns) {
        if (allColumns == null || allColumns.isEmpty()) return List.of();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tableName", entityType);
            body.put("userId", userId == null ? "anonymous" : userId);
            if (tenantId != null) body.put("tenantId", tenantId);
            body.put("allColumns", allColumns);
            Map<String, Object> resp = postJson(PATH_CLS_COLUMNS, body);
            ColumnSurfaceContract.Reading reading =
                    ColumnSurfaceContract.read(resp == null ? null : resp.get("data"));
            if (reading.drifted()) {
                log.warn("AiSecurityEngineClient.allowedColumns 列面漂移 → DENY: entityType={} reason={}",
                        entityType, reading.reason());
                return List.of();
            }
            if (reading.visible().isEmpty()) {
                log.warn("AiSecurityEngineClient.allowedColumns 策略拒绝全部列(ECOS-SEC-402): entityType={}",
                        entityType);
            } else if (reading.legacyKeyUsed()) {
                log.warn("AiSecurityEngineClient.allowedColumns 走 legacy 别名键: entityType={} size={}",
                        entityType, reading.visible().size());
            }
            return reading.visible();
        } catch (Exception e) {
            log.warn("AiSecurityEngineClient.allowedColumns DENY: entityType={} reason={}",
                    entityType, e.getMessage());
            return List.of();
        }
    }

    // ═══════════════ 脱敏 / 行过滤执行（obligations 消费，算法只在 security 侧） ═══════════════

    /**
     * POST {@value #PATH_MASKING_APPLY} — security 侧脱敏（分册 01 SEC-02 契约）。
     *
     * <p>ai-engine 只传值与规则名，<b>不得</b>本地实现任何脱敏算法（F06-02 验收
     * {@code ObligationsApplyTest#engineDoesNotSelfImplementMasking}）。</p>
     *
     * @return security 返回的 results 列表（元素含 {@code index} / {@code masked} / {@code rule}）
     * @throws Exception 不可用 / 响应不可解析（调用方整条结果丢弃 + FAIL_CLOSED）
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> applyMasking(List<String> values, List<String> rules) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", values);
        body.put("rules", rules);
        Map<String, Object> resp = postJson(PATH_MASKING_APPLY, body);
        if (resp == null || !(resp.get("data") instanceof Map)) {
            throw new SecurityResponseUnparsableException("masking/apply 响应不可解析: " + summarize(resp));
        }
        Object results = ((Map<String, Object>) resp.get("data")).get("results");
        if (!(results instanceof List<?> list)) {
            throw new SecurityResponseUnparsableException("masking/apply 响应缺 results 列表");
        }
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
        }
        return out;
    }

    /**
     * POST {@value #PATH_EVALUATE_FILTER} — security 侧行过滤判定（分册 01 SEC-03 契约）。
     *
     * @return security 判定结果（含放行标记）
     * @throws Exception 不可用 / 响应不可解析（调用方整条结果丢弃 + FAIL_CLOSED）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> evaluateFilter(String expression, Map<String, Object> rowData,
                                              String userRole) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("expression", expression);
        body.put("rowData", rowData == null ? Map.of() : rowData);
        body.put("userRole", userRole == null ? "viewer" : userRole);
        Map<String, Object> resp = postJson(PATH_EVALUATE_FILTER, body);
        if (resp == null || !(resp.get("data") instanceof Map)) {
            throw new SecurityResponseUnparsableException("evaluate-filter 响应不可解析: " + summarize(resp));
        }
        return (Map<String, Object>) resp.get("data");
    }

    // ═══════════════ 非红线留痕 — Kafka ecos.audit（runtime-event 单通道） ═══════════════

    /**
     * 非红线审计留痕：发布失败记 WARN 不抛出。
     *
     * <p><b>护栏裁决禁用本方法</b>（F06-03 要点 1，红线走
     * {@link GuardrailAuditPublisher#auditOrThrow}）。</p>
     */
    public void auditQuietly(String userId, String action, String resource, Object result) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventId", UUID.randomUUID().toString());
            event.put("timestamp", Instant.now().toString());
            event.put("module", "ai-engine");
            event.put("traceId", AiTraceSupport.currentTraceId());
            event.put("userId", userId == null ? "anonymous" : userId);
            event.put("action", action);
            event.put("resource", resource);
            event.put("result", result == null ? "OK" : String.valueOf(result));
            EventBusService bus = eventBusProvider.getIfAvailable();
            if (bus == null) {
                log.warn("AiSecurityEngineClient.auditQuietly: EventBusService 未装配，审计仅记日志: action={}",
                        action);
                return;
            }
            bus.publish(KafkaTopics.AUDIT, event);
        } catch (Exception e) {
            log.warn("AiSecurityEngineClient.auditQuietly failed: action={} reason={}", action, e.getMessage());
        }
    }

    // ═══════════════ 内部 ═══════════════

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> postJson(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(AiTraceSupport.HEADER_REQUEST_ID, AiTraceSupport.currentTraceId());
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + path, entity, Map.class);
        return resp == null ? null : resp.getBody();
    }

    private String summarize(Map<String, Object> resp) {
        if (resp == null) return "null";
        Object code = resp.get("code");
        Object message = resp.get("message");
        return "code=" + code + " message=" + message;
    }

    /** security 响应形态不符合契约（缺 data / 缺 results 等）——按不可解析处理，即 FAIL_CLOSED。 */
    public static class SecurityResponseUnparsableException extends RuntimeException {
        public SecurityResponseUnparsableException(String message) {
            super(message);
        }
    }
}
