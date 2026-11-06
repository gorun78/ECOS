package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive2.service.framework.CognitiveMethodCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 认知方法目录 REST（详细设计-认知引擎 v1.0 §1.1：方法与框架职责区分）。
 *
 * <pre>
 * GET /api/v1/cognitive/methods              — 方法字典 + 已发布框架 usedBy
 * GET /api/v1/cognitive/methods/{methodId}   — 方法详情（不存在 404）
 * </pre>
 *
 * <p>只读；方法不是可部署模型，也不新增注册表。错误矩阵由 CognitiveErrorAdvice 承接。</p>
 */
@RestController
@RequestMapping(CognitiveApiPaths.COGNITIVE)
public class CognitiveMethodController {

    private final CognitiveMethodCatalogService catalog;

    public CognitiveMethodController(CognitiveMethodCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping(CognitiveApiPaths.METHODS_REL)
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.success(catalog.catalog());
    }

    @GetMapping(CognitiveApiPaths.METHOD_ITEM_REL)
    public ApiResponse<Map<String, Object>> get(
            @PathVariable(CognitiveApiPaths.PARAM_METHOD_ID) String methodId) {
        return ApiResponse.success(catalog.get(methodId));
    }
}
