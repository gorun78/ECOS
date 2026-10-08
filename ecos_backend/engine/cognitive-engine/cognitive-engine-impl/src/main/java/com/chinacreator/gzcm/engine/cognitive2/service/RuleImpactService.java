package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.model.ImpactAnalysisResult;
import com.chinacreator.gzcm.engine.kb.ComplianceRuleProvider;
import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 规则影响分析服务 — 分析指定规则变更后，哪些下游规则会受到波及影响。
 *
 * <p>F05-13 收口：sys_compliance_rule 不再自持 DAO 直读他域表（旧 {@code RuleDao}），
 * 统一经 kb-engine-api sanctioned 门面 {@link ComplianceRuleProvider} 读取。
 * description 文本匹配逻辑留在 Service 层。</p>
 */
@Service
public class RuleImpactService {

    private static final Logger log = LoggerFactory.getLogger(RuleImpactService.class);

    private final ComplianceRuleProvider ruleProvider;

    public RuleImpactService(ComplianceRuleProvider ruleProvider) {
        this.ruleProvider = ruleProvider;
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
        ComplianceRule sourceRule = ruleProvider.findById(ruleId).orElse(null);
        if (sourceRule == null) {
            log.warn("Rule not found: {}", ruleId);
            return new ImpactAnalysisResult(ruleId, null);
        }

        String sourceName = sourceRule.getName();
        String sourceDomain = sourceRule.getDomain();

        // 2. 加载全量规则
        List<ComplianceRule> allRules = ruleProvider.findAll();

        // 3. 文本匹配：找出 description 中引用源规则的所有规则
        ImpactAnalysisResult result = new ImpactAnalysisResult(ruleId, sourceName);

        for (ComplianceRule rule : allRules) {
            String otherId = rule.getId();
            if (otherId.equals(ruleId)) continue; // 跳过自身

            String otherName = rule.getName();
            String otherDomain = rule.getDomain();
            String otherDesc = rule.getDescription();

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
