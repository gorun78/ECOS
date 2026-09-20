package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.KnowledgeSettingsService;
import com.chinacreator.gzcm.engine.kb.dto.ExtractCandidateVO;
import com.chinacreator.gzcm.engine.kb.dto.ExtractFileVO;
import com.chinacreator.gzcm.engine.kb.dto.ExtractionApproveResultVO;
import com.chinacreator.gzcm.engine.kb.dto.ExtractionPromoteRequest;
import com.chinacreator.gzcm.engine.kb.dto.ExtractionPromoteResultVO;
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

    /** 临时文件上传开关配置键（引擎配置 → 知识抽取）。 */
    private static final String KEY_ALLOW_DIRECT_UPLOAD = "extract.allow_direct_upload";

    private final KnowledgeExtractionService extractionService;

    private final KnowledgeSettingsService settingsService;

    public ExtractionController(KnowledgeExtractionService extractionService,
                                KnowledgeSettingsService settingsService) {
        this.extractionService = extractionService;
        this.settingsService = settingsService;
    }

    /**
     * 上传文档，启动抽取管道。
     *
     * <p>K1 gate：开关 {@code extract.allow_direct_upload} 关闭时直接 400 拒绝，
     * 非结构化快路径默认走 DW 层登记通道，不走临时文件直传。</p>
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        boolean allowed = "true".equalsIgnoreCase(settingsService.getSetting(KEY_ALLOW_DIRECT_UPLOAD));
        if (!allowed) {
            return ApiResponse.badRequest("临时文件上传未开启：请在引擎配置 → 知识抽取 中启用 allow_direct_upload");
        }
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
     * 待审核抽取文件列表（B5-2 / D6，前端 review Tab）— 真实表 extraction_drafts，分页 + 状态过滤。
     */
    @GetMapping("/files")
    public ApiResponse<List<ExtractFileVO>> listFiles(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        try {
            List<ExtractFileVO> files = extractionService.listExtractFiles(status, pageNum, pageSize);
            return ApiResponse.success(files);
        } catch (Exception e) {
            log.error("查询待审核文件列表失败: {}", e.getMessage(), e);
            return ApiResponse.badRequest("查询失败: " + e.getMessage());
        }
    }

    /**
     * 抽取候选清单（B5-2 / D6，前端 review Tab）— 展开 extraction_drafts 三类抽取 JSON。
     */
    @GetMapping("/candidates/{fileId}")
    public ApiResponse<Map<String, List<ExtractCandidateVO>>> listCandidates(@PathVariable String fileId) {
        try {
            Map<String, List<ExtractCandidateVO>> payload = new java.util.LinkedHashMap<>();
            payload.put("candidates", extractionService.listCandidates(fileId));
            return ApiResponse.success(payload);
        } catch (Exception e) {
            log.error("查询抽取候选失败: fileId={}, {}", fileId, e.getMessage(), e);
            return ApiResponse.badRequest("查询失败: " + e.getMessage());
        }
    }

    /**
     * 候选审核入图（方案 §5.3 新增端点）— 委托既有 {@code approve}，
     * 审核通过后候选写入 graph_node/graph_edge（复用 build 台账，不重复实现写入逻辑）。
     */
    @PostMapping("/candidates/{fileId}/approve")
    public ApiResponse<ExtractionApproveResultVO> approveCandidates(@PathVariable String fileId) {
        try {
            return ApiResponse.success(toApproveResult(extractionService.approve(fileId)));
        } catch (IllegalStateException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("候选审核入图失败: fileId={}, {}", fileId, e.getMessage(), e);
            return ApiResponse.badRequest("审核失败: " + e.getMessage());
        }
    }

    /** 既有 approve 匿名结果为强类型 VO（保持接口出入参强类型，铁律 §后端规范）。 */
    private ExtractionApproveResultVO toApproveResult(Map<String, Object> raw) {
        ExtractionApproveResultVO vo = new ExtractionApproveResultVO();
        vo.setId(raw.get("id") == null ? null : String.valueOf(raw.get("id")));
        vo.setStatus(raw.get("status") == null ? null : String.valueOf(raw.get("status")));
        Object counts = raw.get("counts");
        if (counts instanceof Map<?, ?> countMap) {
            vo.setRules(intOf(countMap.get("rules")));
            vo.setEntities(intOf(countMap.get("entities")));
            vo.setLinks(intOf(countMap.get("links")));
        }
        Object rejected = raw.get("rejectedReasons");
        if (rejected instanceof List<?> list) {
            for (Object item : list) {
                vo.getRejectedReasons().add(String.valueOf(item));
            }
        }
        return vo;
    }

    private int intOf(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
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

    /**
     * 抽取实体转候选本体（PMO-50 T5）— 审核面板选中实体 → ontology「本体变更提案」。
     *
     * <p>单实体失败不中止整批，失败明细见返回体 {@code failures}。</p>
     */
    @PostMapping("/promote-to-candidate")
    public ApiResponse<ExtractionPromoteResultVO> promoteToCandidate(@RequestBody ExtractionPromoteRequest request) {
        try {
            return ApiResponse.success(extractionService.promoteToCandidate(request));
        } catch (Exception e) {
            log.error("转候选本体失败: {}", e.getMessage(), e);
            return ApiResponse.badRequest("转候选本体失败: " + e.getMessage());
        }
    }
}
