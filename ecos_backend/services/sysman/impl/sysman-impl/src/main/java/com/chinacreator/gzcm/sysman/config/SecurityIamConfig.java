package com.chinacreator.gzcm.sysman.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Sys-Man IAM子系统Spring配置类
 * 启用组件扫描，自动发现和注册DAO、Service等组件
 * ISystemDatabaseAccess和ILoggingService等基础服务由Runtime提供
 * 
 * @author CDRC Sys-Man Team
 */
@Configuration
// 排除 runtime 模块的 MyBatis 配置，防止重复扫描和 Bean 冲突
// Sys-Man 使用自己的 MyBatis 配置（如果有的话），或者直接使用 runtime 的数据访问接口
// abac/datapermission/policy/compliance 已迁移至 runtime-core
// (com.chinacreator.gzcm.runtime.core.security.* / .compliance / .datapermission)
@ComponentScan(basePackages = {
    "com.chinacreator.gzcm.sysman.iam",
    "com.chinacreator.gzcm.sysman.audit",
    "com.chinacreator.gzcm.sysman.security",
    "com.chinacreator.gzcm.sysman.config",
    "com.chinacreator.gzcm.sysman.log"
})
public class SecurityIamConfig {
    // 所有DAO和Service通过@Component、@Service、@Repository注解自动注册
    // ISystemDatabaseAccess和ILoggingService由Runtime的RuntimeConfig提供
}
