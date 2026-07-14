package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.datanet.model.DataField;
import com.chinacreator.gzcm.datanet.service.MetadataService;
import com.chinacreator.gzcm.engine.data.service.MetadataCollectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/datanet/metadata")
public class MetadataController {

    private static final Logger log = LoggerFactory.getLogger(MetadataController.class);

    private final MetadataService metadataService;
    private final MetadataCollectionService collectionService;

    public MetadataController(MetadataService metadataService,
                               MetadataCollectionService collectionService) {
        this.metadataService = metadataService;
        this.collectionService = collectionService;
    }

    @PostMapping("/collect/{datasourceId}")
    public ApiResponse<Map<String, Object>> collect(@PathVariable String datasourceId) {
        try {
            return ApiResponse.success(collectionService.collect(datasourceId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (Exception e) {
            log.error("Metadata collection failed: {}", e.getMessage(), e);
            return ApiResponse.error(500, "采集失败: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    @PostMapping("/collect")
    public ApiResponse<List<Map<String, Object>>> collectAll() {
        return ApiResponse.success(collectionService.collectAll());
    }

    @GetMapping("/fields/{resourceId}")
    public ApiResponse<List<DataField>> getFields(@PathVariable String resourceId) {
        return ApiResponse.success(metadataService.getFields(resourceId));
    }

    @GetMapping("/resources/{datasourceId}")
    public ApiResponse<?> getResources(@PathVariable String datasourceId) {
        return ApiResponse.success(collectionService.getResources(datasourceId));
    }

    @GetMapping("/resources/all")
    public ApiResponse<List<Map<String, Object>>> getAllResources() {
        return ApiResponse.success(collectionService.getAllResources());
    }

    @GetMapping("/preview/{resourceId}")
    public ApiResponse<Map<String, Object>> preview(@PathVariable String resourceId,
                                                     @RequestParam(defaultValue = "50") int limit) {
        try {
            return ApiResponse.success(collectionService.preview(resourceId, limit));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (Exception e) {
            log.error("Preview failed for {}: {}", resourceId, e.getMessage());
            return ApiResponse.error(500, "数据查询失败: " + e.getMessage());
        }
    }
}
