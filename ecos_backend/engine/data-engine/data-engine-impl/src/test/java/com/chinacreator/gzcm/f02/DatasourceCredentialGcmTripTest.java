package com.chinacreator.gzcm.f02;

import com.chinacreator.gzcm.engine.security.crypto.IDataEncryptionService;
import com.chinacreator.gzcm.engine.security.crypto.IKeyManagementService;
import com.chinacreator.gzcm.engine.security.crypto.impl.DataEncryptionServiceImpl;
import com.chinacreator.gzcm.runtime.core.crypto.SecurityCryptoEgress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import java.security.Key;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F02-01（W52 数据域 P0 安全）GCM facade 通路验证 — 《DatasourceCredentialCipherTest》的
 * 实密文形态部分（roundtrip + IV 每次写独立 12B），经 data-engine 实际消费的真实通道
 * {@link SecurityCryptoEgress} → {@link DataEncryptionServiceImpl}。
 *
 * <p><b>为何放此包而非 {@code com.chinacreator.gzcm.engine.data.service}</b>：
 * data-engine-impl 的 {@code ArchitectureTest#shouldNotDependOnOtherEngines} 扫描
 * {"code com.chinacreator.gzcm.engine.data.."}，若本测放进 engine.data 包且 import
 * security-engine 类型，即便 security-engine-impl 是 runtime scope 也会在 class-level 依赖
 * 判定上 risk 触发 "engine 不直依 security-engine" 铁律 2.4 违例。放入中立包
 * {@code com.chinacreator.gzcm.f02}（非 engine 包）后 ArchUnit importer 不扫此类，
 * 依赖方向保持干净；security-engine-impl 仅 test classpath 可见（runtime scope），
 * 不改 main 依赖方向、不引入 compile-scope 直依。</p>
 */
@DisplayName("F02-01 GCM 密文形态：经 SecurityCryptoEgress 真实 facade（AES-256-GCM，IV 每写独立）")
class DatasourceCredentialGcmTripTest {

    private static final String PLAIN = "Sup3rS3cr3t-Pwd-ABC-123";
    private static final String KEY_ID = "dataSourcePwd";

    @Test
    @DisplayName("roundtrip 可解 + 同一明文两次加密密文不同 + 12B IV 段每次写独立 + 密文含 version|IV 段")
    void gcmRoundtripAndIvUniquenessViaEgressFacade() {
        InMemoryKeyService kms = new InMemoryKeyService();
        kms.put(KEY_ID, new byte[32]); // 32B AES-256 主密钥（测试内存，不落库/不入日志）
        IDataEncryptionService enc = new DataEncryptionServiceImpl(kms);

        StaticApplicationContext ctx = new StaticApplicationContext();
        ctx.getBeanFactory().registerSingleton("dataEncryptionService", enc);
        ctx.getBeanFactory().registerSingleton("iKeyManagementService", kms);
        ctx.refresh();

        SecurityCryptoEgress egress = new SecurityCryptoEgress(ctx);
        assertTrue(egress.available(), "egress 应能解析到 security 加密 bean（真实 facade 通路生效）");

        String c1 = egress.encrypt(PLAIN, KEY_ID);
        String c2 = egress.encrypt(PLAIN, KEY_ID);
        String roundtrip = egress.decrypt(c1, KEY_ID);

        assertEquals(PLAIN, roundtrip, "AES-256-GCM roundtrip：decrypt(encrypt(plain)) == plain");
        assertNotEquals(c1, c2, "同一明文两次加密密文应不同（随机 IV 前置）");
        assertNotEquals(c1, PLAIN, "密文不得等于明文（真加密，非 pass-through）");

        byte[] b1 = Base64.getDecoder().decode(c1);
        byte[] b2 = Base64.getDecoder().decode(c2);
        // 形态：base64(version(1) | IV(12) | ciphertext||tag)
        assertTrue(b1.length >= 1 + 12 + 1, "密文总长应 ≥ version(1)+IV(12)+tag/cipher");
        assertTrue(b2.length >= 1 + 12 + 1, "密文总长应 ≥ version(1)+IV(12)+tag/cipher");

        boolean ivDiffers = false;
        for (int i = 1; i <= 12; i++) {
            if (b1[i] != b2[i]) {
                ivDiffers = true;
                break;
            }
        }
        assertTrue(ivDiffers, "两次写入的 12B IV 段必须不同（GCM IV 独立性，NIST SP 800-38D）");
    }

    @Test
    @DisplayName("篡改密文 → GCM 认证 tag 失败（decry（非 ECB）")
    void tamperedCiphertextFailsAuth() {
        InMemoryKeyService kms = new InMemoryKeyService();
        kms.put(KEY_ID, new byte[32]);
        IDataEncryptionService enc = new DataEncryptionServiceImpl(kms);
        StaticApplicationContext ctx = new StaticApplicationContext();
        ctx.getBeanFactory().registerSingleton("dataEncryptionService", enc);
        ctx.getBeanFactory().registerSingleton("iKeyManagementService", kms);
        ctx.refresh();
        SecurityCryptoEgress egress = new SecurityCryptoEgress(ctx);

        String c1 = egress.encrypt(PLAIN, KEY_ID);
        byte[] tampered = Base64.getDecoder().decode(c1).clone();
        tampered[tampered.length - 1] ^= 0x01; // 翻 bit
        String bad = Base64.getEncoder().encodeToString(tampered);

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> egress.decrypt(bad, KEY_ID),
                "GCM 认证 tag 校验：篡改密文必须抛（SecurityCryptoEgress 包成 IllegalStateException）");
    }

    /** 内存 KMS：seed 32B AES-256，测试可控（与生产 env/KMS 主密钥路径隔离，不污染 env）。 */
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
                throws KeyManagementException {
            throw new KeyManagementException("F02 测试内禁止 createKey（W37 禁自动建钥）");
        }
    }
}
