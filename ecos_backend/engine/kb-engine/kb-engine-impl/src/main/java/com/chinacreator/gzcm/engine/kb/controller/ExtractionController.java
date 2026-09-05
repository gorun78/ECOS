package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.service.KnowledgeExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 知识抽取控制器 — 文档上传、状态查询、审核（通过/驳回）。
 *
 * @author ECOS KB Engine Team
 * @since 2026-08-08, 2026-09-02 (Wave-2C)
 */
@RestController
@RequestMapping("/api/v1/knowledge/extract")
public class ExtractionController {

    private static final Logger log = LoggerFactory.getLogger(ExtractionController.class);
    private final KnowledgeExtractionService extractionService;

    public ExtractionController(KnowledgeExtractionService extractionService) {
        this.extractionService = extractionService;
    }

    /**
     * 上传文档，启动抽取管道。
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> result = extractionService.upload(file);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("上传失败: {}", e.getMessage(), e);
            return ApiResponse.badRequest("上传失败: " + e.getMessage());
        }
    }

    /**
     * 抽取任务列表（分页）。
     */
    @GetMapping("/tasks")
    public ApiResponse<List<Map<String, Object>>> listTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        try {
            List<Map<String, Object>> tasks = extractionService.listTasks(page, pageSize);
            return ApiResponse.success(tasks);
        } catch (Exception e) {
            log.error("查询任务列表失败: {}", e.getMessage(), e);
            return ApiResponse.badRequest("查询失败: " + e.getMessage());
        }
    }

    /**
     * 单个任务详情。
     */
    @GetMapping("/tasks/{id}")
    public ApiResponse<Map<String, Object>> getTask(@PathVariable String id) {
        try {
            Map<String, Object> task = extractionService.getTask(id);
            return ApiResponse.success(task);
        } catch (Exception e) {
            log.error("查询任务失败: id={}, {}", id, e.getMessage());
            return ApiResponse.notFound("任务不存在: " + id);
        }
    }

    /**
     * 审核通过 — 实体写Neo4j + 规则写compliance_rules + 实体链接本体。
     * Wave-2C: 返回结构化 ApprovalOutcome { status, counts, rejectedReasons }
     */
    @PostMapping("/{id}/approve")
    public ApiResponse<Map<String, Object>> approve(@PathVariable String id) {
        try {
            Map<String, Object> result = extractionService.approve(id);
            return ApiResponse.success(result);
        } catch (IllegalStateException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("审核通过失败: id={}, {}", id, e.getMessage());
            return ApiResponse.badRequest("审核失败: " + e.getMessage());
        }
    }

    /**
     * 审核驳回 — Wave-2C: 支持 reason 参数。
     * 请求体: { reason: "..." }, 可选。
     */
    @PostMapping("/{id}/reject")
    public ApiResponse<Map<String, Object>> reject(@PathVariable String id,
                                                    @RequestBody(required = false) Map<String, String> body) {
        try {
            String reason = body != null ? body.getOrDefault("reason", "no reason provided") : "no reason provided";
            Map<String, Object> result = extractionService.reject(id, reason);
            return ApiResponse.success(result);
        } catch (IllegalStateException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("驳回失败: id={}, {}", id, e.getMessage());
            return ApiResponse.badRequest("驳回失败: " + e.getMessage());
        }
    }
}
