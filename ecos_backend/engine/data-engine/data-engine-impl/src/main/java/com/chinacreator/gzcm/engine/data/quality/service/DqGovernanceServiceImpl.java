package com.chinacreator.gzcm.engine.data.quality.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.engine.data.quality.DqGovernanceService;
import com.chinacreator.gzcm.engine.data.quality.mapper.DqRuleMapper;
import com.chinacreator.gzcm.engine.data.quality.model.DqDimensionRegistryVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleDetailVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleQuery;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVersionVO;
import com.chinacreator.gzcm.engine.data.quality.model.PageResult;

/**
 * DQ 治理服务实现 — 读取 {@code ecos_dq.dq_rule}（MyBatis），响应前落安全卡：
 * <ol>
 *   <li>脱敏（铁律 2.4 #3）：rules 逐条对 parameters 中敏感键 mask；detail 额外对
 *       target_field 维度 + 版本 snapshot 做 mask；单值兜底走 security-engine
 *       {@code POST /api/security/mask}（{@link DqSecurityService#maskValue}）。</li>
 *   <li>审计（铁律 2.4 #5）：list/detail 返回前异步 {@code POST /api/security/audit/log}。</li>
 * </ol>
 * Bean 名 {@code ecosDqGovernanceService}（铁律 1.3 防多 Bean 冲突）。
 *
 * @author PMO-48-A T3
 */
@Service("ecosDqGovernanceService")
public class DqGovernanceServiceImpl implements DqGovernanceService {

    private static final Logger log = LoggerFactory.getLogger(DqGovernanceServiceImpl.class);

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<Map<String, Object>>() {};

    private final DqRuleMapper dqRuleMapper;
    private final DqSecurityService securityService;

    public DqGovernanceServiceImpl(DqRuleMapper dqRuleMapper, DqSecurityService securityService) {
        this.dqRuleMapper = dqRuleMapper;
        this.securityService = securityService;
    }

    @Override
    public PageResult<DqRuleVO> listRules(DqRuleQuery q) {
        int pageNum = (q == null || q.getPageNum() == null || q.getPageNum() < 1) ? 1 : q.getPageNum();
        int pageSize = (q == null || q.getPageSize() == null || q.getPageSize() < 1) ? 20 : q.getPageSize();
        if (pageSize > 200) {
            throw new BusinessException("DQ 规则列表 pageSize 上限 200");
        }
        DqRuleQuery query = q != null ? q : new DqRuleQuery();
        List<DqRuleVO> page = dqRuleMapper.listByFilter(query, pageSize, (pageNum - 1) * pageSize);
        long total = dqRuleMapper.countByFilter(query);
        // 脱敏 + 审计（铁律 2.4 #3/#5）
        page.forEach(this::maskRule);
        securityService.auditRead("DQ_RULE_LIST", null);
        return new PageResult<>(page, total, pageNum, pageSize);
    }

    @Override
    public DqRuleDetailVO getRuleDetail(String id) {
        if (id == null || id.isBlank()) {
            throw new BusinessException("DQ 规则 id 不能为空");
        }
        DqRuleVO rule = dqRuleMapper.findById(id);
        if (rule == null) {
            securityService.auditRead("DQ_RULE_DETAIL", id);
            throw new NotFoundException("DQ 规则 " + id + " 不存在");
        }
        List<DqRuleVersionVO> versions = dqRuleMapper.listVersionsByRuleId(id);
        // 脱敏（铁律 2.4 #3）：规则主体 + target_field 维度 + 版本快照
        maskRule(rule);
        if (securityService.containsSensitiveField(rule.getTargetField())) {
            rule.setTargetField(DqSecurityService.MASKED);
        }
        versions.forEach(this::maskVersion);
        securityService.auditRead("DQ_RULE_DETAIL", id);

        DqRuleDetailVO detail = new DqRuleDetailVO();
        detail.setRule(rule);
        detail.setVersions(versions);
        return detail;
    }

    @Override
    public List<DqDimensionRegistryVO> getDimensionRegistry() {
        return List.of(
                dimension("COMPLETENESS", "完整性",
                        List.of("NOT_NULL", "PRESENCE"), 30, "字段非空率 / 必填覆盖度"),
                dimension("ACCURACY", "准确性",
                        List.of("FORMAT", "RANGE", "REGEX"), 25, "格式/范围/正则校验与参考值匹配度"),
                dimension("CONSISTENCY", "一致性",
                        List.of("CONSISTENCY", "STATISTICAL"), 15, "跨表/跨源一致性与分布漂移"),
                dimension("FRESHNESS", "及时性",
                        List.of("FRESHNESS", "TIMELINESS"), 5, "数据新鲜度 / 到达时效"),
                dimension("UNIQUENESS", "唯一性",
                        List.of("UNIQUE"), 15, "主键/业务键无重复"),
                dimension("VALIDITY", "合规有效性",
                        List.of("VALIDITY", "ENUM"), 10, "枚举/合规取值有效"));
    }

    private DqDimensionRegistryVO dimension(String code, String name, List<String> types,
                                             int weight, String description) {
        DqDimensionRegistryVO vo = new DqDimensionRegistryVO();
        vo.setDimension(code);
        vo.setName(name);
        vo.setRuleTypes(new ArrayList<>(types));
        vo.setDefaultWeight(weight);
        vo.setDescription(description);
        return vo;
    }

    // ==================== 脱敏细节 ====================

    /** 单条规则脱敏：parameters 敏感键 + target_field 维度。 */
    private void maskRule(DqRuleVO vo) {
        if (vo.getParametersJson() != null && !vo.getParametersJson().isBlank()) {
            vo.setParametersJson(maskJson(vo.getParametersJson()));
        }
        if (vo.getTargetField() != null && securityService.containsSensitiveField(vo.getTargetField())) {
            vo.setTargetField(DqSecurityService.MASKED);
        }
    }

    /** 单条版本快照脱敏。 */
    private void maskVersion(DqRuleVersionVO version) {
        if (version.getSnapshotJson() != null && !version.getSnapshotJson().isBlank()) {
            version.setSnapshotJson(maskJson(version.getSnapshotJson()));
        }
    }

    /**
     * parameters JSON 敏感键脱敏：命中敏感键的值整体替换为 {masked:true}。
     * 解析失败时兜底走 security-engine maskValue 整串脱敏（默认 DENY 语义）。
     */
    private String maskJson(String json) {
        try {
            Map<String, Object> raw = MAPPER.readValue(json, MAP_TYPE);
            Map<String, Object> masked = securityService.maskParameters(raw);
            return MAPPER.writeValueAsString(masked);
        } catch (JsonProcessingException e) {
            log.warn("DQ parameters 非 JSON 对象, 整串走 security mask: {}", e.getMessage());
            return securityService.maskValue(json, "SHA256");
        }
    }
}
