package com.chinacreator.gzcm.engine.ai.wagent.approval;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * L3 审批凭证服务（V326 数据面）：签发 / 校验 / 一次性消费 / 四眼。
 *
 * <p><b>为什么需要</b>：批 D 前 {@code approveGate} 只判 token 非空——不校验、不存储、不绑人，
 * 任何字符串都能通过。本服务把凭证变成有状态对象：摘要入库、绑定目标对象、带过期、
 * 一次性消费、审批人 ≠ 申请人。</p>
 *
 * <p><b>明文只出现一次</b>：{@link #issue} 返回明文，库里只有 SHA-256 摘要；
 * 日志/审计/响应其余路径<b>禁</b>携带明文。</p>
 */
@Service
public class ApprovalTokenService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalTokenService.class);

    /** 默认有效期 30 分钟（L3 审批到执行的最长人工窗口）。 */
    public static final long DEFAULT_TTL_SECONDS = 1_800L;
    /** 有效期上限 8 小时：防「一次签发长期通行」。 */
    public static final long MAX_TTL_SECONDS = 28_800L;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final WAgentApprovalDao approvalDao;

    public ApprovalTokenService(WAgentApprovalDao approvalDao) {
        this.approvalDao = approvalDao;
    }

    /**
     * 签发结果（强类型；字段名与旧 Map 键逐字一致，键序不变 = 上表顺序）。
     *
     * <p>{@code token} 是明文，仅本次响应返回；日志/审计/其余响应路径禁携带。</p>
     */
    public record IssuedToken(String approvalId, String token, String scopeType, String scopeId,
                              LocalDateTime expiredAt, long ttlSeconds, boolean oneTimeUse,
                              String tokenEcho) {
    }

    /**
     * 签发凭证。{@code approver} 必须是审批人，{@code requestedBy} 是发起该 L3 动作的人；
     * 二者相同即自审批，拒绝（四眼）。
     *
     * @return id / token（明文，仅此一次）/ scopeType / scopeId / expiredAt
     */
    public IssuedToken issue(String approver, String scopeType, String scopeId,
                             String requestedBy, Long ttlSeconds, String traceId) {
        return issue(approver, null, scopeType, scopeId, requestedBy, ttlSeconds, traceId);
    }

    /**
     * 签发凭证（带审批人别名）。{@code approverAlias} 是同一主体的另一个标识（登录名/用户名），
     * 四眼比对时与 {@code approver} 同等对待——否则「用用户名填 requestedBy」可绕过自审批拦截。
     */
    public IssuedToken issue(String approver, String approverAlias, String scopeType,
                             String scopeId, String requestedBy, Long ttlSeconds, String traceId) {
        WAgentApprovalScope scope = WAgentApprovalScope.of(scopeType);
        if (scope == null) {
            throw WAgentOrchestrationException.validation(
                    "scopeType 必填且取 CANDIDATE_PUBLISH|ACTION_COMMIT（当前 " + scopeType + "）");
        }
        if (isBlank(scopeId)) {
            throw WAgentOrchestrationException.validation("scopeId 必填（凭证不得作用于对象集合）");
        }
        if (isBlank(approver)) {
            throw new WAgentOrchestrationException(WAgentErrorCode.E_POLICY,
                    "签发面缺少审批人身份（fail-closed）", null);
        }
        String requester = isBlank(requestedBy) ? null : requestedBy.trim();
        if (requester != null && (requester.equals(approver)
                || (approverAlias != null && requester.equals(approverAlias.trim())))) {
            throw new WAgentOrchestrationException(WAgentErrorCode.E_POLICY,
                    "四眼原则：审批人不得给自己发起的事项签发凭证（approver=requestedBy=" + requester + "）",
                    Map.of("scopeType", scope.name(), "scopeId", scopeId));
        }
        long ttl = ttlSeconds == null || ttlSeconds <= 0 ? DEFAULT_TTL_SECONDS : ttlSeconds;
        if (ttl > MAX_TTL_SECONDS) {
            throw WAgentOrchestrationException.validation(
                    "ttlSeconds 上限 " + MAX_TTL_SECONDS + "（实得 " + ttl + "）");
        }

        String plaintext = newToken();
        WAgentApprovalDO row = new WAgentApprovalDO();
        row.setId(UUID.randomUUID().toString());
        row.setStatus("APPROVED");
        row.setRiskLevel("L3");
        row.setRequestedAt(LocalDateTime.now());
        row.setScopeType(scope.name());
        row.setScopeId(scopeId.trim());
        row.setRequestedBy(requester);
        row.setApprovedBy(approver);
        row.setTokenDigest(digest(plaintext));
        row.setExpiredAt(LocalDateTime.now().plusSeconds(ttl));
        row.setTraceId(traceId);
        approvalDao.insert(row);

        // 明文只出现在本条返回值；落日志只记 id 与 scope
        log.info("[approval] 凭证已签发 id={} scope={}:{} approver={} ttl={}s traceId={}",
                row.getId(), scope.name(), row.getScopeId(), approver, ttl, traceId);

        return new IssuedToken(row.getId(), plaintext, scope.name(), row.getScopeId(),
                row.getExpiredAt(), ttl, true, "仅本次响应返回，服务端只存 SHA-256 摘要");
    }
    /**
     * 校验凭证（不消费）。四道闸：存在 → 作用域匹配 → 未过期 → 未消费 → 非自审批。
     */
    public WAgentApprovalDO verify(String plaintext, String scopeType, String scopeId, String actor) {
        if (isBlank(plaintext)) {
            throw WAgentOrchestrationException.validation(
                    "approval_token 必填（L3 发布/提交凭证，缺失即拒）");
        }
        WAgentApprovalScope scope = WAgentApprovalScope.of(scopeType);
        if (scope == null) {
            throw WAgentOrchestrationException.validation("scopeType 越界: " + scopeType);
        }
        WAgentApprovalDO row = approvalDao.findByDigest(digest(plaintext.trim()))
                .orElseThrow(() -> new WAgentOrchestrationException(WAgentErrorCode.E_POLICY,
                        "审批凭证无效（未签发或已撤销）", null));

        if (!scope.name().equals(row.getScopeType()) || !row.getScopeId().equals(scopeId)) {
            throw new WAgentOrchestrationException(WAgentErrorCode.E_POLICY,
                    "审批凭证与目标对象不匹配（凭证绑定 " + row.getScopeType() + ":" + row.getScopeId() + "）",
                    Map.of("tokenScopeType", String.valueOf(row.getScopeType()),
                            "tokenScopeId", String.valueOf(row.getScopeId())));
        }
        if (row.getExpiredAt() != null && row.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new WAgentOrchestrationException(WAgentErrorCode.E_POLICY,
                    "审批凭证已过期（expiredAt=" + row.getExpiredAt() + "），请重新审批",
                    Map.of("expiredAt", String.valueOf(row.getExpiredAt())));
        }
        if (row.consumed()) {
            throw new WAgentOrchestrationException(WAgentErrorCode.E_WA_STATE,
                    "审批凭证已被使用（一次性凭证，重放必拒）",
                    Map.of("approvalId", row.getId(), "consumedAt", String.valueOf(row.getConsumedAt())));
        }
        if (!isBlank(actor) && actor.equals(row.getRequestedBy())) {
            throw new WAgentOrchestrationException(WAgentErrorCode.E_POLICY,
                    "四眼原则：申请人不得用自己发起的凭证执行 L3 动作（需他人审批）",
                    Map.of("approvalId", row.getId()));
        }
        return row;
    }

    /** 校验 + 一次性消费（CAS）。CAS 返回 0 说明并发下已被他人消费 ⇒ 重放拒绝。 */
    public WAgentApprovalDO verifyAndConsume(String plaintext, String scopeType, String scopeId,
                                             String actor, String traceId) {
        WAgentApprovalDO row = verify(plaintext, scopeType, scopeId, actor);
        if (approvalDao.consume(row.getId(), traceId) != 1) {
            throw new WAgentOrchestrationException(WAgentErrorCode.E_WA_STATE,
                    "审批凭证并发消费失败（已被使用）", Map.of("approvalId", row.getId()));
        }
        return row;
    }

    /** 诊断：按对象列凭证（不含明文、不含摘要）。 */
    public Optional<WAgentApprovalDO> describe(String approvalId) {
        return approvalDao.findById(approvalId);
    }

    /** 凭证明文形态：前缀便于人工识别，主体 32 字节随机（base64url，无填充）。 */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "ecos-apv-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 摘要：SHA-256 hex（小写）。同一明文恒同摘要，用于唯一索引防重签发。 */
    static String digest(String plaintext) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(plaintext.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
