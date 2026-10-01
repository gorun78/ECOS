package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
 * <p>对应表 {@code ecos_scenario_mind}（DDL V146）。
 * cognitive 三表（evidence/hypothesis/belief）写入走后续 P3b T25，本次仅落 workspace 侧。</p>
 *
 * <p>B6a 收口（R2 架构铁律 §2.5 / IR06）：raw-SQL + RowMapper 下沉 {@link WorkspaceDataDao}；
 * 行对象由本服务公开嵌套类 {@link ScenarioMindRawRef} 承载（DAO 侧 RowMapper 需可见）。</p>
 */
@Service
public class ScenarioMindService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioMindService.class);

    private final WorkspaceDataDao dao;
    private final ObjectMapper objectMapper;

    public ScenarioMindService(WorkspaceDataDao dao, ObjectMapper objectMapper) {
        this.dao = dao;
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
        List<Long> existing = dao.listMindIdsByScenarioAndLabel(scenarioId, label);

        if (!existing.isEmpty()) {
            // UPDATE 已有
            Long mindId = existing.get(0);
            boolean isActive = (label.equals("base"));
            if (!isActive) {
                Integer current = dao.selectMindActiveFlag(mindId);
                isActive = current != null && current == 1;
            }
            int n = dao.updateMindUpsert(
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
            dao.deactivateAllMindsOfScenario(scenarioId);
        }
        try {
            dao.insertMind(scenarioId, label, willBeActive ? 1 : 0, beliefJson, evidenceJson, hypothesisJson,
                modelJson, endpointsJson, confidence, operator);
        } catch (DataIntegrityViolationException e) {
            log.warn("upsertMind 唯一约束冲突: scenario={} label={}", scenarioId, label);
            throw new BusinessException(409, "MIND-409: same scenario already has another active mind 或 label 重复: " + label);
        }
        // 取回新插入的 id
        Long newId = dao.selectLatestMindId(scenarioId, label);
        log.info("新建场景心智 {} label={} active={} by={}", newId, label, willBeActive, operator);
        return getMind(newId);
    }

    /** 查询场景下所有心智（未删除，激活优先排序）。 */
    public List<ScenarioMindVO> listMinds(String scenarioId) {
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new BusinessException(400, "MIND-400: scenarioId 必填");
        }
        List<ScenarioMindRawRef> rows = dao.listMindsOfScenario(scenarioId);
        List<ScenarioMindVO> result = new ArrayList<>(rows.size());
        for (ScenarioMindRawRef r : rows) {
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
        Integer n = dao.countMindBelongsToScenario(mindId, scenarioId);
        if (n == null || n == 0) {
            throw new NotFoundException("MIND-404: 心智不存在或不属于该场景: id=" + mindId);
        }
        String operator = currentOperator();
        try {
            // 全清
            dao.deactivateScenarioMinds(operator, scenarioId);
            // 设 1
            int updated = dao.activateMindById(operator, mindId, scenarioId);
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
        String label = dao.selectMindLabel(mindId, scenarioId);
        if (label == null) {
            throw new NotFoundException("MIND-404: 心智不存在或不属于该场景: id=" + mindId);
        }
        int n = dao.logicDeleteMind(operator, mindId, scenarioId);
        if (n == 0) {
            throw new NotFoundException("MIND-404: 心智不存在或已删除: id=" + mindId);
        }
        // 若删的是 base，自动补位
        if ("base".equals(label)) {
            List<Long> remaining = dao.listActiveMindIds(scenarioId);
            if (remaining.isEmpty()) {
                // 找一个未删 mind 设为 active
                List<Long> candidates = dao.listFirstMindId(scenarioId);
                if (!candidates.isEmpty()) {
                    dao.activateMindByIdOnly(operator, candidates.get(0));
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
        List<ScenarioMindRawRef> rows = dao.listActiveMind(scenarioId);
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
        List<ScenarioMindRawRef> rows = dao.listBaseMind(scenarioId);
        if (rows.isEmpty()) {
            return null;
        }
        return toVO(rows.get(0));
    }

    /**
     * 按 id 获取单个 mind。
     */
    public ScenarioMindVO getMind(Long mindId) {
        List<ScenarioMindRawRef> rows = dao.listMindById(mindId);
        if (rows.isEmpty()) {
            throw new NotFoundException("MIND-404: 心智不存在或已删除: id=" + mindId);
        }
        return toVO(rows.get(0));
    }

    // ═══════════════ 私有辅助 ═══════════════

    /** 心智 PO 中间对象（B6a 收口时保留 — JSONB 保持原始字符串，toVO 时解析）。 */
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

    /**
     * B6a 收口：DAO 侧心智行引用（{@code WorkspaceDataDao} 的 RowMapper 落点）。
     * 字段与 {@link ScenarioMindRaw} 逐字段一致，由 Service 侧 {@code toVO} 直接消费。
     */
    public static class ScenarioMindRawRef {
        public Long id;
        public String scenarioId;
        public String mindLabel;
        public int activeMind;
        public String initialBeliefJson;
        public String evidenceRefsJson;
        public String hypothesisRefsJson;
        public String modelRefsJson;
        public String cognitiveEndpointsJson;
        public double initialConfidence;
        public java.sql.Timestamp createTime;
        public java.sql.Timestamp updateTime;
    }

    private ScenarioMindVO toVO(ScenarioMindRawRef r) {
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
