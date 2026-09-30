package com.chinacreator.gzcm.engine.security.crypto;

import java.security.Key;

/**
 * 密钥管理服务接口。
 *
 * <p>详细设计-01 W37（P0）：主密钥<b>只读</b>自 {@code ECOS_MASTER_KEY}(env) / KMS。
 * {@code createKey} 仅保留给运维侧显式建钥入口，<b>加解密路径不得再自动建钥</b>——
 * 缺钥一律抛 {@link KeyNotFoundException}（端点映射 500 {@code ECOS-SEC-430}）。</p>
 */
public interface IKeyManagementService {

    /** 默认主密钥逻辑 id（{@code ECOS_MASTER_KEY} env 对应名）。 */
    String DEFAULT_KEY_ID = "master";

    /**
     * 获取密钥
     */
    Key getKey(String keyId) throws KeyManagementException;

    /**
     * 获取密钥字节数组
     */
    byte[] getKeyBytes(String keyId) throws KeyManagementException;

    /**
     * 创建密钥（仅运维显式入口；加解密链路禁止隐式调用）
     */
    Key createKey(String keyId, String algorithm, int keySize) throws KeyManagementException;

    /**
     * 密钥管理异常
     */
    class KeyManagementException extends Exception {
        private static final long serialVersionUID = 1L;

        public KeyManagementException(String message) {
            super(message);
        }

        public KeyManagementException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 详细设计-01 W37（P0）— 主密钥缺失异常。
     * 替代旧"缺钥静默建钥"路径：写/读敏感列缺主密钥一律抛本异常，
     * 端点映射 500 {@code ECOS-SEC-430}。<b>永不自动生成密钥</b>。
     */
    class KeyNotFoundException extends KeyManagementException {
        private static final long serialVersionUID = 1L;
        private final String keyId;
        /** D.5.1 错误码（端点 500）。 */
        public static final String ERROR_CODE = "ECOS-SEC-430";

        public KeyNotFoundException(String keyId) {
            super("密钥不存在且禁止自动建钥（" + ERROR_CODE + "）: keyId=" + keyId);
            this.keyId = keyId;
        }

        public String getKeyId() { return keyId; }
        /** 端点映射用错误码。 */
        public String getErrorCode() { return ERROR_CODE; }
    }
}
