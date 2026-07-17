package com.chinacreator.gzcm.common.service;

import java.util.Map;

/**
 * Copilot service interface -- AI-assisted development capabilities.
 * Shared across engines that need NL-to-code functionality.
 */
public interface ICopilotService {

    /** Natural language to SQL */
    Map<String, Object> generateSql(String prompt, String schemaInfo);

    /** Natural language to Pipeline DSL */
    Map<String, Object> generatePipeline(String description, String availableSources);

    /** Expression suggestion */
    Map<String, Object> suggestExpression(String fieldName, String context);

    /** Natural language to UDF code */
    Map<String, Object> generateUdf(String description, String language);

    /** Error diagnosis */
    Map<String, Object> diagnose(String runId, String errorLog);
}
