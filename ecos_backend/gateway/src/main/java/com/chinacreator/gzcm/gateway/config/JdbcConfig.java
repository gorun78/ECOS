package com.chinacreator.gzcm.gateway.config;

import com.chinacreator.gzcm.gateway.jdbc.TenantAwareJdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * JDBC 配置 — 注册租户感知的 JdbcTemplate 作为主 Bean。
 *
 * <p>使用 @Primary 确保所有 @Autowired JdbcTemplate 注入的都是
 * TenantAwareJdbcTemplate，自动对业务表查询追加租户过滤。
 */
@Configuration
public class JdbcConfig {

    @Bean
    @Primary
    public JdbcTemplate tenantAwareJdbcTemplate(DataSource dataSource) {
        return new TenantAwareJdbcTemplate(dataSource);
    }
}
