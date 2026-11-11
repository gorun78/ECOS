package com.chinacreator.gzcm.runtime.core.database.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * iBATIS SQL Map 的正文提取门（CDATA 场景）。
 *
 * <p><b>缺陷实况（2026-11-11 活体实测）</b>：解析器此前只收 {@code TEXT_NODE}，而本仓
 * {@code AuditLog-sql.xml} 把整段 INSERT 包在 {@code <![CDATA[…]]>} 里（iBATIS 惯例）。
 * 结果 SQL 取成空串，运行期才以 {@code SQL cannot be null or empty} 暴露，
 * 表现为「审计行静默不落库」——CLI 侧则看到 trace_id 对不上账。</p>
 *
 * <p><b>为什么用活体而不是单测先发现</b>：单测若只断言「map 有 key」，空串同样能通过；
 * 本门因此断言**正文内容**而非键存在性。</p>
 *
 * @author ECOS CLI-PROD-C
 */
class IbatisSqlCdataExtractionTest {

    private static ByteArrayInputStream xml(String body) {
        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("CDATA 包裹的 SQL 必须被完整取出（缺陷复现点）")
    void cdataBodyIsExtracted() throws Exception {
        Map<String, String> map = SystemDatabaseAccessImpl.parseIbatisForTest(xml(
                "<sqlMap namespace=\"AuditLog\">"
                        + "<insert id=\"insertAuditLog\"><![CDATA[INSERT INTO td_audit_log (log_id) VALUES (#[logId])]]></insert>"
                        + "</sqlMap>"));
        String sql = map.get("AuditLog.insertAuditLog");
        assertTrue(sql != null && !sql.isBlank(), "CDATA 正文被解析成空串（历史缺陷回归）: " + sql);
        assertTrue(sql.contains("INSERT INTO td_audit_log"), "正文缺失表名: " + sql);
        assertTrue(sql.contains("#[logId]"), "正文缺失占位符: " + sql);
    }

    @Test
    @DisplayName("纯文本正文仍可取（不得为修 CDATA 而回归 TEXT 场景）")
    void plainTextBodyStillWorks() throws Exception {
        Map<String, String> map = SystemDatabaseAccessImpl.parseIbatisForTest(xml(
                "<sqlMap namespace=\"N\"><select id=\"s\">SELECT 1</select></sqlMap>"));
        assertEquals("SELECT 1", map.get("N.s"));
    }

    @Test
    @DisplayName("CDATA 与文本混排：两段都参与拼接")
    void mixedCdataAndTextConcatenate() throws Exception {
        Map<String, String> map = SystemDatabaseAccessImpl.parseIbatisForTest(xml(
                "<sqlMap namespace=\"N\"><select id=\"m\">SELECT a <![CDATA[FROM t WHERE x < 1]]></select></sqlMap>"));
        String sql = map.get("N.m");
        assertTrue(sql.startsWith("SELECT a"), "文本段丢失: " + sql);
        assertTrue(sql.contains("FROM t WHERE x < 1"), "CDATA 段丢失: " + sql);
    }

    @Test
    @DisplayName("本条门禁针对的是「空串也能过」的历史盲区：正文为空必须可判定")
    void blankBodyIsDetectable() throws Exception {
        Map<String, String> map = SystemDatabaseAccessImpl.parseIbatisForTest(xml(
                "<sqlMap namespace=\"N\"><select id=\"e\"></select></sqlMap>"));
        String sql = map.get("N.e");
        assertFalse(sql != null && !sql.isBlank(), "空正文不应被当成有效 SQL: " + sql);
    }
}
