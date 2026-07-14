package com.chinacreator.gzcm.runtime.core.i18n.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chinacreator.gzcm.runtime.core.i18n.I18nMessageSource;
import com.chinacreator.gzcm.runtime.core.i18n.LocaleResolver;

/**
 * I18n消息源实现类
 * 从properties文件加载国际化资源
 * 
 * @author CDRC Runtime Team
 */
public class I18nMessageSourceImpl implements I18nMessageSource {
    
    private static final Logger logger = LoggerFactory.getLogger(I18nMessageSourceImpl.class);
    
    private static final String MESSAGES_BASE_PATH = "/i18n/messages";
    
    // 缓存已加载的资源文件
    private final Map<String, Properties> messageCache = new ConcurrentHashMap<>();
    
    /**
     * 构造函数，初始化时加载默认语言资源
     */
    public I18nMessageSourceImpl() {
        // 预加载默认语言资源
        String defaultLocale = LocaleResolver.getDefaultLocaleCode();
        loadMessages(defaultLocale);
    }
    
    /**
     * 加载指定语言的资源文件
     * 
     * @param locale 语言代码（如 zh_CN, en_US）
     */
    private Properties loadMessages(String locale) {
        if (messageCache.containsKey(locale)) {
            return messageCache.get(locale);
        }
        
        Properties props = new Properties();
        String resourcePath = MESSAGES_BASE_PATH + "_" + locale + ".properties";
        
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is != null) {
                try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    props.load(reader);
                    messageCache.put(locale, props);
                    logger.debug("Loaded i18n messages for locale: {}", locale);
                }
            } else {
                logger.warn("I18n resource file not found: {}", resourcePath);
            }
        } catch (IOException e) {
            logger.error("Failed to load i18n messages for locale: " + locale, e);
        }
        
        return props;
    }
    
    @Override
    public String getMessage(String code, String locale, Object... args) {
        if (code == null || code.isEmpty()) {
            return code;
        }
        
        String normalizedLocale = LocaleResolver.normalizeLocale(locale);
        Properties props = loadMessages(normalizedLocale);
        
        String message = props.getProperty(code);
        if (message == null) {
            // 尝试回退到默认语言
            String defaultLocale = LocaleResolver.getDefaultLocale().toString().replace("-", "_");
            if (!normalizedLocale.equals(defaultLocale)) {
                Properties defaultProps = loadMessages(defaultLocale);
                message = defaultProps.getProperty(code);
            }
            
            // 如果仍然找不到，返回code本身
            if (message == null) {
                logger.debug("Message not found for code: {} and locale: {}", code, normalizedLocale);
                return code;
            }
        }
        
        // 替换参数占位符 {0}, {1}, ... 或 {param}
        if (args != null && args.length > 0) {
            message = formatMessage(message, args);
        }
        
        return message;
    }
    
    @Override
    public String getMessage(String code, Locale locale, Object... args) {
        if (locale == null) {
            locale = LocaleResolver.getDefaultLocale();
        }
        String localeStr = locale.getLanguage() + "_" + locale.getCountry();
        return getMessage(code, localeStr, args);
    }
    
    @Override
    public String getMessage(String code, String defaultMessage, String locale, Object... args) {
        String message = getMessage(code, locale, args);
        if (message.equals(code)) {
            // 如果返回的是code本身，说明没找到，使用默认消息
            return defaultMessage != null ? formatMessage(defaultMessage, args) : code;
        }
        return message;
    }
    
    @Override
    public String getErrorMessage(String errorCode, String locale, Object... args) {
        String code = "error." + errorCode;
        return getMessage(code, locale, args);
    }
    
    @Override
    public boolean hasMessage(String code, String locale) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        
        String normalizedLocale = LocaleResolver.normalizeLocale(locale);
        Properties props = loadMessages(normalizedLocale);
        return props.containsKey(code);
    }
    
    /**
     * 格式化消息，替换占位符
     * 支持 {0}, {1} 格式和 {param} 格式
     * 
     * @param message 原始消息
     * @param args 参数数组
     * @return 格式化后的消息
     */
    private String formatMessage(String message, Object... args) {
        if (message == null || args == null || args.length == 0) {
            return message;
        }
        
        String result = message;
        // 替换 {0}, {1}, ... 格式
        for (int i = 0; i < args.length; i++) {
            String placeholder = "{" + i + "}";
            if (result.contains(placeholder)) {
                result = result.replace(placeholder, String.valueOf(args[i]));
            }
        }
        
        // 替换 {{param}} 格式（i18next风格）
        for (int i = 0; i < args.length; i++) {
            String placeholder = "{{" + i + "}}";
            if (result.contains(placeholder)) {
                result = result.replace(placeholder, String.valueOf(args[i]));
            }
        }
        
        return result;
    }
}

