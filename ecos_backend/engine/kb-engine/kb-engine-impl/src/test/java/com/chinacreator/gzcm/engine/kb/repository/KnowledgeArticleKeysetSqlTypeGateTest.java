package com.chinacreator.gzcm.engine.kb.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 游标键集 SQL 的<b>类型对齐</b>门（#147BP；活体缺陷回归）。
 *
 * <p><b>为什么只有这一门能防住</b>：{@code created_at} 是 {@code timestamp without time zone}，
 * 而 {@code to_timestamp(double)} 返回 {@code timestamptz}。两者直接比较时 PG 按**会话时区**
 * 把 timestamptz 折算成本地墙钟（dev +08），键集条件恒真 ⇒ 第 2 页原地返回第 1 页同一行
 * （活体实测：page2 与 page1 同 id 同游标，而日志确认走的确实是 {@code findByCategoryAfter}）。
 * 单测 mock 掉 Mapper 看不到这条 SQL 的类型语义，真库 psql 直连又容易用同型两边比较而绕开，
 * 故这里直接钉注解里的 SQL 形状：{@code to_timestamp(...)} 必须显式
 * {@code CAST(... AS TIMESTAMP)} 回到墙钟类型。</p>
 *
 * @author ECOS KB #147BP
 */
class KnowledgeArticleKeysetSqlTypeGateTest {

    @Test
    @DisplayName("findByCategoryAfter 的 SQL 必须把 to_timestamp 显式转回 TIMESTAMP（否则分页原地打转）")
    void keysetSqlMustCastToTimestamp() throws Exception {
        Method m = KnowledgeArticleMapper.class.getMethod(
                "findByCategoryAfter", String.class, long.class, String.class, int.class);
        Select select = m.getAnnotation(Select.class);
        assertTrue(select != null, "findByCategoryAfter 必须是注解式 @Select（本门按注解取 SQL）");

        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");
        assertTrue(sql.contains("TIMESTAMP 'epoch'") && sql.contains("INTERVAL '1 second'"), "游标换算必须走与时区无关的 TIMESTAMP 'epoch' + interval 形式（禁 to_timestamp/timestamptz，会话时区会改渲染）: " + sql);
        assertTrue(!sql.contains("to_timestamp("), "禁 to_timestamp(…)：timestamptz 与 timestamp 比较会随会话时区翻向: " + sql);
        assertTrue(sql.contains("date_trunc('milliseconds', created_at + interval '500 microseconds')"),
                "列是微秒精度、游标只承载毫秒：两侧必须统一截断到毫秒，否则游标被四舍五入到下一毫秒、"
                        + "末行再次入选（活体实测原地打转）： " + sql);
        assertTrue(sql.contains("ORDER BY date_trunc('milliseconds', created_at + interval '500 microseconds') DESC, id DESC"),
                "游标页的排序必须与首页逐字一致（同一截断表达式 + id 次级键），否则两页次序错位: " + sql);
    }

    @Test
    @DisplayName("首页 findByCategory 的排序必须与游标页同一表达式（否则第一页与后续页次序错位）")
    void firstPageOrderMatchesKeysetOrder() throws Exception {
        Method m = KnowledgeArticleMapper.class.getMethod("findByCategory", String.class, int.class);
        Select select = m.getAnnotation(Select.class);
        assertTrue(select != null, "findByCategory 必须是注解式 @Select");

        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");
        assertTrue(sql.contains("ORDER BY date_trunc('milliseconds', created_at + interval '500 microseconds') DESC, id DESC"),
                "首页排序须与游标页一致（同截断表达式 + id 次级键）: " + sql);
    }
}
