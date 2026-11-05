package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.audit.crypto.CryptoAuditLedger;
import com.chinacreator.gzcm.sysman.audit.crypto.CryptoAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CryptoAuditControllerTest — AES-256 加解密过程 aud 留痕。
 *
 * <p>Wave-5.1 T-05：直接构造真实 {@link CryptoAuditService} 与 controller，
 * 验证 record/list/get/verify 四步全留痕，且链式哈希完整。</p>
 *
 * <p>#147CH：载荷位改 record（{@code RecordResultVO}/{@code CryptoAuditPageVO}/
 * {@code ChainVerifyResult}），本类同步改用访问器 + 序列化键集断言（判据零放宽），
 * 请求体改 {@code CryptoAuditRecordRequest}。</p>
 */
class CryptoAuditControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CryptoAuditService auditService;
    private CryptoAuditController controller;

    @BeforeEach
    void setUp() {
        this.auditService = new CryptoAuditService();
        this.controller = new CryptoAuditController(auditService);
    }

    private static CryptoAuditController.CryptoAuditRecordRequest request(String eventType,
                                                                          String resource,
                                                                          String action,
                                                                          String operatorId,
                                                                          String payload) {
        CryptoAuditController.CryptoAuditRecordRequest req =
                new CryptoAuditController.CryptoAuditRecordRequest();
        req.setEventType(eventType);
        req.setResource(resource);
        req.setAction(action);
        req.setOperatorId(operatorId);
        req.setPayload(payload);
        return req;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return MAPPER.convertValue(value, Map.class);
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
        ApiResponse<CryptoAuditController.RecordResultVO> recordResp = controller.record(
                request("AES_ENCRYPT", "secret-key-1", "encrypt", "admin", "{}"));
        assertTrue(recordResp.isSuccess());
        assertEquals(Set.of("id", "currentHash", "timestamp"), asMap(recordResp.getData()).keySet());
        assertNotNull(recordResp.getData().id());

        // 2. list — keyword AES 仅过滤 eventType/ACTION/OPERATOR 含 "AES" 的记录（共 4 条）
        ApiResponse<CryptoAuditController.CryptoAuditPageVO> listResp = controller.list("AES", 1, 10);
        assertTrue(listResp.isSuccess());
        assertNotNull(listResp.getData());
        assertEquals(4, listResp.getData().total());
        assertEquals(Set.of("data", "total", "page", "pageSize"), asMap(listResp.getData()).keySet());

        // 3. get by id
        ApiResponse<?> getResp = controller.get(saved2.getId());
        assertTrue(getResp.isSuccess());

        // 4. verify
        ApiResponse<CryptoAuditService.ChainVerifyResult> verifyResp = controller.verify();
        assertTrue(verifyResp.isSuccess());
        CryptoAuditService.ChainVerifyResult verifyData = verifyResp.getData();
        assertEquals(5, verifyData.total());
        assertEquals(5, verifyData.pass());
        assertEquals(0, verifyData.fail());
        assertTrue(verifyData.intact());
        assertEquals(Set.of("total", "pass", "fail", "intact", "tampered"),
                asMap(verifyData).keySet(), "链验证回执必须恰为契约五键");
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
        ApiResponse<CryptoAuditController.RecordResultVO> resp =
                failingController.record(request("X", null, null, null, null));
        assertEquals(ApiResponse.CODE_INTERNAL_ERROR, resp.getCode());
        assertTrue(resp.getMessage().contains("记录失败"));
    }
}
