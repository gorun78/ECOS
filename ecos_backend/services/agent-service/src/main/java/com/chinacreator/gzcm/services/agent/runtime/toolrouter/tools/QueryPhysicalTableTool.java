package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentGatewayClient;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 经数据源执行物理表查询。走 {@link AgentGatewayClient} → data-engine
 * {@code POST /api/v1/engine/data/query/execute}（该端点内建 RLS 行级过滤、
 * B2 JdbcUrlPolicy 白名单预检与超时保护），不再自建 JDBC 直读 + 字符串拼 LIMIT。
 * 行上限经 {@code max_rows} 参数交给 data-engine 统一管控。
 */
@Component
public class QueryPhysicalTableTool implements ToolExecutor {

    private final AgentGatewayClient gateway;

    public QueryPhysicalTableTool(AgentGatewayClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("datasource_id", params.get("datasourceId"));
        body.put("sql", params.get("sql"));
        Object tableName = params.get("tableName");
        if (tableName != null) {
            body.put("table_name", tableName);
        }
        body.put("max_rows", params.containsKey("maxRows") ? params.get("maxRows") : 1000);
        return gateway.post("/api/v1/engine/data/query/execute", body);
    }

    @Override
    public boolean supports(String toolCode) {
        return "QueryPhysicalTable".equals(toolCode);
    }
}
