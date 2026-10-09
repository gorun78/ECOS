package com.chinacreator.gzcm.common.error;

/**
 * 智能域（ai-engine / llm-gateway）错误分类冻结表 —— 详细设计-06 §5.5（D.5 码表）
 * 与 PRD-06 §5.1 七类矩阵一一对应。
 *
 * <p>每个分类必须同时承载 PRD §5.1 的四元组：<b>重试上限 / 重试形态 / 用户可读消息模板 / 日志级别</b>，
 * 缺任一元组即视为矩阵未落地（验收 {@code AiErrorMatrixTest#sevenClassesEachCarryTemplateRetryBoundAndLevel}）。</p>
 *
 * <p>纪律：</p>
 * <ul>
 *   <li>{@code ECOS-AI-*} 字面量与消息模板为冻结值，禁改写（前端 i18n 与诊断码对齐依赖）；</li>
 *   <li>消息模板必须含「诊断码 {traceId}」，且 traceId 与 MDC 同源（F06-06/F06-14 要点 4）；</li>
 *   <li>响应体与 SSE {@code error} 帧永不携带异常原文（X-49）；原文只入日志；</li>
 *   <li>降备用 provider 只发生在 llm-gateway 内部路由，引擎侧不得绕行（F06-14 要点 2）。</li>
 * </ul>
 */
public enum AiErrorClass {

    // ══════════════ PRD-06 §5.1 七类 ══════════════

    /** 504 — llm-gateway 超时；指数退避 ≤3 次。 */
    LLM_TIMEOUT("ECOS-AI-504", 504, 3, RetryKind.EXPONENTIAL_BACKOFF, false,
            "AI 服务响应超时，请稍后重试（诊断码 {traceId}）", Level.WARN, false, false, true),

    /** 429 — provider 限流；退避 ≤3 次后按配置降级备用 provider（仅 llm-gateway 内部路由）。 */
    LLM_RATE_LIMITED("ECOS-AI-429", 429, 3, RetryKind.EXPONENTIAL_BACKOFF, true,
            "AI 服务繁忙，请稍后重试（诊断码 {traceId}）", Level.WARN, false, false, true),

    /** 500 — LLM 输出解析失败；重生成 ≤2 次（与 F06-12 引用重生成共用配额，不叠加）。 */
    PARSE_FAILURE("ECOS-AI-500P", 500, 2, RetryKind.REGENERATE, false,
            "AI 输出格式异常，已重试（诊断码 {traceId}）", Level.ERROR, false, false, true),

    /** 413 — 超模型上限且压缩失败；不重试，走截断降级（F06-09）。 */
    CONTEXT_OVERFLOW("ECOS-AI-413", 413, 0, RetryKind.TRUNCATE_DEGRADE, false,
            "会话上下文超出模型上限，已降级为摘要模式（诊断码 {traceId}）", Level.WARN, false, false, false),

    /** 403 — 护栏/ABAC 明确 DENY；不重试；不泄露资源细节；必须审计。 */
    GUARDRAIL_DENIED("ECOS-AI-403", 403, 0, RetryKind.NONE, false,
            "当前权限不支持该操作（诊断码 {traceId}）", Level.INFO, true, false, false),

    /** 503 — 护栏不可用（超时/异常/非明确 ALLOW 三态）；不重试；审计 + monitor 告警。 */
    GUARDRAIL_FAIL_CLOSED("ECOS-AI-503", 503, 0, RetryKind.NONE, false,
            "安全服务暂不可用，AI 操作已暂停（诊断码 {traceId}）", Level.ERROR, true, true, false),

    /** 502 — 工具下游异常；≤1 次；执行器未就绪同样归此类（禁 mock-success，X-50）。 */
    TOOL_BACKEND_ERROR("ECOS-AI-502", 502, 1, RetryKind.FIXED_BACKOFF, false,
            "数据服务异常（{service}），请稍后重试（诊断码 {traceId}）", Level.ERROR, false, false, true),

    // ══════════════ D.5 码表补充类（非七类，同表登记）══════════════

    /** 400 — 参数非法 / 工具未注册。 */
    INVALID_ARGUMENT("ECOS-AI-400", 400, 0, RetryKind.NONE, false,
            "请求参数不合法（诊断码 {traceId}）", Level.WARN, false, false, false),

    /** 400 — 口径缺失或未批准（F06-13 澄清分支：只提问，不给数）。 */
    CALIBER_CLARIFICATION("ECOS-AI-400C", 400, 0, RetryKind.NONE, false,
            "缺少已批准的统计口径，请先确认口径（诊断码 {traceId}）", Level.INFO, false, false, false),

    /** 422 — 引用缺失（F06-12 后校验兜底；重生成 ≤2 已内含于 PARSE_FAILURE 配额）。 */
    CITATION_MISSING("ECOS-AI-422", 422, 0, RetryKind.NONE, false,
            "无法提供有依据的数值（诊断码 {traceId}）", Level.WARN, false, false, false),

    /** 404 — 会话 / 运行不存在（真实 404，禁 Exception→404 掩蔽）。 */
    NOT_FOUND("ECOS-AI-404", 404, 0, RetryKind.NONE, false,
            "会话或运行记录不存在（诊断码 {traceId}）", Level.INFO, false, false, false),

