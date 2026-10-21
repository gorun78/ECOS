package com.chinacreator.gzcm.common.security.sql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SqlReadOnlyFloor} 的单源判据不变式（#142/B4-3e-20，收口 <b>D-B4-60</b> 只读判据双源）。
 *
 * <p>本类要钉住的是<b>合流后的口径本身</b>，而不是两侧的历史行为：合流前两套判据各有一批
 * 对方没有的危险关键字，谁也没测过"另一侧的词在我这一侧会不会漏"。因此用例分五组：
 * ①并集关键字（任一侧原缺的词在单源里必须命中，且只按整词命中）；②危险函数按<b>调用位</b>判
 * （既要能抓住旧工具面具名清单之外的 {@code pg_*}，又不能把 {@code pg_class} 这类表名误拒）；
 * ③字面量掩码（合流前只有工具面做，查表面会误拒；单源后两侧都不该误拒）；
 * ④ {@code allowWith} 是两侧唯一差异；⑤掩码<b>不覆盖</b>的残余面显式钉成"仍然拒"，
 * 防止后来者把「误拒可接受」的 fail-closed 取舍当成 bug 顺手放宽。</p>
 *
 * <p>表名一律用合成 schema（不用业务域真表名），避免撞 data-engine 侧横切门。</p>
 */
class SqlReadOnlyFloorTest {

    // ── ① 并集关键字 ────────────────────────────────────

    @Test
    @DisplayName("并集：整词命中即拒，含子串的列名不误杀")
    void wholeWordOnlyNotSubstring() {
        assertTrue(SqlReadOnlyFloor.inspect(
                "SELECT upsert_count, renamed_by, do_flag, comment_text FROM DW_Sales.t", false)
                .allowed());

        assertRejects("SELECT * FROM DW_Sales.t WHERE upsert = 1", "upsert", false);
        assertRejects("SELECT * FROM DW_Sales.t WHERE rename = 1", "rename", false);
        assertRejects("SELECT * FROM DW_Sales.t WHERE do = 1", "do", false);
        assertRejects("SELECT * FROM DW_Sales.t WHERE comment = 1", "comment", false);
    }

    @Test
    @DisplayName("并集：合流前只属于工具面的词（begin/commit/savepoint/reindex/checkpoint 等）在单源里命中")
    void deniesWordsThatOnlyTheToolFaceKnew() {
        for (String word : new String[] {"begin", "commit", "rollback", "savepoint", "prepare",
                "deallocate", "reset", "shutdown", "reindex", "cluster", "listen", "notify",
                "checkpoint"}) {
            assertRejects("SELECT * FROM DW_Sales.t WHERE " + word + " = 1", word, true);
        }
    }

    @Test
    @DisplayName("并集：合流前只属于查表面的词（upsert/rename/comment）命中，两面共有的 into/set/lock 也命中")
    void deniesWordsThatOnlyTheQueryFaceKnew() {
        assertRejects("SELECT * FROM DW_Sales.t INTO bak", "into", true);
        // FOR UPDATE lock_me 同时命中两个词，只钉「命中」这件事而不钉命中顺序（Set 无迭代序）
        assertRejects("SELECT * FROM DW_Sales.t FOR UPDATE lock_me", "非只读关键字", true);
        assertRejects("SELECT * FROM DW_Sales.t SET x = 1", "set", true);
    }

    // ── ② 危险函数：调用位判据 ──────────────────────────

    @Test
    @DisplayName("调用位：pg_sleep( 与具名清单外的 pg_* 一样被拒（修掉旧工具面的漏判那头）")
    void deniesAnyPgNamespacedFunctionAtCallSite() {
        assertRejects("SELECT pg_sleep(5) FROM DW_Sales.t", "pg_sleep(", true);
        assertRejects("SELECT pg_not_in_old_list(1) FROM DW_Sales.t", "pg_not_in_old_list(", true);
        assertRejects("SELECT dblink_connect('x') FROM DW_Sales.t", "dblink_connect(", true);
        assertRejects("SELECT lo_export('a','b') FROM DW_Sales.t", "lo_export(", true);
        assertRejects("SELECT xp_cmdshell('a') FROM DW_Sales.t", "xp_cmdshell(", true);
    }

    @Test
    @DisplayName("调用位：pg_* 作表名／列名不再误拒（修掉旧查表面 contains(\"pg_\") 的那头）")
    void allowsPgNamespacedIdentifiersThatAreNotCalls() {
        SqlReadOnlyFloor.Decision d = SqlReadOnlyFloor.inspect(
                "SELECT relname FROM PG_Catalog.pg_class WHERE relname = 't'", false);
        assertTrue(d.allowed(), d.reason());
        assertEquals(Set.of("pg_catalog.pg_class"), d.tables());

        assertTrue(SqlReadOnlyFloor.inspect(
                "SELECT call_id, created_at, pg_version FROM DW_Sales.t", false).allowed());
    }

