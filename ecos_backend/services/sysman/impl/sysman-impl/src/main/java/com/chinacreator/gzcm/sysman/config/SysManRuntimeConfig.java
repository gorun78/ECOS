package com.chinacreator.gzcm.sysman.config;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.runtime.core.database.impl.SystemDatabaseAccessImpl;
import com.chinacreator.gzcm.runtime.core.logging.ILoggingService;
import com.chinacreator.gzcm.runtime.core.logging.config.LoggingServiceConfig;

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

    // Crypto beans 已迁入 security-engine-impl/CryptoBeanConfig（打破循环依赖）
}
