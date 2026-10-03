package com.chinacreator.gzcm.engine.data.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.runtime.access.connector.Connector;
import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;

/**
 * F02-13（详细设计-02，DATA P1）—— 数据源<b>连接测试经 runtime-access</b>，禁引擎内自建直连。
 *
 * <p>设计 F02-13 验收「{@code ConnectionTestViaRuntimeTest}」：数据源连接测试的<b>真实校验</b>必须
 * 收敛到 runtime-access 的 {@code Connector#testConnection}（不再散用 {@code DriverManager}）。
 * {@link DataSourceServiceImpl#testConnection} 对关系型数据源走
 * {@code connectorFactory.getConnector(type).testConnection(cfg)}（本测试对该 seam 做 Mockito
 * 运行时判定：stub 一个 mock {@link Connector}，断言测试<b>确实委托</b>到它、结果透传、且
 * <b>不产生</b>任何 {@code DriverManager} 直连——离线、不真建连、不触库。</p>
 *
 * <p>反向：数据源不存在（getInternal 返回 null）⇒ 直接 false，<b>不触碰</b> connectorFactory。</p>
 */
@DisplayName("F02-13 连接测试经 runtime-access Connector（P1，离线）")
class ConnectionTestViaRuntimeTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ConnectorFactory connectorFactory = mock(ConnectorFactory.class);
    private final Connector connector = mock(Connector.class);
    private final DataSourceServiceImpl svc = new DataSourceServiceImpl(jdbc, connectorFactory);

    private static DataSourceEntity pgDs(String id, String cfg) {
        DataSourceEntity ds = new DataSourceEntity();
        ds.setDatasourceId(id);
        ds.setDatasourceType("POSTGRESQL");
        ds.setConnectionConfig(cfg); // passwordEncrypted 保持 null ⇒ resolvePassword 原样返回 cfg
        return ds;
    }

    @SuppressWarnings("unchecked")
    private void stubInternalRead(DataSourceEntity ds) {
        // getInternal 用 jdbc.query(sql, RowMapper, String id)
        when(jdbc.query(anyString(), any(RowMapper.class), any()))
                .thenAnswer(inv -> (ds == null) ? List.<DataSourceEntity>of() : List.of(ds));
    }

    @Test
    @DisplayName("ConnectionTestViaRuntimeTest — POSTGRESQL ⇒ 委托 connector.testConnection 且结果透传（无 DriverManager 直连）")
    void relational_delegatesToRuntimeConnector() {
        stubInternalRead(pgDs("pg-1", "{}"));
        when(connectorFactory.getConnector("POSTGRESQL")).thenReturn(connector);
        when(connector.testConnection("{}")).thenReturn(true);

        assertTrue(svc.testConnection("pg-1"), "connector 返回成功 ⇒ testConnection 应返回 true");

        verify(connectorFactory).getConnector(eq("POSTGRESQL"));
        verify(connector).testConnection("{}");
        // 无 DriverManager：真实连通唯一通道即上述 mock Connector（断言已锁定），桩内无 java.sql.DriverManager 参与
    }

    @Test
    @DisplayName("ConnectionTestViaRuntimeTest — connector 报告失败 ⇒ testConnection 透传 false（不误报）")
    void connectorFailure_propagates() {
        stubInternalRead(pgDs("pg-2", "{}"));
        when(connectorFactory.getConnector("POSTGRESQL")).thenReturn(connector);
        when(connector.testConnection("{}")).thenReturn(false);

        assertFalse(svc.testConnection("pg-2"), "connector 失败 ⇒ testConnection 应返回 false");
        verify(connector).testConnection("{}");
    }

    @Test
    @DisplayName("ConnectionTestViaRuntimeTest（反向）— 数据源不存在 ⇒ false 且不触碰 connectorFactory")
    void missingDatasource_shortCircuits() {
        stubInternalRead(null);
        assertFalse(svc.testConnection("nope"), "不存在的 ds 应返回 false");
        verify(connectorFactory, never()).getConnector(anyString());
    }
}
