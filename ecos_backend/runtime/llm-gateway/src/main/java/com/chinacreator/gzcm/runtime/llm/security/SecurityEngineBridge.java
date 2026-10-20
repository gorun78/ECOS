package com.chinacreator.gzcm.runtime.llm.security;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract;
import com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract.Verdict;
import com.chinacreator.gzcm.common.trace.EcosTraceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * H10-T1 (PMO-74 N14 / 铁律 §2.4) — llm-gateway 对 security-engine 密钥/裁决/审计能力的统一桥。
 *
 * <p>背景：llm-gateway 此前 apiKey 由调用方请求体明文携带（{@code ChatRequest.apiKey}），
 * {@code ProfileConfig.apiKeyRef} 设计字段零接线，security-engine 集成探针命中 0。</p>
 *
 * <p>详细设计-06 F06-05 要点 4（R-18 裁决项，本册默认选项 ①）：<b>ABAC 裁决改走 security-engine
 * REST</b>（与 ai-engine {@code AiSecurityEngineClient} 同款先例）。原"类名反射 + 同 JVM bean"
 * 通道在 gateway fat-JAR 态可用、在独立 service 态因 security 类不在 classpath 而恒 DENY，
 * 两态行为不一致；且配套的 {@code llm.gateway.abac-eval-enabled} 可关开关本身即绕过面，已废除。
 * 密钥取得仍走同 JVM bean 反射（引用而非明文，缺失即 null → 调用方 DENY）。</p>
 *
 * <p>裁决 base URL（F02-15 两态寻址单源）：显式 {@code service.security.base-url} 覆写 →
 * runtime-access {@code ServiceEndpointResolver}（route-manifest 单源，owner={@code sysman}）
 * → 皆不可用即空串（默认 DENY）。<b>本类源码内不得出现 {@code http://localhost:1808x} 字面量</b>；
 * resolver 经类名反射访问（与 {@code ISecretService} 同款通道），避免 llm-gateway 新增
 * runtime-access 编译期依赖（该模块带入 tika/minio/neo4j driver，体积不成比例）。</p>
 *
 * <ol>
 *   <li><b>密钥取得</b>：{@link #resolveSecret(String, String)} 解析 {@code apiKeyRef} 引用
 *       （{@code secret:<id>} / {@code enc:<keyId>:<cipherText>} / 裸 {@code <secretId>}），
 *       经 {@code ISecretService.getSecretValue} 或 {@code IDataEncryptionService.decrypt}
 *       （security-engine crypto 域）还原明文密钥，<b>仅在本方法返回值中短暂存在</b>，
 *       日志/审计一律只出掩码引用。</li>
 *   <li><b>ABAC 裁决</b>：{@link #evaluate(String, String, String, Map)} 经 security-engine
 *       {@code POST /api/v1/security/policy-engine/evaluate}（{@link #POLICY_TIMEOUT_MS} 预算）。</li>
 *   <li><b>审计</b>：{@link #audit(String, String, String, String)} 优先 runtime-event
 *       {@code EventBusService}，其次 {@code kafkaTemplate} 反射兜底，topic =
 *       {@link KafkaTopics#AUDIT}（铁律 §2.4#5 新代码走 Kafka）。</li>
 * </ol>
 *
 * <p>🔴 fail-closed（铁律 §2.4#6）：security-engine 类/bean 不在 classpath、REST 非 2xx、
 * 超时、响应不可解析、引用无法解析 — 一律返回「不可用」（null / false）并 {@code log.warn}，
 * 由调用方默认 DENY；<b>绝不回退到调用方自报的明文密钥，绝不放行直连</b>。</p>
 */
@Component
public class SecurityEngineBridge {

    private static final Logger log = LoggerFactory.getLogger(SecurityEngineBridge.class);

    /** security-engine 密钥服务（接口所在 FQN，编译期不依赖，运行时按名解析） */
    private static final String SECRET_SERVICE_FQN =
            "com.chinacreator.gzcm.engine.security.crypto.service.ISecretService";
    /** security-engine 加解密服务 */
    private static final String ENCRYPTION_SERVICE_FQN =
            "com.chinacreator.gzcm.engine.security.crypto.IDataEncryptionService";
    /** runtime-event 横切审计出口（铁律 §2.5#7，禁自建 KafkaTemplate） */
    private static final String EVENTBUS_FQN =
            "com.chinacreator.gzcm.runtime.eventbus.EventBusService";

    /** F06-05 要点 4：ABAC 裁决端点（与 ai-engine 侧同一 security-engine 契约，单点常量） */
    static final String PATH_POLICY_EVALUATE = "/api/v1/security/policy-engine/evaluate";

    /** PRD-06 §1.3 / W145 同口径：裁决预算 2s（复用底座 OkHttpClient 的 per-call 超时） */
    static final int POLICY_TIMEOUT_MS = 2000;

    /** runtime-access 两态寻址单源（F02-15；反射访问，禁为本类新增编译期重依赖） */
    private static final String ENDPOINT_RESOLVER_FQN =
            "com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver";
    /** resolver 的 RouteMode 嵌套枚举 FQN */
    private static final String ROUTE_MODE_FQN = ENDPOINT_RESOLVER_FQN + "$RouteMode";
    /** route-manifest 中承载 security 域的 owner（{@code /api/v1/security} → sysman） */
    private static final String SECURITY_ROUTE_OWNER = "sysman";

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private final ApplicationContext applicationContext;
    private final ObjectProvider<OkHttpClient> httpClientProvider;
    /** 显式覆写（默认空 = 走 resolver）；禁带硬编码端口默认值（F02-15 ①） */
    private final String securityBaseUrlOverride;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    @Qualifier("kafkaTemplate")
    private Object kafkaTemplateBean;

    @Autowired
    public SecurityEngineBridge(ApplicationContext applicationContext,
                               ObjectProvider<OkHttpClient> httpClientProvider,
                               @Value("${service.security.base-url:}") String securityBaseUrlOverride,
                               ObjectMapper objectMapper) {
        this.applicationContext = applicationContext;
        this.httpClientProvider = httpClientProvider;
        this.securityBaseUrlOverride = securityBaseUrlOverride == null
                ? "" : securityBaseUrlOverride.trim();
        this.objectMapper = objectMapper;
    }

    // ═══════════════ 1. 密钥取得（默认 DENY，不回退明文） ═══════════════

    /**
     * 将 {@code apiKeyRef} 引用解析为可外发的 provider 密钥明文。
     *
     * <p>支持三种引用形态（大小写不敏感的 scheme 前缀）：</p>
     * <ul>
     *   <li>{@code secret:<secretId>} 或 {@code secret://<secretId>} —
     *       security-engine {@code ISecretService.getSecretValue(secretId, callerId)}</li>
     *   <li>{@code enc:<keyId>:<base64CipherText>} —
     *       security-engine {@code IDataEncryptionService.decrypt(cipher, keyId)}</li>
     *   <li>裸 {@code <secretId>} — 按 {@code secret:} 形态处理（引用而非明文，由密钥仓校验）</li>
     * </ul>
     *
     * @return 明文密钥；引用为空 / security-engine 不可用 / 解析失败 → {@code null}（调用方须默认 DENY）
     */
    public String resolveSecret(String apiKeyRef, String callerId) {
        if (apiKeyRef == null || apiKeyRef.isBlank()) {
            log.warn("[llm-gateway] apiKeyRef 为空，无法经 security-engine 密钥服务取得密钥（默认 DENY，不回退明文）: caller={}",
                    callerId);
            return null;
        }
        String ref = apiKeyRef.trim();
        try {
            if (ref.regionMatches(true, 0, "enc:", 0, 4)) {
                return decryptViaEncryptionService(ref.substring(4), callerId);
            }
            String secretId;
            if (ref.regionMatches(true, 0, "secret://", 0, 9)) {
                secretId = ref.substring(9);
            } else if (ref.regionMatches(true, 0, "secret:", 0, 7)) {
                secretId = ref.substring(7);
            } else {
                secretId = ref; // 裸引用 = secretId
            }
            return readSecretValue(secretId, callerId);
        } catch (Exception e) {
            // fail-closed：任何解析异常都不回退明文，只 warn 掩码引用
            log.warn("[llm-gateway] security-engine 密钥解析失败（默认 DENY，不回退明文）: ref={}, caller={}, reason={}",
                    maskRef(ref), callerId, rootMessage(e));
            return null;
        }
    }

    private String readSecretValue(String secretId, String callerId) {
        Object bean = findBeanByClassName(SECRET_SERVICE_FQN);
        if (bean == null) {
            log.warn("[llm-gateway] security-engine ISecretService 不可用（类/Bean 缺失）— 密钥无法取得，默认 DENY，不回退明文: ref=secret:{}",
                    shortRef(secretId));
            return null;
        }
        try {
            Method m = bean.getClass().getMethod("getSecretValue", String.class, String.class);
            Object value = m.invoke(bean, secretId, callerId == null ? "service:llm-gateway" : callerId);
            if (value == null || value.toString().isBlank()) {
                log.warn("[llm-gateway] ISecretService.getSecretValue 返回空（默认 DENY）: ref=secret:{}", shortRef(secretId));
                return null;
            }
            return value.toString();
        } catch (Exception e) {
            log.warn("[llm-gateway] ISecretService.getSecretValue 调用失败（默认 DENY，不回退明文）: ref=secret:{}, reason={}",
                    shortRef(secretId), rootMessage(e));
            return null;
        }
    }

    private String decryptViaEncryptionService(String keyIdAndCipher, String callerId) {
        int sep = keyIdAndCipher.indexOf(':');
        if (sep <= 0 || sep == keyIdAndCipher.length() - 1) {
            log.warn("[llm-gateway] enc: 引用格式非法（应为 enc:<keyId>:<cipher>），默认 DENY: caller={}", callerId);
            return null;
        }
        String keyId = keyIdAndCipher.substring(0, sep);
        String cipher = keyIdAndCipher.substring(sep + 1);
        Object bean = findBeanByClassName(ENCRYPTION_SERVICE_FQN);
        if (bean == null) {
            log.warn("[llm-gateway] security-engine IDataEncryptionService 不可用（类/Bean 缺失）— 密文无法解密，默认 DENY，不回退明文: keyId={}",
                    shortRef(keyId));
            return null;
        }
        try {
            Method m = bean.getClass().getMethod("decrypt", String.class, String.class);
            Object value = m.invoke(bean, cipher, keyId);
            if (value == null || value.toString().isBlank()) {
                log.warn("[llm-gateway] IDataEncryptionService.decrypt 返回空（默认 DENY）: keyId={}", shortRef(keyId));
                return null;
            }
            return value.toString();
        } catch (Exception e) {
            log.warn("[llm-gateway] IDataEncryptionService.decrypt 调用失败（默认 DENY，不回退明文）: keyId={}, reason={}",
                    shortRef(keyId), rootMessage(e));
            return null;
        }
    }

    // ═══════════════ 2. ABAC 裁决（security-engine REST，默认 DENY） ═══════════════

    /**
     * 经 security-engine REST {@value #PATH_POLICY_EVALUATE} 做操作授权裁决。
     *
     * <p>F06-05 要点 4：两态（gateway fat-JAR / 独立 service）一致走 HTTP，不再有
     * "security 类不在 classpath 即恒 DENY" 与 "配置开关可关裁决" 两个绕过面。
     * 铁律 §2.4#4/#6：不可用 / 超时 / 非明确 ALLOW → 一律 false（默认 DENY）。</p>
     */
    public boolean evaluate(String callerId, String tenantId, String action, Map<String, Object> attrs) {
        String baseUrl = securityBaseUrl();
        if (baseUrl.isEmpty()) {
            log.warn("[llm-gateway] security base URL 不可得（service.security.base-url 未配置且 route-manifest 寻址失败），"
                    + "ABAC 裁决默认 DENY: action={}, caller={}", action, callerId);
            return false;
        }
        OkHttpClient base;
        try {
            base = httpClientProvider == null ? null : httpClientProvider.getIfAvailable();
        } catch (Exception e) {
            log.warn("[llm-gateway] llm-gateway 底座 OkHttpClient 不可解析 — ABAC 裁决不可用，默认 DENY: action={}, reason={}",
                    action, rootMessage(e));
            return false;
        }
        if (base == null) {
            log.warn("[llm-gateway] llm-gateway 底座 OkHttpClient 未装配 — ABAC 裁决不可用，默认 DENY: action={}, caller={}",
                    action, callerId);
            return false;
        }
        Map<String, Object> body = evaluateBody(callerId, tenantId, action, attrs);
        try {
            OkHttpClient client = base.newBuilder()
                    .callTimeout(POLICY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .connectTimeout(POLICY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .readTimeout(POLICY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .build();
            Request httpReq = new Request.Builder()
                    .url(baseUrl + PATH_POLICY_EVALUATE)
                    .header(EcosTraceContext.HEADER_REQUEST_ID, EcosTraceContext.currentTraceId())
                    .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON_MEDIA_TYPE))
                    .build();
            try (Response resp = client.newCall(httpReq).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) {
                    log.warn("[llm-gateway] security-engine 裁决 HTTP 异常（默认 DENY）: action={}, code={}",
                            action, resp.code());
                    return false;
                }
                return isExplicitAllow(parseDataSegment(resp.body().string()), action, callerId);
            }
        } catch (Exception e) {
            log.warn("[llm-gateway] security-engine 裁决调用异常（默认 DENY）: action={}, reason={}",
                    action, rootMessage(e));
            return false;
        }
    }

    /** 裁决请求体（{@code policy=rbac} + 四段 input，与 security-engine 契约单源一致）。 */
    private Map<String, Object> evaluateBody(String callerId, String tenantId, String action,
                                             Map<String, Object> attrs) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("action", action);
        if (attrs != null) input.putAll(attrs);
        input.put("subject", Map.of(
                "userId", callerId == null ? "anonymous" : callerId,
                "tenantId", tenantId == null ? "default" : tenantId,
                "role", "llm-gateway-service"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("policy", "rbac");
        body.put("input", input);
        return body;
    }

    /** 响应 {@code data} 段；缺段/不可解析 → null（由 {@link #isExplicitAllow} 判 DENY）。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseDataSegment(String json) {
        try {
            Map<String, Object> resp = objectMapper.readValue(json, Map.class);
            return resp.get("data") instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
        } catch (Exception e) {
            log.warn("[llm-gateway] security-engine 裁决响应不可解析（默认 DENY）: {}", rootMessage(e));
            return null;
        }
    }

    /**
     * 恰为显式 ALLOW 才放行 —— 裁决键面改读
     * {@link AbacEvaluateResponseContract#verdictOf(Object)}（#142/B4-3e-4，D-B4-37）。
     *
     * <p>收口前本方法自带一套 {@code decision}/{@code verdict}/{@code allow}/{@code result}
     * 内联字面量，与 ai-engine 侧同型重复，且<b>不看 {@code policyState}</b>：
     * 护栏处于「策略未加载／OPA 不可达兜底」时它按 DENY 处理，日志里说的是
     * "未放行"，实际是"护栏没说话"。现在两态都由单源给出，本方法只保留
     * 未放行原因的留痕（{@link Verdict} 原文进日志，禁在此再解释一遍键面）。</p>
     */
    private boolean isExplicitAllow(Map<String, Object> data, String action, String callerId) {
        Verdict verdict = AbacEvaluateResponseContract.verdictOf(data);
        if (verdict == Verdict.ALLOW) return true;
        if (data == null) {
            log.warn("[llm-gateway] security-engine 裁决无 data 段（默认 DENY）: action={}, caller={}",
                    action, callerId);
        } else {
            log.warn("[llm-gateway] security-engine 裁决未放行（默认 DENY）: action={}, caller={}, verdict={}",
                    action, callerId, verdict);
        }
        return false;
    }

    // ═══════════════ 3. 审计（Kafka ecos.audit，经 runtime-event 横切出口） ═══════════════

    /**
     * 关键事件留痕（密钥解析结果 / LLM 调用裁决结果）。
     * 失败只 warn 不阻塞主流程；payload 只含掩码引用，绝不含密钥明文。
     */
    public void audit(String callerId, String action, String resource, String result) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventId", UUID.randomUUID().toString());
            event.put("timestamp", Instant.now().toString());
            event.put("module", "llm-gateway");
            event.put("userId", callerId == null ? "anonymous" : callerId);
            event.put("traceId", EcosTraceContext.currentTraceId());
            event.put("action", action);
            event.put("resource", resource);
            event.put("result", result == null ? "OK" : result);
            publishAuditEvent(event);
        } catch (Exception e) {
            log.warn("[llm-gateway] 审计事件发布失败: action={}, reason={}", action, e.getMessage());
        }
    }

    /**
     * 结构化审计事件出口（详细设计-06 F06-07 要点 2：{@code LLM_USAGE} 落 {@code ecos.audit} 链）。
     *
     * <p>与 {@link #audit(String, String, String, String)} 同一条通道（runtime-event
     * {@code EventBusService} 优先，其次 kafkaTemplate 反射兜底），供网关计量器复用，
     * 禁在别处自建 KafkaTemplate（铁律 §2.5#7）。</p>
     *
     * @return 事件是否已交到底座通道（false = 无可用通道，调用方须自行留痕并暴露未上报态）
     */
    public boolean publishStructuredAudit(Map<String, Object> event) {
        if (event == null || event.isEmpty()) {
            return false;
        }
        event.putIfAbsent("traceId", EcosTraceContext.currentTraceId());
        return publishAuditEvent(event);
    }

    private boolean publishAuditEvent(Map<String, Object> event) {
        // 1) runtime-event EventBusService 横切通道（类名反射，避免编译期依赖）
        try {
            Class<?> type = Class.forName(EVENTBUS_FQN);
            String[] names = applicationContext.getBeanNamesForType(type);
            if (names.length > 0) {
                Object bus = applicationContext.getBean(names[0]);
                type.getMethod("publish", String.class, Object.class)
                        .invoke(bus, KafkaTopics.AUDIT, event);
                return true;
            }
        } catch (ClassNotFoundException ignored) {
            // runtime-event 不在 classpath → kafkaTemplate 兜底
        } catch (Exception e) {
            log.debug("[llm-gateway] EventBusService 反射发布失败，转 kafkaTemplate 兜底: {}", e.getMessage());
        }
        // 2) kafkaTemplate 兜底
        if (kafkaTemplateBean != null) {
            try {
                Method send = kafkaTemplateBean.getClass().getMethod("send", String.class, Object.class);
                send.invoke(kafkaTemplateBean, KafkaTopics.AUDIT, event);
                return true;
            } catch (Exception e) {
                log.debug("[llm-gateway] kafkaTemplate 兜底失败: {}", e.getMessage());
            }
        }
        // 3) 无通道 → WARN 留痕
        log.warn("[llm-gateway] EventBusService/KafkaTemplate 均不可用，审计仅记日志: action={}, result={}",
                event.get("action"), event.get("result"));
        return false;
    }

    // ═══════════════ 工具 ═══════════════

    /** security-engine 集成是否可用（类在 classpath 且 bean 可解析）— 供调用方区分「未配置」与「断连」 */
    public boolean isSecurityEngineAvailable() {
        return findBeanByClassName(SECRET_SERVICE_FQN) != null
                || findBeanByClassName(ENCRYPTION_SERVICE_FQN) != null;
    }

    /**
     * 裁决 base URL 供给链（F02-15 两态寻址单源）：显式覆写 →
     * {@code ServiceEndpointResolver.resolve("sysman", SERVICE)}（route=monolith 时 resolver
     * 内部自动回落 monolith 端口）→ 皆不可用返回空串（调用方默认 DENY）。
     */
    private String securityBaseUrl() {
        if (!securityBaseUrlOverride.isEmpty()) {
            return securityBaseUrlOverride;
        }
        return resolveViaEndpointResolver();
    }

    private String resolveViaEndpointResolver() {
        Object resolver = findBeanByClassName(ENDPOINT_RESOLVER_FQN);
        if (resolver == null) {
            return "";
        }
        try {
            Class<?> modeType = Class.forName(ROUTE_MODE_FQN);
            Object serviceMode = enumConstant(modeType, "SERVICE");
            if (serviceMode == null) {
                return "";
            }
            Object resolved = resolver.getClass()
                    .getMethod("resolve", String.class, modeType)
                    .invoke(resolver, SECURITY_ROUTE_OWNER, serviceMode);
            if (resolved instanceof Optional<?> opt && opt.orElse(null) instanceof String url) {
                return url == null ? "" : url.trim();
            }
            return "";
        } catch (Exception e) {
            log.warn("[llm-gateway] ServiceEndpointResolver 寻址 security base URL 失败（默认 DENY）: owner={}, reason={}",
                    SECURITY_ROUTE_OWNER, rootMessage(e));
            return "";
        }
    }

    private static Object enumConstant(Class<?> enumType, String name) {
        Object[] constants = enumType.getEnumConstants();
        if (constants == null) {
            return null;
        }
        for (Object c : constants) {
            if (name.equals(String.valueOf(c))) {
                return c;
            }
        }
        return null;
    }

    private Object findBeanByClassName(String fqn) {
        try {
            Class<?> type = Class.forName(fqn);
            String[] names = applicationContext.getBeanNamesForType(type);
            if (names.length == 0) {
                return null;
            }
            return applicationContext.getBean(names[0]);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            log.warn("[llm-gateway] security-engine bean [{}] 解析失败: {}", fqn, e.getMessage());
            return null;
        }
    }

    /** 引用掩码：保留 scheme 与前 4 位，其余以 *** 代替（凭据脱敏红线：报告/日志不出全量） */
    public static String maskRef(String ref) {
        if (ref == null || ref.isBlank()) return "<empty>";
        int schemeEnd = ref.indexOf(':');
        String prefix = schemeEnd > 0 ? ref.substring(0, schemeEnd + 1) : "";
        String body = schemeEnd > 0 ? ref.substring(schemeEnd + 1) : ref;
        String head = body.length() > 4 ? body.substring(0, 4) : body.substring(0, Math.min(2, body.length()));
        return prefix + head + "***";
    }

    private static String shortRef(String id) {
        return id == null ? "<null>" : (id.length() <= 8 ? id : id.substring(0, 8) + "***");
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur instanceof InvocationTargetException ite && ite.getTargetException() != null) {
            cur = ite.getTargetException();
        }
        return cur.getMessage() != null ? cur.getMessage() : cur.getClass().getSimpleName();
    }
}
