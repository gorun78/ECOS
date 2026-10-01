package com.chinacreator.gzcm.engine.data.quality.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * DqRcaDao — DQ 根因分析数据访问层（PMO-48-D T16）。
 * <p>
 * 架构铁律 §2.5 #1 收口：原内联在 {@code DqRcaServiceImpl} 的 JdbcTemplate 调用迁移到本 DAO，
 * SQL 文本一律离开 Service。表前缀统一为 {@code ecos_data}。
 *
 * @author PMO-48-D T16
 */
@Repository
public class DqRcaDao extends JdbcTemplateDaoSupport {

    /** 按 workOrderId 读工单 RCA 上下文（ruleId / assetId / severity / description）。 */
    public List<Map<String, Object>> findWorkOrder(String workOrderId) {
        return jdbcTemplate.queryForList(
                "SELECT rule_id AS ruleId, asset_id AS assetId, severity, description"
                + " FROM ecos_data.dq_work_order WHERE id = ? AND is_deleted = FALSE",
                workOrderId);
    }
}
