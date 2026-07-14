package com.chinacreator.gzcm.worldmodel.service;

import com.chinacreator.gzcm.common.service.IGraphService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A1: PostgreSQL 图服务实现 — 标准版。
 *
 * <p>标准版不部署 Neo4j，改用 ecos_objects + ecos_object_links 表
 * 模拟图查询。提供简单 Cypher→SQL 翻译。</p>
 *
 * <p>表结构预期:
 * <ul>
 *   <li><b>ecos_objects</b>: id, label, type, category, name, properties (JSONB), created_at</li>
 *   <li><b>ecos_object_links</b>: id, source_id, target_id, rel_type, strength, properties (JSONB), created_at</li>
 * </ul>
 */
@Service
@Profile("standard")
public class PgGraphService implements IGraphService {

    private static final Logger log = LoggerFactory.getLogger(PgGraphService.class);

    private final JdbcTemplate jdbc;

    public PgGraphService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ═══════════════════════════════════════════════════════
    //  IGraphService 实现
    // ═══════════════════════════════════════════════════════

    @Override
    public List<Map<String, Object>> query(String cypherPattern, Map<String, Object> params) {
        String sql = translateCypherToSql(cypherPattern, params);
        log.debug("PG Cypher→SQL: {}  =>  {}", cypherPattern, sql);
        try {
            return jdbc.queryForList(sql, extractSqlParams(params));
        } catch (Exception e) {
            log.error("PG graph query failed: {}", sql, e);
            return List.of();
        }
    }

    @Override
    public void createNode(String label, Map<String, Object> props) {
        String id = props != null && props.containsKey("id")
                ? String.valueOf(props.get("id"))
                : UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String name = props != null && props.containsKey("name")
                ? String.valueOf(props.get("name")) : "";
        String type = props != null && props.containsKey("type")
                ? String.valueOf(props.get("type")) : "";
        String category = props != null && props.containsKey("category")
                ? String.valueOf(props.get("category")) : "";
        jdbc.update(
            "INSERT INTO ecos_objects (id, label, type, category, name, properties, created_at) " +
            "VALUES (?, ?, ?, ?, ?, '{}'::jsonb, NOW()) " +
            "ON CONFLICT (id) DO UPDATE SET label=EXCLUDED.label, type=EXCLUDED.type, " +
            "category=EXCLUDED.category, name=EXCLUDED.name",
            id, label, type, category, name
        );
        log.debug("PG node created: label={}, id={}", label, id);
    }

