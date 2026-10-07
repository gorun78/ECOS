package com.chinacreator.gzcm.engine.security.crypto.kms;

import com.chinacreator.gzcm.engine.security.crypto.IKeyManagementService.KeyManagementException;
import com.chinacreator.gzcm.engine.security.crypto.impl.GcmAeadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * PG 持久化密钥存储后端（B1 / 详细设计-01 W38 · 2026-10-07 裁决 A：KEK 本地，无外部 KMS）。
 *
 * <p>信封（envelope）模型：
 * <ul>
 *   <li><b>DEK</b>（数据加密密钥，业务用）由 {@code KeyManagementServiceImpl.createKey} 生成，
 *       经 {@link GcmAeadService} AES-256-GCM 信封加密后落 {@code ecos_control.td_crypto_key.KEY_CONTENT_ENCRYPTED}。</li>
 *   <li><b>KEK</b> = {@code ECOS_MASTER_KEY}（env，Base64=32B，与 {@code GcmAeadService}/
 *       {@code DataEncryptionServiceImpl} 同一主密钥源），进程内直接使用（GCM 每次随机 IV，
 *       DEK 间天然隔离，无需再派生）。</li>
 * </ul>
 *
 * <p><b>best-effort 持久化</b>：PG 表不可用（如 V165 未迁移）时 save 落 WARN 并只写 JVM 内
 * 内存兜底，不抛——「表缺失」是非密钥错误，不应使密钥创建失败；本 JVM 读仍命中兜底。</p>
 *
 * <p><b>fail-closed 读</b>：load 优先 PG；命中但 KEK 缺失或密文校验失败（篡改/存量 ECB）→
 * 回退内存兜底，均无 → null。任何情况<b>不回显密文/异常细节</b>，只打在应用日志。</p>
 */
