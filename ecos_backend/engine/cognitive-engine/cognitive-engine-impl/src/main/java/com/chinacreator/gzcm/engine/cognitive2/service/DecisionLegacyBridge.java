package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.DecisionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 决策遗留桥接层 — 将散落三处的决策收敛到统一 ecos_decision 表。
 *
 * <p>遵循 PMO-32 风险2：不 import agent-service 的 GovernanceDecision 类，
 * 只在 DB 层做 category 映射。V29 因果链和 GovernanceDecision 通过各自的
 * category 标签挂入 ecos_decision，不修改原有代码。</p>
 *
 * <p>三处遗留决策源：</p>
 * <ul>
 *   <li>V29__ecos_ceo_causal_chain.sql → category = "ceo_causal_chain"</li>
 *   <li>GovernanceDecision (agent-service) → category = "governance"</li>
 *   <li>V30__ecos_diagnostic_agent.sql → category = "diagnostic"</li>
 * </ul>
 */
@Service
public class DecisionLegacyBridge {

    private static final Logger log = LoggerFactory.getLogger(DecisionLegacyBridge.class);

    private final JdbcTemplate jdbc;
    private final DecisionService decisionService;

    @Autowired
    public DecisionLegacyBridge(JdbcTemplate jdbc, DecisionService decisionService) {
        this.jdbc = jdbc;
        this.decisionService = decisionService;
    }

    /**
     * 扫描 V29 CEO 因果链表，将未桥接的记录导入 ecos_decision。
     * 幂等：通过 provenance 记录避免重复导入。
     *
     * @return 新桥接的决策数量
     */
    public int bridgeCeoCausalChain() {
        log.info("Bridging V29 CEO causal chain into ecos_decision");

        // V29 表名 ecos_ceo_causal_chain（如存在）
        int count = 0;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM ecos_ceo_causal_chain WHERE id NOT IN " +
                "(SELECT entity_id FROM ecos_provenance_entry WHERE entity_type = 'decision' " +
                "AND source_ref = 'V29_bridge')"
            );

            for (Map<String, Object> row : rows) {
                String legacyId = String.valueOf(row.get("id"));
                String scenario = (String) row.getOrDefault("scenario", row.getOrDefault("description", ""));
                String reasoning = (String) row.getOrDefault("reasoning", row.getOrDefault("chain", ""));
                String outcome = (String) row.getOrDefault("outcome", row.getOrDefault("conclusion", ""));

                String decisionId = decisionService.recordDecision(
                    "ceo_causal_chain", scenario, reasoning, outcome, 0.7, "legacy_bridge"
                );

                // 记录溯源，标记来源
                decisionService.recordProvenance(
                    "decision", decisionId, "MANUAL", "V29_bridge:" + legacyId, "legacy_bridge", "bridge"
                );
                count++;
            }
        } catch (Exception e) {
            log.warn("V29 bridge skipped (table may not exist): {}", e.getMessage());
        }

        log.info("Bridged {} CEO causal chain decisions", count);
        return count;
    }

    /**
     * 扫描诊断 Agent 决策（V30），将未桥接的记录导入 ecos_decision。
     *
     * @return 新桥接的决策数量
     */
    public int bridgeDiagnosticAgent() {
        log.info("Bridging V30 diagnostic agent decisions into ecos_decision");

        int count = 0;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM ecos_diagnostic_agent WHERE id NOT IN " +
                "(SELECT entity_id FROM ecos_provenance_entry WHERE entity_type = 'decision' " +
                "AND source_ref = 'V30_bridge')"
            );

            for (Map<String, Object> row : rows) {
                String legacyId = String.valueOf(row.get("id"));
                String scenario = (String) row.getOrDefault("scenario", row.getOrDefault("symptom", ""));
                String reasoning = (String) row.getOrDefault("reasoning", row.getOrDefault("diagnosis", ""));
                String outcome = (String) row.getOrDefault("outcome", row.getOrDefault("action", ""));

                String decisionId = decisionService.recordDecision(
                    "diagnostic", scenario, reasoning, outcome, 0.6, "legacy_bridge"
                );

                decisionService.recordProvenance(
                    "decision", decisionId, "MANUAL", "V30_bridge:" + legacyId, "legacy_bridge", "bridge"
                );
                count++;
            }
        } catch (Exception e) {
            log.warn("V30 bridge skipped (table may not exist): {}", e.getMessage());
        }

        log.info("Bridged {} diagnostic agent decisions", count);
        return count;
    }

    /**
     * 为 GovernanceDecision（agent-service）提供 DB 层 category 映射。
     * 不 import GovernanceDecision 类，通过 REST 调用方传入参数。
     *
     * @param legacyId    原始 GovernanceDecision ID
     * @param scenario    治理场景
     * @param reasoning   治理推理
     * @param outcome     治理决策结果
     * @return 新建的 ecos_decision ID
     */
    public String bridgeGovernanceDecision(String legacyId, String scenario,
                                           String reasoning, String outcome) {
        log.info("Bridging GovernanceDecision: {}", legacyId);

        String decisionId = decisionService.recordDecision(
            "governance", scenario, reasoning, outcome, 0.8, "legacy_bridge"
        );

        decisionService.recordProvenance(
            "decision", decisionId, "MANUAL", "governance_bridge:" + legacyId,
            "legacy_bridge", "bridge"
        );

        return decisionId;
    }
}
