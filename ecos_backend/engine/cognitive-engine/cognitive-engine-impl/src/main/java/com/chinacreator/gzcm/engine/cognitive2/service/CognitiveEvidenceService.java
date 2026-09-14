package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.common.cognitive.EvidenceRecordVO;
import com.chinacreator.gzcm.common.cognitive.HypothesisVO;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.cognitive2.dto.EvidenceSaveDTO;
import com.chinacreator.gzcm.engine.cognitive2.service.mental.MentalEventPublisher;
import com.chinacreator.gzcm.engine.cognitive2.service.mental.MentalInvalidationDetector;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 认知证据业务服务（PMO-59 P2a / ADR-9 心智层）— 表 {@code ecos_cognitive_evidence}（V127）。
 *
 * <p>能力（外部方案层1"感知接入层"输出物落地）：结构化证据登记（evidence_code 幂等）/
 * 列表 / 详情 / 同事实多值自动冲突检测（登记时按 blob.fact + blob.metric 匹配、value 不等 →
 * {@code is_conflict=true} 并写入 {@code refuting_evidence_ids}，对向证据联动置 CONFLICTED）。</p>
 *
 * <p><b>P2b 增量</b>：登记成功后 ① 发 {@code ecos.cognitive} 证据登记事件
 * （{@link MentalEventPublisher}，补算扫描链路输入）；② 触发假设失效自动检测
 * （{@link MentalInvalidationDetector}：refuting 命中/高可信冲突/数值漂移 → 假设 INVALIDATED
 * + 失效事件 + runtime-monitor 告警）。检测异常不阻断登记主流程（告警降级 WARN）。</p>
 *
 * <p>写操作审计（铁律 §2.4#5）：经 {@link EventBusService}（runtime-event 公共底座，
 * 铁律 §2.5 禁止自建 KafkaTemplate）发 Kafka {@code ecos.audit}；Bus 未装配/发送异常
 * 降级 WARN 不阻塞主流程。</p>
 *
 * <p>安全集成卡口径：本表无密钥/凭据/密码/Token 类字段，不触发 §2.4#8 加密强制卡；
 * source_ref 为来源定位标识非凭据（Phase 1 验收记录 §3 同口径）。</p>
 */
@Service
@RequiredArgsConstructor
public class CognitiveEvidenceService {

    private static final Logger log = LoggerFactory.getLogger(CognitiveEvidenceService.class);

    private final EvidenceStore store;
    private final ObjectMapper objectMapper;

    /** P2b: 假设失效自动检测（登记后即时触发；detector→hypothesisService 单向依赖，无环） */
    private final MentalInvalidationDetector invalidationDetector;

    /** P2b: 心智事件发布（ecos.cognitive 证据登记事件） */
    private final MentalEventPublisher eventPublisher;

    /** 审计通道 — 可选装配（与 ontology/data 引擎侧同款模式：required=false + null 兜底） */
    @Autowired(required = false)
    private EventBusService eventBusService;

    /**
     * 登记一条结构化证据（evidence_code 幂等；服务端生成 id 与审计列）。
     *
     * @param dto 强类型入参（evidenceCode/sourceType 必填；blob 须为合法 JSON 对象）
     * @return 落库后的证据 VO
     */
    public EvidenceRecordVO register(EvidenceSaveDTO dto) {
        if (dto == null || dto.getEvidenceCode() == null || dto.getEvidenceCode().isBlank()) {
            throw new BusinessException(400, "COG-400: evidenceCode 必填");
        }
        String sourceType = upperTrim(dto.getSourceType());
        if (sourceType == null || !EvidenceStore.SOURCE_TYPES.contains(sourceType)) {
            throw new BusinessException(400,
                "COG-400: 非法 sourceType（" + String.join("/", EvidenceStore.SOURCE_TYPES) + "）");
        }
        if (dto.getBlob() == null || !isJsonObjectJson(dto.getBlob())) {
            throw new BusinessException(400, "COG-400: blob 必填且须为合法 JSON 对象");
        }
        double confidence = dto.getConfidence() == null ? 0.5d : dto.getConfidence();
        if (confidence < 0d || confidence > 1d) {
            throw new BusinessException(400, "COG-400: confidence 须在 0~1");
        }
        String code = dto.getEvidenceCode().trim();
        if (store.existsByCode(code)) {
            throw new BusinessException(400, "COG-400: evidenceCode 已存在（幂等拒绝）: " + code);
        }

        // 同 fact+metric 既有证据 value 不等 → 自动判冲突（显式标记优先，检出即追加）
        List<String> autoConflicts = List.of();
        boolean isConflict = Boolean.TRUE.equals(dto.getIsConflict());
        Map<String, Object> blob = store.parseJsonObjectText(dto.getBlob());
        autoConflicts = store.findConflictingIds(
            store.fieldString(blob, "fact"), store.fieldString(blob, "metric"),
            store.fieldString(blob, "value"));
        if (!autoConflicts.isEmpty()) {
            isConflict = true;
            log.info("新证据登记触发自动冲突检测: code={} 冲突方={}", code, autoConflicts);
        }
        List<String> refutingIds = mergeRefutingIds(
            store.parseJsonListText(dto.getRefutingEvidenceIds()), autoConflicts);

        String id = "cog_ev_" + UUID.randomUUID().toString().substring(0, 12);
        String status = isConflict ? "CONFLICTED" : "ACTIVE";
        store.insert(id, code, dto.getTenantScope(), sourceType, dto.getSourceRef(), dto.getBlob(),
            BigDecimal.valueOf(confidence), isConflict, toJsonArrayText(refutingIds),
            dto.getEffectiveTime(), dto.getExpireTime(), status);

        // 对向联动：冲突对方证据追加本 id 进 refuting 并置 CONFLICTED（幂等）
        for (String oppositeId : autoConflicts) {
            if (store.markRefuted(oppositeId, id)) {
                log.info("冲突联动: opposite={} 追加 refuting={} → CONFLICTED", oppositeId, id);
            }
        }
        audit("evidence.create", "ecos_cognitive_evidence", "success", "id=" + id);
        EvidenceRecordVO vo = toVo(store.toNormalizedRow(store.findById(id)));

        // P2b: 登记事件（ecos.cognitive；冲突标记随事件带出，补算扫描链路输入）— 失败不阻塞
        try {
            Map<String, Object> evBlob = store.parseJsonObjectText(vo.getBlob());
            eventPublisher.publishEvidenceRegistered(vo.getId(), code,
                store.fieldString(evBlob, "fact"), store.fieldString(evBlob, "metric"),
                vo.isConflict());
            // P2b: 假设失效自动检测（命中→INVALIDATED + 失效事件 + runtime-monitor 告警）— 失败不阻塞
            var invalidated = invalidationDetector.detectAndInvalidate(vo);
            if (invalidated != null && !invalidated.isEmpty()) {
                log.info("新证据登记触发假设失效 {} 条: 触发证据={} 命中={}",
                    invalidated.size(), id, invalidated.stream().map(HypothesisVO::getHypothesisCode).toList());
            }
        } catch (Exception e) {
            log.warn("证据登记后心智联动失败 (ignored, 登记主流程不受影响): evidence={} err={}",
                id, e.getMessage());
        }
        return vo;
    }

