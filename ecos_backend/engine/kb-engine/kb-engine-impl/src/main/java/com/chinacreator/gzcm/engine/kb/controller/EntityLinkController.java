package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.service.EntityLinkerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 实体链接控制器 — 手动/批量触发实体到本体类型的映射。
 *
 * @author ECOS KB Engine Team
 * @since 2026-08-08
 */
@RestController
@RequestMapping("/api/v1/kb")
public class EntityLinkController {

    private static final Logger log = LoggerFactory.getLogger(EntityLinkController.class);
    private final EntityLinkerService entityLinkerService;

    public EntityLinkController(EntityLinkerService entityLinkerService) {
        this.entityLinkerService = entityLinkerService;
    }

    /**
     * 手动触发实体链接 — 输入实体名+类型，返回本体映射结果。
     */
    @PostMapping("/entity/link")
    public ApiResponse<Map<String, Object>> linkEntity(@RequestBody Map<String, Object> request) {
        try {
            String entityName = (String) request.get("entityName");
            String entityType = (String) request.getOrDefault("entityType", "unknown");
            if (entityName == null || entityName.isBlank()) {
                return ApiResponse.badRequest("entityName is required");
            }
            Map<String, Object> result = entityLinkerService.linkEntity(entityName, entityType);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("实体链接失败: {}", e.getMessage(), e);
            return ApiResponse.badRequest("链接失败: " + e.getMessage());
        }
    }
}
