package com.chinacreator.gzcm.runtime.core.i18n;

import java.util.Locale;

/**
 * 閸ヤ粙妾崠鏍т紣閸忛琚?
 * 閹绘劒绶甸棃娆愨偓浣规煙濞夋洑绶甸幍鈧張澶婄摍缁崵绮烘担璺ㄦ暏
 * 
 * @author CDRC Runtime Team
 */
public class I18nUtils {
    
    /**
     * 濞戝牊浼呭┃鎰杽娓氬绱欓悽鍗炵杽閻滄壆琚▔銊ュ弳閿?
     */
    private static I18nMessageSource messageSource;
    
    /**
     * 鐠佸墽鐤嗗☉鍫熶紖濠ф劕鐤勬笟?
     * 閻㈠崬鎮囩€涙劗閮寸紒鐔烘畱Spring闁板秶鐤嗙猾鏄忕殶閻?
     * 
     * @param source 濞戝牊浼呭┃鎰杽娓?
     */
    public static void setMessageSource(I18nMessageSource source) {
        messageSource = source;
    }
    
    /**
     * 閼惧嘲褰囧☉鍫熶紖濠ф劕鐤勬笟?
     * 
     * @return 濞戝牊浼呭┃鎰杽娓?
     */
    public static I18nMessageSource getMessageSource() {
        return messageSource;
    }
    
    /**
     * 閼惧嘲褰囬崶浠嬫閸栨牗绉烽幁?
     * 
     * @param code 濞戝牊浼呮禒锝囩垳
     * @param locale 鐠囶叀鈻堟禒锝囩垳閿涘牆顩ч敍姝緃_CN, en_US閿?
     * @param args 濞戝牊浼呴崣鍌涙殶
     * @return 閸ヤ粙妾崠鏍ф倵閻ㄥ嫭绉烽幁?
     */
    public static String getMessage(String code, String locale, Object... args) {
        if (messageSource == null) {
            // 婵″倹鐏夊☉鍫熶紖濠ф劖婀拋鍓х枂閿涘矁绻戦崶鐐寸Х閹垯鍞惍浣规拱闊?
            return code;
        }
        
        // 鐟欏嫯瀵栭崠鏍嚔鐟封偓娴狅絿鐖?
        String normalizedLocale = LocaleResolver.normalizeLocale(locale);
        
        // 鐏忔繆鐦痜allback閺堝搫鍩?
        String[] fallbackLocales = LocaleResolver.getFallbackLocales(normalizedLocale);
        
        for (String fallbackLocale : fallbackLocales) {
            if (messageSource.hasMessage(code, fallbackLocale)) {
                return messageSource.getMessage(code, fallbackLocale, args);
            }
        }
        
        // 婵″倹鐏夐幍鈧張濉產llback闁棄銇戠拹銉礉鏉╂柨娲栧☉鍫熶紖娴狅絿鐖?
        return code;
    }
    
    /**
     * 閼惧嘲褰囬崶浠嬫閸栨牗绉烽幁顖ょ礄娴ｈ法鏁ocale鐎电钖勯敍?
     * 
     * @param code 濞戝牊浼呮禒锝囩垳
     * @param locale Locale鐎电钖?
     * @param args 濞戝牊浼呴崣鍌涙殶
     * @return 閸ヤ粙妾崠鏍ф倵閻ㄥ嫭绉烽幁?
     */
    public static String getMessage(String code, Locale locale, Object... args) {
        if (locale == null) {
            locale = LocaleResolver.getDefaultLocale();
        }
        
        String localeStr = locale.getLanguage() + "_" + locale.getCountry();
        return getMessage(code, localeStr, args);
    }
    
    /**
     * 閼惧嘲褰囬崶浠嬫閸栨牗绉烽幁顖ょ礄鐢箓绮拋銈呪偓纭风礆
     * 
     * @param code 濞戝牊浼呮禒锝囩垳
     * @param defaultMessage 姒涙顓诲☉鍫熶紖
     * @param locale 鐠囶叀鈻堟禒锝囩垳
     * @param args 濞戝牊浼呴崣鍌涙殶
     * @return 閸ヤ粙妾崠鏍ф倵閻ㄥ嫭绉烽幁?
     */
    public static String getMessage(String code, String defaultMessage, String locale, Object... args) {
        if (messageSource == null) {
            return defaultMessage != null ? defaultMessage : code;
        }
        
        String normalizedLocale = LocaleResolver.normalizeLocale(locale);
        String[] fallbackLocales = LocaleResolver.getFallbackLocales(normalizedLocale);
        
        for (String fallbackLocale : fallbackLocales) {
            if (messageSource.hasMessage(code, fallbackLocale)) {
                return messageSource.getMessage(code, fallbackLocale, args);
            }
        }
        
        return defaultMessage != null ? defaultMessage : code;
    }
    
    /**
     * 閼惧嘲褰囬柨娆掝嚖濞戝牊浼?
     * 
     * @param errorCode 闁挎瑨顕ゆ禒锝囩垳
     * @param locale 鐠囶叀鈻堟禒锝囩垳
     * @param args 濞戝牊浼呴崣鍌涙殶
     * @return 閸ヤ粙妾崠鏍ф倵閻ㄥ嫰鏁婄拠顖涚Х閹?
     */
    public static String getErrorMessage(String errorCode, String locale, Object... args) {
        if (messageSource == null) {
            return errorCode;
        }
        
        // 闁挎瑨顕ゅ☉鍫熶紖闁艾鐖舵担璺ㄦ暏error閸涜棄鎮曠粚娲？
        String code = "error." + errorCode;
        return getMessage(code, locale, args);
    }
    
    /**
     * 娴犲盯ccept-Language婢剁袙閺嬫劘顕㈢懛鈧獮鎯板箯閸欐牗绉烽幁?
     * 
     * @param code 濞戝牊浼呮禒锝囩垳
     * @param acceptLanguage Accept-Language婢跺娈戦崐?
     * @param args 濞戝牊浼呴崣鍌涙殶
     * @return 閸ヤ粙妾崠鏍ф倵閻ㄥ嫭绉烽幁?
     */
    public static String getMessageFromAcceptLanguage(String code, String acceptLanguage, Object... args) {
        String locale = LocaleResolver.resolveFromAcceptLanguage(acceptLanguage);
        return getMessage(code, locale, args);
    }
    
    /**
     * 濡偓閺屻儲绉烽幁顖涙Ц閸氾箑鐡ㄩ崷?
     * 
     * @param code 濞戝牊浼呮禒锝囩垳
     * @param locale 鐠囶叀鈻堟禒锝囩垳
     * @return 閺勵垰鎯佺€涙ê婀?
     */
    public static boolean hasMessage(String code, String locale) {
        if (messageSource == null) {
            return false;
        }
        
        String normalizedLocale = LocaleResolver.normalizeLocale(locale);
        return messageSource.hasMessage(code, normalizedLocale);
    }
}

