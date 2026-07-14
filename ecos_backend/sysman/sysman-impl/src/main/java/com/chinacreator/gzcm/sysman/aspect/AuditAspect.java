package com.chinacreator.gzcm.sysman.aspect;

import com.chinacreator.gzcm.sysman.audit.model.AuditEvent;
import com.chinacreator.gzcm.sysman.audit.service.IAuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;

/**
 * AOP 审计拦截器 — 自动记录所有 CUD (Create/Update/Delete) 操作到 ecos_audit_log。
 *
 * <p>拦截 {@code com.chinacreator.gzcm.sysman.controller} 包下所有
 * {@link PostMapping @PostMapping} / {@link PutMapping @PutMapping} / {@link DeleteMapping @DeleteMapping}
 * 方法，通过 {@link IAuditLogService} 持久化审计事件。
 *
 * <p>自动提取：操作用户、动作类型 (CREATE/UPDATE/DELETE)、目标实体名、目标ID、请求 IP、时间戳。
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final IAuditLogService auditLogService;

    public AuditAspect(IAuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    // ════════════════════════════════════════════════════════════════
    // Pointcut: 所有 CUD 端点 (PostMapping / PutMapping / DeleteMapping)
    // 在 com.chinacreator.gzcm.sysman.controller 包及其子包中
    // ════════════════════════════════════════════════════════════════

    @Pointcut("execution(* com.chinacreator.gzcm.sysman.controller..*.*(..)) && " +
              "(@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
              " @annotation(org.springframework.web.bind.annotation.PutMapping) || " +
              " @annotation(org.springframework.web.bind.annotation.DeleteMapping))")
    public void cudOperations() {
    }

    /**
     * AfterReturning 通知：在 CUD 方法成功返回后记录审计日志。
     * 若方法抛出异常则不记录（符合只记录成功操作的审计惯例）。
     */
    @AfterReturning(pointcut = "cudOperations()", returning = "result")
    public void logCudOperation(JoinPoint joinPoint, Object result) {
        try {
            // 1. 获取当前认证用户
            String userId = getCurrentUserId();
            if (userId == null) {
                userId = "anonymous";
            }

            // 2. 确定动作类型
            String actionType = resolveActionType(joinPoint);

            // 3. 确定目标实体名称
            String resource = resolveResource(joinPoint);

            // 4. 提取目标 ID（从路径变量或请求体）
            String targetId = resolveTargetId(joinPoint);

            // 5. 获取客户端 IP
            String ipAddress = getClientIp();

            // 6. 组装审计事件
            AuditEvent event = new AuditEvent();
            event.setEventId(UUID.randomUUID().toString());
            event.setEventType(actionType);          // CREATE / UPDATE / DELETE
            event.setTimestamp(LocalDateTime.now());
            event.setUserId(userId);
            event.setResource(resource);
            event.setAction(actionType);
            event.setResult("SUCCESS");
            event.setIpAddress(ipAddress);

            // 将 targetId 放入 details
            Map<String, Object> details = new LinkedHashMap<>();
            if (targetId != null) {
                details.put("targetId", targetId);
            }
            details.put("method", joinPoint.getSignature().toShortString());
            event.setDetails(details);

            // 7. 持久化 — 使用已有的 AuditLogServiceImpl（内部使用 AuditLogDao）
            auditLogService.log(event);

        } catch (Exception e) {
            // 审计日志记录失败不应中断业务主流程
            log.warn("审计日志记录异常 (不影响主流程): {}", e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 辅助方法
    // ════════════════════════════════════════════════════════════════

    /**
     * 从 SecurityContext 中提取当前用户 ID。
     */
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof String) {
                return (String) principal;
            }
        }
        return null;
    }

    /**
     * 根据方法上的 Spring Web 注解确定动作类型。
     */
    private String resolveActionType(JoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        if (method.isAnnotationPresent(PostMapping.class)) {
            return "CREATE";
        }
        if (method.isAnnotationPresent(PutMapping.class)) {
            return "UPDATE";
        }
        if (method.isAnnotationPresent(DeleteMapping.class)) {
            return "DELETE";
        }
        return "UNKNOWN";
    }

    /**
     * 从类级别 {@link RequestMapping} 推导目标实体名。
     * <p>例如 {@code /api/v1/ecos/workflows} → {@code Workflow}。
     */
    private String resolveResource(JoinPoint joinPoint) {
        // 先从方法上的 @RequestMapping（如果有的话）尝试获取更具体的资源名
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String methodPath = extractPathFromAnnotation(
            method.getAnnotation(PostMapping.class),
            method.getAnnotation(PutMapping.class),
            method.getAnnotation(DeleteMapping.class)
        );

        // 再从类级别的 @RequestMapping 获取路径
        Class<?> targetClass = joinPoint.getTarget().getClass();
        RequestMapping classMapping = targetClass.getAnnotation(RequestMapping.class);
        String classPath = (classMapping != null && classMapping.value().length > 0)
                ? classMapping.value()[0]
                : targetClass.getSimpleName();

        // 合并路径: 取最后一个有意义的 segment
        String fullPath = classPath;
        if (methodPath != null && !methodPath.isEmpty() && !methodPath.equals("/")) {
            fullPath = classPath + methodPath;
        }

        return deriveEntityName(fullPath);
    }

    /**
     * 从 PostMapping/PutMapping/DeleteMapping 注解中提取 path value（只取第一个）。
     */
    @SafeVarargs
    private <T extends Annotation> String extractPathFromAnnotation(T... annotations) {
        for (T ann : annotations) {
            if (ann == null) continue;
            if (ann instanceof PostMapping) {
                String[] v = ((PostMapping) ann).value();
                if (v.length > 0) return v[0];
                String[] p = ((PostMapping) ann).path();
                if (p.length > 0) return p[0];
            }
            if (ann instanceof PutMapping) {
                String[] v = ((PutMapping) ann).value();
                if (v.length > 0) return v[0];
                String[] p = ((PutMapping) ann).path();
                if (p.length > 0) return p[0];
            }
            if (ann instanceof DeleteMapping) {
                String[] v = ((DeleteMapping) ann).value();
                if (v.length > 0) return v[0];
                String[] p = ((DeleteMapping) ann).path();
                if (p.length > 0) return p[0];
            }
        }
        return null;
    }

    /**
     * 从 REST 路径推导实体名称。
     * <p>策略：取路径中最后一个非参数段（不含 {id}, {type} 等），将其转换为 UpperCamelCase 单数形式。
     */
    private String deriveEntityName(String path) {
        if (path == null || path.isEmpty()) {
            return "Unknown";
        }
        // 按 / 分割，过滤掉空串
        String[] segments = path.split("/");
        // 从后往前找第一个有意义的非参数段
        for (int i = segments.length - 1; i >= 0; i--) {
            String seg = segments[i].trim();
            if (seg.isEmpty() || seg.startsWith("{") || seg.startsWith("?")) {
                continue;
            }
            // 排除常见前缀如 api, v1, ecos 等
            if ("api".equalsIgnoreCase(seg) || "v1".equalsIgnoreCase(seg)
                    || "ecos".equalsIgnoreCase(seg)) {
                continue;
            }
            return toEntityName(seg);
        }
        // fallback: 取类名去掉 Controller 后缀
        return "Unknown";
    }

    /**
     * 将 kebab-case / snake_case 路径段转为 UpperCamelCase 实体名。
     * 例如 "workflows" → "Workflow", "causal-links" → "CausalLink"
     */
    private String toEntityName(String segment) {
        // 去掉末尾可能存在的 's' 尝试转为单数
        String singular = segment.endsWith("s") && segment.length() > 1
                ? segment.substring(0, segment.length() - 1)
                : segment;

        // 按 '-' 或 '_' 分割，首字母大写
        String[] parts = singular.split("[-_]");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    /**
     * 尝试从方法参数中提取目标 ID。
     * <p>优先从标注了 {@link org.springframework.web.bind.annotation.PathVariable @PathVariable}
     * 且名称包含 "id" 的参数中提取；其次尝试从请求体中查找 "id" 字段。
     */
    private String resolveTargetId(JoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String[] paramNames = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        Object[] args = joinPoint.getArgs();

        if (paramNames == null || args == null) {
            return null;
        }

        // Strategy 1: 查找 @PathVariable 中包含 "id" 的参数
        for (int i = 0; i < Math.min(paramNames.length, args.length); i++) {
            for (Annotation ann : paramAnnotations[i]) {
                if (ann instanceof org.springframework.web.bind.annotation.PathVariable pv) {
                    String varName = pv.value().isEmpty() ? pv.name() : pv.value();
                    if (varName.isEmpty()) varName = paramNames[i];
                    if (varName.toLowerCase().contains("id") && args[i] != null) {
                        return args[i].toString();
                    }
                    // 也捕获 type / entityId 等标识符
                    if (isIdLike(varName) && args[i] != null) {
                        return args[i].toString();
                    }
                }
            }
        }

        // Strategy 2: 查找 @RequestBody 中的 "id" 字段
        for (int i = 0; i < Math.min(paramNames.length, args.length); i++) {
            for (Annotation ann : paramAnnotations[i]) {
                if (ann instanceof org.springframework.web.bind.annotation.RequestBody) {
                    if (args[i] instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> body = (Map<String, Object>) args[i];
                        Object id = body.get("id");
                        if (id != null) {
                            return id.toString();
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * 判断参数名是否为 ID 类标识符。
     */
    private boolean isIdLike(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.contains("id") || lower.contains("key") || lower.contains("code");
    }

    /**
     * 从当前请求中获取客户端 IP 地址。
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attrs.getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("X-Real-IP");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
            // X-Forwarded-For 可能包含逗号分隔的多个 IP
            if (ip != null && ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
            return ip;
        } catch (Exception e) {
            return null;
        }
    }
}
