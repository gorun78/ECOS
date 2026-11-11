package com.chinacreator.gzcm.engine.ai.audit;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * H10-T2 — ai-engine 高危写操作审计锚点（N15 Top20: AgentConfigController 等）。
 *
 * <p>覆盖 ai-engine 两个 controller 包（{@code engine.ai.controller} 与
 * {@code engine.ai.wagent.controller}）全部 POST/PUT/PATCH/DELETE 端点，afterCompletion 经
 * runtime 横切通道发 Kafka {@link KafkaTopics#AUDIT} 审计事件（EventBusService 类名反射优先，
 * kafkaTemplate 兜底，均不可用时 WARN 留痕）。不自建审计存储、不封装新 REST 通道。</p>
 *
 * <p><b>wagent 写面为何必须在内</b>：L3 发布/提交（{@code publishCandidate}、
 * {@code commitAction}、审批凭证签发）全在 {@code engine.ai.wagent.controller} 包，
 * 只扫 {@code engine.ai.controller} 时这些拒单与成功一律零审计行（缺陷已实测：审计表
 * {@code resource LIKE 'WAgent%'} 计数为 0）。批D「越权/过期/自审批须审计留痕」的成立
 * 完全依赖这条范围。</p>
 *
 * <p>自注册（实现 WebMvcConfigurer），不依赖 pom 变更（模块无 aop starter 时的
 * HandlerInterceptor 方案，参照 sysman HeaderAuthInterceptor 先例）。</p>
 */
@Component
public class AiSecurityEngineAuditInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AiSecurityEngineAuditInterceptor.class);

    /** 审计锚点覆盖的 controller 包前缀（wagent 为其子包但非 `controller` 子路径，故并列登记）。 */
    private static final List<String> SCOPE_PACKAGES = List.of(
            "com.chinacreator.gzcm.engine.ai.controller",
            "com.chinacreator.gzcm.engine.ai.wagent.controller");

    private static final String EVENTBUS_IMPL_FQN = "com.chinacreator.gzcm.runtime.eventbus.EventBusService";

    private final ApplicationContext applicationContext;

    @Autowired(required = false)
    @Qualifier("kafkaTemplate")
    private Object kafkaTemplateBean;

    public AiSecurityEngineAuditInterceptor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/**");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            if (!(handler instanceof HandlerMethod hm)) return;
            String pkg = hm.getBeanType().getPackageName();
            if (SCOPE_PACKAGES.stream().noneMatch(pkg::startsWith)) return;
            String httpMethod = request.getMethod();
            if (!"POST".equals(httpMethod) && !"PUT".equals(httpMethod)
                    && !"PATCH".equals(httpMethod) && !"DELETE".equals(httpMethod)) {
                return;
            }
            Map<String, Object> event = buildAuditEvent(request, response.getStatus(), ex,
                    hm.getBeanType().getSimpleName() + "#" + hm.getMethod().getName());
            publishAuditEvent(event);
        } catch (Exception e) {
            log.warn("AiSecurityEngineAuditInterceptor: 审计锚点事件失败(不阻塞主流程): {}", e.getMessage());
        }
    }

    /** 审计载荷（键集固定为 {@link #auditPayloadKeyWhitelist()}，只带 ID 与摘要，禁请求/响应原文）。 */
    public Map<String, Object> buildAuditEvent(HttpServletRequest request, int statusCode,
                                               Exception ex, String resource) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("timestamp", Instant.now().toString());
        event.put("module", "ai-engine");
        event.put("traceId", AiTraceSupport.currentTraceId());
        event.put("userId", resolveUserId(request));
        event.put("action", request.getMethod() + " " + request.getRequestURI());
        event.put("resource", resource);
        event.put("result", statusCode < 400 && ex == null ? "OK" : "FAIL");
        event.put("statusCode", statusCode);
        return event;
    }

    /** 载荷键白名单（traceId 必在：批D 三类拒单需与 CLI 输出同源可对账）。 */
    public static List<String> auditPayloadKeyWhitelist() {
        return List.of("eventId", "timestamp", "module", "traceId", "userId",
                "action", "resource", "result", "statusCode");
    }

    private void publishAuditEvent(Map<String, Object> event) {
        // 1) runtime-event EventBusService 横切通道（铁律 §2.4-5 出口）
        try {
            Class<?> type = Class.forName(EVENTBUS_IMPL_FQN);
            String[] names = applicationContext.getBeanNamesForType(type);
            if (names.length > 0) {
                Object bus = applicationContext.getBean(names[0]);
                type.getMethod("publish", String.class, Object.class).invoke(bus, KafkaTopics.AUDIT, event);
                return;
            }
        } catch (ClassNotFoundException ignored) {
            // runtime-event 不在 classpath → kafkaTemplate 兜底
        } catch (Exception e) {
            log.debug("AiSecurityEngineAuditInterceptor: EventBusService 发布失败，转兜底: {}", e.getMessage());
        }
        // 2) kafkaTemplate 反射兜底
        if (kafkaTemplateBean != null) {
            try {
                Method send = kafkaTemplateBean.getClass().getMethod("send", String.class, Object.class);
                send.invoke(kafkaTemplateBean, KafkaTopics.AUDIT, event);
                return;
            } catch (Exception e) {
                log.debug("AiSecurityEngineAuditInterceptor: kafkaTemplate 兜底失败: {}", e.getMessage());
            }
        }
        // 3) WARN 留痕（审计失败不阻塞业务，但必须可见）
        log.warn("AiSecurityEngineAuditInterceptor: EventBus/Kafka 均不可用，审计仅记日志: action={} userId={}",
                event.get("action"), event.get("userId"));
    }

    /**
     * userId 解析（多进程兼容，全部反射避免编译期依赖 sysman/spring-security）：
     * UserContext ThreadLocal → SecurityContextHolder → X-User-Id 头 → anonymous。
     */
    private String resolveUserId(HttpServletRequest request) {
        try {
            Class<?> uc = Class.forName("com.chinacreator.gzcm.sysman.iam.context.UserContext");
            Object id = uc.getMethod("getCurrentUserId").invoke(null);
            if (id != null && !String.valueOf(id).isBlank()) return String.valueOf(id);
        } catch (Throwable ignored) {
            // sysman 不在 classpath
        }
        try {
            Class<?> sch = Class.forName("org.springframework.security.core.context.SecurityContextHolder");
            Object securityContext = sch.getMethod("getContext").invoke(null);
            Object auth = securityContext.getClass().getMethod("getAuthentication").invoke(securityContext);
            if (auth != null) {
                Object name = auth.getClass().getMethod("getName").invoke(auth);
                if (name != null && !String.valueOf(name).isBlank()
                        && !"anonymousUser".equals(String.valueOf(name))) {
                    return String.valueOf(name);
                }
            }
        } catch (Throwable ignored) {
            // spring-security 不在 classpath
        }
        String header = request.getHeader("X-User-Id");
        if (header != null && !header.isBlank()) return header;
        return "anonymous";
    }
}
