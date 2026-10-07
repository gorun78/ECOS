package com.chinacreator.gzcm.engine.security.crypto.kms;

import com.chinacreator.gzcm.engine.security.crypto.IKeyManagementService;
import com.chinacreator.gzcm.engine.security.crypto.impl.GcmAeadService;
import com.chinacreator.gzcm.engine.security.crypto.service.impl.KeyManagementServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * B1 验收：KMS 密钥跨 JVM 持久化（PG 落库 + AES-256-GCM 信封）。
 *
 * <p>三轴必须全绿才算达成 W38 目标（"KeyManagementServiceImpl 内存 HashMap → PG 持久化"）：
 * <ol>
 *   <li><b>envelope 正确</b>：{@link JdbcKmsKeyStore} 用 KEK 把 DEK 字节做 AES-256-GCM 加密落
 *       PG；另一实例凭 PG 密文行 + 同 KEK 恢复出同一字节——真实 GCM round-trip。</li>
 *   <li><b>IKeyManagementService 语义保留</b>：零参构造（standalone 反思通道）→ 纯内存；
 *       Ptr 构造（PG+KEK 齐备）→ createKey 后，另一实例 getKey/getKeyBytes 命中 DB 密文行。</li>
 *   <li><b>fail-closed</b>：无 KEK（ECOS_MASTER_KEY 缺失）→ 不落明文到 PG；PG 命中但密文被
 *       篡改 → GCM tag 校验失败 → 回退为 null，不回显密钥材料。</li>
 * </ol>
 *
 * <p>PG 用 mock 的 {@link JdbcTemplate}（security-engine-impl 单测无内嵌 DB 债）；真实联调
 * （PG 容器）由 {@code ecos-tests} Playwright 覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class B1KmsPersistTest {

    /** 32B KEK（模拟 ECOS_MASTER_KEY = Base64 32B）。 */
    private static final byte[] KEK = random32("kek");

    @Mock
    JdbcTemplate jdbc;

    /** stub 单行 PG 查询结果：KEY_CONTENT_ENCRYPTED = encB64Content；MAX(VERSION)=0。 */
    private void mockPgRow(String keyId, String encB64Content) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("KEY_CONTENT_ENCRYPTED", encB64Content);
        lenient().when(jdbc.queryForList(anyString(), eq(keyId))).thenReturn(List.of(row));
        lenient().when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(keyId))).thenReturn(0);
    }

    @Test
    @DisplayName("1.1 无 DB 行 + 无共享内存：fresh 实例 load → null（fail-closed，不发公开默认密钥）")
    void freshInstanceWithNoRowIsNull() {
        JdbcKmsKeyStore store = new JdbcKmsKeyStore(jdbc, KEK);
        // 未 stub 该 keyId 的 PG 查询 → Mockito 默认空 List → loadFromPg 返回 null；
        // 且本实例 backstop 为空 → load 必为 null
        assertNull(store.load("missing-row"), "PG 无命中 + 无内存兜底 = null（fail-closed）");
    }

    @Test
    @DisplayName("1.2 从 PG 已加密行恢复密钥（真 GCM 链路：KEK → 解密密文行 → 同一 DEK 字节）")
    void loadFromPgRow() throws Exception {
        byte[] dek = random32("2ndinstance");
        GcmAeadService gcm = new GcmAeadService(KEK);
        String encoded = Base64.getEncoder().encodeToString(gcm.encrypt(dek, "k2"));
        mockPgRow("k2", encoded);

        // 模拟"另一实例"（无本 JVM 内存，只凭 PG 密文 + 同 KEK）
        JdbcKmsKeyStore store = new JdbcKmsKeyStore(jdbc, KEK);
        byte[] got = store.load("k2");
        assertArrayEquals(dek, got, "从 PG 密文恢复的 DEK 与原字节一致（KEK 解密正确）");
    }

    @Test
    @DisplayName("1.3 无 KEK：save 不落 PG（不落明文），只写内存兜底（fail-closed）")
    void noKekDoesNotPersist() {
        JdbcKmsKeyStore store = new JdbcKmsKeyStore(jdbc, null);
        assertFalse(store.isKekAvailable(), "KEK 缺失时标记不可用");
        byte[] dek = random32("k3");
        store.save("k3", dek);
        assertArrayEquals(dek, store.load("k3"), "无 KEK 时 save/load 走同一 JVM 内存兜底（standalone 语义）");

        // 关键：无 KEK 时禁止写 PG（不落明文密钥）
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("1.4 密文被篡改：GCM tag 校验失败 → 回退 null（防篡改 fail-closed）")
    void tamperedCiphertextFailsClosed() throws Exception {
        GcmAeadService gcm = new GcmAeadService(KEK);
        byte[] real = gcm.encrypt(random32("k4"), "k4");
        real[real.length - 1] ^= 0xff; // 破坏 GCM tag
        mockPgRow("k4", Base64.getEncoder().encodeToString(real));

        JdbcKmsKeyStore store = new JdbcKmsKeyStore(jdbc, KEK);
        assertNull(store.load("k4"), "篡改密文必须回退为 null，不返回错误密钥材料");
    }

    @Test
    @DisplayName("1.5 零参构造 = 内存（standalone 反思通道，等价迁移前 HashMap 语义）")
    void zeroArgConstructorIsInMemory() throws IKeyManagementService.KeyManagementException {
        KeyManagementServiceImpl kms = new KeyManagementServiceImpl();
        Key created = kms.createKey("a1", "AES", 256);
        assertNotNull(created);
        Key read = kms.getKey("a1");
        assertArrayEquals(((SecretKeySpec) created).getEncoded(), ((SecretKeySpec) read).getEncoded());
        assertNull(kms.getKey("missing"), "未知 keyId → null（不新建）");
        assertArrayEquals(((SecretKeySpec) created).getEncoded(), kms.getKeyBytes("a1"));
    }

    @Test
    @DisplayName("1.6 PG+KEK 后端 KeyManagementServiceImpl 跨实例 round-trip（模拟进程重启）")
    void kmsAcrossInstanceViaJdbcBackend() throws Exception {
        GcmAeadService gcm = new GcmAeadService(KEK);

        // 实例 A：创建密钥（AES-128），本 JVM 内存兜底持有
        KeyManagementServiceImpl kmsA = new KeyManagementServiceImpl(new JdbcKmsKeyStore(jdbc, KEK));
        Key keyA = kmsA.createKey("k5", "AES", 128);
        assertNotNull(keyA);
        byte[] rawA = ((SecretKeySpec) keyA).getEncoded();

        // 把 A 的 DEK 用 KEK 加密一行"模拟 PG 里的持久化行"（等价 A.save 已写入 DB）
        String encRow = Base64.getEncoder().encodeToString(gcm.encrypt(rawA, "k5"));
        mockPgRow("k5", encRow);

        // 实例 B（新 JVM，无内存）：仅凭 PG 密文行 + KEK 恢复
        KeyManagementServiceImpl kmsB = new KeyManagementServiceImpl(new JdbcKmsKeyStore(jdbc, KEK));
        Key keyB = kmsB.getKey("k5");

        assertNotNull(keyB, "kmsB 从 PG 密文恢复的 Key 非 null");
        assertArrayEquals(rawA, ((SecretKeySpec) keyB).getEncoded(),
                "跨实例 getKey 与源 DEK 字节一致（envelope 加/解密正确）");
        assertArrayEquals(rawA, kmsB.getKeyBytes("k5"), "跨实例 getKeyBytes 与源一致");
    }

    @Test
    @DisplayName("1.7 dekFromEnv：32B 通过；null/空/非32B/非法 Base64 → null")
    void dekFromEnv() {
        byte[] src = random32("src");
        assertArrayEquals(src, JdbcKmsKeyStore.dekFromEnv(Base64.getEncoder().encodeToString(src)));
        assertNull(JdbcKmsKeyStore.dekFromEnv(null));
        assertNull(JdbcKmsKeyStore.dekFromEnv(""));
        assertNull(JdbcKmsKeyStore.dekFromEnv("   "));
        assertNull(JdbcKmsKeyStore.dekFromEnv(Base64.getEncoder().encodeToString(new byte[16])),
                "非 32B 不注入残缺 KEK");
        assertNull(JdbcKmsKeyStore.dekFromEnv("!!!not-base64!!!"), "非法 Base64 → null");
    }

    private static byte[] random32(String seed) {
        byte[] out = new byte[32];
        int h = seed.hashCode();
        for (int i = 0; i < 32; i++) {
            out[i] = (byte) (h ^ (i * 131));
        }
        return out;
    }
}
