package com.chinacreator.gzcm.engine.data;

import java.util.Map;

/**
 * Copilot 服务 — AI 辅助 Pipeline 开发。
 *
 * @author ECOS Pipeline 2.0 Team
 */
public interface CopilotService {

    /** 自然语言 → SQL */
    Map<String, Object> generateSql(String prompt, String schemaInfo);

    /** 自然语言 → Pipeline DSL */
    Map<String, Object> generatePipeline(String description, String availableSources);

    /** 表达式建议 */
    Map<String, Object> suggestExpression(String fieldName, String context);

    /** NL → UDF 代码 */
    Map<String, Object> generateUdf(String description, String language);

    /** 错误诊断 */
    Map<String, Object> diagnose(String runId, String errorLog);
}
