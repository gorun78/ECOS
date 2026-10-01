package com.chinacreator.gzcm.engine.data.quality.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.engine.data.dao.DqRuleIssueDao;
import com.chinacreator.gzcm.engine.data.quality.entity.DqRuleEntity;
import com.chinacreator.gzcm.engine.data.quality.entity.DqIssueEntity;

/**
 * JdbcTemplate 仓库 — 数据质量规则 & 问题持久化 CRUD
 * <p>
 * 遵循 AgentRepository / WorkflowRepository 模式。
 * B4a 收口（R2-B4a）：SQL 已下沉 {@link DqRuleIssueDao}（..dao..，继承
 * runtime-core {@code JdbcTemplateDaoSupport}），本类退化为纯门面，
 * 只保留业务前置条件 / 返回值整形 / 日志；不再继承 JdbcTemplateDaoSupport，
 * 不再碰 SQL 文本。公共方法签名逐字保持，调用方零改动。
 * </p>
 */
@Repository
public class DqRepository {

    private static final Logger log = LoggerFactory.getLogger(DqRepository.class);

    private final DqRuleIssueDao dao;

    public DqRepository(DqRuleIssueDao dao) {
        this.dao = dao;
    }

    // ═══════════════════ Rule CRUD ════════════════════════════

    public List<DqRuleEntity> findAllRules() {
        return dao.findAllRules();
    }

    public Optional<DqRuleEntity> findRuleById(Long id) {
        return dao.findRuleById(id);
    }

    public long insertRule(DqRuleEntity entity) {
        return dao.insertRule(entity);
    }

    public int updateRule(Long id, String name, String description, String ruleType,
                          String configJson, String severity, Boolean enabled) {
        return dao.updateRule(id, name, description, ruleType, configJson, severity, enabled);
    }

    public int deleteRuleById(Long id) {
        return dao.deleteRuleById(id);
    }

    public long countRules() {
        return dao.countRules();
    }

    // ═══════════════════ Issue CRUD ═══════════════════════════

    public List<DqIssueEntity> findAllIssues() {
        return dao.findAllIssues();
    }

    public Optional<DqIssueEntity> findIssueById(Long id) {
        return dao.findIssueById(id);
    }

    public long insertIssue(DqIssueEntity entity) {
        // Wave-7 T-27 (R3) 真实根因:
        //   ecos_dq_issue.id 是 VARCHAR(64) NOT NULL 主键 (无默认, 无序列), RETURN_GENERATED_KEYS 对 VARCHAR 无意义。
        //   Wave-6 T-25 的 resolveGeneratedKey 改法整体错误方向; 必须显式生成 id。
        //   这里改为: 使用 entity.getId() (服务层已赋 UUID), insert 直接把 id 写入, 返回该 id 字段的哈希索引。
        if (entity.getId() == null || entity.getId().isBlank()) {
            throw new com.chinacreator.gzcm.common.exception.ValidationException("DqIssueEntity.id 必填 (服务层应生成 UUID)");
        }
        int updated = dao.insertIssue(entity);
        // 返回 id 字段的 hashCode (保持稳定精度, 与 Map<Long, ...> 接口兼容)
        long idHash = Math.abs((long) entity.getId().hashCode());
        log.debug("Dq issue inserted: id={}, rows={}", entity.getId(), updated);
        return idHash;
    }

    /**
     * 更新质量问题描述/状态/严重度；状态切到 resolved/closed 时同步更新时间戳。
     */
    public int updateIssue(Long id, String description, String status, String severity) {
        return dao.updateIssue(id, description, status, severity);
    }

    public int resolveIssue(Long id, String resolution) {
        return dao.resolveIssue(id, resolution);
    }

    public int deleteIssueById(Long id) {
        return dao.deleteIssueById(id);
    }

    public long countIssues() {
        return dao.countIssues();
    }

    /**
     * Count issues by status.
     */
    public long countIssuesByStatus(String status) {
        return dao.countIssuesByStatus(status);
    }

    /**
     * Average passRate across all rules (parsed from config_json).
     */
    public double avgPassRate() {
        Double result = dao.avgPassRate();
        return result != null ? result : 0.0;
    }

    // ═══════════════════ S3-BE02 Dashboard enhancements ═══════

    /**
     * Count issues grouped by severity.
     */
    public Map<String, Long> countIssuesBySeverity() {
        return dao.countIssuesBySeverity();
    }

    /**
     * Find most recent N issues (unresolved first, then by created_at).
     */
    public List<Map<String, Object>> findRecentIssues(int limit) {
        return dao.findRecentIssues(limit);
    }

    /**
     * Count rules grouped by rule_type.
     */
    public Map<String, Long> countRulesByType() {
        return dao.countRulesByType();
    }
}
