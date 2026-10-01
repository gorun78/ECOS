package com.chinacreator.gzcm.engine.ontology.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * B3c 数据访问收口（R2/B3c）：{@code PostActionExecutor} 的 JDBC 落点
 * （update_field → {@code ecos_ontology_object}；write_audit → {@code ecos_audit_log}）。
 *
 * <p>SQL 文本、参数序原样搬运。</p>
 */
@Repository
public class PostActionDao extends JdbcTemplateDaoSupport {

    public void updateObjectField(String objectId, String field, String value) {
        jdbcTemplate.update(
            "UPDATE ecos_ontology_object SET " + field + " = ?, updated_at = NOW() WHERE id = ?",
            value, objectId);
    }

    public void writeAudit(String auditId, String actionTypeId, String objectId, String userId,
                           String auditType, String summary) {
        jdbcTemplate.update("""
            INSERT INTO ecos_audit_log (id, action_type_id, object_id, user_id,
                audit_type, summary, created_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW())
            """,
            auditId, actionTypeId, objectId, userId,
            auditType, summary);
    }
}
