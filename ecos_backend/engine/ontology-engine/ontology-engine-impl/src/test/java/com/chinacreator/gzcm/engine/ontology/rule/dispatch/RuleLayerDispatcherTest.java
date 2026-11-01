package com.chinacreator.gzcm.engine.ontology.rule.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.engine.ontology.engine.FunctionValidator;
import com.chinacreator.gzcm.engine.ontology.model.OntologyRule;

/** 五层分发与 RuleRegistry 判据（§6.2.6②⑧；DF-4；#147G）。 */
class RuleLayerDispatcherTest {

    private final RuleLayerDispatcher dispatcher = new RuleLayerDispatcher(new FunctionValidator());

    private static OntologyRule rule(String layer, String expression, String action) {
        OntologyRule r = new OntologyRule();
        r.setId("rule-1");
        r.setRuleType("VALIDATION");
        r.setRuleLayer(layer);
        r.setRuleCarrier("RULE_OBJECT");
        r.setScope("tenant-a");
        r.setVersionNo("3");
        r.setGitRef("abc123");
        r.setExpression(expression);
        r.setAction(action);
        return r;
    }

    @Test
    void fiveLayersMapToExactlyFiveTargetsAndOnlyPolicyUsesOpa() {
        assertEquals(5, RuleLayerTarget.values().length);
        assertEquals("OPA_PDP", RuleLayerTarget.POLICY.targetCode());
        long opa = java.util.Arrays.stream(RuleLayerTarget.values())
                .filter(RuleLayerTarget::opaBound).count();
        assertEquals(1, opa, "OPA 只能承接 Policy 层（§4.2.6）");
        assertFalse(RuleLayerTarget.BUSINESS.opaBound(), "BUSINESS 绝不进 OPA/Rego");
        assertFalse(RuleLayerTarget.VALIDATION.opaBound());
        assertFalse(RuleLayerTarget.COGNITIVE.opaBound());
    }

    @Test
    void missingOrUnknownLayerIsRejectedNotDefaulted() {
        assertEquals(RuleDispatchResult.Status.REJECTED, dispatcher.dispatch(rule(null, "1 > 0", null)).status());
        assertEquals(RuleDispatchResult.Status.REJECTED, dispatcher.dispatch(rule("MAGIC", "1 > 0", null)).status());
        assertEquals(RuleDispatchResult.Status.REJECTED, dispatcher.dispatch(null).status());
        assertNull(RuleLayerTarget.fromLayer("UNKNOWN"));
    }

    @Test
    void validationLayerReallyRunsFunctionValidatorSandbox() {
        RuleDispatchResult ok = dispatcher.dispatch(rule("VALIDATION", "amount > 0 and amount < 100", null));
        assertEquals(RuleDispatchResult.Status.ROUTED, ok.status());
        assertEquals(RuleLayerTarget.VALIDATION, ok.target());

        RuleDispatchResult forbidden = dispatcher.dispatch(
                rule("VALIDATION", "Runtime.getRuntime().exec('rm -rf /')", null));
        assertEquals(RuleDispatchResult.Status.REJECTED, forbidden.status());
        assertTrue(forbidden.reason().contains("FunctionValidator"));

        assertEquals(RuleDispatchResult.Status.REJECTED,
                dispatcher.dispatch(rule("VALIDATION", "", null)).status());
    }

    @Test
    void businessNeverRoutesToOpaAndNeedsAction() {
        RuleDispatchResult noAction = dispatcher.dispatch(rule("BUSINESS", "amount > 1000", null));
        assertEquals(RuleDispatchResult.Status.NOT_APPLICABLE, noAction.status());
        assertEquals(RuleLayerTarget.BUSINESS, noAction.target());
        assertTrue(noAction.reason().contains("禁改投 OPA"));

        RuleDispatchResult withAction = dispatcher.dispatch(rule("BUSINESS", "amount > 1000", "APPROVE"));
        assertEquals(RuleDispatchResult.Status.ROUTED, withAction.status());
        assertEquals("ACTION_WORKFLOW", withAction.target().targetCode());
    }

    @Test
    void policyRoutesToOpaAndRequiresExpression() {
        assertEquals(RuleDispatchResult.Status.REJECTED,
                dispatcher.dispatch(rule("POLICY", "", null)).status());
        RuleDispatchResult routed = dispatcher.dispatch(rule("POLICY", "allow if role == 'admin'", null));
        assertEquals(RuleDispatchResult.Status.ROUTED, routed.status());
        assertEquals("OPA_PDP", routed.target().targetCode());
    }

    @Test
    void structuralAndCognitiveLayersHaveHonestPreconditions() {
        assertEquals(RuleDispatchResult.Status.NOT_APPLICABLE,
                dispatcher.dispatch(rule("STRUCTURAL", "", null)).status());
        assertEquals(RuleDispatchResult.Status.ROUTED,
                dispatcher.dispatch(rule("STRUCTURAL", "Cardinality(0..1)", null)).status());
        assertEquals(RuleDispatchResult.Status.REJECTED,
                dispatcher.dispatch(rule("COGNITIVE", "", null)).status());
        assertEquals("COGNITIVE_ENGINE",
                dispatcher.dispatch(rule("COGNITIVE", "evidence => belief", null)).target().targetCode());
    }

    @Test
    void registryReportsNotAvailableWithoutVersionOrGitRef() {
        RuleRegistry registry = new RuleRegistry();
        Map<String, Object> ok = registry.register(rule("VALIDATION", "1 > 0", null));
        assertEquals(RuleRegistry.STATUS_REGISTERED, ok.get("status"));
        assertTrue(registry.isTraceable(rule("VALIDATION", "1 > 0", null)));

        OntologyRule noRef = rule("VALIDATION", "1 > 0", null);
        noRef.setGitRef(null);
        assertEquals(RuleRegistry.STATUS_NOT_AVAILABLE, registry.register(noRef).get("status"));

        OntologyRule noVersion = rule("VALIDATION", "1 > 0", null);
        noVersion.setVersionNo(" ");
        assertEquals(RuleRegistry.STATUS_NOT_AVAILABLE, registry.register(noVersion).get("status"));

        OntologyRule noLayer = rule("BOGUS", "1 > 0", null);
        Map<String, Object> bad = registry.register(noLayer);
        assertEquals(RuleRegistry.STATUS_NOT_AVAILABLE, bad.get("status"));
    }
}
