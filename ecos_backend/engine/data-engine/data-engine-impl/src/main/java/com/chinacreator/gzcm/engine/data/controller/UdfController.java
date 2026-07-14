package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.UdfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * UDF Controller — 用户自定义函数管理 API。
 * <p>
 * 端点 (7个, PRD §5.3):
 * POST /udf/register   — 注册 UDF
 * GET  /udf/list       — UDF 列表
 * GET  /udf/{id}       — UDF 详情
 * PUT  /udf/{id}       — 更新 UDF
 * DELETE /udf/{id}     — 删除 UDF
 * POST /udf/{id}/test  — 测试 UDF
 * POST /udf/convert    — SQL → UDF 转换
 *
 * @author ECOS Pipeline 2.0 Team
 */
@RestController
@RequestMapping("/api/v1/engine/data/udf")
public class UdfController {

    private static final Logger log = LoggerFactory.getLogger(UdfController.class);
    private final UdfService udfService;

    public UdfController(UdfService udfService) {
        this.udfService = udfService;
    }

    // ── 1. 注册 UDF ──
    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success("UDF 注册成功", udfService.register(body));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("注册 UDF 失败", e);
            return ApiResponse.internalError("注册 UDF 失败: " + e.getMessage());
        }
    }

    // ── 2. UDF 列表 ──
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String language) {
        try {
            return ApiResponse.success(udfService.list(page, pageSize, category, language));
        } catch (Exception e) {
            log.error("列出 UDF 失败", e);
            return ApiResponse.internalError("列出 UDF 失败: " + e.getMessage());
        }
    }

    // ── 3. UDF 详情 ──
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getById(@PathVariable String id) {
        try {
            return ApiResponse.success(udfService.getById(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (Exception e) {
            log.error("获取 UDF 详情失败: id={}", id, e);
            return ApiResponse.internalError("获取 UDF 详情失败: " + e.getMessage());
        }
    }

    // ── 4. 更新 UDF ──
    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String id,
                                                    @RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success("UDF 更新成功", udfService.update(id, body));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("更新 UDF 失败: id={}", id, e);
            return ApiResponse.internalError("更新 UDF 失败: " + e.getMessage());
        }
    }

    // ── 5. 删除 UDF ──
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        try {
            udfService.delete(id);
            return ApiResponse.success("UDF 已删除", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (Exception e) {
            log.error("删除 UDF 失败: id={}", id, e);
            return ApiResponse.internalError("删除 UDF 失败: " + e.getMessage());
        }
    }

    // ── 6. 测试 UDF ──
    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> test(@PathVariable String id,
                                                  @RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success("UDF 测试完成", udfService.test(id, body));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("测试 UDF 失败: id={}", id, e);
            return ApiResponse.internalError("测试 UDF 失败: " + e.getMessage());
        }
    }

    // ── 7. SQL → UDF 转换 ──
    @PostMapping("/convert")
    public ApiResponse<Map<String, Object>> convertSqlToUdf(@RequestBody Map<String, Object> body) {
        try {
            String sql = (String) body.get("sql");
            String language = (String) body.getOrDefault("language", "python");

            if (sql == null || sql.isEmpty()) {
                return ApiResponse.badRequest("sql 不能为空");
            }

            return ApiResponse.success("SQL→UDF 转换完成", udfService.convertSqlToUdf(sql, language));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("SQL→UDF 转换失败", e);
            return ApiResponse.internalError("SQL→UDF 转换失败: " + e.getMessage());
        }
    }
}
