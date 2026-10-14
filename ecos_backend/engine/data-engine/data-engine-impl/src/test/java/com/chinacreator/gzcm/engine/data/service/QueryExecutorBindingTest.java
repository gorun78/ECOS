package com.chinacreator.gzcm.engine.data.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.chinacreator.gzcm.engine.data.DataSourceService;
import com.chinacreator.gzcm.engine.data.dao.QueryExecutionDao;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.runtime.access.connector.JdbcConnector;

/**
 * 详细设计-01 W31（P0 安全）／从零实施版 §4.7.1「RLS 参数化」行第 4 枚门禁
 * ——<b>执行层参数绑定门禁</b>。
 *
 * <p><b>本门禁守的是失败判据「字面量入 SQL」</b>。差距分析 §1.16 把它记为「具名门禁不存在」；
 * 落门禁前实扫发现它<b>不只是缺测试</b>：{@code QueryExecutionServiceImpl} 的
 * {@code resolveParams} 把 {@code :name} 的值经 {@code replace(":" + key, "'手工转义值'")}
 * 内联进 SQL 文本，再交给只接受 SQL 字符串的 {@code JdbcConnector.executeQueryGuarded}
 * （内部是 {@code Statement}，无从绑定）。而 {@code QueryController} 的 RLS 参数化谓词
 * 的绑定值正是 merge 进同一个 {@code params} 后走这条通道 ⇒ 「参数化绑定」在执行层是假的，
 * {@code RlsInjectionGuardArchTest} 只禁了 wrapper 拼接形态，看不见这条内联面＝假绿。</p>
 *
 * <p><b>修复形态（同一 commit 内实现，禁先写空转测试＝R9②）</b>：
 * ① {@code JdbcConnector} 增 {@code (config, sql, List<?> params, maxRows, timeout, rowLimit)}
 * 重载，值经 {@code PreparedStatement#setObject} 绑定（空参数仍走原 {@code Statement} 形态，
 * 既有 5 参签名与行为不变＝只加不改）；
 * ② {@code QueryExecutionServiceImpl#bindNamedParameters} 把 {@code :name} 改写为位置占位符
 * {@code ?} 并按出现序收集绑定值，语句与值彻底分离。</p>
 *
 * <p><b>断言口径</b>：主用例走<b>真实执行链</b>（service → 被 mock 的 connector 捕获实参），
 * 因此断言的是「connector 实际收到了什么 SQL 与什么参数表」，不是对源码正则的自证。
 * 词法细节（{@code ::} 转换、引号字面量、未知占位符、重复占位符）另以纯函数用例钉死。
 * 末两条是<b>反空转</b>：源码里若「字面量内联」形态复发、或 connector 的参数化重载被删，
 * 本类立刻变红，而不是在空集上恒真。</p>
 *
 * <p><b>与设计的两处形态落差（如实登记，不冒充）</b>：
 * ① 设计 §4.7.1 写作 {@code mvn -pl gateway -am test}，本类按仓内既有惯例落在<b>载体所在模块</b>
 * （同 {@code RlsPredicateBindingTest} 落 security-engine-impl），gateway 聚合跑由 reactor 承担；
 * ② 本类用 mock connector 验证「语句/参数分离」契约，<b>不</b>主张真实 JDBC 驱动的绑定行为
 * （驱动层留环境级集成，口径同 {@code SqlConsoleRegressionPgTest}）。</p>
 */
@DisplayName("F04/W31：查询执行层参数绑定 —— 命名参数落位置占位符，值不入 SQL 文本")
class QueryExecutorBindingTest {

    private static final String CONFIG =
            "{\"jdbcUrl\":\"jdbc:postgresql://localhost:5432/sys_man\",\"username\":\"u\",\"password\":\"p\"}";

    private static final String SERVICE_SRC_REL =
            "src/main/java/com/chinacreator/gzcm/engine/data/service/QueryExecutionServiceImpl.java";

    // ────────────────────────────────────────────────────────────
    // ① 真实执行链：connector 收到的 SQL 只含 `?`，值全在参数表里
    // ────────────────────────────────────────────────────────────
    @Test
    @DisplayName("RLS 谓词命名参数 → 位置占位符 + 按出现序绑定值；SQL 文本零字面量")
    void namedParametersBecomePositionalBindsOnTheRealPath() throws Exception {
        Fixture f = new Fixture();
        when(f.connector.executeQueryGuarded(anyString(), anyString(), anyList(),
                anyInt(), anyInt(), anyInt())).thenReturn(f.guarded());

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("st", "DELETED");
        params.put("deptId", "d01");
        f.service().execute("ds-binding",
                "SELECT id FROM (SELECT * FROM ecos_project) AS _rls "
                        + "WHERE dept_id = :deptId AND status <> :st LIMIT 50",
                params, 100, 30);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<Object>> bound = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(f.connector).executeQueryGuarded(eq(CONFIG), sql.capture(), bound.capture(),
                anyInt(), anyInt(), eq(10000));

        String executed = sql.getValue();
        List<Object> values = bound.getValue();

        // 位置占位符个数 = 绑定值个数（顺序即语义：dept_id 在前 ⇒ deptId 先绑）
        assertEquals(2, occurrences(executed, '?'), "改写后应恰有 2 枚位置占位符: " + executed);
        assertEquals(2, values.size(), "绑定值个数必须与占位符个数相等");
        assertEquals("d01", values.get(0), "绑定序必须按占位符出现序，不按 params 声明序");
        assertEquals("DELETED", values.get(1));

        // 值绝不出现在 SQL 文本里（＝失败判据「字面量入 SQL」的反面）
        assertFalse(executed.contains("d01"), "参数值不得内联进 SQL: " + executed);
        assertFalse(executed.contains("DELETED"), "参数值不得内联进 SQL: " + executed);
        assertFalse(executed.contains(":deptId") || executed.contains(":st"),
                "已登记的命名占位符必须全部改写: " + executed);
        assertFalse(executed.contains("'"), "绑定路径产出的 SQL 不该再有任何字符串字面量: " + executed);
    }

