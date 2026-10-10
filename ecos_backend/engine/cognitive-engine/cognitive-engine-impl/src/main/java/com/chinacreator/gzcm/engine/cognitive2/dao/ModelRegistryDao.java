package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * B2-b 数据访问收口（架构铁律 §2.5 #1 / IR06）：{@code ModelRegistryService}（PMO-51 T3 /
 * ADR-8）内联的生态认知模型注册 SQL 落点。
 *
 * <p><b>REQ-COG-07 收口（本批 #86，V261 头注"应用侧待办"逐字执行）</b>——相对"只搬不改"的两处
 * <em>任务点名必改</em>已在 V261 登记：
 * <ol>
 *   <li>表名由未限定 {@code public.ecos_cognitive_model}（旧双写形态）收口为控制域权威载体
 *       {@code ecos_cognitive.ecos_cognitive_model}（ST07 / R-13 ① / REQ-COG-07 规格 5；
 *       public 旧表只停写不加列，IR03）；</li>
 *   <li>列名 {@code features}（旧 JSONB）→ {@code features_json}（V192 起 TEXT 形态，MC02），
 *       随之移除旧 {@code ?::jsonb} 强转（MC02/MC03；{@code CognitiveSqlDialectTest} 门禁面）。</li>
 * </ol>
 * 读路径以 {@code features_json AS features} 别名保持既有响应列名不变（API 只增不改）；
 * 其余 SQL 文本 / 字段序 / 参数序与原 Service 逐字节一致。</p>
 *
 * <p>新列（V261：{@code cognitive_model_type}/{@code registry_state}/{@code content_hash}/
 * {@code supersedes_id}/四门禁列/{@code published_at}/{@code published_by}）全部在此收口；
 * 枚举字面量的单源在 cognitive-engine-api（{@code CognitiveModelType}/{@code RegistryState}/
 * {@code GateOutcome}），本 DAO 只做列读写不声明枚举。版本不可变守卫 = 所有 DRAFT 面 UPDATE 语句
 * 带 {@code registry_state <> 'PUBLISHED'} 条件，Service 以影响行数判 409（禁静默）。</p>
 */
@Repository
public class ModelRegistryDao extends JdbcTemplateDaoSupport {

    /** 读列面：新列并入（注册中心视图），旧列名以别名保持既有响应形态。 */
    private static final String READ_COLUMNS =
            "id, model_id, model_type, version, features_json AS features, backtest_score, status, " +
            "create_time, update_time, create_by, update_by, " +
            "cognitive_model_type, registry_state, content_hash, supersedes_id, " +
            "gate_contract, gate_regression, gate_backtest, gate_calibration, published_at, published_by";