    /** 证据列表（status/sourceType 可选过滤）。 */
    public List<EvidenceRecordVO> list(String status, String sourceType) {
        if (status != null && !status.isBlank() && !EvidenceStore.STATUSES.contains(status.toUpperCase())) {
            throw new BusinessException(400, "COG-400: 非法 status（" + String.join("/", EvidenceStore.STATUSES) + "）");
        }
        if (sourceType != null && !sourceType.isBlank()
            && !EvidenceStore.SOURCE_TYPES.contains(sourceType.trim().toUpperCase())) {
            throw new BusinessException(400, "COG-400: 非法 sourceType（" + String.join("/", EvidenceStore.SOURCE_TYPES) + "）");
        }
        return store.list(status, sourceType).stream().map(store::toNormalizedRow).map(this::toVo).toList();
    }

    /** 证据详情；无记录返回 null（由 Controller 转 404）。 */
    public EvidenceRecordVO getDetail(String id) {
        Map<String, Object> row = store.findById(id);
        return row == null ? null : toVo(store.toNormalizedRow(row));
    }

    /** 行映射 → common-api 证据 VO（JSONB 列已在 Store 归一为文本/列表）。 */
    private EvidenceRecordVO toVo(Map<String, Object> row) {
        EvidenceRecordVO vo = new EvidenceRecordVO();
        vo.setId(store.fieldString(row, "id"));
        vo.setEvidenceCode(store.fieldString(row, "evidence_code"));
        vo.setTenantScope(store.fieldString(row, "tenant_scope"));
        vo.setSourceType(store.fieldString(row, "source_type"));
        vo.setSourceRef(store.fieldString(row, "source_ref"));
        vo.setBlob(store.fieldString(row, "blob"));
        vo.setConfidence(row.get("confidence") instanceof Number n ? n.doubleValue() : 0.5d);
        vo.setConflict(store.fieldBoolean(row, "is_conflict"));
        vo.setRefutingEvidenceIds(toJsonArrayText(
            store.parseJsonListText(store.fieldString(row, "refuting_evidence_ids"))));
        vo.setEffectiveTime(store.fieldTime(row, "effective_time"));
        vo.setExpireTime(store.fieldTime(row, "expire_time"));
        vo.setStatus(store.fieldString(row, "status"));
        return vo;
    }

    /** 合并显式 refuting 列表与自动检出冲突（保序去重）。 */
    private List<String> mergeRefutingIds(List<String> explicit, List<String> autoDetected) {
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>(explicit);
        merged.addAll(autoDetected);
        return List.copyOf(merged);
    }

    private String toJsonArrayText(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private boolean isJsonObjectJson(String jsonText) {
        try {
            return objectMapper.readTree(jsonText).isObject();
        } catch (Exception e) {
            return false;
        }
    }

    private String upperTrim(String v) {
        return v == null || v.isBlank() ? null : v.trim().toUpperCase();
    }

    /**
     * 审计（铁律 §2.4#5 写操作必发 ecos.audit）— EventBus 主通道 + 日志兜底；
     * EventBus 未装配/发送异常降级 WARN 不阻塞（payload 与 ontology 侧同构：action/resource/result/detail/userId/timestamp）。
     */
    private void audit(String action, String resource, String result, String detail) {
        if (eventBusService == null) {
            log.warn("认知证据审计无法发送: EventBusService 未装配, action={}", action);
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
            log.warn("认知证据审计 (EventBus) failed (ignored): {}", e.getMessage());
        }
    }
}
