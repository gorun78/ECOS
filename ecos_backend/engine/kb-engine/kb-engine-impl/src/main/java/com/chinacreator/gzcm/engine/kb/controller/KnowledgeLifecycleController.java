package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.kb.dto.LifecycleAssetVO;
import com.chinacreator.gzcm.engine.kb.dto.LifecycleAuditEntryVO;
import com.chinacreator.gzcm.engine.kb.service.KnowledgeLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识资产生命周期控制器 — {@code GET /api/v1/knowledge/assets} 与
 * {@code GET /api/v1/knowledge/lifecycle/audit}（方案 §6.2 K6 知识治理 / 附录 B lifecycle Tab，B5-2 D6）。
 *
 * <p>数据全部来自真实表（knowledge_article / kg_sync_log），无 DEMO 假数据；
 * 支持 {@code pageNum}/{@code pageSize} 分页，资产支持 {@code status} 过滤。</p>
 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeLifecycleController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeLifecycleController.class);

    private final KnowledgeLifecycleService lifecycleService;

    public KnowledgeLifecycleController(KnowledgeLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    /**
     * 知识资产生命周期列表。
     *
     * @param status   生命周期状态过滤（可空；draft/active/deprecated/archived）
     * @param pageNum  页码（默认 1）
     * @param pageSize 每页条数（默认 20）
     */
    @GetMapping("/assets")
    public ApiResponse<List<LifecycleAssetVO>> listAssets(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        try {
            return ApiResponse.success(lifecycleService.listAssets(status, pageNum, pageSize));
        } catch (ValidationException e) {
            log.warn("生命周期资产查询入参非法: {}", e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("生命周期资产查询失败: {}", e.getMessage(), e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    /**
     * 生命周期审计列表（kg_sync_log 真实台账投影）。
     *
     * @param pageNum  页码（默认 1）
     * @param pageSize 每页条数（默认 20）
     */
    @GetMapping("/lifecycle/audit")
    public ApiResponse<List<LifecycleAuditEntryVO>> listAudit(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        try {
            return ApiResponse.success(lifecycleService.listAudit(pageNum, pageSize));
        } catch (Exception e) {
            log.error("生命周期审计查询失败: {}", e.getMessage(), e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }
}
