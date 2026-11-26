package com.chinacreator.gzcm.engine.cognitive2.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive2.service.algorithm.CognitiveAlgorithmService;

/**
 * 认知算法通用执行面（V367 批次 A）。
 *
 * <pre>
 * POST /api/v1/cognitive/algorithms/{code}/execute
 *      body { scenarioId?, inputs:{...} } → { code,status,metrics,assumptions,elapsedMs }
 * GET  /api/v1/cognitive/algorithms                → 已装配执行器键（只读）
 * </pre>
 *
 * <p>数值是本面唯一产出；LLM 解释由调用方（workspace 场景运行）负责。</p>
 */
@RestController
@RequestMapping(CognitiveApiPaths.ALGORITHMS)
public class CognitiveAlgorithmController {

    private final CognitiveAlgorithmService service;

    public CognitiveAlgorithmController(CognitiveAlgorithmService service) {
        this.service = service;
    }

    @PostMapping(CognitiveApiPaths.ALGORITHM_EXECUTE_REL)
    @SuppressWarnings("unchecked")
    public ApiResponse<Map<String, Object>> execute(@PathVariable("code") String code,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> inputs;
        if (body != null && body.get("inputs") instanceof Map<?, ?> in) {
            inputs = (Map<String, Object>) in;
        } else {
            inputs = body == null ? new LinkedHashMap<>() : body;
        }
        return ApiResponse.success(service.execute(code, inputs));
    }

    @GetMapping
    public ApiResponse<List<String>> codes() {
        return ApiResponse.success(service.codes());
    }
}
