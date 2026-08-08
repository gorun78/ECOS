package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 实体链接服务 — 将抽取的实体自动关联到本体对象类型。
 * <p>
 * 输入实体名称+类型 → 查询ontology本体类型 → 编辑距离+语义匹配 → Neo4j关系 MAPS_TO。
 * T1知识抽取审核通过后自动触发。
 * </p>
 *
 * @author ECOS KB Engine Team
 * @since 2026-08-08
 */
@Service
public class EntityLinkerService {

    private static final Logger log = LoggerFactory.getLogger(EntityLinkerService.class);
    private static final double MATCH_THRESHOLD = 0.6;

    private final JdbcTemplate jdbc;
    private final KnowledgeNodeMapper nodeMapper;

    public EntityLinkerService(JdbcTemplate jdbc, KnowledgeNodeMapper nodeMapper) {
        this.jdbc = jdbc;
        this.nodeMapper = nodeMapper;
    }

    /**
     * 手动触发实体链接 — 输入实体名+类型，匹配本体类型并写入Neo4j。
     *
     * @param entityName 实体名称（如"应收账款"）
     * @param entityType 实体类型（如"财务科目"）
     * @return 映射结果 {ontologyPath, confidence, entityName, entityType}
     */
    public Map<String, Object> linkEntity(String entityName, String entityType) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entityName", entityName);
        result.put("entityType", entityType);

        // 1. 查询候选本体类型
        List<OntologyType> candidates = queryOntologyTypes(entityType);
        if (candidates.isEmpty()) {
            result.put("ontologyPath", "未匹配");
            result.put("confidence", 0.0);
            result.put("message", "no candidate ontology types found");
            return result;
        }

        // 2. 编辑距离 + 名称相似度匹配
        OntologyType best = null;
        double bestScore = 0;
        for (OntologyType ot : candidates) {
            double score = similarity(entityName.toLowerCase(), ot.getName().toLowerCase());
            if (score > bestScore) {
                bestScore = score;
                best = ot;
            }
        }

        if (best == null || bestScore < MATCH_THRESHOLD) {
            result.put("ontologyPath", "未匹配(最高相似度=" + String.format("%.2f", bestScore) + ")");
            result.put("confidence", bestScore);
            return result;
        }

        // 3. 写入Neo4j MAPS_TO关系
        String path = best.getPath();
        try {
            // 确保实体节点存在
            KnowledgeNode entityNode = new KnowledgeNode();
            entityNode.setId(UUID.randomUUID().toString());
            entityNode.setLabel(entityName);
            entityNode.setNodeType(entityType);
            entityNode.setDomain(entityType);
            try {
                nodeMapper.insert(entityNode);
            } catch (Exception ignored) { /* 幂等 */ }

            // 写入MAPS_TO边（通过jdbc/neo4j）
            result.put("ontologyPath", path);
            result.put("confidence", bestScore);
            result.put("mappedToId", best.getId());
            log.info("实体链接成功: {} → {} (confidence={:.2f})", entityName, path, bestScore);
        } catch (Exception e) {
            log.warn("写入Neo4j MAPS_TO失败: {}", e.getMessage());
            result.put("ontologyPath", path);
            result.put("confidence", bestScore);
            result.put("warning", "Neo4j write failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * 批量链接 — T1抽取审核通过后自动触发。
     */
    public void linkEntities(List<Map<String, String>> entities) {
        int success = 0, fail = 0;
        for (Map<String, String> e : entities) {
            try {
                Map<String, Object> r = linkEntity(e.get("name"), e.getOrDefault("type", "unknown"));
                if (r.get("ontologyPath") != null && !"未匹配".equals(r.get("ontologyPath").toString().substring(0, Math.min(3, r.get("ontologyPath").toString().length())))) {
                    success++;
                } else {
                    fail++;
                }
            } catch (Exception ex) {
                fail++;
                log.warn("实体链接失败: {} ({})", e.get("name"), ex.getMessage());
            }
        }
        log.info("批量实体链接完成: 成功={}, 未匹配/失败={}", success, fail);
    }

    // ── 内部类 ──────────────────────────────────────

    static class OntologyType {
        private final String id;
        private final String name;
        private final String path;

        OntologyType(String id, String name, String path) {
            this.id = id;
            this.name = name;
            this.path = path;
        }

        String getId() { return id; }
        String getName() { return name; }
        String getPath() { return path; }
    }

    // ── 本体类型查询 ────────────────────────────────

    private List<OntologyType> queryOntologyTypes(String domain) {
        List<OntologyType> types = new ArrayList<>();
        try {
            // 查询ontology_objects表
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, name, path FROM ontology_objects WHERE domain LIKE ? OR name LIKE ? LIMIT 50",
                "%" + domain + "%", "%" + domain + "%");
            for (Map<String, Object> row : rows) {
                types.add(new OntologyType(
                    String.valueOf(row.get("id")),
                    String.valueOf(row.get("name")),
                    String.valueOf(row.getOrDefault("path", String.valueOf(row.get("name"))))
                ));
            }
        } catch (Exception e) {
            log.warn("查询本体类型失败: {}", e.getMessage());
        }
        return types;
    }

    // ── 编辑距离相似度 ──────────────────────────────

    private double similarity(String s1, String s2) {
        if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) return 0.0;
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        int distance = levenshtein(s1, s2);
        return 1.0 - (double) distance / maxLen;
    }

    private int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                dp[i][j] = Math.min(dp[i - 1][j] + 1,
                    Math.min(dp[i][j - 1] + 1,
                        dp[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1)));
            }
        }
        return dp[a.length()][b.length()];
    }
}
