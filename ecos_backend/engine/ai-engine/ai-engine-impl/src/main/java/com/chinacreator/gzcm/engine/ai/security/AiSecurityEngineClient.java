package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.common.event.EventTypes;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * H10-T1 (PMO-74 N8) — ai-engine 对 security-engine 的统一访问层。
 *
 * <p>铁律 §2.4：RLS / CLS / ABAC 裁决 / 审计全部经 security-engine REST + Kafka
 * （{@link KafkaTopics#AUDIT}），AI 引擎内禁止重复实现安全逻辑；
 * security-engine 不可用时<b>默认 DENY</b>（不降级放行）。</p>
 *
 * <p>审计出口：优先 runtime 横切通道 {@code EventBusService}（本模块编译期无
 * runtime-event 依赖，按类名反射查找 bean，参照 sysman AuditAspect 先例），
 * 其次 {@code kafkaTemplate} 反射兜底（与 ontology SecurityEngineClient 同款）。</p>
 *
 * <p>RestTemplate 自构带超时（参照 PipelineSecurityService 先例），不依赖外部 bean，
 * 保证在 gateway / aiming / ai-engine-boot 各进程均可装配。</p>
 */
@Component
public class AiSecurityEngineClient {

    private static final Logger log = LoggerFactory.getLogger(AiSecurityEngineClient.class);

    private static final String EVENTBUS_IMPL_FQN = "com.chinacreator.gzcm.runtime.eventbus.EventBusService";

    private final String baseUrl;
    private final RestTemplate restTemplate;
    private final ApplicationContext applicationContext;

    @Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("kafkaTemplate")
    private Object kafkaTemplateBean;

    public AiSecurityEngineClient(
            @Value("${service.security.base-url:http://localhost:18081}") String baseUrl,
            @Value("${service.security.timeout-ms:2000}") int timeoutMs,
            ApplicationContext applicationContext) {
        this.baseUrl = baseUrl;
        this.applicationContext = applicationContext;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1000, timeoutMs));
        factory.setReadTimeout(Math.max(1000, timeoutMs));
        this.restTemplate = new RestTemplate(factory);
    }

    // ═══════════════ ABAC 裁决（默认 DENY） ═══════════════

    /**
     * POST /api/v1/security/policy-engine/evaluate。
     *
     * @return true=允许；security 不可用 / 响应异常 / 明确 DENY → false（默认 DENY）
     */
    @SuppressWarnings("unchecked")
    public boolean evaluate(String userId, String tenantId, String action, Map<String, Object> attrs) {
        try {
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
            Map<String, Object> resp = postJson("/api/v1/security/policy-engine/evaluate", body);
            if (resp == null || !(resp.get("data") instanceof Map)) return false;
            Object result = ((Map<String, Object>) resp.get("data")).get("result");
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("AiSecurityEngineClient.evaluate DENY (security 不可用/裁决失败): action={} reason={}",
                    action, e.getMessage());
            return false;
        }
    }

    // ═══════════════ RLS（默认 DENY "1=0"） ═══════════════

    /**
     * POST /api/v1/security/rls/apply — 行级安全 WHERE 片段。
     *
     * @return whereClause；无策略=""；security 不可用/异常/DENY → "1=0"
     */
    @SuppressWarnings("unchecked")
    public String applyRls(String userId, String tenantId, String entityType,
                           String tableName, Map<String, Object> filters) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tableName", tableName);
            body.put("userId", userId == null ? "anonymous" : userId);
            body.put("entityType", entityType);
            if (tenantId != null) body.put("tenantId", tenantId);
            if (filters != null && !filters.isEmpty()) body.put("filters", filters);
            Map<String, Object> resp = postJson("/api/v1/security/rls/apply", body);
            if (resp == null || !(resp.get("data") instanceof Map)) return "1=0";
            Object whereObj = ((Map<String, Object>) resp.get("data")).get("whereClause");
            return whereObj == null ? "" : whereObj.toString();
        } catch (Exception e) {
            log.warn("AiSecurityEngineClient.applyRls DENY: entityType={} reason={}", entityType, e.getMessage());
            return "1=0";
        }
    }

    // ═══════════════ CLS（默认 DENY 空集） ═══════════════

    /**
     * POST /api/v1/security/cls/columns — 列级安全可见列。
     *
     * @return 允许列；security 不可用/异常 → List.of()（DENY-all 语义）
     */
    @SuppressWarnings("unchecked")
    public List<String> allowedColumns(String userId, String tenantId, String entityType, List<String> allColumns) {
        if (allColumns == null || allColumns.isEmpty()) return List.of();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tableName", entityType);
            body.put("userId", userId == null ? "anonymous" : userId);
            if (tenantId != null) body.put("tenantId", tenantId);
            body.put("allColumns", allColumns);
            Map<String, Object> resp = postJson("/api/v1/security/cls/columns", body);
            if (resp == null || !(resp.get("data") instanceof Map)) return List.of();
            Object allowedObj = ((Map<String, Object>) resp.get("data")).get("allowedColumns");
            if (allowedObj instanceof List<?> list) {
                List<String> result = new ArrayList<>(list.size());
                for (Object o : list) result.add(String.valueOf(o));
                return result;
            }
            return List.of();
        } catch (Exception e) {
            log.warn("AiSecurityEngineClient.allowedColumns DENY: entityType={} reason={}",
                    entityType, e.getMessage());
            return List.of();
        }
    }

    // ═══════════════ 裁决载荷方向：mask（F06-02 obligations） ═══════════════

    /**
     * F06-02 脱敏 obligations —— 调 security-engine 方向 {@code POST /api/v1/security/masking/apply}，
     * ai-engine 只传值清单与规则清单、<b>不实现任何脱敏算法</b>（分册 01 SEC-02 契约；§2.4-3 铁律）。
     *
     * <p><b>失败语义与 {@link #allowedColumns} 不同</b>：本方法一旦被调（即 security 已裁决
     * 出 mask obligation），调用方依赖"要么获得等长脱敏结果、要么判定失败丢弃整条"——
     * 因此返回空 List 会与"无可脱敏"混淆；此处以 <b>{@link EngineUnavailableException}</b>
     * 显式表达"security 不可用/超时/响应不可解析/明确非成功"，让 applier 走 §2.4-6 丢弃整条。
     * 正常路径始终返回与入参等长的 List。</p>
     *
     * @throws EngineUnavailableException 响应含异常、非 2xx、data 非 Map、results 缺失/不等长
     */
    @SuppressWarnings("unchecked")
    public List<String> applyMasking(List<String> data, List<String> rules) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("data", data);
            body.put("rules", rules);
            Map<String, Object> resp = postJson("/api/v1/security/masking/apply", body);
            // ApiResponse 契约：成功 = code==0（该信封无单独 success 布尔，见 common-api ApiResponse）
            Object codeObj = resp == null ? null : resp.get("code");
            if (resp == null || !(codeObj instanceof Number n) || n.intValue() != 0) {
                throw new EngineUnavailableException("masking/apply 非成功响应 (code!=0): " + resp);
            }
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> dataMap)) {
                throw new EngineUnavailableException("masking/apply data 段不可解析");
            }
            Object resultsObj = dataMap.get("results");
            if (!(resultsObj instanceof List<?> raw)) {
                throw new EngineUnavailableException("masking/apply results 缺失");
            }
            if (raw.size() != data.size()) {
                throw new EngineUnavailableException(
                        "masking/apply 结果与请求长度不一致（拒收）: got=" + raw.size()
                                + " expected=" + data.size());
            }
            List<String> out = new ArrayList<>(raw.size());
            for (Object item : raw) {
                if (!(item instanceof Map<?, ?> m) || !(m.get("masked") instanceof String s)) {
                    throw new EngineUnavailableException("masking/apply 单项 masked 缺失");
                }
                out.add(s);
            }
            return out;
        } catch (EngineUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AiSecurityEngineClient.applyMasking 失败 (fail-closed, SEC-02): reason={}",
                    e.getMessage());
            throw new EngineUnavailableException("masking/apply 调用异常: " + e.getMessage(), e);
        }
    }

    /**
     * F06-04 <b>文本内容审核咽喉</b>（输入/输出护栏改经 security-engine）。
     *
     * <p>铁律 §2.4-3/6/7 + 分册06 F06-04：护栏的两个拦截点（用户输入、模型输出）一律调
     * security-engine 的文本审核端点，<b>引擎内禁本地正则充当放行依据</b>（X-16 收口）。
     * 端点 = {@code POST /api/v1/security/guardrail/screen}（契约归分册 01 SEC，见 J-6 接缝登记）。</p>
     *
     * <p><b>失败语义（红线，fail-closed）</b>：security 不可用 / 超时 / 响应非成功 /
     * {@code data.passed} 非显式 <b>true</b> / 响应不可解析 —— 一律上抛
     * {@link EngineUnavailableException}。此处<b>从不返回一个"放行"信号</b>：
     * 任何无法被 security 明确确认安全的文本都视为不安全，让调用方回 GUARDRAIL_FAIL_CLOSED
     * （对应设计 §536「护栏不可用 → AI 操作已暂停，禁降级为本地正则放行」）。
     * 正常路径：security 明确回 {@code passed=true} 时静默返回。</p>
     *
     * @param scope  审核面（"input"=用户输入前置 / "output"=模型输出后置），进载荷供 security 侧策略路由
     * @param text   待审核文本（经该端点单通道送 security——审核即脱敏/消费方，与 applyMasking 承 data 同款；
     *               <b>不落本地、不外传其它通道</b>；审计 detail 另见 F06-03 §299 禁带原文）
     * @param userId 主体（token 上下文；禁 LLM/请求体自报，PMO-74 H9-T1）
     * @throws EngineUnavailableException security 不可用 / 响应不可解析 / 未明确放行
     */
    @SuppressWarnings("unchecked")
    public void screenOrThrow(String scope, String text, String userId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scope", scope == null ? "input" : scope);
        body.put("userId", userId == null ? "anonymous" : userId);
        // 文本载体键 security 侧以 "text" 约定（分册01 SEC 契约扩展点，J-6）
        body.put("text", text == null ? "" : text);
        Map<String, Object> resp;
        try {
            resp = postJson("/api/v1/security/guardrail/screen", body);
        } catch (Exception e) {
            throw new EngineUnavailableException("guardrail/screen 调用异常（fail-closed）: " + e.getMessage(), e);
        }
        Object codeObj = resp == null ? null : resp.get("code");
        if (resp == null || !(codeObj instanceof Number n) || n.intValue() != 0) {
            throw new EngineUnavailableException(
                    "guardrail/screen 非成功响应(code!=0)（fail-closed）: " + resp);
        }
        Object dataObj = resp.get("data");
        if (!(dataObj instanceof Map<?, ?> dataMap)) {
            throw new EngineUnavailableException("guardrail/screen data 段不可解析（fail-closed）");
        }
        // 只有 security 明确 passed=true 才放行；neg/缺失/其它一律 fail-closed
        if (!Boolean.TRUE.equals(dataMap.get("passed"))) {
            throw new EngineUnavailableException(
                    "guardrail/screen 未明确放行（passed!=true，fail-closed，§2.4-6）");
        }
    }

    /**
     * F06-02 契约：security-engine 方向端点不可用 / 响应不可解析上抛的本客户端异常。
     * applier 捕获该异常即执行"整条丢弃 + FAIL_CLOSED"（§2.4-6），不降级、不重试。
     */
    public static final class EngineUnavailableException extends RuntimeException {
        public EngineUnavailableException(String m) { super(m); }
        public EngineUnavailableException(String m, Throwable c) { super(m, c); }
    }

    // ═══════════════ 审计 — Kafka ecos.audit（runtime 横切出口） ═══════════════

    public void audit(String userId, String action, String resource, Object result) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventId", UUID.randomUUID().toString());
            event.put("timestamp", Instant.now().toString());
            event.put("module", "ai-engine");
            event.put("userId", userId == null ? "anonymous" : userId);
            event.put("action", action);
            event.put("resource", resource);
            event.put("result", result == null ? "OK" : String.valueOf(result));
            doPublishAudit(event);
        } catch (Exception e) {
            log.warn("AiSecurityEngineClient.audit failed: action={} reason={}", action, e.getMessage());
        }
    }

    /**
     * F06-03 红线咽喉 GUARDRAIL_EVAL 审计发布（<b>与 {@link #audit} 严格分离</b>）。
     *
     * <p>铁律 §2.4-5 + 分册 06 F06-03 §297：咽喉 {@code AgentToolPolicyGate.adjudicate}
     * 每次裁决必发一条 {@code eventType=GUARDRAIL_EVAL} 事件到 Kafka {@code ecos.audit}；
     * <b>发布失败 = 裁决整体失败</b>（护栏审计属红线，禁"尽力而为"失败沉默）。该路径：
     * <ol>
     *   <li>优先走 runtime-event {@code EventBusService}（类名反射查找，避免编译期依赖 runtime-event）；</li>
     *   <li>runtime-event 未装配 → 反射 fallback 到 {@code kafkaTemplate.send}（与 ontology 侧先例同款）；</li>
     *   <li>两通道均不可用 / 反射 publish 抛异常 → 上抛
     *       {@link EngineUnavailableException}（本咽喉 catch 后转 DENY）。</li>
     * </ol>
     *
     * <p><b>载荷纪律（F06-03 §299 / X-58）</b>：event {@code detail} 内禁带工具入参原文 /
     * SQL 原文 / 结果原文；只准 tool 名 + decision 三态 + policyId + latencyMs +
     * obligations 计数（防审计库变成新的敏感面）。用户主体从 token 上下文取（PMO-74 H9-T1）。</p>
     *
     * @param detail 已构造的摘要段（{@code tool}/{@code decision}/{@code policyId}/
     *               {@code latencyMs}/{@code obligationsCount}），本方法只办 eventId/timestamp/channel 段
     * @throws EngineUnavailableException 通道不可用 或 EventBus/Kafka 发布抛出
     */
    public void publishGuardrailEventOrThrow(String userId, Map<String, Object> detail) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", EventTypes.Guardrail.GUARDRAIL_EVAL);
        event.put("timestamp", Instant.now().toString());
        event.put("module", "ai-engine");
        // channel 恒 AGENT（本裁判决定位；非 LLM 自报）
        event.put("channel", "AGENT");
        event.put("userId", userId == null ? "anonymous" : userId);
        event.put("moduleTag", "GUARDRAIL");
        // detail 由调用方按摘要纪律构造，本方法不再读原始入参
        event.put("detail", detail);
        doPublishAuditOrThrow(event);
    }

    /** 尽力而为发布（供 {@link #audit} 与历史调用点使用；失败仅 warn） */
    private void doPublishAudit(Map<String, Object> event) {
        ChannelResult r = selectAndPublish(event);
        if (r == ChannelResult.NONE) {
            log.warn("AiSecurityEngineClient.audit: EventBusService/KafkaTemplate 均不可用，审计仅记日志: action={}",
                    event.get("action"));
        }
    }

    /** 红线发布（F06-03）：任一通道抛/无通道 → 上抛 {@link EngineUnavailableException} */
    private void doPublishAuditOrThrow(Map<String, Object> event) {
        ChannelResult r = selectAndPublishOrThrow(event);
        if (r == ChannelResult.NONE) {
            throw new EngineUnavailableException(
                    "GUARDRAIL_EVAL 审计无可发布通道（EventBusService 未装配 且 kafkaTemplate 未注入）");
        }
    }

    private ChannelResult selectAndPublish(Map<String, Object> event) {
        // 1) runtime-event EventBusService 横切通道（类名反射查找，避免编译期依赖）
        try {
            Class<?> type = Class.forName(EVENTBUS_IMPL_FQN);
            String[] names = applicationContext == null ? new String[0] : applicationContext.getBeanNamesForType(type);
            if (names.length > 0) {
                Object bus = applicationContext.getBean(names[0]);
                type.getMethod("publish", String.class, Object.class).invoke(bus, KafkaTopics.AUDIT, event);
                return ChannelResult.EVENT_BUS;
            }
        } catch (ClassNotFoundException ignored) {
            // runtime-event 不在 classpath → 走 kafkaTemplate 兜底
        } catch (Exception e) {
            log.debug("AiSecurityEngineClient: EventBusService 反射发布失败，转 kafkaTemplate 兜底: {}", e.getMessage());
        }
        // 2) kafkaTemplate 反射兜底（与 ontology SecurityEngineClient.audit 同款）
        if (kafkaTemplateBean != null) {
            try {
                Method send = kafkaTemplateBean.getClass().getMethod("send", String.class, Object.class);
                send.invoke(kafkaTemplateBean, KafkaTopics.AUDIT, event);
                return ChannelResult.KAFKA_TEMPLATE;
            } catch (Exception e) {
                log.debug("AiSecurityEngineClient: kafkaTemplate 兜底失败: {}", e.getMessage());
            }
        }
        return ChannelResult.NONE;
    }

    private ChannelResult selectAndPublishOrThrow(Map<String, Object> event) throws EngineUnavailableException {
        // 1) runtime-event EventBusService —— 反射失败/bean 缺失 → 上抛（红线，禁降级）
        try {
            Class<?> type = Class.forName(EVENTBUS_IMPL_FQN);
            String[] names = applicationContext == null ? new String[0] : applicationContext.getBeanNamesForType(type);
            if (names.length > 0) {
                Object bus = applicationContext.getBean(names[0]);
                try {
                    type.getMethod("publish", String.class, Object.class).invoke(bus, KafkaTopics.AUDIT, event);
                    return ChannelResult.EVENT_BUS;
                } catch (java.lang.reflect.InvocationTargetException ite) {
                    throw new EngineUnavailableException(
                            "GUARDRAIL_EVAL EventBus.publish 上抛: " + describe(ite.getTargetException()), ite);
                }
            }
        } catch (ClassNotFoundException ignored) {
            // runtime-event 不在 classpath → 走 kafkaTemplate 兜底
        } catch (EngineUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new EngineUnavailableException(
                    "GUARDRAIL_EVAL EventBusService 反射查找/取 bean 失败: " + describe(e), e);
        }
        // 2) kafkaTemplate 反射兜底 —— 未注入 → 直接抛；抛出/wrapping → 上抛
        if (kafkaTemplateBean == null) {
            throw new EngineUnavailableException(
                    "GUARDRAIL_EVAL kafkaTemplate 未注入（runtime-event 亦不可用），审计发布失败");
        }
        try {
            Method send = kafkaTemplateBean.getClass().getMethod("send", String.class, Object.class);
            try {
                send.invoke(kafkaTemplateBean, KafkaTopics.AUDIT, event);
                return ChannelResult.KAFKA_TEMPLATE;
            } catch (java.lang.reflect.InvocationTargetException ite) {
                throw new EngineUnavailableException(
                        "GUARDRAIL_EVAL kafkaTemplate.send 上抛: " + describe(ite.getTargetException()), ite);
            }
        } catch (EngineUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new EngineUnavailableException(
                    "GUARDRAIL_EVAL kafkaTemplate 反射失败: " + describe(e), e);
        }
    }

    private static String describe(Throwable t) {
        return t == null ? "unknown" : (t.getClass().getSimpleName() + ":" + t.getMessage());
    }

    private enum ChannelResult { EVENT_BUS, KAFKA_TEMPLATE, NONE }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> postJson(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + path, entity, Map.class);
        return resp == null ? null : resp.getBody();
    }
}
