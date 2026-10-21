package com.chinacreator.gzcm.common.security.sql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PhysicalQueryGuard} 的只读单表判据不变式（#142/B4-3e-16，收口 <b>D-B4-49</b> 任意 SQL 子项）。
 *
 * <p>本类要钉住的不是「解析得准」，而是<b>收窄方向</b>：判据一旦放行，SQL 就会带着
 * {@code table_name} 进属主端点并被当真执行；判据一旦误拒，最多是调用方换一种写法。
 * 两者代价不对称，所以用例分三组：①必须放行的最小形态（单表只读，含限定名与尾分号）；
 * ②必须拒绝的越权形态（DML/DDL、多语句、注释、跨表、子查询、CTE、锁、写文件/休眠函数）；
 * ③结论载荷的机读约定（拒绝必带 {@link PhysicalQueryGuard#PREFIX}、放行必带表名、
 * {@code maxRows} 只向下收）。第 ③ 组是防止"拒了但调用方读不懂"的假收口。</p>
 */
class PhysicalQueryGuardTest {

    // ── ① 必须放行 ────────────────────────────────────

    @Test
    @DisplayName("放行：单表 SELECT（含列清单与 LIMIT），表名按小写回填")
    void allowsPlainSingleTableSelect() {
        PhysicalQueryGuard.Decision d = PhysicalQueryGuard.inspect("SELECT id, name FROM ecos_datasource LIMIT 10");
        assertTrue(d.allowed(), d.reason());
        assertEquals("ecos_datasource", d.tableName());
        assertNull(d.reason());
    }

    @Test
    @DisplayName("放行：schema 限定名原样保留（属主侧按它定位策略，不能只回末段表名）")
    void allowsSchemaQualifiedTable() {
        // 用合成 schema 名而非业务域真表名：非 data-engine 文件出现 ecos_dw 限定名会撞
        // data-engine 侧横切门 BusinessDomainWriteArchTest#noEcosDwOutsideDataEngine（该门只看字面量）
        PhysicalQueryGuard.Decision d = PhysicalQueryGuard.inspect("select * from DW_Sales.Order_Detail limit 5");
        assertTrue(d.allowed(), d.reason());
        assertEquals("dw_sales.order_detail", d.tableName());
    }

    @Test
    @DisplayName("放行：单条语句的尾分号被剥掉，与无分号同判")
    void allowsTrailingSemicolon() {
        assertTrue(PhysicalQueryGuard.inspect("SELECT id FROM ecos_catalog_item;").allowed());
    }

    @Test
    @DisplayName("不误杀：列名含关键字子串（created_at/call_id/column_comment）仍放行")
    void doesNotFalseRejectColumnNamesContainingKeywords() {
        PhysicalQueryGuard.Decision d = PhysicalQueryGuard.inspect(
                "SELECT created_at, call_id, column_comment FROM ecos_metadata_field");
        assertTrue(d.allowed(), d.reason());
        assertEquals("ecos_metadata_field", d.tableName());
    }

    // ── ② 必须拒绝 ────────────────────────────────────

    @Test
    @DisplayName("拒绝：非 SELECT 起首（DML/DDL/CTE 同一道门）")
    void rejectsNonSelectHead() {
        assertRejected("UPDATE ecos_datasource SET status = 'x' WHERE id = '1'");
        assertRejected("DROP TABLE ecos_query_log");
        assertRejected("INSERT INTO ecos_catalog_item (id) VALUES ('1')");
        // CTE 起首即 WITH：有意不收的服务面（子查询里的 SELECT 无法定位唯一物理表）
        assertRejected("WITH s AS (SELECT 1) SELECT * FROM ecos_datasource");
    }

    @Test
    @DisplayName("拒绝：多语句（尾分号之外的第二个分号即越权面）")
    void rejectsMultiStatement() {
        assertRejected("SELECT id FROM ecos_datasource; DROP TABLE ecos_query_log");
        assertRejected("SELECT 1 FROM ecos_datasource ; SELECT 2 FROM ecos_datasource");
    }

    @Test
    @DisplayName("拒绝：注释形态（-- / 块注释 / # 都是语句截断的常用绕行面）")
    void rejectsCommentForms() {
        assertRejected("SELECT id FROM ecos_datasource -- where x");
        assertRejected("SELECT /*+ hint */ id FROM ecos_datasource");
        assertRejected("SELECT id FROM ecos_datasource # tail");
    }

    @Test
    @DisplayName("拒绝：整词命中写/锁/副作用关键字（SELECT INTO、FOR UPDATE、pg_sleep、lo_export）")
    void rejectsSideEffectKeywords() {
        assertRejected("SELECT * INTO ecos_backup FROM ecos_datasource");
        assertRejected("SELECT id FROM ecos_datasource FOR UPDATE");
        assertRejected("SELECT pg_sleep(10) FROM ecos_datasource");
        assertRejected("SELECT lo_export(1, '/tmp/x') FROM ecos_datasource");
        assertRejected("SELECT dblink_connect('x') FROM ecos_datasource");
        // dblink_connect 不含任何整词关键字：只有函数前缀判据能拒它 ⇒ 单独钉一句，防那条腿没人守
        assertTrue(PhysicalQueryGuard.inspect("SELECT dblink_connect('x') FROM ecos_datasource")
                        .reason().contains("函数"),
                "dblink_connect 未由函数前缀判据拒掉（词边界判据对它无效，见 DANGEROUS_FUNCTION_PREFIXES）");
    }

    @Test
    @DisplayName("拒绝：跨表形态（JOIN/UNION/INTERSECT/EXCEPT 与 FROM 逗号隐式笛卡尔积）")
    void rejectsMultiTableForms() {
        assertRejected("SELECT a.id FROM ecos_datasource a JOIN ecos_catalog_item b ON a.id = b.id");
        assertRejected("SELECT id FROM ecos_datasource UNION SELECT id FROM ecos_catalog_item");
        assertRejected("SELECT id FROM ecos_datasource EXCEPT SELECT id FROM ecos_catalog_item");
        // 逗号形态最阴：JOIN 词表抓不到，FROM 又只提取到第一张表 ⇒ 不单独钉就是「判成单表、实际两张」
        assertRejected("SELECT * FROM ecos_datasource, ecos_catalog_item");
        assertTrue(PhysicalQueryGuard.inspect("SELECT * FROM DW_Sales.Order_Detail, ecos_datasource WHERE 1 = 1")
                        .reason().contains("逗号"),
                "逗号多表未由 FROM_COMMA_LIST 判住（换成别的原因拒绝也算红，但那条腿就没人钉了）");
    }

    @Test
    @DisplayName("拒绝：子查询/无 FROM 等「提取不到唯一物理表」的形态（fail-closed，不猜表名）")
    void rejectsWhenTableNameIsNotUniquelyDerivable() {
        assertRejected("SELECT 1");
        assertRejected("SELECT * FROM (SELECT id FROM ecos_datasource) t");
    }

    @Test
    @DisplayName("拒绝：空 SQL 与纯空白")
    void rejectsBlankSql() {
        assertRejected(null);
        assertRejected("   ");
    }

    // ── ③ 结论载荷约定 ────────────────────────────────

    @Test
    @DisplayName("机读约定：每条拒绝都带 PREFIX，放行不带表名为 null 的结论")
    void decisionsFollowTheMachineReadableShape() {
        PhysicalQueryGuard.Decision rejected = PhysicalQueryGuard.inspect("DELETE FROM ecos_datasource");
        assertFalse(rejected.allowed());
        assertTrue(rejected.reason().startsWith(PhysicalQueryGuard.PREFIX),
                "拒绝理由未带机读前缀 ⇒ 调用方无法识别拒绝来源: " + rejected.reason());
        assertNull(rejected.tableName());

        PhysicalQueryGuard.Decision allowed = PhysicalQueryGuard.inspect("SELECT id FROM ecos_datasource");
        assertTrue(allowed.allowed());
        assertNotNull(allowed.tableName(), "放行却不回填表名 ⇒ 属主端点会跳过 RLS（table_name 为空即不裁决）");
        assertNull(allowed.reason());
    }

    @Test
    @DisplayName("maxRows 只向下收：非法/缺省回落到缺省值，超上限按上限截，绝不放大")
    void maxRowsIsClampedDownwardsOnly() {
        assertEquals(PhysicalQueryGuard.DEFAULT_MAX_ROWS, PhysicalQueryGuard.clampMaxRows(null));
        assertEquals(PhysicalQueryGuard.DEFAULT_MAX_ROWS, PhysicalQueryGuard.clampMaxRows(0));
        assertEquals(PhysicalQueryGuard.DEFAULT_MAX_ROWS, PhysicalQueryGuard.clampMaxRows(-5));
        assertEquals(PhysicalQueryGuard.DEFAULT_MAX_ROWS, PhysicalQueryGuard.clampMaxRows("10"));
        assertEquals(7, PhysicalQueryGuard.clampMaxRows(7));
        assertEquals(PhysicalQueryGuard.MAX_ROWS_CAP, PhysicalQueryGuard.clampMaxRows(999_999));
    }

    private static void assertRejected(String sql) {
        PhysicalQueryGuard.Decision d = PhysicalQueryGuard.inspect(sql);
        assertFalse(d.allowed(), "本应拒绝却放行: " + sql);
        assertTrue(d.reason().startsWith(PhysicalQueryGuard.PREFIX),
                "拒绝理由未带前缀: " + d.reason());
    }
}
