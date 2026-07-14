package com.chinacreator.gzcm.sysman;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.audit.crypto.CryptoAuditLedger;
import com.chinacreator.gzcm.sysman.audit.crypto.CryptoAuditService;
import com.chinacreator.gzcm.sysman.controller.CryptoAuditController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CryptoAuditController 单元测试 — 覆盖记录/查询/验证 4 个端点
 */
@ExtendWith(MockitoExtension.class)
class CryptoAuditControllerTest {

    @Mock
    private CryptoAuditService service;

    private CryptoAuditController controller;

    @BeforeEach
    void setUp() {
        controller = new CryptoAuditController(service);
    }

    // ── 辅助 ──

    private CryptoAuditLedger createLedger(String id, String eventType) {
        CryptoAuditLedger ledger = new CryptoAuditLedger();
        ledger.setId(id);
        ledger.setEventType(eventType);
        ledger.setResource("/api/test");
        ledger.setAction("READ");
        ledger.setOperatorId("admin");
        ledger.setPayload("{}");
        ledger.setCurrentHash("abc123def456");
        ledger.setTimestamp(System.currentTimeMillis());
        ledger.setVerified(true);
        return ledger;
    }

    // ═══════════════ 用例1: POST /record → 返回 currentHash ═══════════════════

    @Test
    void record_shouldReturnCurrentHash() {
        CryptoAuditLedger saved = createLedger("a1b2c3d4e5f6", "DATA_ACCESS");
        saved.setCurrentHash("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

        when(service.record(any(CryptoAuditLedger.class))).thenReturn(saved);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventType", "DATA_ACCESS");
        body.put("resource", "/api/v1/data");
        body.put("action", "READ");
        body.put("operatorId", "admin");
        body.put("payload", "{\"key\":\"value\"}");

        ApiResponse<Map<String, Object>> resp = controller.record(body);

        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData().get("currentHash"), "currentHash不能为空");
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            resp.getData().get("currentHash"));
        assertEquals("a1b2c3d4e5f6", resp.getData().get("id"));
        assertNotNull(resp.getData().get("timestamp"));
    }

    // ═══════════════ 用例2: GET /logs → 200, 分页数据 ═══════════════════

    @Test
    void list_shouldReturnPaginatedLogs() {
        List<CryptoAuditLedger> entries = List.of(
            createLedger("id-login", "LOGIN"),
            createLedger("id-logout", "LOGOUT")
        );
        when(service.list(null, 1, 20)).thenReturn(entries);
        when(service.count(null)).thenReturn(2);

        ApiResponse<Map<String, Object>> resp = controller.list(null, 1, 20);

        assertTrue(resp.isSuccess());
        assertEquals(2, resp.getData().get("total"));
        assertEquals(1, resp.getData().get("page"));
        assertEquals(20, resp.getData().get("pageSize"));

        @SuppressWarnings("unchecked")
        List<CryptoAuditLedger> data = (List<CryptoAuditLedger>) resp.getData().get("data");
        assertEquals(2, data.size());
        assertEquals("id-login", data.get(0).getId());
        assertEquals("LOGIN", data.get(0).getEventType());
    }

    // ═══════════════ 用例3: GET /verify → 200, intact=true ═══════════════════

    @Test
    void verify_shouldReturnIntactTrue() {
        Map<String, Object> verifyResult = new LinkedHashMap<>();
        verifyResult.put("total", 5);
        verifyResult.put("pass", 5);
        verifyResult.put("fail", 0);
        verifyResult.put("intact", true);
        verifyResult.put("tampered", List.of());

        when(service.chainVerify()).thenReturn(verifyResult);

        ApiResponse<Map<String, Object>> resp = controller.verify();

        assertTrue(resp.isSuccess());
        assertEquals(true, resp.getData().get("intact"));
        assertEquals(5, resp.getData().get("total"));
        assertEquals(5, resp.getData().get("pass"));
        assertEquals(0, resp.getData().get("fail"));
    }

    // ═══════════════ 用例4a: GET /logs/{id} → 200 (找到) ═══════════════════

    @Test
    void getById_shouldReturnLedger_whenFound() {
        CryptoAuditLedger entry = createLedger("id-config-change", "CONFIG_CHANGE");
        entry.setCurrentHash("hash-config-001");
        when(service.getById("id-config-change")).thenReturn(entry);

        ApiResponse<?> resp = controller.get("id-config-change");

        assertTrue(resp.isSuccess());
        CryptoAuditLedger data = (CryptoAuditLedger) resp.getData();
        assertEquals("id-config-change", data.getId());
        assertEquals("CONFIG_CHANGE", data.getEventType());
        assertEquals("hash-config-001", data.getCurrentHash());
    }

    // ═══════════════ 用例4b: GET /logs/{id} → 404 (不存在) ═══════════════════

    @Test
    void getById_shouldReturn404_whenNotFound() {
        when(service.getById("id-not-exist")).thenReturn(null);

        ApiResponse<?> resp = controller.get("id-not-exist");

        assertFalse(resp.isSuccess());
        assertTrue(resp.getMessage().contains("id-not-exist"),
            "错误消息应包含不存在的ID");
    }
}
