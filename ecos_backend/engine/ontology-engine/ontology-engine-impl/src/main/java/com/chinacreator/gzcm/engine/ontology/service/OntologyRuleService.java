package com.chinacreator.gzcm.engine.ontology.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyRuleEvaluationVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyRuleSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyRuleVO;
import com.chinacreator.gzcm.engine.ontology.model.OntologyEntity;
import com.chinacreator.gzcm.engine.ontology.model.OntologyProperty;
import com.chinacreator.gzcm.engine.ontology.model.OntologyRelationship;
import com.chinacreator.gzcm.engine.ontology.model.OntologyRule;
import com.chinacreator.gzcm.engine.ontology.model.OntologyAction;
import com.chinacreator.gzcm.engine.ontology.model.OntologyDomain;
import com.chinacreator.gzcm.engine.ontology.model.OntologyVersion;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyRepository;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyRuleRepository;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyDomainRepository;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyVersionRepository;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyMappingStore;

/**
 * 规则业务服务 — Rule CRUD + 测试/批量评估 + 双维契约与发布前置门（#147B）。
 *
 * <p>#147B（册 §6.2.6⑧ / I-3 / C-9 / DF-5 / L260 正交条款）：</p>
 * <ul>
 *   <li><b>值域闭集</b>：{@code rule_carrier} ∈ {ONTOLOGY_ELEMENT, RULE_OBJECT,
 *       KNOWLEDGE_ASSET}，{@code rule_layer} ∈ {STRUCTURAL, VALIDATION, BUSINESS,
 *       POLICY, COGNITIVE}；非空即校验，非法值拒 400（ONT-030）。空值允许存在（存量
 *       旧记录），但发布时拒绝。</li>
 *   <li><b>发布前置门</b>：经既有 PUT 端点 {@code status="PUBLISHED"} 触发（册 §2.1
 *       裁决「R7~R11 未新增 REST 端点」，不新开 publish 路由）。合并 DTO 与存量后
 *       校验五字段全量就绪（rule_carrier/rule_layer/scope/version_no/git_ref，缺
 *       {@code version_no}/{@code git_ref} 拒发布）＋ rule_type×rule_layer 正交
 *       双登记（缺一即告警并拒发布）＋ 表达式 L1 轻量可解析（FunctionValidator
 *       全量接线属 RuleRegistry 目标态待建项，见册⑤/⑧注）。违例写 warn 日志（C-9
 *       「拒发布并告警，不静默放行」）并拒 400（ONT-031）。</li>
 *   <li><b>Map 旧门面不旁路</b>：Map 形态 create/update 收敛到同一强类型内核，
 *       值域校验与发布门对两轨同时生效。</li>
 * </ul>
 */
@Service
public class OntologyRuleService {

    private static final Logger log = LoggerFactory.getLogger(OntologyRuleService.class);
    private static final AtomicInteger ID_SEQ = new AtomicInteger(1000);

    /** I-3 闭集：rule_carrier 值域（与 V300 CHECK ck_ontology_rule_carrier 同词表）。 */
    static final Set<String> RULE_CARRIER_VALUES = Set.of(
        "ONTOLOGY_ELEMENT", "RULE_OBJECT", "KNOWLEDGE_ASSET");

    /** I-3 闭集：rule_layer 值域（与 V300 CHECK ck_ontology_rule_layer 同词表）。 */
    static final Set<String> RULE_LAYER_VALUES = Set.of(
        "STRUCTURAL", "VALIDATION", "BUSINESS", "POLICY", "COGNITIVE");

    /** 发布触发状态值（PUT status=PUBLISHED 走发布前置门）。 */
    static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 值域违例机读码前缀（ONT-0xx 私设形态沿成本域既有惯例：ONT-001/002/008/020 等）。 */
    static final String CODE_VALUE_DOMAIN = "ONT-030";

    /** 发布门拒绝机读码前缀。 */
    static final String CODE_PUBLISH_GATE = "ONT-031";

    private final OntologyRuleRepository repository;

    public OntologyRuleService(OntologyRuleRepository repository) {
        this.repository = repository;
    }

    private String nextId() { return "rule" + ID_SEQ.incrementAndGet(); }

