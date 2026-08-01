package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.model.ImpactAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 规则影响分析服务 — 分析指定规则变更后，哪些下游规则会受到波及影响。
 *
 * <p>简化实现：通过 sys_compliance_rule 表的 description 字段文本匹配，
 * 查找所有 description 中引用了目标规则名称/ID 的其他规则。</p>
 */
@Service
public class RuleImpactService {

    private static final Logger log = LoggerFactory.getLogger(RuleImpactService.class);

    private static final String SELECT_RULE_BY_ID =
            "SELECT id, name, domain, description FROM sys_compliance_rule WHERE id = ?";

    private static final String SELECT_ALL_RULES =
            "SELECT id, name, domain, description FROM sys_compliance_rule";

    private final JdbcTemplate jdbcTemplate;

    public RuleImpactService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 分析指定规则的下游影响范围。
     *
     * @param ruleId 源规则ID
     * @return 影响分析结果，包含所有 description 中引用了该规则的下游规则
     */
    public ImpactAnalysisResult analyze(String ruleId) {
        log.info("Analyzing rule impact for: {}", ruleId);

        // 1. 查询源规则
        List<Map<String, Object>> rootRows = jdbcTemplate.queryForList(SELECT_RULE_BY_ID, ruleId);
        if (rootRows.isEmpty()) {
            log.warn("Rule not found: {}", ruleId);
            return new ImpactAnalysisResult(ruleId, null);
        }

        Map<String, Object> rootRow = rootRows.get(0);
        String sourceName = (String) rootRow.get("name");
        String sourceDomain = (String) rootRow.get("domain");

        // 2. 加载全量规则
        List<Map<String, Object>> allRows = jdbcTemplate.queryForList(SELECT_ALL_RULES);

        // 3. 文本匹配：找出 description 中引用源规则的所有规则
        ImpactAnalysisResult result = new ImpactAnalysisResult(ruleId, sourceName);

        for (Map<String, Object> row : allRows) {
            String otherId = (String) row.get("id");
            if (otherId.equals(ruleId)) continue; // 跳过自身

            String otherName = (String) row.get("name");
            String otherDomain = (String) row.get("domain");
            String otherDesc = (String) row.get("description");

            String matchReason = findMatchReason(otherDesc, sourceName, ruleId);
            if (matchReason != null) {
                ImpactAnalysisResult.ImpactedRule impacted = new ImpactAnalysisResult.ImpactedRule(
                        otherId,
                        otherName,
                        otherDomain,
                        matchReason
                );
                result.getImpactedRules().add(impacted);
            }
        }

        log.info("Impact analysis complete: {} impacted rules for rule {} ({})",
                result.getImpactedRules().size(), ruleId, sourceName);
        return result;
    }

    /**
     * 在 description 中查找对源规则的引用，返回匹配原因，未匹配则返回 null。
     */
    private String findMatchReason(String description, String sourceName, String sourceId) {
        if (description == null || sourceName == null) return null;

        String lower = description.toLowerCase();

        // 检查名称引用
        if (lower.contains(sourceName.toLowerCase())) {
            int idx = lower.indexOf(sourceName.toLowerCase());
            int start = Math.max(0, idx - 10);
            int end = Math.min(lower.length(), idx + sourceName.length() + 30);
            String excerpt = description.substring(start, end).trim();
            if (start > 0) excerpt = "..." + excerpt;
            if (end < description.length()) excerpt = excerpt + "...";
            return "description text contains rule name \"" + sourceName + "\": " + excerpt;
        }

        // 检查ID引用
        if (sourceId != null && lower.contains(sourceId.toLowerCase())) {
            int idx = lower.indexOf(sourceId.toLowerCase());
            int start = Math.max(0, idx - 10);
            int end = Math.min(lower.length(), idx + sourceId.length() + 30);
            String excerpt = description.substring(start, end).trim();
            if (start > 0) excerpt = "..." + excerpt;
            if (end < description.length()) excerpt = excerpt + "...";
            return "description text contains rule ID \"" + sourceId + "\": " + excerpt;
        }

        return null;
    }
}
