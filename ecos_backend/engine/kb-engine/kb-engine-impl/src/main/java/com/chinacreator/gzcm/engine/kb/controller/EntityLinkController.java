package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.dto.EntityLinkRequestDTO;
import com.chinacreator.gzcm.engine.kb.dto.EntityLinkResultVO;
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
 * @group WIKI
 */
@RestController
// F04-02 (2026-09-30)：K-12 组合畸形修复——保留旧组合路径 /api/v1/knowledge/entity-link/entity/link，
// 新正典路径 /api/v1/knowledge/entity/link 并入 /api/v1/knowledge 家族（方法级一律相对路径）。
@RequestMapping({"/api/v1/knowledge/entity-link", "/api/v1/knowledge"})
public class EntityLinkController {

    private static final Logger log = LoggerFactory.getLogger(EntityLinkController.class);
    private final EntityLinkerService entityLinkerService;

    public EntityLinkController(EntityLinkerService entityLinkerService) {
        this.entityLinkerService = entityLinkerService;
    }

    /**
     * 手动触发实体链接 — 输入实体名+类型，返回本体映射结果。
     *
     * <p>PMO-74 H11-T4：出入参由 {@code Map<String,Object>} 收口为
     * {@link EntityLinkRequestDTO} / {@link EntityLinkResultVO}（wire JSON 键名不变）。</p>
     */
    @PostMapping({"/entity/link", "/entity-link/entity/link"})
    @RequirePermission(permission = "knowledge:entity-link:write")
    public ApiResponse<EntityLinkResultVO> linkEntity(@RequestBody EntityLinkRequestDTO request) {
        try {
            String entityName = request.getEntityName();
            String entityType = request.getEntityType() != null ? request.getEntityType() : "unknown";
            if (entityName == null || entityName.isBlank()) {
                return ApiResponse.badRequest("entityName is required");
            }
            Map<String, Object> result = entityLinkerService.linkEntity(entityName, entityType);
            EntityLinkResultVO vo = new EntityLinkResultVO();
            vo.setEntityName(asString(result.get("entityName")));
            vo.setEntityType(asString(result.get("entityType")));
            vo.setOntologyPath(asString(result.get("ontologyPath")));
            Object conf = result.get("confidence");
            vo.setConfidence(conf instanceof Number n ? n.doubleValue() : null);
            vo.setMessage(asString(result.get("message")));
            return ApiResponse.success(vo);
        } catch (Exception e) {
            log.error("实体链接失败: {}", e.getMessage(), e);
            return ApiResponse.badRequest("链接失败: " + e.getMessage());
        }
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
