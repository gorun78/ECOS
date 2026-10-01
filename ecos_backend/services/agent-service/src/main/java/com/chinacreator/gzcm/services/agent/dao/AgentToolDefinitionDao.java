package com.chinacreator.gzcm.services.agent.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import com.chinacreator.gzcm.services.agent.runtime.model.ToolDefinition;
import com.chinacreator.gzcm.services.agent.runtime.model.ToolType;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * R2-B4b 数据访问收口（架构铁律 §2.5 #1 / IR06）：agent-service 工具注册表
 * （{@code ecos_ai.ecos_tool_definition}）SQL 落点。
 *
 * <p>只搬不改：SQL 文本 / 表名 / 字段 / 参数序与原 ToolRouterServiceImpl 逐字节一致；
 * 行 → ToolDefinition 归一化逻辑随搬。</p>
 *
 * <p>承接原文件：toolrouter/ToolRouterServiceImpl.java。</p>
 */
@Repository
public class AgentToolDefinitionDao extends JdbcTemplateDaoSupport {

    /** 全部 ACTIVE 工具。行归一化随搬自原 getAvailableTools。 */
    public List<ToolDefinition> listActiveTools() {
        return jdbcTemplate.query(
            "SELECT code, name, tool_type, endpoint_url, status FROM ecos_ai.ecos_tool_definition WHERE status = 'ACTIVE'",
            (rs, rowNum) -> {
                ToolDefinition def = new ToolDefinition();
                def.setCode(rs.getString("code"));
                def.setName(rs.getString("name"));
                def.setType(ToolType.valueOf(rs.getString("tool_type")));
                def.setEndpoint(rs.getString("endpoint_url"));
                def.setStatus(rs.getString("status"));
                return def;
            });
    }
}
