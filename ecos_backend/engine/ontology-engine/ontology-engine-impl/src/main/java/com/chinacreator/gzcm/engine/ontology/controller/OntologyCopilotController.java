package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.CopilotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/engine/ontology/copilot")
public class OntologyCopilotController {

    private static final Logger log = LoggerFactory.getLogger(OntologyCopilotController.class);

    private final CopilotService copilotService;

    public OntologyCopilotController(CopilotService copilotService) {
        this.copilotService = copilotService;
    }

    @PostMapping("/entity")
    public ApiResponse<Map<String, Object>> suggestEntity(@RequestBody Map<String, Object> body) {
        String prompt = (String) body.getOrDefault("prompt", "");
        String schemaInfo = (String) body.getOrDefault("schemaInfo", "");
        String fullPrompt = "请根据以下描述生成本体实体建模建议: " + prompt;
        Map<String, Object> result = copilotService.generateSql(fullPrompt, schemaInfo);
        return ApiResponse.success(result);
    }

    @PostMapping("/relation")
    public ApiResponse<Map<String, Object>> suggestRelation(@RequestBody Map<String, Object> body) {
        String prompt = (String) body.getOrDefault("prompt", "");
        String schemaInfo = (String) body.getOrDefault("schemaInfo", "");
        String fullPrompt = "请根据以下描述生成本体关系建模建议: " + prompt;
        Map<String, Object> result = copilotService.generateSql(fullPrompt, schemaInfo);
        return ApiResponse.success(result);
    }

    @PostMapping("/validate")
    public ApiResponse<Map<String, Object>> validateConsistency(@RequestBody Map<String, Object> body) {
        String schemaInfo = (String) body.getOrDefault("schemaInfo", "");
        Map<String, Object> result = copilotService.diagnose("ontology-validate", schemaInfo);
        return ApiResponse.success(result);
    }

    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> reverseImport(@RequestBody Map<String, Object> body) {
        String schemaInfo = (String) body.getOrDefault("schemaInfo", "");
        String fullPrompt = "请根据以下数据源schema逆向生成本体定义";
        Map<String, Object> result = copilotService.generatePipeline(fullPrompt, schemaInfo);
        return ApiResponse.success(result);
    }
}
