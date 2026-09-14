package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.data.dto.DataSourceDTO;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.service.DataSourceRegistryService;
import com.chinacreator.gzcm.engine.data.service.DataSourceServiceImpl;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * PMO-45: 数据源管理 CRUD 全链路 — /api/v1/datasource/ 路径。
 *
 * 与 DataSourceController（/api/v1/datanet/datasource/）并存，两条路径都暴露。
 * 三滤波器：VersionPrefixRewriteFilter / SecurityConfig / ClearanceInterceptor
 * 已在 PMO-45 任务中注册。
 *
 * 新增 endpoint：
 *   POST /test-connection/{id}  — 对已保存数据源执行连通性测试
 *   POST /preview-schema         — 预览数据源代码结构 (body: {datasourceId})
 *
 * @author ECOS-BE (PMO-45)
 */
@RestController
@RequestMapping({"/api/v1/datasource", "/datasource"})
public class PMO45DataSourceController {

    private final DataSourceRegistryService registryService;
    private final DataSourceServiceImpl impl;

    public PMO45DataSourceController(DataSourceRegistryService registryService,
                                     DataSourceServiceImpl impl) {
        this.registryService = registryService;
        this.impl = impl;
    }

    // ── CRUD ──────────────────────────────────────────────────────────

    @PostMapping
    public org.springframework.http.ResponseEntity<ApiResponse<DataSourceEntity>> register(@RequestBody DataSourceDTO dto) {
        try {
            // PMO-45 T5 验收: POST 创建资源返回 201 Created（REST 规范）
            return org.springframework.http.ResponseEntity
                    .status(org.springframework.http.HttpStatus.CREATED)
                    .body(ApiResponse.success(registryService.register(dto)));
        } catch (IllegalArgumentException e) {
            return org.springframework.http.ResponseEntity
                    .status(org.springframework.http.HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping
    public ApiResponse<List<DataSourceEntity>> list() {
        return ApiResponse.success(registryService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<DataSourceEntity> getById(@PathVariable String id) {
        DataSourceEntity entity = registryService.getById(id);
        if (entity == null) {
            return ApiResponse.error(404, "数据源不存在: " + id);
        }
        return ApiResponse.success(entity);
    }

    @PutMapping("/{id}")
    public ApiResponse<DataSourceEntity> update(@PathVariable String id,
                                                 @RequestBody DataSourceDTO dto) {
        DataSourceEntity updated = registryService.update(id, dto);
        if (updated == null) {
            return ApiResponse.error(404, "数据源不存在: " + id);
        }
        return ApiResponse.success(updated);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(@PathVariable String id) {
        registryService.remove(id);
        return ApiResponse.success(null);
    }

    // ── 未保存前测试（raw） ──────────────────────────────────────────

    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> testConnectionRaw(@RequestBody DataSourceDTO dto) {
        try {
            return ApiResponse.success(registryService.testConnectionRaw(dto));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    // ── PMO-45 新增 ──────────────────────────────────────────────────

    /**
     * 对已保存数据源执行连通性测试。
     * 关系型: 走 JdbcConnector 真实连接。
     * MINIO/FILESYSTEM: 验证 URI 格式合法性。
     */
    @PostMapping("/test-connection/{id}")
    public ApiResponse<Map<String, Object>> testConnectionById(@PathVariable String id) {
        Map<String, Object> result = impl.testConnectionById(id);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        return ApiResponse.success(result);
    }

    /**
     * 预览数据源代码结构。
     * body: {"datasourceId": "xxx"}
     */
    @PostMapping("/preview-schema")
    public ApiResponse<Map<String, Object>> previewSchema(
            @RequestBody java.util.Map<String, String> body) {
        String id = body.get("datasourceId");
        if (id == null || id.isBlank()) {
            return ApiResponse.error(400, "datasourceId 不能为空");
        }
        try {
            return ApiResponse.success(impl.previewSchema(id));
        } catch (RuntimeException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    /**
     * 查询支持的数据源类型列表（供前端动态渲染类型下拉）。
     */
    @GetMapping("/supported-types")
    public ApiResponse<List<String>> supportedTypes() {
        return ApiResponse.success(List.copyOf(
            DataSourceServiceImpl.SUPPORTED_TYPES
        ));
    }
}
