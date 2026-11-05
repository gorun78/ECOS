package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.security.service.SecurityAuditService;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

/**
 * 详设-01 F01-08（W40）impersonate 二次确认门禁 —— 诚实实现。
 *
 * <p><b>复盘差距</b>（复盘报告 §3.1 详设-01，F01-08 行）：超管旁路"加确认"子项缺，
 * 全仓 impersonate 零命中。超管旁路强审计已在位（ClearanceInterceptor W40），
 * 但后端从未存在任何"以他人身份签发会话"的能力底座（无 session-issuance 实现）。</p>
 *
 * <p><b>本端点行为（fail-closed，只收紧不放松）</b>：
 * <ol>
 *   <li>{@code confirm != true} → <b>HTTP 428</b> + errorCode
 *       {@code IMPERSONATE_CONFIRM_REQUIRED}（二次确认缺失即拒，方向为 deny 收紧，
 *       符合 API 只增不改约束）；</li>
 *   <li>{@code confirm == true} → <b>HTTP 501</b> + errorCode
 *       {@code IMPERSONATE_CAPABILITY_NOT_AVAILABLE} + traceId —— 显式声明
 *       "冒充会话签发能力未实现"，<b>绝不伪造成功/假 token</b>（无假真理 P0）。</li>
 * </ol>
 * 两个分支均写审计事件（action=IMPERSONATE_REQUEST，含服务端已认证主体与
 * 自报目标主体），满足 F01-08 "二次确认 + 强审计" 的强审计面。</p>
 *
 * <p><b>#147CE（DoD DI-2 第九批）</b>：请求体由弱类型 Map 改 {@link ImpersonateRequest}
 * （{@code confirm} 保留「布尔 true 或字符串 "true"」双形态宽容解析）；响应信封的载荷位
 * 由 Map 改 {@code Void}（三条腿都只回错误信封，不携 data）。同批删除两段**从未使用**的
 * 局部 Map（428 的 {@code hint}、501 的 {@code detail}）——它们不参与响应也不影响审计，
 * 属死代码。wire 键面（code/message/errorCode/traceId）与状态行逐字不变。</p>
 */
@RestController
@RequestMapping({"/api/security/impersonate", "/api/v1/security/impersonate"})
public class ImpersonateController {

    private static final Logger log = LoggerFactory.getLogger(ImpersonateController.class);

    /** 428 缺失二次确认错误码（详设-01 F01-08 派生；前端据 errorCode 弹确认框重试）。 */
    public static final String ERROR_CONFIRM_REQUIRED = "IMPERSONATE_CONFIRM_REQUIRED";
    /** 501 能力缺失错误码（诚实呈现：后端无冒充会话签发底座）。 */
    public static final String ERROR_CAPABILITY_NOT_AVAILABLE = "IMPERSONATE_CAPABILITY_NOT_AVAILABLE";

    private final SecurityAuditService securityAuditService;

    public ImpersonateController(SecurityAuditService securityAuditService) {
        this.securityAuditService = securityAuditService;
    }

    /**
     * POST /api/v1/security/impersonate  body: {targetUserId, reason, confirm}
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> impersonate(
            @RequestBody(required = false) ImpersonateRequest body,
            HttpServletRequest request) {

        ImpersonateRequest req = body != null ? body : new ImpersonateRequest();
        String targetUserId = req.getTargetUserId();
        String reason = req.getReason();
        boolean confirmed = Boolean.TRUE.equals(req.getConfirm())
                || (req.getConfirm() != null && "true".equalsIgnoreCase(String.valueOf(req.getConfirm())));
        String actor = resolveActor();
        String traceId = resolveTraceId();

        if (targetUserId == null || targetUserId.isBlank()) {
            audit(actor, targetUserId, "REJECTED_BAD_REQUEST", "missing targetUserId");
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Void>badRequest("targetUserId 必填").withTraceId(traceId));
        }

        if (!confirmed) {
            // ① 二次确认缺失 → 428 Precondition Required（fail-closed 收紧）
            audit(actor, targetUserId, "REJECTED_CONFIRM_REQUIRED", reason);
            log.warn("impersonate 拒绝（缺二次确认）: actor={} target={} traceId={}",
                    actor, targetUserId, traceId);
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(ApiResponse.<Void>error(
                            HttpStatus.PRECONDITION_REQUIRED.value(),
                            ERROR_CONFIRM_REQUIRED,
                            "impersonate 需要二次确认：请显式携带 confirm=true")
                            .withTraceId(traceId));
        }

        // ② confirm=true 仍诚实 501：后端不存在冒充会话签发能力（无假真理 P0，不造 token）
        audit(actor, targetUserId, "NOT_IMPLEMENTED", reason);
        log.warn("impersonate 能力未实现（已确认仍拒发）: actor={} target={} traceId={}",
                actor, targetUserId, traceId);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponse.<Void>error(
                        HttpStatus.NOT_IMPLEMENTED.value(),
                        ERROR_CAPABILITY_NOT_AVAILABLE,
                        "impersonate 会话签发能力尚未实现（二次确认门禁已生效，不返回伪造身份会话）")
                        .withTraceId(traceId));
    }

    /** 强审计留痕（F01-08）：无论拒绝分支为何，均写审计。 */
    private void audit(String actor, String target, String result, String detail) {
        try {
            String id = UUID.randomUUID().toString().replace("-", "");
            securityAuditService.asyncWrite(id, null, actor, "IMPERSONATE_REQUEST",
                    "user:" + target, result, detail, null);
        } catch (Exception e) {
            log.warn("impersonate 审计留痕失败（不阻断响应）: {}", e.getMessage());
        }
    }

    /** 服务端已认证主体（不可得时记 anonymous，不伪装）。 */
    String resolveActor() {
        String userId = UserContext.getCurrentUserId();
        if (userId != null && !userId.isBlank()) return userId;
        String username = UserContext.getCurrentUsername();
        if (username != null && !username.isBlank()) return username;
        return "anonymous";
    }

    /** MDC traceId 优先；缺省生成 t- 前缀 UUID（保证 501/428 必带可追踪 id）。 */
    private String resolveTraceId() {
        try {
            String tid = org.slf4j.MDC.get("traceId");
            if (tid != null && !tid.isBlank()) return tid;
        } catch (Exception ignored) { }
        return "t-" + UUID.randomUUID();
    }

    /** 冒充请求（3 字段；{@code confirm} 兼容布尔与字符串两种形态，与旧 Map 读取口径一致）。 */
    public static final class ImpersonateRequest {
        private String targetUserId;
        private String reason;
        private Object confirm;

        public String getTargetUserId() {
            return targetUserId;
        }

        public void setTargetUserId(String targetUserId) {
            this.targetUserId = targetUserId;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public Object getConfirm() {
            return confirm;
        }

        public void setConfirm(Object confirm) {
            this.confirm = confirm;
        }
    }
}
