package com.chinacreator.gzcm.workspace.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import com.chinacreator.gzcm.workspace.scenario.BusinessScenario;
import com.chinacreator.gzcm.workspace.scenario.ScenarioBinding;
import com.chinacreator.gzcm.workspace.scenario.ScenarioBindingLinkService;
import com.chinacreator.gzcm.workspace.scenario.ScenarioMindService;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * B6a 数据访问收口（R2 架构铁律 §2.5 / IR06）：workspace-impl 各层
 * {@code JdbcTemplate} 内联 raw-SQL 的统一 DAO 承载点。
 *
 * <p>架构铁律 §2.5#1：Service / Controller / Aggregator / Runtime 层禁私持
 * {@code org.springframework.jdbc.core.JdbcTemplate}，raw-SQL 一律下沉
 * {@code ..dao..}。本 DAO 只搬不改：所有 SQL 文本与调用点原实现逐字节一致，
 * 参数传递方式、JSONB 强转（{@code ?::jsonb}）、RowMapper 语义均不变更。</p>
 *
 * <p>覆盖调用点（均为原 {@code JdbcTemplate} 直持类）：
 * ObjectActionService / ObjectStateMachineService / ObjectQueryService /
 * ObjectQLService / PgObjectStorageService / SqlRuntime(workbook) /
 * ScenarioService / ScenarioMindService / ScenarioBindingLinkService /
 * ScenarioRunService / ScenarioSandboxLayoutService /
 * KnowledgeStatsAggregator / KnowledgeHealthAggregator / KnowledgeConfigAggregator。</p>
 */
@Repository
public class WorkspaceDataDao extends JdbcTemplateDaoSupport {

