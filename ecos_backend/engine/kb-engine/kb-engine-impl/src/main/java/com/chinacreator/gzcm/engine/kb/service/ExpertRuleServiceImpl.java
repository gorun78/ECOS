package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.ExpertRuleService;
import com.chinacreator.gzcm.engine.kb.model.ExpertRule;
import com.chinacreator.gzcm.engine.kb.model.RuleExecutionResult;
import com.chinacreator.gzcm.engine.kb.repository.ExpertRuleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ExpertRuleServiceImpl implements ExpertRuleService {

    private static final Logger log = LoggerFactory.getLogger(ExpertRuleServiceImpl.class);

    private final ExpertRuleMapper ruleMapper;

    public ExpertRuleServiceImpl(ExpertRuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    @Override
    public List<ExpertRule> listRules(String domain) {
        return domain != null ? ruleMapper.findByDomain(domain) : ruleMapper.findAll();
    }

    @Override
    public ExpertRule getRule(String ruleId) {
        return ruleMapper.findById(ruleId);
    }

    @Override
    public ExpertRule createRule(ExpertRule rule) {
        if (rule.getId() == null) rule.setId(UUID.randomUUID().toString());
        rule.setCreatedAt(System.currentTimeMillis());
        rule.setUpdatedAt(System.currentTimeMillis());
        ruleMapper.insert(rule);
        log.info("Created expert rule: {} [{}]", rule.getId(), rule.getName());
        return rule;
    }

    @Override
    public ExpertRule updateRule(String ruleId, ExpertRule rule) {
        rule.setId(ruleId);
        rule.setUpdatedAt(System.currentTimeMillis());
        ruleMapper.update(rule);
        return ruleMapper.findById(ruleId);
    }

    @Override
    public void deleteRule(String ruleId) {
        ruleMapper.deleteById(ruleId);
        log.info("Deleted expert rule: {}", ruleId);
    }

    @Override
    public Map<String, Object> executeRule(String ruleId, Map<String, Object> context) {
        ExpertRule rule = ruleMapper.findById(ruleId);
        if (rule == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "Rule not found: " + ruleId);
            return err;
        }
        long start = System.currentTimeMillis();
        boolean fired = evaluateCondition(rule.getCondition(), context);
        Map<String, Object> output = new LinkedHashMap<>();
        if (fired) {
            output.put("action", rule.getAction());
            output.put("context", context);
        }
        long elapsed = System.currentTimeMillis() - start;
        RuleExecutionResult result = new RuleExecutionResult(ruleId, fired, output, elapsed);
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("ruleId", result.getRuleId());
        resultMap.put("fired", result.isFired());
        resultMap.put("output", result.getOutput());
        resultMap.put("executionTimeMs", result.getExecutionTimeMs());
        return resultMap;
    }

    @Override
    public int batchImport(List<ExpertRule> rules) {
        int count = 0;
        for (ExpertRule rule : rules) {
            if (rule.getId() == null) rule.setId(UUID.randomUUID().toString());
            rule.setCreatedAt(System.currentTimeMillis());
            rule.setUpdatedAt(System.currentTimeMillis());
            ruleMapper.insert(rule);
            count++;
        }
        log.info("Batch imported {} expert rules", count);
        return count;
    }

    private boolean evaluateCondition(String condition, Map<String, Object> context) {
        return condition != null && !condition.isEmpty() && context != null && !context.isEmpty();
    }
}