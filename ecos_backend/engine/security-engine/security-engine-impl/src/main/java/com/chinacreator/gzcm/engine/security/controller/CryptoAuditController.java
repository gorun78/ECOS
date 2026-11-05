package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.audit.crypto.CryptoAuditLedger;
import com.chinacreator.gzcm.sysman.audit.crypto.CryptoAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * P1-2: 密码学审计控制器。
 * GET /verify → 链式完整性验证
 * GET /logs   → 审计日志查询
 *
 * <p><b>#147CH（DoD DI-2 第十二批）</b>：登记/分页/验证三条腿的载荷位由弱类型 Map 改
 * {@link RecordResultVO}/{@link CryptoAuditPageVO}/{@link CryptoAuditService.ChainVerifyResult}
 * （键集与键序不变；{@code tampered[].issue} 仍只在异常时出现），请求体改
 * {@link CryptoAuditRecordRequest}（五字段缺省值与旧 {@code getOrDefault} 逐字一致）。</p>
 */
@RestController
@RequestMapping({"/api/v1/audit/crypto", "/api/v1/security/audit/crypto"})
public class CryptoAuditController {
    private static final Logger log = LoggerFactory.getLogger(CryptoAuditController.class);
    private final CryptoAuditService service;

    public CryptoAuditController(CryptoAuditService service) {
        this.service = service;
        log.info("CryptoAuditController 初始化完成");
    }

    @PostMapping("/record")
    public ApiResponse<RecordResultVO> record(@RequestBody CryptoAuditRecordRequest body) {
        try {
            CryptoAuditLedger entry = new CryptoAuditLedger();
            entry.setEventType(body.getEventType() != null ? body.getEventType() : "UNKNOWN");
            entry.setResource(body.getResource() != null ? body.getResource() : "");
            entry.setAction(body.getAction() != null ? body.getAction() : "");
            entry.setOperatorId(body.getOperatorId() != null ? body.getOperatorId() : "system");
            entry.setPayload(body.getPayload() != null ? body.getPayload() : "{}");
            CryptoAuditLedger saved = service.record(entry);

            return ApiResponse.success(new RecordResultVO(saved.getId(), saved.getCurrentHash(),
                    saved.getTimestamp()));
        } catch (Exception e) {
            log.error("记录加密审计失败", e);
            return ApiResponse.internalError("记录失败");
        }
    }

    @GetMapping("/logs")
    public ApiResponse<CryptoAuditPageVO> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            List<CryptoAuditLedger> data = service.list(keyword, page, pageSize);
            int total = service.count(keyword);
            return ApiResponse.success(new CryptoAuditPageVO(data, total, page, pageSize));
        } catch (Exception e) {
            log.error("查询加密审计日志失败", e);
            return ApiResponse.internalError("查询失败");
        }
    }

    @GetMapping("/logs/{id}")
    public ApiResponse<?> get(@PathVariable String id) {
        try {
            CryptoAuditLedger entry = service.getById(id);
            if (entry == null) return ApiResponse.notFound("记录不存在: " + id);
            return ApiResponse.success(entry);
        } catch (Exception e) {
            log.error("查询加密审计详情失败", e);
            return ApiResponse.internalError("查询失败");
        }
    }

    @GetMapping("/verify")
    public ApiResponse<CryptoAuditService.ChainVerifyResult> verify() {
        try {
            return ApiResponse.success(service.chainVerify());
        } catch (Exception e) {
            log.error("链验证失败", e);
            return ApiResponse.internalError("验证失败");
        }
    }

    /** 登记请求（5 字段，缺省值与旧 getOrDefault 一致）。 */
    public static final class CryptoAuditRecordRequest {
        private String eventType;
        private String resource;
        private String action;
        private String operatorId;
        private String payload;

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            this.resource = resource;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getOperatorId() {
            return operatorId;
        }

        public void setOperatorId(String operatorId) {
            this.operatorId = operatorId;
        }

        public String getPayload() {
            return payload;
        }

        public void setPayload(String payload) {
            this.payload = payload;
        }
    }

    /** 登记回执（3 键 id/currentHash/timestamp）。 */
    public record RecordResultVO(String id, String currentHash, Long timestamp) {
    }

    /** 日志分页（4 键 data/total/page/pageSize）。 */
    public record CryptoAuditPageVO(List<CryptoAuditLedger> data, int total, int page, int pageSize) {
    }
}