    @Override
    public void createRelationship(String fromId, String toId, String relType) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        jdbc.update(
            "INSERT INTO ecos_object_links (id, source_id, target_id, rel_type, strength, properties, created_at) " +
            "VALUES (?, ?, ?, ?, 0.5, '{}'::jsonb, NOW())",
            id, fromId, toId, relType
        );
        log.debug("PG relationship created: {} --[{}]--> {}", fromId, relType, toId);
    }

    @Override
    public Map<String, Object> getSubgraph(String entityId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 查询中心节点及关联节点
        List<Map<String, Object>> nodes = jdbc.queryForList(
            "SELECT o1.*, o2.id   AS linked_id, o2.label AS linked_label, " +
            "       o2.type AS linked_type, o2.category AS linked_category, o2.name AS linked_name " +
            "FROM ecos_objects o1 " +
            "JOIN ecos_object_links l ON o1.id = l.source_id " +
            "JOIN ecos_objects o2 ON l.target_id = o2.id " +
            "WHERE o1.id = ?",
            entityId
        );

        // 查询边
        List<Map<String, Object>> edges = jdbc.queryForList(
            "SELECT l.id, l.source_id, l.target_id, l.rel_type, l.strength " +
            "FROM ecos_object_links l " +
            "WHERE l.source_id = ? OR l.target_id = ?",
            entityId, entityId
        );

        result.put("nodes", nodes);
        result.put("edges", edges);
        return result;
    }

    // ═══════════════════════════════════════════════════════
    //  Cypher → SQL 简单翻译
    // ═══════════════════════════════════════════════════════

    /**
     * 简单 Cypher → SQL 翻译。
     *
     * <p>支持的模式:
     * <ul>
     *   <li>MATCH (n:Label) RETURN ... → SELECT ... FROM ecos_objects WHERE label = 'Label'</li>
     *   <li>MATCH (a:LabelA)-[r:REL]->(b:LabelB) RETURN ... → JOIN 查询</li>
     * </ul>
     */
    private String translateCypherToSql(String cypher, Map<String, Object> params) {
        String upper = cypher.toUpperCase().trim();

        // ── 节点类查询: MATCH (n:Label) RETURN ... ──
        Pattern nodePattern = Pattern.compile(
            "MATCH\\s*\\(\\w+:?(\\w*)\\)\\s+RETURN\\s+(.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher nodeMatch = nodePattern.matcher(cypher.trim());
        if (nodeMatch.find()) {
            String label = nodeMatch.group(1);
            String returnClause = nodeMatch.group(2).trim();

            // 转换 RETURN 子句
            String selectClause = translateReturnClause(returnClause);

            StringBuilder sql = new StringBuilder("SELECT ").append(selectClause)
                    .append(" FROM ecos_objects");
            if (label != null && !label.isEmpty()) {
                sql.append(" WHERE label = '").append(label).append("'");
            }
            return sql.toString();
        }

        // ── 关系类查询: MATCH (a:LabelA)-[r:REL]->(b:LabelB) RETURN ... ──
        Pattern relPattern = Pattern.compile(
            "MATCH\\s*\\(\\w+:?(\\w*)\\)\\s*-\\s*\\[\\w*:?(\\w*)\\]\\s*-\\>\\s*\\(\\w+:?(\\w*)\\)\\s+RETURN\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher relMatch = relPattern.matcher(cypher.trim());
        if (relMatch.find()) {
            String srcLabel = relMatch.group(1);
            String relType = relMatch.group(2);
            String tgtLabel = relMatch.group(3);
            String returnClause = relMatch.group(4).trim();

            String selectClause = translateReturnClause(returnClause);

            StringBuilder sql = new StringBuilder("SELECT ").append(selectClause)
                    .append(" FROM ecos_objects a ")
                    .append("JOIN ecos_object_links r ON a.id = r.source_id ")
                    .append("JOIN ecos_objects b ON r.target_id = b.id WHERE 1=1");

            if (srcLabel != null && !srcLabel.isEmpty()) {
                sql.append(" AND a.label = '").append(srcLabel).append("'");
            }
            if (relType != null && !relType.isEmpty()) {
                sql.append(" AND r.rel_type = '").append(relType).append("'");
            }
            if (tgtLabel != null && !tgtLabel.isEmpty()) {
                sql.append(" AND b.label = '").append(tgtLabel).append("'");
            }

            // 处理参数化条件（如 {name: $from}）
            if (params != null) {
                for (Map.Entry<String, Object> entry : params.entrySet()) {
                    String paramName = entry.getKey();
                    // 尝试从 Cypher 中找到对应的参数引用
                    if (cypher.contains("$" + paramName)) {
                        // 推断字段名：看 $param 前面的模式，如 name: $from → name
                        Pattern paramField = Pattern.compile(
                            "(\\w+)\\s*:\\s*\\$" + Pattern.quote(paramName),
                            Pattern.CASE_INSENSITIVE
                        );
                        Matcher pfm = paramField.matcher(cypher);
                        if (pfm.find()) {
                            String field = pfm.group(1);
                            sql.append(" AND a.").append(field).append(" = ?");
                        }
                    }
                }
            }

            return sql.toString();
        }

        // ── 无法翻译时返回原始查询的近似 SQL ──
        log.warn("Cannot translate Cypher to SQL, returning fallback: {}", cypher);
        return "SELECT 'cypher_translation_failed' AS error";
    }

    /**
     * 将 RETURN 子句中的字段映射为 SQL SELECT 列。
     *
     * <p>例: "a.name AS name, a.type AS type, id(a) AS id"
     * → "a.name AS name, a.type AS type, a.id AS id"</p>
     */
    private String translateReturnClause(String returnStr) {
        if (returnStr.equalsIgnoreCase("count(*)")) {
            return "COUNT(*) AS count";
        }
        // 处理 id(a) → a.id, id(b) → b.id
        String translated = returnStr
                .replaceAll("(?i)id\\(a\\)", "a.id")
                .replaceAll("(?i)id\\(b\\)", "b.id")
                .replaceAll("(?i)id\\(n\\)", "id")
                .replaceAll("(?i)type\\(r\\)", "r.rel_type");
        return translated;
    }

    /**
     * 从参数 Map 提取有序参数数组（用于 JdbcTemplate 占位符替换）。
     */
    private Object[] extractSqlParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) return new Object[0];
        return params.values().toArray();
    }
}
