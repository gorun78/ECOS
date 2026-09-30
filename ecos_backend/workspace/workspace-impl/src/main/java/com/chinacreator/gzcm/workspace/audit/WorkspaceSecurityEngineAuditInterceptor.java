package com.chinacreator.gzcm.workspace.audit;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * H10-T2 — workspace 高危写操作审计锚点（N15 Top20: ObjectController /
 * ScenarioRunController / ScenarioSandboxController 等全部 CUD 端点）。
 *
 * <p>覆盖 {@code com.chinacreator.gzcm.workspace.controller} 包 POST/PUT/DELETE，
 * afterCompletion 经 runtime 横切通道发 Kafka {@link KafkaTopics#AUDIT}
 * （EventBusService 类名反射优先 → kafkaTemplate 兜底 → WARN 留痕）。
 * 不自建审计表、不各自封装通道。</p>
 */
@Component
public class WorkspaceSecurityEngineAuditInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceSecurityEngineAuditInterceptor.class);

    private static final String SCOPE_PACKAGE = "com.chinacreator.gzcm.workspace.controller";

    private static final String EVENTBUS_IMPL_FQN = "com.chinacreator.gzcm.runtime.eventbus.EventBusService";

    private final ApplicationContext applicationContext;

    @Autowired(required = false)
    @Qualifier("kafkaTemplate")
    private Object kafkaTemplateBean;

    public WorkspaceSecurityEngineAuditInterceptor(ApplicationContext applicationContext) {
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
            if (!pkg.startsWith(SCOPE_PACKAGE)) return;
            String httpMethod = request.getMethod();
            if (!"POST".equals(httpMethod) && !"PUT".equals(httpMethod) && !"DELETE".equals(httpMethod)) {
                return;
            }
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventId", UUID.randomUUID().toString());
            event.put("timestamp", Instant.now().toString());
            event.put("module", "workspace");
            event.put("userId", resolveUserId(request));
            event.put("action", httpMethod + " " + request.getRequestURI());
            event.put("resource", hm.getBeanType().getSimpleName() + "#" + hm.getMethod().getName());
            event.put("result", response.getStatus() < 400 && ex == null ? "OK" : "FAIL");
            event.put("statusCode", response.getStatus());
            publishAuditEvent(event);
        } catch (Exception e) {
            log.warn("WorkspaceSecurityEngineAuditInterceptor: 审计锚点事件失败(不阻塞主流程): {}", e.getMessage());
        }
    }

    private void publishAuditEvent(Map<String, Object> event) {
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
            log.debug("WorkspaceSecurityEngineAuditInterceptor: EventBusService 发布失败，转兜底: {}", e.getMessage());
        }
        if (kafkaTemplateBean != null) {
            try {
                Method send = kafkaTemplateBean.getClass().getMethod("send", String.class, Object.class);
                send.invoke(kafkaTemplateBean, KafkaTopics.AUDIT, event);
                return;
            } catch (Exception e) {
                log.debug("WorkspaceSecurityEngineAuditInterceptor: kafkaTemplate 兜底失败: {}", e.getMessage());
            }
        }
        log.warn("WorkspaceSecurityEngineAuditInterceptor: EventBus/Kafka 均不可用，审计仅记日志: action={} userId={}",
                event.get("action"), event.get("userId"));
    }

    private String resolveUserId(HttpServletRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                return auth.getName();
            }
        } catch (Throwable ignored) {
            // spring-security 不在 classpath
        }
        String header = request.getHeader("X-User-Id");
        if (header != null && !header.isBlank()) return header;
        return "anonymous";
    }
}
