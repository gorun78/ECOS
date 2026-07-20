package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.PromptCompilerService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/cognitive")
public class PromptCompilerController {

    private final PromptCompilerService promptCompilerService;

    public PromptCompilerController(PromptCompilerService promptCompilerService) {
        this.promptCompilerService = promptCompilerService;
    }

    @PostMapping("/compile-context")
    public ApiResponse<Map<String, Object>> compile(@RequestBody Map<String, Object> req) {
        Map<String, Object> result = promptCompilerService.compileContext(req);
        return ApiResponse.success(result);
    }

    @GetMapping("/index-status")
    public ApiResponse<Map<String, Object>> indexStatus() {
        Map<String, Object> result = promptCompilerService.getIndexStatus();
        return ApiResponse.success(result);
    }
}
