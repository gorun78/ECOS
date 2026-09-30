package com.chinacreator.gzcm.runtime.llm.security;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * H10-T1 (PMO-74 N14 / 铁律 §2.4) — llm-gateway 对 security-engine 密钥/裁决/审计能力的统一桥。
 *
 * <p>背景：llm-gateway 此前 apiKey 由调用方请求体明文携带（{@code ChatRequest.apiKey}），
 * {@code ProfileConfig.apiKeyRef} 设计字段零接线，security-engine 集成探针命中 0。</p>
 *
 * <p>本桥按「runtime 不编译期依赖 engine-impl」的依赖方向铁律，采用<b>类名反射 + 可选 bean</b>
 * 通道（参照 ai-engine {@code AiSecurityEngineClient} 对 runtime-event 的同款先例）访问同 JVM
 * （gateway fat-JAR 聚合部署态）的 security-engine 能力：</p>
 * <ol>
 *   <li><b>密钥取得</b>：{@link #resolveSecret(String, String)} 解析 {@code apiKeyRef} 引用
 *       （{@code secret:<id>} / {@code enc:<keyId>:<cipherText>} / 裸 {@code <secretId>}），
 *       经 {@code ISecretService.getSecretValue} 或 {@code IDataEncryptionService.decrypt}
 *       （security-engine crypto 域）还原明文密钥，<b>仅在本方法返回值中短暂存在</b>，
 *       日志/审计一律只出掩码引用。</li>
 *   <li><b>ABAC 裁决</b>：{@link #evaluate(String, String, String, Map)} 经同 JVM
 *       {@code OpaPolicyService} bean（缺失即视为 security 不可用）。</li>
 *   <li><b>审计</b>：{@link #audit(String, String, String, String)} 优先 runtime-event
 *       {@code EventBusService}，其次 {@code kafkaTemplate} 反射兜底，topic =
 *       {@link KafkaTopics#AUDIT}（铁律 §2.4#5 新代码走 Kafka）。</li>
 * </ol>
 *
 * <p>🔴 fail-closed（铁律 §2.4#6）：security-engine 类/bean 不在 classpath、bean 调用异常、
 * 引用无法解析 — 一律返回「不可用」（null / false）并 {@code log.warn}，由调用方默认 DENY；
 * <b>绝不回退到调用方自报的明文密钥，绝不放行直连</b>。</p>
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
    /** security-engine ABAC 裁决服务（PolicyEngineController 背后的 OPA 门面） */
    private static final String OPA_SERVICE_FQN =
            "com.chinacreator.gzcm.engine.security.service.OpaPolicyService";
    /** runtime-event 横切审计出口（铁律 §2.5#7，禁自建 KafkaTemplate） */
    private static final String EVENTBUS_FQN =
            "com.chinacreator.gzcm.runtime.eventbus.EventBusService";

    private final ApplicationContext applicationContext;

    @Autowired(required = false)
    @Qualifier("kafkaTemplate")
    private Object kafkaTemplateBean;

    @Autowired
    public SecurityEngineBridge(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
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

    // ═══════════════ 2. ABAC 裁决（默认 DENY） ═══════════════

    /**
     * 经 security-engine {@code OpaPolicyService}（同 JVM bean，PolicyEngineController 背后同一实现）
     * 做操作授权裁决。铁律 §2.4#4/#6：不可用 / 异常 / 非明确 ALLOW → 一律 false（默认 DENY）。
     */
    public boolean evaluate(String callerId, String tenantId, String action, Map<String, Object> attrs) {
        try {
            Object bean = findBeanByClassName(OPA_SERVICE_FQN);
            if (bean == null) {
                log.warn("[llm-gateway] security-engine OpaPolicyService 不可用，ABAC 裁决默认 DENY: action={}, caller={}",
                        action, callerId);
                return false;
            }
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("action", action);
            if (attrs != null) input.putAll(attrs);
            input.put("subject", Map.of(
                    "userId", callerId == null ? "anonymous" : callerId,
                    "tenantId", tenantId == null ? "default" : tenantId,
                    "role", "llm-gateway-service"));
            Method m = bean.getClass().getMethod("evaluate", String.class, Map.class);
            Object resp = m.invoke(bean, "rbac", input);
            if (resp instanceof Map<?, ?> map) {
                // OpaPolicyService.evaluate 返回体裁决键为 allow（兼容 result 别名）
                Object verdict = map.containsKey("allow") ? map.get("allow") : map.get("result");
                if (Boolean.TRUE.equals(verdict)) {
                    return true;
                }
                log.warn("[llm-gateway] security-engine 裁决未放行（默认 DENY）: action={}, caller={}, verdict={}",
                        action, callerId, verdict);
                return false;
            }
            log.warn("[llm-gateway] security-engine 裁决响应异常（默认 DENY）: action={}, respType={}",
                    action, resp == null ? "null" : resp.getClass().getName());
            return false;
        } catch (Exception e) {
            log.warn("[llm-gateway] security-engine 裁决调用异常（默认 DENY）: action={}, reason={}",
                    action, rootMessage(e));
            return false;
        }
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
            event.put("action", action);
            event.put("resource", resource);
            event.put("result", result == null ? "OK" : result);
            publishAuditEvent(event);
        } catch (Exception e) {
            log.warn("[llm-gateway] 审计事件发布失败: action={}, reason={}", action, e.getMessage());
        }
    }

    private void publishAuditEvent(Map<String, Object> event) {
        // 1) runtime-event EventBusService 横切通道（类名反射，避免编译期依赖）
        try {
            Class<?> type = Class.forName(EVENTBUS_FQN);
            String[] names = applicationContext.getBeanNamesForType(type);
            if (names.length > 0) {
                Object bus = applicationContext.getBean(names[0]);
                type.getMethod("publish", String.class, Object.class)
                        .invoke(bus, KafkaTopics.AUDIT, event);
                return;
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
                return;
            } catch (Exception e) {
                log.debug("[llm-gateway] kafkaTemplate 兜底失败: {}", e.getMessage());
            }
        }
        // 3) 无通道 → WARN 留痕
        log.warn("[llm-gateway] EventBusService/KafkaTemplate 均不可用，审计仅记日志: action={}, result={}",
                event.get("action"), event.get("result"));
    }

    // ═══════════════ 工具 ═══════════════

    /** security-engine 集成是否可用（类在 classpath 且 bean 可解析）— 供调用方区分「未配置」与「断连」 */
    public boolean isSecurityEngineAvailable() {
        return findBeanByClassName(SECRET_SERVICE_FQN) != null
                || findBeanByClassName(ENCRYPTION_SERVICE_FQN) != null;
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
