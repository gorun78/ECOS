package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.data.dto.DataSourceDTO;
import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import com.chinacreator.gzcm.runtime.core.crypto.SecurityCryptoEgress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PMO-74 H8-T-DS-PLAIN — 数据源口令「明文落库 / 明文回显」两处泄漏的收口验证。
 *
 * <p>写入侧：{@code encryptPassword} 在加密出口缺席或加密失败时抛
 * {@link ValidationException}（fail-closed），不再降级返回明文；
 * 读取侧：{@code listAll()} 与 {@code getById()} 同口径走
 * {@code maskProtected}（config 内口令 → {@code ********}，密文列 → null）。</p>
 *
 * <p>含 1 条正向对照（加密可用 → 返回密文）与 1 条 known-0 对照（无口令 → null 不抛），
 * 证明断言本身有效而非恒真。</p>
 */
class DataSourceServiceImplDsPlainTest {

    private static final String PLAIN = "Sup3rS3cr3t-Pwd";
    private static final String CIPHER = "BASE64_CIPHER_TEXT==";
    private static final String KEY_ID = "dataSourcePwd";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ConnectorFactory connectorFactory = mock(ConnectorFactory.class);

    private DataSourceServiceImpl newService() {
        return new DataSourceServiceImpl(jdbc, connectorFactory);
    }

    /** 正向对照：加密出口可用时返回密文，说明本类断言能真的观察到成功路径。 */
    @Test
    @DisplayName("对照-1 加密出口可用 → 返回密文")
    void encryptsThroughEgressWhenAvailable() {
        SecurityCryptoEgress egress = mock(SecurityCryptoEgress.class);
        when(egress.encrypt(PLAIN, KEY_ID)).thenReturn(CIPHER);
        DataSourceServiceImpl svc = newService();
        svc.setCryptoEgress(egress);

        assertEquals(CIPHER, svc.encryptPassword(PLAIN));
    }

    /** known-0 对照：无口令输入不触发加密路径，也不应被 fail-closed 拦住。 */
    @Test
    @DisplayName("对照-2 空白口令 → null 且不抛")
    void blankPasswordIsNoOp() {
        assertNull(newService().encryptPassword("   "));
    }

