package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.chinacreator.gzcm.engine.ontology.dto.OntologySourceQuery;
import com.chinacreator.gzcm.engine.ontology.dto.OntologySourceVO;
import com.chinacreator.gzcm.engine.ontology.service.OntologySourceService;

/**
 * 本体对象来源聚合查询 — PMO-40 批次3 T4a → T16-2 强类型重构。
 *
 * <p>聚合 {@code ecos_entity_table_mapping} 表，按本体实体维度返回
 * 来源域（sourceDomain）、原始表（originManifest 即 resource_name）、创建时间。
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>GET /api/v1/ecos/ontology/sources — 本体对象来源聚合列表</li>
 * </ul>
 *
 * <p>T16-2 (2026-09-12)：
 * <ul>
 *   <li>SQL 从 Controller 抽到 {@link OntologySourceService}（Controller 不再持 JdbcTemplate，
 *       与"Controller 不写业务"铁律对齐）</li>
 *   <li>返回强类型 {@link OntologySourceVO}（替代 {@code Map<String,Object>}）</li>
 * </ul>
 */
@RestController
@RequestMapping({"/api/v1/ecos/ontology", "/api/ecos/ontology"})
public class OntologySourceController {

    private static final Logger log = LoggerFactory.getLogger(OntologySourceController.class);

    private final OntologySourceService sourceService;

    public OntologySourceController(OntologySourceService sourceService) {
        this.sourceService = sourceService;
    }

    // ═══════════════════════════════════════════════════════════════
    //  GET /api/v1/ecos/ontology/sources — 本体对象来源聚合
    // ═══════════════════════════════════════════════════════════════

    /**
     * 查询本体对象的来源信息聚合（强类型 VO）。
     *
     * @param domainCode 可选，按业务域过滤
     * @param limit      返回行数上限，默认 100，最大 500
     */
    @GetMapping("/sources")
    public ApiResponse<List<OntologySourceVO>> listSources(
            @RequestParam(required = false) String domainCode,
            @RequestParam(defaultValue = "100") int limit) {
        try {
            OntologySourceQuery query = new OntologySourceQuery();
            query.setDomainCode(domainCode);
            query.setLimit(limit);
            return ApiResponse.success(sourceService.listSources(query));
        } catch (Exception e) {
            log.error("Failed to query ontology sources: {}", e.getMessage(), e);
            return ApiResponse.internalError("查询本体来源失败: " + e.getMessage());
        }
    }
}
