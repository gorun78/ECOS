package com.chinacreator.gzcm.engine.ontology.audit;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.service.AuditContextGuard;
import com.chinacreator.gzcm.engine.ontology.service.AuditContextGuard.AuditContextMissingException;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W81/M0 — 审计上下文缺失即拒收测。
 *
 * <p>锁定：缺操作人 / 缺 traceId / 都缺 → {@link AuditContextGuard#require()}
 * 抛 {@link AuditContextMissingException}；Controller 层据此映射 {@code 400 ECOS-ONTO-060}。</p>
 */
@ExtendWith(MockitoExtension.class)
class FunctionAuditRejectWithoutContextTest {

    private final AuditContextGuard guard = new AuditContextGuard();

    @BeforeEach
    void cleanCtx() {
        UserContext.clear();
        MDC.clear();
    }

    @AfterEach
    void teardown() {
        UserContext.clear();
        MDC.clear();
    }

    /** 模拟 Controller 出口决策：缺上下文 → badRequest(400)（与 FunctionController 一致）。 */
    private static final Predicate<ApiResponse<?>> IS_BAD_REQUEST =
            r -> r.getCode() == ApiResponse.CODE_BAD_REQUEST;

    @Test
    @DisplayName("W81 — 无 UserContext 且无 traceId → require() 抛 AuditContextMissingException")
    void require_bothMissing_throws() {
        // cleanCtx 已确保两者皆 null/blank
        assertThrows(AuditContextMissingException.class, guard::require);
    }

    @Test
    @DisplayName("W81 — 仅缺 UserContext(username) → require() 抛异常")
    void require_onlyUsernameMissing_throws() {
        MDC.put("traceId", "tr-1");
        // 不设 UserContext → getCurrentUsername()=null
        assertThrows(AuditContextMissingException.class, guard::require);
    }

    @Test
    @DisplayName("W81 — 仅缺 traceId → require() 抛异常")
    void require_onlyTraceMissing_throws() {
        UserContext ctx = new UserContext();
        ctx.setUsername("alice");
        UserContext.setCurrent(ctx);
        // MDC 无 traceId
        assertThrows(AuditContextMissingException.class, guard::require);
    }

    @Test
    @DisplayName("W81 — 操作人 + traceId 齐备 → require() 不抛, resolveCallerId/resolveTraceId 回显正确")
    void require_present_passesAndResolves() {
        UserContext ctx = new UserContext();
        ctx.setUsername("alice");
        UserContext.setCurrent(ctx);
        MDC.put("traceId", "tr-1");

        guard.require(); // 不应抛
        assertEquals("alice", guard.resolveCallerId());
        assertEquals("tr-1", guard.resolveTraceId());
    }

    @Test
    @DisplayName("W81 — Controller 语义：缺上下文时返回 400 ECOS-ONTO-060，齐备时放行")
    void controllerSemantics_missingContext_yieldsBadRequest400() {
        // 缺上下文 → 模拟 Controller 返回体
        ApiResponse<?> missing;
        try {
            guard.require();
            missing = null; // 不应到达
        } catch (AuditContextMissingException e) {
            missing = ApiResponse.badRequest("ECOS-ONTO-060: " + e.getMessage());
        }
        assertTrue(IS_BAD_REQUEST.test(missing), "缺上下文应映射 400");
        assertTrue(missing.getMessage().startsWith("ECOS-ONTO-060"), "错误前缀应含 ECOS-ONTO-060");

        // 齐备 → 放行（require 不抛, resolve 回显 operator）
        UserContext ctx = new UserContext();
        ctx.setUsername("alice");
        UserContext.setCurrent(ctx);
        MDC.put("traceId", "tr-2");
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(guard::require);
        assertEquals("alice", guard.resolveCallerId());
    }
}
