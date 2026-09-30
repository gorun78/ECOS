package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import com.chinacreator.gzcm.runtime.core.crypto.SecurityCryptoEgress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * F02-01（W52 数据域 P0 安全）验收《DatasourceLegacyPasswordMigrationTest》。
 *
 * <p>对存量 2 行明文密码跑启动期迁移器（{@link DatasourceLegacyPasswordMigrator}）：
 * <ol>
 *   <li>断言每命中行：{@code password_enc} 写入"加密后"的值（非明文），{@code connection_config}
 *       剥离 {@code password} 键（连同 secret / passwd 等敏感键）；</li>
 *   <li>断言幂等：迁移器选择集以 {@code password_enc IS NULL} 为守门条件，
 *       第二次查询模拟已迁移后 DB 状态 → 迁移器不再写库（0 update）。</li>
 * </ol></p>
 *
 * <p>用 <b>mock JdbcTemplate</b> 而非 @MybatisTest-lite —— 无需真实 DB，纯粹验证"选择/加密/剥离"
 * 三段逻辑及幂等守门条件。</p>
 */
@DisplayName("F02-01 数据源存量明文凭据迁移器")
class DatasourceLegacyPasswordMigrationTest {

    private static final String KEY_ID = "dataSourcePwd";

    /** 样本 1：标准明文 password 键 + 额外 host/username 非敏感字段。 */
    private static final String PENDING_ROW_1_CFG =
            "{\"host\":\"10.0.0.1\",\"port\":5432,\"username\":\"reader\",\"password\":\"PlainPwd1\"}";
    /** 样本 2：极简单 hash 密钥，含 password 键 → 命中。 */
    private static final String PENDING_ROW_2_CFG =
            "{\"host\":\"10.0.0.2\",\"password\":\"PlainPwd2\"}";

    private JdbcTemplate jdbc;
    private DataSourceServiceImpl impl;
    private SecurityCryptoEgress egress;
    private DatasourceLegacyPasswordMigrator migrator;

    @BeforeEach
    void setup() {
        jdbc = mock(JdbcTemplate.class);
        impl = new DataSourceServiceImpl(jdbc, mock(ConnectorFactory.class));
        egress = mock(SecurityCryptoEgress.class);
        // 与现有 DatasourceCredentialCipherTest 一致：idempotent, 同一 (明文, keyId) → 同一"密文"
        when(egress.encrypt(anyString(), anyString())).thenAnswer(
                inv -> {
                    Object p = inv.getArgument(0);
                    return p == null ? null : "CIPHER_of_" + p;
                });
        impl.setCryptoEgress(egress);
        migrator = new DatasourceLegacyPasswordMigrator(jdbc, impl);
    }

    @Test
    @DisplayName("initial：2 行 pending 命中 → 2 次 UPDATE 剥离 password 键 + 写密文到 password_enc")
    void migratesTwoPendingRowsOnce() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                row("ds-A", PENDING_ROW_1_CFG),
                row("ds-B", PENDING_ROW_2_CFG)));

        int migrated = migrator.migrate();
        assertEquals(2, migrated, "两条 pending 行应全部迁移");

        ArgumentCaptor<Object> e1 = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> e2 = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> e3 = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> e4 = ArgumentCaptor.forClass(Object.class);

        // 两次 url=UPDATE ... set password_enc=?,connection_config=?,update_time=? where datasource_id=?
        verify(jdbc, times(2)).update(anyString(),
                e1.capture(), e2.capture(), e3.capture(), e4.capture());

        // 捕获按调用顺序配对
        String encA = (String) e1.getAllValues().get(0);
        String cfgA = (String) e2.getAllValues().get(0);
        String idA  = (String) e4.getAllValues().get(0);
        String encB = (String) e1.getAllValues().get(1);
        String cfgB = (String) e2.getAllValues().get(1);
        String idB  = (String) e4.getAllValues().get(1);

        // 顺序跟 jdbc.queryForList 返回顺序（LinkedHashMap 保序）
        assertEquals("ds-A", idA, "第一次 update 应针对 ds-A");
        assertEquals("ds-B", idB, "第二次 update 应针对 ds-B");

        // 断言 1：密文列不含明文
        assertEquals("CIPHER_of_PlainPwd1", encA, "ds-A password_enc 应写入 encrypt(PlainPwd1)");
        assertEquals("CIPHER_of_PlainPwd2", encB, "ds-B password_enc 应写入 encrypt(PlainPwd2)");

        // 断言 2：connection_config 剥离 password 键，但 host/username 保留
        assertFalse(cfgA.contains("\"password\""), "ds-A config 不得再含 password 键: " + cfgA);
        assertFalse(cfgA.contains("PlainPwd1"),    "ds-A config 不得含明文: " + cfgA);
        assertTrue(cfgA.contains("10.0.0.1"), "ds-A host 应保留: " + cfgA);
        assertTrue(cfgA.contains("reader"),   "ds-A username 应保留: " + cfgA);

        assertFalse(cfgB.contains("\"password\""), "ds-B config 不得再含 password 键: " + cfgB);
        assertFalse(cfgB.contains("PlainPwd2"),    "ds-B config 不得含明文: " + cfgB);
        assertTrue(cfgB.contains("10.0.0.2"), "ds-B host 应保留: " + cfgB);
    }

    @Test
    @DisplayName("idempotent：迁移后查询 0 行 pending → 0 次 UPDATE（守住幂等）")
    void secondRunPerformsZeroWrites() {
        // 第二次查时，DB 里 password_enc 已非空 → 查询返回 0 行
        when(jdbc.queryForList(anyString())).thenReturn(List.of());

        int migrated = migrator.migrate();
        assertEquals(0, migrated, "幂等：第二次 0 行命中");
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("mask-only 行：config 内 password='********' → 不算明文，跳过不写")
    void skipsMaskPlaceholder() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                row("ds-M", "{\"host\":\"10.0.0.99\",\"password\":\"********\"}")));

        int migrated = migrator.migrate();
        assertEquals(0, migrated, "掩码占位不是明文，不应迁移");
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("null guard：config 空白 → 跳过，不触发加密")
    void skipsBlankConfig() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                row("ds-X", "   ")));

        int migrated = migrator.migrate();
        assertEquals(0, migrated);
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any());
        verifyNoMoreInteractions(egress);
    }

    private static Map<String, Object> row(String id, String cfg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("datasource_id", id);
        m.put("connection_config", cfg);
        return m;
    }
}
