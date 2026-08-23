package com.chinacreator.gzcm.runtime.core.i18n;

import java.util.Locale;

/**
 * 鐠囶叀鈻堢憴锝嗙€介崳?
 * 閺€顖涘瘮鐠囶叀鈻堟禒锝囩垳鐟欏嫯瀵栭崠鏍モ偓涔玜llback閺堝搫鍩楅崪灞惧⒖鐏?
 * 
 * @author CDRC Runtime Team
 */
public class LocaleResolver {
    
    /**
     * 姒涙顓荤拠顓♀枅
     */
    private static final String DEFAULT_LOCALE = "zh_CN";
    
    /**
     * 姒涙顓籐ocale鐎电钖?
     */
    private static final Locale DEFAULT_LOCALE_OBJ = new Locale("zh", "CN");
    
    /**
     * 鐟欙絾鐎界拠顓♀枅娴狅絿鐖?
     * 閺€顖涘瘮婢舵氨顫掗弽鐓庣础閿涙h-CN, zh_CN, zh-CN, en-US, en_US缁?
     * 
     * @param localeStr 鐠囶叀鈻堟禒锝囩垳鐎涙顑佹稉?
     * @return 鐟欏嫯瀵栭崠鏍ф倵閻ㄥ嫯顕㈢懛鈧禒锝囩垳閿涘牊鐗稿蹇ョ窗zh_CN閿?
     */
    public static String normalizeLocale(String localeStr) {
        if (localeStr == null || localeStr.trim().isEmpty()) {
            return DEFAULT_LOCALE;
        }
        
        // 缁夊娅庣粚鐑樼壐
        localeStr = localeStr.trim();
        
        // 缂佺喍绔存潪顒佸床娑撹桨绗呴崚鎺斿殠閺嶇厧绱￠敍鍧篽-CN -> zh_CN閿?
        localeStr = localeStr.replace('-', '_');
        
        // 鏉烆剚宕叉稉鍝勭毈閸?
        localeStr = localeStr.toLowerCase();
        
        // 婵″倹鐏夐弽鐓庣础娑撳秵顒滅涵顕嗙礉鐏忔繆鐦憴锝嗙€?
        String[] parts = localeStr.split("_");
        if (parts.length >= 2) {
            return parts[0] + "_" + parts[1].toUpperCase();
        } else if (parts.length == 1) {
            // 閸欘亝婀佺拠顓♀枅娴狅絿鐖滈敍宀冪箲閸ョ偠顕㈢懛鈧禒锝囩垳_鐠囶叀鈻堟禒锝囩垳閿涘牆顩h -> zh_ZH閿?
            return parts[0] + "_" + parts[0].toUpperCase();
        }
        
        return DEFAULT_LOCALE;
    }
    
    /**
     * 鐟欙絾鐎紸ccept-Language婢?
     * 
     * @param acceptLanguage Accept-Language婢跺娈戦崐纭风礄婵″偊绱皕h-CN,zh;q=0.9,en-US;q=0.8閿?
     * @return 鐟欙絾鐎介崥搴ｆ畱鐠囶叀鈻堟禒锝囩垳閿涘牊鐗稿蹇ョ窗zh_CN閿?
     */
    public static String resolveFromAcceptLanguage(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.trim().isEmpty()) {
            return DEFAULT_LOCALE;
        }
        
        // 鐟欙絾鐎紸ccept-Language婢?
        // 閺嶇厧绱￠敍姝緃-CN,zh;q=0.9,en-US;q=0.8
        String[] languages = acceptLanguage.split(",");
        
        for (String lang : languages) {
            // 缁夊娅庣拹銊╁櫤閸婄》绱檘=0.9閿?
            String localeStr = lang.split(";")[0].trim();
            String normalized = normalizeLocale(localeStr);
            
            // 鏉╂柨娲栫粭顑跨娑擃亝婀侀弫鍫㈡畱鐠囶叀鈻堟禒锝囩垳
            if (isValidLocale(normalized)) {
                return normalized;
            }
        }
        