    /** 模型列表（按 modelType 可选过滤，未删除，按 model_id/version 排序）。 */
    public List<Map<String, Object>> listModels(String modelType) {
        StringBuilder sql = new StringBuilder(
            "SELECT " + READ_COLUMNS + " FROM ecos_cognitive.ecos_cognitive_model WHERE is_deleted = 0");
        List<Object> args = new ArrayList<>();
        if (modelType != null && !modelType.isBlank()) {
            sql.append(" AND model_type = ?");
            args.add(modelType.toUpperCase());
        }
        sql.append(" ORDER BY model_id, version DESC");
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    /** 某 model_id 的全部版本（未删除，version 倒序）。 */
    public List<Map<String, Object>> getModel(String modelId) {
        return jdbcTemplate.queryForList(
            "SELECT " + READ_COLUMNS + " " +
            "FROM ecos_cognitive.ecos_cognitive_model WHERE model_id = ? AND is_deleted = 0 ORDER BY version DESC",
            modelId);
    }

    /** 同 model_id 当前最大 version（无记录返回 0）。 */
    public int maxVersion(String modelId) {
        Integer maxVer = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(version),0) FROM ecos_cognitive.ecos_cognitive_model WHERE model_id = ?",
            Integer.class, modelId);
        return maxVer == null ? 0 : maxVer;
    }

    /** 同 model_id 当前（version 最大且未删除）版本行的行 id — supersedes 链指旧版用；无记录返回 null。 */
    public String latestRowId(String modelId) {
        List<String> ids = jdbcTemplate.queryForList(
            "SELECT id FROM ecos_cognitive.ecos_cognitive_model " +
            "WHERE model_id = ? AND is_deleted = 0 ORDER BY version DESC LIMIT 1",
            String.class, modelId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** 单行读取（未删除；注册中心端点以行 id 定位版本行）。 */
    public Map<String, Object> selectRowById(String id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT " + READ_COLUMNS + " FROM ecos_cognitive.ecos_cognitive_model " +
            "WHERE id = ? AND is_deleted = 0",
            id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 新增一个模型 version（features_json 为 TEXT 列，MC02 禁 jsonb ⇒ 无强转；
     * registry_state 依 V261 默认 'DRAFT'，四门禁列默认 'PENDING'，发布面禁在此越权写入）。
     */
    public void insertModel(String id, String modelId, String modelType, int version,
                            String featuresJson, BigDecimal backtest, String status,
                            String cognitiveModelType, String supersedesId) {
        jdbcTemplate.update(
            "INSERT INTO ecos_cognitive.ecos_cognitive_model " +
            "(id, model_id, model_type, version, features_json, backtest_score, status, create_by, update_by, is_deleted, " +
            " cognitive_model_type, supersedes_id) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, 'system', 'system', 0, ?, ?)",
            id, modelId, modelType, version, featuresJson, backtest, status, cognitiveModelType, supersedesId);
    }

    /**
     * DRAFT 行冻结内容哈希（{@code registry_state <> 'PUBLISHED'} 守卫 ⇒ 已发布行必 0 行受影响，
     * Service 判 409）。
     */
    public int updateContentHashIfDraft(String id, String contentHash) {
        return jdbcTemplate.update(
            "UPDATE ecos_cognitive.ecos_cognitive_model SET content_hash = ?, update_time = CURRENT_TIMESTAMP " +
            "WHERE id = ? AND is_deleted = 0 AND registry_state <> 'PUBLISHED'",
            contentHash, id);
    }

    /** DRAFT 行写四门禁裁决（同 immutable 守卫；列值 PASS/FAIL/PENDING 由 GateOutcome 单源产生）。 */
    public int updateGatesIfDraft(String id, String gateContract, String gateRegression,
                                  String gateBacktest, String gateCalibration) {
        return jdbcTemplate.update(
            "UPDATE ecos_cognitive.ecos_cognitive_model " +
            "SET gate_contract = ?, gate_regression = ?, gate_backtest = ?, gate_calibration = ?, " +
            "    update_time = CURRENT_TIMESTAMP " +
            "WHERE id = ? AND is_deleted = 0 AND registry_state <> 'PUBLISHED'",
            gateContract, gateRegression, gateBacktest, gateCalibration, id);
    }

    /**
     * DRAFT → PUBLISHED 升格（SQL 层二次防线：哈希已冻结 + 四门禁全 PASS 才允许落状态，
     * 防 Service 判据与库内留痕漂移；0 行受影响 ⇒ Service 显式 4xx，禁静默成功）。
     */
    public int promoteToPublishedIfReady(String id, String publishedBy) {
        return jdbcTemplate.update(
            "UPDATE ecos_cognitive.ecos_cognitive_model " +
            "SET registry_state = 'PUBLISHED', published_at = CURRENT_TIMESTAMP, published_by = ?, " +
            "    update_time = CURRENT_TIMESTAMP " +
            "WHERE id = ? AND is_deleted = 0 AND registry_state = 'DRAFT' " +
            "  AND content_hash IS NOT NULL AND content_hash <> '' " +
            "  AND gate_contract = 'PASS' AND gate_regression = 'PASS' " +
            "  AND gate_backtest = 'PASS' AND gate_calibration = 'PASS'",
            publishedBy, id);
    }

    // ── 《认知域与智能域补充详细设计 v1.0》§8 增量（只增不改上方既有方法；V268 列位）──

    /** 生命周期读投影（resolve/状态机用；V268 lifecycle_state + legacy status/registry_state）。 */
    public Map<String, Object> findLifecycleStateById(String id) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT id, model_id, model_type, version, status, registry_state, lifecycle_state " +
                "FROM ecos_cognitive.ecos_cognitive_model WHERE id = ? AND is_deleted = 0", id);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** 治理态落库（V268 lifecycle_state 唯一权威列；0 行受影响 ⇒ 调用方 fail-loud）。 */
    public int updateLifecycleState(String id, String lifecycleState, String operator) {
        return jdbcTemplate.update(
            "UPDATE ecos_cognitive.ecos_cognitive_model " +
            "SET lifecycle_state = ?, update_by = ?, update_time = CURRENT_TIMESTAMP " +
            "WHERE id = ? AND is_deleted = 0",
            lifecycleState, operator, id);
    }
}
