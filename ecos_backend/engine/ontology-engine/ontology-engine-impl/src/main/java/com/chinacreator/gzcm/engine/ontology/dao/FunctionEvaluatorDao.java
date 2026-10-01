package com.chinacreator.gzcm.engine.ontology.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.engine.ontology.model.OntologyProperty;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * B3c 数据访问收口（R2/B3c）：{@code FunctionEvaluator} 计算属性求值（AGGREGATION / LOOKUP /
 * FUNCTION 属性查询）的 JDBC 落点。
 *
 * <p>SQL 文本、参数序原样搬运；异常向调用方传播（try/catch 仍留在引擎侧，语义不变）。</p>
 */
@Repository
public class FunctionEvaluatorDao extends JdbcTemplateDaoSupport {

    /** AGGREGATION：{@code SELECT func(field) FROM entity [WHERE ...]}，返回单值 Object。 */
    public Object aggregate(String sql, Object[] params) {
        return jdbcTemplate.queryForObject(sql, params, Object.class);
    }

    /** LOOKUP：{@code SELECT targetField FROM entity WHERE pk = ? LIMIT 1}，返回行列表。 */
    public List<Map<String, Object>> lookup(String sql, Object fkValue) {
        return jdbcTemplate.queryForList(sql, fkValue);
    }

    /** 查询指定 entity 的所有 FUNCTION 类型属性。 */
    public List<OntologyProperty> findFunctionProperties(String entityCode) {
        RowMapper<OntologyProperty> mapper = (rs, rn) -> {
            OntologyProperty p = new OntologyProperty();
            p.setId(rs.getString("id"));
            p.setEntityId(rs.getString("entity_id"));
            p.setCode(rs.getString("code"));
            p.setName(rs.getString("name"));
            p.setPropertyType(rs.getString("property_type"));
            p.setFunctionType(rs.getString("function_type"));
            p.setFunctionExpression(rs.getString("function_expression"));
            return p;
        };
        return jdbcTemplate.query(
            "SELECT p.* FROM ecos_ontology_property p " +
            "JOIN ecos_ontology_entity e ON p.entity_id = e.id " +
            "WHERE e.code = ? AND p.function_type IS NOT NULL AND p.function_type <> ''",
            mapper, entityCode);
    }
}
