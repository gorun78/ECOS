package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.scenario.RequiredEdgePolicy;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * N2 绑定目录端点（详细设计-07 D-2 / F07-05）。
 *
 * <p>{@code GET /api/v1/workspace/binding-catalog} 运行时单源下发六类绑定的
 * 类型 + 三层归属（子图/横切/出口）+ i18n key + lucide 图标 + 邻接可达类型；
 * 前端据此渲染资源面板，删除本地常量与"唯一桥"映射（X-68/X-73）。
 * 权限经 security evaluate（F07-18），失败 403。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace")
public class BindingCatalogController {

    private final RequiredEdgePolicy requiredEdgePolicy;

    public BindingCatalogController(RequiredEdgePolicy requiredEdgePolicy) {
        this.requiredEdgePolicy = requiredEdgePolicy;
    }

    @Operation(operationId = "listBindingCatalog", summary = "listBindingCatalog")
    @GetMapping("/binding-catalog")
    public ApiResponse<List<RequiredEdgePolicy.CatalogEntry>> catalog() {
        return ApiResponse.success(requiredEdgePolicy.catalog());
    }
}
