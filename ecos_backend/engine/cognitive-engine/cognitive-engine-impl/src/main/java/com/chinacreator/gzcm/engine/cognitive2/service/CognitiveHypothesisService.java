package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.common.cognitive.HypothesisVO;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.cognitive2.dto.HypothesisSaveDTO;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 认知假设业务服务（PMO-59 P2a / ADR-9 心智层 H 库）— 表 {@code ecos_cognitive_hypothesis}（V128）。
 *
 * <p><b>P2a 范围</b>：注册 / 列表 / 详情 / 人工失效（status 切换 + invalid_at + invalid_reason 写）/ 归档。
 * 本单<IHypothesisLifecycleService> 契约只做"状态切换 + 失效时间写"；
 * <b>失效检测联动规则与告警链路 Phase 2b 实现</b>（{@code detectInvalidation} 的监测判定届时接入，
 * 本单人工失效端点即其兜底入口，契约前置不阻塞）。</p>
 *
 * <p>写操作审计（铁律 §2.4#5）：经 {@link EventBusService} 发 Kafka {@code ecos.audit}；
 * 未装配/异常降级 WARN 不阻塞。</p>
 */
@Service
@RequiredArgsConstructor
public class CognitiveHypothesisService {

    private static final Logger log = LoggerFactory.getLogger(CognitiveHypothesisService.class);

    private final HypothesisStore store;
    private final EvidenceStore evidenceStore;
    private final ObjectMapper objectMapper;

    /** 审计/事件通道 — 可选装配（required=false + null 兜底，与 CognitiveEvidenceService 同款） */
    @Autowired(required = false)
    private EventBusService eventBusService;

    /** 注册一条假设（初始 VALID / is_valid=true；hypothesis_code 幂等）。 */
    public HypothesisVO register(HypothesisSaveDTO dto) {
        if (dto == null || dto.getHypothesisCode() == null || dto.getHypothesisCode().isBlank()) {
            throw new BusinessException(400, "COG-400: hypothesisCode 必填");
        }
        if (dto.getStatement() == null || dto.getStatement().isBlank()) {
            throw new BusinessException(400, "COG-400: statement 必填");
        }
        String code = dto.getHypothesisCode().trim();
        if (store.existsByCode(code)) {
            throw new BusinessException(400, "COG-400: hypothesisCode 已存在（幂等拒绝）: " + code);
        }
        String id = "cog_hyp_" + UUID.randomUUID().toString().substring(0, 12);
        store.insert(id, code, dto.getTenantScope(), dto.getStatement().trim(),
            trimToNull(dto.getDomain()), trimToNull(dto.getMetricRef()), toJsonArray(dto.getEvidenceIds()));
        audit("hypothesis.create", "ecos_cognitive_hypothesis", "success", "id=" + id);
        return toVo(store.toNormalizedRow(store.findById(id), evidenceStore));
    }

    /** 假设列表（domain/status 可选过滤）。 */
    public List<HypothesisVO> list(String domain, String status) {
        if (status != null && !status.isBlank()
            && !HypothesisStore.STATUSES.contains(status.toUpperCase())) {
            throw new BusinessException(400, "COG-400: 非法 status（" + String.join("/", HypothesisStore.STATUSES) + "）");
        }
        return store.list(domain, status).stream()
            .map(row -> store.toNormalizedRow(row, evidenceStore))
            .map(this::toVo).toList();
    }

    /** 假设详情；无记录返回 null（由 Controller 转 404）。 */
    public HypothesisVO getDetail(String id) {
        Map<String, Object> row = store.findById(id);
        return row == null ? null : toVo(store.toNormalizedRow(row, evidenceStore));
    }

    /** 人工失效（专家干预兜底）：status→INVALIDATED + is_valid=false + invalid_at/invalid_reason 留痕。 */
    public HypothesisVO invalidate(String id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(400, "COG-400: reason 必填（留痕审计）");
        }
        Map<String, Object> row = store.findById(id);
        if (row == null) {
            throw new BusinessException(404, "COG-404: 假设不存在: id=" + id);
        }
        if (!"VALID".equals(evidenceStore.fieldString(row, "status"))) {
            throw new BusinessException(400, "COG-400: 仅 VALID 假设可失效（当前 status="
                + evidenceStore.fieldString(row, "status") + "）");
        }
        boolean updated = store.updateStatus(id, "INVALIDATED", false, LocalDateTime.now(), reason.trim());
        if (!updated) {
            throw new BusinessException(400, "COG-400: 假设失效更新失败（并发冲突? 请重查状态）");
        }
        // P2b 接入点: 失效自动检测 + ecos.cognitive 失效事件 + runtime-monitor 告警（本单仅人工失效留痕）
        audit("hypothesis.invalidate", "ecos_cognitive_hypothesis", "success", "id=" + id);
        return getDetail(id);
    }

    /** 归档：退出有效集合，保留历史可查。 */
    public HypothesisVO archive(String id) {
        Map<String, Object> row = store.findById(id);
        if (row == null) {
            throw new BusinessException(404, "COG-404: 假设不存在: id=" + id);
        }
        boolean updated = store.updateStatus(id, "ARCHIVED", false, null, "archived");
        if (!updated) {
            throw new BusinessException(400, "COG-400: 假设归档更新失败（并发冲突? 请重查状态）");
        }
        audit("hypothesis.archive", "ecos_cognitive_hypothesis", "success", "id=" + id);
        return getDetail(id);
    }

    private HypothesisVO toVo(Map<String, Object> row) {
        HypothesisVO vo = new HypothesisVO();
        vo.setId(evidenceStore.fieldString(row, "id"));
        vo.setHypothesisCode(evidenceStore.fieldString(row, "hypothesis_code"));
        vo.setTenantScope(evidenceStore.fieldString(row, "tenant_scope"));
        vo.setStatement(evidenceStore.fieldString(row, "statement"));
        vo.setDomain(evidenceStore.fieldString(row, "domain"));
        vo.setMetricRef(evidenceStore.fieldString(row, "metric_ref"));
        @SuppressWarnings("unchecked")
        List<String> evidenceIds = (List<String>) row.get("evidenceIds");
        vo.setEvidenceIds(evidenceIds);
        vo.setValid((Boolean) row.get("valid"));
        vo.setInvalidAt((LocalDateTime) row.get("invalidAt"));
        vo.setInvalidReason(evidenceStore.fieldString(row, "invalid_reason"));
        vo.setStatus(evidenceStore.fieldString(row, "status"));
        return vo;
    }

    private String toJsonArray(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String trimToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private void audit(String action, String resource, String result, String detail) {
        if (eventBusService == null) {
            log.warn("认知假设审计无法发送: EventBusService 未装配, action={}", action);
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
            log.warn("认知假设审计 (EventBus) failed (ignored): {}", e.getMessage());
        }
    }
}
