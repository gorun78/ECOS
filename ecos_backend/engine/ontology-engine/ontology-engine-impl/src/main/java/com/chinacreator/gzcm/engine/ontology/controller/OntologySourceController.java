package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 本体对象来源聚合查询 — PMO-40 批次3 T4a。
 *
 * <p>聚合 {@code ecos_entity_table_mapping} 表，按本体实体维度返回
 * 来源域（sourceDomain）、原始表（originManifest 即 resource_name）、创建时间。
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>GET /api/v1/ecos/ontology/sources — 本体对象来源聚合列表</li>
 * </ul>
 *
 * @author PMO-40 Batch3
 */
@RestController
@RequestMapping({"/api/v1/ecos/ontology", "/api/ecos/ontology"})
public class OntologySourceController {

    private static final Logger log = LoggerFactory.getLogger(OntologySourceController.class);

    private final JdbcTemplate jdbc;

    public OntologySourceController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ═══════════════════════════════════════════════════════════════
    //  GET /api/v1/ecos/ontology/sources — 本体对象来源聚合
    // ═══════════════════════════════════════════════════════════════

    /**
     * 查询本体对象的来源信息聚合。
     *
     * <p>数据来源：{@code ecos_entity_table_mapping} JOIN
     * {@code ecos_ontology_entity} JOIN {@code ecos_domain}。</p>
     *
     * @param domainCode 可选，按业务域过滤
     * @param limit      返回行数上限，默认 100，最大 500
     */
    @GetMapping("/sources")
    public ApiResponse<List<Map<String, Object>>> listSources(
            @RequestParam(required = false) String domainCode,
            @RequestParam(defaultValue = "100") int limit) {
        try {
            int lim = Math.max(1, Math.min(limit, 500));

            String sql = "SELECT " +
                "  e.id          AS \"ontologyId\", " +
                "  d.code        AS \"sourceDomain\", " +
                "  m.resource_name AS \"originManifest\", " +
                "  e.created_at  AS \"createdAt\", " +
                "FROM ecos_entity_table_mapping m " +
                "INNER JOIN ecos_ontology_entity e " +
                "  ON e.code = m.entity_code " +
                "INNER JOIN ecos_domain d " +
                "  ON d.id = e.domain_id " +
                "  AND d.code = m.domain_code";

            if (domainCode != null && !domainCode.isBlank()) {
                sql += " WHERE m.domain_code = ?";
            }
            sql += " ORDER BY m.created_at DESC LIMIT ?";

            List<Map<String, Object>> rows;
            if (domainCode != null && !domainCode.isBlank()) {
                rows = jdbc.queryForList(sql, domainCode, lim);
            } else {
                rows = jdbc.queryForList(sql, lim);
            }

            return ApiResponse.success(rows);
        } catch (Exception e) {
            log.error("Failed to query ontology sources: {}", e.getMessage(), e);
            return ApiResponse.internalError("查询本体来源失败: " + e.getMessage());
        }
    }
}
