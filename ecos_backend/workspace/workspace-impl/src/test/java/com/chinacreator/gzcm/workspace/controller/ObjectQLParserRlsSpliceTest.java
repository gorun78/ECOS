package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.workspace.controller.ObjectQLParser.ParsedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ObjectQL 页面的 RLS 参数序拼接验收（#142/B4-3c-2，差距 D-B4-17 的消费者一半）。
 *
 * <p>生产者发的执行契约是「带 {@code ?} 的片段 + 同序参数」。片段注入点落在
 * ORDER BY/LIMIT/OFFSET <b>之前</b>，所以 JDBC 绑定序＝"插入点前的原参数 + RLS 参数 +
 * 插入点后的原参数"，不是"原参数表 + RLS 参数尾接"。尾接会让 RLS 值绑到 LIMIT 上、
 * 把分页值绑到 {@code department_id} 上——SQL 语法合法、结果却是错的（超发或空集），
 * 这类错误不会被任何断言发现，只能靠本类钉住顺序。</p>
 *
 * <p>另一半是"不猜"：参数表与占位符数对不上时抛 {@link IllegalArgumentException}
 * 交由调用方 fail-closed，而不是按某种猜测顺序拼下去。</p>
 */
@DisplayName("ObjectQL：RLS 片段与参数按插入点成序拼接，序对不上即拒")
class ObjectQLParserRlsSpliceTest {

    private static ParsedQuery pq(String sql, Object... params) {
        return new ParsedQuery(sql, List.of(params));
    }

    @Test
    void rlsParamsLandBetweenWhereParamsAndPaginationParams() {
        ParsedQuery origin = pq("SELECT * FROM t_obj WHERE name = ? ORDER BY id LIMIT ? OFFSET ?",
                "张三", 10, 0);

        ParsedQuery out = ObjectQLParser.appendWhereCondition(
                origin, "department_id = ? AND owner = ?", List.of("d-1", "u-1"));

        assertTrue(out.getSql().contains("WHERE name = ? AND (department_id = ? AND owner = ?)"),
                "RLS 段必须以 AND 追加进既有 WHERE: " + out.getSql());
        // 绑定序 = 插入点前(name) → RLS(dept, owner) → 插入点后(LIMIT, OFFSET)
        assertEquals(List.of("张三", "d-1", "u-1", 10, 0), out.getParams(),
                "参数序错位会把值绑到别的列/分页上（语法对、结果错）");
        // 5 个占位符：name(1) + RLS(dept,owner)(2) + LIMIT/OFFSET(2)；断言写 4 会把正确实现判红
        assertEquals(5, countMarks(out.getSql()));
        assertEquals(out.getParams().size(), countMarks(out.getSql()), "占位符数必须与参数数相等");
    }

    @Test
    void insertsWhereWhenAbsentAndKeepsLimitParamLast() {
        ParsedQuery origin = pq("SELECT * FROM t_obj LIMIT ?", 5);

        ParsedQuery out = ObjectQLParser.appendWhereCondition(origin, "(org_id = ?)", List.of("o-9"));

        assertTrue(out.getSql().startsWith("SELECT * FROM t_obj WHERE ((org_id = ?))"),
                "无 WHERE 时应插在 LIMIT 之前: " + out.getSql());
        assertEquals(List.of("o-9", 5), out.getParams());
    }

    @Test
    void denyClauseNeedsNoParamsAndKeepsOriginalParams() {
        ParsedQuery origin = pq("SELECT * FROM t_obj WHERE name = ? LIMIT ?", "张三", 10);

        ParsedQuery out = ObjectQLParser.appendWhereCondition(origin, "1=0");

        assertTrue(out.getSql().contains("(1=0)"), "拒绝片段必须真的拼进 WHERE（否则等于没裁决）");
        assertEquals(List.of("张三", 10), out.getParams(), "无占位符片段不得改动原参数表");
    }

    @Test
    void paramMismatchAcrossSplicePointRejectsInsteadOfGuessing() {
        // SQL 只有 1 个 ? 而参数表有 2 个：无法保证插入点前后切分正确 → 不猜，直接拒
        ParsedQuery broken = pq("SELECT * FROM t_obj WHERE name = ? LIMIT ?", "张三");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ObjectQLParser.appendWhereCondition(broken, "org_id = ?", List.of("o-1")));
        assertTrue(ex.getMessage().contains("参数表与占位符不同序"), ex.getMessage());
    }

    @Test
    void noParamOverloadRejectsParamBearingFragment() {
        // 兼容出口若接受带 ? 的片段，就是"只拼片段不绑参"的半条 SQL —— 必须拒
        ParsedQuery origin = pq("SELECT * FROM t_obj WHERE name = ?", "张三");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ObjectQLParser.appendWhereCondition(origin, "department_id = ?"));
        assertTrue(ex.getMessage().contains("appendWhereCondition(pq, clause, params)"),
                "报错要指向正确出口，禁调用方靠猜: " + ex.getMessage());
    }

    @Test
    void placeholderInsideStringLiteralIsNotCounted() {
        // 字面量里的 ? 不是占位符（与 JDBC 同口径）；按 1 个占位符成序绑定
        ParsedQuery origin = pq("SELECT * FROM t_obj WHERE note = 'a?b' AND name = ?", "张三");

        ParsedQuery out = ObjectQLParser.appendWhereCondition(origin, "org_id = ?", List.of("o-1"));

        assertEquals(List.of("张三", "o-1"), out.getParams());
    }

    private static int countMarks(String sql) {
        int n = 0;
        boolean inSingle = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'') {
                if (inSingle && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                inSingle = !inSingle;
                continue;
            }
            if (c == '?' && !inSingle) n++;
        }
        return n;
    }
}
