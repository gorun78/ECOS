package com.chinacreator.gzcm.engine.data.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.chinacreator.gzcm.engine.data.DataSourceService;
import com.chinacreator.gzcm.engine.data.dao.QueryExecutionDao;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import com.chinacreator.gzcm.runtime.access.connector.JdbcConnector;

/**
 * F02-13 Doris 方言回归（ultimate 档）— 验收测试：
 * SQL 控制台执行经 {@link JdbcConnector} 路由时对 Doris 数据源与 PG 共用
 * runtime 建连通道（最终版三态：PG / Doris / ClickHouse 均为外置数仓）。
 *
 * <p>本用例的可行性 note（与 D.13.3 珠江叠加档实际分支一致，保留 archive 一致性）：</p>
 * <ul>
 *   <li>运行时 JDBC 查询路由与 dialect 无关 → 可 mock {@link JdbcConnector} 单测；</li>
 *   <li>Doris wire 协议（MySQL protocol + 列存查询语义）需 live Doris 环境级集成，
 *       本用例不覆盖 wire 层，只覆盖「Doris 数据源 → 走 runtime JdbcConnector」
 *       这条不得回退 DriverManager 的路由守卫（ARCH-06 D.13 一致性）；</li>
 *   <li>同时断言 {@link ConnectorFactory} 将 "DORIS" 归一到 "JDBC"，
 *       防止未来 alias 表改动让 Doris 类型脱离 JDBC 连接器池。</li>
 * </ul>
 *
 * @author ECOS Data Engine Team / F02-13
 */
@DisplayName("F02-13 Doris 回归：ultimate 档 SQL 控制台复用 runtime JdbcConnector")
class SqlConsoleRegressionDorisTest {

    private static final String DORIS_CONFIG =
            "{\"jdbcUrl\":\"jdbc:mysql://doris:9030/ods?useSSL=false\",\"username\":\"u\",\"password\":\"p\"}";

    @Test
    @DisplayName("Doris 控制台执行仍走 runtime JdbcConnector.executeQueryGuarded（不 fallback DriverManager）")
    void dorisSqlConsoleRoutesViaJdbcConnector() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DataSourceService dsSvc = mock(DataSourceService.class);
        JdbcConnector jc = mock(JdbcConnector.class);
        QueryExecutionServiceImpl svc = new QueryExecutionServiceImpl(mock(QueryExecutionDao.class), dsSvc, jc);

        DataSourceEntity ds = new DataSourceEntity();
        ds.setDatasourceId("ds-doris");
        ds.setDatasourceName("doris-ultimate");
        ds.setDatasourceType("DORIS");
        ds.setConnectionConfig(DORIS_CONFIG);
        when(dsSvc.getById("ds-doris")).thenReturn(ds);

        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("stat_key", "k");
        row.put("value", 42L);
        List<Map<String, Object>> rows = new java.util.ArrayList<>(List.of(row));
        JdbcConnector.GuardedResult guarded = new JdbcConnector.GuardedResult(
                Arrays.asList(
                        new JdbcConnector.ColumnDescriptor("stat_key", "stat_key", "varchar"),
                        new JdbcConnector.ColumnDescriptor("value", "value", "bigint")),
                rows, false);
        // W31（QueryExecutorBindingTest 批）：executeQueryGuarded 增第 3 位绑定值参数；
        // 本类只钉通道路由，参数表取 anyList()，绑定语义断言见 QueryExecutorBindingTest。
        when(jc.executeQueryGuarded(eq(DORIS_CONFIG), anyString(), anyList(), anyInt(), anyInt(), anyInt()))
                .thenReturn(guarded);

        Map<String, Object> result = svc.execute(
                "ds-doris",
                "SELECT stat_key, sum(value) FROM ods.fct_stats GROUP BY stat_key ORDER BY stat_key LIMIT 50",
                Collections.emptyMap(), 50, 30);

        verify(jc).executeQueryGuarded(eq(DORIS_CONFIG), anyString(), anyList(), anyInt(), anyInt(), eq(10000));
        assertNotNull(result.get("columns"));
        assertEquals(1, (Integer) result.get("rowCount"), "Doris 查询结果 rowCount 应 = 1");
        Map<?, ?> first = (Map<?, ?>) ((List<?>) result.get("rows")).get(0);
        assertEquals("k", first.get("stat_key"));
        assertEquals(42L, first.get("value"));
    }

    @Test
    @DisplayName("ConnectorFactory 将 DORIS 别名归一到 JDBC，防止 Doris 数据源脱离 JDBC 连接器池")
    void connectorFactoryDorisAliasResolvesToJdbc() {
        assertNotNull(ConnectorFactory.knownType("DORIS"), "DORIS 必须在连接器工厂的已知类型表内");
        assertEquals("JDBC", ConnectorFactory.knownType("DORIS"),
                "DORIS 必须归一到 JDBC（架构铁律 2.5#1：Doris 统一经 runtime-access JdbcConnector）");
    }

    @Test
    @DisplayName("源码守卫：JdbcConnector (runtime-access) 是仓内唯一 JDBC 建连通道（ARCH-06 白名单闭合）")
    void jdbcConnectorSourceExistsAndUsesDriverManagerWhiteListed() throws Exception {
        // runtime-access 是唯一允许 java.sql.DriverManager 的模块（Database 访问规范 IR-06 白名单）。
        // 此处从 data-engine-impl user.dir 上溯到仓库根以定位 JdbcConnector 源码，
        // 断言其 testConnection/execute 全路径使用 DriverManager（合法白名单），
        // 而 data-engine-impl 侧（NoDriverManagerArchTest 已守）无 DriverManager。
        Path repoRoot = findRepoRoot();
        Path jcPath = repoRoot.resolve(
                "ecos_backend/runtime/runtime-access/src/main/java/com/chinacreator/gzcm/runtime/access/connector/JdbcConnector.java");
        assertTrue(Files.isRegularFile(jcPath), "缺 JdbcConnector 源码: " + jcPath);
        String src = Files.readString(jcPath, StandardCharsets.UTF_8);
        assertTrue(src.contains("DriverManager.getConnection"),
                "JdbcConnector 为 ARCH-06 白名单合法建连点，必须保留 java.sql.DriverManager 入口");
        assertTrue(src.contains("openConnection"),
                "JdbcConnector 必须暴露 openConnection 单通道（供 engine 侧继承 DatabaseMetaData）");
    }

    /** 从 user.dir 上溯仓库根，Fallback pom.xml 祖先。 */
    static Path findRepoRoot() {
        Path dir = java.nio.file.Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path cursor = dir;
        while (cursor != null) {
            Path git = cursor.resolve(".git");
            if (Files.isDirectory(git) || Files.isRegularFile(git)) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        cursor = dir;
        while (cursor != null) {
            if (Files.exists(cursor.resolve("pom.xml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("无法从 user.dir=" + dir + " 定位仓根");
    }
}
