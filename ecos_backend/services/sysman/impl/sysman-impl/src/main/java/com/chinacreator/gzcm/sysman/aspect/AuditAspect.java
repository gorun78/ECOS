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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
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

    /**
     * R1.2 basic 模式下记录的关键操作集合。
     */
    private static final Set<String> CRITICAL_ACTIONS = Set.of(
            "LOGIN", "DELETE", "CREATE", "UPDATE", "PERMISSION_CHANGE");

    private final IAuditLogService auditLogService;

    // R1.2: 用于查询用户 audit_mode，以及 full 模式下写入 ecos_audit_log（哈希链表）
    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    // R1.2: full 模式下联动 AuditHashChainService（位于 security-engine-impl，
    // sysman-impl 编译期不可见，故通过 ApplicationContext + 反射调用，不可用时降级）
    @Autowired(required = false)
    private ApplicationContext applicationContext;

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

            // ── R1.2: 审计力度分级 ──
            String auditMode = resolveAuditMode(userId);
            switch (auditMode) {
                case "basic":
                    // basic: 只记录关键操作（LOGIN/DELETE/CREATE/UPDATE/PERMISSION_CHANGE）
                    if (!CRITICAL_ACTIONS.contains(actionType)) {
                        log.debug("R1.2 basic 模式跳过非关键操作: action={}, user={}", actionType, userId);
                        return;
                    }
                    break;
                case "detailed":
                case "full":
                    // detailed/full: 记录所有操作（full 额外做哈希链双写，在持久化后处理）
                    break;
                default:
                    // 未知模式按 detailed 处理（保守记录）
                    break;
            }

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

            // R1.2: detailed/full 模式额外记录请求参数
            if ("detailed".equals(auditMode) || "full".equals(auditMode)) {
                Map<String, Object> args = extractRequestArgs(joinPoint);
                if (args != null && !args.isEmpty()) {
                    details.put("arguments", args);
                }
            }
            event.setDetails(details);

            // 7. 持久化 — 使用已有的 AuditLogServiceImpl（内部使用 AuditLogDao）
            auditLogService.log(event);

            // R1.2: full 模式 — 联动 AuditHashChainService 哈希链双写
            if ("full".equals(auditMode)) {
                writeHashChainAudit(userId, actionType, resource, targetId, ipAddress, details);
            }

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

    // ════════════════════════════════════════════════════════════════
    // R1.2 审计力度分级辅助方法
    // ════════════════════════════════════════════════════════════════

    /**
     * R1.2: 查询用户的 audit_mode（basic/detailed/full）。
     * <p>查询 td_user_security_profile 表，异常或无配置时默认 "basic"。
     */
    private String resolveAuditMode(String userId) {
        if (jdbcTemplate == null || userId == null || "anonymous".equals(userId)) {
            return "basic";
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT audit_mode FROM td_user_security_profile " +
                    "WHERE scope_type = 'user' AND user_id = ? LIMIT 1",
                    userId);
            if (!rows.isEmpty() && rows.get(0).get("audit_mode") != null) {
                String mode = rows.get(0).get("audit_mode").toString().trim().toLowerCase();
                if (!mode.isBlank()) {
                    return mode;
                }
            }
        } catch (Exception e) {
            log.debug("R1.2 查询 audit_mode 失败 user={}, 降级为 basic: {}", userId, e.getMessage());
        }
        return "basic";
    }

    /**
     * R1.2: 提取请求参数（detailed/full 模式记录）。
     * <p>将方法参数名与参数值组装为 Map，跳过 HttpServletRequest/Response 等不可序列化对象。
     */
    private Map<String, Object> extractRequestArgs(JoinPoint joinPoint) {
        try {
            MethodSignature sig = (MethodSignature) joinPoint.getSignature();
            String[] paramNames = sig.getParameterNames();
            Object[] args = joinPoint.getArgs();
            if (paramNames == null || args == null) {
                return Collections.emptyMap();
            }
            Map<String, Object> result = new LinkedHashMap<>();
            for (int i = 0; i < Math.min(paramNames.length, args.length); i++) {
                Object val = args[i];
                if (val == null) continue;
                // 跳过 Servlet 相关对象，避免序列化问题
                String typeName = val.getClass().getName();
                if (typeName.startsWith("jakarta.servlet") || typeName.startsWith("org.springframework.web")) {
                    continue;
                }
                // 安全转字符串（避免大对象）
                String strVal;
                try {
                    strVal = val.toString();
                } catch (Exception e) {
                    strVal = "<toString-failed>";
                }
                if (strVal.length() > 500) {
                    strVal = strVal.substring(0, 500) + "...[truncated]";
                }
                result.put(paramNames[i], strVal);
            }
            return result;
        } catch (Exception e) {
            log.debug("R1.2 提取请求参数失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * R1.2 full 模式: 将审计记录写入 ecos_audit_log（哈希链表）并联动 AuditHashChainService 戳记哈希。
     * <p>ecos_audit_log 与 td_audit_log 双写，保证哈希链完整性保护。
     * AuditHashChainService 位于 security-engine-impl，sysman-impl 编译期不可见，
     * 故通过 ApplicationContext 查找 bean 后反射调用 stampHashChain(long)，不可用时降级为不双写。
     */
    private void writeHashChainAudit(String userId, String actionType, String resource,
                                     String targetId, String ipAddress, Map<String, Object> details) {
        if (jdbcTemplate == null) {
            log.debug("R1.2 full 模式: JdbcTemplate 不可用，跳过哈希链双写");
            return;
        }
        try {
            // 组装 changes jsonb
            String changesJson;
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> changes = new LinkedHashMap<>();
                changes.put("action", actionType);
                changes.put("resource", resource);
                if (targetId != null) changes.put("targetId", targetId);
                if (details != null) changes.put("details", details);
                changesJson = om.writeValueAsString(changes);
            } catch (Exception e) {
                changesJson = "{\"action\":\"" + actionType + "\"}";
            }

            // 写入 ecos_audit_log，获取自增 id（bigint）
            Long auditId = jdbcTemplate.queryForObject(
                    "INSERT INTO ecos_audit_log (username, operation, entity_type, entity_id, changes, ip_address, created_at, category) " +
                    "VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, NOW(), ?) RETURNING id",
                    Long.class,
                    userId,
                    actionType,
                    resource != null ? resource : "Unknown",
                    targetId,
                    changesJson,
                    ipAddress,
                    "audit");

            if (auditId == null || auditId <= 0) {
                log.warn("R1.2 full 模式: ecos_audit_log 写入未返回 id");
                return;
            }

            // 联动 AuditHashChainService.stampHashChain(auditId) — 反射调用
            invokeStampHashChain(auditId);
        } catch (Exception e) {
            log.warn("R1.2 full 模式哈希链双写失败: {}", e.getMessage());
        }
    }

    /**
     * R1.2: 通过反射调用 AuditHashChainService.stampHashChain(long auditLogId)。
     * <p>该 Service 位于 security-engine-impl（上层模块），sysman-impl 编译期不可见，
     * 故运行时从 ApplicationContext 查找 bean 并反射调用；bean 不存在时降级为不戳记。
     */
    private void invokeStampHashChain(long auditLogId) {
        if (applicationContext == null) {
            return;
        }
        try {
            // 按类名查找 bean（避免编译期依赖）
            Map<String, ?> beans = applicationContext.getBeansOfType(Object.class);
            Object hashChainService = null;
            for (Map.Entry<String, ?> entry : beans.entrySet()) {
                if (entry.getValue().getClass().getName().endsWith("AuditHashChainService")) {
                    hashChainService = entry.getValue();
                    break;
                }
            }
            if (hashChainService == null) {
                return; // AuditHashChainService 不可用 → 降级为不双写
            }
            Method stampMethod = hashChainService.getClass().getMethod("stampHashChain", long.class);
            stampMethod.invoke(hashChainService, auditLogId);
            log.debug("R1.2 full 模式: 哈希链戳记成功 auditId={}", auditLogId);
        } catch (Exception e) {
            log.debug("R1.2 哈希链戳记降级 auditId={}: {}", auditLogId, e.getMessage());
        }
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
