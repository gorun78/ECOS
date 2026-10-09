package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 场景心智变体服务 — 读面保留、写面停写（设计-05 F05-02 / W116）。
 *
 * <p>legacy {@code ecos_scenario_mind}（V146）库内不存在且不重建 legacy 名，
 * upsert/activate/delete 统一抛 501；场景↔Mind 绑定改经 cognitive 注册门面写
 * {@code ecos_cognitive.ecos_cognitive_scenario_mind}（V188）。</p>
 *
 * <p>B6a 收口（R2 架构铁律 §2.5 / IR06）：raw-SQL + RowMapper 下沉 {@link WorkspaceDataDao}；
 * 行对象由本服务公开嵌套类 {@link ScenarioMindRawRef} 承载（DAO 侧 RowMapper 需可见）。</p>
 */
@Service
public class ScenarioMindService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioMindService.class);

    /** F05-02/W116 停写：legacy V146 表库内不存在且不重建，写面统一拒绝。 */
    private static final String STOPPED_WRITE_MSG =
            "MIND-501: legacy ecos_scenario_mind 写面已停写"
            + "（设计-05 F05-02/W116：V146 表库内不存在且不重建 legacy 名；场景↔Mind 绑定一律改经"
            + " cognitive 注册门面写认知域场景-Mind 绑定表（V188）";

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
        throw new BusinessException(501, STOPPED_WRITE_MSG);
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
        throw new BusinessException(501, STOPPED_WRITE_MSG);
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
        throw new BusinessException(501, STOPPED_WRITE_MSG);
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
}