package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.approval.ApprovalTokenService;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrationException;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.IdentityWithRoles;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * L3 审批凭证签发面（批 D，{@link WAgentApiPaths#APPROVAL_TOKENS}）。
 *
 * <p><b>两道服务端闸</b>：① admin 角色 fail-closed（非 admin ⇒ 403 {@code E-POLICY}，
 * 与 kill-switch 同口径）；② 四眼——审批人（当前主体）不得为自己发起的事项签发
 * （{@link ApprovalTokenService#issue} 强制）。</p>
 *
 * <p><b>明文只回一次</b>：响应 {@code data.token} 是唯一一次明文返回，服务端只存 SHA-256 摘要；
 * 该字段已列入 CLI 脱敏规则（不得进日志/回放）。</p>
 */
@RestController
public class WAgentApprovalController {

    private static final Logger log = LoggerFactory.getLogger(WAgentApprovalController.class);

    private final ApprovalTokenService approvalTokenService;
    private final WAgentIdentityResolver identityResolver;

    public WAgentApprovalController(ApprovalTokenService approvalTokenService,
                                    WAgentIdentityResolver identityResolver) {
        this.approvalTokenService = approvalTokenService;
        this.identityResolver = identityResolver;
    }

    /** 签发请求体（{@code requestedBy} 是发起 L3 动作的人；审批人 = 当前登录主体）。 */
    public record ApprovalTokenRequest(String scopeType, String scopeId, String requestedBy,
                                       Long ttlSeconds) {
    }

    @PostMapping(WAgentApiPaths.APPROVAL_TOKENS)
    public ApiResponse<ApprovalTokenService.IssuedToken> createApprovalToken(
            @RequestBody(required = false) ApprovalTokenRequest req,
            jakarta.servlet.http.HttpServletRequest request) {
        IdentityWithRoles identity = identityResolver.requireWithRoles();
        if (!identity.isAdmin()) {
            throw new WAgentOrchestrationException(WAgentErrorCode.E_POLICY,
                    "审批凭证签发仅 admin 角色可操作（当前主体角色不满足，fail-closed）", null);
        }
        if (req == null) {
            throw WAgentOrchestrationException.validation(
                    "请求体必填：scopeType + scopeId（+ 可选 requestedBy/ttlSeconds）");
        }
        String traceId = AiTraceSupport.currentTraceId();
        // 四眼别名：同一主体的登录名（JWT username）——只比对 operator 会被「填用户名」绕过
        ApprovalTokenService.IssuedToken data = approvalTokenService.issue(identity.operator(),
                jwtUsername(request.getHeader("Authorization")),
                req.scopeType(), req.scopeId(), req.requestedBy(), req.ttlSeconds(), traceId);
        // 只记 id 与 scope，禁记 token 明文
        log.info("[approval] 签发成功 approvalId={} scope={}:{} approver={} traceId={}",
                data.approvalId(), data.scopeType(), data.scopeId(),
                identity.operator(), traceId);
        return ApiResponse.success(data).withTraceId(traceId);
    }

    /** 读已认证 JWT 的 username 声明（本请求已过认证链，只做声明提取，不做二次验签）。 */
    private static String jwtUsername(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String[] parts = authorization.substring(7).trim().split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] payload = java.util.Base64.getUrlDecoder().decode(parts[1]);
            String username = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(payload).path("username").asText(null);
            return username == null || username.isBlank() ? null : username.trim();
        } catch (RuntimeException | java.io.IOException e) {
            return null;
        }
    }
}
