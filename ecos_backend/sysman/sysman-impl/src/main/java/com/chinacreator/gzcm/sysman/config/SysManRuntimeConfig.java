package com.chinacreator.gzcm.sysman.config;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.runtime.core.database.impl.SystemDatabaseAccessImpl;
import com.chinacreator.gzcm.runtime.core.logging.ILoggingService;
import com.chinacreator.gzcm.runtime.core.logging.config.LoggingServiceConfig;

import com.chinacreator.gzcm.runtime.core.crypto.KeyManagementService;
import com.chinacreator.gzcm.runtime.core.crypto.service.impl.KeyManagementServiceFullImpl;
import com.chinacreator.gzcm.runtime.core.crypto.IKeyManagementService;
import com.chinacreator.gzcm.runtime.core.crypto.service.impl.KeyManagementServiceImpl;
import com.chinacreator.gzcm.runtime.core.crypto.IDataEncryptionService;
import com.chinacreator.gzcm.runtime.core.crypto.impl.DataEncryptionServiceImpl;
import com.chinacreator.gzcm.runtime.core.crypto.service.ISecretService;
import com.chinacreator.gzcm.runtime.core.crypto.service.impl.SecretServiceImpl;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import javax.sql.DataSource;

@Configuration
public class SysManRuntimeConfig {

    @Bean
    @ConditionalOnMissingBean
    public ISystemDatabaseAccess systemDatabaseAccess(DataSource dataSource) {
        return new SystemDatabaseAccessImpl(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public ILoggingService loggingService(ISystemDatabaseAccess databaseAccess) {
        return LoggingServiceConfig.createLoggingService(databaseAccess);
    }
    
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
