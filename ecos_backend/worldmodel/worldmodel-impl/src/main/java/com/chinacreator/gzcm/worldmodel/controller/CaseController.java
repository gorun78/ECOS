package com.chinacreator.gzcm.worldmodel.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.*;

/**
 * P2-5 Case Controller — 自学习案例库 CRUD + 检索。
 */
@RestController
@RequestMapping("/cases")
public class CaseController {

    private static final Logger log = LoggerFactory.getLogger(CaseController.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public CaseController(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    // ═══ POST /cases/record ═══
    @PostMapping("/record")
    public ApiResponse<Map<String, Object>> record(@RequestBody Map<String, Object> body) {
        try {
            String title = (String) body.getOrDefault("title", "Untitled");
            String scenario = (String) body.getOrDefault("scenario", "");
            String source = (String) body.getOrDefault("source", "manual");
            String feedback = (String) body.getOrDefault("feedback", "pending");

            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) body.getOrDefault("tags", List.of());
            String[] tagArray = tags.toArray(new String[0]);

            String decision = mapper.writeValueAsString(body.getOrDefault("decision", Map.of()));
            String result = mapper.writeValueAsString(body.getOrDefault("result", Map.of()));

            String sql = "INSERT INTO ecos_decision_case (title, scenario, tags, decision, result, feedback, source, created_at, updated_at) " +
                         "VALUES (?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, NOW(), NOW()) RETURNING id";

            Long id = jdbc.queryForObject(sql, Long.class,
                title, scenario, tagArray, decision, result, feedback, source);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("id", id);
            resp.put("title", title);
            resp.put("status", "recorded");
            log.info("Case recorded: id={}, title={}", id, title);
            return ApiResponse.success(resp);
        } catch (Exception e) {
            log.error("Failed to record case", e);
            return ApiResponse.internalError("案例记录失败: " + e.getMessage());
        }
    }

    // ═══ GET /cases ═══
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        String sql = "SELECT id, title, scenario, tags, feedback, source, created_at " +
                     "FROM ecos_decision_case ORDER BY created_at DESC LIMIT 50";
        List<Map<String, Object>> rows = jdbc.query(sql, (rs, _i) -> rowToMap(rs));
        return ApiResponse.success(rows);
    }

    // ═══ GET /cases/{id} ═══
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id) {
        String sql = "SELECT * FROM ecos_decision_case WHERE id = ?";
        try {
            List<Map<String, Object>> rows = jdbc.query(sql, (rs, _i) -> rowToMapFull(rs), id);
            if (rows.isEmpty()) return ApiResponse.notFound("案例 " + id + " 不存在");
            return ApiResponse.success(rows.get(0));
        } catch (Exception e) {
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    // ═══ GET /cases/search?q=X&k=5 ═══
    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int k) {

        try {
            String searchTerm = "%" + q.toLowerCase() + "%";

            // Simple keyword match: title LIKE + scenario LIKE
            String sql = """
                SELECT id, title, scenario, tags, feedback, source, created_at,
                       (CASE WHEN lower(title) LIKE ? THEN 3 ELSE 0 END +
                        CASE WHEN lower(scenario) LIKE ? THEN 2 ELSE 0 END)
                       AS relevance
                FROM ecos_decision_case
                WHERE lower(title) LIKE ? OR lower(scenario) LIKE ?
                ORDER BY relevance DESC, created_at DESC
                LIMIT ?
                """;

            List<Map<String, Object>> rows = jdbc.query(sql, (rs, _i) -> {
                Map<String, Object> m = rowToMap(rs);
                m.put("relevance", rs.getInt("relevance"));
                return m;
            }, searchTerm, searchTerm, searchTerm, searchTerm, k);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("query", q);
            resp.put("total", rows.size());
            resp.put("results", rows);
            return ApiResponse.success(resp);
        } catch (Exception e) {
            log.error("Search failed for q={}", q, e);
            return ApiResponse.internalError("搜索失败: " + e.getMessage());
        }
    }

    // ═══ Helpers ═══
    private Map<String, Object> rowToMap(ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getLong("id"));
        m.put("title", rs.getString("title"));
        m.put("scenario", rs.getString("scenario"));
        m.put("feedback", rs.getString("feedback"));
        m.put("source", rs.getString("source"));
        Timestamp ts = rs.getTimestamp("created_at");
        m.put("created_at", ts != null ? ts.toLocalDateTime().toString() : null);

        // tags array (nullable)
        java.sql.Array arr = rs.getArray("tags");
        m.put("tags", arr != null ? Arrays.asList((String[]) arr.getArray()) : List.of());
        return m;
    }

    private Map<String, Object> rowToMapFull(ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> m = rowToMap(rs);
        m.put("decision", rs.getString("decision"));
        m.put("result", rs.getString("result"));
        return m;
    }
}
