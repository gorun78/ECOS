package com.chinacreator.gzcm.engine.security.crypto;

import com.chinacreator.gzcm.engine.security.crypto.impl.DataEncryptionServiceImpl;
import com.chinacreator.gzcm.engine.security.crypto.service.ISecretService;
import com.chinacreator.gzcm.engine.security.crypto.service.impl.KeyManagementServiceFullImpl;
import com.chinacreator.gzcm.engine.security.crypto.service.impl.KeyManagementServiceImpl;
import com.chinacreator.gzcm.engine.security.crypto.service.impl.SecretServiceImpl;
import com.chinacreator.gzcm.engine.security.crypto.kms.InMemoryKeyStore;
import com.chinacreator.gzcm.engine.security.crypto.kms.IKeyStore;
import com.chinacreator.gzcm.engine.security.crypto.kms.JdbcKmsKeyStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Crypto bean definitions (从 SysManRuntimeConfig 迁入，打破 sysman-impl → security-engine-impl 循环依赖).
 *
 * <p>B1（2026-10-07 裁决 A：KEK 本地文件/env）：{@link #iKeyManagementService} 在
 * DataSource + {@code ECOS_MASTER_KEY} 齐备时挂 {@link JdbcKmsKeyStore}（PG 信封持久化）；
 * 缺任一条件退回 {@link InMemoryKeyStore}（standalone 引擎与不注入主密钥的部署态行为不变）。</p>
 */
@Configuration
public class CryptoBeanConfig {

    private static final Logger log = LoggerFactory.getLogger(CryptoBeanConfig.class);

    @Autowired(required = false)
    private DataSource dataSource;

    @Value("${ECOS_MASTER_KEY:}")
    private String masterKey;

    @Bean
    @ConditionalOnMissingBean
    public KeyManagementService keyManagementService() {
        return new KeyManagementServiceFullImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public IKeyManagementService iKeyManagementService() {
        return new KeyManagementServiceImpl(buildKeyStore());
    }

    /** B1 存储后端选择：PG + 主密钥齐备 → JdbcKmsKeyStore；否则内存（fail-closed，不留明文缺口）。 */
    private IKeyStore buildKeyStore() {
        byte[] kek = JdbcKmsKeyStore.dekFromEnv(masterKey);
        if (dataSource != null && kek != null) {
            log.info("KMS 密钥持久化启用：JdbcKmsKeyStore（ecos_control.td_crypto_key + AES-256-GCM 信封）");
            return new JdbcKmsKeyStore(new JdbcTemplate(dataSource), kek);
        }
        if (dataSource == null) {
            log.info("KMS 密钥存储=内存（无 DataSource，standalone 部署态）");
        } else {
            log.info("KMS 密钥存储=内存（ECOS_MASTER_KEY 未注入，fail-closed 不落明文）");
        }
        return new InMemoryKeyStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public IDataEncryptionService dataEncryptionService(IKeyManagementService keyService) {
        return new DataEncryptionServiceImpl(keyService);
    }

    @Bean
    @ConditionalOnMissingBean
    public ISecretService secretService(IKeyManagementService keyService, IDataEncryptionService encryptionService) {
        return new SecretServiceImpl(keyService, encryptionService);
    }
}
