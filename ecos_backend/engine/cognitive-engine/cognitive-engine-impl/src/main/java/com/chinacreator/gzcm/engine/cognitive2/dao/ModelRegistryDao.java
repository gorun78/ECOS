package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * B2-b 数据访问收口（架构铁律 §2.5 #1 / IR06）：{@code ModelRegistryService}（PMO-51 T3 /
 * ADR-8）内联的 {@code ecos_cognitive_model} SQL 落点。
 *
 * <p>只搬不改：SQL 文本 / 表名 / 字段 / 参数序与原 Service 逐字节一致；
 * 入参白名单校验与版本推导的决策语义留在 Service 层。</p>
 */
@Repository
public class ModelRegistryDao extends JdbcTemplateDaoSupport {

    /** 模型列表（按 modelType 可选过滤，未删除，按 model_id/version 排序）。 */
    public List<Map<String, Object>> listModels(String modelType) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, model_id, model_type, version, features, backtest_score, status, " +
            "       create_time, update_time, create_by, update_by FROM ecos_cognitive_model WHERE is_deleted = 0");
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
            "SELECT id, model_id, model_type, version, features, backtest_score, status, " +
            "       create_time, update_time, create_by, update_by " +
            "FROM ecos_cognitive_model WHERE model_id = ? AND is_deleted = 0 ORDER BY version DESC",
            modelId);
    }

    /** 同 model_id 当前最大 version（无记录返回 0）。 */
    public int maxVersion(String modelId) {
        Integer maxVer = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(version),0) FROM ecos_cognitive_model WHERE model_id = ?",
            Integer.class, modelId);
        return maxVer == null ? 0 : maxVer;
    }

    /** 新增一个模型 version（JSONB features 强转）。 */
    public void insertModel(String id, String modelId, String modelType, int version,
                            String featuresJson, BigDecimal backtest, String status) {
        jdbcTemplate.update(
            "INSERT INTO ecos_cognitive_model (id, model_id, model_type, version, features, backtest_score, status, create_by, update_by, is_deleted) " +
            "VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, 'system', 'system', 0)",
            id, modelId, modelType, version, featuresJson, backtest, status);
    }
}
