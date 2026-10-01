package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.DecisionService;
import com.chinacreator.gzcm.engine.cognitive2.dao.DecisionDao;
import com.chinacreator.gzcm.engine.cognitive2.model.Decision;
import com.chinacreator.gzcm.engine.cognitive2.model.ProvenanceEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.*;

/**
 * 决策智能服务实现 — 五步生命周期：record → link → query → govern → audit
 *
 * <p>数据访问已收口至 {@link DecisionDao}（架构铁律 §2.5 #1 / IR06，B2-b）。
 * findSimilarDecisions 降级为 category 精确匹配 + 关键词 ILIKE。</p>
 */
@Service
public class DecisionServiceImpl implements DecisionService {

    private static final Logger log = LoggerFactory.getLogger(DecisionServiceImpl.class);

    private final DecisionDao decisionDao;

    public DecisionServiceImpl(DecisionDao decisionDao) {
        this.decisionDao = decisionDao;
    }

    // ── record ──────────────────────────────────────────

    @Override
    public String recordDecision(String category, String scenario, String reasoning,
                                 String outcome, double confidence, String decisionMaker) {
        String id = UUID.randomUUID().toString().replace("-", "");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        log.info("Recording decision: id={}, category={}, outcome={}", id, category, outcome);

        decisionDao.insertDecision(
            id, category, scenario, reasoning, outcome,
            confidence, decisionMaker, now
        );

        // 自动记录溯源
        recordProvenance("decision", id, "MANUAL", scenario, decisionMaker, "record");

        return id;
    }

    // ── link ────────────────────────────────────────────

    @Override
    public void addCausalRelationship(String sourceId, String targetId, String relationship) {
        String id = UUID.randomUUID().toString().replace("-", "");
        log.info("Adding causal link: {} --{}--> {}", sourceId, relationship, targetId);

        decisionDao.insertCausalLink(id, sourceId, targetId, relationship);

        recordProvenance("decision", sourceId, "MANUAL", "link:" + targetId, null, "link");
    }

    // ── query ───────────────────────────────────────────

    @Override
    public List<Decision> findSimilarDecisions(String query, int maxResults) {
        log.info("Finding similar decisions: query={}, max={}", query, maxResults);

        // 降级策略：category 精确匹配 + scenario/reasoning 关键词 ILIKE
        String pattern = "%" + query + "%";
        List<Map<String, Object>> rows = decisionDao.findSimilarDecisions(query, pattern, maxResults);

        return rows.stream().map(this::mapRowToDecision).toList();
    }

    @Override
    public List<Decision> traceDecisionChain(String decisionId) {
        log.info("Tracing decision chain: {}", decisionId);

        // 递归向上查祖先链：通过 ecos_decision_causal_link 的 source_decision_id 反向遍历
        List<Decision> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String currentId = decisionId;

        while (currentId != null && !visited.contains(currentId)) {
            visited.add(currentId);
            Decision decision = findDecisionById(currentId);
            if (decision == null) break;
            chain.add(decision);

            // 查上游：谁指向了 currentId（target_decision_id = currentId → source 是祖先）
            List<Map<String, Object>> parentRows = decisionDao.findParentLinks(currentId);

            if (parentRows.isEmpty()) break;
            currentId = (String) parentRows.get(0).get("source_decision_id");
        }

        return chain;
    }

    // ── govern ──────────────────────────────────────────

    @Override
    public Map<String, Object> analyzeDecisionImpact(String decisionId) {
        log.info("Analyzing decision impact: {}", decisionId);

        // 查下游：currentId 指向了谁（source_decision_id = currentId → target 是下游）
        List<Map<String, Object>> downstreamRows = decisionDao.findDownstreamLinks(decisionId);

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        // 起点节点
        Decision root = findDecisionById(decisionId);
        if (root != null) {
            nodes.add(Map.of("id", decisionId, "category", root.getCategory(),
                             "outcome", root.getOutcome() != null ? root.getOutcome() : ""));
        }

        // BFS 遍历下游
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(decisionId);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (visited.contains(current)) continue;
            visited.add(current);

            List<Map<String, Object>> children = decisionDao.findDownstreamLinks(current);

            for (Map<String, Object> child : children) {
                String targetId = (String) child.get("target_decision_id");
                String rel = (String) child.get("relationship");

                edges.add(Map.of("source", current, "target", targetId, "relationship", rel));

                if (!visited.contains(targetId)) {
                    Decision target = findDecisionById(targetId);
                    if (target != null) {
                        nodes.add(Map.of("id", targetId, "category", target.getCategory(),
                                         "outcome", target.getOutcome() != null ? target.getOutcome() : ""));
                    }
                    queue.add(targetId);
                }
            }
        }

