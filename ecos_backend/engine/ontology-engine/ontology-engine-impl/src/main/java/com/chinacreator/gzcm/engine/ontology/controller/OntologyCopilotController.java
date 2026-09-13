package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.service.ICopilotService;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyCopilotQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 本体 Copilot 智能建模建议 Controller — T16-3。
 *
 * <p>入参改强类型 {@link OntologyCopilotQuery}（原 {@code Map<String,Object>}
 * body 平铺 prompt/schemaInfo）。
 *
 * <p>返回 Map 豁免说明：解释原因——{@link ICopilotService} 是
 * common-api 横切契约（不可改接口），其 generateSql / generatePipeline /
 * diagnose 返回体是 LLM 动态 payload（多语言模型自由结构），Java 端
 * 无法静态定型。点级保留 Map 输出，与 Function 沙箱动态 payload 同款
 * 豁免（参考 {@code OntologyFunctionCompileVO} 注释）。
 */
@RestController
@RequestMapping("/api/v1/engine/ontology/copilot")
public class OntologyCopilotController {

    private static final Logger log = LoggerFactory.getLogger(OntologyCopilotController.class);

    private final ICopilotService copilotService;

    public OntologyCopilotController(ICopilotService copilotService) {
        this.copilotService = copilotService;
    }

    /**
     * POST /entity — 实体建模建议。
     *
     * <p>T16-3：入参 Map → {@link OntologyCopilotQuery}。
     * 返回 Map 豁免（AI 动态 payload，ICopilotService 在 common-api）。
     */
    @PostMapping("/entity")
    public ApiResponse<Map<String, Object>> suggestEntity(@RequestBody OntologyCopilotQuery query) {
        // T16-3: 函数/ Copilot 运行时 payload 动态结构豁免 Map（ICopilotService 返回体）
        String prompt = query.getPrompt() != null ? query.getPrompt() : "";
        String schemaInfo = query.getSchemaInfo() != null ? query.getSchemaInfo() : "";
        String fullPrompt = "请根据以下描述生成本体实体建模建议: " + prompt;
        Map<String, Object> result = copilotService.generateSql(fullPrompt, schemaInfo);
        return ApiResponse.success(result);
    }

    /**
     * POST /relation — 关系建模建议。
     *
     * <p>T16-3：入参 Map → {@link OntologyCopilotQuery}。返回 Map 豁免同上。
     */
    @PostMapping("/relation")
    public ApiResponse<Map<String, Object>> suggestRelation(@RequestBody OntologyCopilotQuery query) {
        String prompt = query.getPrompt() != null ? query.getPrompt() : "";
        String schemaInfo = query.getSchemaInfo() != null ? query.getSchemaInfo() : "";
        String fullPrompt = "请根据以下描述生成本体关系建模建议: " + prompt;
        Map<String, Object> result = copilotService.generateSql(fullPrompt, schemaInfo);
        return ApiResponse.success(result);
    }

    /**
     * POST /validate — 一致性诊断。
     *
     * <p>T16-3：入参 Map → {@link OntologyCopilotQuery}（仅用 schemaInfo）。
     * 返回 Map 豁免同上。
     */
    @PostMapping("/validate")
    public ApiResponse<Map<String, Object>> validateConsistency(@RequestBody OntologyCopilotQuery query) {
        String schemaInfo = query.getSchemaInfo() != null ? query.getSchemaInfo() : "";
        Map<String, Object> result = copilotService.diagnose("ontology-validate", schemaInfo);
        return ApiResponse.success(result);
    }

    /**
     * POST /import — schema 逆向本体定义。
     *
     * <p>T16-3：入参 Map → {@link OntologyCopilotQuery}（仅用 schemaInfo）。
     * 返回 Map 豁免同上。
     */
    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> reverseImport(@RequestBody OntologyCopilotQuery query) {
        String schemaInfo = query.getSchemaInfo() != null ? query.getSchemaInfo() : "";
        String fullPrompt = "请根据以下数据源schema逆向生成本体定义";
        Map<String, Object> result = copilotService.generatePipeline(fullPrompt, schemaInfo);
        return ApiResponse.success(result);
    }
}
