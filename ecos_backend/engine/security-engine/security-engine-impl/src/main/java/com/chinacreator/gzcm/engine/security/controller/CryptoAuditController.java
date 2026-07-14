package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.audit.crypto.CryptoAuditLedger;
import com.chinacreator.gzcm.sysman.audit.crypto.CryptoAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * P1-2: 密码学审计控制器。
 * GET /verify → 链式完整性验证
 * GET /logs   → 审计日志查询
 */
@RestController
@RequestMapping("/api/v1/audit/crypto")
public class CryptoAuditController {
    private static final Logger log = LoggerFactory.getLogger(CryptoAuditController.class);
    private final CryptoAuditService service;

    public CryptoAuditController(CryptoAuditService service) {
        this.service = service;
        log.info("CryptoAuditController 初始化完成");
    }

    @PostMapping("/record")
    public ApiResponse<Map<String, Object>> record(@RequestBody Map<String, Object> body) {
        try {
            CryptoAuditLedger entry = new CryptoAuditLedger();
            entry.setEventType((String) body.getOrDefault("eventType", "UNKNOWN"));
            entry.setResource((String) body.getOrDefault("resource", ""));
            entry.setAction((String) body.getOrDefault("action", ""));
            entry.setOperatorId((String) body.getOrDefault("operatorId", "system"));
            entry.setPayload((String) body.getOrDefault("payload", "{}"));
            CryptoAuditLedger saved = service.record(entry);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", saved.getId());
            result.put("currentHash", saved.getCurrentHash());
            result.put("timestamp", saved.getTimestamp());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("记录加密审计失败", e);
            return ApiResponse.internalError("记录失败: " + e.getMessage());
        }
    }

    @GetMapping("/logs")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            List<CryptoAuditLedger> data = service.list(keyword, page, pageSize);
            int total = service.count(keyword);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", data);
            result.put("total", total);
            result.put("page", page);
            result.put("pageSize", pageSize);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询加密审计日志失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
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
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/verify")
    public ApiResponse<Map<String, Object>> verify() {
        try {
            Map<String, Object> result = service.chainVerify();
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("链验证失败", e);
            return ApiResponse.internalError("验证失败: " + e.getMessage());
        }
    }
}
