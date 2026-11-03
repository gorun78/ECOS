package com.chinacreator.gzcm.engine.data.quality.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.engine.data.quality.DqRuleLifecycleService;
import com.chinacreator.gzcm.engine.data.quality.dao.DqRuleLifecycleDao;
import com.chinacreator.gzcm.engine.data.quality.mapper.DqRuleMapper;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleDTO;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVersionVO;
import com.chinacreator.gzcm.engine.data.quality.model.LogicDeleteResult;
import com.chinacreator.gzcm.runtime.core.cache.CacheJson;
import com.chinacreator.gzcm.runtime.core.cache.CacheKey;
import com.chinacreator.gzcm.runtime.core.cache.RuntimeCacheManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DQ 规则生命周期状态机实现（PMO-48-B T7c）。
 * <p>
 * 7 动作 + 版本快照 + 行锁幂等 + 审计闭环：
 * <ul>
 *   <li>createDraft / updateDraft / deleteRule — 基础 CRUD（逻辑删除）</li>
 *   <li>submit / approve / reject / deprecate / supersede / disable — 状态转换</li>
 * </ul>
 * 状态转换合法性由 {@link #TRANSITIONS} 转换表强制校验，违例抛
 * {@code BusinessException}（HTTP 400）。
 * </p>
 * <p>
 * 并发安全：
 * <ul>
 *   <li>approve 动作用 {@code SELECT ... FOR UPDATE} 行锁防止并发审批写双版本</li>
 *   <li>状态变更用 CAS（{@code WHERE status = expected} 防 ABA）</li>
 * </ul>
 * </p>
 * <p>
 * 安全卡（铁律 2.4）：
 * <ol>
 *   <li>写操作后异步 {@code auditWrite("DQ_RULE_<ACTION>", ruleId, "SUCCESS")}</li>
 *   <li>读操作 {@code auditRead("DQ_RULE_DETAIL", ruleId)}</li>
 * </ol>
 * </p>
 *
 * @author PMO-48-B T7c
 */
@Service("ecosDqRuleLifecycleService")
public class DqRuleLifecycleServiceImpl implements DqRuleLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(DqRuleLifecycleServiceImpl.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 14 类矩阵 #6「质量规则」缓存域（版本清单读多写少；失效只收敛本域）。 */
    static final String CACHE_DOMAIN = "data-engine-dq-rule";
    static final String TYPE_VERSIONS = "dq-rule-versions";
    static final String SCOPE_GLOBAL = "global";
    static final long CACHE_TTL_SECONDS = 300L;

    private static final TypeReference<List<DqRuleVersionVO>> VERSIONS_TYPE = new TypeReference<>() {
    };

    /** Runtime 缓存（缺席时逐次回源，不影响正确性）。 */
    @Autowired(required = false)
    private ObjectProvider<RuntimeCacheManager> cacheProvider;

    // ==================== 状态机转换表 ====================
    // key = 当前状态, value = 允许的目标状态集合
    static final Map<String, Set<String>> TRANSITIONS;

    static {
        Map<String, Set<String>> map = new HashMap<>();
        map.put("DRAFT",      Set.of("IN_REVIEW", "DEPRECATED", "SUPERSEDED"));
        map.put("IN_REVIEW",  Set.of("ACTIVE", "REJECTED", "DRAFT"));
        map.put("ACTIVE",     Set.of("DEPRECATED", "SUPERSEDED", "DISABLED"));
        map.put("REJECTED",   Set.of("DRAFT", "DEPRECATED"));
        map.put("DEPRECATED", Set.of("SUPERSEDED"));
        map.put("SUPERSEDED", Set.of());
        map.put("DISABLED",   Set.of("ACTIVE", "DEPRECATED"));
        TRANSITIONS = Map.copyOf(map);
    }

    // ==================== 校验方法 ====================

    /**
     * 断言状态转换合法，违例抛 BusinessException。
     *
     * @param from   当前状态
     * @param to     目标状态
     * @param ruleId 规则 ID（用于异常消息）
     */
    private void assertTransition(String from, String to, String ruleId) {
        if (from == null) {
            throw new BusinessException("规则 " + ruleId + " 状态未知，无法转换到 " + to);
        }
        Set<String> allowed = TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new BusinessException(String.format(
                    "规则 %s 状态转换非法：%s → %s（允许目标：%s）", ruleId, from, to, allowed));
        }
    }

    // ==================== 基础 CRUD ====================

    @Override
    @Transactional
    public String createDraft(DqRuleDTO dto) {
        validateRuleDTO(dto);
        String ruleId = UUID.randomUUID().toString().replace("-", "");
        String operator = dto.getOperator() != null ? dto.getOperator() : "system";

        // 基础字段：status 固定 DRAFT，version 固定 1
        String ruleName = dto.getRuleName();
        String ruleCode = dto.getRuleCode() != null ? dto.getRuleCode() : "auto_" + ruleId;
        String category = dto.getCategory() != null ? dto.getCategory() : "TECHNICAL";
        String ruleType = dto.getRuleType() != null ? dto.getRuleType() : "NOT_NULL";
        String severity = dto.getSeverity() != null ? dto.getSeverity() : "MEDIUM";
        String targetKind = dto.getTargetKind() != null ? dto.getTargetKind() : "TABLE";
        String parameters = dto.getParameters() != null ? dto.getParameters() : "{}";

        ruleLifecycleDao.insertRule(ruleId, ruleName, ruleCode, category, dto.getDomain(), ruleType,
                severity, targetKind, dto.getTargetId(), dto.getTargetTable(),
                dto.getTargetField(), dto.getTargetPipelineId(), parameters,
                dto.getApprovedBy(), dto.getSourceType(), dto.getSourceRef(),
                dto.getDescription(), operator);

        securityService.auditWrite("DQ_RULE_CREATE", ruleId, "SUCCESS");
        log.info("DQ rule created (DRAFT): id={}, ruleCode={}, operator={}", ruleId, ruleCode, operator);
        return ruleId;
    }

    @Override
    @Transactional
    public void updateDraft(String ruleId, DqRuleDTO dto) {
        DqRuleVO existing = loadAndAssertExists(ruleId);
        // 仅 DRAFT 可编辑（非状态转换，直接校验状态）
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("规则 " + ruleId + " 非草稿状态，不可编辑");
        }
        String operator = dto.getOperator() != null ? dto.getOperator() : "system";

        String ruleName = dto.getRuleName() != null ? dto.getRuleName() : existing.getRuleName();
        String ruleCode = dto.getRuleCode() != null ? dto.getRuleCode() : existing.getRuleCode();
        String category = dto.getCategory() != null ? dto.getCategory() : existing.getCategory();
        String domain = dto.getDomain() != null ? dto.getDomain() : existing.getDomain();
        String ruleType = dto.getRuleType() != null ? dto.getRuleType() : existing.getRuleType();
        String severity = dto.getSeverity() != null ? dto.getSeverity() : existing.getSeverity();
        String targetKind = dto.getTargetKind() != null ? dto.getTargetKind() : existing.getTargetKind();
        String targetId = dto.getTargetId() != null ? dto.getTargetId() : existing.getTargetId();
        String targetTable = dto.getTargetTable() != null ? dto.getTargetTable() : existing.getTargetTable();
        String targetField = dto.getTargetField() != null ? dto.getTargetField() : existing.getTargetField();
        String targetPipelineId = dto.getTargetPipelineId() != null ? dto.getTargetPipelineId() : existing.getTargetPipelineId();
        String parameters = dto.getParameters() != null ? dto.getParameters() : (existing.getParametersJson() != null ? existing.getParametersJson() : "{}");
        String description = dto.getDescription() != null ? dto.getDescription() : existing.getDescription();
        String sourceType = dto.getSourceType() != null ? dto.getSourceType() : existing.getSourceType();
        String sourceRef = dto.getSourceRef() != null ? dto.getSourceRef() : existing.getSourceRef();

        int affected = ruleLifecycleDao.updateDraft(
                ruleName, ruleCode, category, domain, ruleType, severity,
                targetKind, targetId, targetTable, targetField, targetPipelineId,
                parameters, description, sourceType, sourceRef, operator, ruleId);
        if (affected == 0) {
            throw new BusinessException("规则 " + ruleId + " 更新失败：规则不草稿态/不存在/已删除");
        }
        securityService.auditWrite("DQ_RULE_UPDATE", ruleId, "SUCCESS");
        log.info("DQ rule updated (DRAFT): id={}, operator={}", ruleId, operator);
    }

    @Override
    @Transactional
    public LogicDeleteResult deleteRule(String ruleId) {
        DqRuleVO existing = loadAndAssertExists(ruleId);
        LogicDeleteResult result = new LogicDeleteResult();
        result.setId(ruleId);
        LocalDateTime now = LocalDateTime.now();
        result.setBeforeDeleteAt(now);

        if (existing.getUpdatedAt() != null && "DRAFT".equals(existing.getStatus())
                && Boolean.TRUE.equals(isAlreadyDeleted(ruleId))) {
            result.setAlreadyDeleted(true);
            return result;
        }

        int affected = ruleLifecycleDao.softDelete(ruleId);
        if (affected == 0) {
            result.setAlreadyDeleted(true);
            return result;
        }
        securityService.auditWrite("DQ_RULE_DELETE", ruleId, "SUCCESS");
        log.info("DQ rule logically deleted: id={}", ruleId);
        return result;
    }

    // ==================== 状态转换 ====================

    @Override
    @Transactional
    public String submit(String ruleId, String submitter) {
        DqRuleVO rule = loadAndAssertExists(ruleId);
        assertTransition(rule.getStatus(), "IN_REVIEW", ruleId);
        int affected = ruleLifecycleDao.submitReview(submitter, ruleId, rule.getStatus());
        if (affected == 0) {
            throw new BusinessException("规则 " + ruleId + " 并发状态变更失败，请重试");
        }
        securityService.auditWrite("DQ_RULE_SUBMIT", ruleId, "SUCCESS");
        log.info("DQ rule submit: id={}, submitter={}, from={}", ruleId, submitter, rule.getStatus());
        return "IN_REVIEW";
    }

    @Override
    @Transactional
    public String approve(String ruleId, String approver) {
        // 行锁防并发重审
        DqRuleVO rule = lockAndAssertExists(ruleId);
        assertTransition(rule.getStatus(), "ACTIVE", ruleId);

        int newVersion = rule.getVersion() + 1;
        String snapshotJson = serializeRuleSnapshot(rule);
        String versionId = UUID.randomUUID().toString().replace("-", "");

        // 写版本快照 + 状态变更（同事务）
        ruleLifecycleDao.insertVersionSnapshot(versionId, ruleId, newVersion, snapshotJson, approver);
        int affected = ruleLifecycleDao.approve(ruleId, newVersion, approver);
        if (affected == 0) {
            throw new BusinessException("规则 " + ruleId + " 并发审批失败，请重试");
        }
        securityService.auditWrite("DQ_RULE_APPROVE", ruleId, "SUCCESS");
        log.info("DQ rule approved: id={}, approver={}, version={}", ruleId, approver, newVersion);
        evictVersions(ruleId);
        return "ACTIVE";
    }

    @Override
    @Transactional
    public String reject(String ruleId, String rejector, String reason) {
        DqRuleVO rule = loadAndAssertExists(ruleId);
        assertTransition(rule.getStatus(), "REJECTED", ruleId);
        int affected = ruleLifecycleDao.reject(rejector, ruleId);
        if (affected == 0) {
            throw new BusinessException("规则 " + ruleId + " 驳回失败：状态已变更，请重试");
        }
        securityService.auditWrite("DQ_RULE_REJECT", ruleId, "SUCCESS");
        log.info("DQ rule rejected: id={}, rejector={}, reason={}", ruleId, rejector, reason);
        return "REJECTED";
    }

    @Override
    @Transactional
    public String deprecate(String ruleId, String operator) {
        DqRuleVO rule = loadAndAssertExists(ruleId);
        assertTransition(rule.getStatus(), "DEPRECATED", ruleId);
        int affected = ruleLifecycleDao.deprecate(operator, ruleId, rule.getStatus());
        if (affected == 0) {
            throw new BusinessException("规则 " + ruleId + " 废止失败：状态已变更，请重试");
        }
        securityService.auditWrite("DQ_RULE_DEPRECATE", ruleId, "SUCCESS");
        log.info("DQ rule deprecated: id={}, operator={}, from={}", ruleId, operator, rule.getStatus());
        return "DEPRECATED";
    }

    @Override
    @Transactional
    public String supersede(String ruleId, String operator, String reason) {
        DqRuleVO rule = loadAndAssertExists(ruleId);
        assertTransition(rule.getStatus(), "SUPERSEDED", ruleId);
        int affected = ruleLifecycleDao.supersede(operator, ruleId, rule.getStatus());
        if (affected == 0) {
            throw new BusinessException("规则 " + ruleId + " 替代失败：状态已变更，请重试");
        }
        securityService.auditWrite("DQ_RULE_SUPERSEDE", ruleId, "SUCCESS");
        log.info("DQ rule superseded: id={}, operator={}, reason={}", ruleId, operator, reason);
        return "SUPERSEDED";
    }

    @Override
    @Transactional
    public String disable(String ruleId, String operator) {
        DqRuleVO rule = loadAndAssertExists(ruleId);
        assertTransition(rule.getStatus(), "DISABLED", ruleId);
        int affected = ruleLifecycleDao.disable(operator, ruleId);
        if (affected == 0) {
            throw new BusinessException("规则 " + ruleId + " 停用失败：状态已变更，请重试");
        }
        securityService.auditWrite("DQ_RULE_DISABLE", ruleId, "SUCCESS");
        log.info("DQ rule disabled: id={}, operator={}", ruleId, operator);
        return "DISABLED";
    }

    // ==================== 版本查询 ====================

    @Override
    public DqRuleVersionVO getVersion(String ruleId, int versionNumber) {
        loadAndAssertExists(ruleId);
        List<DqRuleVersionVO> all = cachedVersions(ruleId);
        securityService.auditRead("DQ_RULE_VERSION_GET", ruleId);
        return all.stream()
                .filter(v -> v.getVersionNumber() != null && v.getVersionNumber() == versionNumber)
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<DqRuleVersionVO> listVersions(String ruleId) {
        loadAndAssertExists(ruleId);
        List<DqRuleVersionVO> versions = cachedVersions(ruleId);
        securityService.auditRead("DQ_RULE_VERSION_LIST", ruleId);
        return versions;
    }

    // ── Runtime 缓存接线（14 类矩阵 #6；#147AI）────────────────────────────
    // 版本快照只在 approve 时新增，失效点与写入点一一对应；缓存关闭/异常一律回源。

    private List<DqRuleVersionVO> cachedVersions(String ruleId) {
        CacheKey key = cacheKey(ruleId);
        List<DqRuleVersionVO> cached = CacheJson.read(stringOf(key), VERSIONS_TYPE);
        if (cached != null) {
            return cached;
        }
        List<DqRuleVersionVO> loaded = ruleMapper.listVersionsByRuleId(ruleId);
        if (loaded == null) {
            loaded = List.of();
        }
        store(key, loaded);
        return loaded;
    }

    private RuntimeCacheManager cache() {
        return cacheProvider == null ? null : cacheProvider.getIfAvailable();
    }

    private CacheKey cacheKey(String ruleId) {
        RuntimeCacheManager manager = cache();
        if (manager == null || ruleId == null) {
            return null;
        }
        try {
            return manager.key(CACHE_DOMAIN, TYPE_VERSIONS, SCOPE_GLOBAL, ruleId, "1");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String stringOf(CacheKey key) {
        RuntimeCacheManager manager = cache();
        return key == null || manager == null ? null : manager.getString(key).orElse(null);
    }

    private void store(CacheKey key, Object value) {
        RuntimeCacheManager manager = cache();
        if (key == null || manager == null) {
            return;
        }
        manager.putString(key, CacheJson.write(value), CACHE_TTL_SECONDS);
    }

    private void evictVersions(String ruleId) {
        RuntimeCacheManager manager = cache();
        if (manager != null && ruleId != null) {
            manager.evictObject(CACHE_DOMAIN, TYPE_VERSIONS, ruleId);
        }
    }

    // ==================== 私有工具方法 ====================

    /** 加载规则（不锁定行），不存在抛 NotFoundException。 */
    private DqRuleVO loadAndAssertExists(String ruleId) {
        return doLoad(ruleId, false);
    }

    /** 加载规则（FOR UPDATE 行锁），用于 approve 防并发。 */
    private DqRuleVO lockAndAssertExists(String ruleId) {
        return doLoad(ruleId, true);
    }

    private DqRuleVO doLoad(String ruleId, boolean forUpdate) {
        if (ruleId == null || ruleId.isBlank()) {
            throw new BusinessException("规则 ID 不能为空");
        }
        List<DqRuleVO> rows = ruleLifecycleDao.findRule(ruleId, forUpdate, (rs, rowNum) -> {
            DqRuleVO vo = new DqRuleVO();
            vo.setId(rs.getString("id"));
            vo.setRuleName(rs.getString("rule_name"));
            vo.setRuleCode(rs.getString("rule_code"));
            vo.setCategory(rs.getString("category"));
            vo.setDomain(rs.getString("domain"));
            vo.setRuleType(rs.getString("rule_type"));
            vo.setSeverity(rs.getString("severity"));
            vo.setTargetKind(rs.getString("target_kind"));
            vo.setTargetId(rs.getString("target_id"));
            vo.setTargetTable(rs.getString("target_table"));
            vo.setTargetField(rs.getString("target_field"));
            vo.setTargetPipelineId(rs.getString("target_pipeline_id"));
            vo.setParametersJson(rs.getString("parameters_json"));
            vo.setStatus(rs.getString("status"));
            vo.setVersion(rs.getInt("version"));
            vo.setApprovedBy(rs.getString("approved_by"));
            long e = rs.getLong("effective_date");
            vo.setEffectiveDate(rs.wasNull() ? null : e);
            long x = rs.getLong("expiry_date");
            vo.setExpiryDate(rs.wasNull() ? null : x);
            vo.setSourceType(rs.getString("source_type"));
            vo.setSourceRef(rs.getString("source_ref"));
            vo.setDescription(rs.getString("description"));
            vo.setCreatedBy(rs.getString("created_by"));
            vo.setUpdatedBy(rs.getString("updated_by"));
            java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
            vo.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
            java.sql.Timestamp updatedAt = rs.getTimestamp("updated_at");
            vo.setUpdatedAt(updatedAt != null ? updatedAt.toLocalDateTime() : null);
            return vo;
        });
        if (rows.isEmpty()) {
            securityService.auditRead("DQ_RULE_DETAIL", ruleId);
            throw new NotFoundException("DQ 规则 " + ruleId + " 不存在或已删除");
        }
        return rows.get(0);
    }

    /** 检查规则是否已逻辑删除（幂等保护用）。 */
    private Boolean isAlreadyDeleted(String ruleId) {
        try {
            List<Integer> results = ruleLifecycleDao.findDeletedFlag(ruleId);
            return !results.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** 校验 DTO 必填字段。 */
    private void validateRuleDTO(DqRuleDTO dto) {
        if (dto == null) {
            throw new BusinessException("规则 DTO 不能为空");
        }
        if (dto.getRuleName() == null || dto.getRuleName().isBlank()) {
            throw new BusinessException("规则名称 ruleName 不能为空");
        }
        if (dto.getRuleType() == null || dto.getRuleType().isBlank()) {
            throw new BusinessException("规则类型 ruleType 不能为空");
        }
        if (dto.getCategory() == null || dto.getCategory().isBlank()) {
            throw new BusinessException("规则类别 category 不能为空");
        }
    }

    /** 序列化规则全文为 JSON 快照字符串（写入 dq_rule_version.snapshot JSONB）。 */
    private String serializeRuleSnapshot(DqRuleVO rule) {
        try {
            return MAPPER.writeValueAsString(rule);
        } catch (JsonProcessingException e) {
            throw new BusinessException("规则快照序列化失败: " + e.getMessage());
        }
    }

    // ==================== 构造注入 ====================

    private final DqRuleMapper ruleMapper;
    private final DqSecurityService securityService;
    private final DqRuleLifecycleDao ruleLifecycleDao;

    public DqRuleLifecycleServiceImpl(DqRuleMapper ruleMapper,
                                       DqSecurityService securityService,
                                       DqRuleLifecycleDao ruleLifecycleDao) {
        this.ruleMapper = ruleMapper;
        this.securityService = securityService;
        this.ruleLifecycleDao = ruleLifecycleDao;
    }
}
