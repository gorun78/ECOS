package com.chinacreator.gzcm.engine.ai.oag;

import com.chinacreator.gzcm.common.security.caller.CallerCredentialSource;
import com.chinacreator.gzcm.common.security.caller.CallerCredentialSource.Carrier;
import com.chinacreator.gzcm.common.security.header.HeaderAuthInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * D148 入口驱动用例：SSE 承流腿的身份必须活着进池线程。
 *
 * <p><b>缺陷形态（2026-11-16 活体实证）</b>：{@code chatStream} 把管道丢进
 * {@code CompletableFuture.runAsync}，池线程没有 {@code RequestContextHolder}；
 * 管道节点里出站 {@code POST /api/v1/security/rls/apply} 于是不带
 * {@code X-ECOS-USER}/{@code X-ECOS-TENANT}，security-engine 的
 * {@code HeaderAuthInterceptor} 回 <b>403 ECOS-AUTH-010</b>，而 403 又被
 * {@code AiSecurityEngineClient} 折成 {@code RLS_SERVICE_UNAVAILABLE} ——
 * 界面把"没有身份"报成"安全服务挂了"。同一 body 走非流式 {@code /chat}
 * 在请求线程上执行，如实回 {@code RLS_NO_POLICY_MATCHED}，两腿就此分叉。</p>
 *
 * <p><b>为什么必须是入口级</b>：同族 {@code OagSseChannelSequenceTest} 直接调
 * {@code streamChannel}（已在调用者线程上），对"凭证没跨过线程"完全无感 ——
 * 这个缺陷正是在它眼皮底下活了一整轮。本类只断 {@code chatStream} 这一面。</p>
 */
@DisplayName("D148 入口驱动：SSE 承流腿的调用方身份跨线程存活")
class OagChatStreamCallerIdentityTest {

    private OagController controller;
    private OagPipelineEngine engine;

    @BeforeEach
    void setUp() {
        controller = new OagController();
        engine = mock(OagPipelineEngine.class);
        ReflectionTestUtils.setField(controller, "pipelineEngine", engine);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** 绑定一张带信任头的入站请求（真实入站：JWT + gateway 注入属性）。 */
    private static void bindInboundRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer signed.jwt.value");
        request.addHeader(HeaderAuthInterceptor.H_USER, "u-1001");
        request.addHeader(HeaderAuthInterceptor.H_TENANT, "tenant-A");
        request.setAttribute("ECOS_INJECTED_USER", "u-1001");
        request.setAttribute("ECOS_INJECTED_TENANT", "tenant-A");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static Map<String, Object> body(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("userId", "u-1");
        body.put("tenantId", "t-1");
        return body;
    }

    @Test
    @DisplayName("池线程里 CallerCredentialSource.current() 必须取到请求线程的入站身份")
    void poolThreadSeesTheInboundCaller() throws Exception {
        bindInboundRequest();

        AtomicReference<Carrier> seenOnWorker = new AtomicReference<>();
        AtomicReference<Thread> workerThread = new AtomicReference<>();
        AtomicBoolean ran = new AtomicBoolean(false);
        CountDownLatch finished = new CountDownLatch(1);

        when(engine.run(anyString(), any(), anyString(), anyString(), any())).thenAnswer(inv -> {
            workerThread.set(Thread.currentThread());
            seenOnWorker.set(CallerCredentialSource.current());
            ran.set(true);
            finished.countDown();
            OagPipelineContext ctx = new OagPipelineContext("u-1", "t-1");
            ctx.setStatus("COMPLETED");
            ctx.setSecurityPassed(true);
            ctx.setFinalResponse("ok");
            return ctx;
        });

        Thread requestThread = Thread.currentThread();
        SseEmitter emitter = controller.chatStream(body("查一下三季度营收"));

        assertTrue(finished.await(10, TimeUnit.SECONDS), "管道 10s 内未进入执行，用例无法判定");
        assertTrue(ran.get());
        assertNotEquals(requestThread, workerThread.get(),
                "管道必须真的在池线程上跑；若与测试线程同名，本用例对 D148 无感（等同直接调 streamChannel）");

        Carrier carrier = seenOnWorker.get();
        assertEquals("u-1001", carrier.user(),
                "池线程取不到 X-ECOS-USER ⇒ 出站 rls/apply 缺信任头 ⇒ 403 ECOS-AUTH-010 被折成"
                        + "「安全服务不可用」（D148 复现）");
        assertEquals("tenant-A", carrier.tenant(),
                "租户同理：缺 X-ECOS-TENANT 时 security-engine 无法绑定主体上下文");
        assertEquals("Bearer signed.jwt.value", carrier.authorization(),
                "Authorization 也要逐字中继，缺它则属主侧连验签都做不了");
        assertTrue(carrier.present(), "载体必须非空：空载体就是本缺陷");

        emitter.complete();
    }

    @Test
    @DisplayName("无入站身份时池线程取到 EMPTY，不得凭空造主语")
    void poolThreadStaysEmptyWithoutInboundIdentity() throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Carrier> seenOnWorker = new AtomicReference<>();

        when(engine.run(anyString(), any(), anyString(), anyString(), any())).thenAnswer(inv -> {
            seenOnWorker.set(CallerCredentialSource.current());
            finished.countDown();
            OagPipelineContext ctx = new OagPipelineContext("u-1", "t-1");
            ctx.setStatus("COMPLETED");
            ctx.setSecurityPassed(true);
            ctx.setFinalResponse("ok");
            return ctx;
        });

        SseEmitter emitter = controller.chatStream(body("无身份请求"));

        assertTrue(finished.await(10, TimeUnit.SECONDS), "管道 10s 内未进入执行，用例无法判定");
        assertFalse(seenOnWorker.get().present(),
                "请求本身没带身份时，池线程也必须取到 EMPTY——兜一个默认主语就是把 plumbing 缺口变成越权");
        emitter.complete();
    }

