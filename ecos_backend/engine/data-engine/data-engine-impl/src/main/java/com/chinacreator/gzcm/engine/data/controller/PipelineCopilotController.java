package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.CopilotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Copilot Controller — AI 辅助 Pipeline 开发 API。
 * <p>
 * 端点 (5个, PRD §5.5):
 * POST /copilot/sql        — NL → SQL
 * POST /copilot/pipeline   — NL → Pipeline DSL
 * POST /copilot/expression — 表达式建议
 * POST /copilot/udf        — NL → UDF 代码
 * POST /copilot/diagnose   — 错误诊断
 *
 * @author ECOS Pipeline 2.0 Team
 */
@RestController
@RequestMapping("/api/v1/engine/data/copilot")
public class PipelineCopilotController {

    private static final Logger log = LoggerFactory.getLogger(PipelineCopilotController.class);
    private final CopilotService copilotService;

    public PipelineCopilotController(CopilotService copilotService) {
        this.copilotService = copilotService;
    }

    // ── 1. NL → SQL ──
    @PostMapping("/sql")
    public ApiResponse<Map<String, Object>> generateSql(@RequestBody Map<String, Object> body) {
        try {
            String prompt = (String) body.get("prompt");
            String schemaInfo = (String) body.get("schemaInfo");

            if (prompt == null || prompt.isEmpty()) {
                return ApiResponse.badRequest("prompt 不能为空");
            }

            return ApiResponse.success("SQL 生成完成", copilotService.generateSql(prompt, schemaInfo));
        } catch (Exception e) {
            log.error("NL→SQL 失败", e);
            return ApiResponse.internalError("NL→SQL 失败: " + e.getMessage());
        }
    }

    // ── 2. NL → Pipeline DSL ──
    @PostMapping("/pipeline")
    public ApiResponse<Map<String, Object>> generatePipeline(@RequestBody Map<String, Object> body) {
        try {
            String description = (String) body.get("description");
            String availableSources = (String) body.get("availableSources");

            if (description == null || description.isEmpty()) {
                return ApiResponse.badRequest("description 不能为空");
            }

            return ApiResponse.success("Pipeline 生成完成", copilotService.generatePipeline(description, availableSources));
        } catch (Exception e) {
            log.error("NL→Pipeline 失败", e);
            return ApiResponse.internalError("NL→Pipeline 失败: " + e.getMessage());
        }
    }

    // ── 3. 表达式建议 ──
    @PostMapping("/expression")
    public ApiResponse<Map<String, Object>> suggestExpression(@RequestBody Map<String, Object> body) {
        try {
            String fieldName = (String) body.get("fieldName");
            String context = (String) body.get("context");

            if (fieldName == null || fieldName.isEmpty()) {
                return ApiResponse.badRequest("fieldName 不能为空");
            }

            return ApiResponse.success("表达式建议生成完成", copilotService.suggestExpression(fieldName, context));
        } catch (Exception e) {
            log.error("表达式建议失败", e);
            return ApiResponse.internalError("表达式建议失败: " + e.getMessage());
        }
    }

    // ── 4. NL → UDF 代码 ──
    @PostMapping("/udf")
    public ApiResponse<Map<String, Object>> generateUdf(@RequestBody Map<String, Object> body) {
        try {
            String description = (String) body.get("description");
            String language = (String) body.getOrDefault("language", "python");

            if (description == null || description.isEmpty()) {
                return ApiResponse.badRequest("description 不能为空");
            }

            return ApiResponse.success("UDF 生成完成", copilotService.generateUdf(description, language));
        } catch (Exception e) {
            log.error("NL→UDF 失败", e);
            return ApiResponse.internalError("NL→UDF 失败: " + e.getMessage());
        }
    }

    // ── 5. 错误诊断 ──
    @PostMapping("/diagnose")
    public ApiResponse<Map<String, Object>> diagnose(@RequestBody Map<String, Object> body) {
        try {
            String runId = (String) body.get("runId");
            String errorLog = (String) body.get("errorLog");

            if (runId == null || runId.isEmpty()) {
                return ApiResponse.badRequest("runId 不能为空");
            }

            return ApiResponse.success("诊断完成", copilotService.diagnose(runId, errorLog));
        } catch (Exception e) {
            log.error("错误诊断失败", e);
            return ApiResponse.internalError("错误诊断失败: " + e.getMessage());
        }
    }
}