    /** 501 — 显式 stub（响应必须带 {@code stub=true}，禁 200 空 body 伪成功）。 */
    STUB_CAPABILITY("ECOS-AI-501", 501, 0, RetryKind.NONE, false,
            "该能力尚未上线（诊断码 {traceId}）", Level.INFO, false, false, false);

    /** PRD §5.1 七类（矩阵测试的枚举基准，顺序即 PRD 表序）。 */
    public static final AiErrorClass[] SEVEN_CLASSES = {
            LLM_TIMEOUT, LLM_RATE_LIMITED, PARSE_FAILURE, CONTEXT_OVERFLOW,
            GUARDRAIL_DENIED, GUARDRAIL_FAIL_CLOSED, TOOL_BACKEND_ERROR
    };

    public enum RetryKind {
        /** 不重试 */
        NONE,
        /** 指数退避 */
        EXPONENTIAL_BACKOFF,
        /** 固定退避 */
        FIXED_BACKOFF,
        /** 重生成（LLM 重新产出，与引用重生成共用配额） */
        REGENERATE,
        /** 截断降级（非重试，属降级路径） */
        TRUNCATE_DEGRADE
    }

    /** 避免 common-api 依赖 slf4j 的自有级别常量（映射到 logback 行）。 */
    public enum Level {
        TRACE, DEBUG, INFO, WARN, ERROR
    }

    private static final int MAX_RETRY_BOUND = 3;

    private final String errorCode;
    private final int httpStatus;
    private final int maxRetries;
    private final RetryKind retryKind;
    private final boolean fallbackProviderAllowed;
    private final String messageTemplate;
    private final Level logLevel;
    private final boolean audited;
    private final boolean monitorAlerted;
    private final boolean retryable;

    AiErrorClass(String errorCode, int httpStatus, int maxRetries, RetryKind retryKind,
                 boolean fallbackProviderAllowed, String messageTemplate, Level logLevel,
                 boolean audited, boolean monitorAlerted, boolean retryable) {
        if (maxRetries > MAX_RETRY_BOUND) {
            throw new IllegalStateException("重试上限不得超过 PRD §5.1 最大界 3: " + errorCode);
        }
        if (retryKind == RetryKind.NONE && maxRetries != 0) {
            throw new IllegalStateException("RetryKind.NONE 必须 0 次重试: " + errorCode);
        }
        if (!messageTemplate.contains("{traceId}")) {
            throw new IllegalStateException("消息模板必须含诊断码 {traceId}: " + errorCode);
        }
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.maxRetries = maxRetries;
        this.retryKind = retryKind;
        this.fallbackProviderAllowed = fallbackProviderAllowed;
        this.messageTemplate = messageTemplate;
        this.logLevel = logLevel;
        this.audited = audited;
        this.monitorAlerted = monitorAlerted;
        this.retryable = retryable;
    }

    public String errorCode() {
        return errorCode;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public RetryKind retryKind() {
        return retryKind;
    }

    /** 备用 provider 降级开关 —— 仅 {@code LLM_RATE_LIMITED} 为 true，且只能由 llm-gateway 内部路由消费。 */
    public boolean fallbackProviderAllowed() {
        return fallbackProviderAllowed;
    }

    public String messageTemplate() {
        return messageTemplate;
    }

    public Level logLevel() {
        return logLevel;
    }

    /** 该分类必须写护栏/错误审计事件（铁律 §2.4-5）。 */
    public boolean audited() {
        return audited;
    }

    /** 该分类必须同时上抛 runtime-monitor 告警。 */
    public boolean monitorAlerted() {
        return monitorAlerted;
    }

    public boolean retryable() {
        return retryable;
    }

    /** 本次尝试是否仍可继续：{@code attemptMade} 为已消耗的重试次数。 */
    public boolean canRetry(int attemptMade) {
        return retryable && attemptMade < maxRetries;
    }

    /** 退避毫秒（指数：500→1000→2000；固定：800）。 */
    public long backoffMillis(int attemptMade) {
        return switch (retryKind) {
            case EXPONENTIAL_BACKOFF -> 500L << Math.max(0, Math.min(attemptMade, 3));
            case FIXED_BACKOFF, REGENERATE -> 800L;
            case NONE, TRUNCATE_DEGRADE -> 0L;
        };
    }

    /** 渲染用户可读消息；{@code traceId} 缺失以 {@code -} 占位，禁留花括号。 */
    public String renderMessage(String traceId) {
        return renderMessage(traceId, null);
    }

    /**
     * 渲染用户可读消息。
     *
     * @param traceId MDC traceId（与诊断码同源）
     * @param service 下游服务名（仅 {@code TOOL_BACKEND_ERROR} 的 {service} 占位消费）
     */
    public String renderMessage(String traceId, String service) {
        String tid = (traceId == null || traceId.isBlank()) ? "-" : traceId;
        String svc = (service == null || service.isBlank()) ? "-" : service;
        return messageTemplate.replace("{traceId}", tid).replace("{service}", svc);
    }
}
