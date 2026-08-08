package com.chinacreator.gzcm.engine.data;

import java.util.List;
import java.util.Map;

public interface QualityService {

    Map<String, Object> createRule(Map<String, Object> body);

    Map<String, Object> updateRule(String ruleId, Map<String, Object> body);

    void deleteRule(String ruleId);

    Map<String, Object> getRule(String ruleId);

    Map<String, Object> listRules(String datasetId, String ruleType, int page, int pageSize);

    Map<String, Object> evaluate(String datasetId, String datasourceId, String tableName, int sampleSize);

    Map<String, Object> evaluateRule(String ruleId, String datasourceId, String tableName, int sampleSize);

    Map<String, Object> getEvaluationHistory(String datasetId, int page, int pageSize);

    /**
     * 全量巡检 — 遍历所有 enabled 的 DQ 规则并逐条评估。
     *
     * @return 巡检结果摘要 ({success, totalRules, successCount, failCount, ...})
     */
    Map<String, Object> evaluateAll();
}
