package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainNode;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainResult;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 规则因果链服务 — 通过 sys_compliance_rule 表的 description 字段文本匹配构建因果链。
 *
 * <p>简化实现：使用 PG SQL 直接查询 sys_compliance_rule，通过 description 中的规则名称/ID 引用
 * 发现因果依赖关系，递归构建因果链节点和边。</p>
 */
@Service
public class RuleCausalService {

    private static final Logger log = LoggerFactory.getLogger(RuleCausalService.class);

    private static final String SELECT_RULE_BY_ID =
            "SELECT id, name, domain, description FROM sys_compliance_rule WHERE id = ?";

    private static final String SELECT_ALL_RULES =
            "SELECT id, name, domain, description FROM sys_compliance_rule";

    private final JdbcTemplate jdbcTemplate;

    public RuleCausalService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 获取指定规则的因果链，包含该规则及其上下游依赖关系。
     *
     * @param ruleId 根规则ID
     * @return 因果链结果（节点 + 边）
     */
    public CausalChainResult getCausalChain(String ruleId) {
        log.info("Building causal chain for rule: {}", ruleId);

        // 1. 查询目标规则
        List<Map<String, Object>> rootRows = jdbcTemplate.queryForList(SELECT_RULE_BY_ID, ruleId);
        if (rootRows.isEmpty()) {
            log.warn("Rule not found: {}", ruleId);
            return new CausalChainResult(ruleId);
        }

        Map<String, Object> rootRow = rootRows.get(0);
        String rootName = (String) rootRow.get("name");
        String rootDomain = (String) rootRow.get("domain");
        String rootDesc = (String) rootRow.get("description");

        // 2. 加载全量规则
        List<Map<String, Object>> allRows = jdbcTemplate.queryForList(SELECT_ALL_RULES);
        Map<String, Map<String, Object>> ruleMap = new HashMap<>();
        for (Map<String, Object> row : allRows) {
            ruleMap.put((String) row.get("id"), row);
        }

        // 3. 构建因果链 (BFS)
        CausalChainResult result = new CausalChainResult(ruleId);
        Set<String> visited = new HashSet<>();
        Set<String> edgeSet = new HashSet<>(); // 去重边: "sourceId->targetId"
        AtomicInteger nodeCounter = new AtomicInteger(1);

        // 添加根节点
        CausalChainNode rootNode = new CausalChainNode(
                "node_" + nodeCounter.getAndIncrement(),
                ruleId,
                rootName,
                rootDomain,
                truncate(rootDesc, 200)
        );
        result.getNodes().add(rootNode);
        visited.add(ruleId);

        // BFS 队列: (ruleId, nodeId)
        Deque<String[]> queue = new ArrayDeque<>();
        queue.add(new String[]{ruleId, rootNode.getId()});

        while (!queue.isEmpty()) {
            String[] current = queue.poll();
            String curRuleId = current[0];
            String curNodeId = current[1];
            Map<String, Object> curRow = ruleMap.get(curRuleId);
            if (curRow == null) continue;

            String curName = (String) curRow.get("name");
            String curDesc = (String) curRow.get("description");

            // 遍历所有规则，查找 description 中引用了当前规则名称或ID的规则
            for (Map.Entry<String, Map<String, Object>> entry : ruleMap.entrySet()) {
                String otherId = entry.getKey();
                Map<String, Object> otherRow = entry.getValue();
                String otherName = (String) otherRow.get("name");
                String otherDesc = (String) otherRow.get("description");

                // 跳过自身；检查 otherDesc 是否引用 curName 或 curRuleId
                if (otherId.equals(curRuleId)) continue;

                boolean refers = refersTo(otherDesc, curName, curRuleId);
                if (!refers) continue;

                // 构建边
                String edgeKey = otherId + "->" + curRuleId;
                if (!edgeSet.contains(edgeKey)) {
                    edgeSet.add(edgeKey);
                    CausalEdge edge = new CausalEdge();
                    edge.setId("edge_" + edgeSet.size());
                    edge.setSourceNode(otherId);
                    edge.setTargetNode(curRuleId);
                    edge.setWeight(0.5);
                    edge.setDescription(otherName + " → " + curName);
                    result.getEdges().add(edge);
                }

                // 如果该规则未访问过，加入队列
                if (visited.add(otherId)) {
                    CausalChainNode childNode = new CausalChainNode(
                            "node_" + nodeCounter.getAndIncrement(),
                            otherId,
                            otherName,
                            (String) otherRow.get("domain"),
                            truncate(otherDesc, 200)
                    );
                    result.getNodes().add(childNode);
                    queue.add(new String[]{otherId, childNode.getId()});
                }
            }
        }

        log.info("Causal chain built: {} nodes, {} edges for rule {}", 
                result.getNodes().size(), result.getEdges().size(), ruleId);
        return result;
    }

    /**
     * 检查 description 文本中是否引用了指定的规则名称或规则ID。
     */
    private boolean refersTo(String description, String ruleName, String ruleId) {
        if (description == null) return false;
        String lower = description.toLowerCase();
        if (ruleName != null && lower.contains(ruleName.toLowerCase())) return true;
        if (ruleId != null && lower.contains(ruleId.toLowerCase())) return true;
        return false;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
