package com.chinacreator.gzcm.f02;

import com.chinacreator.gzcm.common.data.dto.DataSourceDTO;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.dao.DataSourceDao;
import com.chinacreator.gzcm.engine.data.service.DataSourceServiceImpl;
import com.chinacreator.gzcm.engine.security.crypto.IDataEncryptionService;
import com.chinacreator.gzcm.engine.security.crypto.IKeyManagementService;
import com.chinacreator.gzcm.engine.security.crypto.impl.DataEncryptionServiceImpl;
import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import com.chinacreator.gzcm.runtime.core.crypto.SecurityCryptoEgress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.support.StaticApplicationContext;

import java.security.Key;
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
 * <p>测 {@link DataSourceServiceImpl}（{@code com.chinacreator.gzcm.engine.data.service}）
 * 三处契约：写路径把明文密码抽取 → 经真实
 * {@link SecurityCryptoEgress} 加密落 {@code password_enc} 并从 {@code connection_config}
 * 剥离；读路径（getById/listAll）绝不回显明文与密文，仅暴露 {@code credentialPresent} 布尔。</p>
 *
 * <p>放中立包 {@code com.chinacreator.gzcm.f02}：本测需 import security-engine 的
 * {@code DataEncryptionServiceImpl}/{@code IKeyManagementService} 以构造内存 KMS 出口，
 * 若放进 {@code engine.data..} 会触发 data 引擎 CrossEngine ArchUnit 铁律 2.4。
 * 实密文 GCM 形态（IV 独立性）由兄弟测 {@link DatasourceCredentialGcmTripTest} 覆盖。</p>
 *
 * <p>数据访问收口（铁律 §2.5 #1）后，{@link DataSourceServiceImpl} 的 SQL 已下沉
 * {@link DataSourceDao}；本测通过 mock DAO 捕获 {@code register(...)} 落库参数、
 * stub {@code findByDatasourceId/listAll} 驱动读路径，SQL 文本回归由 DAO 契约测覆盖。</p>
 */
@DisplayName("F02-01 凭证治理：写剥离明文+落密文，读不回显仅暴露 credentialPresent")
class DatasourceCredentialCipherTest {

    private static final String PLAIN = "S3cr3t-Pw!";
    private static final String KEY_ID = "dataSourcePwd";
    private static final String HOST = "10.0.0.1:5432";
    private static final String MASK = "********";

    private DataSourceDao dao;
    private DataSourceServiceImpl service;

    @BeforeEach
    void setUp() {
        dao = Mockito.mock(DataSourceDao.class);
        service = new DataSourceServiceImpl(dao, Mockito.mock(ConnectorFactory.class));
        InMemoryKeyService kms = new InMemoryKeyService();
        kms.put(KEY_ID, new byte[32]); // 预置 32B AES-256 主密钥，避免触发 createKey（W37 禁自动建钥）
        service.setCryptoEgress(egress(kms));
        // getById/register 默认读空 → 不触发 asyncTrigger / 返回 null
        Mockito.lenient().when(dao.findByDatasourceId(Mockito.anyString())).thenReturn(List.of());
    }

    @Test
    @DisplayName("register 写入：明文从 connection_config 剥离，密文落 password_enc 列")
    void registerStripsPlainAndStoresCipher() {
        DataSourceDTO dto = new DataSourceDTO();
        dto.setDatasourceName("pg-a");
        dto.setDatasourceType("POSTGRESQL");
        dto.setConnectionConfig("{\"host\":\"" + HOST + "\",\"username\":\"u\",\"password\":\"" + PLAIN + "\"}");

        service.register(dto);

        ArgumentCaptor<String> connCfgCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> encCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(dao).register(Mockito.anyString(), Mockito.eq("pg-a"),
                Mockito.eq("POSTGRESQL"), Mockito.any(), Mockito.any(),
                connCfgCaptor.capture(), Mockito.any(), Mockito.any(),
                Mockito.any(), encCaptor.capture());
        String storedConfig = connCfgCaptor.getValue();
        assertNotNull(storedConfig, "落库 connection_config 不得为 null");
        assertFalse(storedConfig.contains(PLAIN), "落库 connection_config 不得含明文口令");
        assertFalse(storedConfig.contains("\"password\""), "落库 connection_config 不得含 password 键");
        // password_enc 列应为真实密文（Base64，非明文）且非空 · DAO register 末位参数
        String enc = encCaptor.getValue();
        assertNotNull(enc, "password_enc 列必须携带密文");
        assertFalse(enc.contains(PLAIN), "密文不得回显明文");
    }

    @Test
    @DisplayName("getById：不回显明文/密文，credentialPresent=true，config 密码字段掩码")
    void getByIdEchoesPresentButNoSecret() {
        String cipher = "Base64CipherSentinel";
        DataSourceEntity stored = row("ds-A",
                "{\"host\":\"" + HOST + "\",\"username\":\"u\",\"password\":\"" + PLAIN + "\"}", cipher);
        Mockito.when(dao.findByDatasourceId("ds-A")).thenReturn(List.of(stored));

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
        Mockito.when(dao.findByDatasourceId("ds-B"))
                .thenReturn(List.of(row("ds-B", "{\"host\":\"" + HOST + "\",\"username\":\"u\"}", null)));
        DataSourceEntity out = service.getById("ds-B");
        assertNotNull(out);
        assertFalse(out.isCredentialPresent());
        assertNull(out.getPasswordEncrypted());
    }

    @Test
    @DisplayName("listAll：与 getById 同口径脱敏")
    void listAllEchoesSameGovernance() {
        Mockito.when(dao.listAll()).thenReturn(List.of(row("ds-A",
                "{\"host\":\"" + HOST + "\",\"username\":\"u\",\"password\":\"" + PLAIN + "\"}",
                "Base64CipherSentinel")));
        List<DataSourceEntity> out = service.listAll();
        assertEquals(1, out.size());
        DataSourceEntity e = out.get(0);
        assertNull(e.getPasswordEncrypted());
        assertTrue(e.isCredentialPresent());
        assertFalse(e.getConnectionConfig().contains(PLAIN));
        assertTrue(e.getConnectionConfig().contains(MASK));
    }

    // ── helpers ────────────────────────────────────────────────────────────

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
}
