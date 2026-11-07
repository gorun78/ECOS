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
import java.util.Map;

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
    /** B44/P1-20：认知域 Mind 注册门面客户端（V187+V188 唯一写面）。 */
    private final DcchengClient cognitionClient;

    public ScenarioMindService(WorkspaceDataDao dao, ObjectMapper objectMapper,
                               DcchengClient cognitionClient) {
        this.dao = dao;
        this.objectMapper = objectMapper;
        this.cognitionClient = cognitionClient;
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
        MindSaveDTO body = dto == null ? new MindSaveDTO() : dto;
        String label = forceLabel != null ? forceLabel
                : (body.getMindLabel() == null || body.getMindLabel().isBlank() ? "base" : body.getMindLabel().trim());
        String mindKey = "mind_" + scenarioId.trim() + "_" + label;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scenarioId", scenarioId.trim());
        payload.put("mindKey", mindKey);
        payload.put("name", label);
        payload.put("description", "");
        payload.put("capabilityMask", capabilityMaskOf(body.getCognitiveEndpoints()));
        payload.put("closedLoopBounds", List.of());
        payload.put("bindingRole", "PRIMARY");
        payload.put("priority", 100);
        payload.put("active", activateIfNew);
        payload.put("operator", "workspace");
        Map<String, Object> registered = cognitionClient.registerMind(payload);

        ScenarioMindVO vo = new ScenarioMindVO();
        vo.setScenarioId(scenarioId.trim());
        vo.setMindLabel(label);
        vo.setActiveMind(Boolean.TRUE.equals(registered.get("isActive")) ? 1 : 0);
        vo.setMindRef(registered.get("mindId") == null ? null : String.valueOf(registered.get("mindId")));
        vo.setCognitiveEndpoints(body.getCognitiveEndpoints());
        vo.setInitialConfidence(body.getInitialConfidence());
        log.info("Mind 已注册到认知域: scenario={} label={} mindRef={}", scenarioId, label, vo.getMindRef());
        return vo;
    }

    /** B44/P1-20：按认知 Mind 引用解绑（返回受影响行数；0 行如实 0）。 */
    public int unbindByRef(String scenarioId, String mindRef) {
        Map<String, Object> result = cognitionClient.unbindMind(mindRef, scenarioId);
        Object rows = result == null ? null : result.get("unboundRows");
        return rows instanceof Number n ? n.intValue() : 0;
    }

    /** B44/P1-20：按引用切换激活位（返回受影响行数；0 = 目标不存在，如实回 0）。 */
    public int setActiveByRef(String scenarioId, String mindRef, boolean active) {
        Map<String, Object> result = cognitionClient.setMindActive(mindRef, active);
        Object rows = result == null ? null : result.get("updatedRows");
        log.info("Mind 激活位切换: scenario={} mindRef={} active={} rows={}", scenarioId, mindRef, active, rows);
        return rows instanceof Number n ? n.intValue() : 0;
    }

    /** 认知端点配置里 enabled!=false 的键 → capabilityMask（缺配置 = 空掩码，不臆造能力）。 */
    private static List<String> capabilityMaskOf(Object cognitiveEndpoints) {
        List<String> mask = new ArrayList<>();
        if (cognitiveEndpoints instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Object v = e.getValue();
                boolean disabled = v instanceof Map<?, ?> vm && Boolean.FALSE.equals(vm.get("enabled"));
                if (!disabled && e.getKey() != null) {
                    mask.add(String.valueOf(e.getKey()).trim().toUpperCase());
                }
            }
        }
        return mask;
    }

    /** 查询场景下所有心智（未删除，激活优先排序）。读源 = 认知域 V188 绑定（P0-2）。 */
    public List<ScenarioMindVO> listMinds(String scenarioId) {
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new BusinessException(400, "MIND-400: scenarioId 必填");
        }
        List<Map<String, Object>> rows = cognitionClient.listScenarioMinds(scenarioId.trim());
        List<ScenarioMindVO> result = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            result.add(toCognitiveVO(scenarioId.trim(), r));
        }
        return result;
    }

    /** 认知绑定行 → workspace VO（只映射事实；legacy Long id 为空，引用面走 mindRef）。 */
    private static ScenarioMindVO toCognitiveVO(String scenarioId, Map<String, Object> r) {
        ScenarioMindVO vo = new ScenarioMindVO();
        vo.setScenarioId(scenarioId);
        vo.setMindRef(str(r.get("mindId")));
        String name = str(r.get("name"));
        String key = str(r.get("mindKey"));
        vo.setMindLabel(name != null && !name.isBlank() ? name : key);
        vo.setActiveMind(Boolean.TRUE.equals(r.get("active")) ? 1 : 0);
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("capabilityMask", r.get("capabilityMask"));
        endpoints.put("closedLoopBounds", r.get("closedLoopBounds"));
        vo.setCognitiveEndpoints(endpoints);
        vo.setCreateTime(str(r.get("createTime")));
        vo.setUpdateTime(str(r.get("updateTime")));
        return vo;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
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
        throw new BusinessException(501, 501, STOPPED_WRITE_MSG);
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
        throw new BusinessException(501, 501, STOPPED_WRITE_MSG);
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
        for (ScenarioMindVO vo : listMinds(scenarioId)) {
            if (Integer.valueOf(1).equals(vo.getActiveMind())) {
                return vo;
            }
        }
        return null;
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
        List<ScenarioMindVO> minds = listMinds(scenarioId);
        for (ScenarioMindVO vo : minds) {
            String label = vo.getMindLabel();
            if (label != null && ("base".equalsIgnoreCase(label) || label.toLowerCase().endsWith("_base"))) {
                return vo;
            }
        }
        // 兼容口径：认知绑定无「base」标签概念时，退化为首个绑定（激活优先，
        // SQL 已 ORDER BY is_active DESC）——v1.x /mind-model 不断流，但不伪造第二条心智。
        return minds.isEmpty() ? null : minds.get(0);
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