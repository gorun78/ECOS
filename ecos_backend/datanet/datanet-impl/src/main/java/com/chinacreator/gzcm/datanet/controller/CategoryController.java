package com.chinacreator.gzcm.datanet.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.datanet.model.DataCategory;
import com.chinacreator.gzcm.datanet.service.CategoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 分类管理 Controller — 管理数据目录的分类树。
 *
 * @author DataBridge Datanet Team
 */
@RestController
@RequestMapping("/datanet/category")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** 创建分类 */
    @PostMapping
    public ApiResponse<DataCategory> create(@RequestBody DataCategory category) {
        return ApiResponse.success(categoryService.create(category));
    }

    /** 更新分类 */
    @PutMapping("/{id}")
    public ApiResponse<DataCategory> update(@PathVariable String id, @RequestBody DataCategory category) {
        category.setCategoryId(id);
        return ApiResponse.success(categoryService.update(category));
    }

    /** 获取分类详情 */
    @GetMapping("/{id}")
    public ApiResponse<DataCategory> getById(@PathVariable String id) {
        DataCategory cat = categoryService.getById(id);
        if (cat == null) {
            return ApiResponse.error(404, "分类不存在: " + id);
        }
        return ApiResponse.success(cat);
    }

    /** 获取完整分类树 */
    @GetMapping("/tree")
    public ApiResponse<List<DataCategory>> tree() {
        return ApiResponse.success(categoryService.getTree());
    }

    /** 获取子分类列表 */
    @GetMapping("/{id}/children")
    public ApiResponse<List<DataCategory>> children(@PathVariable String id) {
        return ApiResponse.success(categoryService.getChildren(id));
    }

    /** 获取分类统计（含资源数） */
    @GetMapping("/stats")
    public ApiResponse<List<DataCategory>> stats() {
        return ApiResponse.success(categoryService.getCategoryStats());
    }

    /** 删除分类（递归删除子分类） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(@PathVariable String id) {
        categoryService.remove(id);
        return ApiResponse.success(null);
    }
}
