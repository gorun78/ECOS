package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * 业务场景服务 — 场景工作台（PMO-50）。
 *
 * <p>替代原内存 {@code ConcurrentHashMap} 实现，落 PG 表 {@code ecos_business_scenario} +
 * {@code ecos_scenario_binding}。遵循分层铁律：参数校验 / 空值判断 / 枚举约束 / 异常捕获均在 Service。</p>
 */
@Service
public class ScenarioService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioService.class);

    /** 允许的场景优先级枚举 */
    private static final Set<String> PRIORITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    /** 允许的场景状态枚举 */
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "COMPLETED", "SUSPENDED");
    /** 绑定类型 → 六类（key 为库内枚举，value 为 VO 侧 camelCase 分组） */
    private static final Map<String, String> BINDING_ENDPOINTS = new LinkedHashMap<>();

    static {
        BINDING_ENDPOINTS.put("DATASET", "datasets");
        BINDING_ENDPOINTS.put("OBJECT_TYPE", "objectTypes");
        BINDING_ENDPOINTS.put("KNOWLEDGE_BASE", "knowledgeBases");
        BINDING_ENDPOINTS.put("AI_AGENT", "aiAgents");
        BINDING_ENDPOINTS.put("SECURITY_POLICY", "securityPolicies");
        BINDING_ENDPOINTS.put("INTERFACE", "interfaces");
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ScenarioService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    // ═══════════════ CRUD ═══════════════

    /** 场景列表（未删除，按创建时间倒序）。 */
    public List<ScenarioVO> list() {
        List<BusinessScenario> rows = jdbc.query(
            "SELECT id, name, description, business_goal, department, priority, status, budget, " +
            "       safety_index_target, actual_safety_index, metrics, create_time, update_time, " +
            "       create_by, update_by, is_deleted " +
            "FROM ecos_business_scenario WHERE is_deleted = 0 ORDER BY create_time DESC",
            ROW_MAPPER);
        List<ScenarioVO> result = new ArrayList<>(rows.size());
        for (BusinessScenario e : rows) {
            result.add(ScenarioVO.fromEntity(e));
        }
        fillAggregates(result);
        return result;
    }

    /** 场景详情（含聚合绑定 + 指标）。 */
    public ScenarioVO get(String id) {
        List<BusinessScenario> rows = jdbc.query(
            "SELECT id, name, description, business_goal, department, priority, status, budget, " +
            "       safety_index_target, actual_safety_index, metrics, create_time, update_time, " +
            "       create_by, update_by, is_deleted " +
            "FROM ecos_business_scenario WHERE id = ? AND is_deleted = 0", ROW_MAPPER, id);
        if (rows.isEmpty()) {
            throw new NotFoundException("SCEN-404: 场景不存在: " + id);
        }
        ScenarioVO vo = ScenarioVO.fromEntity(rows.get(0));
        fillAggregates(List.of(vo));
        return vo;
    }

    /**
     * 创建场景（含绑定）。
     *
     * @param dto 保存入参（name 必填）
     * @return 创建后的 VO
     */
    @Transactional
    public ScenarioVO create(ScenarioSaveDTO dto) {
        validate(dto);
        String id = "sc_" + UUID.randomUUID().toString().substring(0, 12);
        String operator = currentOperator();
        String metricsJson = toMetricsJson(dto.getMetrics());

        jdbc.update(
            "INSERT INTO ecos_business_scenario " +
            "(id, name, description, business_goal, department, priority, status, budget, " +
            " safety_index_target, actual_safety_index, metrics, create_by, update_by, is_deleted) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, 0)",
            id, dto.getName(),
            dto.getDescription() == null ? "" : dto.getDescription(),
            dto.getBusinessGoal() == null ? dto.getDescription() : dto.getBusinessGoal(),
            dto.getDepartment(),
            normalizeEnum(dto.getPriority(), PRIORITIES, "MEDIUM", "优先级"),
            normalizeEnum(dto.getStatus(), STATUSES, "DRAFT", "状态"),
            dto.getBudget(),
            dto.getSafetyIndexTarget(),
            dto.getActualSafetyIndex(),
            metricsJson,
            operator, operator);

        if (dto.getBindings() != null) {
            saveBindings(id, dto.getBindings(), operator);
        }
        log.info("创建业务场景 id={} name={} by={}", id, dto.getName(), operator);
        return get(id);
    }

    /**
     * 更新场景。若 {@code bindings} 非 null 则以全量替换语义重写绑定（空列表=清空）。
     *
     * @param id  场景 id
     * @param dto 更新入参（null 字段视为不改动）
     * @return 更新后的 VO
     */
    @Transactional
    public ScenarioVO update(String id, ScenarioSaveDTO dto) {
        if (dto == null) {
            throw new BusinessException(400, "SCEN-400: 更新体不可为空");
        }
        if (dto.getName() != null && (dto.getName().isBlank())) {
            throw new BusinessException(400, "SCEN-400: 场景名称不能为空白");
        }
        if (!exists(id)) {
            throw new NotFoundException("SCEN-404: 场景不存在: " + id);
        }
        String operator = currentOperator();

        StringBuilder sql = new StringBuilder("UPDATE ecos_business_scenario SET update_time = NOW(), update_by = ?");
        List<Object> args = new ArrayList<>();
        args.add(operator);
        if (dto.getName() != null) {
            sql.append(", name = ?");
            args.add(dto.getName());
        }
        if (dto.getDescription() != null) {
            sql.append(", description = ?");
            args.add(dto.getDescription());
        }
        if (dto.getBusinessGoal() != null) {
            sql.append(", business_goal = ?");
            args.add(dto.getBusinessGoal());
        }
        if (dto.getDepartment() != null) {
            sql.append(", department = ?");
            args.add(dto.getDepartment());
        }
        if (dto.getPriority() != null) {
            sql.append(", priority = ?");
            args.add(normalizeEnum(dto.getPriority(), PRIORITIES, null, "优先级"));
        }
        if (dto.getStatus() != null) {
            sql.append(", status = ?");
            args.add(normalizeEnum(dto.getStatus(), STATUSES, null, "状态"));
        }
        if (dto.getBudget() != null) {
            sql.append(", budget = ?");
            args.add(dto.getBudget());
        }
        if (dto.getSafetyIndexTarget() != null) {
            sql.append(", safety_index_target = ?");
            args.add(dto.getSafetyIndexTarget());
        }
        if (dto.getActualSafetyIndex() != null) {
            sql.append(", actual_safety_index = ?");
            args.add(dto.getActualSafetyIndex());
        }
        if (dto.getMetrics() != null) {
            sql.append(", metrics = ?::jsonb");
            args.add(toMetricsJson(dto.getMetrics()));
        }
        sql.append(" WHERE id = ? AND is_deleted = 0");
        args.add(id);

        int updated = jdbc.update(sql.toString(), args.toArray());
        if (updated == 0) {
            throw new NotFoundException("SCEN-404: 场景不存在或已删除: " + id);
        }
        if (dto.getBindings() != null) {
            jdbc.update("UPDATE ecos_scenario_binding SET is_deleted = 1, update_time = NOW() WHERE scenario_id = ? AND is_deleted = 0", id);
            saveBindings(id, dto.getBindings(), operator);
        }
        log.info("更新业务场景 id={} by={}", id, operator);
        return get(id);
    }

    /** 逻辑删除场景 + 级联逻辑删除绑定。 */
    @Transactional
    public void delete(String id) {
        if (!exists(id)) {
            throw new NotFoundException("SCEN-404: 场景不存在: " + id);
        }
        String operator = currentOperator();
        jdbc.update("UPDATE ecos_business_scenario SET is_deleted = 1, update_time = NOW(), update_by = ? WHERE id = ?", operator, id);
        jdbc.update("UPDATE ecos_scenario_binding SET is_deleted = 1, update_time = NOW() WHERE scenario_id = ? AND is_deleted = 0", id);
        log.info("逻辑删除业务场景 id={} by={}", id, operator);
    }

    /** 场景绑定关系（六类分组，缺省空列表）。 */
    public Map<String, List<String>> bindings(String id) {
        if (!exists(id)) {
            throw new NotFoundException("SCEN-404: 场景不存在: " + id);
        }
        Map<String, List<String>> grouped = ScenarioVO.emptyBindings();
        List<ScenarioBinding> rows = jdbc.query(
            "SELECT id, scenario_id, binding_type, target_ref, remark, create_time " +
            "FROM ecos_scenario_binding WHERE scenario_id = ? AND is_deleted = 0 ORDER BY id", BINDING_MAPPER, id);
        for (ScenarioBinding b : rows) {
            String key = BINDING_ENDPOINTS.get(b.getBindingType());
            if (key != null) {
                grouped.get(key).add(b.getTargetRef());
            }
        }
        return grouped;
    }

    /**
     * 场景指标回写（PMO-52 供认知运行编排回写实际指标）。
     *
     * @param id     场景 id
     * @param actual 新的实际安全指标（0~1）
     * @param mergeMetrics 需合并进 metrics JSONB 的增量键值（null 表示不改动 metrics）
     * @return 是否更新成功
     */
    @Transactional
    public boolean updateMetrics(String id, BigDecimal actual, Map<String, Object> mergeMetrics) {
        if (!exists(id)) {
            throw new NotFoundException("SCEN-404: 场景不存在: " + id);
        }
        String operator = currentOperator();
        if (mergeMetrics == null || mergeMetrics.isEmpty()) {
            int n = jdbc.update(
                "UPDATE ecos_business_scenario SET actual_safety_index = ?, update_time = NOW(), update_by = ? WHERE id = ? AND is_deleted = 0",
                actual, operator, id);
            return n > 0;
        }
        // 合并 metrics：读旧 JSONB → 合并 → 写回
        String oldJson = jdbc.query(
            "SELECT metrics FROM ecos_business_scenario WHERE id = ? AND is_deleted = 0",
            (rs, rowNum) -> rs.getString("metrics"), id).stream().findFirst().orElse(null);
        Map<String, Object> merged = new LinkedHashMap<>();
        if (oldJson != null) {
            try {
                merged = objectMapper.readValue(oldJson, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ex) {
                log.warn("场景 metrics JSONB 解析失败，覆盖重建: id={}, err={}", id, ex.getMessage());
            }
        }
        merged.putAll(mergeMetrics);
        int n = jdbc.update(
            "UPDATE ecos_business_scenario SET actual_safety_index = ?, metrics = ?::jsonb, update_time = NOW(), update_by = ? WHERE id = ? AND is_deleted = 0",
            actual, toJson(merged), operator, id);
        return n > 0;
    }

    // ═══════════════ 私有辅助 ═══════════════

    private RowMapper<BusinessScenario> ROW_MAPPER = (rs, rowNum) -> {
        BusinessScenario e = new BusinessScenario();
        e.setId(rs.getString("id"));
        e.setName(rs.getString("name"));
        e.setDescription(rs.getString("description"));
        e.setBusinessGoal(rs.getString("business_goal"));
        e.setDepartment(rs.getString("department"));
        e.setPriority(rs.getString("priority"));
        e.setStatus(rs.getString("status"));
        e.setBudget(rs.getString("budget"));
        e.setSafetyIndexTarget(rs.getBigDecimal("safety_index_target"));
        e.setActualSafetyIndex(rs.getBigDecimal("actual_safety_index"));
        e.setMetricsJson(rs.getString("metrics"));
        e.setCreateTime(rs.getTimestamp("create_time") == null ? null : rs.getTimestamp("create_time").toLocalDateTime());
        e.setUpdateTime(rs.getTimestamp("update_time") == null ? null : rs.getTimestamp("update_time").toLocalDateTime());
        e.setCreateBy(rs.getString("create_by"));
        e.setUpdateBy(rs.getString("update_by"));
        e.setIsDeleted(rs.getInt("is_deleted"));
        return e;
    };

    private RowMapper<ScenarioBinding> BINDING_MAPPER = (rs, rowNum) -> {
        ScenarioBinding b = new ScenarioBinding();
        b.setId(rs.getString("id"));
        b.setScenarioId(rs.getString("scenario_id"));
        b.setBindingType(rs.getString("binding_type"));
        b.setTargetRef(rs.getString("target_ref"));
        b.setRemark(rs.getString("remark"));
        b.setCreateTime(rs.getTimestamp("create_time") == null ? null : rs.getTimestamp("create_time").toLocalDateTime());
        return b;
    };

    /** 填充 VO 的绑定 + 指标聚合。 */
    private void fillAggregates(List<ScenarioVO> vos) {
        if (vos.isEmpty()) {
            return;
        }
        for (ScenarioVO vo : vos) {
            String json = null;
            try {
                json = jdbc.query(
                    "SELECT metrics FROM ecos_business_scenario WHERE id = ?",
                    (rs, rowNum) -> rs.getString("metrics"), vo.getId()).stream().findFirst().orElse("{}");
            } catch (Exception ex) {
                log.debug("读取场景 metrics 失败: id={}, err={}", vo.getId(), ex.getMessage());
            }
            vo.setMetrics(parseMetrics(json));
            vo.setBindings(bindings(vo.getId()));
        }
    }

    /** 保存绑定列表（binding 项必填校验）。 */
    private void saveBindings(String scenarioId, List<ScenarioSaveDTO.ScenarioBindingItem> items, String operator) {
        if (items == null) {
            return;
        }
        for (ScenarioSaveDTO.ScenarioBindingItem item : items) {
            if (item == null || item.getBindingType() == null || item.getBindingType().isBlank()) {
                throw new BusinessException(400, "SCEN-400: 绑定缺少 bindingType");
            }
            if (!BINDING_ENDPOINTS.containsKey(item.getBindingType().toUpperCase())) {
                throw new BusinessException(400, "SCEN-400: 非法绑定类型: " + item.getBindingType());
            }
            if (item.getTargetRef() == null || item.getTargetRef().isBlank()) {
                throw new BusinessException(400, "SCEN-400: 绑定缺少 targetRef");
            }
            String bid = "sb_" + UUID.randomUUID().toString().substring(0, 12);
            jdbc.update(
                "INSERT INTO ecos_scenario_binding (id, scenario_id, binding_type, target_ref, remark, create_by, update_by, is_deleted) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 0)",
                bid, scenarioId, item.getBindingType().toUpperCase(), item.getTargetRef(),
                item.getRemark() == null ? "" : item.getRemark(), operator, operator);
        }
    }

    /** 场景是否存在（未删除）。 */
    private boolean exists(String id) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(1) FROM ecos_business_scenario WHERE id = ? AND is_deleted = 0", Integer.class, id);
        return n != null && n > 0;
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

    private void validate(ScenarioSaveDTO dto) {
        if (dto == null) {
            throw new BusinessException(400, "SCEN-400: 创建体不可为空");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException(400, "SCEN-400: 场景名称 name 必填");
        }
        if (dto.getName().length() > 255) {
            throw new BusinessException(400, "SCEN-400: 场景名称长度超限(≤255)");
        }
        if (dto.getPriority() != null && !PRIORITIES.contains(dto.getPriority().toUpperCase())) {
            throw new BusinessException(400, "SCEN-400: 非法优先级: " + dto.getPriority());
        }
        if (dto.getStatus() != null && !STATUSES.contains(dto.getStatus().toUpperCase())) {
            throw new BusinessException(400, "SCEN-400: 非法状态: " + dto.getStatus());
        }
    }

    /** 归一枚举（大小写）；defaultValue 仅创建语义下非 null 兜底，更新语义传 null 强制显式合法值。 */
    private String normalizeEnum(String value, Set<String> allowed, String defaultValue, String label) {
        String v = value == null ? defaultValue : value.toUpperCase();
        if (v == null) {
            throw new BusinessException(400, "SCEN-400: " + label + " 必填");
        }
        if (!allowed.contains(v)) {
            throw new BusinessException(400, "SCEN-400: 非法" + label + ": " + value);
        }
        return v;
    }

    private String toMetricsJson(Map<String, Object> metrics) {
        return metrics == null ? "{}" : toJson(metrics);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BusinessException(500, "SCEN-500: metrics JSON 序列化失败: " + e.getMessage());
        }
    }

    private Map<String, Object> parseMetrics(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("场景 metrics 解析失败，返回空: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }
}