    @Test
    @DisplayName("含引号的注入载荷：值原样进参数表，SQL 文本不含其任何片段")
    void quoteBearingPayloadIsCarriedOnlyAsBoundValue() throws Exception {
        Fixture f = new Fixture();
        when(f.connector.executeQueryGuarded(anyString(), anyString(), anyList(),
                anyInt(), anyInt(), anyInt())).thenReturn(f.guarded());

        String payload = "d01' OR '1'='1'; DROP TABLE ecos_project; --";
        f.service().execute("ds-inject",
                "SELECT id FROM ecos_project WHERE dept_id = :deptId LIMIT 10",
                Map.of("deptId", payload), 100, 30);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<Object>> bound = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(f.connector).executeQueryGuarded(eq(CONFIG), sql.capture(), bound.capture(),
                anyInt(), anyInt(), eq(10000));

        assertEquals(1, occurrences(sql.getValue(), '?'));
        assertEquals(List.of(payload), bound.getValue(),
                "值必须未经转义/截断/改写地作为绑定值送达驱动");
        for (String frag : new String[] {"DROP TABLE", "OR '1'='1'", "d01", "--"}) {
            assertFalse(sql.getValue().contains(frag),
                    "注入载荷片段 " + frag + " 不得出现在 SQL 文本: " + sql.getValue());
        }
    }

    @Test
    @DisplayName("审计落库记的是绑定前模板：参数值不进查询历史文本")
    void historyRecordsTemplateNotBoundValues() throws Exception {
        Fixture f = new Fixture();
        when(f.connector.executeQueryGuarded(anyString(), anyString(), anyList(),
                anyInt(), anyInt(), anyInt())).thenReturn(f.guarded());

        f.service().execute("ds-hist", "SELECT 1 FROM t WHERE a = :a LIMIT 5",
                Map.of("a", "secret-value"), 100, 30);

        ArgumentCaptor<String> recorded = ArgumentCaptor.forClass(String.class);
        verify(f.dao).insertHistoryStart(anyString(), eq("ds-hist"), recorded.capture());
        assertTrue(recorded.getValue().contains(":a"),
                "历史应保留命名参数模板（可复核绑定形态）: " + recorded.getValue());
        assertFalse(recorded.getValue().contains("secret-value"),
                "参数值不得落入查询历史文本: " + recorded.getValue());
    }

    // ────────────────────────────────────────────────────────────
    // ② 纯函数：词法边界钉死（回归风险最高的三类）
    // ────────────────────────────────────────────────────────────
    @Test
    @DisplayName("PostgreSQL ::类型转换与引号字面量不被误当占位符")
    void castsAndQuotedLiteralsAreNotPlaceholders() {
        QueryExecutionServiceImpl.BoundSql b = QueryExecutionServiceImpl.bindNamedParameters(
                "SELECT reltuples::bigint, '10:30' AS t, \"weird:col\" FROM x WHERE id = :id",
                Map.of("id", 7L));

        assertEquals("SELECT reltuples::bigint, '10:30' AS t, \"weird:col\" FROM x WHERE id = ?",
                b.sql());
        assertEquals(List.of(7L), b.values());
    }

    @Test
    @DisplayName("未登记 key 的 :name 保持原样：不绑 NULL（静默空结果＝越权面）")
    void unknownPlaceholderIsLeftUnbound() {
        QueryExecutionServiceImpl.BoundSql b = QueryExecutionServiceImpl.bindNamedParameters(
                "SELECT 1 FROM x WHERE a = :known AND b = :missing", Map.of("known", "k"));

        assertEquals("SELECT 1 FROM x WHERE a = ? AND b = :missing", b.sql());
        assertEquals(List.of("k"), b.values(), "未知占位符不得占一个绑定位");
    }

