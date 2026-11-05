package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.security.service.SecurityAuditService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 安全审计写入口（{@code POST /api/security/audit/log}，fire-and-forget）。
 *
 * <p><b>#147CD（DoD DI-2 第八批）</b>：请求体由弱类型 Map 改 {@link SecurityAuditLogRequest}，
 * 回执由 {@code Map} 改 {@link SecurityAuditAcceptedVO}（3 键 {@code id/status/action}，
 * 键序不变）。绑定面等价：7 个字段均可缺省（缺省＝null），仅 {@code action} 必填
 * （空/空白 ⇒ 400，判据与文案同旧）。缺 {@code @RequestBody} 仍由 Spring 归 400，
 * 本类不多写兜底。</p>
 */
@RestController
@RequestMapping("/api/security/audit")
public class SecurityAuditController {

    private final SecurityAuditService securityAuditService;

    public SecurityAuditController(SecurityAuditService securityAuditService) {
        this.securityAuditService = securityAuditService;
    }

    /**
     * 异步写入审计日志。
     * 请求格式: {userId, username, action, resource, result, detail, ipAddress}
     */
    @PostMapping("/log")
    public ApiResponse<SecurityAuditAcceptedVO> log(@RequestBody SecurityAuditLogRequest body) {
        String id = UUID.randomUUID().toString().replace("-", "");
        String action = body.getAction();

        if (action == null || action.isBlank()) {
            return ApiResponse.badRequest("action不能为空");
        }

        // Fire-and-forget 异步写入
        securityAuditService.asyncWrite(id, body.getUserId(), body.getUsername(), action,
                body.getResource(), body.getResult(), body.getDetail(), body.getIpAddress());

        return ApiResponse.success(new SecurityAuditAcceptedVO(id, "accepted", action));
    }

    /** 审计事件请求（7 字段，除 action 外均可缺省）。 */
    public static final class SecurityAuditLogRequest {
        private String userId;
        private String username;
        private String action;
        private String resource;
        private String result;
        private String detail;
        private String ipAddress;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            this.resource = resource;
        }

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = detail;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public void setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
        }
    }

    /** 受理回执（3 键，键序 id/status/action）。 */
    public record SecurityAuditAcceptedVO(String id, String status, String action) {
    }
}
