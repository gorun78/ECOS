package com.chinacreator.gzcm.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 批 A 硬化：重试/幂等/分页/脱敏/错误码单源。 */
class HardeningTest {

    private static HttpTransport.Result ok() {
        return new HttpTransport.Result(200, "{}", null, null);
    }

    @Test
    @DisplayName("重试：幂等 GET 遇 503 可重试，4xx 不重试，写命令无幂等键不重试")
    void retryDecisions() {
        RetryPolicy p = RetryPolicy.standard();
        assertTrue(p.shouldRetry(1, new HttpTransport.Result(503, null, null, null), true));
        assertTrue(p.shouldRetry(1, new HttpTransport.Result(429, null, null, null), true));
        assertTrue(p.shouldRetry(1, new HttpTransport.Result(-1, null, null, "conn reset"), true));
        assertFalse(p.shouldRetry(1, new HttpTransport.Result(400, null, null, null), true));
        assertFalse(p.shouldRetry(1, new HttpTransport.Result(404, null, null, null), true));
        assertFalse(p.shouldRetry(1, new HttpTransport.Result(503, null, null, null), false),
                "写命令无幂等键必须不重试");
        assertFalse(p.shouldRetry(3, new HttpTransport.Result(503, null, null, null), true),
                "达到上限后不得再重试");
    }

    @Test
    @DisplayName("重试：429 的 Retry-After 优先于指数退避且封顶 60s")
    void retryAfterHonoured() {
        RetryPolicy p = RetryPolicy.standard();
        Duration d = p.delayFor(1, new HttpTransport.Result(429, null, null, null, 7L));
        assertEquals(Duration.ofSeconds(7), d);
        Duration capped = p.delayFor(1, new HttpTransport.Result(429, null, null, null, 9_999L));
        assertEquals(Duration.ofSeconds(60), capped);
    }

    @Test
    @DisplayName("重试：429 分两种语义 —— E-RATE 可退避重试，E-WA-BUDGET（预算耗尽）禁重试")
    void budgetExhausted429IsNotRetried() {
        RetryPolicy p = RetryPolicy.standard();
        HttpTransport.Result rate = new HttpTransport.Result(429,
                "{\"code\":429,\"errorCode\":\"E-RATE\",\"message\":\"配额超限\"}", null, null);
        assertTrue(p.shouldRetry(1, rate, true), "E-RATE 应按退避重试（设计册 §10.3.4 重试=是）");

        HttpTransport.Result budget = new HttpTransport.Result(429,
                "{\"code\":429,\"errorCode\":\"E-WA-BUDGET\",\"message\":\"预算耗尽\"}", null, null);
        assertFalse(p.shouldRetry(1, budget, true),
                "E-WA-BUDGET 重试=s 否，预算已烧完，再打一次只会继续消耗配额");
        assertFalse(p.shouldRetry(1, budget, false), "写命令本就不重试");

        // 无信封/无 errorCode 的 429 仍按可恢复处理（不猜语义）
        assertTrue(p.shouldRetry(1, new HttpTransport.Result(429, null, null, null), true));
        // 其余码即使文本里出现 E-WA-BUDGET 也不影响判定（只看 errorCode 字段）
        assertTrue(p.shouldRetry(1, new HttpTransport.Result(503,
                "{\"code\":503,\"errorCode\":\"E-WA-DEP\",\"message\":\"依赖不可用\"}", null, null), true));
    }

    @Test
    @DisplayName("重试：退避有上限且带抖动（不产生大于上限+半区的等待）")
    void backoffBounded() {
        RetryPolicy p = RetryPolicy.standard();
        for (int attempt = 1; attempt <= 5; attempt++) {
            long ms = p.delayFor(attempt, new HttpTransport.Result(503, null, null, null)).toMillis();
            assertTrue(ms >= 0 && ms <= 6_000, "退避应落在 [0, 6s]：实得 " + ms);
        }
    }

    @Test
    @DisplayName("幂等键：缺省生成 cli- 前缀；非法字符与超长均 USAGE，防头部注入")
    void idempotencyKeys() {
        assertTrue(IdempotencyKeys.resolve(null).startsWith("cli-"));
        assertTrue(IdempotencyKeys.resolve("  ").startsWith("cli-"));
        assertEquals("run-2026:a.b_c", IdempotencyKeys.resolve("run-2026:a.b_c"));
        assertEquals(ExitCodes.USAGE, assertThrows(CliException.class,
                () -> IdempotencyKeys.resolve("bad key")).exitCode());
        assertEquals(ExitCodes.USAGE, assertThrows(CliException.class,
                () -> IdempotencyKeys.resolve("x".repeat(129))).exitCode());
    }