    @Test
    @DisplayName("同名占位符重复出现按出现序重复绑定；空参数表 SQL 逐字不变")
    void repeatedPlaceholderBindsPerOccurrenceAndEmptyParamsAreNoop() {
        QueryExecutionServiceImpl.BoundSql rep = QueryExecutionServiceImpl.bindNamedParameters(
                "SELECT 1 FROM x WHERE a = :v OR b = :v", Map.of("v", "z"));
        assertEquals("SELECT 1 FROM x WHERE a = ? OR b = ?", rep.sql());
        assertEquals(List.of("z", "z"), rep.values());

        String plain = "SELECT id FROM t WHERE tenant_id = 'a''b' LIMIT 3";
        QueryExecutionServiceImpl.BoundSql empty =
                QueryExecutionServiceImpl.bindNamedParameters(plain, Collections.emptyMap());
        assertEquals(plain, empty.sql(), "无参数时不得改写 SQL 一个字符");
        assertTrue(empty.values().isEmpty());

        QueryExecutionServiceImpl.BoundSql nullIn =
                QueryExecutionServiceImpl.bindNamedParameters(null, Map.of("a", 1));
        assertNull(nullIn.sql(), "空 SQL 输入必须原样透传（不伪造语句）");
        assertThrows(UnsupportedOperationException.class, () -> nullIn.values().add("x"),
                "绑定表必须是不可变视图（防执行后被下游改写）");
    }

    // ────────────────────────────────────────────────────────────
    // ③ 反空转：旧形态复发或参数化通道被拆 ⇒ 本类必须变红
    // ────────────────────────────────────────────────────────────
    @Test
    @DisplayName("源码级：执行层不再有『值替换进 SQL』形态，且必须把参数表交给 connector")
    void executorSourceHasNoLiteralSubstitutionLeft() throws IOException {
        String src = Files.readString(Path.of(SERVICE_SRC_REL), StandardCharsets.UTF_8);
        String code = stripComments(src);

        assertFalse(code.contains("formatParamValue"),
                "W31 复发：值格式化器仍在源码中（字面量内联的唯一入口）");
        assertFalse(code.contains("replace(\":\""),
                "W31 复发：命名参数被字符串替换进 SQL 文本");
        assertTrue(code.contains("bindNamedParameters"),
                "执行层必须经命名参数改写器");
        assertTrue(code.contains("executeQueryGuarded") && code.contains("bound.values()"),
                "执行层必须把绑定值交给 JdbcConnector 的参数化通道（否则又退回 Statement 拼接）");
    }

    @Test
    @DisplayName("通道级：JdbcConnector 参数化重载真实存在且以 List 承载绑定值")
    void connectorHasParameterizedGuardedOverloadOnClasspath() throws Exception {
        Method m = JdbcConnector.class.getMethod("executeQueryGuarded",
                String.class, String.class, List.class, int.class, int.class, int.class);
        assertEquals(JdbcConnector.GuardedResult.class, m.getReturnType(),
                "参数化重载必须返回受保护结果（列元数据 + 行 + 触顶标志）");
        assertEquals(List.class, m.getParameterTypes()[2],
                "第 3 位实参必须是绑定值列表（值与语句分离的通道契约）");
        // 5 参旧签名必须仍在（只加不改：既有无参调用方零改动）
        Method legacy = JdbcConnector.class.getMethod("executeQueryGuarded",
                String.class, String.class, int.class, int.class, int.class);
        assertNotNull(legacy);
    }

    // ────────────────────────────────────────────────────────────
    // 夹具
    // ────────────────────────────────────────────────────────────
    private static final class Fixture {
        final QueryExecutionDao dao = mock(QueryExecutionDao.class);
        final DataSourceService dsSvc = mock(DataSourceService.class);
        final JdbcConnector connector = mock(JdbcConnector.class);
        final QueryExecutionServiceImpl service =
                new QueryExecutionServiceImpl(dao, dsSvc, connector);

        Fixture() {
            DataSourceEntity ds = new DataSourceEntity();
            ds.setDatasourceId("ds");
            ds.setDatasourceType("POSTGRESQL");
            ds.setConnectionConfig(CONFIG);
            when(dsSvc.getById(anyString())).thenReturn(ds);
        }

        QueryExecutionServiceImpl service() {
            return service;
        }

        JdbcConnector.GuardedResult guarded() {
            List<Map<String, Object>> rows = new ArrayList<>(
                    List.of(new LinkedHashMap<>(Map.of("id", 1L))));
            return new JdbcConnector.GuardedResult(
                    List.of(new JdbcConnector.ColumnDescriptor("id", "id", "bigint")), rows, false);
        }
    }

    private static int occurrences(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) n++;
        }
        return n;
    }

    /** 剥掉 {@code //} 行注释与 javadoc/块注释，避免文档里的「旧形态」字样被当成代码命中。 */
    private static String stripComments(String src) {
        String noBlock = src.replaceAll("(?s)/\\*.*?\\*/", " ");
        StringBuilder out = new StringBuilder();
        for (String line : noBlock.split("\n")) {
            int c = line.indexOf("//");
            out.append(c >= 0 ? line.substring(0, c) : line).append('\n');
        }
        return out.toString();
    }
}
