package com.chinacreator.gzcm.engine.ai.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.approval.AiIdentityResolver.IdentityWithRoles;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import com.chinacreator.gzcm.engine.ai.approval.AiIdentityResolver;
import com.chinacreator.gzcm.engine.ai.approval.AiOrchestrationErrorCodes;
import com.chinacreator.gzcm.engine.ai.approval.AiOrchestrationException;
import com.chinacreator.gzcm.engine.ai.approval.L3ApprovalTokenService;

/**
 * L3 审批凭证签发面（原 {@code wagent.controller.WAgentApprovalController}，2026-11-23 去 wagent 化迁入本包）。
 *
 * <p><b>路由不变</b>：仍是 {@code POST /api/v1/wagent/approval-tokens} —— CLI（{@code ops.json}）
 * 与 e2e 用例已按此路径落契约，改路径即破坏既有依赖（API 只增不改）。类名去 wagent 前缀，
 * 因为它是 AI 域通用 L3 闸门，不再属于 wagent 面。</p>
 *
 * <p><b>两道服务端闸</b>：① admin 角色 fail-closed（非 admin ⇒ 403 {@code E-POLICY}）；
 * ② 四眼——审批人（当前主体）不得为自己发起的事项签发。</p>
 *
 * <p><b>明文只回一次</b>：响应 {@code data.token} 是唯一一次明文返回，服务端只存 SHA-256 摘要；
 * 该字段已列入 CLI 脱敏规则（不得进日志/回放）。</p>
 */
@RestController
public class L3ApprovalController {

    private static final Logger log = LoggerFactory.getLogger(L3ApprovalController.class);

    /**
     * 路由单源：沿用既有 wagent 前缀（CLI 契约，禁改）。
     *
     * <p><b>注解直接用字面量</b>：{@code _win_tasks/route-rewrite-lint.ps1} 与三滤波器静态校验
     * 都只认 {@code @*Mapping("...")} 里的字符串字面量，写成常量引用会让他们把本端点算作
     * 「无 Controller 承载」（实测 hits=0 → FAIL-R4）。故字面量与常量双写，值必须一致。</p>
     */
    static final String APPROVAL_TOKENS_PATH = "/api/v1/wagent/approval-tokens";

    private final L3ApprovalTokenService approvalTokenService;
    private final AiIdentityResolver identityResolver;

    public L3ApprovalController(L3ApprovalTokenService approvalTokenService,
                                AiIdentityResolver identityResolver) {
        this.approvalTokenService = approvalTokenService;
        this.identityResolver = identityResolver;
    }

    /** 签发请求体（{@code requestedBy} 是发起 L3 动作的人；审批人 = 当前登录主体）。 */
    public record ApprovalTokenRequest(String scopeType, String scopeId, String requestedBy,
                                       Long ttlSeconds) {
    }

    @PostMapping("/api/v1/wagent/approval-tokens")
    public ApiResponse<L3ApprovalTokenService.IssuedToken> createApprovalToken(
            @RequestBody(required = false) ApprovalTokenRequest req,
            jakarta.servlet.http.HttpServletRequest request) {
        IdentityWithRoles identity = identityResolver.requireWithRoles();
        if (!identity.isAdmin()) {
            throw new AiOrchestrationException(AiOrchestrationErrorCodes.Code.E_POLICY,
                    "审批凭证签发仅 admin 角色可操作（当前主体角色不满足，fail-closed）", null);
        }
        if (req == null) {
            throw AiOrchestrationException.validation(
                    "请求体必填：scopeType + scopeId（+ 可选 requestedBy/ttlSeconds）");
        }
        String traceId = AiTraceSupport.currentTraceId();
        // 四眼别名：同一主体的登录名（JWT username）——只比对 operator 会被「填用户名」绕过
        L3ApprovalTokenService.IssuedToken data = approvalTokenService.issue(identity.operator(),
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
