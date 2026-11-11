package com.chinacreator.gzcm.cli;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;

/**
 * 重试策略（详设 §16.6 四类故障区分 + §16.8 测试项）。
 *
 * <p>三条硬规则：</p>
 * <ol>
 *   <li><b>只重试幂等请求</b>：GET 与携带幂等键的写命令；无幂等键的写命令一律不重试
 *       （避免重复提交产生重复副作用）。</li>
 *   <li><b>只重试可恢复故障</b>：连接失败、超时、429、500/502/503/504；
 *       4xx（除 429）视为确定性拒绝，立即返回。</li>
 *   <li><b>429 尊重 Retry-After</b>：有该头则按其等待，且不计入退避上限。</li>
 * </ol>
 */
public final class RetryPolicy {

    public static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final long BASE_DELAY_MS = 200L;
    private static final long MAX_DELAY_MS = 4_000L;
    /** 设计册 §10.3.4：429 + 该码 ⇒ 预算耗尽，禁重试。 */
    private static final String BUDGET_EXHAUSTED = "E-WA-BUDGET";
    private static final java.util.regex.Pattern ERROR_CODE =
            java.util.regex.Pattern.compile("\"errorCode\"\\s*:\\s*\"([^\"]+)\"");

    private final int maxAttempts;

    public RetryPolicy(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts 必须 >= 1");
        }
        this.maxAttempts = maxAttempts;
    }

    public static RetryPolicy standard() {
        return new RetryPolicy(DEFAULT_MAX_ATTEMPTS);
    }

    public static RetryPolicy noRetry() {
        return new RetryPolicy(1);
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    /** 该次失败是否还值得再试（attempt 从 1 开始）。 */
    public boolean shouldRetry(int attempt, HttpTransport.Result result, boolean idempotent) {
        if (attempt >= maxAttempts) {
            return false;
        }
        if (!idempotent) {
            return false;
        }
        if (result.transportFailed()) {
            return true;
        }
        int status = result.status();
        // 设计册 §10.3.4 两张 429 语义相反：E-RATE 可退避重试，E-WA-BUDGET（Run 预算耗尽）
        // 明确「否」—— 预算已烧完，重试只会再打一次并继续消耗配额。
        if (status == 429 && isBudgetExhausted(result.body())) {
            return false;
        }
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    /** 预算耗尽判定：只看信封的 errorCode 字段，不做文本猜测。 */
    static boolean isBudgetExhausted(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        Matcher m = ERROR_CODE.matcher(body);
        return m.find() && BUDGET_EXHAUSTED.equalsIgnoreCase(m.group(1));
    }

    /** 计算本次退避等待；有 Retry-After 时优先采用（秒或 HTTP-date 简化为秒）。 */
    public Duration delayFor(int attempt, HttpTransport.Result result) {
        Long retryAfterSeconds = result.retryAfterSeconds();
        if (retryAfterSeconds != null && retryAfterSeconds >= 0) {
            return Duration.ofSeconds(Math.min(retryAfterSeconds, 60L));
        }
        long exp = BASE_DELAY_MS * (1L << Math.min(attempt - 1, 5));
        long capped = Math.min(exp, MAX_DELAY_MS);
        long jitter = ThreadLocalRandom.current().nextLong(capped / 2 + 1);
        return Duration.ofMillis(capped + jitter);
    }

    /** 供测试注入：跳过节流等待。 */
    void sleep(Duration d, boolean skip) {
        if (skip || d.isZero() || d.isNegative()) {
            return;
        }
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
