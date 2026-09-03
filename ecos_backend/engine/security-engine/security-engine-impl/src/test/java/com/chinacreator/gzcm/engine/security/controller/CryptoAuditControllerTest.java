package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.audit.crypto.CryptoAuditLedger;
import com.chinacreator.gzcm.sysman.audit.crypto.CryptoAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CryptoAuditControllerTest — AES-256 加解密过程 aud 留痕。
 *
 * <p>Wave-5.1 T-05：直接构造真实 {@link CryptoAuditService} 与 controller，
 * 验证 record/list/get/verify 四步全留痕，且链式哈希完整。
 */
class CryptoAuditControllerTest {

    private CryptoAuditService auditService;
    private CryptoAuditController controller;

    @BeforeEach
    void setUp() {
        this.auditService = new CryptoAuditService();
        this.controller = new CryptoAuditController(auditService);
    }

    @Test
    @DisplayName("POST /record → GET /logs → GET /logs/{id} → GET /verify 四步全留痕")
    void encryptDecryptFullAuditTrail() {
        // 模拟 AES-256 encrypt 四步留痕
        CryptoAuditLedger e1 = new CryptoAuditLedger();
        e1.setEventType("AES_ENCRYPT");
        e1.setResource("secret-key-1");
        e1.setAction("encrypt");
        e1.setOperatorId("admin");
        e1.setPayload("{\"plaintext\":\"***\"}");
        CryptoAuditLedger saved1 = auditService.record(e1);
        assertNotNull(saved1.getId());

        CryptoAuditLedger e2 = new CryptoAuditLedger();
        e2.setEventType("AES_DECRYPT");
        e2.setResource("secret-key-1");
        e2.setAction("decrypt");
        e2.setOperatorId("admin");
        e2.setPayload("{\"ciphertext\":\"***\"}");
        CryptoAuditLedger saved2 = auditService.record(e2);
        assertNotNull(saved2.getId());
        // 链式：第二条 prevHash 应等于第一条 currHash
        assertEquals(saved1.getCurrentHash(), saved2.getPrevHash());

        // 再补两条使 total >= 4
        CryptoAuditLedger e3 = new CryptoAuditLedger();
        e3.setEventType("AES_ENCRYPT");
        e3.setResource("secret-key-2");
        e3.setAction("encrypt");
        e3.setOperatorId("service-account");
        e3.setPayload("{}");
        auditService.record(e3);

        CryptoAuditLedger e4 = new CryptoAuditLedger();
        e4.setEventType("KEY_ROTATE");
        e4.setResource("secret-key-1");
        e4.setAction("rotate");
        e4.setOperatorId("admin");
        e4.setPayload("{}");
        auditService.record(e4);

        // 1. record
        ApiResponse<Map<String, Object>> recordResp = controller.record(Map.of(
                "eventType", "AES_ENCRYPT",
                "resource", "secret-key-1",
                "action", "encrypt",
                "operatorId", "admin",
                "payload", "{}"));
        assertTrue(recordResp.isSuccess());

        // 2. list — keyword AES 仅过滤 eventType/ACTION/OPERATOR 含 "AES" 的记录
        //    前 4 条直接 record: AES_ENCRYPT/KEY_ROTATE"AES_DECRYPT" + AES_ENCRYPT
        //    加上 controller.record 的 AES_ENCRYPT，共 4 条 keyword 命中
        ApiResponse<Map<String, Object>> listResp = controller.list("AES", 1, 10);
        assertTrue(listResp.isSuccess());
        assertNotNull(listResp.getData());
        assertEquals(4, (int) listResp.getData().get("total"));

        // 3. get by id
        ApiResponse<?> getResp = controller.get(saved2.getId());
        assertTrue(getResp.isSuccess());

        // 4. verify
        ApiResponse<Map<String, Object>> verifyResp = controller.verify();
        assertTrue(verifyResp.isSuccess());
        Map<String, Object> verifyData = verifyResp.getData();
        assertEquals(5, (int) verifyData.get("total"));
        assertEquals(5, (int) verifyData.get("pass"));
        assertEquals(0, (int) verifyData.get("fail"));
        assertTrue((Boolean) verifyData.get("intact"));
    }

    @Test
    @DisplayName("异常路径 — record 抛错应返回 500 而非崩溃")
    void recordExceptionReturnsInternalError() {
        // 用一个总是在 record 时抛错的 service 模拟
        CryptoAuditService failing = new CryptoAuditService() {
            @Override
            public synchronized CryptoAuditLedger record(CryptoAuditLedger entry) {
                throw new RuntimeException("simulated DB down");
            }
        };
        CryptoAuditController failingController = new CryptoAuditController(failing);
        ApiResponse<Map<String, Object>> resp = failingController.record(Map.of("eventType", "X"));
        assertEquals(ApiResponse.CODE_INTERNAL_ERROR, resp.getCode());
        assertTrue(resp.getMessage().contains("记录失败"));
    }
}
