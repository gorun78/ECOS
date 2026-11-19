package com.chinacreator.gzcm.engine.ai.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

/**
 * LLM 模型注册表 DAO —— {@code ecos_ai.ecos_llm_model}（V359，E-3 模型注册入表裁决）的 JDBC 落点。
 *
 * <p>模式同 {@link AiToolRegistryDao}：表名 schema 限定（DR01/ST07）、COLUMNS 显式（禁 {@code SELECT *}）、
 * SQL 全参数化。基类 {@link JdbcTemplateDaoSupport}，禁裸 JdbcTemplate。</p>
 *
 * <p><b>密钥纪律</b>：本表只存 {@code api_key_ref}（引用名），<b>禁存密钥材料</b>（X-49/ST04）。
 * DAO 不提供任何字段的掩码职责——调用方也不得回显该列值。</p>
 *
 * <p><b>升版语义</b>：同 {@code model_key} 再注册 ⇒ 旧行置 {@code RETIRED}、新行 {@code version_no + 1}，
 * 不物理删（IR03 只加不删）。唯一索引 {@code uniq_llm_model_key(model_key, is_deleted)}
 * 保证同 key 至多一条在用行。</p>
 */
@Repository
public class LlmModelRegistryDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_ai.ecos_llm_model";

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** V359 全列（列序固定，禁 SELECT *：列序变更会静默错位）。 */
    private static final String COLUMNS = "id, model_key, provider, endpoint, api_key_ref, context_limit, "
            + "capabilities_json, cost_class, status, domain, version_no, is_deleted, "
            + "create_by, create_time, update_by, update_time";

    /** 注册表是否就位（读侧先探活，才能区分"表空"与"表不在"）。 */
    public boolean tableReady() {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'ecos_ai' AND table_name = 'ecos_llm_model'",
                Integer.class);
        return exists != null && exists > 0;
    }

    /** 全部未删行（含 RETIRED；调用方按需过滤 status）。 */
    public List<Map<String, Object>> listAll() {
        return jdbcTemplate.queryForList(
                "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE is_deleted = 0 ORDER BY model_key, version_no");
    }

    /** 指定 model_key 的当前 ACTIVE 行（无则 null）。 */
    public Map<String, Object> findActive(String modelKey) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT " + COLUMNS + " FROM " + TABLE
                        + " WHERE model_key = ? AND status = ? AND is_deleted = 0",
                modelKey, "ACTIVE");
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 指定 model_key 的最大 version_no（无行则 0）。 */
    public int maxVersion(String modelKey) {
        Integer max = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE model_key = ?", Integer.class, modelKey);
        return max == null ? 0 : max;
    }

    /** 把同 key 的 ACTIVE 行置为 RETIRED（升版前调用；返回受影响行数）。 */
    public int retireActive(String modelKey, String updateBy) {
        return jdbcTemplate.update(
                "UPDATE " + TABLE + " SET status = ?, update_by = ?, update_time = NOW()"
                        + " WHERE model_key = ? AND status = ? AND is_deleted = 0",
                "RETIRED", updateBy, modelKey, "ACTIVE");
    }

    /** 插入一行注册记录（version_no 由调用方给定）。返回受影响行数。 */
    public int insert(String id, String modelKey, String provider, String endpoint, String apiKeyRef,
                      int contextLimit, String capabilitiesJson, String costClass,
                      String versionNo, String createBy) {
        return jdbcTemplate.update(
                "INSERT INTO " + TABLE
                        + " (id, model_key, provider, endpoint, api_key_ref, context_limit,"
                        + " capabilities_json, cost_class, status, version_no, create_by)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, modelKey, provider, endpoint, apiKeyRef, contextLimit,
                capabilitiesJson, costClass, "ACTIVE", versionNo, createBy);
    }

    /**
     * 原子升版：retire 旧 ACTIVE 行 + 插入新 ACTIVE 行，同一事务内完成。
     *
     * <p><b>为什么必须原子</b>（2026-11-19 活体实测）：早先 Controller 直接顺序调
     * {@link #retireActive} 再 {@link #insert}，第二次注册因唯一索引冲突导致 insert 失败，
     * 而 retire 已提交 ⇒ 库内只剩 RETIRED 行、无 ACTIVE，模型从目录里"消失"。
     * 事务化后失败即整体回滚，旧 ACTIVE 保持可用。</p>
     *
     * <p>顺序说明：先 retire 再 insert 是必要顺序——部分唯一索引
     * {@code uniq_llm_model_active_key} 只允许一条 ACTIVE 行。</p>
     */
    public void replaceActive(String id, String modelKey, String provider, String endpoint, String apiKeyRef,
                              int contextLimit, String capabilitiesJson, String costClass,
                              String versionNo, String createBy) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            retireActive(modelKey, createBy);
            insert(id, modelKey, provider, endpoint, apiKeyRef, contextLimit,
                    capabilitiesJson, costClass, versionNo, createBy);
        });
    }
}
