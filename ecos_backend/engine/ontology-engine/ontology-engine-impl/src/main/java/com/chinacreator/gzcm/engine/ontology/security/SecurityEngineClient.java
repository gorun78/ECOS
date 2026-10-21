package com.chinacreator.gzcm.engine.ontology.security;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract;
import com.chinacreator.gzcm.common.security.cls.ColumnSurfaceContract;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * T12 — SecurityEngineClient：ontology 引擎对 security-engine 的 RLS / CLS / ABAC / 脱敏 / 审计统一访问层。
 *
 * <p><b>来源: Wave B-1 · T12</b> | <b>日期: 2026-09-12</b> | <b>责任人: fullstack-implementer</b></p>
 * <p><b>继承铁律</b>：架构铁律 §2.4（行 RLS / 列 CLS / 脱敏 / OPA ABAC / Kafka 审计 5 项
 * + 默认 DENY，统一走 security-engine REST）、§2.5（横切护，禁止引擎内重复实现）。</p>
 *
 * <p>对应架构铁律 §2.4：
 * <ul>
 *   <li><b>行级过滤</b>：调 {@code POST /api/v1/security/rls/apply}</li>
 *   <li><b>列级过滤</b>：调 {@code POST /api/v1/security/cls/columns}</li>
 *   <li><b>脱敏</b>：调 {@code POST /api/v1/security/masking/apply}</li>
 *   <li><b>操作授权</b>：调 {@code POST /api/v1/security/policy-engine/evaluate}（OPA）</li>
 *   <li><b>审计</b>：发 Kafka {@code ecos.audit} topic（common-api KafkaTopics.AUDIT 常量）</li>
 *   <li><b>fail-closed</b>：security-engine 不可用时一律拒绝、不降级放行；但
 *       「不可用」与「策略拒绝」分态（ABAC 腿返回契约三态，见
 *       {@link #evaluatePolicy}，差距 D-B4-44）</li>
 * </ul>
 *
 * <p>不 import 其他 engine-impl（架构铁律 2.1），仅走 REST。</p>
 *
 * @author PMO-13
 * @since 2026-09-12
 */
@Component
public class SecurityEngineClient {

    private static final Logger log = LoggerFactory.getLogger(SecurityEngineClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_INSTANT;

    private static final String EMAIL_REGEX = "^(.)[^@]*(@.*)$";
    private static final String PHONE_REGEX = "^(\\d{3})\\d{4}(\\d{4})$";
    private static final String IDCARD_REGEX = "^(\\d{4})\\d{10}(\\d{4})$";

    /** RLS 拒绝片段（单源别名，值来自 {@link RlsExecutionContract#DENY_CLAUSE}，禁本类内联字面量）。 */
    public static final String DENY_CLAUSE = RlsExecutionContract.DENY_CLAUSE;

    /** RLS 恒真片段（裁决明确"不限制行"），与"未给谓词"分态；同上为单源别名。 */
    public static final String ALLOW_ALL_CLAUSE = RlsExecutionContract.ALLOW_ALL_CLAUSE;

    private static final Map<String, String> MASKING_RULES;
    static {
        // 10+ 对，Map.of 上限 10 对，改用 HashMap
        Map<String, String> m = new LinkedHashMap<>();
        m.put("phone", "PHONE");
        m.put("mobile", "PHONE");
        m.put("telephone", "PHONE");
        m.put("email", "EMAIL");
        m.put("mail", "EMAIL");
        m.put("idcard", "ID_CARD");
        m.put("id_card", "ID_CARD");
        m.put("identity", "ID_CARD");
        m.put("idnumber", "ID_CARD");
        m.put("id_number", "ID_CARD");
        m.put("amount", "AMOUNT");
        MASKING_RULES = Map.copyOf(m);
    }

    private final String baseUrl;
    private final int timeoutMs;

    /**
     * 可选 — runtime 统一事件总线（铁律 §2.5 公共底座）。
     * <p>O-25/W89 修复：原先以 Object 类型 Bean + 反射元数据查找方式直连
     * spring-kafka KafkaTemplate（该写法对 import 图检查不可见，且 bean 缺失时
     * 静默丢审计事件）。现收敛到 runtime-event {@link EventBusService}（Kafka 在 →
     * 入 topic；不在 → 内存 fallback），required=false 保证 runtime-event 未装配时
     * 静默拿到 null，audit 仅记 error 不抛。</p>
     */
    @Autowired(required = false)
    private EventBusService eventBus;

    /**
     * security-engine REST 专用客户端（2026-10-11 修订：自建，带配置超时）。
     * <p>原构造参数为 {@code @Autowired(required=false) RestTemplate}「来自 buszhi/gateway 全局」。
     * 聚合宿主下 RestTemplate 候选 Bean 十余个且无类型限定：gateway 靠 kb @Primary 才侥幸唯一，
     * workspace（不扫 kb config）直接 NoUniqueBeanDefinitionException 启动失败——且 required=false
     * 对「多候选歧义」并不生效，只兜「零候选」。改为按 sibling 客户端（DataNetResourceClient /
     * KbEngineGraphSyncClient / PublishGateService）同构的自建口径：
     * connect/read 超时取 {@code service.security.timeout-ms}，语义确定、任何宿主可装配。
     * 失败兜底不变：调用异常仍按铁律 §2.4 默认 DENY（applyRls 返回 "1=0"）。</p>
     */
    private final RestTemplate serviceRestTemplate;

    public SecurityEngineClient(
            @Value("${service.security.base-url:http://localhost:18081}") String baseUrl,
            @Value("${service.security.timeout-ms:5000}") int timeoutMs) {
        this.baseUrl = baseUrl;
        this.timeoutMs = timeoutMs;
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.serviceRestTemplate = new RestTemplate(factory);
    }

    /**
     * 测试缝（#142/B4-3e-12）：注入替身 {@link RestTemplate} 以覆盖 ABAC 三态读数面。
     * <p>生产装配仍走上面的自建口径，本构造只供同包单测构造裁决替身应答，禁业务代码调用。</p>
     */
    SecurityEngineClient(String baseUrl, int timeoutMs, RestTemplate serviceRestTemplate) {
        this.baseUrl = baseUrl;
        this.timeoutMs = timeoutMs;
        this.serviceRestTemplate = serviceRestTemplate;
    }

    // ═══════════════ 1. RLS — 行级安全 ═══════════════

    /**
     * 行级 RLS — 请求 security-engine 计算 WHERE 条件。
     *
     * <p><b>#142/B4-3c-2 收口（D-B4-17）</b>：本方法此前在应答缺 {@code whereClause}
     * 字段时返回<b>空串</b>，而调用方把空串当"无行级限制"直接返回原行集 —— 即
     * 字段名漂移（生产者从未发过该字段）= 全量渗漏。</p>
     *
     * <p><b>#142/B4-3d-5 单源化（差距 D-B4-24）</b>：上述收口在本类里是
     * <b>第二套手抄解析</b>（自读 {@code "whereClause"} 字面量 + 自判 {@code '?'}）。
     * 现改为委托 {@link RlsExecutionContract#read}：四道构造期不变量（缺片段／
     * {@code ?} 与参数个数不符／参数含 {@code null}／仍含 {@code :name}）由单源一次做完，
     * 本通道再叠加自己的形态约束 {@link RlsExecutionContract.Execution#rejectForUnbindableChannel}
     * ——本引擎的 RLS 消费面是<b>响应侧内存过滤</b>、无参数绑定通道，含 {@code ?}
     * 的片段宁 DENY 不猜值拼接（IR05；参数化下推需 DAO 侧改造，已登记）。</p>
     *
     * <p><b>对外形态不变</b>：返回值仍是 WHERE 片段字符串，
     * 拒绝/漂移/不可绑定 → {@link #DENY_CLAUSE}，恒真 → 原样返回（调用方据此"不限制行"）。</p>
     *
     * <p><b>#142/B4-3d-10（D-B4-26 余项）</b>：本方法保留「把不可用折叠成 {@code 1=0}」的
     * 旧语义（改签名会撞「API 只增不改」），但折叠不再是唯一出口 —— 需要分态的调用方
     * 改用 {@link #applyRlsExecution}，本方法只是它上面的一层归一。</p>
     *
     * @param entityType 实体类型
     * @param table      表名
     * @param filters    过滤条件键值
     * @return WHERE 条件片段；拒绝/漂移/不可绑定/不可用 → {@code "1=0"}
     */
    public String applyRls(String entityType, String table, Map<String, Object> filters) {
        RlsExecutionContract.Execution exec = applyRlsExecution(entityType, table, filters);
        if (!exec.usable()) {
            // 日志在此不再重复：applyRlsExecution 已按态打过一次带理由的 warn，
            // 两处各打＝同一事实两套文案，排障时无法确定哪条是权威。
            return DENY_CLAUSE;
        }
        return exec.clause();
    }

    /**
     * 行级 RLS 的<b>带态</b>出口（#142/B4-3d-10，差距 D-B4-26 余项）。
     *
     * <p><b>与 {@link #applyRls} 的差别只有一处、但语义上是一级</b>：本方法把
     * "security 进程没答"（连不上／超时／寻址失败／空信封／{@code ECOS-SEC-501}
     * 失败信封）表达为 {@link RlsExecutionContract.State#UNAVAILABLE}，
     * 而不是把它冒充成"策略拒绝"（{@code 1=0}）。§4.5.4「不可用 ≠ 无权限」要的
     * 不是两种处置（两者都 fail-closed），而是<b>可寻址</b>：403 触发权限申诉、
     * 503 触发运维告警，折叠之后两者互相掩盖。</p>
     *
     * <p><b>永不抛异常</b>：传输故障也归到返回值里（{@link RlsExecutionContract.Execution#unavailable}），
     * 调用方拿到的永远是四态之一的可解释读数；本引擎的对外形态由调用方决定
     * （读切面写 503 信封，而非让异常穿过 {@code @AfterReturning} 变成 500）。</p>
     *
     * @param entityType 实体类型
     * @param table      表名
     * @param filters    过滤条件键值
     * @return 永不为 {@code null} 的执行契约读数（本通道已叠加 {@code rejectForUnbindableChannel}）
     */
    public RlsExecutionContract.Execution applyRlsExecution(String entityType, String table,
                                                            Map<String, Object> filters) {
        String subject = "table=" + table;
        if (serviceRestTemplate == null) {
            log.warn("SecurityEngineClient.applyRls UNAVAILABLE: 通道未装配 entityType={} table={}",
                    entityType, table);
            return RlsExecutionContract.Execution.unavailable(subject + "(RestTemplate 未装配)");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tableName", table);
            body.put("userId", currentUserIdOrAnonymous());
            body.put("entityType", entityType);
            if (filters != null && !filters.isEmpty()) {
                body.put("filters", filters);
            }
            Map<String, Object> resp = postJson("/api/v1/security/rls/apply", body);
            if (resp == null) {
                log.warn("SecurityEngineClient.applyRls UNAVAILABLE: 应答空信封 entityType={} table={}",
                        entityType, table);
                return RlsExecutionContract.Execution.unavailable(subject + "(应答为空信封)");
            }
            // security 内部裁决失败以 HTTP 200 + ECOS-SEC-501 信封应答：不认这层信封
            // 就会把"引擎没结论"读成"应答缺执行契约"＝把可用性故障冒充成形态漂移。
            if (RlsExecutionContract.isUnavailableEnvelope(resp)) {
                log.warn("SecurityEngineClient.applyRls UNAVAILABLE: security 失败信封 entityType={} table={} code={} message={}",
                        entityType, table, resp.get("code"), resp.get("message"));
                return RlsExecutionContract.Execution.unavailable(
                        subject + "(security 失败信封 code=" + resp.get("code") + ")");
            }
            RlsExecutionContract.Execution exec = RlsExecutionContract
                    .read(resp.get("data"), subject)
                    .rejectForUnbindableChannel(subject);
            if (!exec.usable()) {
                log.warn("SecurityEngineClient.applyRls DENY: table={} state={} reason={}",
                        table, exec.state(), exec.reason());
            }
            return exec;
        } catch (Exception e) {
            log.warn("SecurityEngineClient.applyRls UNAVAILABLE: entityType={} table={} reason={}",
                    entityType, table, e.getMessage());
            return RlsExecutionContract.Execution.unavailable(
                    subject + "(" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
        }
    }

    // ═══════════════ 2. CLS — 列级安全 ═══════════════

    /**
     * 列级 CLS — 给定实体全列，返回当前用户可见列。
     *
     * <p><b>#142/B4-3d-1 语义订正（差距 D-B4-25）</b>：读数位此前只认
     * {@code data.allowedColumns}，而 CLS 生产者写的键是 {@code visibleColumns}
     * ⇒ 本体通道的列裁剪结构性恒空集（恒 DENY）。现委托
     * {@link ColumnSurfaceContract#read} 单源读取；空集＝策略拒绝、
     * 两键皆不可读＝形态漂移（带理由日志），两者都返 {@code List.of()}，
     * 但真实可读集现在真的能返回。</p>
     *
     * @param entityType 实体类型
     * @param cols       全列列表
     * @return 可见列集合；DENY / 列面漂移时返回 List.of()
     */
    @SuppressWarnings("unchecked")
    public List<String> filterColumns(String entityType, List<String> cols) {
        if (serviceRestTemplate == null || cols == null || cols.isEmpty()) {
            return List.of();
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tableName", entityType);
            body.put("userId", currentUserIdOrAnonymous());
            body.put("allColumns", cols);
            Map<String, Object> resp = postJson("/api/v1/security/cls/columns", body);
            ColumnSurfaceContract.Reading reading =
                    ColumnSurfaceContract.read(resp == null ? null : resp.get("data"));
            if (reading.drifted()) {
                log.warn("SecurityEngineClient.filterColumns 列面漂移 → DENY: entityType={} reason={}",
                        entityType, reading.reason());
                return List.of();
            }
            if (reading.visible().isEmpty()) {
                log.warn("SecurityEngineClient.filterColumns 策略拒绝全部列(ECOS-SEC-402): entityType={}",
                        entityType);
            } else if (reading.legacyKeyUsed()) {
                log.warn("SecurityEngineClient.filterColumns 走 legacy 别名键: entityType={} size={}",
                        entityType, reading.visible().size());
            }
            return reading.visible();
        } catch (Exception e) {
            log.warn("SecurityEngineClient.filterColumns DENY: entityType={} reason={}",
                    entityType, e.getMessage());
            return List.of();
        }
    }

    // ═══════════════ 3. 脱敏 ═══════════════

    /**
     * 脱敏 — 本地正则保留响应速度，远端 security-engine 做加固（不阻塞主流程）。
     * 敏感字段名按 {@link #MASKING_RULES} 命中。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> mask(Object record, List<String> keyCols) {
        Map<String, Object> original = record instanceof Map
                ? (Map<String, Object>) record
                : toStringMap(record);
        Map<String, Object> result = new LinkedHashMap<>(original);
        // 本地兜底脱敏
        for (String key : result.keySet()) {
            if (keyCols != null && !keyCols.isEmpty() && !keyCols.contains(key)) continue;
            String rule = MASKING_RULES.get(key == null ? "" : key.toLowerCase());
            if (rule == null || rule.isEmpty()) continue;
            Object val = result.get(key);
            if (val instanceof String str && !str.isEmpty()) {
                result.put(key, applyLocalMask(str, rule));
            }
        }
        // 远端加固脱敏 — 异常忽略
        try {
            if (serviceRestTemplate != null) {
                List<String> data = new ArrayList<>();
                for (Object v : result.values()) {
                    if (v instanceof String s && !s.isEmpty()) data.add(s);
                }
                List<String> rules = new ArrayList<>();
                if (keyCols != null) {
                    for (String k : keyCols) {
                        String r = MASKING_RULES.getOrDefault(k == null ? "" : k.toLowerCase(), "PHONE");
                        if (r != null && !r.isEmpty()) rules.add(r);
                    }
                }
                if (!data.isEmpty() && !rules.isEmpty()) {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("data", data);
                    body.put("rules", rules);
                    Map<String, Object> resp = postJson("/api/v1/security/masking/apply", body);
                    applyRemoteMask(result, resp == null ? null : (List<?>) (resp.get("data") instanceof Map d ? ((Map<?, ?>) d).get("results") : null));
                }
            }
        } catch (Exception e) {
            log.debug("SecurityEngineClient.mask remote failed, fallback to local: {}", e.getMessage());
        }
        return result;
    }

    // ═══════════════ 4. ABAC — OPA 策略引擎 ═══════════════

    /**
     * ABAC — OPA 策略裁决，返回<b>契约三态</b>，不在本类做布尔结论。
     *
     * <p><b>#142/B4-3e-12 分态（差距 D-B4-44）</b>：收口前本方法用
     * {@link AbacEvaluateResponseContract#isExplicitAllow} 返回 {@code boolean}，
     * 该读数对 {@code DENY} 与 {@code UNEVALUATED} 一律给 {@code false} —— 于是
     * ①RestTemplate 缺失 ②空信封／security 5xx／传输解析异常
     * ③HTTP 200 但应答无可识别裁决，这三种"护栏没说话"与真实策略 DENY 同值，
     * 调用方统一转 403，把依赖故障伪装成"这人没权限"（违 §4.7.2）。
     * 现三态原样返回，由 {@code OntologyCandidateService} 分面落码
     * （DENY→403／UNEVALUATED→503 + ECOS-SEC-501），与 workspace 腿（D-B4-41）同口径。</p>
     *
     * @return {@code ALLOW} 放行／{@code DENY} 策略拒绝／{@code UNEVALUATED} 护栏未说话（不可用）
     */
    @SuppressWarnings("unchecked")
    public AbacEvaluateResponseContract.Verdict evaluatePolicy(String action, Map<String, Object> attrs) {
        if (serviceRestTemplate == null) {
            return unevaluated(action, "RestTemplate 未装配（security-engine 客户端缺失）");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("policy", "rbac");
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("action", action);
            if (attrs != null) input.putAll(attrs);
            input.put("subject", Map.of(
                    "userId", currentUserIdOrAnonymous(),
                    "tenantId", "default",
                    "role", "user"));
            body.put("input", input);
            Map<String, Object> resp = postJson("/api/v1/security/policy-engine/evaluate", body);
            if (resp == null) {
                return unevaluated(action, "应答为空信封");
            }
            // security 侧内部裁决失败以 HTTP 200 + 失败信封应答（没有 data 段），同属不可用，
            // 不得读成"应答形态漂移"＝策略拒绝；判据单源在 RlsExecutionContract。
            if (RlsExecutionContract.isUnavailableEnvelope(resp)) {
                return unevaluated(action, "security 失败信封 code=" + resp.get("code"));
            }
            Object dataObj = resp.get("data");
            if (dataObj == null) {
                return unevaluated(action, "应答缺 data 段（契约断裂）");
            }
            // 裁决读数走单源：本类此前只认历史别名 result，而现契约应答 data 段发的是 allow，
            // 实测恒读到 null ⇒ 无论策略怎么判都 DENY（差距 D-B4-39）。
            AbacEvaluateResponseContract.Verdict verdict =
                    AbacEvaluateResponseContract.verdictOf(dataObj);
            if (verdict == AbacEvaluateResponseContract.Verdict.UNEVALUATED) {
                return unevaluated(action, "HTTP 200 但应答无可识别裁决");
            }
            return verdict;
        } catch (Exception e) {
            return unevaluated(action, "传输/解析失败 " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
    }

    /** UNEVALUATED 归一出口：留 warn 日志（含原因）后原样返回三态，不在本类折叠成布尔。 */
    private AbacEvaluateResponseContract.Verdict unevaluated(String action, String reason) {
        log.warn("SecurityEngineClient.evaluatePolicy UNEVALUATED (不可用，非策略 DENY): "
                + "action={} reason={}", action, reason);
        return AbacEvaluateResponseContract.Verdict.UNEVALUATED;
    }

    // ═══════════════ 5. 审计 — Kafka ecos.audit ═══════════════

    /**
     * 审计事件 — 经 runtime-event {@link EventBusService#publish} 发 {@link KafkaTopics#AUDIT}
     * topic（common-api 常量）。
     * <p>不阻塞主流程：bean 缺失或 publish 抛错仅记 error，不影响业务主流程（O-25/W89）。
     * 引擎内禁止 new KafkaTemplate 或以反射元查找方式直接调用其发送方法；此类基础设施
     * 一律收敛于 runtime-event {@link EventBusService}，源码级门禁见
     * {@code ReflectiveKafkaBypassArchTest}。</p>
     */
    public void audit(String action, Object result) {
        try {
            long ts = System.currentTimeMillis();
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventId", UUID.randomUUID().toString());
            event.put("timestamp", Instant.ofEpochMilli(ts)
                    .atZone(ZoneId.systemDefault()).format(ISO_DATE));
            event.put("userId", currentUserIdOrAnonymous());
            event.put("action", action);
            event.put("resource", "ontology");
            event.put("result", result == null ? "OK" : String.valueOf(result));
            if (eventBus == null) {
                log.error("SecurityEngineClient.audit: EventBusService 未装配（runtime-event 缺失），审计事件仅记日志(数据丢失风险): action={} userId={}",
                        action, currentUserIdOrAnonymous());
                return;
            }
            eventBus.publish(KafkaTopics.AUDIT, event);
        } catch (Exception e) {
            log.error("SecurityEngineClient.audit: 发布审计事件失败: action={} reason={}",
                    action, e.getMessage(), e);
        }
    }

    // ═══════════════ helpers ═══════════════

    private String currentUserIdOrAnonymous() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                Object principal = auth.getPrincipal();
                if (principal instanceof String s) return s;
                return auth.getName();
            }
        } catch (Exception ignored) {
            // fail-safe：退化为 anonymous 走最小权限，而非伪造真实主体
            log.debug("SecurityEngineClient: 读取 SecurityContext 失败，主体退化为 anonymous: {}", ignored.getMessage());
        }
        return "anonymous";
    }

    private Map<String, Object> postJson(String path, Map<String, Object> body) {
        if (serviceRestTemplate == null) return null;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> resp = serviceRestTemplate.postForEntity(
                baseUrl + path, entity, Map.class);
        return resp == null ? null : resp.getBody();
    }

    private static Map<String, Object> toStringMap(Object o) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (o instanceof Map<?, ?> map) {
            map.forEach((k, v) -> m.put(String.valueOf(k), v));
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private void applyRemoteMask(Map<String, Object> result, Object remoteResults) {
        if (!(remoteResults instanceof List<?> remote)) return;
        int i = 0;
        for (Map.Entry<String, Object> e : result.entrySet()) {
            if (i >= remote.size()) break;
            if (e.getValue() instanceof String) {
                Object v = remote.get(i);
                if (v instanceof String s) {
                    result.put(e.getKey(), s);
                    i++;
                }
            }
        }
    }

    private String applyLocalMask(String raw, String rule) {
        return switch (rule) {
            case "PHONE" -> maskPhone(raw);
            case "EMAIL" -> maskEmail(raw);
            case "ID_CARD" -> maskIdCard(raw);
            case "AMOUNT" -> raw.replaceAll("\\d{1,9}(?=(\\.\\d{1,2})?$)", "*");
            default -> raw;
        };
    }

    private static String maskEmail(String raw) {
        if (raw == null || !raw.contains("@")) return raw;
        var m = Pattern.compile(EMAIL_REGEX).matcher(raw);
        if (m.matches()) return m.group(1) + "***" + m.group(2);
        return raw.charAt(0) + "***" + raw.substring(raw.indexOf('@'));
    }

    private static String maskPhone(String raw) {
        if (raw == null) return raw;
        var m = Pattern.compile(PHONE_REGEX).matcher(raw);
        if (m.matches()) return m.group(1) + "****" + m.group(2);
        if (raw.length() >= 7) {
            return raw.substring(0, 3) + "*".repeat(raw.length() - 6) + raw.substring(raw.length() - 3);
        }
        return raw;
    }

    private static String maskIdCard(String raw) {
        if (raw == null) return raw;
        var m = Pattern.compile(IDCARD_REGEX).matcher(raw);
        if (m.matches()) return m.group(1) + "**********" + m.group(2);
        if (raw.length() == 18) return raw.substring(0, 4) + "**********" + raw.substring(14);
        if (raw.length() == 15) return raw.substring(0, 4) + "*******" + raw.substring(11);
        return raw;
    }
}
