package com.chinacreator.gzcm.workspace.security;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.security.cls.ColumnSurfaceContract;
import com.chinacreator.gzcm.common.security.header.HeaderAuthInterceptor;
import com.chinacreator.gzcm.common.security.header.ServiceContext;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.workspace.exception.SecurityEngineUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * H10-T1 (PMO-74 N7/N16) — workspace 对 security-engine 的统一访问层。
 *
 * <p>铁律 §2.4：RLS / CLS / ABAC 裁决 / 审计一律走 security-engine REST + Kafka，
 * workspace 内不再自建 ABAC 策略解析（原 {@link AbacQueryFilter} 直查
 * td_abac_policy + {@code ${userId}} 字符串拼接的二阶注入路径已废除）；
 * security-engine <b>拒绝</b>时默认 DENY：RLS → "1=0"，CLS → 空集，ABAC → false。</p>
 *
 * <p><b>不可用与拒绝分态（详设-01 §4.5.4 六×四降级矩阵 / §4.7.1 三通道全 DENY 门禁，
 * 验收载体 {@code SecurityDownAllChannelDenyTest#page} / {@code #export}）</b>：
 * 连不上 / 读超时 / security 侧 5xx 属<b>可用性故障</b>而非策略裁决，
 * 由 {@link #postJson} 单点转 {@link SecurityEngineUnavailableException}
 * （HTTP 503 / ECOS-SEC-501）上抛，禁再折叠成 {@code "1=0"} / 空列集 / {@code false} ——
 * 旧形态把"安全引擎故障"渲染成"你没有数据"（200 + 空列表），
 * 违从零实施版 line 2602「503 DENY 且禁渲染空列表冒充无数据」。
 * 4xx（含默认 DENY 的 403）与响应缺段不可解析仍按既有兜底走 DENY 语义，分态不外溢。</p>
 *
 * <p>卷07 F07-17 底座收口（对齐 ai-engine W89 先例）：
 * <ul>
 *   <li>REST 出口注入 {@code WorkspaceRestClientConfig#workspaceSecurityEngineRest}
 *       共享 Bean（base-url 经 runtime-access ServiceEndpointResolver 两态寻址，
 *       禁自建 RestTemplate / 硬编码 localhost）；</li>
 *   <li>审计出口编译期注入 runtime-event {@link EventBusService}
 *       （原 Class.forName 反射 + kafkaTemplate 反射兜底已删除，铁律 §2.5-7
 *       禁业务侧自建 KafkaTemplate；Bus 未装配 → WARN 留痕，不再兜底）。</li>
 * </ul></p>
 */
@Component
public class WorkspaceSecurityEngineClient {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceSecurityEngineClient.class);

    private final RestTemplate restTemplate;
    private final ObjectProvider<EventBusService> eventBusProvider;

    public WorkspaceSecurityEngineClient(
            @Qualifier("workspaceSecurityEngineRest") RestTemplate restTemplate,
            ObjectProvider<EventBusService> eventBusProvider) {
        this.restTemplate = restTemplate;
        this.eventBusProvider = eventBusProvider;
    }

    // ═══════════════ RLS（默认 DENY "1=0"） ═══════════════

    /**
     * POST /api/v1/security/rls/apply —— 只取 WHERE 片段的兼容出口（签名不变，只增语义）。
     *
     * <p><b>B4-3c-2 起的口径订正（原为 fail-open）</b>：本方法此前在应答缺
     * {@code whereClause} 时返回<b>空串</b>，调用方把空串理解成"无策略所以不过滤"——
     * 而 {@code whereClause} 在生产者侧从来没发过，于是页面通道的行级安全恒等于关闭
     * （差距 D-B4-17）。收口后缺字段/空白一律是 {@link RlsFilter} 的拒绝形态 ⇒
     * 这里返回 {@code "1=0"}。</p>
     *
     * <p><b>需要真正下推行过滤的调用方必须改用 {@link #applyRlsFilter}</b>：
     * 片段是带 {@code ?} 的 JDBC 形态，只取片段不绑参数等于把半条 SQL 交给数据库。</p>
     *
     * @return WHERE 片段（含 {@code ?} 占位，禁单独使用）；恒真＝{@code "1=1"}；
     *         security 明确 DENY / 无策略 / 应答漂移 = {@code "1=0"}（本方法永不返回空串）
     * @throws SecurityEngineUnavailableException security 进程不可用（连不上/超时/5xx），
     *         由 {@link #postJson} 单点转 503，禁冒充"无数据"
     */
    public String applyRls(String entityType, String tableName, Map<String, Object> filters) {
        return applyRlsFilter(entityType, tableName, filters).clause();
    }

    /**
     * 行级裁决的<b>完整执行契约</b>出口（#142/B4-3c-2 只增）：
     * {@code whereClause} 片段 + 同序 {@code whereParams}，二者由 {@link RlsFilter}
     * 成对承载并做绑定前不变量校验（缺片段/{@code ?} 数与参数数不符/参数含 null/
     * 占位符残留 ⇒ 一律拒绝态）。
     *
     * @return 永不为 {@code null}；拒绝态见 {@link RlsFilter#denied()}
     * @throws SecurityEngineUnavailableException security 进程不可用（503，独立态）
     */
    @SuppressWarnings("unchecked")
    public RlsFilter applyRlsFilter(String entityType, String tableName, Map<String, Object> filters) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tableName", tableName);
            body.put("userId", currentUserId());
            body.put("entityType", entityType);
            if (filters != null && !filters.isEmpty()) body.put("filters", filters);
            Map<String, Object> resp = postJson("/api/v1/security/rls/apply", body);
            Object data = resp == null ? null : resp.get("data");
            if (!(data instanceof Map)) {
                return RlsFilter.deny(RlsFilter.DRIFT_ABSENT + " entity=" + entityType
                        + "（应答无 data 段）", true, List.of());
            }
            RlsFilter filter = RlsFilter.fromData((Map<String, Object>) data, entityType);
            if (filter.denied()) {
                log.warn("WorkspaceSecurityEngineClient.applyRls DENY: entityType={} reason={}",
                        entityType, filter.denyReason());
            }
            return filter;
        } catch (SecurityEngineUnavailableException e) {
            throw e; // 可用性故障保持独立态上抛（§4.5.4 分态）
        } catch (Exception e) {
            log.warn("WorkspaceSecurityEngineClient.applyRls DENY: entityType={} reason={}",
                    entityType, e.getMessage());
            return RlsFilter.deny("RLS_APPLY_EXCEPTION(" + e.getMessage() + ")", true, List.of());
        }
    }

    // ═══════════════ CLS（默认 DENY 空集） ═══════════════

    /**
     * POST /api/v1/security/cls/columns。
     *
     * <p><b>#142/B4-3d-1 语义订正（差距 D-B4-25）</b>：本方法此前只读
     * {@code data.allowedColumns}，而 CLS 生产者写的一直是 {@code visibleColumns}
     * （仓根实证：生产侧零处写 {@code allowedColumns}）——键面漂移使页面通道的列裁剪
     * <b>结构性恒 DENY</b>：任何主体任何资产都拿不到可读列。现在读数委托
     * {@link ColumnSurfaceContract#read}（单源读取面，与 ontology/ai 同一份逻辑），
     * 并区分三态：非空可读集＝可裁剪；空集＝策略明确拒绝；两键皆不可读＝形态漂移
     * （带理由落日志，禁静默空集冒充"策略裁决"）。</p>
     *
     * <p><b>返回值语义不变（只增不改签名）</b>：拒绝态与漂移态都返回空表。差异在于
     * <b>真实可读集现在真的能返回来</b>，以及漂移态现在可解释。</p>
     *
     * <p><b>#142/B4-3d-2 订正（D-B4-15 收口）</b>：原文记载"调用方
     * {@code AbacQueryFilter#filterColumns} 按空表置空行集"已不成立 —— 页面通道的列裁剪
     * 已合流 {@code /decision}（{@code purpose=page}），不再经本方法，workspace 侧列面
     * 因此只剩一条裁决腿。本方法保留：①作为 {@code /cls/columns} 端点的消费者契约面
     * （端点未退役，退役判定见差距文档 B4-3d 余项）；②作为
     * {@code SecurityDownAllChannelDenyTest} 反向对照的可用性探针载体。
     * <b>禁</b>新增业务调用方经本方法取列面 —— 那等于恢复第二条腿。</p>
     *
     * @return 允许列；security 明确 DENY / 应答键面漂移 → List.of()
     * @throws SecurityEngineUnavailableException security 进程不可用（连不上/超时/5xx）
     */
    public List<String> allowedColumns(String entityType, List<String> allColumns) {
        if (allColumns == null || allColumns.isEmpty()) return List.of();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tableName", entityType);
            body.put("userId", currentUserId());
            body.put("allColumns", allColumns);
            Map<String, Object> resp = postJson("/api/v1/security/cls/columns", body);
            ColumnSurfaceContract.Reading reading =
                    ColumnSurfaceContract.read(resp == null ? null : resp.get("data"));
            if (reading.drifted()) {
                log.warn("WorkspaceSecurityEngineClient.allowedColumns 列面漂移 → DENY: "
                        + "entityType={} reason={}", entityType, reading.reason());
                return List.of();
            }
            if (reading.visible().isEmpty()) {
                log.warn("WorkspaceSecurityEngineClient.allowedColumns 策略拒绝全部列(ECOS-SEC-402): "
                        + "entityType={} blocked={}", entityType, reading.blocked().size());
            } else if (reading.legacyKeyUsed()) {
                log.warn("WorkspaceSecurityEngineClient.allowedColumns 走 legacy 别名键: "
                        + "entityType={} size={}", entityType, reading.visible().size());
            }
            return reading.visible();
        } catch (SecurityEngineUnavailableException e) {
            throw e; // 可用性故障不得冒充"无可见列"（§4.5.4 分态）
        } catch (Exception e) {
            log.warn("WorkspaceSecurityEngineClient.allowedColumns DENY: entityType={} reason={}",
                    entityType, e.getMessage());
            return List.of();
        }
    }

    // ═══════════════ 三通道唯一裁决入口（详设-01 C.1 [3] / F01-02） ═══════════════

    /**
     * POST /api/v1/security/decision —— 三通道唯一裁决入口的 workspace 侧接线
     * （#142/B4-3b；此前 workspace 只有 {@code /rls/apply}＋{@code /cls/columns}＋
     * {@code /policy-engine/evaluate} 三个分散调用，{@code Purpose.EXPORT} 生产零调用 ⇒
     * 导出通道在结构上仍能"漏掉某一条"，即 S-6 旁路面未关，差距文档 D-B4-10）。
     *
     * @param table   受保护资产定位符（与 security 侧 {@code AssetRef.table} 同义）
     * @param columns 请求列集；{@code null}/空 = 未指定列（此时 bundle 的列面
     *                <b>不可</b>用作裁剪依据，列裁剪仍走 {@link #allowedColumns}，
     *                收口项见差距文档 D-B4-16）
     * @param purpose 通道字面量（{@code page} / {@code export} / {@code ai}）
     * @return {@code DecisionBundle} 的 REST 形态（含 {@code rls.policyRefs} 范围证据）；
     *         security 明确拒绝（4xx 信封）或应答缺段不可解析 → {@code null}（调用方必须按
     *         fail-closed 处理，禁把 null 当"无策略所以放行"）
     * @throws SecurityEngineUnavailableException security 进程不可用（连不上/超时/5xx），
     *         或 security 内部裁决失败后以 {@code code=503 / ECOS-SEC-501} 信封应答
     *         —— 前者由 {@link #postJson} 单点转换，后者由 {@link #unavailableEnvelope}
     *         在<b>同一分类点</b>补上：{@code DecisionController} 把内部异常包成
     *         HTTP 200 + {@code code=503} 信封，若不识别该信封就会把"引擎裁决失败"
     *         误归成"没有裁决证据"，违详设-01 §4.5.4「不可用 ≠ 无权限」分态
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> decide(String table, List<String> columns, String purpose) {
        try {
            Map<String, Object> asset = new LinkedHashMap<>();
            asset.put("table", table);
            if (columns != null && !columns.isEmpty()) asset.put("columns", columns);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("assetRef", asset);
            body.put("purpose", purpose);
            Map<String, Object> resp = postJson("/api/v1/security/decision", body);
            if (resp == null) return null;
            if (unavailableEnvelope(resp)) {
                throw new SecurityEngineUnavailableException("/api/v1/security/decision",
                        "security 信封 code=" + resp.get("code") + " message=" + resp.get("message"), null);
            }
            if (!(resp.get("data") instanceof Map)) return null;
            return (Map<String, Object>) resp.get("data");
        } catch (SecurityEngineUnavailableException e) {
            throw e; // 可用性故障保持独立态上抛（§4.5.4 分态）
        } catch (Exception e) {
            log.warn("WorkspaceSecurityEngineClient.decide 无裁决证据: table={} purpose={} reason={}",
                    table, purpose, e.getMessage());
            return null;
        }
    }

    /**
     * 是否为「不可用」信封：{@code ApiResponse.code == 503} 或 message 以
     * {@code ECOS-SEC-501} 起头（{@code DecisionController} 的 fail-closed 出口形态）。
     * 4xx 业务信封（400 参数错 / 403 无上下文）不算不可用 —— 那是明确的"无裁决"，
     * 由调用方走 403 分支。
     */
    private static boolean unavailableEnvelope(Map<String, Object> resp) {
        Object code = resp.get("code");
        if (code instanceof Number n && n.intValue() == 503) return true;
        Object message = resp.get("message");
        return message != null && String.valueOf(message).startsWith("ECOS-SEC-501");
    }

    // ═══════════════ ABAC（默认 DENY false） ═══════════════

    /**
     * POST /api/v1/security/policy-engine/evaluate。
     *
     * <p>导出通道（{@code audit:export}）咽喉：security 明确 DENY → {@code false}
     * （调用方转 403）；进程不可用 → 503 {@link SecurityEngineUnavailableException} 上抛，
     * 由 {@code SecurityDownAllChannelDenyTest#export} 钉住"拒绝产出任何字节"。</p>
     */
    @SuppressWarnings("unchecked")
    public boolean evaluate(String action, Map<String, Object> attrs) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("policy", "rbac");
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("action", action);
            if (attrs != null) input.putAll(attrs);
            input.put("subject", Map.of("userId", currentUserId(), "role", "user"));
            body.put("input", input);
            Map<String, Object> resp = postJson("/api/v1/security/policy-engine/evaluate", body);
            if (resp == null || !(resp.get("data") instanceof Map)) return false;
            Object result = ((Map<String, Object>) resp.get("data")).get("result");
            return Boolean.TRUE.equals(result);
        } catch (SecurityEngineUnavailableException e) {
            throw e; // 可用性故障不得冒充"策略拒绝"（§4.5.4 分态）
        } catch (Exception e) {
            log.warn("WorkspaceSecurityEngineClient.evaluate DENY: action={} reason={}",
                    action, e.getMessage());
            return false;
        }
    }

    // ═══════════════ 审计 — Kafka ecos.audit ═══════════════

    public void audit(String action, String resource, Object result) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventId", UUID.randomUUID().toString());
            event.put("timestamp", Instant.now().toString());
            event.put("module", "workspace");
            event.put("userId", currentUserId());
            event.put("action", action);
            event.put("resource", resource);
            event.put("result", result == null ? "OK" : String.valueOf(result));
            publishAuditEvent(event);
        } catch (Exception e) {
            log.warn("WorkspaceSecurityEngineClient.audit failed: action={} reason={}",
                    action, e.getMessage());
        }
    }

    private void publishAuditEvent(Map<String, Object> event) {
        EventBusService bus = eventBusProvider.getIfAvailable();
        if (bus == null) {
            log.warn("WorkspaceSecurityEngineClient.audit: EventBusService 未装配，审计仅记日志: action={}",
                    event.get("action"));
            return;
        }
        try {
            bus.publish(KafkaTopics.AUDIT, event);
        } catch (Exception e) {
            log.warn("WorkspaceSecurityEngineClient.audit: EventBus 发布失败: action={} reason={}",
                    event.get("action"), e.getMessage());
        }
    }

    // ═══════════════ helpers ═══════════════

    private String currentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                return auth.getName();
            }
        } catch (Exception ignored) {
            // fail-safe：退化为信任链上下文（ADR-7），再退 anonymous 走最小权限，而非伪造真实主体
            log.debug("WorkspaceSecurityEngineClient: 读取 SecurityContext 失败，退化 ServiceContext: {}",
                    ignored.getMessage());
        }
        // workspace 独立进程（:18090）无 Spring Security 上下文，主体由
        // HeaderAuthInterceptor 还原进 ServiceContext（X-ECOS-USER 信任头单源）。
        ServiceContext ctx = ServiceContext.current();
        if (ctx != null && ctx.getUserId() != null && !ctx.getUserId().isBlank()) {
            return ctx.getUserId();
        }
        return "anonymous";
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> postJson(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        relayCallerCredentials(headers);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> resp;
        try {
            // path 为相对 Bean rootUri（sysman 基址，F07-17 两态寻址单源）的完整路由
            resp = restTemplate.postForEntity(path, entity, Map.class);
        } catch (ResourceAccessException | HttpServerErrorException e) {
            // 连不上 / 超时 / security 侧 5xx = 可用性故障，不是策略裁决（详设-01 §4.5.4）：
            // 单点转 503 ECOS-SEC-501 上抛，禁在此折叠成 "1=0" / 空列集 / false
            throw new SecurityEngineUnavailableException(path, e.getMessage(), e);
        }
        return resp == null ? null : resp.getBody();
    }

    /**
     * 服务间凭证透传（H10 服务凭证收口前的 ADR-7 信任链唯一合法通道）。
     *
     * <p>security-engine 的 RLS/CLS/ABAC 控制面不在
     * {@code AnonymousEndpointRegistry} ANONYMOUS 面（{@code /api/v1/security} 为
     * CLEARANCE_EXEMPT：需认证、跳过准入裁决）。2026-10-11 live 实证：本客户端
     * 原样出站不带任何凭证 → sysman/gateway Spring Security 默认 DENY 403 →
     * {@code applyRls} 折叠成 "1=0" → {@link ScenarioReadGuard} fail-closed →
     * 场景域 L1/L2 对已认证 admin 也 403（误 DENY，非策略 DENY）。</p>
     *
     * <p>整改：入口请求携带的 {@code Authorization}（用户 JWT 原样中继，security 端
     * JwtAuthenticationFilter 真实验签）与 {@code X-ECOS-USER/ROLE/TENANT/DOMAIN}
     * 信任头逐字转发；无入口请求上下文（异步/定时任务）时不伪造凭证 ——
     * 保持 fail-closed 语义不变，只修"把合法已认证用户误判为不可用"这一 plumbing 缺口。</p>
     */
    private void relayCallerCredentials(HttpHeaders headers) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return; // 无请求上下文：不带凭证，由 security-engine 默认 DENY（宁 DENY 不超发）
        }
        HttpServletRequest request = servletAttrs.getRequest();
        String authorization = trimToNull(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (authorization != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
        copyIfPresent(request, headers, HeaderAuthInterceptor.H_USER);
        copyIfPresent(request, headers, HeaderAuthInterceptor.H_ROLE);
        copyIfPresent(request, headers, HeaderAuthInterceptor.H_TENANT);
        copyIfPresent(request, headers, HeaderAuthInterceptor.H_DOMAIN);
    }

    private static void copyIfPresent(HttpServletRequest request, HttpHeaders headers, String header) {
        String value = trimToNull(request.getHeader(header));
        if (value != null) {
            headers.set(header, value);
        }
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
