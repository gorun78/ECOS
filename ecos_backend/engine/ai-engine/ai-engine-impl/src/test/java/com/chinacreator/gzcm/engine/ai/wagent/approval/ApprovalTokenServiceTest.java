package com.chinacreator.gzcm.engine.ai.wagent.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrationException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L3 审批凭证服务门禁（批 D）：四眼 / 过期 / 作用域绑定 / 一次性消费。
 *
 * <p>用内存 DAO 子类替代 JDBC（不连库），只钉服务层语义；
 * 真实 SQL 形态由 {@code WAgentApprovalDao} 的列契约与迁移门禁守护。</p>
 */
class ApprovalTokenServiceTest {

    private InMemoryDao dao;
    private ApprovalTokenService service;

    @BeforeEach
    void setUp() {
        dao = new InMemoryDao();
        service = new ApprovalTokenService(dao);
    }

    @Test
    @DisplayName("签发返回明文一次，库里只存摘要（明文 != 摘要）")
    void issueStoresDigestOnly() {
        ApprovalTokenService.IssuedToken data = service.issue("admin", "CANDIDATE_PUBLISH", "c-1",
                "generator", 600L, "trace-1");

        String token = data.token();
        assertTrue(token.startsWith("ecos-apv-"), "明文形态: " + token);
        WAgentApprovalDO row = dao.rows.values().iterator().next();
        assertNotEquals(token, row.getTokenDigest(), "库里不得存明文");
        assertEquals(64, row.getTokenDigest().length(), "SHA-256 hex 长度");
        assertEquals("generator", row.getRequestedBy());
        assertEquals("admin", row.getApprovedBy());
        assertTrue(row.getExpiredAt().isAfter(LocalDateTime.now()));
    }

    @Test
    @DisplayName("四眼①：审批人给自己签发 ⇒ 403 E-POLICY")
    void selfIssueRejected() {
        WAgentOrchestrationException e = assertThrows(WAgentOrchestrationException.class,
                () -> service.issue("admin", "CANDIDATE_PUBLISH", "c-1", "admin", 600L, "t"));
        assertEquals(WAgentErrorCode.E_POLICY, e.errorCode());
        assertTrue(dao.rows.isEmpty(), "拒绝时不落库");
    }

    @Test
    @DisplayName("四眼②：申请人用自己发起的凭证执行 ⇒ 403 E-POLICY")
    void requesterCannotUseOwnToken() {
        String token = service.issue("admin", "CANDIDATE_PUBLISH", "c-1",
                "generator", 600L, "t").token();

        WAgentOrchestrationException e = assertThrows(WAgentOrchestrationException.class,
                () -> service.verify(token, "CANDIDATE_PUBLISH", "c-1", "generator"));
        assertEquals(WAgentErrorCode.E_POLICY, e.errorCode());
    }

    @Test
    @DisplayName("作用域绑定：凭证只能用于签发时的对象，换对象 ⇒ 403")
    void scopeBindingEnforced() {
        String token = service.issue("admin", "CANDIDATE_PUBLISH", "c-1",
                "generator", 600L, "t").token();

        assertThrows(WAgentOrchestrationException.class,
                () -> service.verify(token, "CANDIDATE_PUBLISH", "c-2", "publisher"));
        assertThrows(WAgentOrchestrationException.class,
                () -> service.verify(token, "ACTION_COMMIT", "c-1", "publisher"));
        // 正例：对象与作用域都匹配时通过
        assertEquals("c-1", service.verify(token, "CANDIDATE_PUBLISH", "c-1", "publisher").getScopeId());
    }

    @Test
    @DisplayName("过期凭证 ⇒ 403（不自动续期）")
    void expiredTokenRejected() {
        String token = (String) service.issue("admin", "CANDIDATE_PUBLISH", "c-1",
                "generator", 1L, "t").token();
        dao.rows.values().forEach(r -> r.setExpiredAt(LocalDateTime.now().minusSeconds(1)));

        WAgentOrchestrationException e = assertThrows(WAgentOrchestrationException.class,
                () -> service.verify(token, "CANDIDATE_PUBLISH", "c-1", "publisher"));
        assertEquals(WAgentErrorCode.E_POLICY, e.errorCode());
    }

    @Test
    @DisplayName("一次性消费：首次成功，重放 409 E-WA-STATE")
    void replayRejected() {
        String token = (String) service.issue("admin", "CANDIDATE_PUBLISH", "c-1",
                "generator", 600L, "t").token();

        service.verifyAndConsume(token, "CANDIDATE_PUBLISH", "c-1", "publisher", "t");
        WAgentOrchestrationException e = assertThrows(WAgentOrchestrationException.class,
                () -> service.verifyAndConsume(token, "CANDIDATE_PUBLISH", "c-1", "publisher", "t"));
        assertEquals(WAgentErrorCode.E_WA_STATE, e.errorCode());
    }

    @Test
    @DisplayName("未知凭证 / 空凭证：分别 403 E-POLICY 与 400 E-VALIDATION（不泄露存在性细节）")
    void unknownAndBlankToken() {
        assertEquals(WAgentErrorCode.E_POLICY, assertThrows(WAgentOrchestrationException.class,
                () -> service.verify("ecos-apv-not-issued", "CANDIDATE_PUBLISH", "c-1", "publisher"))
                .errorCode());
        assertEquals(WAgentErrorCode.E_VALIDATION, assertThrows(WAgentOrchestrationException.class,
                () -> service.verify("  ", "CANDIDATE_PUBLISH", "c-1", "publisher")).errorCode());
    }

    @Test
    @DisplayName("作用域字面量越界 ⇒ 400（禁静默默认）")
    void unknownScopeRejected() {
        assertEquals(WAgentErrorCode.E_VALIDATION, assertThrows(WAgentOrchestrationException.class,
                () -> service.issue("admin", "SOMETHING_ELSE", "c-1", "generator", 600L, "t"))
                .errorCode());
    }

    /** 内存 DAO：只实现本服务用到的四个方法，不连库。 */
    private static final class InMemoryDao extends WAgentApprovalDao {
        private final Map<String, WAgentApprovalDO> rows = new LinkedHashMap<>();

        @Override
        public int insert(WAgentApprovalDO row) {
            rows.put(row.getId(), row);
            return 1;
        }

        @Override
        public Optional<WAgentApprovalDO> findByDigest(String tokenDigest) {
            return rows.values().stream()
                    .filter(r -> tokenDigest.equals(r.getTokenDigest()))
                    .findFirst();
        }

        @Override
        public Optional<WAgentApprovalDO> findById(String id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public int consume(String id, String traceId) {
            WAgentApprovalDO row = rows.get(id);
            if (row == null || row.consumed()) {
                return 0;
            }
            row.setConsumedAt(LocalDateTime.now());
            row.setStatus("CONSUMED");
            if (traceId != null) {
                row.setTraceId(traceId);
            }
            return 1;
        }
    }
}
