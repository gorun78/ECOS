package com.chinacreator.gzcm.engine.data.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.chinacreator.gzcm.engine.data.DataSourceService;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.runtime.access.connector.JdbcConnector;

/**
 * F02-13 PG 方言回归 — 验收测试：
 * SQL 控制台路径（{@link QueryExecutionServiceImpl#execute}）建连 +
 * 受保护查询必须走 runtime-access {@link JdbcConnector}.executeQueryGuarded，
 * 引擎侧不允许再直调 {@code java.sql.DriverManager}（ARCH-06 唯一白名单在 runtime-access）。
 *
 * <p>PG 是 ECOS 主推 dialect；本用例以 PG 语义 SQL（{@code ... LIMIT ?}）
 * 覆盖代码路由 + 参数契约，不引 live DB（wire 协议层留环境级集成，本单测守路由）。</p>
 *
 * @author ECOS Data Engine Team / F02-13
 */
@DisplayName("F02-13 PG 回归：SQL 控制台走 runtime JdbcConnector.executeQueryGuarded")
class SqlConsoleRegressionPgTest {

    private static final String PG_CONFIG =
            "{\"jdbcUrl\":\"jdbc:postgresql://localhost:5432/sys_man\",\"username\":\"u\",\"password\":\"p\"}";

    @Test
    @DisplayName("PG 控制台执行 → JdbcConnector.executeQueryGuarded 被调；响应契约 columns/rows/rowCount")
    void pgSqlConsoleRoutesThroughJdbcConnectorGuarded() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DataSourceService dsSvc = mock(DataSourceService.class);
        JdbcConnector jc = mock(JdbcConnector.class);
        QueryExecutionServiceImpl svc = new QueryExecutionServiceImpl(jdbc, dsSvc, jc);

        DataSourceEntity ds = new DataSourceEntity();
        ds.setDatasourceId("ds-pg");
        ds.setDatasourceName("local pg");
        ds.setDatasourceType("POSTGRESQL");
        ds.setConnectionConfig(PG_CONFIG);
        when(dsSvc.getById("ds-pg")).thenReturn(ds);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("tenant", "t1");
        List<Map<String, Object>> rows = new java.util.ArrayList<>(List.of(row));
        JdbcConnector.GuardedResult guarded = new JdbcConnector.GuardedResult(
                Arrays.asList(
                        new JdbcConnector.ColumnDescriptor("id", "id", "bigint"),
                        new JdbcConnector.ColumnDescriptor("tenant", "tenant", "character varying")),
                rows, false);
        when(jc.executeQueryGuarded(eq(PG_CONFIG), anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(guarded);

        Map<String, Object> result = svc.execute(
                "ds-pg",
                "SELECT id, tenant FROM t WHERE tenant = 't1' LIMIT 100",
                Collections.emptyMap(), 100, 30);

        // 关键路由断言：建连与查询统一走 JdbcConnector（1 次）
        verify(jc).executeQueryGuarded(eq(PG_CONFIG), anyString(), anyInt(), anyInt(), eq(10000));
        assertNotNull(result.get("columns"));
        assertNotNull(result.get("rows"));
        assertEquals(1, (Integer) result.get("rowCount"), "rowCount 应 = 1");
        Map<?, ?> firstRow = (Map<?, ?>) ((List<?>) result.get("rows")).get(0);
        assertEquals("t1", firstRow.get("tenant"), "DB 行内容需按 JDBC columnLabel 保留");
    }

    @Test
    @DisplayName("控制台执行：JdbcConnector 上抛 SQLException → 服务映射为运行时异常（不静默假结果）")
    void pgSqlConsoleSqliExceptionSurfaces() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DataSourceService dsSvc = mock(DataSourceService.class);
        JdbcConnector jc = mock(JdbcConnector.class);
        QueryExecutionServiceImpl svc = new QueryExecutionServiceImpl(jdbc, dsSvc, jc);

        DataSourceEntity ds = new DataSourceEntity();
        ds.setDatasourceId("ds-pg-err");
        ds.setDatasourceType("POSTGRESQL");
        ds.setConnectionConfig(PG_CONFIG);
        when(dsSvc.getById("ds-pg-err")).thenReturn(ds);
        when(jc.executeQueryGuarded(anyString(), anyString(), anyInt(), anyInt(), anyInt()))
                .thenThrow(new java.sql.SQLException("relation \"t\" does not exist"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> svc.execute("ds-pg-err", "SELECT * FROM t LIMIT 100",
                        Collections.emptyMap(), 100, 30),
                "PG syntax/relation 错误必须 fail-loud，不得假返回空 rows");
        verify(jc).executeQueryGuarded(eq(PG_CONFIG), anyString(), anyInt(), anyInt(), eq(10000));
    }

    @Test
    @DisplayName("源码级：QueryExecutionServiceImpl 无 java.sql.DriverManager import / 调用（ARCH-06 白名单闭合）")
    void sourceHasNoDriverManagerDirectUsage() throws IOException {
        Path srcPath = Path.of(
                "src/main/java/com/chinacreator/gzcm/engine/data/service/QueryExecutionServiceImpl.java");
        List<String> lines = Files.readAllLines(srcPath, StandardCharsets.UTF_8);
        for (String raw : lines) {
            String t = raw.trim();
            if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) {
                continue;
            }
            assertFalse(t.contains("import java.sql.DriverManager"),
                    "源码行不得 import java.sql.DriverManager: " + t);
            assertFalse(t.contains("DriverManager.getConnection"),
                    "源码行不得直接调 DriverManager.getConnection: " + t);
        }
        // 反证：源码必须显式引用 JdbcConnector（连接器通道路由存在）
        String whole = String.join("\n", lines);
        assertTrue(whole.contains("JdbcConnector"),
                "QueryExecutionServiceImpl 应显式依赖 runtime-access JdbcConnector");
        assertTrue(whole.contains("executeQueryGuarded"),
                "SQL 控制台查询必须走 JdbcConnector.executeQueryGuarded（H2-T6 通道路由）");
    }
}
