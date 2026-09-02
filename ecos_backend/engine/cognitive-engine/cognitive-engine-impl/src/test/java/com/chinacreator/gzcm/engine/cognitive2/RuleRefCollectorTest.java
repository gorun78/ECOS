package com.chinacreator.gzcm.engine.cognitive2;

import com.chinacreator.gzcm.engine.cognitive2.model.Justification;
import com.chinacreator.gzcm.engine.cognitive2.model.ReasoningPath;
import com.chinacreator.gzcm.engine.cognitive2.model.ReasoningStep;
import com.chinacreator.gzcm.engine.cognitive2.model.RuleRef;
import com.chinacreator.gzcm.engine.cognitive2.service.RuleRefCollector;
import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wave-3.2 T4 + T5 — RuleRefCollector 行为测试。
 *
 * <p>T4: (name+domain) 去重 + sourceRank 按 priority 升序
 * <p>T5: rule_hits / precedent_count / avg_confidence 结构化计数
 */
class RuleRefCollectorTest {

    private ComplianceRule rule(String id, String name, String domain, int prio, boolean enabled) {
        ComplianceRule r = new ComplianceRule();
        r.setId(id);
        r.setName(name);
        r.setDomain(domain);
        r.setPriority(prio);
        r.setEnabled(enabled);
        r.setCondition("#a>1");
        r.setAction("REJECT");
        r.setVersion(2);
        return r;
    }

    @Test
    void dedupesByNameAndDomainAndKeepsLowerPriority() {
        List<ComplianceRule> rules = List.of(
                rule("R1", "坏账规则", "finance", 10, true),
                rule("R2", "坏账规则", "finance", 20, true), // 同名同 domain, prio>10 → 应被去掉
                rule("R3", "坏账规则", "hr", 1, true)       // 同名不同 domain → 保留
        );
        RuleRefCollector c = new RuleRefCollector();
        Map<String, RuleRef> idx = c.toIndex(rules);

        // 应剩 R1(finance) + R3(hr)
        assertEquals(2, idx.size());
        assertTrue(idx.containsKey("R1"));
        assertTrue(idx.containsKey("R3"));
        assertFalse(idx.containsKey("R2"));

        // sourceRank 按 priority 升序：R3 prio=1 第 0, R1 prio=10 第 1
        assertEquals(0, idx.get("R3").getSourceRank());
        assertEquals(1, idx.get("R1").getSourceRank());

        // version 应被填到 RuleRef
        assertEquals("2", idx.get("R1").getVersion());
        assertEquals("compliance".equals(idx.get("R1").getCategory())
                || "finance".equals(idx.get("R1").getCategory()), true);
    }

    @Test
    void disabledRuleSkipped() {
        RuleRefCollector c = new RuleRefCollector();
        Map<String, RuleRef> idx = c.toIndex(List.of(rule("R1", "x", "d", 1, false)));
        assertTrue(idx.isEmpty(), "禁用规则不应进入索引");
    }

    @Test
    void countForJustificationAggregatesRuleAndPrecedentHits() {
        // 6 step：2 RULE + 1 PRECEDENT + 3 KG
        List<ReasoningStep> steps = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ReasoningStep s = new ReasoningStep();
            s.setStepId("s" + i);
            s.setStepIndex(i + 1);
            s.setConfidence(0.5 + i * 0.05);
            if (i == 0) s.setSourceType("RULE");
            if (i == 1) {
                s.setSourceType("PRECEDENT");
                s.setPrecedentRef(null); // 简化测（不算 precedent）
            }
            steps.add(s);
        }
        // 第 0 步带 ruleRef, 第 1 步带 precedentRef
        steps.get(0).setRuleRef(new RuleRef("R1", "rule-A", null, null));
        steps.get(1).setPrecedentRef(new com.chinacreator.gzcm.engine.cognitive2.model.PrecedentRef(
                "P1", "d-1", "x", "approved", 0.8));

        ReasoningPath path = new ReasoningPath();
        path.setSteps(steps);
        path.setConclusion("root");
        path.setJustification("text");

        Justification justification = new Justification("root", path, List.of("R1"));

        RuleRefCollector c = new RuleRefCollector();
        Map<String, Object> counts = c.countForJustification(path);
        assertEquals(6, counts.get("step_count"));
        assertEquals(1, counts.get("rule_hits"));
        assertEquals(1, counts.get("precedent_count"));
        // avg_conf = (0.5+0.55+0.6+0.65+0.7+0.75)/6 = 3.75/6 = 0.625
        assertEquals(0.625, ((Number) counts.get("avg_confidence")).doubleValue());

        // attach 后 justification 末尾应带 counts
        c.attachStructuralCount(path);
        assertTrue(path.getJustification().contains("counts="));
        assertTrue(path.getJustification().contains("rule_hits:1"));
        assertTrue(path.getJustification().contains("precedent_count:1"));
    }

    @Test
    void emptyPathCountsEmptyMap() {
        RuleRefCollector c = new RuleRefCollector();
        assertNotNull(c.countForJustification(null));
        assertTrue(c.countForJustification(null).isEmpty());
    }
}
