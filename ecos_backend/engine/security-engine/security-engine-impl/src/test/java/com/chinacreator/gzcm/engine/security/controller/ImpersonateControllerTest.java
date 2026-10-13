package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.security.service.SecurityAuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * ImpersonateControllerTest — 详设-01 F01-08 impersonate 二次确认门禁。
 *
 * <p>验证两条诚实路径：缺 confirm → 428 IMPERSONATE_CONFIRM_REQUIRED；
 * 带 confirm → 501 IMPERSONATE_CAPABILITY_NOT_AVAILABLE（能力底座缺失，
 * 显式声明不造假）。两条均强审计留痕 + traceId。</p>
 */
class ImpersonateControllerTest {

    private final SecurityAuditService auditService = mock(SecurityAuditService.class);
    private final ImpersonateController controller = new ImpersonateController(auditService);

    @Test
    @DisplayName("缺 confirm → 428 + IMPERSONATE_CONFIRM_REQUIRED + traceId + 审计留痕")
    void missingConfirmRejectedWith428() {
        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controller.impersonate(Map.of("targetUserId", "u-2001", "reason", "support"), null);

        assertEquals(HttpStatus.PRECONDITION_REQUIRED, resp.getStatusCode());
        ApiResponse<Map<String, Object>> body = resp.getBody();
        assertNotNull(body);
        assertEquals(ImpersonateController.ERROR_CONFIRM_REQUIRED, body.getErrorCode());
        assertEquals(428, body.getCode());
        assertNotNull(body.getTraceId(), "428 响应必须携带 traceId");

        ArgumentCaptor<String> resultCap = ArgumentCaptor.forClass(String.class);
        verify(auditService).asyncWrite(anyString(), any(), anyString(),
                eq("IMPERSONATE_REQUEST"), eq("user:u-2001"), resultCap.capture(), any(), any());
        assertEquals("REJECTED_CONFIRM_REQUIRED", resultCap.getValue());
    }

    @Test
    @DisplayName("confirm=true → 501 诚实呈现能力缺失（不伪造会话），绝不返回成功")
    void confirmedStillReturnsHonest501() {
        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controller.impersonate(Map.of("targetUserId", "u-2001", "reason", "support", "confirm", true), null);

        assertEquals(HttpStatus.NOT_IMPLEMENTED, resp.getStatusCode());
        ApiResponse<Map<String, Object>> body = resp.getBody();
        assertNotNull(body);
        assertEquals(ImpersonateController.ERROR_CAPABILITY_NOT_AVAILABLE, body.getErrorCode());
        assertEquals(501, body.getCode());
        assertNotNull(body.getTraceId(), "501 响应必须携带 traceId");
        assertNotNull(body.getMessage());

        verify(auditService).asyncWrite(anyString(), any(), anyString(),
                eq("IMPERSONATE_REQUEST"), eq("user:u-2001"), eq("NOT_IMPLEMENTED"), any(), any());
    }

    @Test
    @DisplayName("字符串 confirm=\"true\" 亦视为已确认（宽容解析，仍走 501 诚实路径）")
    void stringTrueConfirmAccepted() {
        ResponseEntity<ApiResponse<Map<String, Object>>> resp = controller.impersonate(
                Map.of("targetUserId", "u-9", "confirm", "true"), null);
        assertEquals(HttpStatus.NOT_IMPLEMENTED, resp.getStatusCode());
    }

    @Test
    @DisplayName("缺 targetUserId → 400（不进入确认分支），仍审计留痕")
    void missingTargetUserIdRejected400() {
        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controller.impersonate(Map.of("confirm", true), null);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        verify(auditService).asyncWrite(anyString(), any(), anyString(),
                eq("IMPERSONATE_REQUEST"), any(), eq("REJECTED_BAD_REQUEST"), any(), any());
    }

    @Test
    @DisplayName("空 body 不抛异常：400 + 审计（fail-closed）")
    void nullBodyFailsClosed() {
        assertDoesNotThrow(() -> {
            ResponseEntity<ApiResponse<Map<String, Object>>> resp = controller.impersonate(null, null);
            assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        });
    }
}
