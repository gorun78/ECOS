package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * B2-b 数据访问收口（架构铁律 §2.5 #1 / IR06）：{@code DecisionLegacyBridge} 内联的
 * V29/V30 遗留决策源只读 SQL 落点（{@code ecos_ceo_causal_chain} /
 * {@code ecos_diagnostic_agent}，未桥接行按 provenance source_ref 排除）。
 *
 * <p>只搬不改：SQL 文本 / 表名 / 字段 / 参数序与原 Bridge 逐字节一致；
 * 桥接幂等 + decisionService 写入留在 Bridge 层。</p>
 */
@Repository
public class LegacyBridgeDao extends JdbcTemplateDaoSupport {

    /** 未桥接的 V29 CEO 因果链行（provenance source_ref='V29_bridge' 排除）。 */
    public List<Map<String, Object>> findUnbridgedCeoCausalChains() {
        return jdbcTemplate.queryForList(
            "SELECT * FROM ecos_ceo_causal_chain WHERE id NOT IN " +
            "(SELECT entity_id FROM ecos_provenance_entry WHERE entity_type = 'decision' " +
            "AND source_ref = 'V29_bridge')"
        );
    }

    /** 未桥接的 V30 诊断 Agent 行（provenance source_ref='V30_bridge' 排除）。 */
    public List<Map<String, Object>> findUnbridgedDiagnosticAgents() {
        return jdbcTemplate.queryForList(
            "SELECT * FROM ecos_diagnostic_agent WHERE id NOT IN " +
            "(SELECT entity_id FROM ecos_provenance_entry WHERE entity_type = 'decision' " +
            "AND source_ref = 'V30_bridge')"
        );
    }
}
