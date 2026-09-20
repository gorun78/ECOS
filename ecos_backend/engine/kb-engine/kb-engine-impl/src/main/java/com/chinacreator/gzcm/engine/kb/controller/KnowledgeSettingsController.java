package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.KnowledgeSettingsService;
import com.chinacreator.gzcm.engine.kb.dto.ExtractUploadGateVO;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/v1/knowledge/settings")
public class KnowledgeSettingsController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSettingsController.class);
    private static final AtomicBoolean defaultsLoaded = new AtomicBoolean(false);

    private static final String[][] DEFAULTS = {
        {"knowledge.graph.defaultDomain", "default", "knowledge", "string", "Default graph domain"},
        {"knowledge.graph.maxNeighborDegree", "3", "knowledge", "int", "Max neighbor expansion degree"},
        {"knowledge.index.autoSyncEnabled", "true", "knowledge", "bool", "Auto sync toggle"},
        {"knowledge.index.batchSize", "500", "knowledge", "int", "Index batch size"},
        {"knowledge.rag.topK", "5", "knowledge", "int", "RAG recall TopK"},
        {"knowledge.rag.similarityThreshold", "0.7", "knowledge", "float", "RAG similarity threshold"},
        {"knowledge.rag.model", "text-embedding-3-small", "knowledge", "string", "RAG vector model"},
        {"knowledge.lineage.maxDepth", "10", "knowledge", "int", "Lineage max depth"},
        {"extract.allow_direct_upload", "false", "knowledge", "bool", "是否允许知识工作台直接上传临时文件（非结构化快路径，默认关闭）"},
        {"extract.page_limit", "500", "knowledge", "int", "结构化抽取单页行数"},
        {"extract.max_pages", "200", "knowledge", "int", "单资源最大页数"},
        {"extract.periodic_enabled", "false", "knowledge", "bool", "是否启用周期增量抽取（委托 runtime-task，本批次默认关闭）"},
    };

    /** 临时文件上传开关配置键（E2 门控端点与 ExtractionController.upload 共用）。 */
    private static final String KEY_ALLOW_DIRECT_UPLOAD = "extract.allow_direct_upload";

    /** 未开启时的提示语。 */
    private static final String HINT_DISABLED = "临时文件上传未开启：请在 引擎配置 → 知识抽取 中启用 allow_direct_upload";

    /** 已开启时的提示语。 */
    private static final String HINT_ENABLED = "临时文件上传已开启";

    @Autowired
    private KnowledgeSettingsService settingsService;

    @PostConstruct
    public void init() {
        if (defaultsLoaded.compareAndSet(false, true)) {
            try {
                for (String[] row : DEFAULTS) {
                    settingsService.upsertSetting(row[0], row[1], row[2], row[3], row[4]);
                }
                log.info("Knowledge settings defaults loaded ({} items)", DEFAULTS.length);
            } catch (Exception e) {
                log.warn("Failed to load knowledge defaults: {}", e.getMessage());
            }
        }
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getAll() {
        return ApiResponse.success(settingsService.getAllSettings());
    }

    @PutMapping
    public ApiResponse<Map<String, Object>> batchUpdate(@RequestBody List<Map<String, String>> updates) {
        int count = settingsService.batchUpdate(updates);
        return ApiResponse.success(Map.of("updated", count));
    }

    /**
     * E2 — 临时文件上传门控查询：前端据此决定上传入口可可用性。
     *
     * <p>路径即为最终裸路径（/api/v1/knowledge/extract/... 不做 v1 重写，
     * 与 {@code ExtractionController} 落点一致），业务数据端点默认 DENY，不写 permitAll。</p>
     */
    @GetMapping("/api/v1/knowledge/extract/upload-enabled")
    public ApiResponse<ExtractUploadGateVO> uploadEnabled() {
        boolean allowed = parseBoolTrue(settingsService.getSetting(KEY_ALLOW_DIRECT_UPLOAD));
        ExtractUploadGateVO vo = new ExtractUploadGateVO();
        vo.setAllowed(allowed);
        vo.setHint(allowed ? HINT_ENABLED : HINT_DISABLED);
        return ApiResponse.success(vo);
    }

    /** 布尔配置解析：含 "true"（忽略大小写）或 "1" 视为 true，其余 false。 */
    private static boolean parseBoolTrue(String raw) {
        if (raw == null) {
            return false;
        }
        String value = raw.trim();
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }
}