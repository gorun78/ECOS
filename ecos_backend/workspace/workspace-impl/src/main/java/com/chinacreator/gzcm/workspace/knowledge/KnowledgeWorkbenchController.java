package com.chinacreator.gzcm.workspace.knowledge;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.workspace.knowledge.dto.KnowledgeEngineConfigVO;
import com.chinacreator.gzcm.workspace.knowledge.dto.KnowledgeHealthVO;
import com.chinacreator.gzcm.workspace.knowledge.dto.KnowledgeStatsVO;
import com.chinacreator.gzcm.workspace.knowledge.service.KnowledgeConfigAggregator;
import com.chinacreator.gzcm.workspace.knowledge.service.KnowledgeHealthAggregator;
import com.chinacreator.gzcm.workspace.knowledge.service.KnowledgeStatsAggregator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 知识工作台聚合 Controller — E-A#1/#2/#3 产物。
 *
 * <p>WM 基石三端点（前端 15 Tab 对齐 knowledgeApi.ts，统一走 apiFetchData）：
 * <ul>
 *   <li>{@code GET  /api/v1/knowledge/health}          — 6 引擎 + PG 主库/向量库状态条</li>
 *   <li>{@code GET  /api/v1/knowledge/stats}           — 13 计数 + 时间戳</li>
 *   <li>{@code GET  /api/v1/knowledge/engine-config}   — scope 分发 (knowledge/cognitive/all) 读</li>
 *   <li>{@code PUT  /api/v1/knowledge/engine-config}   — scope 分发 (knowledge/cognitive) 写</li>
 * </ul>
 *
 * <p>路径沿用 xb 层 {@code com.chinacreator.gzcm.engine.kb.controller.KnowledgeApiController}
 * 的 {@code /api/v1/knowledge} 基础前缀（铁律 §1.1 新增端点不冲突已有）。
 *
 * <p>降级策略：单引擎 DOWN 不 500；stats 表缺失 0 占位；engine-config 远端 fail → 0 项+_remote_failed。
 *
 * @author ecos-factory
 * @since PMO-55 批次 E-A
 */
@RestController
@RequestMapping("/api/v1/knowledge")
@Tag(name = "Knowledge Workbench (Workspace)")
public class KnowledgeWorkbenchController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeWorkbenchController.class);

    private final KnowledgeHealthAggregator healthAggregator;
    private final KnowledgeStatsAggregator statsAggregator;
    private final KnowledgeConfigAggregator configAggregator;

    public KnowledgeWorkbenchController(KnowledgeHealthAggregator healthAggregator,
                                        KnowledgeStatsAggregator statsAggregator,
                                        KnowledgeConfigAggregator configAggregator) {
        this.healthAggregator = healthAggregator;
        this.statsAggregator = statsAggregator;
        this.configAggregator = configAggregator;
    }

    @Operation(summary = "知识工作台健康检查聚合",
            description = "6 引擎 (kb/ai/security/data/ontology/cognitive) + PG 主库 + 向量库")
    @GetMapping("/health")
    public ApiResponse<KnowledgeHealthVO> health() {
        try {
            return ApiResponse.success(healthAggregator.aggregate());
        } catch (Exception ex) {
            log.error("knowledge workbench health aggregation failed", ex);
            return ApiResponse.internalError("health aggregation failed: " + shortMsg(ex));
        }
    }

    @Operation(summary = "知识工作台数据量统计", description = "13 字段 + lastUpdatedAt（PG 只读）")
    @GetMapping("/stats")
    public ApiResponse<KnowledgeStatsVO> stats() {
        try {
            return ApiResponse.success(statsAggregator.aggregate());
        } catch (Exception ex) {
            log.error("knowledge workbench stats aggregation failed", ex);
            return ApiResponse.internalError("stats aggregation failed: " + shortMsg(ex));
        }
    }

    @Operation(summary = "读引擎配置（scope 分发）",
            description = "scope=knowledge 转 :18086/settings / scope=cognitive 转 :18084/cognitive/config / scope=all 并行 + 本地 sys_config")
    @GetMapping("/engine-config")
    public ApiResponse<KnowledgeEngineConfigVO> readEngineConfig(
            @RequestParam(value = "scope", defaultValue = "all") String scope) {
        try {
            return ApiResponse.success(configAggregator.read(scope));
        } catch (BusinessException bex) {
            return ApiResponse.badRequest(bex.getMessage());
        } catch (Exception ex) {
            log.error("knowledge engine-config read failed scope={}", scope, ex);
            return ApiResponse.internalError("engine-config read failed: " + shortMsg(ex));
        }
    }

    /**
     * PUT 入参：{@code { "knowledge": {}, "cognitive": {}, "scope": "knowledge" }}
     * 或 {@code { "scope": "knowledge", items: {key: value, ...} }}
     */
    public static class EngineConfigUpdateReq {
        /** 目标 scope：knowledge 或 cognitive */
        private String scope;
        /** key→value 映射（手动约定） */
        private Map<String, String> items;
        /** 兼容单 scope 直传：entries 直接是 key→value */
        private Map<String, String> entries;

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        public Map<String, String> getItems() {
            return items;
        }

        public void setItems(Map<String, String> items) {
            this.items = items;
        }

        public Map<String, String> getEntries() {
            return entries;
        }

        public void setEntries(Map<String, String> entries) {
            this.entries = entries;
        }
    }

    @Operation(summary = "写引擎配置（scope 分发 PUT）",
            description = "scope 必填 knowledge/cognitive；items 为 key→value")
    @PutMapping("/engine-config")
    public ApiResponse<Map<String, Object>> writeEngineConfig(@RequestBody EngineConfigUpdateReq req) {
        if (req == null || req.getScope() == null || req.getScope().isBlank()) {
            return ApiResponse.badRequest("scope required: knowledge | cognitive");
        }
        Map<String, String> payload = req.getEntries() != null && !req.getEntries().isEmpty()
                ? req.getEntries()
                : req.getItems();
        if (payload == null || payload.isEmpty()) {
            return ApiResponse.badRequest("items or entries required: {key: value, ...}");
        }
        try {
            int updated = configAggregator.update(req.getScope(), payload);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("updated", updated);
            return ApiResponse.success("updated " + updated + " entries", data);
        } catch (BusinessException bex) {
            log.warn("engine-config write rejected: {}", bex.getMessage());
            return ApiResponse.badRequest(bex.getMessage());
        } catch (Exception ex) {
            log.error("knowledge engine-config write failed scope={}", req.getScope(), ex);
            return ApiResponse.internalError("engine-config write failed: " + shortMsg(ex));
        }
    }

    private static String shortMsg(Throwable t) {
        String msg = t.getClass().getSimpleName();
        if (t.getMessage() != null) {
            msg = msg + ": " + t.getMessage();
        }
        return msg.length() > 160 ? msg.substring(0, 160) : msg;
    }
}
