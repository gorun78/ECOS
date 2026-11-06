package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.service.framework.CognitiveExecutionService;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认知执行状态机 REST（详细设计-认知引擎 v1.0 §7.2/§7.3）。
 *
 * <pre>
 * POST /api/v1/cognitive/executions                          — 创建执行记录（幂等键）
 * GET  /api/v1/cognitive/executions/{executionId}            — 查询状态/历史
 * POST /api/v1/cognitive/executions/{executionId}/transitions— 状态迁移
 * </pre>
 *
 * <p>不新增执行表：记录为请求态（进程内），长任务仍由 runtime-task 调度、W Agent Runtime 执行；
 * SUCCEEDED 只表示流程与验证通过，业务结论状态独立记录。</p>
 */
@RestController
@RequestMapping(CognitiveApiPaths.COGNITIVE)
public class CognitiveExecutionController {

    private final CognitiveExecutionService executionService;

    public CognitiveExecutionController(CognitiveExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping(CognitiveApiPaths.EXECUTIONS_REL)
    public ApiResponse<Map<String, Object>> create(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.success(executionService.create(body));
    }

    @GetMapping(CognitiveApiPaths.EXECUTION_ITEM_REL)
    public ApiResponse<Map<String, Object>> get(
            @PathVariable(CognitiveApiPaths.PARAM_EXECUTION_ID) String executionId) {
        return ApiResponse.success(executionService.get(executionId));
    }

    @PostMapping(CognitiveApiPaths.EXECUTION_TRANSITIONS_REL)
    public ApiResponse<Map<String, Object>> transition(
            @PathVariable(CognitiveApiPaths.PARAM_EXECUTION_ID) String executionId,
            @RequestBody(required = false) Map<String, Object> body) {
        if (body == null || body.get("targetStatus") == null) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "targetStatus");
        }
        return ApiResponse.success(executionService.transition(executionId, body));
    }
}
