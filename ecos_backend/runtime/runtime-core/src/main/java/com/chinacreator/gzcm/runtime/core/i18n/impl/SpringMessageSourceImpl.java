package com.chinacreator.gzcm.runtime.core.i18n.impl;

import java.util.Locale;

import org.springframework.context.MessageSource;

import com.chinacreator.gzcm.runtime.core.i18n.I18nMessageSource;

/**
 * SpringMessageSourceImpl - Spring MessageSource适配器
 * 将Spring的MessageSource包装为I18nMessageSource接口
 * 
 * @author CDRC Runtime Team
 */
public class SpringMessageSourceImpl implements I18nMessageSource {
    
    private final MessageSource messageSource;
    
    /**
     * 构造函数
     * @param messageSource Spring MessageSource实例
     */
    public SpringMessageSourceImpl(MessageSource messageSource) {
        this.messageSource = messageSource;
    }
    
    @Override
    public String getMessage(String code, String locale, Object... args) {
        if (messageSource == null) {
            return code;
        }
        Locale loc = parseLocale(locale);
        return messageSource.getMessage(code, args, code, loc);
    }
    
    @Override
    public String getMessage(String code, Locale locale, Object... args) {
        if (messageSource == null) {
            return code;
        }
        return messageSource.getMessage(code, args, code, locale);
    }
    
    @Override
    public String getMessage(String code, String defaultMessage, String locale, Object... args) {
        if (messageSource == null) {
            return defaultMessage != null ? defaultMessage : code;
        }
        Locale loc = parseLocale(locale);
        return messageSource.getMessage(code, args, defaultMessage, loc);
    }
    
    @Override
    public String getErrorMessage(String errorCode, String locale, Object... args) {
        return getMessage(errorCode, locale, args);
    }
    
    @Override
    public boolean hasMessage(String code, String locale) {
        if (messageSource == null) {
            return false;
        }
        Locale loc = parseLocale(locale);
        try {
            String message = messageSource.getMessage(code, null, null, loc);
            return message != null && !message.equals(code);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 解析Locale字符串
     * @param localeStr Locale字符串，如 "zh_CN", "en_US"
     * @return Locale对象
     */
    private Locale parseLocale(String localeStr) {
        if (localeStr == null || localeStr.isEmpty()) {
            return Locale.getDefault();
        }
        String[] parts = localeStr.split("_");
        if (parts.length == 1) {
            return new Locale(parts[0]);
        } else if (parts.length == 2) {
            return new Locale(parts[0], parts[1]);
        } else if (parts.length >= 3) {
            return new Locale(parts[0], parts[1], parts[2]);
        }
        return Locale.getDefault();
    }
}