    @Test
    @DisplayName("作用域退出后池线程不残留：第二个无身份请求不得继承第一个的身份")
    void secondRequestOnReusedWorkerDoesNotInheritTheFirstCaller() throws Exception {
        bindInboundRequest();

        AtomicReference<Carrier> first = new AtomicReference<>();
        AtomicReference<Carrier> second = new AtomicReference<>();
        CountDownLatch firstDone = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);

        when(engine.run(anyString(), any(), anyString(), anyString(), any()))
                .thenAnswer(inv -> {
                    first.set(CallerCredentialSource.current());
                    firstDone.countDown();
                    return doneCtx();
                })
                .thenAnswer(inv -> {
                    second.set(CallerCredentialSource.current());
                    secondDone.countDown();
                    return doneCtx();
                });

        SseEmitter e1 = controller.chatStream(body("带身份"));
        assertTrue(firstDone.await(10, TimeUnit.SECONDS), "首个请求 10s 内未执行");
        e1.complete();

        // 第二个请求：入站无身份（模拟同一池线程被复用）
        RequestContextHolder.resetRequestAttributes();
        SseEmitter e2 = controller.chatStream(body("无身份"));
        assertTrue(secondDone.await(10, TimeUnit.SECONDS), "第二个请求 10s 内未执行");
        e2.complete();

        assertEquals("u-1001", first.get().user(), "首个请求必须拿到自己的身份");
        assertFalse(second.get().present(),
                "第二个请求继承了第一位调用方的身份 ⇒ 池线程串号（InheritableThreadLocal 的典型事故）");
    }

    private static OagPipelineContext doneCtx() {
        OagPipelineContext ctx = new OagPipelineContext("u-1", "t-1");
        ctx.setStatus("COMPLETED");
        ctx.setSecurityPassed(true);
        ctx.setFinalResponse("ok");
        return ctx;
    }
}
