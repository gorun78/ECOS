package com.chinacreator.gzcm.engine.ontology.security;

import com.chinacreator.gzcm.common.event.KafkaTopics;
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

import java.lang.reflect.Method;
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
 *   <li><b>默认 DENY</b>：security-engine 不可用时默认拒绝，不降级</li>
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

    /** 可选 — 容器中存在 KafkaTemplate Bean 时注入（反射访问容忍 classpath 差异） */
    @Autowired(required = false)
    private Object kafkaTemplateBean;

    /** 可选 — RestTemplate 来自 buszhi/gateway 全局 */
    private final RestTemplate serviceRestTemplate;

    public SecurityEngineClient(
            @Value("${service.security.base-url:http://localhost:18081}") String baseUrl,
            @Value("${service.security.timeout-ms:5000}") int timeoutMs,
            @Autowired(required = false) RestTemplate serviceRestTemplate) {
        this.baseUrl = baseUrl;
        this.timeoutMs = timeoutMs;
        this.serviceRestTemplate = serviceRestTemplate;
    }

    // ═══════════════ 1. RLS — 行级安全 ═══════════════

    /**
     * 行级 RLS — 请求 security-engine 计算 WHERE 条件。
     *
     * @param entityType 实体类型
     * @param table      表名
     * @param filters    过滤条件键值
     * @return WHERE 条件字符串；security-engine 不可用 DENY 时返回 "1=0"
     */
    @SuppressWarnings("unchecked")
    public String applyRls(String entityType, String table, Map<String, Object> filters) {
        if (serviceRestTemplate == null) {
            log.debug("SecurityEngineClient.applyRls: RestTemplate 未注入，DENY");
            return "1=0";
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
            if (resp == null || resp.get("data") == null) return "1=0";
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map)) return "1=0";
            Map<String, Object> data = (Map<String, Object>) dataObj;
            Object whereObj = data.get("whereClause");
            if (whereObj == null) return "";
            return whereObj.toString();
        } catch (Exception e) {
            log.warn("SecurityEngineClient.applyRls DENY: entityType={} table={} reason={}",
                    entityType, table, e.getMessage());
            return "1=0";
        }
    }

    // ═══════════════ 2. CLS — 列级安全 ═══════════════

    /**
     * 列级 CLS — 给定实体全列，返回当前用户可见列。
     *
     * @param entityType 实体类型
     * @param cols       全列列表
     * @return 可见列集合；DENY 时返回 List.of()
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
            if (resp == null || resp.get("data") == null) return List.of();
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map)) return List.of();
            Map<String, Object> data = (Map<String, Object>) dataObj;
            Object allowedObj = data.get("allowedColumns");
            if (allowedObj instanceof List<?> list) {
                List<String> result = new ArrayList<>(list.size());
                for (Object o : list) result.add(String.valueOf(o));
                return result;
            }
            return List.of();
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
     * ABAC — OPA 策略裁决。
     * @return true 表示允许；DENY 或不可用时返回 false
     */
    @SuppressWarnings("unchecked")
    public boolean evaluatePolicy(String action, Map<String, Object> attrs) {
        if (serviceRestTemplate == null) {
            log.warn("SecurityEngineClient.evaluatePolicy DENY (no RestTemplate): action={}", action);
            return false;
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
            if (resp == null || resp.get("data") == null) return false;
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map)) return false;
            Object result = ((Map<String, Object>) dataObj).get("result");
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("SecurityEngineClient.evaluatePolicy DENY: action={} reason={}",
                    action, e.getMessage());
            return false;
        }
    }

    // ═══════════════ 5. 审计 — Kafka ecos.audit ═══════════════

    /**
     * 审计事件 — 发 Kafka {@link KafkaTopics#AUDIT} topic（common-api 常量）
     * <p>不阻塞主流程，失败仅 warn。</p>
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
            if (kafkaTemplateBean == null) {
                log.warn("SecurityEngineClient.audit: KafkaTemplate 不可用，审计事件仅记日志: action={} userId={}",
                        action, currentUserIdOrAnonymous());
                return;
            }
            // 通过反射调用 KafkaTemplate.send(String, Object) 容忍 classpath 差异
            Method send = kafkaTemplateBean.getClass().getMethod("send", String.class, Object.class);
            Object future = send.invoke(kafkaTemplateBean, KafkaTopics.AUDIT, event);
            if (future instanceof java.util.concurrent.CompletableFuture<?> cf) {
                cf.whenComplete((r, ex) -> {
                    if (ex != null) {
                        log.warn("SecurityEngineClient.audit async failed: action={} reason={}",
                                action, ex.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            log.warn("SecurityEngineClient.audit failed: action={} reason={}",
                    action, e.getMessage());
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
