package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.engine.security.service.AuditHashChainService;
import com.chinacreator.gzcm.sysman.audit.model.AuditEvent;
import com.chinacreator.gzcm.sysman.audit.service.IAuditLogService;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuditControllerIngestTest — 审计摄取端点的主体来源与回显清零。
 *
 * <p>对应 H10-T2 / T2-1c。本文件**不套 §9.87 的 Kafka 出口四件套**：
 * 该 Controller 自己就是审计写入面（实测落 td_audit_log），再加一条
 * ecos.audit 出口会形成自写自读的环。故本节只覆盖可安全落地的两点
 * （旁录已认证主体 + 异常细节不回显），主体信任模型本身待裁 Q15。
 */
@ExtendWith(MockitoExtension.class)
class AuditControllerIngestTest {

    @Mock
    private IAuditLogService auditLogService;

    @Mock
    private AuditHashChainService hashChainService;

    private AuditController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        // #142/B4-3e-56：本类改为 ObjectProvider 构造注入（未装配腿显式抛 503，见 AuditUnavailableCarrierTest）
        controller = new AuditController(availableProvider(auditLogService),
                availableProvider(hashChainService));
        request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("User-Agent", "ecos-gateway/1.0");
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> availableProvider(T instance) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        // setUp 里为两枚通道都装了桩，个别用例只走其中一枚 ⇒ 取 lenient 以免 strict-stubs 误报
        org.mockito.Mockito.lenient().when(provider.getIfAvailable()).thenReturn(instance);
        return provider;
    }

    /** 未装配通道：{@code getIfAvailable()} 返回 null，即线上「Bean 没装配」的读数。 */
    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> absentProvider() {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        org.mockito.Mockito.lenient().when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static void authenticate(String userId, String username) {
        UserContext context = new UserContext();
        context.setUserId(userId);
        context.setUsername(username);
        UserContext.setCurrent(context);
    }

    private static Map<String, Object> body(String userId, String action) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("action", action);
        body.put("resource", "dataset:1");
        body.put("result", "SUCCESS");
        body.put("detail", "d");
        return body;
    }

    private AuditEvent captureLoggedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogService).log(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("旁录的 ingestActor 取已认证上下文，非硬编码")
    void ingestActorComesFromAuthenticatedContext() {
        authenticate("u-21", "alice");
        assertEquals("u-21", controller.resolveIngestActor());
    }

    @Test
    @DisplayName("无上下文时 ingestActor=anonymous，不伪装 admin/system")
    void missingContextYieldsAnonymousIngestActor() {
        String actor = controller.resolveIngestActor();
        assertEquals(AuditController.ANONYMOUS_OPERATOR, actor);
        assertNotEquals("admin", actor);
        assertNotEquals("system", actor);
    }

    @Test
    @DisplayName("userId 空白时退回 username")
    void ingestActorFallsBackToUsername() {
        authenticate("  ", "bob");
        assertEquals("bob", controller.resolveIngestActor());
    }

    @Test
    @DisplayName("现状基线（待裁 Q15）：入库主体仍是调用方自报，IP/UA 由服务端取")
    void writeLogStoresClaimedSubjectButServerDerivedNetworkFacts() {
        authenticate("u-21", "alice");

        ApiResponse<Map<String, Object>> resp = controller.writeLog(body("claimed-attacker-id", "export"), request);

        assertTrue(resp.isSuccess());
        AuditEvent event = captureLoggedEvent();
        // 自报主体原样入库 ⇒ 可伪造面，Q15 裁决后本断言必须改为"以 token 主体覆盖"
        assertEquals("claimed-attacker-id", event.getUserId());
        // 网络事实不可由客户端供给
        assertEquals("203.0.113.9", event.getIpAddress());
        assertEquals("ecos-gateway/1.0", event.getUserAgent());
        assertNotNull(event.getEventId());
        assertNotNull(event.getTimestamp());
        assertEquals("export", event.getEventType());
    }

    @Test
    @DisplayName("额外请求体字段原样透传进 details（Q15 需一并收紧的写入面）")
    void extraBodyFieldsPassThroughIntoDetails() {
        Map<String, Object> body = body("u-1", "login");
        body.put("injectedKey", "injectedValue");

        controller.writeLog(body, request);

        AuditEvent event = captureLoggedEvent();
        Map<String, Object> details = event.getDetails();
        assertEquals("injectedValue", details.get("injectedKey"));
        assertEquals("d", details.get("detail"));
        assertFalse(details.containsKey("userId"));
    }

    @Test
    @DisplayName("写路径异常细节不回显客户端（N-25 本文件清零）")
    void writeFailureDoesNotLeakDetailToClient() {
        doThrow(new RuntimeException("jdbc:postgresql://10.0.0.7 password=Pa55"))
                .when(auditLogService).log(any());

        ApiResponse<Map<String, Object>> resp = controller.writeLog(body("u-1", "login"), request);

        assertFalse(resp.isSuccess());
        assertNotNull(resp.getMessage());
        assertFalse(resp.getMessage().contains("Pa55"));
        assertFalse(resp.getMessage().contains("jdbc"));
    }

    @Test
    @DisplayName("读路径（list/stats）异常细节不回显")
    void readFailuresDoNotLeakDetailToClient() {
        when(auditLogService.query(any()))
                .thenThrow(new RuntimeException("token=eyJhbGciOi  leak-marker"));

        assertFalse(controller.list(null, null, null, null, null, 1, 20).getMessage().contains("leak-marker"));

        ApiResponse<Map<String, Object>> stats = controller.stats();
        assertFalse(stats.isSuccess());
        assertFalse(stats.getMessage().contains("leak-marker"));
    }

    /**
     * #142/B4-3e-56 订正：原案断言「服务未就绪时 list 仍返回空集而非 500（降级契约不变）」，
     * 那条断言钉的正是差距 <b>D-B4-86</b> 本身——把「依赖没装配」谎报成「库里没有数据」，
     * 违详设 §4.7.2「不可用 ≠ 无权限 ≠ 空结果」。现按收口后的契约改钉显式不可用承载。
     */
    @Test
    @DisplayName("服务未装配时 list 抛 503＋ECOS-SEC-501，不再折成 200＋空集")
    void listFailsClosedWhenServiceMissing() {
        AuditController down = new AuditController(absentProvider(), absentProvider());

        DataBridgeException thrown = assertThrows(DataBridgeException.class,
                () -> down.list("u-1", null, null, null, null, 1, 20));

        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_HTTP, thrown.getHttpStatus());
        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_HTTP, thrown.getErrorCode());
        assertTrue(thrown.getMessage().startsWith(RlsExecutionContract.SEC_UNAVAILABLE_CODE + ":"),
                "消息首段必须是机读码 ECOS-SEC-501，实际：" + thrown.getMessage());
    }
}
