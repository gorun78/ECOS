package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景心智变体服务 — 场景授权心智 1:N CRUD（PMO-60 v2.0 P1）。
 *
 * <p>遵循 {@code ScenarioService} 既有模式：单类 {@code @Service} + JdbcTemplate + 无 Lombok。
 * 对应表 {@code ecos_scenario_mind}（DDL V146）。
 * cognitive 三表（evidence/hypothesis/belief）写入走后续 P3b T25，本次仅落 workspace 侧。</p>
 */
@Service
public class ScenarioMindService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioMindService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ScenarioMindService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    // ═══════════════ CRUD ═══════════════

    /**
     * 保存 base mind（upsert 语义）：若 scenario 下已存在 mind_label='base' 则 UPDATE，否则 INSERT。
     *
     * @param scenarioId 场景 id
     * @param dto        心智保存入参
     * @return 保存后的 VO
     */
    @Transactional
    public ScenarioMindVO saveBaselineMind(String scenarioId, MindSaveDTO dto) {
        return upsertMind(scenarioId, dto, "base", true);
    }

    /**
     * 新建或更新心智变体（通用 upsert）。
     *
     * @param scenarioId   场景 id
     * @param dto          心智保存入参（null 时使用默认值）
     * @param forceLabel   强制标签（null = 使用 dto.mindLabel 或 "base"）
     * @param activateIfNew 新建时是否设为 active
     * @return 保存后的 VO
     */
    @Transactional
    public ScenarioMindVO upsertMind(String scenarioId, MindSaveDTO dto, String forceLabel, boolean activateIfNew) {
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new BusinessException(400, "MIND-400: scenarioId 必填");
        }
        String label = (forceLabel != null) ? forceLabel
                : (dto != null && dto.getMindLabel() != null && !dto.getMindLabel().isBlank())
                        ? dto.getMindLabel().trim() : "base";
        String operator = currentOperator();
        Double confidence = (dto != null && dto.getInitialConfidence() != null) ? dto.getInitialConfidence() : 0.5;
        String beliefJson = toJson(dto != null ? dto.getInitialBelief() : null, "{}");
        String evidenceJson = toJsonList(dto != null ? dto.getEvidenceIds() : null);
        String hypothesisJson = toJsonList(dto != null ? dto.getHypothesisIds() : null);
        String modelJson = toJsonList(dto != null ? dto.getModelIds() : null);
        String endpointsJson = toJson(dto != null ? dto.getCognitiveEndpoints() : null, "{}");

        // 查已有（同 scenario + label 未删除）
        List<Long> existing = jdbc.queryForList(
            "SELECT id FROM ecos_scenario_mind WHERE scenario_id = ? AND mind_label = ? AND is_deleted = 0",
            Long.class, scenarioId, label);

        if (!existing.isEmpty()) {
            // UPDATE 已有
            Long mindId = existing.get(0);
            boolean isActive = (label.equals("base"));
            if (!isActive) {
                Integer current = jdbc.query(
                    "SELECT active_mind FROM ecos_scenario_mind WHERE id = ?",
                    (rs, rowNum) -> rs.getInt("active_mind"), mindId).get(0);
                isActive = current == 1;
            }
            int n = jdbc.update(
                "UPDATE ecos_scenario_mind SET mind_label=?, active_mind=?, initial_belief_jsonb=?::jsonb, " +
                "evidence_refs=?::jsonb, hypothesis_refs=?::jsonb, model_refs=?::jsonb, " +
                "cognitive_endpoints=?::jsonb, initial_confidence=?, update_time=NOW(), update_by=? " +
                "WHERE id=? AND is_deleted=0",
                label, isActive ? 1 : 0, beliefJson, evidenceJson, hypothesisJson, modelJson,
                endpointsJson, confidence, operator, mindId);
            if (n == 0) {
                throw new NotFoundException("MIND-404: 心智不存在或已删除: id=" + mindId);
            }
            log.info("更新场景心智 {} label={} by={}", mindId, label, operator);
            return getMind(mindId);
        }

        // INSERT 新 mind
        boolean willBeActive = activateIfNew || label.equals("base");
        if (willBeActive) {
            // 先全清 active_mind（与 activateMind 同一语义，但纳入本事务）
            jdbc.update("UPDATE ecos_scenario_mind SET active_mind=0, update_time=NOW() WHERE scenario_id=? AND is_deleted=0 AND active_mind=1", scenarioId);
        }
        try {
            jdbc.update(
                "INSERT INTO ecos_scenario_mind (scenario_id, mind_label, active_mind, initial_belief_jsonb, " +
                "evidence_refs, hypothesis_refs, model_refs, cognitive_endpoints, initial_confidence, create_by, update_by) " +
                "VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?)",
                scenarioId, label, willBeActive ? 1 : 0, beliefJson, evidenceJson, hypothesisJson,
                modelJson, endpointsJson, confidence, operator, operator);
        } catch (DataIntegrityViolationException e) {
            log.warn("upsertMind 唯一约束冲突: scenario={} label={}", scenarioId, label);
            throw new BusinessException(409, "MIND-409: same scenario already has another active mind 或 label 重复: " + label);
        }
        // 取回新插入的 id
        Long newId = jdbc.queryForList(
            "SELECT id FROM ecos_scenario_mind WHERE scenario_id=? AND mind_label=? AND is_deleted=0 ORDER BY id DESC LIMIT 1",
            Long.class, scenarioId, label).get(0);
        log.info("新建场景心智 {} label={} active={} by={}", newId, label, willBeActive, operator);
        return getMind(newId);
    }

    /** 查询场景下所有心智（未删除，激活优先排序）。 */
    public List<ScenarioMindVO> listMinds(String scenarioId) {
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new BusinessException(400, "MIND-400: scenarioId 必填");
        }
        List<ScenarioMindRaw> rows = jdbc.query(
            "SELECT id, scenario_id, mind_label, active_mind, initial_belief_jsonb, " +
            "evidence_refs, hypothesis_refs, model_refs, cognitive_endpoints, initial_confidence, " +
            "create_time, update_time " +
            "FROM ecos_scenario_mind WHERE scenario_id=? AND is_deleted=0 " +
            "ORDER BY (active_mind=1) DESC, mind_label", MIND_MAPPER, scenarioId);
        List<ScenarioMindVO> result = new ArrayList<>(rows.size());
        for (ScenarioMindRaw r : rows) {
            result.add(toVO(r));
        }
        return result;
    }

    /**
     * 切换激活心智：先全清 active_mind=0，再设目标 mind active_mind=1（同一事务）。
     *
     * @param scenarioId 场景 id
     * @param mindId     目标心智 id
     * @return 切换后的 VO
     */
    @Transactional
    public ScenarioMindVO activateMind(String scenarioId, Long mindId) {
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new BusinessException(400, "MIND-400: scenarioId 必填");
        }
        if (mindId == null) {
            throw new BusinessException(400, "MIND-400: mindId 必填");
        }
        // 验证 mind 属于该场景
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(1) FROM ecos_scenario_mind WHERE id=? AND scenario_id=? AND is_deleted=0",
            Integer.class, mindId, scenarioId);
        if (n == null || n == 0) {
            throw new NotFoundException("MIND-404: 心智不存在或不属于该场景: id=" + mindId);
        }
        String operator = currentOperator();
        try {
            // 全清
            jdbc.update("UPDATE ecos_scenario_mind SET active_mind=0, update_time=NOW(), update_by=? WHERE scenario_id=? AND is_deleted=0 AND active_mind=1",
                    operator, scenarioId);
            // 设 1
            int updated = jdbc.update(
                "UPDATE ecos_scenario_mind SET active_mind=1, update_time=NOW(), update_by=? WHERE id=? AND scenario_id=? AND is_deleted=0",
                operator, mindId, scenarioId);
            if (updated == 0) {
                throw new NotFoundException("MIND-404: 心智不存在或已删除: id=" + mindId);
            }
        } catch (DataIntegrityViolationException e) {
            log.warn("activateMind 唯一约束冲突: scenario={} mindId={}", scenarioId, mindId);
            throw new BusinessException(409, "MIND-409: 激活心智切换失败（唯一约束）");
        }
        log.info("切换激活心智 scenario={} mindId={} by={}", scenarioId, mindId, operator);
        return getMind(mindId);
    }

    /**
     * 逻辑删除心智。若删除的是 base（mind_label='base'），自动补位：找其他未删 mind 的第一个设为 active=1。
     *
     * @param scenarioId 场景 id
     * @param mindId     心智 id
     * @return 是否删除成功
     */
    @Transactional
    public boolean deleteMind(String scenarioId, Long mindId) {
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new BusinessException(400, "MIND-400: scenarioId 必填");
        }
        if (mindId == null) {
            throw new BusinessException(400, "MIND-400: mindId 必填");
        }
        String operator = currentOperator();
        // 先查被删 mind 的 label
        String label = jdbc.queryForList(
            "SELECT mind_label FROM ecos_scenario_mind WHERE id=? AND scenario_id=? AND is_deleted=0",
            String.class, mindId, scenarioId).stream().findFirst().orElse(null);
        if (label == null) {
            throw new NotFoundException("MIND-404: 心智不存在或不属于该场景: id=" + mindId);
        }
        int n = jdbc.update(
            "UPDATE ecos_scenario_mind SET is_deleted=1, update_time=NOW(), update_by=? WHERE id=? AND scenario_id=? AND is_deleted=0",
            operator, mindId, scenarioId);
        if (n == 0) {
            throw new NotFoundException("MIND-404: 心智不存在或已删除: id=" + mindId);
        }
        // 若删的是 base，自动补位
        if ("base".equals(label)) {
            List<Long> remaining = jdbc.queryForList(
                "SELECT id FROM ecos_scenario_mind WHERE scenario_id=? AND is_deleted=0 AND active_mind=1 LIMIT 1",
                Long.class, scenarioId);
            if (remaining.isEmpty()) {
                // 找一个未删 mind 设为 active
                List<Long> candidates = jdbc.queryForList(
                    "SELECT id FROM ecos_scenario_mind WHERE scenario_id=? AND is_deleted=0 ORDER BY create_time LIMIT 1",
                    Long.class, scenarioId);
                if (!candidates.isEmpty()) {
                    jdbc.update("UPDATE ecos_scenario_mind SET active_mind=1, update_time=NOW(), update_by=? WHERE id=?",
                            operator, candidates.get(0));
                    log.info("删除 base mind 后自动补位: scenario={} newActive={}", scenarioId, candidates.get(0));
                }
            }
        }
        log.info("逻辑删除场景心智 scenario={} mindId={} by={}", scenarioId, mindId, operator);
        return true;
    }

    /**
     * 获取场景当前激活心智（active_mind=1 的行）。
     *
     * @param scenarioId 场景 id
     * @return 激活的 mind VO；若不存在则返回 null
     */
    public ScenarioMindVO getActiveMind(String scenarioId) {
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new BusinessException(400, "MIND-400: scenarioId 必填");
        }
        List<ScenarioMindRaw> rows = jdbc.query(
            "SELECT id, scenario_id, mind_label, active_mind, initial_belief_jsonb, " +
            "evidence_refs, hypothesis_refs, model_refs, cognitive_endpoints, initial_confidence, " +
            "create_time, update_time " +
            "FROM ecos_scenario_mind WHERE scenario_id=? AND active_mind=1 AND is_deleted=0", MIND_MAPPER, scenarioId);
        if (rows.isEmpty()) {
            return null;
        }
        return toVO(rows.get(0));
    }

    /**
     * 获取 base mind（兼容 v1.x /mind-model 端点）。
     *
     * @param scenarioId 场景 id
     * @return base mind VO；若不存在则返回 null
     */
    public ScenarioMindVO getMindBase(String scenarioId) {
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new BusinessException(400, "MIND-400: scenarioId 必填");
        }
        List<ScenarioMindRaw> rows = jdbc.query(
            "SELECT id, scenario_id, mind_label, active_mind, initial_belief_jsonb, " +
            "evidence_refs, hypothesis_refs, model_refs, cognitive_endpoints, initial_confidence, " +
            "create_time, update_time " +
            "FROM ecos_scenario_mind WHERE scenario_id=? AND mind_label='base' AND is_deleted=0", MIND_MAPPER, scenarioId);
        if (rows.isEmpty()) {
            return null;
        }
        return toVO(rows.get(0));
    }

    /**
     * 按 id 获取单个 mind。
     */
    public ScenarioMindVO getMind(Long mindId) {
        List<ScenarioMindRaw> rows = jdbc.query(
            "SELECT id, scenario_id, mind_label, active_mind, initial_belief_jsonb, " +
            "evidence_refs, hypothesis_refs, model_refs, cognitive_endpoints, initial_confidence, " +
            "create_time, update_time " +
            "FROM ecos_scenario_mind WHERE id=? AND is_deleted=0", MIND_MAPPER, mindId);
        if (rows.isEmpty()) {
            throw new NotFoundException("MIND-404: 心智不存在或已删除: id=" + mindId);
        }
        return toVO(rows.get(0));
    }

    // ═══════════════ 私有辅助 ═══════════════

    /** 心智行映射（JSONB 字段读为字符串，后续 toVO 解析）。 */
    private final RowMapper<ScenarioMindRaw> MIND_MAPPER = (rs, rowNum) -> {
        ScenarioMindRaw r = new ScenarioMindRaw();
        r.id = rs.getLong("id");
        r.scenarioId = rs.getString("scenario_id");
        r.mindLabel = rs.getString("mind_label");
        r.activeMind = rs.getInt("active_mind");
        r.initialBeliefJson = rs.getString("initial_belief_jsonb");
        r.evidenceRefsJson = rs.getString("evidence_refs");
        r.hypothesisRefsJson = rs.getString("hypothesis_refs");
        r.modelRefsJson = rs.getString("model_refs");
        r.cognitiveEndpointsJson = rs.getString("cognitive_endpoints");
        r.initialConfidence = rs.getDouble("initial_confidence");
        r.createTime = rs.getTimestamp("create_time");
        r.updateTime = rs.getTimestamp("update_time");
        return r;
    };

    /** PO 中间对象（JSONB 保持原始字符串，toVO 时解析）。 */
    private static class ScenarioMindRaw {
        Long id;
        String scenarioId;
        String mindLabel;
        int activeMind;
        String initialBeliefJson;
        String evidenceRefsJson;
        String hypothesisRefsJson;
        String modelRefsJson;
        String cognitiveEndpointsJson;
        double initialConfidence;
        java.sql.Timestamp createTime;
        java.sql.Timestamp updateTime;
    }

    private ScenarioMindVO toVO(ScenarioMindRaw r) {
        ScenarioMindVO vo = new ScenarioMindVO();
        vo.setId(r.id);
        vo.setScenarioId(r.scenarioId);
        vo.setMindLabel(r.mindLabel);
        vo.setActiveMind(r.activeMind);
        vo.setInitialBelief(parseJson(r.initialBeliefJson));
        vo.setEvidenceRefs(parseJson(r.evidenceRefsJson));
        vo.setHypothesisRefs(parseJson(r.hypothesisRefsJson));
        vo.setModelRefs(parseJson(r.modelRefsJson));
        vo.setCognitiveEndpoints(parseJson(r.cognitiveEndpointsJson));
        vo.setInitialConfidence(r.initialConfidence);
        vo.setCreateTime(r.createTime != null ? r.createTime.toLocalDateTime().toString() : null);
        vo.setUpdateTime(r.updateTime != null ? r.updateTime.toLocalDateTime().toString() : null);
        return vo;
    }

    private String toJson(Object obj, String defaultStr) {
        if (obj == null) {
            return defaultStr;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BusinessException(500, "MIND-500: JSON 序列化失败: " + e.getMessage());
        }
    }

    private String toJsonList(List<String> list) {
        return toJson(list != null ? list : new ArrayList<>(), "[]");
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Object>() {});
        } catch (Exception e) {
            log.warn("mind JSONB 解析失败，返 raw: {}", e.getMessage());
            return json;
        }
    }

    private String currentOperator() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String) {
                return (String) auth.getPrincipal();
            }
        } catch (Exception e) {
            log.debug("无法从 SecurityContext 取操作人，回退 system: {}", e.getMessage());
        }
        return "system";
    }
}
