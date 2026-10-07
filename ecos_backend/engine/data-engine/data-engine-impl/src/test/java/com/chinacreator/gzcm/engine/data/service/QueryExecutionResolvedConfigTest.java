package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.engine.data.DataSourceService;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.runtime.access.connector.JdbcConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B6 验收：QueryExecutionServiceImpl 建连必须走 getResolvedConnectionConfig（密码回填 + URL 白名单前置），
 * 不得持有 raw getConnectionConfig (密文 PF 明文超柫 : "PasswordEncrypted" 列与 ConnectionConfig JSON 双存时,
 * raw JSON 可能不含 password 明文字段, JdbcConnector 直接消费会拿到空密码或残留占位).
 *
 * <p>JdbcConnector 内部对 URL 已强制 {@code JdbcUrlPolicy.validate}（B2 已在
 * buildJdbcUrl: line 133 / openConnection: line 494 / direct 通道: line 596 三处接入）；
 * 本测试只锁定 QueryExecutionServiceImpl 侧的 "resolved 通道" 契约，不重复 JdbcConnector 白名单断言。</p>
 */
@ExtendWith(MockitoExtension.class)
class QueryExecutionResolvedConfigTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private DataSourceService dataSourceService;
    @Mock private JdbcConnector jdbcConnector;

    private QueryExecutionServiceImpl service;

    private static final String DS_ID = "ds-uuid-1";
    private static final String RESOLVED_CONFIG = "{\"host\":\"pg-svc\",\"port\":5432,\"user\":\"svc\",\"password\":\"real-pw\"}";
    private static final String RAW_CONFIG = "{\"host\":\"pg-svc\",\"port\":5432,\"user\":\"svc\"}";

    @BeforeEach
    void setUp() {
        service = new QueryExecutionServiceImpl(jdbc, dataSourceService, jdbcConnector);
    }

    @Test
    @DisplayName("execute → 建连走 getResolvedConnectionConfig，不持有 raw getConnectionConfig 建连")
    void executeUsesResolvedConnectionConfig() throws SQLException {
        DataSourceEntity ds = new DataSourceEntity();
        ds.setDatasourceId(DS_ID);
        ds.setConnectionConfig(RAW_CONFIG);
        ds.setDatasourceName("ds1");
        ds.setDatasourceType("postgresql");
        when(dataSourceService.getById(DS_ID)).thenReturn(ds);
        when(dataSourceService.getResolvedConnectionConfig(DS_ID)).thenReturn(RESOLVED_CONFIG);

        JdbcConnector.GuardedResult guarded = new JdbcConnector.GuardedResult(
                List.of(), List.<Map<String, Object>>of(), false);
        when(jdbcConnector.executeQueryGuarded(any(String.class), any(String.class), anyInt(), anyInt(), anyInt()))
                .thenReturn(guarded);

        service.execute(DS_ID, "SELECT 1", Map.of(), 100, 30);

        ArgumentCaptor<String> cfgCap = ArgumentCaptor.forClass(String.class);
        verify(jdbcConnector).executeQueryGuarded(cfgCap.capture(), any(String.class), anyInt(), anyInt(), anyInt());
        assertEquals(RESOLVED_CONFIG, cfgCap.getValue(),
                "execute 传给 JdbcConnector 的连接配置必须是 getResolvedConnectionConfig 输出（密码回填）");
        // 反向：JdbcConnector 收到的必须是 resolved 而非 raw
        verify(dataSourceService).getResolvedConnectionConfig(DS_ID);
    }

    @Test
    @DisplayName("getResolvedConnectionConfig 返回 null → 拒绝建连（fail-closed），不回落 raw")
    void nullResolvedConfigFailsClosed() throws SQLException {
        DataSourceEntity ds = new DataSourceEntity();
        ds.setDatasourceId(DS_ID);
        ds.setConnectionConfig(RAW_CONFIG);
        when(dataSourceService.getById(DS_ID)).thenReturn(ds);
        when(dataSourceService.getResolvedConnectionConfig(DS_ID)).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> service.execute(DS_ID, "SELECT 1", Map.of(), 100, 30),
                "resolved 未命中必须 fail-closed，禁止回退到 raw getConnectionConfig（可能缺密码明文）");
        verify(jdbcConnector, never()).executeQueryGuarded(any(String.class), any(String.class), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("getSchemaTree → 同样走 getResolvedConnectionConfig 建连")
    void getSchemaTreeUsesResolvedConnectionConfig() throws SQLException {
        DataSourceEntity ds = new DataSourceEntity();
        ds.setDatasourceId(DS_ID);
        ds.setConnectionConfig(RAW_CONFIG);
        ds.setDatasourceName("ds1");
        ds.setDatasourceType("postgresql");
        when(dataSourceService.getById(DS_ID)).thenReturn(ds);
        when(dataSourceService.getResolvedConnectionConfig(DS_ID)).thenReturn(RESOLVED_CONFIG);

        // 简化：mock Connection 反编译 → 空 result；关键是断言走 resolved 通道
        java.sql.Connection conn = org.mockito.Mockito.mock(java.sql.Connection.class);
        java.sql.DatabaseMetaData md = org.mockito.Mockito.mock(java.sql.DatabaseMetaData.class);
        try (java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class)) {
            when(md.getTables(any(), any(), any(), any(String[].class))).thenReturn(rs);
            when(conn.getMetaData()).thenReturn(md);
            when(conn.getCatalog()).thenReturn("sys_man");
            when(conn.getSchema()).thenReturn("public");
            when(jdbcConnector.openConnection(RESOLVED_CONFIG)).thenReturn(conn);
        }

        service.getSchemaTree(DS_ID);

        verify(jdbcConnector).openConnection(RESOLVED_CONFIG);
        verify(dataSourceService).getResolvedConnectionConfig(DS_ID);
    }
}
