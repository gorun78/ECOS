package com.chinacreator.gzcm.runtime.core.mybatis.config;

import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * MyBatis Configuration
 * 
 * This configuration class provides MyBatis setup for the runtime module.
 * It configures SqlSessionFactory, Mapper scanning, and transaction management.
 * 
 * All MyBatis-related configurations are centralized here to be used by other modules
 * that depend on runtime-impl.
 */
@org.springframework.context.annotation.Configuration
@EnableTransactionManagement
@ComponentScan(basePackages = "com.chinacreator.gzcm.runtime.core.mybatis")
public class MyBatisConfig {
    
    /**
     * Configure SqlSessionFactory
     * 
     * @param dataSource DataSource bean (can be injected from application context)
     * @return SqlSessionFactory
     * @throws Exception if configuration fails
     */
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);
        
        // Set mapper XML locations
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<Resource> mapperLocations = new ArrayList<>();
        try {
            Resource[] mapperResources = resolver.getResources("classpath*:mapper/**/*.xml");
            for (Resource resource : mapperResources) {
                mapperLocations.add(resource);
            }
        } catch (Exception e) {
            // Ignore if no mapper resources found
        }
        try {
            Resource[] sqlResources = resolver.getResources("classpath*:**/dao/**/*-sql.xml");
            for (Resource resource : sqlResources) {
                mapperLocations.add(resource);
            }
        } catch (Exception e) {
            // Ignore if no SQL resources found
        }
        if (!mapperLocations.isEmpty()) {
            sessionFactory.setMapperLocations(mapperLocations.toArray(new Resource[0]));
        }
        
        // Set type aliases package
        sessionFactory.setTypeAliasesPackage("com.chinacreator.gzcm");
        
        // Configure MyBatis settings
        Configuration configuration = new Configuration();
        // Set mapUnderscoreToCamelCase via settings
        configuration.getVariables().put("mapUnderscoreToCamelCase", "true");
        configuration.setCacheEnabled(true);
        configuration.setLazyLoadingEnabled(false);
        configuration.setAggressiveLazyLoading(false);
        sessionFactory.setConfiguration(configuration);
        
        // Configure PageHelper plugin (if available)
        try {
            Class<?> pageInterceptorClass = Class.forName("com.github.pagehelper.PageInterceptor");
            Interceptor pageInterceptor = (Interceptor) pageInterceptorClass.getDeclaredConstructor().newInstance();
            Properties properties = new Properties();
            properties.setProperty("helperDialect", "mysql");
            properties.setProperty("reasonable", "true");
            properties.setProperty("supportMethodsArguments", "true");
            properties.setProperty("params", "count=countSql");
            pageInterceptorClass.getMethod("setProperties", Properties.class).invoke(pageInterceptor, properties);
            sessionFactory.setPlugins(new Interceptor[]{pageInterceptor});
        } catch (Exception e) {
            // PageHelper not available, skip plugin configuration
            // This allows the configuration to work even if PageHelper is not in classpath
        }
        
        return sessionFactory.getObject();
    }
    
    /**
     * Configure Mapper Scanner
     * 
     * Scans for Mapper interfaces in the specified packages
     * 
     * @return MapperScannerConfigurer
     */
    @Bean
    public MapperScannerConfigurer mapperScannerConfigurer() {
        MapperScannerConfigurer scannerConfigurer = new MapperScannerConfigurer();
        scannerConfigurer.setBasePackage("com.chinacreator.gzcm.**.dao");
        scannerConfigurer.setSqlSessionFactoryBeanName("sqlSessionFactory");
        return scannerConfigurer;
    }
    
    /**
     * Configure Transaction Manager
     * 
     * @param dataSource DataSource bean
     * @return DataSourceTransactionManager
     */
    @Bean
    public DataSourceTransactionManager transactionManager(DataSource dataSource) {
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager();
        transactionManager.setDataSource(dataSource);
        return transactionManager;
    }
}
