package com.chinacreator.gzcm.engine.security.crypto;

import com.chinacreator.gzcm.engine.security.crypto.service.impl.KeyManagementServiceFullImpl;
import com.chinacreator.gzcm.engine.security.crypto.service.impl.KeyManagementServiceImpl;
import com.chinacreator.gzcm.engine.security.crypto.service.impl.SecretServiceImpl;
import com.chinacreator.gzcm.engine.security.crypto.IDataEncryptionService;
import com.chinacreator.gzcm.engine.security.crypto.impl.DataEncryptionServiceImpl;
import com.chinacreator.gzcm.engine.security.crypto.service.ISecretService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/**
 * Crypto bean definitions (从 SysManRuntimeConfig 迁入，打破 sysman-impl → security-engine-impl 循环依赖).
 */
@Configuration
public class CryptoBeanConfig {

    @Bean
    @ConditionalOnMissingBean
    public KeyManagementService keyManagementService() {
        return new KeyManagementServiceFullImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public IKeyManagementService iKeyManagementService() {
        return new KeyManagementServiceImpl();
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