        return DEFAULT_LOCALE;
    }
    
    /**
     * 濡偓閺屻儴顕㈢懛鈧禒锝囩垳閺勵垰鎯侀張澶嬫櫏
     * 閸欘垯浜掗弽瑙勫祦闂団偓鐟曚焦澧跨仦鏇礉閻╊喖澧犻崗浣筋啅閹碘偓閺堝鐗稿?
     * 
     * @param locale 鐠囶叀鈻堟禒锝囩垳
     * @return 閺勵垰鎯侀張澶嬫櫏
     */
    public static boolean isValidLocale(String locale) {
        if (locale == null || locale.trim().isEmpty()) {
            return false;
        }
        
        // 閸╃儤婀伴弽鐓庣础濡偓閺屻儻绱扮拠顓♀枅娴狅絿鐖淿閸ヨ棄顔嶆禒锝囩垳閿涘牆顩h_CN閿?
        return locale.matches("^[a-z]{2}_[A-Z]{2}$");
    }
    
    /**
     * 閼惧嘲褰囩拠顓♀枅娴狅絿鐖滈敍鍫滅瑝閸氼偄娴楃€规湹鍞惍渚婄礆
     * 婵″偊绱皕h_CN -> zh
     * 
     * @param locale 鐎瑰本鏆ｇ拠顓♀枅娴狅絿鐖?
     * @return 鐠囶叀鈻堟禒锝囩垳
     */
    public static String getLanguageCode(String locale) {
        if (locale == null || locale.trim().isEmpty()) {
            return "zh";
        }
        
        String normalized = normalizeLocale(locale);
        String[] parts = normalized.split("_");
        return parts.length > 0 ? parts[0] : "zh";
    }
    
    /**
     * 閼惧嘲褰嘗ocale鐎电钖?
     * 
     * @param localeStr 鐠囶叀鈻堟禒锝囩垳鐎涙顑佹稉?
     * @return Locale鐎电钖?
     */
    public static Locale toLocale(String localeStr) {
        String normalized = normalizeLocale(localeStr);
        String[] parts = normalized.split("_");
        
        if (parts.length >= 2) {
            return new Locale(parts[0], parts[1]);
        } else if (parts.length == 1) {
            return new Locale(parts[0]);
        }
        
        return DEFAULT_LOCALE_OBJ;
    }
    
    /**
     * 閼惧嘲褰囨妯款吇Locale
     * 
     * @return 姒涙顓籐ocale鐎电钖?
     */
    public static Locale getDefaultLocale() {
        return DEFAULT_LOCALE_OBJ;
    }
    
    /**
     * 閼惧嘲褰囨妯款吇鐠囶叀鈻堟禒锝囩垳
     * 
     * @return 姒涙顓荤拠顓♀枅娴狅絿鐖?
     */
    public static String getDefaultLocaleCode() {
        return DEFAULT_LOCALE;
    }
    
    /**
     * 閼惧嘲褰噁allback鐠囶叀鈻堟禒锝囩垳閸掓銆?
     * 娴兼ê鍘涚痪褝绱扮划鍓р€橀崠褰掑帳 -> 鐠囶叀鈻堟禒锝囩垳閸栧綊鍘?-> 姒涙顓荤拠顓♀枅
     * 
     * @param locale 鐠囶叀鈻堟禒锝囩垳
     * @return fallback鐠囶叀鈻堟禒锝囩垳閺佹壆绮?
     */
    public static String[] getFallbackLocales(String locale) {
        String normalized = normalizeLocale(locale);
        String languageCode = getLanguageCode(normalized);
        
        return new String[]{
            normalized,           // 缁墽鈥橀崠褰掑帳閿涙h_CN
            languageCode,         // 鐠囶叀鈻堟禒锝囩垳閸栧綊鍘ら敍姝緃
            DEFAULT_LOCALE        // 姒涙顓荤拠顓♀枅閿涙h_CN
        };
    }
}

