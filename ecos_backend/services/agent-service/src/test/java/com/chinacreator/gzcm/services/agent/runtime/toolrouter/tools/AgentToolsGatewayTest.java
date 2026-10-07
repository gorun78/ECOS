package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentGatewayClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * B8e — agent 工具三件套改走 {@link AgentGatewayClient}（不再有直连 JDBC / 硬编码网关地址）。
 *
 * <p>用一个「记录型」{@link AgentGatewayClient} 子类捕获调用的 method/path/body，
 * 断言三个命名工具（QueryPhysicalTable / ListDataSources / AuditAccessLog）把请求
 * 路由到正确的 data-engine / security-engine REST 端点，且租户头由客户端统一注入。</p>
 */
class AgentToolsGatewayTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    /** 记录型客户端：捕获 method + apiPath + body，返回一个可断言的 sentinel。 */
    private static final class RecordingGateway extends AgentGatewayClient {
        String method;
        String apiPath;
        Object body;
        Object[] vars;
        final String sentinel = "RESP:" + System.nanoTime();

        RecordingGateway() {
            super("http://127.0.0.1:8080", 5000, 5000);
        }

        @Override
        public Object get(String apiPath, Object... uriVars) {
            this.method = "GET";
            this.apiPath = apiPath;
            this.vars = uriVars;
            return sentinel;
        }

        @Override
        public Object post(String apiPath, Object body, Object... uriVars) {
            this.method = "POST";
            this.apiPath = apiPath;
            this.body = body;
            this.vars = uriVars;
            return sentinel;
        }
    }

    @Test
    @DisplayName("AuditAccessLog 走 gateway → /api/v1/audit/logs（不再直连硬编码网关）")
    void auditAccessLogRoutesThroughGateway() {
        RecordingGateway gw = new RecordingGateway();
        AuditAccessLogTool tool = new AuditAccessLogTool(gw);
        Object out = tool.execute("AuditAccessLog", new HashMap<>());
        assertEquals("GET", gw.method);
        assertEquals("/api/v1/audit/logs", gw.apiPath);
        assertSame(gw.sentinel, out);
        assertEquals(true, tool.supports("AuditAccessLog"));
    }

    @Test
    @DisplayName("ListDataSources 走 gateway → /api/v1/datasource（不再自建 JDBC 直读跨引擎表）")
    void listDataSourcesRoutesThroughGateway() {
        RecordingGateway gw = new RecordingGateway();
        ListDataSourcesTool tool = new ListDataSourcesTool(gw);
        tool.execute("ListDataSources", new HashMap<>());
        assertEquals("GET", gw.method);
        assertEquals("/api/v1/datasource", gw.apiPath);
        assertEquals(true, tool.supports("ListDataSources"));
    }

    @Test
    @DisplayName("QueryPhysicalTable 走 gateway → POST /api/v1/engine/data/query/execute 且透传数据源/SQL/行上限")
    void queryPhysicalTableRoutesThroughGateway() {
        RecordingGateway gw = new RecordingGateway();
        QueryPhysicalTableTool tool = new QueryPhysicalTableTool(gw);
        Map<String, Object> params = new HashMap<>();
        params.put("datasourceId", "ds-1");
        params.put("sql", "SELECT 1");
        params.put("tableName", "sales");
        tool.execute("QueryPhysicalTable", params);

        assertEquals("POST", gw.method);
        assertEquals("/api/v1/engine/data/query/execute", gw.apiPath);
        Map<?, ?> body = (Map<?, ?>) gw.body;
        assertEquals("ds-1", body.get("datasource_id"));
        assertEquals("SELECT 1", body.get("sql"));
        assertEquals("sales", body.get("table_name"));
        assertEquals(1000, ((Number) body.get("max_rows")).intValue());
        assertEquals(true, tool.supports("QueryPhysicalTable"));
    }
}
