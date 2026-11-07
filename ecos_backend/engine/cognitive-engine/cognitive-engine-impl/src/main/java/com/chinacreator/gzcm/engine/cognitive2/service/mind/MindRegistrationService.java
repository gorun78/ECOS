package com.chinacreator.gzcm.engine.cognitive2.service.mind;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.mind.ScenarioMindBindingVO;
import com.chinacreator.gzcm.engine.cognitive2.dao.MindAssetDao;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 场景 Mind 注册门面（B42/P1-19）：V187 `ecos_cognitive_mind` + V188
 * `ecos_cognitive_scenario_mind` 的<b>唯一写入口</b>。
 *
 * <p>背景：workspace legacy `ecos_scenario_mind` 写面按 F05-02/W116 停写，设计要求
 * 「场景↔Mind 绑定一律改经 cognitive 注册门面写认知域场景-Mind 绑定表（V188）」。本服务承接
 * 该门面：按 `mind_key` 幂等 upsert Mind、按 `(scenario_id, mind_id)` upsert 绑定，
 * 且只表达「能力配置」事实（capabilityMask/闭环边界），不含任何结论或金额。</p>
 *
 * <p>失败语义：缺必填 → 400 {@code ECOS-COG-400}；不吞异常（写失败原样上抛，事务回滚）。</p>
 */
@Service
public class MindRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(MindRegistrationService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEFAULT_VERSION = "1.0.0";

    private final MindAssetDao mindDao;

    public MindRegistrationService(MindAssetDao mindDao) {
        this.mindDao = mindDao;
    }

    /**
     * 注册/更新场景 Mind 并建立绑定。
     *
     * @param scenarioId     场景 id（必填）
     * @param mindKey        心智业务键（必填；同键幂等更新）
     * @param name           显示名（必填）
     * @param description    描述（可空）
     * @param capabilityMask 能力掩码（如 DIAGNOSE/FORECAST/SIMULATE/STRATEGY；可空 = 空数组）
     * @param closedLoopBounds 闭环边界（可空 = 空数组）
     * @param bindingRole    绑定角色（缺省 PRIMARY）
     * @param priority       优先级（缺省 100）
     * @param active         是否激活（缺省 true）
     * @param operator       操作者
     */
    @Transactional
    public Map<String, Object> register(String scenarioId, String mindKey, String name, String description,
                                        List<String> capabilityMask, List<String> closedLoopBounds,
                                        String bindingRole, Integer priority, Boolean active,
                                        String operator) {
        require(scenarioId, "scenarioId");
        require(mindKey, "mindKey");
        require(name, "name");
        String key = mindKey.trim();
        String op = operator == null || operator.isBlank() ? "system" : operator.trim();

        Map<String, Object> existing = mindDao.findMindByKey(key);
        String mindId = existing == null
                ? UUID.randomUUID().toString().replace("-", "")
                : String.valueOf(existing.get("id"));
        String version = existing == null || existing.get("mind_version") == null
                ? DEFAULT_VERSION : String.valueOf(existing.get("mind_version"));
        String domain = existing == null || existing.get("domain") == null
                ? "default" : String.valueOf(existing.get("domain"));
        int isActive = Boolean.FALSE.equals(active) ? 0 : 1;

        mindDao.upsertMind(mindId, key, name.trim(), description,
                toJson(capabilityMask), toJson(closedLoopBounds),
                "ACTIVE", isActive, version, domain, op);
        mindDao.upsertScenarioBinding(
                "sm_" + UUID.randomUUID().toString().replace("-", ""),
                scenarioId.trim(), mindId,
                bindingRole == null || bindingRole.isBlank() ? "PRIMARY" : bindingRole.trim(),
                priority == null ? 100 : priority,
                domain, op);

        log.info("Mind 注册: scenario={} mindKey={} mindId={} active={} by={}",
                scenarioId, key, mindId, isActive == 1, op);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mindId", mindId);
        out.put("mindKey", key);
        out.put("name", name.trim());
        out.put("scenarioId", scenarioId.trim());
        out.put("isActive", isActive == 1);
        out.put("mindVersion", version);
        return out;
    }

    /**
     * P0-2 读面：场景下全部绑定 Mind（含未激活；激活优先、绑定优先级次之）。
     * 无行 = 空表（不伪造）；通道故障原样上抛（不折叠成空）。
     */
    public List<ScenarioMindBindingVO> listForScenario(String scenarioId) {
        require(scenarioId, "scenarioId");
        List<Map<String, Object>> rows = mindDao.listMindsForScenario(scenarioId.trim());
        List<ScenarioMindBindingVO> out = new java.util.ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            out.add(new ScenarioMindBindingVO(
                    str(r.get("mind_id")),
                    str(r.get("mind_key")),
                    str(r.get("mind_name")),
                    str(r.get("description")),
                    decodeStringList(r.get("capability_mask_json")),
                    decodeStringList(r.get("closed_loop_bounds_json")),
                    toInt(r.get("is_active")) == 1,
                    str(r.get("binding_role")),
                    toInt(r.get("priority")),
                    str(r.get("create_time")),
                    str(r.get("update_time"))));
        }
        return out;
    }

    /** 解除场景↔Mind 绑定（逻辑删除）；未命中返回 0，不伪造成功。 */
    @Transactional
    public int unbind(String scenarioId, String mindId) {
        require(scenarioId, "scenarioId");
        require(mindId, "mindId");
        int rows = mindDao.softDeleteBinding(scenarioId.trim(), mindId.trim());
        log.info("Mind 解绑: scenario={} mindId={} rows={}", scenarioId, mindId, rows);
        return rows;
    }

    /** 切换激活位（B44/P1-20）；返回受影响行数（0 = 目标不存在）。 */
    @Transactional
    public int setActive(String mindId, boolean active, String operator) {
        require(mindId, "mindId");
        String op = operator == null || operator.isBlank() ? "system" : operator.trim();
        int rows = mindDao.setMindActive(mindId.trim(), active ? 1 : 0, op);
        log.info("Mind 激活位: mindId={} active={} rows={}", mindId, active, rows);
        return rows;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static int toInt(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }

    /** 读侧容错：非法 JSON 数组按空集返回并告警（形状事实不阻塞列表读面）。 */
    private static List<String> decodeStringList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        try {
            return JSON.readValue(String.valueOf(raw), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Mind JSON 数组解析失败，按空集返回: {}", raw);
            return List.of();
        }
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, field);
        }
    }

    private static String toJson(List<String> values) {
        try {
            return JSON.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception e) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "capabilityMask/closedLoopBounds");
        }
    }
}
