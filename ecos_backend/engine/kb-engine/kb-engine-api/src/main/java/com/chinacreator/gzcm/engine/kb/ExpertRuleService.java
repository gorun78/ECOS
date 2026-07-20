package com.chinacreator.gzcm.engine.kb;

import com.chinacreator.gzcm.engine.kb.model.ExpertRule;

import java.util.List;
import java.util.Map;

public interface ExpertRuleService {

    List<ExpertRule> listRules(String domain);

    ExpertRule getRule(String ruleId);

    ExpertRule createRule(ExpertRule rule);

    ExpertRule updateRule(String ruleId, ExpertRule rule);

    void deleteRule(String ruleId);

    Map<String, Object> executeRule(String ruleId, Map<String, Object> context);

    int batchImport(List<ExpertRule> rules);
}