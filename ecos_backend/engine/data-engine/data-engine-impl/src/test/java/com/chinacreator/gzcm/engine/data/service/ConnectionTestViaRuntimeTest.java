package com.chinacreator.gzcm.engine.data.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.dao.DataSourceDao;
import com.chinacreator.gzcm.runtime.access.connector.Connector;
import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import com.chinacreator.gzcm.runtime.access.connector.JdbcConnector;

/**
 * F02-13 连通性测试经 runtime — 验收测试：
 * 数据源「测试连接」两个出口（{@code testConnection} 布尔 + {@code testConnectionById}
 * 明细 Map）必须路由到 runtime-access {@link JdbcConnector}，引擎侧不得再自建
 * {@code java.sql.DriverManager}。
 *
 * <p>P0-2 判定:</p>
 * <ul>
 *   <li>{@code testConnection(id)} → {@code ConnectorFactory.getConnector(type).testConnection(cfg)}
 *       被调用 1 次（关系型一律归 JdbcConnector）；</li>
 *   <li>{@code testConnectionById(id)} 关系型走 {@code JdbcConnector.testConnectionDetailed(cfg, type)}；</li>
 *   <li>Spy 拦截 {@code getInternal} 以免真实回 PG 拉数据源行。</li>
 * </ul>
 *
 * @author ECOS Data Engine Team / F02-13
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("F02-13 连通性测试经 runtime JdbcConnector（无 DriverManager）")
class ConnectionTestViaRuntimeTest {

    private static final String CFG =
            "{\"jdbcUrl\":\"jdbc:postgresql://localhost:5432/sys_man\",\"username\":\"u\",\"password\":\"p\"}";

    @Test
    @DisplayName("testConnection(id) → ConnectorFactory 路由到 JdbcConnector.testConnection（1 次）")
    void testConnectionDelegatesToRuntimeConnector() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcConnector jdbcConnector = mock(JdbcConnector.class);
        ConnectorFactory factory = mock(ConnectorFactory.class);
        DataSourceServiceImpl svc = new DataSourceServiceImpl(mock(DataSourceDao.class), factory);

        // 子类 spy 包住 getInternal + resolvePassword 加载私有回显逻辑
        DataSourceServiceImpl slicer = spy(svc);
        DataSourceEntity ds = new DataSourceEntity();
        ds.setDatasourceId("ds-route");
        ds.setDatasourceType("POSTGRESQL");
        ds.setConnectionConfig(CFG);
        Mockito.doReturn(ds).when(slicer).getInternal("ds-route");
        Mockito.doReturn(CFG).when(slicer).resolvePassword(anyString(), (String) Mockito.any());

        Connector resolved = mock(JdbcConnector.class);
        when(factory.getConnector("POSTGRESQL")).thenReturn(resolved);
        when(resolved.testConnection(CFG)).thenReturn(true);

        boolean ok = slicer.testConnection("ds-route");

        assertTrue(ok, "连通性应返回 true（连接器标 OK）");
        verify(factory).getConnector("POSTGRESQL");
        verify(resolved).testConnection(CFG);
    }

    @Test
    @DisplayName("testConnectionById(id) 关系型 → JdbcConnector.testConnectionDetailed(cfg, type)；success=false 时 error 可读")
    void testConnectionByIdRoutesToJdbcConnectorDetailed() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ConnectorFactory factory = mock(ConnectorFactory.class);
        DataSourceServiceImpl svc = new DataSourceServiceImpl(mock(DataSourceDao.class), factory);
        DataSourceServiceImpl slicer = spy(svc);
        DataSourceEntity ds = new DataSourceEntity();
        ds.setDatasourceId("ds-det");
        ds.setDatasourceType("POSTGRESQL");
        ds.setConnectionConfig(CFG);
        Mockito.doReturn(ds).when(slicer).getInternal("ds-det");
        Mockito.doReturn(CFG).when(slicer).resolvePassword(anyString(), (String) Mockito.any());

        JdbcConnector jc = mock(JdbcConnector.class);
        when(factory.getConnector("POSTGRESQL")).thenReturn(jc);
        java.util.Map<String, Object> detail = new java.util.LinkedHashMap<>();
        detail.put("success", false);
        detail.put("error", "网络不可达/连接被拒绝 (type=POSTGRESQL, jdbc:postgresql://localhost:5432/sys_man)");
        detail.put("message", detail.get("error"));
        when(jc.testConnectionDetailed(CFG, "POSTGRESQL")).thenReturn(detail);

        java.util.Map<String, Object> res = slicer.testConnectionById("ds-det");

        verify(factory).getConnector("POSTGRESQL");
        verify(jc).testConnectionDetailed(CFG, "POSTGRESQL");
        assertEquals(Boolean.FALSE, res.get("success"), "success=false 应透传");
        Object err = res.get("error");
        assertTrue(err instanceof String && !((String) err).isBlank(), "error 字段必须可读（分类化 error）");
        assertFalse(((String) err).contains("password"), "error 消息不得回显 password（H10-T4 安全口径）");
    }

    @Test
    @DisplayName("源码守卫：DataSourceServiceImpl 无 java.sql.DriverManager 直用")
    void dataSourceServiceSourceFreeOfDriverManager() throws Exception {
        String src = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                "src/main/java/com/chinacreator/gzcm/engine/data/service/DataSourceServiceImpl.java")));
        assertFalse(src.contains("import java.sql.DriverManager"),
                "DataSourceServiceImpl 不得 import java.sql.DriverManager");
        assertFalse(src.contains("DriverManager.getConnection"),
                "DataSourceServiceImpl 不得直接调 DriverManager.getConnection");
        assertTrue(src.contains("connectorFactory"),
                "连通性测试应经 ConnectorFactory → JdbcConnector 单通道（F02-13）");
    }
}
