package com.chinacreator.gzcm.engine.security.crypto.kms;

import com.chinacreator.gzcm.engine.security.crypto.IKeyManagementService.KeyManagementException;

/**
 * 密钥字节存储后端（B1 / 详细设计-01 W38 · 2026-10-07 裁决：本地文件+KDF 落 PG 持久化）。
 *
 * <p>实现方负责「密钥字节跨 JVM 重启存活」的全部细节（如 {@link JdbcKmsKeyStore} 用
 * PBKDF2 派生 KEK 把 DEK 字节做 AES-256-GCM 信封加密后落 {@code ecos_control.td_crypto_key}）；
 * {@code KeyManagementServiceImpl} 只按接口契约 load/save，不关心后端是内存还是 PG。
 *
 * <p>实现契约：
 * <ul>
 *   <li>{@link #load} 不存在 → null（不抛）；加载字节若不合法/被篡改 → null（fail-closed，
 *       上层缺钥拒绝）——<b>本层不回显异常细节</b>，只打日志。</li>
 *   <li>{@link #save} 允许实现「best-effort 降级」：若后端不可用（如表未迁移）可 WARN 后只写
 *       内存兜底，不抛——避免把「表缺失」这类非密钥错误静默升级为密钥创建失败。</li>
 * </ul>
 */
public interface IKeyStore {

    /** 读回密钥原始字节；不存在 → null。永不抛。 */
    byte[] load(String keyId) throws KeyManagementException;

    /** 持久化（或至少内存兜底）密钥字节。 */
    void save(String keyId, byte[] keyBytes) throws KeyManagementException;
}
