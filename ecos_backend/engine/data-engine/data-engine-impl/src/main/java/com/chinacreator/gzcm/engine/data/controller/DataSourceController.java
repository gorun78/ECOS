package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.datanet.dto.DataSourceDTO;
import com.chinacreator.gzcm.engine.data.service.DataSourceRegistryService;
import com.chinacreator.gzcm.runtime.core.datasource.entity.DataSourceEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/datanet/datasource")
public class DataSourceController {

    private final DataSourceRegistryService service;

    public DataSourceController(DataSourceRegistryService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<DataSourceEntity> register(@RequestBody DataSourceDTO dto) {
        return ApiResponse.success(service.register(dto));
    }

    @GetMapping
    public ApiResponse<List<DataSourceEntity>> list() {
        return ApiResponse.success(service.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<DataSourceEntity> getById(@PathVariable String id) {
        DataSourceEntity entity = service.getById(id);
        if (entity == null) {
            return ApiResponse.error(404, "数据源不存在: " + id);
        }
        return ApiResponse.success(entity);
    }

    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> testConnection(@PathVariable String id) {
        boolean ok = service.testConnection(id);
        return ApiResponse.success(Map.of("success", ok, "datasourceId", id));
    }

    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> testConnectionRaw(@RequestBody DataSourceDTO dto) {
        try {
            return ApiResponse.success(service.testConnectionRaw(dto));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(@PathVariable String id) {
        service.remove(id);
        return ApiResponse.success(null);
    }
}
