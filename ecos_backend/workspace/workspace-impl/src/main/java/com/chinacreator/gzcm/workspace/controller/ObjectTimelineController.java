package com.chinacreator.gzcm.workspace.controller;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.service.ObjectRuntimeService;

/**
 * 对象结构化 Timeline Controller — 替代旧 LIKE 审计日志查询。
 *
 * <p>使用 TimelineRepository 进行结构化查询，不再依赖 td_audit_log。
 *
 * <h3>端点</h3>
 * <pre>
 * GET /api/v1/ecos/objects/{entityCode}/{id}/timeline  — 结构化时间线（分页）
 * GET /api/v1/ecos/objects/{entityCode}/{id}/versions  — 版本列表
 * GET /api/v1/ecos/objects/{entityCode}/{id}/versions/{ver} — 特定版本
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/ecos/objects")
public class ObjectTimelineController {

    private static final Logger log = LoggerFactory.getLogger(ObjectTimelineController.class);

    private final ObjectRuntimeService runtimeService;

    public ObjectTimelineController(ObjectRuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    // ═══════════════ Timeline ═══════════════════

    /**
     * GET /{entityCode}/{id}/timeline?page=1&size=50
     * 查询对象的结构化时间线（按时间倒序，分页）。
     * 使用 TimelineRepository 替代旧 LIKE 查询。
     */
    @GetMapping("/{entityCode}/{id}/timeline")
    public ApiResponse<Map<String, Object>> getTimeline(
            @PathVariable String entityCode,
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        Map<String, Object> result = runtimeService.getTimeline(entityCode, id, page, size);
        return ApiResponse.success(result);
    }

    // ═══════════════ Version ═══════════════════

    /**
     * GET /{entityCode}/{id}/versions
     * 查询对象的版本历史列表。
     */
    @GetMapping("/{entityCode}/{id}/versions")
    public ApiResponse<List<Map<String, Object>>> getVersions(
            @PathVariable String entityCode,
            @PathVariable String id) {
        List<Map<String, Object>> versions = runtimeService.getVersions(entityCode, id);
        return ApiResponse.success(versions);
    }

    /**
     * GET /{entityCode}/{id}/versions/{ver}
     * 查询对象的特定版本详情。
     */
    @GetMapping("/{entityCode}/{id}/versions/{ver}")
    public ApiResponse<Map<String, Object>> getVersion(
            @PathVariable String entityCode,
            @PathVariable String id,
            @PathVariable int ver) {
        Map<String, Object> version = runtimeService.getVersion(entityCode, id, ver);
        if (version == null) {
            return ApiResponse.notFound("版本 v" + ver + " 不存在");
        }
        return ApiResponse.success(version);
    }

    // ═══════════════ Attachment ═══════════════════

    /**
     * GET /{entityCode}/{id}/attachments
     * 查询对象的附件列表。
     */
    @GetMapping("/{entityCode}/{id}/attachments")
    public ApiResponse<List<Map<String, Object>>> getAttachments(
            @PathVariable String entityCode,
            @PathVariable String id) {
        List<Map<String, Object>> attachments = runtimeService.getAttachments(entityCode, id);
        return ApiResponse.success(attachments);
    }
}
