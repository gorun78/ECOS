package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.EcosKnowledgeGraphService;
import com.chinacreator.gzcm.engine.kb.dto.KgGraphSnapshotVO;
import com.chinacreator.gzcm.engine.kb.dto.KgNeo4jSyncResultVO;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Ecos 知识图谱通用兼容端点（{@code /api/v1/knowledge/ecos-graph/*}）。
 *
 * <p>PMO-74 H11-T3/T4：字段 {@code @Autowired} 改构造器注入；出参由
 * {@code Map<String,Object>} 收口为 {@link KgGraphSnapshotVO} /
 * {@link KgNeo4jSyncResultVO}（服务接口在 kb-engine-api 不可改签名，
 * 故在本 Controller 出口用 ObjectMapper 做 Map→VO 强类型映射，JSON 键名不变）。
 *
 * @group GRAPH
 */
@RestController
@RequestMapping("/api/v1/knowledge/ecos-graph")
public class EcosKnowledgeGraphController {

    private static final Logger log = LoggerFactory.getLogger(EcosKnowledgeGraphController.class);

    /** Map→VO 映射用：容忍未来新增键（与 Map 版响应的前向兼容语义一致） */
    private static final ObjectMapper MAPPER =
            new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final EcosKnowledgeGraphService ecosKgService;

    public EcosKnowledgeGraphController(EcosKnowledgeGraphService ecosKgService) {
        this.ecosKgService = ecosKgService;
    }

    @GetMapping
    public ApiResponse<KgGraphSnapshotVO> getGraph() {
        try {
            Map<String, Object> snapshot = ecosKgService.getGraphSnapshot();
            return ApiResponse.success(MAPPER.convertValue(snapshot, KgGraphSnapshotVO.class));
        } catch (Exception e) {
            log.error("Knowledge graph query failed", e);
            return ApiResponse.internalError("Knowledge graph query failed: " + e.getMessage());
        }
    }

    @PostMapping("/sync")
    public ApiResponse<KgNeo4jSyncResultVO> syncToNeo4j() {
        try {
            Map<String, Object> result = ecosKgService.syncToNeo4j();
            return ApiResponse.success(MAPPER.convertValue(result, KgNeo4jSyncResultVO.class));
        } catch (Exception e) {
            log.error("Knowledge graph sync failed", e);
            return ApiResponse.internalError("Sync failed: " + e.getMessage());
        }
    }
}
