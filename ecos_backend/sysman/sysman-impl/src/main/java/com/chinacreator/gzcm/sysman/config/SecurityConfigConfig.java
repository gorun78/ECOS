package com.chinacreator.gzcm.sysman.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Sys-Man配置管理子系统Spring配置类
 * 启用组件扫描，自动发现和注册配置管理相关组件
 * ISystemDatabaseAccess和ILoggingService等基础服务由Runtime提供
 * 
 * @author CDRC Sys-Man Team
 */
@Configuration
@ComponentScan(basePackages = {
    "com.chinacreator.gzcm.sysman.config"
})
public class SecurityConfigConfig {
    // 所有DAO和Service通过@Component、@Service、@Repository注解自动注册
    // ISystemDatabaseAccess和ILoggingService由Runtime的RuntimeConfig提供
}