    // ── ③ 字面量掩码 ────────────────────────────────────

    @Test
    @DisplayName("掩码：字符串字面量里的关键字不再误拒（合流前查表面自认的粗糙处）")
    void masksLiteralsSoWordsInsideAreNotKeywords() {
        SqlReadOnlyFloor.Decision d = SqlReadOnlyFloor.inspect(
                "SELECT id FROM DW_Sales.t WHERE remark = 'please delete this row'", false);
        assertTrue(d.allowed(), d.reason());
        assertEquals(Set.of("dw_sales.t"), d.tables());
    }

    @Test
    @DisplayName("normalized 是掩码后的小写正文（尾分号已剥），供调用方做面专属扫描")
    void normalizedIsMaskedLoweredBody() {
        SqlReadOnlyFloor.Decision d = SqlReadOnlyFloor.inspect("SELECT id FROM DW_Sales.t;", true);
        assertTrue(d.allowed(), d.reason());
        assertEquals("select id from dw_sales.t", d.normalized());
        assertNull(d.reason());
    }

    // ── ④ allowWith = 两侧唯一差异 ──────────────────────

    @Test
    @DisplayName("差异参数：WITH 起首在工具面放行、在查表面拒绝")
    void withHeadIsTheOnlyDifferenceBetweenFaces() {
        String cte = "WITH c AS (SELECT id FROM DW_Sales.t) SELECT id FROM c";

        assertTrue(SqlReadOnlyFloor.inspect(cte, true).allowed());

        SqlReadOnlyFloor.Decision queryFace = SqlReadOnlyFloor.inspect(cte, false);
        assertFalse(queryFace.allowed());
        assertTrue(queryFace.reason().contains("SELECT 起首"), queryFace.reason());
    }

    @Test
    @DisplayName("公共形态两面同判：空／多语句／注释／非只读头部")
    void bothFacesRejectTheSameNonReadOnlyShapes() {
        assertRejects(null, "SQL 为空", true);
        assertRejects("   ", "SQL 为空", false);
        assertRejects("SELECT 1; SELECT 2", "多语句分隔符", false);
        assertRejects("SELECT 1; SELECT 2", "多语句分隔符", true);
        assertRejects("SELECT id FROM DW_Sales.t /* hide */", "注释形态", true);
        assertRejects("SELECT id FROM DW_Sales.t -- tail", "注释形态", false);
        assertRejects("DELETE FROM DW_Sales.t", "必须 SELECT / WITH 起首", true);
        assertRejects("DELETE FROM DW_Sales.t", "必须 SELECT 起首", false);
    }

    // ── ⑤ 掩码不覆盖的残余面（有意保留的误拒） ──────────

    @Test
    @DisplayName("残余面：分号检查在未掩码正文上做 → 字面量里带分号仍拒（宁可误拒不误放）")
    void semicolonLegStaysOnRawBody() {
        assertRejects("SELECT id FROM DW_Sales.t WHERE remark = 'a; b'", "多语句分隔符", true);
    }

    @Test
    @DisplayName("表提取：FROM/JOIN 都吃、字面量不参与、小写保序去重")
    void extractsTablesFromFromAndJoinOnly() {
        assertEquals(Set.of("dw_sales.a", "dw_sales.b"), SqlReadOnlyFloor.extractTables(
                "SELECT x FROM DW_Sales.A JOIN dw_sales.B ON x = x WHERE y = 'from dw_sales.notatable'"));
        assertEquals(Set.of(), SqlReadOnlyFloor.extractTables("SELECT 1"));
        assertEquals(Set.of(), SqlReadOnlyFloor.extractTables(null));
        // 同一表名重复引用只记一次（属主侧按集合定策略，不按出现次数）
        assertEquals(Set.of("dw_sales.a"), SqlReadOnlyFloor.extractTables(
                "SELECT 1 FROM DW_Sales.a WHERE EXISTS (SELECT 1 FROM dw_sales.A)"));
    }

    private static void assertRejects(String sql, String expectedFragment, boolean allowWith) {
        SqlReadOnlyFloor.Decision d = SqlReadOnlyFloor.inspect(sql, allowWith);
        assertFalse(d.allowed(), "应拒绝但放行了: " + sql);
        assertNotNull(d.reason());
        assertTrue(d.reason().contains(expectedFragment),
                "拒绝理由应含「" + expectedFragment + "」，实际: " + d.reason());
        assertNull(d.tables(), "拒绝时不回填表集，避免调用方拿半截结论");
        assertNull(d.normalized(), "拒绝时不回填正文");
    }
}
