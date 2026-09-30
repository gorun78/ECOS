package com.chinacreator.gzcm.f02;

import com.chinacreator.gzcm.common.data.dto.DataSourceDTO;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.service.DataSourceServiceImpl;
import com.chinacreator.gzcm.engine.security.crypto.IDataEncryptionService;
import com.chinacreator.gzcm.engine.security.crypto.IKeyManagementService;
import com.chinacreator.gzcm.engine.security.crypto.impl.DataEncryptionServiceImpl;
import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import com.chinacreator.gzcm.runtime.core.crypto.SecurityCryptoEgress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.security.Key;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F02-01（W52 数据域 P0 安全）凭证治理契约测试。
 *
 * <p>测 {@link DataSourceServiceImpl}（{@code com.chinacreator.gzcm.engine.data.service}，
 * 根侧 JdbcTemplate 实现）三处契约：写路径把明文密码抽取 → 经真实
 * {@link SecurityCryptoEgress} 加密落 {@code password_enc} 并从 {@code connection_config}
 * 剥离；读路径（getById/listAll）绝不回显明文与密文，仅暴露 {@code credentialPresent} 布尔。</p>
 *
 * <p>放中立包 {@code com.chinacreator.gzcm.f02}：本测需 import security-engine 的
 * {@code DataEncryptionServiceImpl}/{@code IKeyManagementService} 以构造内存 KMS 出口，
 * 若放进 {@code engine.data..} 会触发 data 引擎 CrossEngine ArchUnit 铁律 2.4。
 * 实密文 GCM 形态（IV 独立性）由兄弟测 {@link DatasourceCredentialGcmTripTest} 覆盖。</p>
 */
@DisplayName("F02-01 凭证治理：写剥离明文+落密文，读不回显仅暴露 credentialPresent")
class DatasourceCredentialCipherTest {

    private static final String PLAIN = "S3cr3t-Pw!";
    private static final String KEY_ID = "dataSourcePwd";
    private static final String HOST = "10.0.0.1:5432";
    private static final String MASK = "********";

    private CapturingJdbc jdbc;
    private DataSourceServiceImpl service;

    @BeforeEach
    void setUp() {
        jdbc = new CapturingJdbc();
        jdbc.queryResult = List.of();
        service = new DataSourceServiceImpl(jdbc, Mockito.mock(ConnectorFactory.class));
        InMemoryKeyService kms = new InMemoryKeyService();
        kms.put(KEY_ID, new byte[32]); // 预置 32B AES-256 主密钥，避免触发 createKey（W37 禁自动建钥）
        service.setCryptoEgress(egress(kms));
    }

    @Test
    @DisplayName("register 写入：明文从 connection_config 剥离，密文落 password_enc 列")
    void registerStripsPlainAndStoresCipher() {
        jdbc.queryResult = List.of(); // register 末尾 getById 返回 null（不触发 asyncTrigger）
        DataSourceDTO dto = new DataSourceDTO();
        dto.setDatasourceName("pg-a");
        dto.setDatasourceType("POSTGRESQL");
        dto.setConnectionConfig("{\"host\":\"" + HOST + "\",\"username\":\"u\",\"password\":\"" + PLAIN + "\"}");

        service.register(dto);

        assertEquals(1, jdbc.updates.size(), "register 必须落一行");
        String[] args = jdbc.updates.get(0);
        String json = firstContaining(args, "\"host\"");
        assertNotNull(json, "insert 参数应含 JSON 载荷");
        assertFalse(json.contains(PLAIN), "落库 connection_config 不得含明文口令");
        assertFalse(json.contains("\"password\""), "落库 connection_config 不得含 password 键");
        // password_enc 列应为真实密文（Base64，非明文）且非空 · 位于 insert 参数末位
        String enc = args[args.length - 1];
        assertNotNull(enc, "password_enc 列必须携带密文");
        assertFalse(enc.contains(PLAIN), "密文不得回显明文");
    }

    @Test
    @DisplayName("getById：不回显明文/密文，credentialPresent=true，config 密码字段掩码")
    void getByIdEchoesPresentButNoSecret() {
        String cipher = "Base64CipherSentinel";
        DataSourceEntity stored = row("ds-A",
                "{\"host\":\"" + HOST + "\",\"username\":\"u\",\"password\":\"" + PLAIN + "\"}", cipher);
        jdbc.queryResult = List.of(stored);

        DataSourceEntity out = service.getById("ds-A");
        assertNotNull(out);
        assertNull(out.getPasswordEncrypted(), "读路径必须置空密文");
        assertTrue(out.isCredentialPresent(), "有密文时 credentialPresent=true");
        assertNotNull(out.getConnectionConfig());
        assertFalse(out.getConnectionConfig().contains(PLAIN), "响应不含明文");
        assertFalse(out.getConnectionConfig().contains(cipher), "响应不含密文");
        assertTrue(out.getConnectionConfig().contains(MASK), "响应密码字段应为掩码");
    }

