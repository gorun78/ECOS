package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.dao.ComplianceRuleDao;
import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import com.chinacreator.gzcm.runtime.access.git.archive.ArchiveOutcome;
import com.chinacreator.gzcm.runtime.access.git.archive.DesignAssetArchiver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ComplianceRuleVersionService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceRuleVersionService.class);

    /** 注册表 repositoryId（V300 种子；§2.9.7 全域接入的 kb 属主）。 */
    static final String GIT_REPO_KB = "kb-asset-tree";

    private final ComplianceRuleDao complianceRuleDao;

    /** 设计资产归档器（#147E）；未装配时归档腿显式 NOT_APPLICABLE，不影响版本入库。 */
    private final ObjectProvider<DesignAssetArchiver> archiverProvider;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /** 测试/非 Spring 装配（归档腿缺省）。 */
    public ComplianceRuleVersionService(ComplianceRuleDao complianceRuleDao) {
        this(complianceRuleDao, null);
    }

    @Autowired
    public ComplianceRuleVersionService(ComplianceRuleDao complianceRuleDao,
                                        ObjectProvider<DesignAssetArchiver> archiverProvider) {
        this.complianceRuleDao = complianceRuleDao;
        this.archiverProvider = archiverProvider;
    }

    /**
     * 版本快照：将当前版本写入 sys_rule_version，并归档到 {@code kb-asset-tree}（#147E）。
     *
     * <p>归档失败不阻断版本入库（可用性优先），但结果如实落日志/返回值：
     * {@code NOT_AVAILABLE}（未注册/工作树未解析）与 {@code FAILED} 分态记录，
     * 禁按「已归档」理解（§2.9.6① 三腿分别记录）。</p>
     */
    public ArchiveOutcome snapshotVersion(ComplianceRule existing, String approvedBy, long now, int newVersion) {
        String versionId = UUID.randomUUID().toString();
        String json = toJson(existing);
        complianceRuleDao.insertVersion(
            versionId,
            existing.getId(),
            existing.getVersion(),
            json,
            approvedBy,
            now,
            "Updated to version " + newVersion
        );
        return archive(existing, json, newVersion, approvedBy);
    }

    /** 归档规则快照（KB 属主的 {@code kb-asset-tree} 通道）。 */
    private ArchiveOutcome archive(ComplianceRule rule, String json, int newVersion, String approvedBy) {
        DesignAssetArchiver archiver = archiverProvider != null ? archiverProvider.getIfAvailable() : null;
        if (archiver == null) {
            log.info("设计资产归档器未装配，跳过 kb 归档（kb-asset-tree）ruleId={}", rule.getId());
            return ArchiveOutcome.notAvailable("DesignAssetArchiver 未装配");
        }
        ArchiveOutcome outcome = archiver.archive(GIT_REPO_KB, "rules", rule.getId(),
                String.valueOf(newVersion), json,
                "Snapshot compliance rule " + rule.getId() + " v" + newVersion, approvedBy);
        if (outcome.status() == ArchiveOutcome.Status.FAILED) {
            log.warn("kb 规则归档失败 ruleId={} reason={}", rule.getId(), outcome.reason());
        }
        return outcome;
    }

    /**
     * 查询规则版本历史
     */
    public List<Map<String, Object>> getVersions(String ruleId) {
        return complianceRuleDao.getVersions(ruleId);
    }

    // ── 简易JSON序列化 ────────────────────────────

    private String toJson(ComplianceRule rule) {
        try {
            return MAPPER.writeValueAsString(rule);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ComplianceRule to JSON: id={}", rule.getId(), e);
            return "{}";
        }
    }
}