    // ═══════════════ 泛化委托（原 Service 直接透传 JdbcTemplate 重载） ═══════════════

    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return jdbcTemplate.queryForList(sql, args);
    }

    public List<Map<String, Object>> queryForList(String sql) {
        return jdbcTemplate.queryForList(sql);
    }

    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
        return jdbcTemplate.queryForObject(sql, type, args);
    }

    public Map<String, Object> queryForMap(String sql, Object... args) {
        return jdbcTemplate.queryForMap(sql, args);
    }

    public int update(String sql, Object... args) {
        return jdbcTemplate.update(sql, args);
    }

    /**
     * 泛化 RowMapper 行级查询（多行 List 结果）。原
     * {@code jdbc.query(sql, (rs, rowNum) -> .., args...)} 形态的收口点。
     */
    public <T> List<T> queryList(String sql, RowMapper<T> rowMapper, Object... args) {
        return jdbcTemplate.query(sql, rowMapper, args);
    }

    /** 宽 Table 场景：无参 RowMapper 行级查询（ScenarioMindService 无参 mapper 调用）。 */
    public <T> List<T> queryList(String sql, RowMapper<T> rowMapper) {
        return jdbcTemplate.query(sql, rowMapper);
    }

    /**
     * 泛化行级消费（结果行回调 void 语义）。原
     * {@code jdbc.query(sql, rs -> ..)}（1 参 lambda，void 兼容 = {@link RowCallbackHandler}）
     * 形态的收口点；调用方 lambda 与原实现逐字节一致。
     */
    public void consumeRow(String sql, RowCallbackHandler handler) {
        jdbcTemplate.query(sql, handler);
    }

    // ═══════════════ object 域（ObjectAction / ObjectStateMachine / ObjectQuery / ObjectQL） ═══════════════

    /** 对象存在性校验（ObjectActionService.findObjectById）。 */
    public List<Map<String, Object>> findObjectById(String table, String id) {
        return jdbcTemplate.queryForList(
            "SELECT id FROM " + table + " WHERE id = ?", id);
    }

    /** 按 entity code 查可用 Action 列表（ObjectActionService.listAvailableActions）。 */
    public List<Map<String, Object>> listAvailableActions(String entityCode) {
        return jdbcTemplate.queryForList(
            """
            SELECT a.id, a.entity_id, a.name, a.action_type, a.strategy, a.status, a.created_at
            FROM ecos_ontology_action a
            JOIN ecos_ontology_entity e ON a.entity_id = e.id
            WHERE e.code = ? AND a.status = 'ACTIVE'
            ORDER BY a.created_at
            """, entityCode);
    }

    /** 按 action code 找 Action 定义（ObjectActionService.findActionByCode）。 */
    public List<Map<String, Object>> findActionByCode(String actionCode) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM ecos_ontology_action WHERE name = ? OR id = ? LIMIT 1",
            actionCode, actionCode);
    }

    /** demo 实体表状态查询（ObjectStateMachineService.getObjectStatus）。 */
    public List<Map<String, Object>> findStatusById(String table, String objectId) {
        return jdbcTemplate.queryForList(
            "SELECT status FROM " + table + " WHERE id = ?", objectId);
    }

    /** link 目标实体存在性计数（ObjectQLService.countEntityByCode）。 */
    public Integer countEntityByCode(String entityCode) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ecos_ontology_entity WHERE code = ?",
            Integer.class, entityCode);
    }

    // ═══════════════ A3 对象存储（PgObjectStorageService · ecos_files BYTEA） ═══════════════

    /** upsert 文件（PgObjectStorageService.putObject）。 */
    public int upsertFile(String id, byte[] data, String contentType) {
        String sql = "INSERT INTO ecos_files (id, content, content_type) VALUES (?, ?, ?) " +
                     "ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content, content_type = EXCLUDED.content_type";
        return jdbcTemplate.update(sql, id, data, contentType);
    }

    /** 读文件 BYTEA（PgObjectStorageService.getObject；无记录返回 null 由调用方 WARN）。 */
    public byte[] getFileContent(String key) {
        String sql = "SELECT content FROM ecos_files WHERE id = ?";
        return jdbcTemplate.query(sql,
                rs -> rs.next() ? rs.getBytes("content") : null, key);
    }

    /** 删文件（PgObjectStorageService.deleteObject）。返回命中行数。 */
    public int deleteFile(String key) {
        String sql = "DELETE FROM ecos_files WHERE id = ?";
        return jdbcTemplate.update(sql, key);
    }

    // ═══════════════ P2-16 SQL 运行时（workbook.SqlRuntime · 安全 SELECT 执行） ═══════════════

    /** 安全 SELECT 执行（SqlRuntime.execute；调用方已完成白名单/黑名单/多语句校验）。 */
    public List<Map<String, Object>> executeSelect(String sql) {
        return jdbcTemplate.queryForList(sql);
    }

    // ═══════════════ 场景工作台（ecos_business_scenario / ecos_scenario_binding） ═══════════════

    /** 场景行映射（与 ScenarioService 原 ROW_MAPPER 逐字段一致）。 */
    private final RowMapper<BusinessScenario> SCENARIO_ROW_MAPPER = (rs, rowNum) -> {
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

    /** 场景绑定行映射（与 ScenarioService 原 BINDING_MAPPER 逐字段一致）。 */
    private final RowMapper<ScenarioBinding> BINDING_ROW_MAPPER = (rs, rowNum) -> {
        ScenarioBinding b = new ScenarioBinding();
        b.setId(rs.getString("id"));
        b.setScenarioId(rs.getString("scenario_id"));
        b.setBindingType(rs.getString("binding_type"));
        b.setTargetRef(rs.getString("target_ref"));
        b.setTargetId(rs.getString("target_id"));
        b.setTargetType(rs.getString("target_type"));
        b.setRemark(rs.getString("remark"));
        b.setCreateTime(rs.getTimestamp("create_time") == null ? null : rs.getTimestamp("create_time").toLocalDateTime());
        return b;
    };

    /** 场景列表（未删除，按创建时间倒序）。 */
    public List<BusinessScenario> listScenarios() {
        return jdbcTemplate.query(
            "SELECT id, name, description, business_goal, department, priority, status, budget, " +
            "       safety_index_target, actual_safety_index, metrics, create_time, update_time, " +
            "       create_by, update_by, is_deleted " +
            "FROM ecos_business_scenario WHERE is_deleted = 0 ORDER BY create_time DESC",
            SCENARIO_ROW_MAPPER);
    }

    /** 场景详情（未删除）。 */
    public List<BusinessScenario> listScenarioById(String id) {
        return jdbcTemplate.query(
            "SELECT id, name, description, business_goal, department, priority, status, budget, " +
            "       safety_index_target, actual_safety_index, metrics, create_time, update_time, " +
            "       create_by, update_by, is_deleted " +
            "FROM ecos_business_scenario WHERE id = ? AND is_deleted = 0", SCENARIO_ROW_MAPPER, id);
    }

    /** 按名称查未删除场景的 id（创建幂等：同名已存在则复用，不追加新行）。 */
    public List<String> findScenarioIdsByName(String name) {
        return jdbcTemplate.queryForList(
            "SELECT id FROM ecos_business_scenario WHERE name = ? AND is_deleted = 0 "
            + "ORDER BY create_time ASC LIMIT 1", String.class, name);
    }

    /** 创建场景（metrics 走 JSONB 强转；budget 双写文本列与 MC01 数值列）。 */
    public int insertScenario(String id, String name, String description, String businessGoal,
                              String department, String priority, String status, String budget,
                              Object safetyIndexTarget, Object actualSafetyIndex, String metricsJson,
                              String createBy, String updateBy) {
        return jdbcTemplate.update(
            "INSERT INTO ecos_business_scenario " +
            "(id, name, description, business_goal, department, priority, status, budget, budget_amount, " +
            " safety_index_target, actual_safety_index, metrics, create_by, update_by, is_deleted) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, 0)",
            id, name, description, businessGoal, department, priority, status, budget,
            parseBudgetAmount(budget),
            safetyIndexTarget, actualSafetyIndex, metricsJson, createBy, updateBy);
    }

    /**
     * 把预算文本解析为 MC01 金额（元），规则与 V330 回填 SQL 一致：
     * 「850万」/「850万元」/「850 万」→ 8500000；纯数字（可带千分位）原样；空或不可解析 → null。
     *
     * <p>不可解析时返回 null 而不是抛异常：旧文本列仍照写，双写只是补齐权威列，
     * 不该因为一个自由文本字段把创建流程打断。解析失败的值在 budget_amount 留空，可被巡检发现。</p>
     */
    public static java.math.BigDecimal parseBudgetAmount(String budget) {
        if (budget == null) {
            return null;
        }
        String text = budget.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            java.util.regex.Matcher wan = WAN_AMOUNT.matcher(text);
            if (wan.matches()) {
                return new java.math.BigDecimal(wan.group(1))
                        .multiply(new java.math.BigDecimal("10000"))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
            }
            String plain = text.replace(",", "");
            if (PLAIN_AMOUNT.matcher(plain).matches()) {
                return new java.math.BigDecimal(plain).setScale(2, java.math.RoundingMode.HALF_UP);
            }
            // RT#14/V344（2026-11-17）：补中文数字百万级形态（"约五百万"/"三百万"等）。
            // 存量 33 行"约五百万"全因此前不认中文数字而漏解析，V344 已在库内回填；
            // 这里补齐解析口径，避免新写入继续漏。中文数字只覆盖一到九 + 百万量级，
            // 更复杂的（"一千二百万"等）仍返回 null，由巡检发现而非瞎猜。
            java.math.BigDecimal cn = parseChineseMillion(text);
            if (cn != null) {
                return cn;
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    private static final java.util.regex.Pattern WAN_AMOUNT =
            java.util.regex.Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)\\s*万(?:元)?$");
    private static final java.util.regex.Pattern PLAIN_AMOUNT =
            java.util.regex.Pattern.compile("^[0-9]+(?:\\.[0-9]+)?$");

    /** 中文数字 → 数值（一到九）。 */
    private static final java.util.Map<Character, Integer> CN_DIGIT = java.util.Map.of(
            '一', 1, '二', 2, '三', 3, '四', 4, '五', 5,
            '六', 6, '七', 7, '八', 8, '九', 9);

    /**
     * 解析「[约]&lt;中文数字&gt;百万」形态（RT#14/V344）。
     *
     * <p>只认单字中文数字 + "百万"，不解析"一千二百万"这类复合中文数词 —— 那需要完整
     * 中文数字文法，超出展示列回填的必要范围，遇到就返回 null 留给巡检发现。</p>
     */
    private static java.math.BigDecimal parseChineseMillion(String text) {
        String s = text.startsWith("约") ? text.substring(1) : text;
        if (!s.endsWith("百万") || s.length() != 3) {
            return null;
        }
        Integer d = CN_DIGIT.get(s.charAt(0));
        if (d == null) {
            return null;
        }
        return new java.math.BigDecimal(d).multiply(new java.math.BigDecimal("1000000"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** 动态更新场景（SQL 与参数由调用方按非 null 字段组装，此处仅透传执行）。 */
    public int updateScenarioDynamic(String sql, Object[] args) {
        return jdbcTemplate.update(sql, args);
    }

    /** 逻辑删除场景。 */
    public int logicDeleteScenario(String operator, String id) {
        return jdbcTemplate.update(
            "UPDATE ecos_business_scenario SET is_deleted = 1, update_time = NOW(), update_by = ? WHERE id = ?",
            operator, id);
    }

    /** 场景级联逻辑删除绑定。 */
    public int logicDeleteBindings(String scenarioId) {
        return jdbcTemplate.update(
            "UPDATE ecos_scenario_binding SET is_deleted = 1, update_time = NOW() WHERE scenario_id = ? AND is_deleted = 0",
            scenarioId);
    }

    /** 场景绑定列表（六类资源）。 */
    public List<ScenarioBinding> listBindingsByScenario(String scenarioId) {
        return jdbcTemplate.query(
            "SELECT id, scenario_id, binding_type, target_ref, target_id, target_type, remark, create_time " +
            "FROM ecos_scenario_binding WHERE scenario_id = ? AND is_deleted = 0 ORDER BY id",
            BINDING_ROW_MAPPER, scenarioId);
    }

    /** 只取场景 metrics JSONB 原文。 */
    public String selectScenarioMetrics(String id) {
        List<String> rows = jdbcTemplate.query(
            "SELECT metrics FROM ecos_business_scenario WHERE id = ?",
            (rs, rowNum) -> rs.getString("metrics"), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 回写场景实际安全指标（不动 metrics）。 */
    public int updateScenarioSafetyIndex(Object actual, String operator, String id) {
        return jdbcTemplate.update(
            "UPDATE ecos_business_scenario SET actual_safety_index = ?, update_time = NOW(), update_by = ? WHERE id = ? AND is_deleted = 0",
            actual, operator, id);
    }

    /** 回写场景实际安全指标 + metrics（metrics 走 JSONB 强转）。 */
    public int updateScenarioSafetyIndexAndMetrics(Object actual, String metricsJson, String operator, String id) {
        return jdbcTemplate.update(
            "UPDATE ecos_business_scenario SET actual_safety_index = ?, metrics = ?::jsonb, update_time = NOW(), update_by = ? WHERE id = ? AND is_deleted = 0",
            actual, metricsJson, operator, id);
    }

    /**
     * 回写场景当前已发布定义版本/摘要（V361 列；只存当前版本，历史走 Git）。
     */
    public int updateScenarioDefinitionPointer(String definitionVersion, String definitionDigest,
                                               String operator, String id) {
        return jdbcTemplate.update(
            "UPDATE ecos_business_scenario SET definition_version = ?, definition_digest = ?, " +
            "update_time = NOW(), update_by = ? WHERE id = ? AND is_deleted = 0",
            definitionVersion, definitionDigest, operator, id);
    }

    /** 运行锚点行插入（V361：定义版本/摘要/分析类型/来源）。 */
    public int insertAnalysisAnchor(String runId, String scenarioId, String analysisType, String status,
                                    String metric, Object deviation, boolean degraded, String operator,
                                    String runMode, String sourceRunId, String definitionVersion,
                                    String definitionDigest, String sourceType, String sourceId) {
        return jdbcTemplate.update(
            "INSERT INTO ecos_scenario_run " +
            "(id, scenario_id, run_type, status, metric, deviation, degraded, " +
            " run_mode, source_run_id, sandbox_lineage, create_by, update_by, " +
            " definition_version, definition_digest, analysis_type, source_type, source_id) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'NONE', ?, ?, ?, ?, ?, ?, ?)",
            runId, scenarioId, analysisType, status, metric, deviation, degraded,
            runMode, sourceRunId, operator, operator,
            definitionVersion, definitionDigest, analysisType, sourceType, sourceId);
    }

    /** 锚点状态回写（预测远端结果回来后）。 */
    public int updateAnalysisAnchorStatus(String runId, String status, String updateBy) {
        return jdbcTemplate.update(
            "UPDATE ecos_scenario_run SET status = ?, update_time = NOW(), update_by = ? " +
            "WHERE id = ? AND is_deleted = 0",
            status, updateBy, runId);
    }

    /**
     * 既有运行行补锚点列（因果/仿真由 ScenarioRunService 先写行，本方法只补元数据，
     * 不新增行、不改结果列）。
     */
    public int updateAnalysisAnchorMeta(String runId, String analysisType, String sourceType,
                                        String sourceId, String definitionVersion, String definitionDigest) {
        return jdbcTemplate.update(
            "UPDATE ecos_scenario_run SET analysis_type = ?, source_type = ?, source_id = ?, " +
            "definition_version = ?, definition_digest = ?, update_time = NOW() " +
            "WHERE id = ? AND is_deleted = 0",
            analysisType, sourceType, sourceId, definitionVersion, definitionDigest, runId);
    }

    /** 定义摘要读取（运行引用；无当前版本返回 null，禁伪造）。 */
    public List<Map<String, Object>> findDefinitionPointer(String scenarioId) {
        return jdbcTemplate.queryForList(
            "SELECT definition_version, definition_digest FROM ecos_business_scenario " +
            "WHERE id = ? AND is_deleted = 0",
            scenarioId);
    }

    /** 按锚点 id 读运行（含来源）。 */
    public List<Map<String, Object>> findAnalysisById(String runId) {
        return jdbcTemplate.queryForList(
            "SELECT id, scenario_id, run_type, analysis_type, status, metric, deviation, degraded, " +
            "       run_mode, source_run_id, source_type, source_id, definition_version, definition_digest, " +
            "       diagnosis_result, forecast_result, simulation_result, strategy_result, create_time " +
            "FROM ecos_scenario_run WHERE id = ? AND is_deleted = 0",
            runId);
    }

    /** 场景分析列表（锚点表统一读，倒序）。 */
    public List<Map<String, Object>> listAnalyses(String scenarioId, int limit) {
        return jdbcTemplate.queryForList(
            "SELECT id, scenario_id, run_type, analysis_type, status, metric, deviation, degraded, " +
            "       run_mode, source_run_id, source_type, source_id, definition_version, definition_digest, " +
            "       diagnosis_result, forecast_result, simulation_result, strategy_result, create_time " +
            "FROM ecos_scenario_run WHERE scenario_id = ? AND is_deleted = 0 " +
            "ORDER BY create_time DESC LIMIT ?",
            scenarioId, limit);
    }

    // ═══════════════ 场景配置（V361：模板/算法库，供 ScenarioConfigService） ═══════════════

    public int insertScenarioTemplate(String id, String name, String category, String description,
                                      String defaults, String analyses, String status, String operator) {
        return jdbcTemplate.update(
            "INSERT INTO ecos_scenario_template (id, name, category, description, definition_defaults, default_analysis, status, create_by, update_by) " +
            "VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)",
            id, name, category, description, defaults, analyses, status, operator, operator);
    }

    public int updateScenarioTemplate(String id, String name, String category, String description,
                                      String defaults, String analyses, String status, String operator) {
        return jdbcTemplate.update(
            "UPDATE ecos_scenario_template SET name = ?, category = ?, description = ?, " +
            "definition_defaults = ?::jsonb, default_analysis = ?::jsonb, status = ?, update_by = ?, update_time = NOW() " +
            "WHERE id = ? AND is_deleted = 0",
            name, category, description, defaults, analyses, status, operator, id);
    }

    public int deleteScenarioTemplate(String id, String operator) {
        return jdbcTemplate.update(
            "UPDATE ecos_scenario_template SET is_deleted = 1, update_by = ?, update_time = NOW() WHERE id = ? AND is_deleted = 0",
            operator, id);
    }

    public int insertScenarioAlgorithm(String id, String name, String analysisType, String version,
                                       String providerType, String implRef, String params, String output,
                                       String status, String operator) {
        return jdbcTemplate.update(
            "INSERT INTO ecos_algorithm_registry (id, name, analysis_type, version, provider_type, implementation_ref, params_schema, output_schema, status, create_by, update_by) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)",
            id, name, analysisType, version, providerType, implRef, params, output, status, operator, operator);
    }

    public int updateScenarioAlgorithm(String id, String name, String analysisType, String version,
                                       String providerType, String implRef, String params, String output,
                                       String status, String operator) {
        return jdbcTemplate.update(
            "UPDATE ecos_algorithm_registry SET name = ?, analysis_type = ?, version = ?, provider_type = ?, " +
            "implementation_ref = ?, params_schema = ?::jsonb, output_schema = ?::jsonb, status = ?, update_by = ?, update_time = NOW() " +
            "WHERE id = ? AND is_deleted = 0",
            name, analysisType, version, providerType, implRef, params, output, status, operator, id);
    }

    public int deleteScenarioAlgorithm(String id, String operator) {
        return jdbcTemplate.update(
            "UPDATE ecos_algorithm_registry SET is_deleted = 1, update_by = ?, update_time = NOW() WHERE id = ? AND is_deleted = 0",
            operator, id);
    }

    /** 新建一条场景绑定。 */
    public int insertBinding(String bid, String scenarioId, String bindingType, String targetRef,
                             String targetId, String targetType, String remark, String createBy, String updateBy) {
        return jdbcTemplate.update(
            "INSERT INTO ecos_scenario_binding (id, scenario_id, binding_type, target_ref, target_id, target_type, remark, create_by, update_by, is_deleted) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
            bid, scenarioId, bindingType, targetRef, targetId, targetType, remark, createBy, updateBy);
    }

    /** 场景是否存在（未删除）计数。 */
    public Integer countScenarioById(String id) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM ecos_business_scenario WHERE id = ? AND is_deleted = 0", Integer.class, id);
    }

    // ═══════════════ 场景心智（ecos_scenario_mind — 只读；写面已按设计-05 F05-02/W116 停写删除） ═══════════════

    /** 心智行映射 — 对应 ScenarioMindService 内部 MIND_MAPPER；行对象由
     * {@link ScenarioMindService} 的私有 PO 承载，此处仅以原始列序列暴露，
     * 由调用方 {@code ScenarioMindService} 构造 PO（保持 PO 私有可见性不变）。 */
    private final RowMapper<ScenarioMindService.ScenarioMindRawRef> MIND_ROW_MAPPER = (rs, rowNum) -> {
        ScenarioMindService.ScenarioMindRawRef r = new ScenarioMindService.ScenarioMindRawRef();
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

    public List<ScenarioMindService.ScenarioMindRawRef> listMindsOfScenario(String scenarioId) {
        return jdbcTemplate.query(
            "SELECT id, scenario_id, mind_label, active_mind, initial_belief_jsonb, " +
            "evidence_refs, hypothesis_refs, model_refs, cognitive_endpoints, initial_confidence, " +
            "create_time, update_time " +
            "FROM ecos_scenario_mind WHERE scenario_id=? AND is_deleted=0 " +
            "ORDER BY (active_mind=1) DESC, mind_label", MIND_ROW_MAPPER, scenarioId);
    }

    public List<ScenarioMindService.ScenarioMindRawRef> listActiveMind(String scenarioId) {
        return jdbcTemplate.query(
            "SELECT id, scenario_id, mind_label, active_mind, initial_belief_jsonb, " +
            "evidence_refs, hypothesis_refs, model_refs, cognitive_endpoints, initial_confidence, " +
            "create_time, update_time " +
            "FROM ecos_scenario_mind WHERE scenario_id=? AND active_mind=1 AND is_deleted=0",
            MIND_ROW_MAPPER, scenarioId);
    }

    public List<ScenarioMindService.ScenarioMindRawRef> listBaseMind(String scenarioId) {
        return jdbcTemplate.query(
            "SELECT id, scenario_id, mind_label, active_mind, initial_belief_jsonb, " +
            "evidence_refs, hypothesis_refs, model_refs, cognitive_endpoints, initial_confidence, " +
            "create_time, update_time " +
            "FROM ecos_scenario_mind WHERE scenario_id=? AND mind_label='base' AND is_deleted=0",
            MIND_ROW_MAPPER, scenarioId);
    }

    public List<ScenarioMindService.ScenarioMindRawRef> listMindById(Long mindId) {
        return jdbcTemplate.query(
            "SELECT id, scenario_id, mind_label, active_mind, initial_belief_jsonb, " +
            "evidence_refs, hypothesis_refs, model_refs, cognitive_endpoints, initial_confidence, " +
            "create_time, update_time " +
            "FROM ecos_scenario_mind WHERE id=? AND is_deleted=0", MIND_ROW_MAPPER, mindId);
    }

    // ═══════════════ 场景边（ecos_scenario_binding_link） ═══════════════

    /** 边行映射（与 ScenarioBindingLinkService 原 inline lambda 字段一致）。 */
    private final RowMapper<ScenarioBindingLinkService.ScenarioBindingLinkVO> LINK_ROW_MAPPER = (rs, rowNum) -> new ScenarioBindingLinkService.ScenarioBindingLinkVO(
        rs.getString("id"), rs.getString("scenario_id"),
        rs.getString("source_binding_id"), rs.getString("target_binding_id"),
        rs.getString("link_type"), rs.getString("source_contract"),
        rs.getString("remark"), rs.getString("version_no"));

    public List<ScenarioBindingLinkService.ScenarioBindingLinkVO> listLinksOfScenario(String scenarioId) {
        return jdbcTemplate.query(
            "SELECT id, scenario_id, source_binding_id, target_binding_id, link_type, " +
            "       source_contract, remark, version_no " +
            "FROM ecos_scenario_binding_link " +
            "WHERE scenario_id = ? AND is_deleted = 0 ORDER BY id",
            LINK_ROW_MAPPER, scenarioId);
    }

    public List<ScenarioBindingLinkService.ScenarioBindingLinkVO> listLinkByIdAndScenario(String linkId, String scenarioId) {
        return jdbcTemplate.query(
            "SELECT id, scenario_id, source_binding_id, target_binding_id, link_type, " +
            "       source_contract, remark, version_no " +
            "FROM ecos_scenario_binding_link WHERE id = ? AND scenario_id = ? AND is_deleted = 0",
            LINK_ROW_MAPPER, linkId, scenarioId);
    }

    /** 场景绑定（图构建用；不动 is_deleted 之外的列）。 */
    public List<Map<String, Object>> listBindingRowsForGraph(String scenarioId) {
        return jdbcTemplate.queryForList(
            "SELECT id, binding_type, target_ref, target_id, target_type " +
            "FROM ecos_scenario_binding WHERE scenario_id = ? AND is_deleted = 0 ORDER BY id",
            scenarioId);
    }

    public Integer countLinksOfScenario(String scenarioId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ecos_scenario_binding_link WHERE scenario_id = ? AND is_deleted = 0",
            Integer.class, scenarioId);
    }

    public Integer countDuplicateLink(String scenarioId, String source, String target, String linkType) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ecos_scenario_binding_link " +
            "WHERE scenario_id = ? AND source_binding_id = ? AND target_binding_id = ? " +
            "AND link_type = ? AND is_deleted = 0",
            Integer.class, scenarioId, source, target, linkType);
    }

    public int insertLink(String id, String scenarioId, String source, String target, String linkType,
                          String sourceContract, String remark, String operator) {
        return jdbcTemplate.update(
            "INSERT INTO ecos_scenario_binding_link " +
            "(id, scenario_id, source_binding_id, target_binding_id, link_type, source_contract, " +
            " remark, version_no, create_by, update_by, is_deleted, domain) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, '1', ?, ?, 0, 'default')",
            id, scenarioId, source, target, linkType, sourceContract, remark, operator, operator);
    }

    public int logicDeleteLink(String operator, String linkId, String scenarioId) {
        return jdbcTemplate.update(
            "UPDATE ecos_scenario_binding_link " +
            "SET is_deleted = 1, update_time = NOW(), update_by = ? " +
            "WHERE id = ? AND scenario_id = ? AND is_deleted = 0",
            operator, linkId, scenarioId);
    }

    public Integer countScenarioForLink(String scenarioId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ecos_business_scenario WHERE id = ? AND is_deleted = 0",
            Integer.class, scenarioId);
    }

    public Integer countBindingInScenario(String bindingId, String scenarioId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ecos_scenario_binding " +
            "WHERE id = ? AND scenario_id = ? AND is_deleted = 0",
            Integer.class, bindingId, scenarioId);
    }

    public List<Map<String, Object>> listBindingTypesOfScenario(String scenarioId) {
        return jdbcTemplate.queryForList(
            "SELECT binding_type FROM ecos_scenario_binding WHERE scenario_id = ? AND is_deleted = 0",
            scenarioId);
    }

    public Integer countMappingLinksOfScenario(String scenarioId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ecos_scenario_binding_link " +
            "WHERE scenario_id = ? AND link_type = 'MAPPING' AND is_deleted = 0",
            Integer.class, scenarioId);
    }

    // ═══════════════ 场景运行（ecos_scenario_run） ═══════════════

    /** 旧签名（FORMAL 缺省）保留，避免破坏既有调用方；新写入走带 lineage 的重载。 */
    public int insertRun(String runId, String scenarioId, String runType, String status, String metric,
                         Object deviation, String diagnosisJson, String forecastJson,
                         String simulationJson, String strategyJson, boolean degraded, String operator) {
        return insertRun(runId, scenarioId, runType, status, metric, deviation,
                diagnosisJson, forecastJson, simulationJson, strategyJson, degraded, operator,
                "FORMAL", null, "NONE");
    }

    public int insertRun(String runId, String scenarioId, String runType, String status, String metric,
                         Object deviation, String diagnosisJson, String forecastJson,
                         String simulationJson, String strategyJson, boolean degraded, String operator,
                         String runMode, String sourceRunId, String sandboxLineage) {
        return jdbcTemplate.update(
            "INSERT INTO ecos_scenario_run " +
            "(id, scenario_id, run_type, status, metric, deviation, " +
            " diagnosis_result, forecast_result, simulation_result, strategy_result, degraded, " +
            " run_mode, source_run_id, sandbox_lineage, create_by, update_by) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?)",
            runId, scenarioId, runType, status, metric, deviation,
            diagnosisJson, forecastJson, simulationJson, strategyJson, degraded,
            runMode, sourceRunId, sandboxLineage, operator, operator);
    }

    public List<Map<String, Object>> listRunHistory(String scenarioId, int limit) {
        return jdbcTemplate.queryForList(
            "SELECT id, scenario_id, run_type, status, metric, deviation, degraded, " +
            "       run_mode, source_run_id, sandbox_lineage, " +
            "       diagnosis_result, forecast_result, simulation_result, strategy_result, create_time " +
            "FROM ecos_scenario_run WHERE scenario_id = ? AND is_deleted = 0 ORDER BY create_time DESC LIMIT ?",
            scenarioId, limit);
    }

    /** 运行模式解析（沙盘基线守卫唯一 IO 点）；无记录返回 null（诚实缺省，不伪造 FORMAL）。 */
    public String findRunMode(String runId) {
        List<String> rows = jdbcTemplate.queryForList(
            "SELECT run_mode FROM ecos_scenario_run WHERE id = ? AND is_deleted = 0",
            String.class, runId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ═══════════════ 沙盘布局（ecos_scenario_sandbox_layout） ═══════════════

    public List<Map<String, Object>> layoutRows(String scenarioId) {
        return jdbcTemplate.queryForList(
            "SELECT scenario_id, layout_jsonb, layout_version, update_time " +
            "FROM ecos_scenario_sandbox_layout WHERE scenario_id=? AND is_deleted=0", scenarioId);
    }

    public int updateLayoutVersion(String layoutJson, String operator, String scenarioId, int expected) {
        return jdbcTemplate.update(
            "UPDATE ecos_scenario_sandbox_layout SET layout_jsonb=?::jsonb, " +
            "layout_version=layout_version+1, update_time=NOW(), update_by=? " +
            "WHERE scenario_id=? AND is_deleted=0 AND layout_version=?",
            layoutJson, operator, scenarioId, expected);
    }

    public Integer layoutRowExists(String scenarioId) {
        List<Integer> rows = jdbcTemplate.queryForList(
            "SELECT 1 FROM ecos_scenario_sandbox_layout WHERE scenario_id=? AND is_deleted=0",
            Integer.class, scenarioId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Integer currentLayoutVersion(String scenarioId) {
        List<Integer> rows = jdbcTemplate.queryForList(
            "SELECT layout_version FROM ecos_scenario_sandbox_layout WHERE scenario_id=? AND is_deleted=0",
            Integer.class, scenarioId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int insertLayout(String scenarioId, String layoutJson, String operator) {
        return jdbcTemplate.update(
            "INSERT INTO ecos_scenario_sandbox_layout (scenario_id, layout_jsonb, layout_version, create_by, update_by, is_deleted) " +
            "VALUES (?, ?::jsonb, 1, ?, ?, 0)",
            scenarioId, layoutJson, operator, operator);
    }

    // ═══════════════ 知识工作台（知识资产计数 / 健康探活 / 引擎配置 · sys_config） ═══════════════

    /** 知识域只读 COUNT（StatsAggregator.count；表未建时抛异常由调用方兜底 0）。 */
    public Long knowledgeCount(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    /** 向量维数（pgvector 未装 / 无行时抛异常由调用方兜底 null）。 */
    public Integer vectorDims() {
        return jdbcTemplate.queryForObject(
                "SELECT vector_dims(MAX(embedding_vec)) FROM ecos_knowledge.knowledge_embedding", Integer.class);
    }

    /** pgdb 探活。 */
    public Long probePgDb() {
        return jdbcTemplate.queryForObject("SELECT 1", Long.class);
    }

    /** pgvector 扩展是否存在。 */
    public Boolean probePgVectorExtension() {
        return jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'vector')",
                Boolean.class);
    }

    /** 本地 sys_config 读取（ConfigAggregator.fetchLocal 兜底）。 */
    public List<Map<String, Object>> listSysConfigByGroup(String group) {
        return jdbcTemplate.queryForList(
                "SELECT config_key, config_value FROM sys_config WHERE config_group = ? ORDER BY config_key",
                group);
    }
}
