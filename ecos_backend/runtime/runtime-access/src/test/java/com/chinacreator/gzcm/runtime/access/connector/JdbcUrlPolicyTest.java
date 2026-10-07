package com.chinacreator.gzcm.runtime.access.connector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * R1.3（详细设计-02 W49）JDBC URL 白名单反证集。
 *
 * <p>命中任一危险参数的 URL 必须拒收（抛 {@link IllegalArgumentException}，消息含参数名但不回显 URL 原文）；
 * 正常生产 URL 必须放行。拼装分支（{@code JdbcConnector#buildJdbcUrl} 无 {@code config.jdbcUrl} 时的自产 URL）
 * 不走本类，因而 {@code encrypt=false} / {@code useSSL=false} 等既有拼装默认项不受影响（回归由
 * {@link #accepts_mssqlSystemDefaultParameters} 保护）— 本类只校验用户直传 URL。</p>
 */
@DisplayName("JdbcUrlPolicy 白名单反证")
class JdbcUrlPolicyTest {

    // ─────────────── 拒绝：MySQL 反序列化 / 文件加载 ───────────────

    @Test
    void rejects_mssqlTrustServerCertificateTrue() {
        String url = "jdbc:sqlserver://db.example.com:1433;databaseName=sales;trustServerCertificate=true";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> JdbcUrlPolicy.validate(url));
        assertEquals("连接 URL 含禁止参数 trustservercertificate", ex.getMessage());
    }

    @Test
    void rejects_mssqlEncryptFalse() {
        String url = "jdbc:sqlserver://db.example.com:1433;encrypt=false;databaseName=sales";
        assertThrows(IllegalArgumentException.class, () -> JdbcUrlPolicy.validate(url));
    }

    @Test
    void rejects_mysqlAllowUrlInSelect_true() {
        String url = "jdbc:mysql://db.example.com:3306/prod?allowUrlInSelect=true&useSSL=false";
        assertThrows(IllegalArgumentException.class, () -> JdbcUrlPolicy.validate(url));
    }

    @Test
    void rejects_mysqlAutoDeserialize_true() {
        String url = "jdbc:mysql://db.example.com:3306/prod?autoDeserialize=true";
        assertThrows(IllegalArgumentException.class, () -> JdbcUrlPolicy.validate(url));
    }

    @Test
    void rejects_mysqlAllowLoadLocalInfile_1() {
        String url = "jdbc:mysql://db.example.com:3306/prod?allowLoadLocalInfile=1";
        assertThrows(IllegalArgumentException.class, () -> JdbcUrlPolicy.validate(url));
    }

    @Test
    void rejects_postgresqlSslModeDisable() {
        String url = "jdbc:postgresql://db.example.com:5432/prod?sslmode=disable";
        assertThrows(IllegalArgumentException.class, () -> JdbcUrlPolicy.validate(url));
    }

    @Test
    void rejects_nonJdbcScheme() {
        assertThrows(IllegalArgumentException.class, () -> JdbcUrlPolicy.validate("http://evil.example.com/"));
    }

    @Test
    void rejects_embeddedFileScheme() {
        assertThrows(IllegalArgumentException.class, () -> JdbcUrlPolicy.validate("jdbc:file:/etc/passwd"));
    }

    // ─────────────── 放行：正常 URL / 拼装默认项不受影响（回归保护）───────────────

    @Test
    void accepts_pgNormal() {
        String url = "jdbc:postgresql://db.local:5432/sys_man?connectTimeout=10&socketTimeout=10";
        assertDoesNotThrow(() -> JdbcUrlPolicy.validate(url));
    }

    @Test
    void accepts_mysqlSystemDefaultParameters() {
        // 系统拼装默认 useSSL=false 属"拼装分支"，不进本校验；这里测本类不误伤合法参数值
        String url = "jdbc:mysql://db.local:3306/prod?connectTimeout=10000&socketTimeout=10000";
        assertDoesNotThrow(() -> JdbcUrlPolicy.validate(url));
    }

    @Test
    void accepts_emptyOrNull() {
        assertEquals(null, JdbcUrlPolicy.validate(null));
        assertEquals("", JdbcUrlPolicy.validate(""));
    }

    @Test
    void messageDoesNotLeakUrl() {
        String url = "jdbc:mysql://secret.example.com:3306/prod?autoDeserialize=true&password=hunter2";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> JdbcUrlPolicy.validate(url));
        org.junit.jupiter.api.Assertions.assertFalse(ex.getMessage().contains("secret.example.com"));
        org.junit.jupiter.api.Assertions.assertFalse(ex.getMessage().contains("hunter2"));
        org.junit.jupiter.api.Assertions.assertFalse(ex.getMessage().contains("prod"));
    }
}
