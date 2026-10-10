package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextBuildRequest;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextBuildResult;
import com.chinacreator.gzcm.engine.cognitive2.service.context.ContextBuilderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Context Builder REST API（《认知域与智能域补充详细设计 v1.0》§5.2）。
 *
 * <pre>
 * POST /api/v1/cognitive/context/build — 请求态 Context Pack 组装
 *      （权威源清单驱动装配；不新建 cognitive_context 表，§5.2 红线）
 * </pre>
 *
 * <p>挂 cognitive2 既有 {@code /api/v1/cognitive} 类前缀（同 ForecastController 挂法：
 * 类级前缀 + 相对子路径常量 {@link CognitiveApiPaths#CONTEXT_BUILD}）。
 * 失败语义（503/403/409/422/429/400）由 {@link ContextBuildErrorAdvice} 承载；
 * 成功响应经 {@code ApiResponse.data} = ContextBuildResult（含 pack + digest + ttl），
 * body 级 traceId 经 {@code withTraceIdFromMdc()} 回显（§12.2 统一 trace 维度）。</p>
 *
 * <p>安全：非匿名可达（与 /api/v1/cognitive/** 既有面一致：Bearer Token +
 * ClearanceInterceptor 准入；权限裁剪在 ContextSecurityTrimmer 检查点执行，
 * 依赖缺失 fail-closed，§5.5 / SUP-CTX-02）。</p>
 */
@RestController
@RequestMapping(CognitiveApiPaths.COGNITIVE)
public class ContextBuildController {

    private static final Logger log = LoggerFactory.getLogger(ContextBuildController.class);

    private final ContextBuilderService contextBuilderService;

    public ContextBuildController(ContextBuilderService contextBuilderService) {
        this.contextBuilderService = contextBuilderService;
    }

    /**
     * 组装 Context Pack（§5.4 十步：校验→权威源清单→读取→权限裁剪→版本统一→
     * 冲突检测→gap→预算→digest→TTL）。
     */
    @PostMapping("/context/build")
    public ApiResponse<ContextBuildResult> build(@RequestBody ContextBuildRequest request) {
        log.info("Context 构建请求: scenarioId={}, requiredAssets={}, requiredCapabilities={}",
                request == null ? null : request.getScenarioId(),
                request == null ? null : request.getRequiredAssets().size(),
                request == null ? null : request.getRequiredCapabilities());
        ContextBuildResult result = contextBuilderService.build(request);
        return ApiResponse.success(result).withTraceIdFromMdc();
    }
}
