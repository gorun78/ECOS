package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.common.cognitive.BeliefDistributionVO;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.cognitive2.dto.BeliefEvidenceUpdateDTO;
import com.chinacreator.gzcm.engine.cognitive2.dto.BeliefOverrideDTO;
import com.chinacreator.gzcm.engine.cognitive2.dto.BeliefSaveDTO;
import com.chinacreator.gzcm.engine.cognitive2.service.mental.BayesianUpdater;
import com.chinacreator.gzcm.engine.cognitive2.service.mental.MentalEventPublisher;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 不确定性判断业务服务（PMO-59 P2a / ADR-9 心智层 P 库）— 表 {@code ecos_cognitive_belief}（V129）。
 *
 * <p><b>术语口径（已定）</b>：业务方统一称"不确定性判断"（原稿"信念"改称，避心理学歧义）。</p>
 *
 * <p><b>P2a 范围</b>：注册/查询（列表/详情，强制 domain 参数，Phase 1 残留风险 4 落点）/ 分布强校验
 * （prob 和=1，容差 1e-6；residual risk 3 落点）。
 * <b>P2b 增量</b>：新证据加权贝叶斯更新（{@link #updateByEvidence}，纯 Java 确定性似然→version+1）
 * 与人工覆写（{@link #override}，manualOverride=true + reason 留痕，专家干预优先于模型更新）。</p>
 *
 * <p>写操作审计（铁律 §2.4#5）：经 {@link EventBusService} 发 Kafka {@code ecos.audit}；
 * 未装配/异常降级 WARN 不阻塞。LLM 零参与认知计算（ADR-5 口径，本类纯规则计算）。</p>
 */
@Service
@RequiredArgsConstructor
public class CognitiveBeliefService {

    private static final Logger log = LoggerFactory.getLogger(CognitiveBeliefService.class);

    /** prob 和=1 校验容差（浮点累加误差兜底） */
    public static final double PROB_SUM_TOLERANCE = 1e-6;

    private final BeliefStore store;
    private final EvidenceStore evidenceStore;
    private final ObjectMapper objectMapper;
    /** P2b: 心智事件发布器（ecos.cognitive 版本变更事件；构造器注入） */
    private final MentalEventPublisher eventPublisher;

    /** 审计/事件通道 — 可选装配（required=false + null 兜底） */
    @Autowired(required = false)
    private EventBusService eventBusService;

    /**
     * 注册/初始化不确定性判断：同一 variableName+domain 已存在时作为下一 version 落库（max+1），
     * 否则首版 version=1。分布 prob 和=1 强校验。
     */
    public BeliefDistributionVO register(BeliefSaveDTO dto) {
        if (dto == null || dto.getVariableName() == null || dto.getVariableName().isBlank()) {
            throw new BusinessException(400, "COG-400: variableName 必填");
        }
        if (dto.getDomain() == null || dto.getDomain().isBlank()) {
            throw new BusinessException(400, "COG-400: domain 必填（跨域重名变量隔离键）");
        }
        String variableName = dto.getVariableName().trim();
        String domain = dto.getDomain().trim();
        validateDistribution(dto.getDiscreteDistribution());

        int version = store.maxVersion(variableName, domain) + 1;
        String id = "cog_blf_" + UUID.randomUUID().toString().substring(0, 12);
        store.insert(id, variableName, dto.getTenantScope(), domain,
            toDistributionJson(dto.getDiscreteDistribution()), version,
            trimToNull(dto.getLastEvidenceId()), false, null, "ACTIVE");
        audit("belief.create", "ecos_cognitive_belief", "success",
            "variable=" + variableName + ", domain=" + domain + ", version=" + version);
        return getLatest(variableName, domain);
    }

    /** 某变量某域当前生效版本（domain 必填——跨域重名变量隔离）；无记录返回 null。 */
    public BeliefDistributionVO getLatest(String variableName, String domain) {
        Map<String, Object> row = store.findLatest(require(variableName, "variableName"), require(domain, "domain"));
        return toVo(row == null ? null : store.toNormalizedRow(row, evidenceStore));
    }

    /** 列表（domain 必填强制过滤，Phase 1 残留风险 4；status 可选）。 */
    public List<BeliefDistributionVO> list(String domain, String status) {
        String requiredDomain = require(domain, "domain");
        if (status != null && !status.isBlank()
            && !BeliefStore.STATUSES.contains(status.toUpperCase())) {
            throw new BusinessException(400, "COG-400: 非法 status（" + String.join("/", BeliefStore.STATUSES) + "）");
        }
        return store.list(requiredDomain, status).stream()
            .map(row -> store.toNormalizedRow(row, evidenceStore))
            .map(this::toVo).toList();
    }

    /** 按主键查询；无记录返回 null（由 Controller 转 404）。 */
    public BeliefDistributionVO getDetail(String id) {
        Map<String, Object> row = store.findById(id);
        return row == null ? null : toVo(store.toNormalizedRow(row, evidenceStore));
    }

    /**
     * P2b — 新证据加权更新（api-contract 预登记 {@code POST /{variable}/update-by-evidence}）：
     * 取当前最新分布 → 纯 Java 贝叶斯更新（blob 显式似然优先，否则置顶收敛规则）→
     * version=max+1 落库 + last_evidence_id 溯源 → 发 COGNITIVE_BELIEF_UPDATED 事件。
     *
     * <p>守卫：当前最新版本若为人工覆写（manualOverride=true），模型自动更新让位专家意见，
     * 直接拒绝（400）——须先经人工覆写端点释放守卫。LLM 零参与（ADR-5 口径）。</p>
     */
    public BeliefDistributionVO updateByEvidence(String variableName, BeliefEvidenceUpdateDTO dto) {
        String variable = require(variableName, "variableName");
        String domain = require(dto == null ? null : dto.getDomain(), "domain");
        String evidenceId = require(dto == null ? null : dto.getEvidenceId(), "evidenceId");

        Map<String, Object> evidenceRow = evidenceStore.findById(evidenceId);
        if (evidenceRow == null) {
            throw new BusinessException(404, "COG-404: 证据不存在: id=" + evidenceId);
        }
        Map<String, Object> latest = store.findLatest(variable, domain);
        if (latest == null) {
            throw new BusinessException(404,
                "COG-404: 不确定性判断不存在: variable=" + variable + ", domain=" + domain);
        }

        boolean manualGuarded = evidenceStore.fieldBoolean(latest, "is_manual_override");
        if (manualGuarded) {
            throw new BusinessException(400,
                "COG-400: 当前最新版本为人工覆写，模型自动更新让位专家意见——请先经覆写端点释放守卫");
        }

        // 当前分布（归一化行内 distribution = List<Map {outcome, prob(double)}>）
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawDist = (List<Map<String, Object>>) store
            .toNormalizedRow(latest, evidenceStore).get("distribution");
        List<BeliefSaveDTO.OutcomeProb> current = new ArrayList<>(rawDist.size());
        for (Map<String, Object> point : rawDist) {
            BeliefSaveDTO.OutcomeProb p = new BeliefSaveDTO.OutcomeProb();
            p.setOutcome(String.valueOf(point.get("outcome")));
            p.setProb(point.get("prob") instanceof Number n ? n.doubleValue() : 0d);
            current.add(p);
        }

        // 证据可信度 + blob 显式似然（无显式似然则走默认置顶收敛规则）
        double confidence = evidenceRow.get("confidence") instanceof Number n ? n.doubleValue() : 0.5d;
        Map<String, Object> blob = evidenceStore.parseJsonObjectText(evidenceStore.fieldString(evidenceRow, "blob"));
        Map<String, Double> likelihood = BayesianUpdater.parseLikelihood(blob);

        // 更新后分布再做强校验（规则已归一化，此处兜底浮点漂移，保持入库口径与注册同款）
        List<BeliefSaveDTO.OutcomeProb> updatedDist = BayesianUpdater.update(current, likelihood, confidence);
        validateDistribution(updatedDist);

        int version = store.maxVersion(variable, domain) + 1;
        String id = "cog_blf_" + UUID.randomUUID().toString().substring(0, 12);
        store.insert(id, variable, trimToNull(evidenceStore.fieldString(latest, "tenant_scope")), domain,
            toDistributionJson(updatedDist), version, evidenceId, false, null, "ACTIVE");
        eventPublisher.publishBeliefUpdated(variable, domain, version, evidenceId, false);
        audit("belief.update-by-evidence", "ecos_cognitive_belief", "success",
            "variable=" + variable + ", domain=" + domain + ", version=" + version + ", evidence=" + evidenceId);
        return getLatest(variable, domain);
    }

    /**
     * P2b — 人工覆写（api-contract 预登记 {@code POST /{variable}/override}）：
     * 专家分布覆盖并置 manualOverride=true + overrideReason 留痕，version=max+1；
     * 覆写后模型自动更新让位专家意见（{@link #updateByEvidence} 以 manualOverride 守卫拒绝），
     * 直至下一次人工覆写主动释放。
     */
    public BeliefDistributionVO override(String variableName, BeliefOverrideDTO dto) {
        String variable = require(variableName, "variableName");
        if (dto == null) {
            throw new BusinessException(400, "COG-400: 请求体必填");
        }
        String domain = require(dto.getDomain(), "domain");
        if (dto.getOverrideReason() == null || dto.getOverrideReason().isBlank()) {
            throw new BusinessException(400, "COG-400: overrideReason 必填（留痕审计）");
        }
        validateDistribution(dto.getDiscreteDistribution());

        Map<String, Object> latest = store.findLatest(variable, domain);
        String tenantScope = latest == null ? null : trimToNull(evidenceStore.fieldString(latest, "tenant_scope"));
        int version = store.maxVersion(variable, domain) + 1;
        String id = "cog_blf_" + UUID.randomUUID().toString().substring(0, 12);
        store.insert(id, variable, tenantScope, domain, toDistributionJson(dto.getDiscreteDistribution()),
            version, trimToNull(dto.getLastEvidenceId()), true, dto.getOverrideReason().trim(), "ACTIVE");
        eventPublisher.publishBeliefUpdated(variable, domain, version, trimToNull(dto.getLastEvidenceId()), true);
        audit("belief.override", "ecos_cognitive_belief", "success",
            "variable=" + variable + ", domain=" + domain + ", version=" + version
                + ", evidence=" + trimToNull(dto.getLastEvidenceId()));
        return getLatest(variable, domain);
    }

    /**
     * 分布强校验（Phase 1 验收记录残留风险 3：DB 为 JSONB 无 CHECK，Service 层补齐）——
     * 非空、outcome 非空不重名、各项 prob∈[0,1]、概率和=1（±{@link #PROB_SUM_TOLERANCE}）。
     */
    public static void validateDistribution(List<BeliefSaveDTO.OutcomeProb> distribution) {
        if (distribution == null || distribution.isEmpty()) {
            throw new BusinessException(400, "COG-400: discreteDistribution 必填且非空");
        }
        java.util.Set<String> outcomes = new java.util.HashSet<>();
        double sum = 0d;
        for (BeliefSaveDTO.OutcomeProb point : distribution) {
            if (point == null || point.getOutcome() == null || point.getOutcome().isBlank()) {
                throw new BusinessException(400, "COG-400: distribution.outcome 必填");
            }
            if (point.getProb() < 0d || point.getProb() > 1d) {
                throw new BusinessException(400, "COG-400: distribution.prob 须在 0~1（当前 " + point.getProb() + "）");
            }
            if (!outcomes.add(point.getOutcome().trim())) {
                throw new BusinessException(400, "COG-400: distribution.outcome 重复: " + point.getOutcome());
            }
            sum += point.getProb();
        }
        if (Math.abs(sum - 1d) > PROB_SUM_TOLERANCE) {
            throw new BusinessException(400, "COG-400: 概率分布和必须 = 1（当前 " + sum + "）");
        }
    }

    /** 分布点 → JSONB 文本 [{"outcome","prob"}]。 */
    public String toDistributionJson(List<BeliefSaveDTO.OutcomeProb> distribution) {
        try {
            return objectMapper.writeValueAsString(distribution);
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, "COG-400: 分布序列化失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private BeliefDistributionVO toVo(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        BeliefDistributionVO vo = new BeliefDistributionVO();
        vo.setId(evidenceStore.fieldString(row, "id"));
        vo.setVariableName(evidenceStore.fieldString(row, "variable_name"));
        vo.setTenantScope(evidenceStore.fieldString(row, "tenant_scope"));
        vo.setDomain(evidenceStore.fieldString(row, "domain"));
        List<Map<String, Object>> dist = (List<Map<String, Object>>) row.get("distribution");
        if (dist != null) {
            List<BeliefDistributionVO.OutcomeProb> points = new ArrayList<>(dist.size());
            for (Map<String, Object> point : dist) {
                points.add(new BeliefDistributionVO.OutcomeProb(
                    String.valueOf(point.get("outcome")), point.get("prob") instanceof Number n ? n.doubleValue() : 0d));
            }
            vo.setDistribution(points);
        }
        vo.setVersion(row.get("version") instanceof Number n ? n.intValue() : 1);
        vo.setSnapshotVersion(row.get("snapshot_version") instanceof Number n ? n.intValue() : null);
        vo.setManualOverride((Boolean) row.get("manualOverride"));
        vo.setOverrideReason(evidenceStore.fieldString(row, "override_reason"));
        vo.setLastEvidenceId(evidenceStore.fieldString(row, "last_evidence_id"));
        vo.setStatus(evidenceStore.fieldString(row, "status"));
        return vo;
    }

    private String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, "COG-400: " + name + " 必填");
        }
        return value.trim();
    }

    private String trimToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** 审计（铁律 §2.4#5 写操作必发 ecos.audit）；未装配/异常降级 WARN 不阻塞。 */
    private void audit(String action, String resource, String result, String detail) {
        if (eventBusService == null) {
            log.warn("不确定性判断审计无法发送: EventBusService 未装配, action={}", action);
            return;
        }
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("action", action);
            event.put("resource", resource);
            event.put("result", result);
            event.put("detail", detail);
            event.put("userId", "system");
            event.put("timestamp", LocalDateTime.now().toString());
            eventBusService.publish(KafkaTopics.AUDIT, event);
        } catch (Exception e) {
            log.warn("不确定性判断审计 (EventBus) failed (ignored): {}", e.getMessage());
        }
    }
}