    @Test
    @DisplayName("加密出口缺席 → 拒绝保存并抛 ValidationException，异常文案不含口令")
    void rejectsSaveWhenEgressAbsent() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> newService().encryptPassword(PLAIN));
        assertFalse(ex.getMessage().contains(PLAIN), "异常文案不得回显口令: " + ex.getMessage());
    }

    @Test
    @DisplayName("加密失败 → 拒绝保存，异常文案不含口令也不含底层异常详情")
    void rejectsSaveWhenEncryptionFails() {
        SecurityCryptoEgress egress = mock(SecurityCryptoEgress.class);
        when(egress.encrypt(anyString(), anyString()))
                .thenThrow(new IllegalStateException("KMS down, key material " + PLAIN));
        DataSourceServiceImpl svc = newService();
        svc.setCryptoEgress(egress);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> svc.encryptPassword(PLAIN));
        assertFalse(ex.getMessage().contains(PLAIN), "异常文案不得含口令: " + ex.getMessage());
        assertFalse(ex.getMessage().contains("KMS down"), "异常文案不得含底层详情: " + ex.getMessage());
    }

    @Test
    @DisplayName("register 带口令且加密不可用 → 抛异常且完全不触库")
    void registerNeverTouchesJdbcWhenCryptoUnavailable() {
        DataSourceDTO dto = new DataSourceDTO();
        dto.setDatasourceName("pg-src");
        dto.setDatasourceType("POSTGRESQL");
        dto.setConnectionConfig("{\"host\":\"10.0.0.1\",\"port\":5432,\"password\":\"" + PLAIN + "\"}");

        assertThrows(ValidationException.class, () -> newService().register(dto));
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("listAll 出口脱敏 → config 口令掩码、host 保留、密文列为 null")
    void listAllMasksPasswordAndCipher() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("datasource_id")).thenReturn("ds-1");
        when(rs.getString("connection_config"))
                .thenReturn("{\"host\":\"10.0.0.1\",\"username\":\"reader\",\"password\":\"" + PLAIN + "\"}");
        when(rs.getString("password_enc")).thenReturn(CIPHER);

        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(inv -> {
            RowMapper<DataSourceEntity> mapper = inv.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });

        List<DataSourceEntity> rows = newService().listAll();
        assertEquals(1, rows.size());
        DataSourceEntity row = rows.get(0);
        assertTrue(row.getConnectionConfig().contains("********"),
                "口令应被掩码: " + row.getConnectionConfig());
        assertFalse(row.getConnectionConfig().contains(PLAIN),
                "列表出口不得含明文口令: " + row.getConnectionConfig());
        assertTrue(row.getConnectionConfig().contains("10.0.0.1"),
                "host 属 owner 域展示字段，不得被误删");
        assertNull(row.getPasswordEncrypted(), "密文列不得外泄");
    }

    /**
     * 差分对照（known-&gt;0）：同一行数据走<b>未脱敏</b>的 mapRow 路径时口令明文可见，
     * 证明上一条用例观察到的"明文消失"来自 maskProtected，而非种子数据本身没含口令。
     */
    @Test
    @DisplayName("对照-3 同一种子行：未脱敏路径明文可见（证明脱敏断言非恒真）")
    void unmaskedMapperPathStillLeaksPlaintext() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("connection_config"))
                .thenReturn("{\"host\":\"10.0.0.1\",\"password\":\"" + PLAIN + "\"}");
        when(rs.getString("password_enc")).thenReturn(CIPHER);

        java.lang.reflect.Method mapRow = DataSourceServiceImpl.class
                .getDeclaredMethod("mapRow", ResultSet.class);
        mapRow.setAccessible(true);
        DataSourceEntity raw = (DataSourceEntity) mapRow.invoke(newService(), rs);

        assertTrue(raw.getConnectionConfig().contains(PLAIN),
                "未脱敏路径应仍可见明文（种子有效性校验）");
        assertEquals(CIPHER, raw.getPasswordEncrypted());
    }

    /**
     * PMO-74 H10-T4b（N-A）：connectionConfig 解析失败时，脱敏不得回显原文（原实现 fail-open）。
     * 输入是非法 JSON 且内嵌明文口令 —— 修复前 {@code return cfgJson} 会把它原样送出。
     */
    @Test
    @DisplayName("H10-T4b 掩码解析失败 → fail-closed 全量掩码，不回显原文")
    void maskFailsClosedOnUnparseableConfig() {
        String broken = "{ host=\"10.0.0.1\" password: \"" + PLAIN + "\"" ;

        String out = newService().maskSensitiveFields(broken);

        assertFalse(out.contains(PLAIN), "fail-closed 后不得含明文口令: " + out);
        assertFalse(out.equals(broken), "不得原样回显入参");
        assertTrue(out.contains("********"), "应返回掩码占位: " + out);
    }

    /** 正向对照（known-&gt;0）：合法 JSON 仍走逐键掩码，host 保留，证明上一条不是恒真分支。 */
    @Test
    @DisplayName("对照-4 合法 JSON → 逐键掩码且 host 保留（fail-closed 未误伤正常路径）")
    void maskStillKeepsStructuredPath() {
        String cfg = "{\"host\":\"10.0.0.1\",\"password\":\"" + PLAIN + "\"}";

        String out = newService().maskSensitiveFields(cfg);

        assertTrue(out.contains("********"), "口令应掩码: " + out);
        assertFalse(out.contains(PLAIN), "明文不得出现: " + out);
        assertTrue(out.contains("10.0.0.1"), "正常路径 host 仍可见: " + out);
    }
}
