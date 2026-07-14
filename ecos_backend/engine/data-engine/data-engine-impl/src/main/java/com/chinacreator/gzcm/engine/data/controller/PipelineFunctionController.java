package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.PipelineFunctionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Pipeline 函数注册表 API — 管理 120+ PB 函数。
 *
 * @author ECOS Pipeline 2.0 Team
 */
@RestController
@RequestMapping("/api/v1/engine/data/functions")
public class PipelineFunctionController {

    private static final Logger log = LoggerFactory.getLogger(PipelineFunctionController.class);
    private final PipelineFunctionService functionService;

    public PipelineFunctionController(PipelineFunctionService functionService) {
        this.functionService = functionService;
    }

    // ── 1. 列出所有函数 (分页，可选分类过滤) ──
    @GetMapping
    public ApiResponse<Map<String, Object>> listFunctions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String category) {
        try {
            Map<String, Object> result = functionService.listFunctions(page, pageSize, category);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("列出函数失败", e);
            return ApiResponse.internalError("列出函数失败: " + e.getMessage());
        }
    }

    // ── 2. 按分类列出 ──
    @GetMapping("/category/{category}")
    public ApiResponse<List<Map<String, Object>>> listByCategory(@PathVariable String category) {
        try {
            return ApiResponse.success(functionService.listByCategory(category));
        } catch (Exception e) {
            log.error("按分类列出函数失败: category={}", category, e);
            return ApiResponse.internalError("按分类列出函数失败: " + e.getMessage());
        }
    }

    // ── 3. 搜索函数 ──
    @GetMapping("/search")
    public ApiResponse<List<Map<String, Object>>> search(@RequestParam String q) {
        try {
            return ApiResponse.success(functionService.search(q));
        } catch (Exception e) {
            log.error("搜索函数失败: q={}", q, e);
            return ApiResponse.internalError("搜索函数失败: " + e.getMessage());
        }
    }

    // ── 4. 函数详情 ──
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getById(@PathVariable String id) {
        try {
            return ApiResponse.success(functionService.getById(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (Exception e) {
            log.error("获取函数详情失败: id={}", id, e);
            return ApiResponse.internalError("获取函数详情失败: " + e.getMessage());
        }
    }

    // ── 5. 获取分类统计 ──
    @GetMapping("/categories")
    public ApiResponse<List<Map<String, Object>>> getCategories() {
        try {
            return ApiResponse.success(functionService.getCategories());
        } catch (Exception e) {
            log.error("获取函数分类失败", e);
            return ApiResponse.internalError("获取函数分类失败: " + e.getMessage());
        }
    }

    // ── 6. 创建自定义函数 ──
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success("函数创建成功", functionService.create(body));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("创建函数失败", e);
            return ApiResponse.internalError("创建函数失败: " + e.getMessage());
        }
    }

    // ── 7. 更新函数 ──
    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String id,
                                                    @RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success("函数更新成功", functionService.update(id, body));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("更新函数失败: id={}", id, e);
            return ApiResponse.internalError("更新函数失败: " + e.getMessage());
        }
    }

    // ── 8. 删除函数 ──
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        try {
            functionService.delete(id);
            return ApiResponse.success("函数已删除", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("删除函数失败: id={}", id, e);
            return ApiResponse.internalError("删除函数失败: " + e.getMessage());
        }
    }
}
