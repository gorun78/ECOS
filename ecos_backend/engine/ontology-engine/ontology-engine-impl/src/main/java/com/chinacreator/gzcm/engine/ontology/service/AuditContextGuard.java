package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * AuditContextGuard — Function 审计上下文守卫（W81/M0 · F03-06 / C64）。
 *
 * <p><b>问题背景</b>：旧实现 {@code callerId = req.getCallerId() != null ? ... : "anonymous"}
 * 兜底匿名，导致审计行无法归因（无操作人）；且 traceId 缺失无法做 REQ-NF-04 链路追踪。</p>
 *
 * <p><b>裁决</b>：审计上下文（操作人 + traceId）必须来自运行时安全上下文，缺失即拒收，
 * 不得兜底 anonymous。语义对齐 V172 列注释：</p>
 * <blockquote>
 *   caller_id — 只取 UserContext（无上下文 = 400 ECOS-ONTO-060 拒收，不落表）
 * </blockquote>
 *
 * <p>本类<b>只判定不响应</b>：缺上下文抛 {@link AuditContextMissingException}，
 * 由调用方（Controller）决定映射为 {@code 400 ECOS-ONTO-060}；不在此打 warn 日志。</p>
 */
@Component
public class AuditContextGuard {

    /** traceId 在 MDC 中的键（与 logback 埋点一致，见 task #14）。 */
    static final String MDC_TRACE_ID = "traceId";

    /**
     * 校验审计上下文完备（用户 + traceId 均非空）。
     *
     * @throws AuditContextMissingException 任一缺失时抛出，异常消息说明缺哪一项
     */
    public void require() {
        boolean opMissing = isBlank(UserContext.getCurrentUsername());
        boolean traceMissing = isBlank(MDC.get(MDC_TRACE_ID));
        if (opMissing && traceMissing) {
            throw new AuditContextMissingException("operator and traceId are both missing");
        }
        if (opMissing) {
            throw new AuditContextMissingException("operator (currentUsername) is missing");
        }
        if (traceMissing) {
            throw new AuditContextMissingException("traceId is missing");
        }
    }

    /** 返回操作人（应已由 {@link #require()} 校验非空）。 */
    public String resolveCallerId() {
        return UserContext.getCurrentUsername();
    }

    /** 返回链路 traceId（应已由 {@link #require()} 校验非空）。 */
    public String resolveTraceId() {
        return MDC.get(MDC_TRACE_ID);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 审计上下文缺失异常（OWA 嵌套公开静态类）。
     *
     * <p>Controller 捕获后映射 {@code 400 ECOS-ONTO-060}（对函数执行 = 拒收，不落无归因审计行）。</p>
     */
    public static class AuditContextMissingException extends RuntimeException {
        public AuditContextMissingException(String message) {
            super(message);
        }
    }
}
