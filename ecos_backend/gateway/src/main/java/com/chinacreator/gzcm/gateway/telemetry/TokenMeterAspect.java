package com.chinacreator.gzcm.gateway.telemetry;

import com.chinacreator.gzcm.common.annotation.TokenMeter;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/**
 * P2-13 TokenMeterAspect — @TokenMeter 注解的 AOP 切面。
 * <p>
 * 拦截被 @TokenMeter 标记的方法，在方法执行完成后自动记录 Token 用量到审计表。
 * 使用反射从返回值中提取 sessionId、tokensInput、tokensOutput 字段，兼容
 * hermes-engine 和 runtime-core 的 AgentResult 类（避免模块间耦合）。
 * </p>
 */
@Aspect
@Component
public class TokenMeterAspect {

    private static final Logger log = LoggerFactory.getLogger(TokenMeterAspect.class);

    private final TokenAuditService auditService;

    public TokenMeterAspect(TokenAuditService auditService) {
        this.auditService = auditService;
    }

    @Pointcut("@annotation(com.chinacreator.gzcm.common.annotation.TokenMeter)")
    public void tokenMeterAnnotated() {
    }

    @Around("tokenMeterAnnotated()")
    public Object aroundTokenMeter(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        TokenMeter annotation = method.getAnnotation(TokenMeter.class);

        String operation = annotation.operation();
        if (operation == null || operation.isEmpty()) {
            operation = "llm_call";
        }
        String modelExpr = annotation.model();
        String model = modelExpr != null && !modelExpr.isEmpty() ? modelExpr : "hermes-agent";

        long startTime = System.currentTimeMillis();

        // 执行目标方法
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            log.warn("@TokenMeter method {} threw exception: {}", method.getName(), t.getMessage());
            throw t;
        }

        long latencyMs = System.currentTimeMillis() - startTime;

        // 处理 CompletableFuture 返回值
        if (result instanceof CompletableFuture) {
            @SuppressWarnings("unchecked")
            CompletableFuture<?> future = (CompletableFuture<?>) result;
            final String op = operation;
            final String mdl = model;
            final long lat = latencyMs;
            future.thenAccept(innerResult -> recordByReflection(innerResult, op, mdl, lat));
            return result;
        }

        // 处理直接返回值
        if (result != null) {
            recordByReflection(result, operation, model, latencyMs);
        }

        return result;
    }

    /**
     * 通过反射从返回值中提取 Token 用量信息。
     * 兼容 hermes-engine 的 AgentResult (tokensInput/tokensOutput)
     * 和 runtime-core 的 AgentResult (totalTokens)。
     */
    private void recordByReflection(Object result, String operation, String model, long latencyMs) {
        try {
            Class<?> clazz = result.getClass();
            String sessionId = getStringField(result, clazz, "sessionId", "unknown");

            // 获取 tokensInput / tokensOutput（hermes-engine AgentResult）
            int promptTokens = getIntField(result, clazz, "tokensInput", 0);
            int completionTokens = getIntField(result, clazz, "tokensOutput", 0);

            // 如果 tokensInput 为 0，尝试 totalTokens（runtime-core AgentResult），此时均分
            if (promptTokens == 0 && completionTokens == 0) {
                int totalTokens = getIntField(result, clazz, "totalTokens", 0);
                if (totalTokens > 0) {
                    promptTokens = totalTokens / 2;
                    completionTokens = totalTokens - promptTokens;
                }
            }

            if (promptTokens > 0 || completionTokens > 0) {
                auditService.recordUsage(sessionId, model, promptTokens, completionTokens, operation, latencyMs);
                log.debug("TokenMeterAspect recorded: sessionId={}, model={}, prompt={}, completion={}, operation={}",
                        sessionId, model, promptTokens, completionTokens, operation);
            }
        } catch (Exception e) {
            log.warn("TokenMeterAspect failed to record usage via reflection: {}", e.getMessage());
        }
    }

    private String getStringField(Object obj, Class<?> clazz, String fieldName, String defaultValue) {
        try {
            // Try getter first
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            Method getter = clazz.getMethod(getterName);
            Object val = getter.invoke(obj);
            return val != null ? val.toString() : defaultValue;
        } catch (Exception e) {
            // Try direct field access
            try {
                java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object val = field.get(obj);
                return val != null ? val.toString() : defaultValue;
            } catch (Exception e2) {
                return defaultValue;
            }
        }
    }

    private int getIntField(Object obj, Class<?> clazz, String fieldName, int defaultValue) {
        try {
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            Method getter = clazz.getMethod(getterName);
            Object val = getter.invoke(obj);
            if (val instanceof Number) return ((Number) val).intValue();
        } catch (Exception e) {
            try {
                java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object val = field.get(obj);
                if (val instanceof Number) return ((Number) val).intValue();
            } catch (Exception e2) {
                // ignore
            }
        }
        return defaultValue;
    }
}
