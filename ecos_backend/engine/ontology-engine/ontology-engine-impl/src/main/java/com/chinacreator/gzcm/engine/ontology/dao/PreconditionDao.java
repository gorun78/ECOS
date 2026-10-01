package com.chinacreator.gzcm.engine.ontology.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * B3c 数据访问收口（R2/B3c）：{@code PreconditionEngine} 前置状态读取
 * （ecos_ontology_object / ecos_ontology 对象态 + sys_user_role 角色检查）的 JDBC 落点。
 *
 * <p>SQL 文本、参数序原样搬运；异常向调用方传播（try/catch 仍留在引擎侧，语义不变）。</p>
 */
@Repository
public class PreconditionDao extends JdbcTemplateDaoSupport {

    /** 多表尝试①：{@code SELECT * FROM ecos_ontology_object WHERE id = ?}。 */
    public List<Map<String, Object>> findObjectStateFromOntologyObject(String objectId) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM ecos_ontology_object WHERE id = ?", objectId);
    }

    /** 多表尝试②：{@code SELECT * FROM ecos_ontology WHERE id = ?}。 */
    public List<Map<String, Object>> findObjectStateFromOntology(String objectId) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM ecos_ontology WHERE id = ?", objectId);
    }

    /** 角色检查：COUNT 用户-角色关联，返回 Integer（可空）。 */
    public Integer countUserRole(String userId, String role) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_user_role ur " +
            "JOIN sys_role r ON ur.role_id = r.id " +
            "WHERE ur.user_id = ? AND r.code = ?",
            Integer.class, userId, role);
    }
}
