package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * B2-b 数据访问收口（架构铁律 §2.5 #1 / IR06）：{@code RuleCausalService} 与
 * {@code RuleImpactService} 共用的 {@code sys_compliance_rule} 只读 SQL 落点
 * （两个 Service 的 SELECT_RULE_BY_ID / SELECT_ALL_RULES 常量完全同文，收敛为一处）。
 *
 * <p>只搬不改：SQL 文本 / 表名 / 字段 / 参数序与原 Service 逐字节一致；
 * description 文本引用匹配逻辑留在 Service 层。</p>
 */
@Repository
public class RuleDao extends JdbcTemplateDaoSupport {

    /** 按 id 查规则。 */
    public List<Map<String, Object>> findRuleById(String id) {
        return jdbcTemplate.queryForList(
            "SELECT id, name, domain, description FROM sys_compliance_rule WHERE id = ?", id);
    }

    /** 全量规则（description 文本匹配输入集）。 */
    public List<Map<String, Object>> findAllRules() {
        return jdbcTemplate.queryForList(
            "SELECT id, name, domain, description FROM sys_compliance_rule");
    }
}
