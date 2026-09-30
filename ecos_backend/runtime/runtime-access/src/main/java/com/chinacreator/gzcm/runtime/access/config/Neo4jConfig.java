package com.chinacreator.gzcm.runtime.access.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Neo4j 统一配置 Bean。
 * <p>
 * 只在档位声明 {@code ecos.graph.provider=neo4j}（enterprise / ultimate）且 classpath
 * 中存在 neo4j-java-driver 时激活；standard 档 provider=pg，不创建 Driver，
 * 各引擎经 {@code Neo4jClient.isAvailable()} 降级为 no-op。
 * 连接参数取 {@code neo4j.*}（可由 {@code NEO4J_URI} 等环境变量覆盖）。
 * 使用连接池配置 connectionTimeout=5s, maxConnectionPoolSize=10。
 * </p>
 */
@Configuration
@ConditionalOnProperty(name = "ecos.graph.provider", havingValue = "neo4j")
@ConditionalOnClass(Driver.class)
public class Neo4jConfig {

    private static final Logger log = LoggerFactory.getLogger(Neo4jConfig.class);

    @Bean
    public Driver neo4jDriver(
            @Value("${ecos.graph.neo4j-uri:${neo4j.uri}}") String uri,
            @Value("${neo4j.username:neo4j}") String username,
            @Value("${neo4j.password:neo4j}") String password,
            @Value("${neo4j.database:neo4j}") String database) {

        Config config = Config.builder()
                .withConnectionTimeout(5, TimeUnit.SECONDS)
                .withMaxConnectionPoolSize(10)
                .build();

        Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password), config);
        log.info("Neo4j Driver created: uri={}, database={}, poolSize=10, timeout=5s", uri, database);
        return driver;
    }
}
