package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.QueryHistoryService;
import com.chinacreator.gzcm.workspace.security.AbacQueryFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * ObjectQL 查询控制器 — 接受 JSON DSL 查询，解析后执行 SQL 并返回结果。
 *
 * <h3>端点</h3>
 * <pre>
 * POST /api/query/objectql
 * Body: {"query": "{\"entity\":\"Supplier\",\"filter\":{\"field\":\"creditScore\",\"op\":\">=\",\"value\":80}}"}
 * Response: {"code":0,"data":[{"id":"s1","name":"优质供应商A","creditScore":92},...]}
 * </pre>
 */
@RestController
@RequestMapping("/api/query")
public class ObjectQLController {

    private static final Logger log = LoggerFactory.getLogger(ObjectQLController.class);
    private final JdbcTemplate jdbc;
    private final QueryHistoryService historyService;
    private final AbacQueryFilter abacFilter;

    public ObjectQLController(JdbcTemplate jdbc, QueryHistoryService historyService,
                               AbacQueryFilter abacFilter) {
        this.jdbc = jdbc;
        this.historyService = historyService;
        this.abacFilter = abacFilter;
    }

    /**
     * 执行 ObjectQL 查询。
     *
     * @param body {"query": "JSON 查询字符串"}
     * @return 查询结果列表
     */
    @PostMapping("/objectql")
    public ApiResponse<List<Map<String, Object>>> query(@RequestBody Map<String, Object> body) {
        try {
            // 1. 提取 query 字符串
            Object queryObj = body.get("query");
            if (queryObj == null) {
                return ApiResponse.badRequest("缺少 query 字段");
            }
            String queryJson = queryObj.toString();

            // 1b. 安全校验：links 中的 target entity 必须在 ecos_ontology_entity 表中存在
            List<String> linkEntities = ObjectQLParser.extractLinkEntities(queryJson);
            for (String linkEntity : linkEntities) {
                Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecos_ontology_entity WHERE code = ?",
                    Integer.class, linkEntity);
                if (count == null || count == 0) {
                    return ApiResponse.badRequest("link 目标实体不存在: " + linkEntity);
                }
            }

            // 2. 解析
            ObjectQLParser.ParsedQuery pq = ObjectQLParser.parse(queryJson);

            // ★ ABAC 行过滤：从查询 JSON 中提取 entity 名，注入行过滤条件
            String entityCode = extractEntityFromQueryJson(queryJson);
            if (entityCode != null) {
                String abacRowFilter = abacFilter.buildRowFilterCondition(entityCode);
                if (!abacRowFilter.isEmpty()) {
                    pq = ObjectQLParser.appendWhereCondition(pq, abacRowFilter);
                }
            }

            log.info("ObjectQL: {} | params={}", pq.getSql(), pq.getParams());

            // 3. 执行
            List<Map<String, Object>> rows;
            if (pq.getParams().isEmpty()) {
                rows = jdbc.queryForList(pq.getSql());
            } else {
                rows = jdbc.queryForList(pq.getSql(), pq.getParamsArray());
            }

            // ★ ABAC 列裁剪
            if (entityCode != null) {
                rows = abacFilter.filterColumns(entityCode, rows);
            }

            // 4. 字段名转小写，方便前端使用
            List<Map<String, Object>> normalized = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                for (Map.Entry<String, Object> e : row.entrySet()) {
                    m.put(e.getKey() != null ? e.getKey().toLowerCase() : null, e.getValue());
                }
                normalized.add(m);
            }

            // 5. 保存查询历史
            String question = queryJson.length() > 100 ? queryJson.substring(0, 100) + "..." : queryJson;
            historyService.save(question, queryJson, normalized.size());

            return ApiResponse.success(normalized);
        } catch (IllegalArgumentException e) {
            log.warn("ObjectQL 参数错误: {}", e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("ObjectQL 查询失败", e);
            return ApiResponse.internalError("查询执行失败: " + e.getMessage());
        }
    }

    /**
     * 从 ObjectQL JSON 查询字符串中提取 entity 字段值。
     */
    private String extractEntityFromQueryJson(String queryJson) {
        try {
            // 简单字符串提取：查找 "entity" 后的值
            int idx = queryJson.indexOf("\"entity\"");
            if (idx < 0) return null;
            int colonIdx = queryJson.indexOf(':', idx);
            if (colonIdx < 0) return null;
            String rest = queryJson.substring(colonIdx + 1).trim();
            // 提取引号内的值
            if (rest.startsWith("\"")) {
                int endQuote = rest.indexOf('"', 1);
                if (endQuote > 0) {
                    return rest.substring(1, endQuote);
                }
            }
        } catch (Exception e) {
            log.debug("提取 entity 字段失败: {}", e.getMessage());
        }
        return null;
    }
}
