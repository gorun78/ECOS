package com.chinacreator.gzcm.engine.security.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.engine.EngineStatus;
import com.chinacreator.gzcm.engine.security.SecurityEngineImpl;
import com.chinacreator.gzcm.engine.security.service.SecurityAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 安全引擎小控制器 wire 键面保真门（#147CD · DoD DI-2 第八批）。
 *
 * <p><b>钉的是什么</b>：{@code /status|/start|/stop} 两键 {@code name/status}（键序）；
 * 审计写入口回执三键 {@code id/status/action}，请求 7 字段可缺省、仅 {@code action} 必填；
 * 异步写调用的 8 参顺序与旧 Map 读取逐字段一致（含可缺省字段落 null）。</p>
 *
 * <p><b>变异自证</b>：把 {@code SecurityStatusVO} 的 {@code status} 组件改名 ⇒ 键集断言红；
 * 把 audit 必填判据删掉 ⇒ 空 action 用例红。</p>
 */
class SecuritySmallControllerWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return MAPPER.convertValue(value, Map.class);
    }

    private static List<String> keysOf(Object value) {
        return new ArrayList<>(asMap(value).keySet());
    }

    private static SecurityEngineStatusController statusController() {
        SecurityEngineImpl engine = mock(SecurityEngineImpl.class);
        when(engine.getName()).thenReturn("security-engine");
        when(engine.getStatus()).thenReturn(EngineStatus.RUNNING);
        SecurityEngineStatusController controller = new SecurityEngineStatusController();
        ReflectionTestUtils.setField(controller, "engine", engine);
        return controller;
    }

    @Test
    @DisplayName("status/start/stop 键面 = name/status（键序）")
    void statusFacesKeepTwoKeys() {
        SecurityEngineStatusController controller = statusController();

        assertEquals(List.of("name", "status"), keysOf(controller.status().getData()));
        assertEquals(List.of("name", "status"), keysOf(controller.start().getData()));
        assertEquals(List.of("name", "status"), keysOf(controller.stop().getData()));
        assertEquals("security-engine", controller.status().getData().name());
        assertEquals("RUNNING", controller.status().getData().status());
    }

    @Test
    @DisplayName("审计回执 = id/status/action；7 字段按旧口径透传（缺省落 null）")
    void auditAcceptedKeepsKeysAndPassesFields() throws Exception {
        SecurityAuditService service = mock(SecurityAuditService.class);
        SecurityAuditController controller = new SecurityAuditController(service);

        SecurityAuditController.SecurityAuditLogRequest request = MAPPER.readValue(
                "{\"userId\":\"u1\",\"username\":\"alice\",\"action\":\"login\","
                        + "\"resource\":\"session\",\"result\":\"OK\",\"detail\":\"d\",\"ipAddress\":\"1.1.1.1\"}",
                SecurityAuditController.SecurityAuditLogRequest.class);
        ApiResponse<SecurityAuditController.SecurityAuditAcceptedVO> resp = controller.log(request);

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode());
        assertEquals(List.of("id", "status", "action"), keysOf(resp.getData()));
        assertEquals("accepted", resp.getData().status());
        assertEquals("login", resp.getData().action());
        assertNotNull(resp.getData().id());

        verify(service).asyncWrite(anyString(), eq("u1"), eq("alice"), eq("login"),
                eq("session"), eq("OK"), eq("d"), eq("1.1.1.1"));
    }

    @Test
    @DisplayName("审计缺省字段：只给 action 时其余一律 null（不得凭空补值）")
    void auditDefaultsAreNull() throws Exception {
        SecurityAuditService service = mock(SecurityAuditService.class);
        SecurityAuditController controller = new SecurityAuditController(service);

        SecurityAuditController.SecurityAuditLogRequest request = MAPPER.readValue(
                "{\"action\":\"logout\"}", SecurityAuditController.SecurityAuditLogRequest.class);
        assertEquals(ApiResponse.CODE_SUCCESS, controller.log(request).getCode());

        verify(service).asyncWrite(anyString(), isNull(), isNull(), eq("logout"),
                isNull(), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("空 action 仍 400（DTO 化不得丢校验），且不触达异步写")
    void blankActionRejected() throws Exception {
        SecurityAuditService service = mock(SecurityAuditService.class);
        SecurityAuditController controller = new SecurityAuditController(service);

        SecurityAuditController.SecurityAuditLogRequest request = MAPPER.readValue(
                "{\"action\":\"  \"}", SecurityAuditController.SecurityAuditLogRequest.class);
        ApiResponse<?> resp = controller.log(request);
        assertEquals(400, resp.getCode());
        assertTrue(resp.getMessage() != null);
        assertFalse(resp.isSuccess());
        verify(service, org.mockito.Mockito.never()).asyncWrite(anyString(), any(), any(), anyString(),
                any(), any(), any(), any());
    }
}
