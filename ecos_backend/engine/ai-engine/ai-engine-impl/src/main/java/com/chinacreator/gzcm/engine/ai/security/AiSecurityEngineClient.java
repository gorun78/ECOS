package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.security.cls.ColumnSurfaceContract;
import com.chinacreator.gzcm.common.security.decision.DecisionChannelContract;
import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
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

    /**
     * 三通道唯一裁决入口（详设-01 C.1 [3] / F01-02 / W33）。
     *
     * <p><b>#142/B4-3d-6 只增（差距 D-B4-28 / D-B4-30）</b>：AI 通道此前在列面上零命中
     * 本端点（仓根实证 {@code grep -rn '/security/decision' engine/ai-engine} = 0），
     * 护栏裁决只问 {@value #PATH_POLICY_EVALUATE}。本常量是 ai-engine 内的第一处引用位，
     * 消费方为 {@link AiToolColumnSurfaceGate}。</p>
     */
    private static final String PATH_DECISION = "/api/v1/security/decision";

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
     * 空串形态等于把"应答漂移"伪装成"无行级限制"。</p>
     *
     * <p><b>#142/B4-3d-5 单源化（差距 D-B4-24）</b>：本类的 {@code whereClauseOf} +
     * {@code requireRenderableRls} 是键面的<b>第三套手抄解析</b>（自读字面量、自判 {@code '?'}、
     * 自写理由文案）。现全部委托 {@link RlsExecutionContract}：四道构造期不变量由单源一次做完，
     * 本通道再叠加 {@link RlsExecutionContract.Execution#rejectForUnbindableChannel}
     * ——OAG 只把片段写进上下文、没有参数绑定通道，含 {@code ?} 的片段宁 DENY 不猜值拼接（IR05）。</p>
     *
     * <p><b>对外形态不变</b>：缺字段/空白/需绑参/不可用/DENY 一律 {@code "1=0"}；
     * 恒真 {@code 1=1}＝不限制行（与"未给谓词"分态）。</p>
     *
     * <p><b>#142/B4-3d-10（D-B4-26 余项）</b>：本方法保留折叠语义（改签名撞「API 只增不改」），
     * 但不再是唯一出口 —— 需要「不可用 ≠ 无权限」分态的调用方用 {@link #applyRlsExecution}。</p>
     *
     * @return whereClause；恒真 {@code 1=1}＝不限制行；缺字段/空白/不可绑定/不可用/DENY → {@code "1=0"}
     */
    public String applyRls(String userId, String tenantId, String entityType,
                           String tableName, Map<String, Object> filters) {
        RlsExecutionContract.Execution exec = applyRlsExecution(userId, tenantId, entityType,
                tableName, filters);
        // 日志不在此重复：applyRlsExecution 已按态打过一次带理由的 warn（折叠点只留一处口径）
        return exec.usable() ? exec.clause() : DENY_CLAUSE;
    }

    /**
     * RLS 的<b>带态、不抛异常</b>出口（#142/B4-3d-10，差距 D-B4-26 余项）。
     *
     * <p><b>与本类另两个出口的分工</b>：{@link #applyRls} 把一切不可用折叠成 {@code 1=0}
     * （旧对外形态），{@link #applyRlsOrThrow} 把故障原样上抛（obligations 链的
     * FAIL_CLOSED 用），本方法则把四种形态留在返回值里。三者共用同一份单源读数，
     * 差别只在"折叠／上抛／带态"，禁再出现第四套解析。</p>
     *
     * <p><b>不可用的四种形态</b>（与 data-engine {@code QueryController} 的判据同口径）：
     * 传输/寻址异常、应答空信封、security 失败信封（HTTP 200 + {@code code=503}／
     * message 以 {@code ECOS-SEC-501} 起头，见 {@link RlsExecutionContract#isUnavailableEnvelope}）、
     * 以及本引擎特有的 RestTemplate 缺失。四类都出
     * {@link RlsExecutionContract.State#UNAVAILABLE}，不得伪装成"策略拒绝"（§4.5.4）。</p>
     *
     * @return 永不为 {@code null} 的执行契约读数（已叠加 {@code rejectForUnbindableChannel}）
     */
    public RlsExecutionContract.Execution applyRlsExecution(String userId, String tenantId,
                                                            String entityType, String tableName,
                                                            Map<String, Object> filters) {
        String subject = "entityType=" + entityType;
        try {
            Map<String, Object> resp = postJson(PATH_RLS_APPLY,
                    rlsRequestBody(userId, tenantId, entityType, tableName, filters));
            if (resp == null) {
                log.warn("AiSecurityEngineClient.applyRls UNAVAILABLE: 应答空信封 entityType={}", entityType);
                return RlsExecutionContract.Execution.unavailable(subject + "(应答为空信封)");
            }
            if (RlsExecutionContract.isUnavailableEnvelope(resp)) {
                log.warn("AiSecurityEngineClient.applyRls UNAVAILABLE: security 失败信封 entityType={} code={} message={}",
                        entityType, resp.get("code"), resp.get("message"));
                return RlsExecutionContract.Execution.unavailable(
                        subject + "(security 失败信封 code=" + resp.get("code") + ")");
            }
            RlsExecutionContract.Execution exec = RlsExecutionContract
                    .read(resp.get("data"), subject)
                    .rejectForUnbindableChannel(subject);
            if (!exec.usable()) {
                log.warn("AiSecurityEngineClient.applyRls DENY: entityType={} state={} reason={}",
                        entityType, exec.state(), exec.reason());
            }
            return exec;
        } catch (Exception e) {
            log.warn("AiSecurityEngineClient.applyRls UNAVAILABLE: entityType={} reason={}",
                    entityType, e.getMessage());
            return RlsExecutionContract.Execution.unavailable(
                    subject + "(" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
        }
    }

    /**
     * RLS 的<b>不吞异常</b>版本：obligations 执行必须区分"无策略"与"security 不可用"，
     * 后者整条结果丢弃并 FAIL_CLOSED（F06-02 要点 2，禁"先返回再说明"）。
     *
     * <p>传输异常原样上抛（＝不可用态）；应答不可解析/缺片段/片段不可绑定同样抛
     * {@link SecurityResponseUnparsableException}（而不是返回空串冒充"无行级限制"），
     * 由调用方走 FAIL_CLOSED。理由码取单源，禁本类自写文案。</p>
     */
    public String applyRlsOrThrow(String userId, String tenantId, String entityType,
                                  String tableName, Map<String, Object> filters) throws Exception {
        RlsExecutionContract.Execution exec = rlsExecution(userId, tenantId, entityType,
                tableName, filters);
        if (!exec.usable()) {
            throw new SecurityResponseUnparsableException(
                    exec.reason() == null ? "rls/apply 返回不可执行形态: entityType=" + entityType
                            : exec.reason());
        }
        return exec.clause();
    }

    /** 拒绝片段（单源别名，值来自 {@link RlsExecutionContract#DENY_CLAUSE}，禁本类内联字面量）。 */
    public static final String DENY_CLAUSE = RlsExecutionContract.DENY_CLAUSE;

    /** 恒真片段＝裁决明确"不限制行"（与非平凡谓词、与拒绝皆分态）；同上为单源别名。 */
    public static final String ALLOW_ALL_CLAUSE = RlsExecutionContract.ALLOW_ALL_CLAUSE;

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

    /**
     * 单源读数 + 本通道形态约束（无参数绑定通道 ⇒ 需绑参的片段转拒绝）。
     *
     * <p>传输/寻址异常照抛，由 {@link #applyRls} 折叠成 {@code 1=0}、
     * {@link #applyRlsOrThrow} 原样上抛 —— 后者保留 §4.5.4 的"不可用 ≠ 无权限"分态。</p>
     */
    private RlsExecutionContract.Execution rlsExecution(String userId, String tenantId, String entityType,
                                                        String tableName, Map<String, Object> filters)
            throws Exception {
        Map<String, Object> resp = postJson(PATH_RLS_APPLY,
                rlsRequestBody(userId, tenantId, entityType, tableName, filters));
        String subject = "entityType=" + entityType;
        return RlsExecutionContract.read(resp == null ? null : resp.get("data"), subject)
                .rejectForUnbindableChannel(subject);
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

    /**
     * POST {@value #PATH_DECISION} — <b>AI 通道列面消费腿</b>（详设-01 C.2.3「三处同源」+
     * F01-02/W33「三通道唯一裁决入口」，#142/B4-3d-6 收口差距 <b>D-B4-28</b>）。
     *
     * <p><b>为什么走 /decision 而不是 {@code /cls/columns}</b>：S-6/W33 的"绕不过"前提是
     * <b>只有一条入口</b>。页面通道已在 B4-3d-2 合流，导出在 B4-3b，AI 再调分散面等于
     * 给"绕过裁决入口"留一条合法路径。{@code /cls/columns} 的退役判定另批做
     * （D-B4-15 附注），本方法不改它。</p>
     *
     * <p><b>不吞异常</b>（本类既有分态纪律，也是 {@code AiClsColumnSurfaceTest}
     * 里那条"钉现状"用例不适用于本腿的理由）：传输/寻址故障照抛 ⇒ 调用方按
     * {@code GUARDRAIL_FAIL_CLOSED}（503）处置；旧 {@code allowedColumns} 把同类故障
     * 折成空集＝冒充"策略拒绝"（403），那正是 §4.5.4「不可用 ≠ 无权限」禁止的折叠。</p>
     *
     * <p><b>主体只来自服务端解析</b>：入参 {@code userId} 由
     * {@code ToolSubjectResolver}（token 上下文）给出，禁取 LLM 自报的入参值。
     * 本进程出站不带信任链头（{@code postJson} 只带 {@code X-Request-Id}），故把主体
     * 放在请求体 {@code subject.subjectId}；security 侧该位可覆盖信任链，
     * 这一信任边界弱点登记为新差距（见差距文档 v1.0.23 行），不在本批掩盖。</p>
     *
     * @param userId  服务端解析的裁决主体
     * @param table   受保护资产定位符（工具 SQL 的单表白名单结果）
     * @param columns 列全集＝工具结果行的实际列键（空集时 security 侧判宇宙未解析）
     * @return 永不为 {@code null} 的 {@link ColumnSurfaceContract.Surface}（四态已分类）
     * @throws Exception 传输/寻址故障＝不可用态，原样上抛（禁折叠成空集）
     */
    public ColumnSurfaceContract.Surface columnSurface(String userId, String table,
                                                       List<String> columns) throws Exception {
        Map<String, Object> asset = new LinkedHashMap<>();
        asset.put(DecisionChannelContract.KEY_ASSET_TABLE, table);
        asset.put(DecisionChannelContract.KEY_ASSET_COLUMNS,
                columns == null ? List.of() : columns);
        Map<String, Object> subject = new LinkedHashMap<>();
        subject.put(DecisionChannelContract.KEY_SUBJECT_ID,
                userId == null || userId.isBlank() ? "anonymous" : userId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(DecisionChannelContract.KEY_ASSET_REF, asset);
        body.put(DecisionChannelContract.KEY_PURPOSE, DecisionChannelContract.PURPOSE_AI);
        body.put(DecisionChannelContract.KEY_SUBJECT, subject);

        Map<String, Object> resp = postJson(PATH_DECISION, body);
        if (resp == null) {
            throw new SecurityResponseUnparsableException(
                    "decision 应答为空信封: table=" + table);
        }
        // security 内部裁决失败以 HTTP 200 + code=503 信封应答（DecisionController 的
        // fail-closed 出口）；AI 侧必须认这层信封，否则"引擎没结论"会被读成"列面段缺失"，
        // 把可用性故障冒充成形态漂移（workspace 侧同一分类点见 unavailableEnvelope）。
        if (RlsExecutionContract.isUnavailableEnvelope(resp)) {
            throw new SecurityResponseUnparsableException(
                    "security 内部裁决失败（" + RlsExecutionContract.SEC_UNAVAILABLE_CODE + " 信封）: table="
                            + table + " code=" + resp.get("code") + " message=" + resp.get("message"));
        }
        Object data = resp.get("data");
        if (!(data instanceof Map<?, ?> dataMap)) {
            // 无 data 段＝无裁决出处。抛而非返回 SEGMENT_ABSENT：调用方要能在
            // 日志里区分"security 说没这回事"与"security 根本没答"。
            throw new SecurityResponseUnparsableException(
                    "decision 应答缺 data 段: table=" + table);
        }
        Object columnsSegment = dataMap.get(DecisionChannelContract.KEY_BUNDLE_COLUMNS);
        ColumnSurfaceContract.Surface surface = ColumnSurfaceContract.decide(columnsSegment);
        if (surface.state() == ColumnSurfaceContract.SurfaceState.TRIMMED
                && surface.legacyKeyUsed()) {
            log.warn("AiSecurityEngineClient.columnSurface 走 legacy 别名键: table={} size={}",
                    table, surface.visible().size());
        }
        return surface;
    }

    // 「不可用」信封的判据与码面（code=503 / ECOS-SEC-501）单源在
    // RlsExecutionContract#isUnavailableEnvelope（#142/B4-3d-10 收口 D-B4-26 余项时归并：
    // 此前本类与 workspace 各持一份，同一事实两套检索口径）。

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