    @Test
    @DisplayName("getById：password_enc 为空 → credentialPresent=false 且不抛")
    void getByIdWithoutCipherReportsFalse() {
        jdbc.queryResult = List.of(row("ds-B", "{\"host\":\"" + HOST + "\",\"username\":\"u\"}", null));
        DataSourceEntity out = service.getById("ds-B");
        assertNotNull(out);
        assertFalse(out.isCredentialPresent());
        assertNull(out.getPasswordEncrypted());
    }

    @Test
    @DisplayName("listAll：与 getById 同口径脱敏")
    void listAllEchoesSameGovernance() {
        jdbc.queryResult = List.of(row("ds-A",
                "{\"host\":\"" + HOST + "\",\"username\":\"u\",\"password\":\"" + PLAIN + "\"}",
                "Base64CipherSentinel"));
        List<DataSourceEntity> out = service.listAll();
        assertEquals(1, out.size());
        DataSourceEntity e = out.get(0);
        assertNull(e.getPasswordEncrypted());
        assertTrue(e.isCredentialPresent());
        assertFalse(e.getConnectionConfig().contains(PLAIN));
        assertTrue(e.getConnectionConfig().contains(MASK));
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static String firstContaining(String[] args, String needle) {
        for (String a : args) {
            if (a != null && a.contains(needle)) return a;
        }
        return null;
    }

    private static DataSourceEntity row(String id, String cfg, String enc) {
        DataSourceEntity e = new DataSourceEntity();
        e.setDatasourceId(id);
        e.setDatasourceName("pg-row");
        e.setDatasourceType("POSTGRESQL");
        e.setConnectionConfig(cfg);
        e.setPasswordEncrypted(enc);
        e.setStatus("active");
        return e;
    }

    private static SecurityCryptoEgress egress(InMemoryKeyService kms) {
        IDataEncryptionService enc = new DataEncryptionServiceImpl(kms);
        StaticApplicationContext ctx = new StaticApplicationContext();
        ctx.getBeanFactory().registerSingleton("dataEncryptionService", enc);
        ctx.getBeanFactory().registerSingleton("iKeyManagementService", kms);
        ctx.refresh();
        return new SecurityCryptoEgress(ctx);
    }

    /** 内存 KMS：seed 32B AES-256，禁 createKey（与生产 KMS 主密钥路径隔离）。 */
    static final class InMemoryKeyService implements IKeyManagementService {
        private final Map<String, Key> map = new ConcurrentHashMap<>();

        void put(String id, byte[] bytes) {
            map.put(id, new javax.crypto.spec.SecretKeySpec(bytes, "AES"));
        }

        @Override public Key getKey(String keyId) { return map.get(keyId); }

        @Override public byte[] getKeyBytes(String keyId) {
            Key k = map.get(keyId);
            return k == null ? null : k.getEncoded();
        }

        @Override public Key createKey(String keyId, String algorithm, int keySize)
                throws IKeyManagementService.KeyManagementException {
            throw new IKeyManagementService.KeyManagementException("F02 测试内禁止 createKey（W37 禁自动建钥）");
        }
    }

    /** JdbcTemplate 覆写：捕获 update 参数 + 用预置 DataSourceEntity 驱动的 RowMapper stub（无 Mockito varargs）。 */
    static final class CapturingJdbc extends JdbcTemplate {
        final List<String[]> updates = new ArrayList<>();
        List<DataSourceEntity> queryResult = List.of();

        @Override
        public int update(String sql, Object... args) {
            String[] arr = new String[args.length];
            for (int i = 0; i < args.length; i++) {
                arr[i] = args[i] == null ? null : args[i].toString();
            }
            updates.add(arr);
            return 1;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            @SuppressWarnings("unchecked")
            List<T> r = (List<T>) pump(rowMapper);
            return r;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
            @SuppressWarnings("unchecked")
            List<T> r = (List<T>) pump(rowMapper);
            return r;
        }

        @SuppressWarnings("rawtypes")
        private List<Object> pump(RowMapper rowMapper) {
            List<Object> out = new ArrayList<>();
            int i = 0;
            for (DataSourceEntity e : queryResult) {
                ResultSet rs = Mockito.mock(ResultSet.class);
                try {
                    stub(rs, "datasource_id", e.getDatasourceId());
                    stub(rs, "datasource_name", e.getDatasourceName());
                    stub(rs, "datasource_type", e.getDatasourceType());
                    stub(rs, "connection_config", e.getConnectionConfig());
                    stub(rs, "password_enc", e.getPasswordEncrypted());
                    stub(rs, "status", e.getStatus());
                    out.add(rowMapper.mapRow(rs, i++));
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            }
            return out;
        }

        private static void stub(ResultSet rs, String col, String val) throws SQLException {
            org.mockito.Mockito.lenient().when(rs.getString(col)).thenReturn(val);
        }
    }
}
