package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.scenario.BindingCatalog;
import com.chinacreator.gzcm.workspace.scenario.BindingCatalogItem;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 六类三层单一词表端点（卷07 F07-05 N2）。
 * <pre>GET /api/v1/workspace/binding-catalog</pre>
 */
@RestController
@RequestMapping("/api/v1/workspace/binding-catalog")
public class BindingCatalogController {

    private final BindingCatalog catalog;

    public BindingCatalogController(BindingCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public ApiResponse<List<BindingCatalogItem>> list() {
        return ApiResponse.success(catalog.list());
    }
}
