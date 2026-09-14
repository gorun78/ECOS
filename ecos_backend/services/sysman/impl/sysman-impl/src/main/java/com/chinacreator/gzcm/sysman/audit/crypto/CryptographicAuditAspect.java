package com.chinacreator.gzcm.sysman.audit.crypto;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ECOS Phase 1 P1-2: Cryptographic Audit Aspect — 加密审计切面。
 *
 * <p>拦截所有标有 {@link CryptographicAudit @CryptographicAudit} 注解的方法，
 * 在方法成功返回后：
 * <ol>
 *   <li>提取注解中的 eventType / resource(SpEL) / action(SpEL)</li>
 *   <li>将方法返回结果序列化为 JSON payload</li>
 *   <li>获取当前认证用户作为 operatorId</li>
 *   <li>调用 {@link CryptoAuditService#record(CryptoAuditLedger)} 写入哈希链</li>
 * </ol>
 *
 * <p>审计记录失败不会影响主流程（异常被捕获并 warn 日志输出）。
 */
@Aspect
@Component
public class CryptographicAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(CryptographicAuditAspect.class);

    private final CryptoAuditService cryptoAuditService;
    private final ExpressionParser spelParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public CryptographicAuditAspect(CryptoAuditService cryptoAuditService) {
        this.cryptoAuditService = cryptoAuditService;
    }

    /**
     * Around 通知：拦截 @CryptographicAudit 注解的方法。
     */
    @Around("@annotation(annotation)")
    public Object audit(ProceedingJoinPoint joinPoint, CryptographicAudit annotation) throws Throwable {
        // 执行原方法
        Object result = joinPoint.proceed();

        try {
            recordAudit(joinPoint, annotation, result);
        } catch (Exception e) {
            // 审计记录失败不应中断业务
            log.warn("加密审计记录失败 (不影响主流程): {}", e.getMessage(), e);
        }

        return result;
    }

    // ────────────────────────────────────────────────
    // 审计记录逻辑
    // ────────────────────────────────────────────────

    private void recordAudit(ProceedingJoinPoint joinPoint,
                             CryptographicAudit annotation,
                             Object result) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object[] args = joinPoint.getArgs();

        // 1. 构建 SpEL 上下文
        EvaluationContext context = buildSpelContext(method, args, result);

        // 2. 解析 SpEL 表达式
        String eventType = annotation.eventType();
        String resource = resolveSpel(annotation.resource(), context);
        String action = resolveSpel(annotation.action(), context);

        // 3. 获取操作人 ID
        String operatorId = getCurrentUserId();

        // 4. 构建 payload（方法返回结果 + 参数摘要）
        String payload = buildPayload(method, args, result);

        // 5. 组装审计记录
        CryptoAuditLedger record = new CryptoAuditLedger();
        record.setEventType(eventType);
        record.setResource(resource != null ? resource : method.getDeclaringClass().getSimpleName());
        record.setAction(action != null && !action.isEmpty() ? action : method.getName());
        record.setOperatorId(operatorId);
        record.setPayload(payload);

        // 6. 写入
        cryptoAuditService.record(record);

        log.debug("加密审计切面记录成功: method={}, eventType={}, operatorId={}",
                method.getName(), eventType, operatorId);
    }

    // ────────────────────────────────────────────────
    // SpEL 解析
    // ────────────────────────────────────────────────

    /**
     * 构建 SpEL 求值上下文，包含方法参数和返回值。
     */
    private EvaluationContext buildSpelContext(Method method, Object[] args, Object result) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 注册方法参数
        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
        if (paramNames != null && args != null) {
            for (int i = 0; i < Math.min(paramNames.length, args.length); i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        // 注册返回值
        context.setVariable("result", result);

        return context;
    }

    /**
     * 解析 SpEL 表达式。空字符串或纯字面量直接返回。
     */
    private String resolveSpel(String expression, EvaluationContext context) {
        if (expression == null || expression.isEmpty()) {
            return expression;
        }
        // 如果是 SpEL 表达式（包含 #），则解析
        if (expression.contains("#")) {
            try {
                Object value = spelParser.parseExpression(expression).getValue(context);
                return value != null ? value.toString() : expression;
            } catch (Exception e) {
                log.warn("SpEL 表达式解析失败: expression={}, error={}", expression, e.getMessage());
                return expression; // fallback: 返回原始表达式
            }
        }
        // 普通字面量，直接返回
        return expression;
    }

    // ────────────────────────────────────────────────
    // 辅助
    // ────────────────────────────────────────────────

    /**
     * 从 SecurityContext 中获取当前用户 ID。
     */
    private String getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                Object principal = authentication.getPrincipal();
                if (principal instanceof String) {
                    return (String) principal;
                }
                if (principal != null) {
                    return principal.toString();
                }
            }
        } catch (Exception e) {
            log.debug("获取当前用户失败: {}", e.getMessage());
        }
        return "anonymous";
    }

    /**
     * 构建 JSON payload：包含方法返回结果摘要和参数信息。
     */
    private String buildPayload(Method method, Object[] args, Object result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("method", method.getDeclaringClass().getSimpleName() + "." + method.getName());

        // 参数摘要
        if (args != null && args.length > 0) {
            Map<String, Object> params = new LinkedHashMap<>();
            String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
            for (int i = 0; i < Math.min(paramNames != null ? paramNames.length : 0, args.length); i++) {
                Object arg = args[i];
                // 只记录简单类型，避免大对象序列化
                if (arg instanceof String || arg instanceof Number || arg instanceof Boolean) {
                    params.put(paramNames[i], arg);
                } else if (arg != null) {
                    params.put(paramNames[i], arg.getClass().getSimpleName() + "@" + Integer.toHexString(arg.hashCode()));
                }
            }
            payload.put("parameters", params);
        }

        // 结果摘要
        if (result != null) {
            if (result instanceof String || result instanceof Number || result instanceof Boolean) {
                payload.put("result", result);
            } else {
                payload.put("resultType", result.getClass().getSimpleName());
            }
        } else {
            payload.put("result", null);
        }

        try {
            // 使用简单的 JSON 序列化
            return toJson(payload);
        } catch (Exception e) {
            return "{\"method\":\"" + method.getName() + "\"}";
        }
    }

    /**
     * 简单的 Map → JSON 序列化（避免依赖 Jackson ObjectMapper）。
     */
    @SuppressWarnings("unchecked")
    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(entry.getKey())).append("\":");
            Object value = entry.getValue();
            if (value == null) {
                sb.append("null");
            } else if (value instanceof String) {
                sb.append("\"").append(escape((String) value)).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else if (value instanceof Map) {
                sb.append(toJson((Map<String, Object>) value));
            } else {
                sb.append("\"").append(escape(value.toString())).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