    @Test
    @DisplayName("分页：--all 遍历到短页即止，单页模式标记 truncated")
    void pageWalker() {
        PageWalker.PageResult all = PageWalker.collect(2, 10, true, (offset, limit) -> {
            int from = offset;
            int count = from >= 4 ? 1 : 2;
            StringBuilder sb = new StringBuilder("{\"code\":0,\"data\":[");
            for (int i = 0; i < count; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append("{\"i\":").append(from + i).append('}');
            }
            return ok2(sb.append("]}").toString());
        });
        assertEquals(5, all.rows().size());
        assertEquals(3, all.pagesFetched());
        assertFalse(all.truncated());

        PageWalker.PageResult single = PageWalker.collect(2, 10, false, (offset, limit) ->
                ok2("{\"code\":0,\"data\":[{\"i\":0},{\"i\":1}]}"));
        assertEquals(2, single.rows().size());
        assertTrue(single.truncated(), "单页模式必须显式标记未取全");
    }

    @Test
    @DisplayName("分页：页大小上限 500，越界 USAGE")
    void pageSizeBounds() {
        assertEquals(500, PageWalker.pageSize(500, 20));
        assertEquals(ExitCodes.USAGE, assertThrows(CliException.class,
                () -> PageWalker.pageSize(501, 20)).exitCode());
        assertEquals(ExitCodes.USAGE, assertThrows(CliException.class,
                () -> PageWalker.pageSize(0, 20)).exitCode());
    }

    @Test
    @DisplayName("脱敏加固：JWT 片段、URL query token、apiKey/password 均不得回显")
    void redactorHardened() {
        String jwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImV4cCI6MX0.c2lnbmF0dXJlLXZhbHVl";
        String s = Redactor.redact("token=" + jwt
                + " url=/api/x?access_token=abc123&keep=1"
                + " {\"password\":\"p@ss\",\"apiKey\":\"k-999\"}");
        assertFalse(s.contains(jwt), "JWT 必须被脱敏");
        assertFalse(s.contains("abc123"), "URL query token 必须被脱敏");
        assertFalse(s.contains("p@ss"), "password 必须被脱敏");
        assertFalse(s.contains("k-999"), "apiKey 必须被脱敏");
        assertTrue(s.contains("keep=1"), "非敏感 query 参数应保留，避免过度脱敏破坏排障");
    }

    @Test
    @DisplayName("错误码单源：HTTP 码带状态后缀，核心码非空且互不重复")
    void errorCodeSingleSource() {
        assertEquals("ECOS-CLI-HTTP-418", CliErrorCodes.http(418));
        var codes = new java.util.HashSet<String>(java.util.List.of(
                CliErrorCodes.AUTH, CliErrorCodes.FORBIDDEN, CliErrorCodes.UNAVAILABLE,
                CliErrorCodes.DEP_UNAVAILABLE, CliErrorCodes.RESPONSE, CliErrorCodes.BLOCKED,
                CliErrorCodes.FAILED, CliErrorCodes.PARTIAL, CliErrorCodes.INTERNAL,
                CliErrorCodes.USAGE, CliErrorCodes.DRY_RUN, CliErrorCodes.DOCTOR,
                CliErrorCodes.NOT_IMPLEMENTED, CliErrorCodes.CREDENTIALS, CliErrorCodes.RETRY_EXHAUSTED));
        assertEquals(15, codes.size(), "错误码不得重复");
        assertTrue(codes.stream().allMatch(c -> c.startsWith("ECOS-CLI-")));
    }

    @Test
    @DisplayName("配置分层：flag > env > 默认；--show-config 不含 token 明文")
    void configLayering() {
        CliOptions o = CliOptions.parse(new String[] {"doctor", "--base-url", "http://localhost:8080/x"}, 1);
        assertEquals("http://localhost:8080/x", o.baseUrl());
        var cfg = o.effectiveConfig();
        assertTrue(cfg.containsKey("tokenConfigured"));
        assertFalse(String.valueOf(cfg).contains("token="), "配置快照不得含 token 明文");
    }

    @Test
    @DisplayName("trace/幂等键：同一实例反复取值必须相等，跨实例才应不同（禁每次调用重生成）")
    void traceAndIdempotencyAreStablePerRun() {
        CliOptions a = CliOptions.parse(new String[] {"doctor"}, 1);
        assertEquals(a.traceId(), a.traceId(), "同一次运行内 trace 必须稳定");
        assertEquals(a.idempotencyKey(), a.idempotencyKey(), "头与回显必须用同一幂等键");

        CliOptions b = CliOptions.parse(new String[] {"doctor"}, 1);
        assertFalse(a.traceId().equals(b.traceId()), "不同运行应各自生成 trace");
        assertFalse(a.idempotencyKey().equals(b.idempotencyKey()), "不同运行应各自生成幂等键");

        CliOptions pinned = CliOptions.parse(
                new String[] {"doctor", "--trace-id", "cli-fixed", "--idempotency-key", "run-1"}, 1);
        assertEquals("cli-fixed", pinned.traceId());
        assertEquals("run-1", pinned.idempotencyKey());
    }

    private static HttpTransport.Result ok2(String body) {
        return new HttpTransport.Result(200, body, null, null);
    }
}
