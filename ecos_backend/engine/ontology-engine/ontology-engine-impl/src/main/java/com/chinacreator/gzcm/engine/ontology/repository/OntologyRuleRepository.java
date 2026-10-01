package com.chinacreator.gzcm.engine.ontology.repository;

import java.util.List;
import java.util.Optional;

import com.chinacreator.gzcm.engine.ontology.dao.OntologyRuleDao;
import com.chinacreator.gzcm.engine.ontology.model.OntologyRule;
import org.springframework.stereotype.Repository;

/**
 * JdbcTemplate 仓库（门面）— 规则 CRUD（ecos_ontology_rule 表）。
 *
 * <p>B3c 数据访问收口：JDBC 实现下沉到 {@link OntologyRuleDao}，本仓库保留 API 委托。</p>
 */
@Repository
public class OntologyRuleRepository {

    private final OntologyRuleDao dao;

    public OntologyRuleRepository(OntologyRuleDao dao) {
        this.dao = dao;
    }

    public List<OntologyRule> findByEntity(String entityId) {
        return dao.findByEntity(entityId);
    }

    public List<OntologyRule> findAll() {
        return dao.findAll();
    }

    public Optional<OntologyRule> findById(String id) {
        return dao.findById(id);
    }

    public int insert(OntologyRule rule) {
        return dao.insert(rule);
    }

    public int update(String id, String code, String name, String ruleType,
                       String expression, String action, Integer priority,
                       Integer enabled, String description) {
        return dao.update(id, code, name, ruleType, expression, action, priority, enabled, description);
    }

    /** 逻辑删除 rule (T17)：原 DELETE → UPDATE SET is_deleted=1, status='ARCHIVED'。 */
    public int delete(String id) {
        return dao.delete(id);
    }

    /** 逻辑删除 rule (T17)：级联逻辑删除某实体下全部规则。 */
    public int deleteByEntity(String entityId) {
        return dao.deleteByEntity(entityId);
    }
}
