package com.chinacreator.gzcm.engine.kb.security;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract;
import com.chinacreator.gzcm.common.security.header.ServiceContext;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 知识导航 (kb-nav) ABAC 客户端 — 走 security-engine REST（与 ontology-engine 同款范式）。
 *
 * <p><b>来源</b>: PMO-A A4 ｜ <b>日期</b>: 2026-09-23 ｜ <b>责任人</b>: fullstack</p>
 *
 * <p><b>继承铁律</b>：架构铁律 §2.4：
 * <ul>
 *   <li>所有写操作 ABAC 裁决（{@code POST /api/v1/security/policy-engine/evaluate}）</li>
 *   <li>不可用与拒绝<b>分态</b>（#142/B4-3e-12，差距 D-B4-44）：{@link #evaluatePolicy} 原样
 *       返回契约三态，绝不降级放行 —— 策略 DENY 由调用方转 403，
 *       {@code UNEVALUATED}（护栏没说话）转 503 {@link KbErrorCodes#SECURITY_UNAVAILABLE}，
 *       两者不得相互冒充（§4.7.2「不可用 ≠ 无权限」）。</li>
 *   <li>审计事件经 runtime 底座 {@link EventBusService} 发 {@link KafkaTopics#AUDIT}
 *       topic（F04-15 收敛：不再依赖 KafkaTemplate 反射调用）。</li>
 * </ul>
 *
 * <p><b>F04-15 审计 fail-closed</b>：
 * {@link #audit(String, Object)} 走 EventBusService：
 * <ol>
 *   <li>eventBus 缺失（null）或 {@code isKafkaAvailable()==false} → 先做 best-effort
 *       本地兜底（结构化 log 写，等价于让审计证据留在应用日志里）；</li>
 *   <li>本地兜底写也失败 → 抛
 *       {@link KbErrorCodes#EVENT_BASE_UNAVAILABLE}（ECOS-KB-022） —— 审计不可达时
 *       绝不静默吞掉，调用方应理解成「写不可归因 → 视为整次写失败」。</li>
 * </ol>
 * 调用方 {@code NavTaxonServiceImpl.auditNotFound(...)} 捕获该异常后按「写已落库、
 * 审计失败」降级 log.error，不再阻塞业务主链（写本身已经成功）。
 *
 * <p>不直接负责 RLS/CLS/脱敏（kb-nav 是目录/标签维度，无敏感列，不涉及脱敏）。
 * 本类仅承载「操作授权」+「审计」两个动作。</p>
 */
@Component
public class KnowledgeNavSecurityEngineClient {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeNavSecurityEngineClient.class);

    private final String baseUrl;
    private final int timeoutMs;
    /** 可选 — 无 security-engine 环境（如纯 kb dev）保持 null，evaluatePolicy 归一为 UNEVALUATED（不可用） */
    private final RestTemplate serviceRestTemplate;
    /** 可选 — EventBusService 底座（F04-15 收敛，替代 KafkaTemplate 反射）；缺失时 audit fail-closed */
    private final EventBusService eventBus;

    public KnowledgeNavSecurityEngineClient(
            @Value("${service.security.base-url:http://localhost:18081}") String baseUrl,
            @Value("${service.security.timeout-ms:5000}") int timeoutMs,
            @Autowired(required = false) RestTemplate serviceRestTemplate,
            @Autowired(required = false) EventBusService eventBus) {
        this.baseUrl = baseUrl;
        this.timeoutMs = timeoutMs;
        this.serviceRestTemplate = serviceRestTemplate;
        this.eventBus = eventBus;
    }

    /**
     * ABAC 裁决 — 调 security-engine 的 OPA evaluate，返回<b>契约三态</b>，不在本类做布尔结论。
     *
     * <p><b>#142/B4-3e-12 分态（差距 D-B4-44）</b>：收口前本方法用
     * {@link AbacEvaluateResponseContract#isExplicitAllow} 直接返回 {@code boolean}，
     * 而该读数对 {@code DENY} 与 {@code UNEVALUATED} 一律给 {@code false}。于是
     * ①{@code RestTemplate} 未装配 ②空信封／security 侧 5xx／传输解析异常
     * ③HTTP 200 但应答无可识别裁决（缺 {@code allow} 键／{@code source=fallback}／
     * 带 {@code opaError}／{@code policyState} 未评估）
     * 这三种"护栏没说话"与真实策略 DENY 同值，调用方 {@code checkAbac} 把它们统一转成
     * 403 —— 违 §4.7.2「不可用 ≠ 无权限」，后果不是保守而是把链路缺陷伪装成业务结论：
     * 上层拿到 403 就不再报依赖故障，运维面永远看不到 security 侧的问题。
     * 现三态原样上抛，由调用方分态落面（DENY→403／UNEVALUATED→503 ECOS-KB-020），
     * 与 {@code WorkspaceSecurityEngineClient#evaluate}（D-B4-41 腿）同一口径。</p>
     *
     * <p>F04-16：subject.userId / tenantId / role 改为从
     * {@link ServiceContext#current()} + {@link TenantContextHolder} 读取真实身份；
     * 缺失（如单测直接调用）时回退 "anonymous" / "default"（保留默认值以便裁决端
     * 连通性不被硬拆）。role 取 ServiceContext.roles 首值，逗号串。</p>
     *
     * @param action 业务动作标识（如 {@code kb.category.create}）
     * @param attrs  附加属性（可空；会 flat 合并到 input）
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
            if (attrs != null) {
                input.putAll(attrs);
            }
            input.put("subject", Map.of(
                    "userId", currentUserIdOrAnonymous(),
                    "tenantId", currentTenantIdOrDefault(),
                    "role", currentRoleOrDefault()));
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
        log.warn("KNavSecurityEngineClient.evaluatePolicy UNEVALUATED (不可用，非策略 DENY): "
                + "action={} reason={}", action, reason);
        return AbacEvaluateResponseContract.Verdict.UNEVALUATED;
    }

    /**
     * 审计事件 — 经 {@link EventBusService} 发 {@link KafkaTopics#AUDIT}（F04-15）。
     *
     * <p><b>fail-closed 语义</b>：
     * <ul>
     *   <li>{@code eventBus == null}（Spring 未装配）→ 本地 log 兜底后抛
     *       {@link KbErrorCodes#EVENT_BASE_UNAVAILABLE}（ECOS-KB-022）。;</li>
     *   <li>{@code publish(...)} 抛异常 → 本地 log 兜底后抛
     *       {@link KbErrorCodes#EVENT_BASE_UNAVAILABLE}。</li>
     *   <li>{@code isKafkaAvailable()==false} 但是内存总线实现有效 → 仍走 publish
     *       （内存成功即视为已发布，符合 EventBusService 契约的内存降级）。</li>
     * </ul>
     * </p>
     *
     * @param action 业务动作（如 {@code kb.category.update}）
     * @param attr   关键属性（如 entityId / userId）
     * @throws KbErrorCodes.DomainException 当 eventBus 缺失或 publish 抛异常时
     */
    public void audit(String action, Object attr) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("timestamp", System.currentTimeMillis());
        event.put("userId", currentUserIdOrAnonymous());
        event.put("tenantId", currentTenantIdOrDefault());
        event.put("action", action);
        event.put("resource", "kb-nav");
        event.put("attr", attr == null ? Map.of() : attr);

        if (eventBus == null) {
            // eventBus 未装配 — 本地 log 兜底 + fail-closed
            localFallbackWrite(action, event);
            throw KbErrorCodes.EVENT_BASE_UNAVAILABLE.exception(
                    "audit 总线不可用: eventBus=null action=" + action);
        }
        boolean kafka = eventBus.isKafkaAvailable();
        try {
            eventBus.publish(KafkaTopics.AUDIT, event);
        } catch (Exception ex) {
            localFallbackWrite(action, event);
            throw KbErrorCodes.EVENT_BASE_UNAVAILABLE.exception(
                    "audit publish failed (eventBus.publish threw): action=" + action
                            + " kafkaAvailable=" + kafka
                            + " reason=" + ex.getMessage());
        }
        if (!kafka) {
            // 内存总线降级 — log 提示便于运维感知（不阻塞）
            log.debug("KNavSecurityEngineClient.audit via memory bus: action={} eventId={}",
                    action, event.get("eventId"));
        }
    }

    /**
     * 本地兜底 — 结构化 log 写（仅证据落地审计链；不阻塞主进程）。
     *
     * @return true=写成功；false=写失败（供调用方判定是否 fail-closed 抛码）
     */
    private boolean localFallbackWrite(String action, Map<String, Object> event) {
        try {
            log.warn("[AUDIT-LOCAL-FALLBACK] topic={} action={} event={}",
                    KafkaTopics.AUDIT, action, event);
            return true;
        } catch (Exception ex) {
            log.warn("KNavSecurityEngineClient local fallback log failed: action={} reason={}",
                    action, ex.getMessage());
            return false;
        }
    }

    // ── helpers ─────────────────────────────────────────────

    /** 从 {@link ServiceContext} 取真实 userId；缺失时回退 "anonymous"。 */
    private String currentUserIdOrAnonymous() {
        try {
            ServiceContext ctx = ServiceContext.current();
            if (ctx != null && ctx.getUserId() != null && !ctx.getUserId().isBlank()) {
                return ctx.getUserId();
            }
            // 兼容网关直连场景下 SecurityContext
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext()
                            .getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getName() != null
                    && !auth.getName().isBlank()) {
                return auth.getName();
            }
        } catch (Exception ignored) {
            // 客户端解析失败返回 anonymous，不抛
        }
        return "anonymous";
    }

    /** 从 {@link TenantContextHolder} 取真实 tenantId；缺失时回退 "default"（并留 log 以便发现）。 */
    private String currentTenantIdOrDefault() {
        try {
            String t = TenantContextHolder.getTenantId();
            if (t != null && !t.isBlank()) {
                return t;
            }
        } catch (Exception ignored) { }
        return "default";
    }

    /** 从 {@link ServiceContext} 取首角色；缺失时回退 "user"。 */
    private String currentRoleOrDefault() {
        try {
            ServiceContext ctx = ServiceContext.current();
            if (ctx != null) {
                String roles = ctx.getRoles();
                if (roles != null && !roles.isBlank()) {
                    int comma = roles.indexOf(',');
                    String first = comma >= 0 ? roles.substring(0, comma) : roles;
                    first = first.trim();
                    if (!first.isEmpty()) {
                        return first;
                    }
                }
            }
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext()
                            .getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getAuthorities() != null) {
                for (org.springframework.security.core.GrantedAuthority a : auth.getAuthorities()) {
                    if (a != null && a.getAuthority() != null && !a.getAuthority().isBlank()) {
                        return a.getAuthority();
                    }
                }
            }
        } catch (Exception ignored) { }
        return "user";
    }

    private Map<String, Object> postJson(String path, Map<String, Object> body) {
        if (serviceRestTemplate == null) {
            return null;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> resp = serviceRestTemplate.postForEntity(baseUrl + path, entity, Map.class);
        return resp == null ? null : resp.getBody();
    }
}
