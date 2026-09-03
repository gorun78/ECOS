package com.chinacreator.gzcm.engine.security.policy.engine.impl;

import com.chinacreator.gzcm.sysman.abac.model.AbacContext;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPermissionChecker;
import com.chinacreator.gzcm.sysman.policy.engine.PDP;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * PDPImplTest — ABAC 策略决策点。
 *
 * <p>Wave-5.1 T-05：userId=alice 且策略命中 DRAFT/受限条件时应 DENY。
 */
@ExtendWith(MockitoExtension.class)
class PDPImplTest {

    @Mock
    private IAbacPermissionChecker abacChecker;

    private PDPImpl pdp;

    @BeforeEach
    void setUp() {
        this.pdp = new PDPImpl(abacChecker);
    }

    @Test
    @DisplayName("evaluate — 命中 DRAFT 或同级 Hermes5+ ABAC 策略应 DENY")
    void evaluateDeniesForDraftPolicy() throws Exception {
        AbacContext ctx = new AbacContext();
        ctx.setSubject(Map.of("userId", "alice", "status", "DRAFT"));
        ctx.setResource(Map.of("name", "docs/design"));
        ctx.setAction(Map.of("name", "write"));
        ctx.setEnvironment(Map.of("hermes", "5"));

        when(abacChecker.check(ctx)).thenReturn(IAbacPermissionChecker.Decision.DENY);

        PDP.Decision decision = pdp.evaluate(ctx);
        assertEquals(PDP.Decision.DENY, decision);
    }

    @Test
    @DisplayName("evaluateWithDetails — DENY 时 reason 非空且 attributes 完整")
    void evaluateWithDetailsRejects() throws Exception {
        AbacContext ctx = new AbacContext();
        ctx.setSubject(Map.of("userId", "alice"));
        ctx.setResource(Map.of("id", "res-1"));
        ctx.setAction(Map.of("name", "delete"));
        ctx.setEnvironment(Map.of("ip", "10.0.0.1"));

        when(abacChecker.check(ctx)).thenReturn(IAbacPermissionChecker.Decision.DENY);

        PDP.DecisionResult result = pdp.evaluateWithDetails(ctx);
        assertEquals(PDP.Decision.DENY, result.getDecision());
        assertNotNull(result.getReason());
        assertNotNull(result.getAttributes());
        // attributes 中 subject 放的是 context.getSubject()（Map），不是把值 toString
        Object subject = result.getAttributes().get("subject");
        assertNotNull(subject);
        assertEquals("alice", ((Map<?, ?>) subject).get("userId"));
        assertEquals("res-1", ((Map<?, ?>) result.getAttributes().get("resource")).get("id"));
    }

    @Test
    @DisplayName("evaluate — 无适用策略应 NOT_APPLICABLE")
    void evaluateNoPolicyNotApplicable() throws Exception {
        AbacContext ctx = new AbacContext();
        ctx.setSubject(Map.of("userId", "bob"));
        ctx.setResource(Map.of("id", "res-2"));
        ctx.setAction(Map.of("name", "read"));
        ctx.setEnvironment(Map.of());

        when(abacChecker.check(ctx)).thenReturn(IAbacPermissionChecker.Decision.NOT_APPLICABLE);

        assertEquals(PDP.Decision.NOT_APPLICABLE, pdp.evaluate(ctx));
    }

    @Test
    @DisplayName("evaluate — 底层异常应包装为 PolicyEvaluationException")
    void evaluateWrapsException() throws Exception {
        AbacContext ctx = new AbacContext();
        when(abacChecker.check(ctx))
                .thenThrow(new IAbacPermissionChecker.PolicyEvaluationException("boom"));

        PDP.PolicyEvaluationException ex =
                assertThrows(PDP.PolicyEvaluationException.class, () -> pdp.evaluate(ctx));
        assertNotNull(ex.getMessage());
    }
}