public final class JdbcKmsKeyStore implements IKeyStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcKmsKeyStore.class);

    /**
     * 目标表。表名 lowercase（建表 DDL 未加引号），可直接裸写；
     * 但列名以双引号字面量定义（uppercase，字面保留大小写），SQL 里必须加双引号才匹配。
     * 参见 {@code td_crypto_key_pkey} = ("KEY_ID","VERSION") 双引号定义的证据。
     */
    private static final String TABLE = "ecos_control.td_crypto_key";

    private final JdbcTemplate jdbc;
    private final GcmAeadService gcm;
    private final boolean kekAvailable;
    /** 本 JVM 内存兜底：PG 读不到 / 未迁移时保证同 JVM 内 create→get 仍可 round-trip。 */
    private final InMemoryKeyStore backstop = new InMemoryKeyStore();

    /**
     * @param jdbc      目标数据源（指向 sys_man；SQL 以 schema 限定名 {@code ecos_control.td_crypto_key} 解析）
     * @param kekBytes  信封主密钥 32B（Base64 解码后的 {@code ECOS_MASTER_KEY}）；null/非 32B = 不可用
     */
    public JdbcKmsKeyStore(JdbcTemplate jdbc, byte[] kekBytes) {
        this.jdbc = jdbc;
        this.kekAvailable = kekBytes != null && kekBytes.length == 32;
        this.gcm = new GcmAeadService(this.kekAvailable ? kekBytes : null);
        if (!this.kekAvailable) {
            log.warn("KMS KEK 未注入（ECOS_MASTER_KEY 缺失/非 32B）：JdbcKmsKeyStore 不落明文，仅内存兜底（fail-closed）");
        }
    }

    /**
     * @param masterKeyBase64 {@code ECOS_MASTER_KEY} 原值（Base64 32B）
     * @return 解码后的 32B KEK；null/非法 Base64 → null
     */
    public static byte[] dekFromEnv(String masterKeyBase64) {
        if (masterKeyBase64 == null || masterKeyBase64.isBlank()) {
            return null;
        }
        try {
            byte[] kb = Base64.getDecoder().decode(masterKeyBase64.trim());
            return kb.length == 32 ? kb : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 解析后的 KEK 是否可用（诊断/测试）。 */
    public boolean isKekAvailable() {
        return kekAvailable;
    }

    @Override
    public byte[] load(String keyId) {
        byte[] fromPg = loadFromPg(keyId);
        if (fromPg != null) {
            return fromPg;
        }
        return backstop.load(keyId);
    }

    private byte[] loadFromPg(String keyId) {
        if (!kekAvailable) {
            return null;
        }
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT \"KEY_CONTENT_ENCRYPTED\" FROM " + TABLE
                            + " WHERE \"KEY_ID\" = ? AND \"STATUS\" = 'ACTIVE' ORDER BY \"VERSION\" DESC LIMIT 1",
                    keyId);
            if (rows.isEmpty()) {
                return null;
            }
            Object enc = rows.get(0).get("KEY_CONTENT_ENCRYPTED");
            if (enc == null) {
                return null;
            }
            byte[] raw = Base64.getDecoder().decode(enc.toString());
            return gcm.decrypt(raw, keyId);
        } catch (DataAccessException e) {
            // 表缺失/不可用 → 视为无 PG 结果（交给内存兜底），不抛
            log.warn("KMS PG 读取失败（回退内存兜底）: keyId={}, err={}", keyId, e.getMessage());
            return null;
        } catch (IllegalArgumentException e) {
            log.warn("KMS 密文非合法 Base64（回退内存兜底）: keyId={}, err={}", keyId, e.getMessage());
            return null;
        } catch (GcmAeadService.AeadDecryptException tamper) {
            log.warn("KMS 密文校验失败（旧 ECB/篡改，fail-closed 回退）: keyId={}", keyId);
            return null;
        } catch (Exception e) {
            log.warn("KMS 解密失败（回退内存兜底）: keyId={}, err={}", keyId, e.getMessage());
            return null;
        }
    }

    @Override
    public void save(String keyId, byte[] keyBytes) {
        // 先写内存兜底（同 JVM 立即可读，即便 PG 写入被跳过）
        backstop.save(keyId, keyBytes);

        if (!kekAvailable) {
            return; // 无 KEK 不落明文（fail-closed）
        }
        try {
            byte[] encrypted = gcm.encrypt(keyBytes, keyId);
            String encB64 = Base64.getEncoder().encodeToString(encrypted);
            Integer maxVer = jdbc.queryForObject(
                    "SELECT COALESCE(MAX(\"VERSION\"), 0) FROM " + TABLE + " WHERE \"KEY_ID\" = ?",
                    Integer.class, keyId);
            int nextVer = (maxVer == null ? 0 : maxVer) + 1;
            jdbc.update(
                    "INSERT INTO " + TABLE
                            + " (\"KEY_ID\", \"KEY_TYPE\", \"ALGORITHM\", \"VERSION\", \"KEY_SIZE\", \"KEY_CONTENT_ENCRYPTED\", \"MASTER_KEY_ID\", \"STATUS\", \"CREATED_TIME\", \"UPDATED_TIME\", \"CREATED_BY\", \"UPDATED_BY\") "
                            + "VALUES (?, 'DEK', 'AES', ?, ?, ?, 'local-kek', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'kms-persist', 'kms-persist') "
                            + "ON CONFLICT (\"KEY_ID\", \"VERSION\") DO UPDATE SET "
                            + "\"KEY_CONTENT_ENCRYPTED\" = EXCLUDED.\"KEY_CONTENT_ENCRYPTED\", \"STATUS\" = 'ACTIVE', \"UPDATED_TIME\" = CURRENT_TIMESTAMP",
                    keyId, nextVer, keyBytes.length * 8, encB64);
        } catch (DataAccessException e) {
            log.warn("KMS PG 写入失败（回退内存兜底）: keyId={}, err={}", keyId, e.getMessage());
        } catch (Exception e) {
            // GCM 加密失败（KEK 异常）/ 持久化运行时错误 → 回退内存兜底，不使密钥创建失败
            log.warn("KMS 持久化失败（回退内存兜底）: keyId={}, err={}", keyId, e.getMessage());
        }
    }
}
