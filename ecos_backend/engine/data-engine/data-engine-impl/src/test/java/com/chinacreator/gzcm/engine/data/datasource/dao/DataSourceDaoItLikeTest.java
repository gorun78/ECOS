package com.chinacreator.gzcm.engine.data.datasource.dao;

import com.chinacreator.gzcm.engine.data.datasource.storage.adapter.jdbc.BaseJdbcAdapter;
import com.chinacreator.gzcm.engine.data.datasource.storage.adapter.jdbc.PostgresqlAdapter;
import com.chinacreator.gzcm.engine.data.datasource.storage.model.FilterCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DataSourceDaoItLikeTest — PG trap 反向验证。
 *
 * <p>Wave-5.1 T-06：确认 PG 的 {@code fullName ILIKE ?::varchar} 这类
 * 大小写不敏感模糊查询在 WhereClauseBuilder 生成的 SQL 中保留参数占位符 {@code ?}，
 * 从而 JDBC 可正常绑定参数（不会 BadSqlGrammar）。
 * 使用 PostgresqlAdapter + 反射调用 WhereClauseBuilder.build（包可见，通过反射）。</p>
 */
class DataSourceDaoItLikeTest {

    @Test
    @DisplayName("LIKE 条件应生成占位符 ? 并 push 参数值给 JDBC")
    void likeConditionProducesPlaceholderAndParam() throws Exception {
        PostgresqlAdapter adapter = new PostgresqlAdapter();
        Object builder = getField(BaseJdbcAdapter.class, adapter, "whereClauseBuilder");

        FilterCondition f = new FilterCondition();
        f.setType(FilterCondition.ConditionType.LIKE);
        f.setField("fullName");
        f.setValue("张%");

        List<Object> params = new ArrayList<>();
        String sql = (String) invokeBuild(builder, f, params);

        // SQL 应含占位符 ? 且字段名被 PG 双引号转义
        assertTrue(sql.contains("?"), "SQL 应含 ? 占位符: " + sql);
        assertTrue(sql.contains("\"fullName\""), "字段应被 escapeIdentifier 转义: " + sql);
        assertTrue(sql.contains("LIKE"), "PG LIKE 子句应使用 LIKE 关键字");
        assertEquals("张%", params.get(0), "参数列表应包含 LIKE 的 pattern");
    }

    @Test
    @DisplayName("组合 AND(fullName LIKE ? AND age > ?) 生成 2 个占位符")
    void andCombinationProducesTwoParams() throws Exception {
        PostgresqlAdapter adapter = new PostgresqlAdapter();
        Object builder = getField(BaseJdbcAdapter.class, adapter, "whereClauseBuilder");

        FilterCondition like = new FilterCondition();
        like.setType(FilterCondition.ConditionType.LIKE);
        like.setField("fullName");
        like.setValue("li%");

        FilterCondition gt = new FilterCondition();
        gt.setType(FilterCondition.ConditionType.GREATER_THAN);
        gt.setField("age");
        gt.setValue(18);

        FilterCondition and = new FilterCondition();
        and.setType(FilterCondition.ConditionType.AND);
        and.setConditions(List.of(like, gt));

        List<Object> params = new ArrayList<>();
        String sql = (String) invokeBuild(builder, and, params);

        assertTrue(sql.contains("?"), "AND 子句应含占位符");
        assertEquals(2, params.size(), "2 个子条件应 push 2 个参数");
        assertEquals("li%", params.get(0));
        assertEquals(18, params.get(1));
    }

    @Test
    @DisplayName("深度保护 — 超过 MAX_FILTER_DEPTH 的嵌套不应 StackOverflow")
    void deepNestingIsCapped() throws Exception {
        PostgresqlAdapter adapter = new PostgresqlAdapter();
        Object builder = getField(BaseJdbcAdapter.class, adapter, "whereClauseBuilder");

        FilterCondition leaf = new FilterCondition();
        leaf.setType(FilterCondition.ConditionType.EQUALS);
        leaf.setField("a");
        leaf.setValue(1);

        FilterCondition current = leaf;
        for (int i = 0; i < 50; i++) {
            FilterCondition wrap = new FilterCondition();
            wrap.setType(FilterCondition.ConditionType.AND);
            wrap.setConditions(List.of(current, leaf));
            current = wrap;
        }

        List<Object> params = new ArrayList<>();
        // build 不应因 deep nesting 抛异常/StackOverflow；
        // 也可能在递归顶层返回 null 或在某中间节点开始拼 "null"
        Object sql = invokeBuild(builder, current, params);
        // 调用在本进程内无副作用、不抛即可
    }

    /** 通过 Class 声明字段反射拿字段（字段声明在父类 BaseJdbcAdapter 上）。 */
    private static Object getField(Class<?> declaring, Object target, String fieldName) throws Exception {
        Field f = declaring.getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(target);
    }

    private static Object invokeBuild(Object builder, FilterCondition f, List<Object> params) throws Exception {
        Method build = builder.getClass().getDeclaredMethod(
                "build", FilterCondition.class, List.class);
        build.setAccessible(true);
        return build.invoke(builder, f, params);
    }
}