    // ═══════════════ Strong-Typed Facades (T16-1, 2026-09-12) ═══════════════════
    // 旧 Map 签名方法保留（外部调用方可能依赖）；新增 DTO/VO 签名供 6 Controller 使用。

    public List<OntologyRuleVO> listRulesByEntityVO(String entityId) {
        return repository.findByEntity(entityId).stream().map(this::toVO).collect(Collectors.toList());
    }

    public List<OntologyRuleVO> listAllRulesVO() {
        return repository.findAll().stream().map(this::toVO).collect(Collectors.toList());
    }

    public OntologyRuleVO getRuleVO(String ruleId) {
        return repository.findById(ruleId).map(this::toVO).orElse(null);
    }

    public OntologyRuleVO createRule(String entityId, OntologyRuleSaveDTO dto) {
        validateDualDimension(dto.getRuleCarrier(), dto.getRuleLayer());
        OntologyRule rule = new OntologyRule();
        rule.setId(nextId());
        rule.setEntityId(entityId);
        rule.setCode(dto.getCode() != null ? dto.getCode() : "");
        rule.setName(dto.getName() != null ? dto.getName() : "");
        rule.setRuleType(dto.getRuleType() != null ? dto.getRuleType() : "VALIDATION");
        rule.setExpression(dto.getExpression() != null ? dto.getExpression() : "");
        rule.setAction(dto.getAction() != null ? dto.getAction() : "");
        rule.setPriority(dto.getPriority() != null ? dto.getPriority() : 0);
        rule.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : 1);
        rule.setDescription(dto.getDescription() != null ? dto.getDescription() : "");
        rule.setRuleCarrier(blankToNull(dto.getRuleCarrier()));
        rule.setRuleLayer(blankToNull(dto.getRuleLayer()));
        rule.setScope(blankToNull(dto.getScope()));
        rule.setVersionNo(blankToNull(dto.getVersionNo()) != null ? blankToNull(dto.getVersionNo()) : "1");
        rule.setGitRef(blankToNull(dto.getGitRef()));
        rule.setStatus(blankToNull(dto.getStatus()) != null ? blankToNull(dto.getStatus()) : "ACTIVE");
        rule.setDomain("default");
        if (STATUS_PUBLISHED.equals(rule.getStatus())) {
            rejectPublishIfNotReady(rule.getId(), rule);
        }
        repository.insert(rule);
        log.info("Rule created: {} [{}] type={} carrier={} layer={} for entity {}",
            rule.getId(), rule.getCode(), rule.getRuleType(), rule.getRuleCarrier(), rule.getRuleLayer(), entityId);
        return toVO(rule);
    }

    public Optional<OntologyRuleVO> updateRule(String ruleId, OntologyRuleSaveDTO dto) {
        return repository.findById(ruleId).map(existing -> {
            validateDualDimension(dto.getRuleCarrier(), dto.getRuleLayer());
            if (STATUS_PUBLISHED.equals(blankToNull(dto.getStatus()))) {
                rejectPublishIfNotReady(ruleId, mergeForGate(existing, dto));
            }
            repository.update(ruleId, dto.getCode(), dto.getName(), dto.getRuleType(),
                dto.getExpression(), dto.getAction(), dto.getPriority(), dto.getEnabled(), dto.getDescription(),
                blankToNull(dto.getRuleCarrier()), blankToNull(dto.getRuleLayer()), blankToNull(dto.getScope()),
                blankToNull(dto.getVersionNo()), blankToNull(dto.getGitRef()), blankToNull(dto.getStatus()));
            return repository.findById(ruleId).map(this::toVO).orElse(null);
        });
    }

    /** 强类型版规则测试。 */
    public OntologyRuleEvaluationVO testRuleVO(String ruleId) {
        OntologyRule rule = repository.findById(ruleId)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Rule '" + ruleId + "' not found"));
        OntologyRuleEvaluationVO vo = new OntologyRuleEvaluationVO();
        vo.setRuleId(rule.getId());
        vo.setCode(rule.getCode());
        vo.setExpression(rule.getExpression());
        vo.setParsable(tryParseExpression(rule.getExpression()));
        vo.setRuleType(rule.getRuleType());
        vo.setEnabled(rule.getEnabled() == 1);
        return vo;
    }

    /** 强类型版批量评估。 */
    public List<OntologyRuleEvaluationVO> evaluateRulesVO(List<String> entityIds) {
        return entityIds.stream().flatMap(entityId ->
            repository.findByEntity(entityId).stream().filter(r -> r.getEnabled() == 1).map(rule -> {
                OntologyRuleEvaluationVO vo = new OntologyRuleEvaluationVO();
                vo.setRuleId(rule.getId());
                vo.setEntityId(entityId);
                vo.setCode(rule.getCode());
                vo.setExpression(rule.getExpression());
                vo.setRuleType(rule.getRuleType());
                vo.setParsable(tryParseExpression(rule.getExpression()));
                return vo;
            })
        ).collect(Collectors.toList());
    }

    private OntologyRuleVO toVO(OntologyRule r) {
        OntologyRuleVO vo = new OntologyRuleVO();
        vo.setId(r.getId());
        vo.setEntityId(r.getEntityId());
        vo.setCode(r.getCode());
        vo.setName(r.getName());
        vo.setRuleType(r.getRuleType());
        vo.setExpression(r.getExpression());
        vo.setAction(r.getAction());
        vo.setPriority(r.getPriority());
        vo.setEnabled(r.getEnabled());
        vo.setDescription(r.getDescription());
        vo.setRuleCarrier(r.getRuleCarrier());
        vo.setRuleLayer(r.getRuleLayer());
        vo.setScope(r.getScope());
        vo.setVersionNo(r.getVersionNo());
        vo.setGitRef(r.getGitRef());
        vo.setStatus(r.getStatus());
        vo.setDomain(r.getDomain());
        vo.setCreatedAt(r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        vo.setUpdatedAt(r.getUpdatedAt() != null ? r.getUpdatedAt().toString() : null);
        return vo;
    }

    public List<Map<String, Object>> listRulesByEntity(String entityId) {
        return repository.findByEntity(entityId).stream().map(this::toMap).collect(Collectors.toList());
    }

    public List<Map<String, Object>> listAllRules() {
        return repository.findAll().stream().map(this::toMap).collect(Collectors.toList());
    }

    public Map<String, Object> getRule(String ruleId) {
        return repository.findById(ruleId).map(this::toMap).orElse(null);
    }

    /**
     * 旧 Map 门面新增 — #147B 起收敛到强类型内核（值域闭集 + 发布门同效，不旁路）。
     */
    public Map<String, Object> createRule(String entityId, Map<String, Object> body) {
        return voToMap(createRule(entityId, fromMap(body)));
    }

    /**
     * 旧 Map 门面编辑 — #147B 起收敛到强类型内核（containsKey 语义 = DTO null 不更新）。
     */
    public Optional<Map<String, Object>> updateRule(String ruleId, Map<String, Object> body) {
        return updateRule(ruleId, fromMap(body)).map(OntologyRuleService::voToMap);
    }

    /** Map 请求体 → 强类型 DTO（containsKey 才写入，保持旧 hasKey 不更新语义）。 */
    private static OntologyRuleSaveDTO fromMap(Map<String, Object> body) {
        if (body == null) return null;
        OntologyRuleSaveDTO dto = new OntologyRuleSaveDTO();
        if (body.containsKey("code")) dto.setCode(String.valueOf(body.get("code")));
        if (body.containsKey("name")) dto.setName(String.valueOf(body.get("name")));
        if (body.containsKey("ruleType")) dto.setRuleType(String.valueOf(body.get("ruleType")));
        if (body.containsKey("expression")) dto.setExpression(String.valueOf(body.get("expression")));
        if (body.containsKey("action")) dto.setAction(String.valueOf(body.get("action")));
        if (body.containsKey("priority")) dto.setPriority(toInt(body.get("priority")));
        if (body.containsKey("enabled")) dto.setEnabled(toInt(body.get("enabled")));
        if (body.containsKey("description")) dto.setDescription(String.valueOf(body.get("description")));
        if (body.containsKey("ruleCarrier")) dto.setRuleCarrier(String.valueOf(body.get("ruleCarrier")));
        if (body.containsKey("ruleLayer")) dto.setRuleLayer(String.valueOf(body.get("ruleLayer")));
        if (body.containsKey("scope")) dto.setScope(String.valueOf(body.get("scope")));
        if (body.containsKey("versionNo")) dto.setVersionNo(String.valueOf(body.get("versionNo")));
        if (body.containsKey("gitRef")) dto.setGitRef(String.valueOf(body.get("gitRef")));
        if (body.containsKey("status")) dto.setStatus(String.valueOf(body.get("status")));
        return dto;
    }

    public boolean deleteRule(String ruleId) {
        return repository.delete(ruleId) > 0;
    }

    /**
     * 测试单条规则：尝试解析表达式，返回解析结果
     */
    public Map<String, Object> testRule(String ruleId) {
        OntologyRule rule = repository.findById(ruleId)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Rule '" + ruleId + "' not found"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ruleId", rule.getId());
        result.put("code", rule.getCode());
        result.put("expression", rule.getExpression());
        result.put("parsable", tryParseExpression(rule.getExpression()));
        result.put("ruleType", rule.getRuleType());
        result.put("enabled", rule.getEnabled() == 1);
        return result;
    }

    /**
     * 批量评估：给定实体 ID 列表，返回每条规则对该实体的评估结果
     */
    public List<Map<String, Object>> evaluateRules(List<String> entityIds) {
        return entityIds.stream().flatMap(entityId ->
            repository.findByEntity(entityId).stream().filter(r -> r.getEnabled() == 1).map(rule -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("ruleId", rule.getId());
                m.put("entityId", entityId);
                m.put("code", rule.getCode());
                m.put("expression", rule.getExpression());
                m.put("ruleType", rule.getRuleType());
                m.put("parsable", tryParseExpression(rule.getExpression()));
                return m;
            })
        ).collect(Collectors.toList());
    }

    private boolean tryParseExpression(String expr) {
        if (expr == null || expr.isBlank()) return false;
        // 简单语法检查：支持 comparison / boolean / arithmetic 表达式
        String trimmed = expr.replaceAll("\\s+", " ").trim();
        return trimmed.contains(">") || trimmed.contains("<") || trimmed.contains("=") ||
               trimmed.contains("AND") || trimmed.contains("OR") || trimmed.contains("NOT") ||
               trimmed.startsWith("SET(");
    }

    // ═══════════════ #147B 双维契约：值域闭集 + 发布前置门 ═══════════════

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** I-3 闭集校验：非空即须落值域（大小写敏感，与 V300 CHECK 约束同词表），非法拒 ONT-030。 */
    private static void validateDualDimension(String ruleCarrier, String ruleLayer) {
        String carrier = blankToNull(ruleCarrier);
        String layer = blankToNull(ruleLayer);
        if (carrier != null && !RULE_CARRIER_VALUES.contains(carrier)) {
            throw new IllegalArgumentException(CODE_VALUE_DOMAIN
                + ": rule_carrier '" + carrier + "' not in closed set " + RULE_CARRIER_VALUES);
        }
        if (layer != null && !RULE_LAYER_VALUES.contains(layer)) {
            throw new IllegalArgumentException(CODE_VALUE_DOMAIN
                + ": rule_layer '" + layer + "' not in closed set " + RULE_LAYER_VALUES);
        }
    }

    /** 发布门判定对象 = 存量行 + 本次 DTO 的合并态（null=不更新，取存量）。 */
    private static OntologyRule mergeForGate(OntologyRule existing, OntologyRuleSaveDTO dto) {
        OntologyRule merged = new OntologyRule();
        merged.setId(existing.getId());
        merged.setRuleType(blankToNull(dto.getRuleType()) != null ? blankToNull(dto.getRuleType()) : existing.getRuleType());
        merged.setExpression(blankToNull(dto.getExpression()) != null ? blankToNull(dto.getExpression()) : existing.getExpression());
        merged.setRuleCarrier(blankToNull(dto.getRuleCarrier()) != null ? blankToNull(dto.getRuleCarrier()) : existing.getRuleCarrier());
        merged.setRuleLayer(blankToNull(dto.getRuleLayer()) != null ? blankToNull(dto.getRuleLayer()) : existing.getRuleLayer());
        merged.setScope(blankToNull(dto.getScope()) != null ? blankToNull(dto.getScope()) : existing.getScope());
        merged.setVersionNo(blankToNull(dto.getVersionNo()) != null ? blankToNull(dto.getVersionNo()) : existing.getVersionNo());
        merged.setGitRef(blankToNull(dto.getGitRef()) != null ? blankToNull(dto.getGitRef()) : existing.getGitRef());
        merged.setStatus(STATUS_PUBLISHED);
        return merged;
    }

    /**
     * §6.2.6⑧ 发布前置门：五字段（rule_carrier/rule_layer/scope/version_no/git_ref）全量就绪
     * 方可发布，缺 version_no/git_ref 拒发布（L204/DF-5）；rule_layer 空值记录拒发布并告警
     * （C-9/I-3）；rule_type×rule_layer 正交双登记缺一即告警并拒发布（L260）；表达式须过
     * L1 轻量可解析基线（FunctionValidator 全量接线属 RuleRegistry 目标态，未接入面不假绿）。
     */
    private void rejectPublishIfNotReady(String ruleId, OntologyRule merged) {
        List<String> violations = new ArrayList<>();
        if (blankToNull(merged.getRuleCarrier()) == null) violations.add("rule_carrier missing");
        if (blankToNull(merged.getRuleLayer()) == null) violations.add("rule_layer missing (C-9)");
        if (blankToNull(merged.getScope()) == null) violations.add("scope missing");
        if (blankToNull(merged.getVersionNo()) == null) violations.add("version_no missing (DF-5)");
        if (blankToNull(merged.getGitRef()) == null) violations.add("git_ref missing (DF-5)");
        if (blankToNull(merged.getRuleType()) == null) violations.add("rule_type missing (orthogonality: rule_type x rule_layer)");
        if (!tryParseExpression(merged.getExpression())) violations.add("expression not parsable (L1 baseline)");
        if (!violations.isEmpty()) {
            String detail = String.join("; ", violations);
            log.warn("Rule publish rejected: {} [{}] violations: {}", ruleId, merged.getRuleType(), detail);
            throw new IllegalArgumentException(CODE_PUBLISH_GATE
                + ": publish rejected for rule '" + ruleId + "' — " + detail);
        }
    }

    private Map<String, Object> toMap(OntologyRule r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("entityId", r.getEntityId());
        m.put("code", r.getCode());
        m.put("name", r.getName());
        m.put("ruleType", r.getRuleType());
        m.put("expression", r.getExpression());
        m.put("action", r.getAction());
        m.put("priority", r.getPriority());
        m.put("enabled", r.getEnabled());
        m.put("description", r.getDescription());
        m.put("ruleCarrier", r.getRuleCarrier());
        m.put("ruleLayer", r.getRuleLayer());
        m.put("scope", r.getScope());
        m.put("versionNo", r.getVersionNo());
        m.put("gitRef", r.getGitRef());
        m.put("status", r.getStatus());
        m.put("domain", r.getDomain());
        m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        m.put("updatedAt", r.getUpdatedAt() != null ? r.getUpdatedAt().toString() : null);
        return m;
    }

    /** Map 轨返回面：VO → 与旧 toMap 同键序的 Map（#147B 收敛内核后，两轨输出一致）。 */
    private static Map<String, Object> voToMap(OntologyRuleVO v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("entityId", v.getEntityId());
        m.put("code", v.getCode());
        m.put("name", v.getName());
        m.put("ruleType", v.getRuleType());
        m.put("expression", v.getExpression());
        m.put("action", v.getAction());
        m.put("priority", v.getPriority());
        m.put("enabled", v.getEnabled());
        m.put("description", v.getDescription());
        m.put("ruleCarrier", v.getRuleCarrier());
        m.put("ruleLayer", v.getRuleLayer());
        m.put("scope", v.getScope());
        m.put("versionNo", v.getVersionNo());
        m.put("gitRef", v.getGitRef());
        m.put("status", v.getStatus());
        m.put("domain", v.getDomain());
        m.put("createdAt", v.getCreatedAt());
        m.put("updatedAt", v.getUpdatedAt());
        return m;
    }

    private static int toInt(Object val) {
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(String.valueOf(val)); } catch (Exception e) { return 0; }
    }
}
