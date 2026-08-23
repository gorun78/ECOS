package com.chinacreator.gzcm.sysman.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import com.chinacreator.gzcm.runtime.core.i18n.I18nMessageSource;
import com.chinacreator.gzcm.runtime.core.i18n.I18nUtils;
// import com.chinacreator.gzcm.runtime.core.i18n.impl.I18nMessageSourceImpl; // 位于 runtime-impl，对 api 模块不可见
import com.chinacreator.gzcm.runtime.core.i18n.impl.SpringMessageSourceImpl; // 位于 runtime-api，作为替代

/**
 * Sys-Man子系统国际化配置
 * 
 * @author CDRC Sys-Man Team
 */
@Configuration("sysManI18nConfig")
public class I18nConfig {
    
    /**
     * Spring MessageSource配置
     * 支持动态加载所有语言资源文件（通过命名约定：messages_{locale}.properties和error_{locale}.properties）
     * 同时支持业务消息和错误消息
     */
    @Bean
    @Primary
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = 
            new ReloadableResourceBundleMessageSource();
        
        // 设置多个资源文件基础路径（支持通配符匹配）
        // Spring会自动发现所有符合命名约定的文件：
        // messages_zh_CN.properties, messages_en_US.properties等
        // error_zh_CN.properties, error_en_US.properties等
        messageSource.setBasenames("classpath:i18n/messages", "classpath:i18n/error");
        
        // 设置编码为UTF-8
        messageSource.setDefaultEncoding("UTF-8");
        
        // 设置缓存时间（秒）
        messageSource.setCacheSeconds(3600);
        
        // 不fallback到系统Locale，使用我们自己的fallback机制
        messageSource.setFallbackToSystemLocale(false);
        
        // 如果找不到消息，返回消息代码本身
        messageSource.setUseCodeAsDefaultMessage(true);
        
        return messageSource;
    }
    
    /**
     * 错误消息MessageSource配置
     */
    @Bean("errorMessageSource")
    public MessageSource errorMessageSource() {
        ReloadableResourceBundleMessageSource messageSource = 
            new ReloadableResourceBundleMessageSource();
        
        messageSource.setBasename("classpath:i18n/error");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setCacheSeconds(3600);
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setUseCodeAsDefaultMessage(true);
        
        return messageSource;
    }
    
    /**
     * I18nMessageSource实现
     * 包装Spring的MessageSource，供I18nUtils使用
     */
    @Bean
    public I18nMessageSource i18nMessageSource(MessageSource messageSource) {
        // 使用 SpringMessageSourceImpl 包装 Spring 的 MessageSource
        // 这样可以避免直接依赖 runtime-impl 中的 I18nMessageSourceImpl
        SpringMessageSourceImpl impl = new SpringMessageSourceImpl(messageSource);
        
        // 初始化I18nUtils
        I18nUtils.setMessageSource(impl);
        
        return impl;
    }
}