        return Map.of("rootId", decisionId, "nodes", nodes, "edges", edges);
    }

    @Override
    public Map<String, Object> checkDecisionRules(String decisionId) {
        log.info("Checking decision rules: {}", decisionId);

        Decision decision = findDecisionById(decisionId);
        if (decision == null) {
            return Map.of("compliant", false, "violations", List.of("Decision not found: " + decisionId));
        }

        // 查同 category 的 active policy
        List<Map<String, Object>> policies = decisionDao.findPolicies(decision.getCategory());

        List<String> violations = new ArrayList<>();
        for (Map<String, Object> policy : policies) {
            // 简化合规检查：policy.rules 是 JSONB，这里只验证决策 outcome 非空
            // 完整 SpEL 评估留后续指令
            if (decision.getOutcome() == null || decision.getOutcome().isEmpty()) {
                violations.add("Policy " + policy.get("name") + ": outcome is empty");
            }
        }

        // 查是否有未批准的例外
        List<Map<String, Object>> exceptions = decisionDao.findPendingExceptions(decisionId);

        for (Map<String, Object> exc : exceptions) {
            violations.add("Pending exception: " + exc.get("reason"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("decisionId", decisionId);
        result.put("compliant", violations.isEmpty());
        result.put("violations", violations);
        result.put("policiesChecked", policies.size());
        return result;
    }

    // ── audit (provenance) ──────────────────────────────

    @Override
    public String recordProvenance(String entityType, String entityId, String sourceType,
                                   String sourceRef, String agent, String activity) {
        String id = UUID.randomUUID().toString().replace("-", "");
        decisionDao.insertProvenance(
            id, entityType, entityId, sourceType, sourceRef, agent, activity
        );
        return id;
    }

    @Override
    public List<ProvenanceEntry> queryProvenance(String entityType, String entityId) {
        List<Map<String, Object>> rows = decisionDao.queryProvenance(entityType, entityId);

        List<ProvenanceEntry> entries = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            ProvenanceEntry entry = new ProvenanceEntry();
            entry.setId((String) row.get("id"));
            entry.setEntityType((String) row.get("entity_type"));
            entry.setEntityId((String) row.get("entity_id"));
            entry.setSourceType((String) row.get("source_type"));
            entry.setSourceRef((String) row.get("source_ref"));
            entry.setAgent((String) row.get("agent"));
            entry.setActivity((String) row.get("activity"));
            entry.setTimestamp((Timestamp) row.get("timestamp"));
            entries.add(entry);
        }
        return entries;
    }

    // ── helpers ─────────────────────────────────────────

    private Decision findDecisionById(String id) {
        List<Map<String, Object>> rows = decisionDao.findDecisionById(id);
        if (rows.isEmpty()) return null;
        return mapRowToDecision(rows.get(0));
    }

    private Decision mapRowToDecision(Map<String, Object> row) {
        Decision d = new Decision();
        d.setId((String) row.get("id"));
        d.setCategory((String) row.get("category"));
        d.setScenario((String) row.get("scenario"));
        d.setReasoning((String) row.get("reasoning"));
        d.setOutcome((String) row.get("outcome"));
        Object conf = row.get("confidence");
        d.setConfidence(conf != null ? ((Number) conf).doubleValue() : 0);
        d.setDecisionMaker((String) row.get("decision_maker"));
        d.setValidFrom((Timestamp) row.get("valid_from"));
        d.setValidUntil((Timestamp) row.get("valid_until"));
        d.setCreatedAt((Timestamp) row.get("created_at"));
        d.setUpdatedAt((Timestamp) row.get("updated_at"));
        return d;
    }
}
