package com.chinacreator.gzcm.runtime.core.i18n;

import java.util.Locale;

/**
 * 閸ヤ粙妾崠鏍ㄧХ閹垱绨幒銉ュ經
 * 閹绘劒绶电紒鐔剁閻ㄥ嫬娴楅梽鍛濞戝牊浼呴懢宄板絿閹恒儱褰?
 * 
 * @author CDRC Runtime Team
 */
public interface I18nMessageSource {
    
    /**
     * 閼惧嘲褰囬崶浠嬫閸栨牗绉烽幁?
     * 
     * @param code 濞戝牊浼呮禒锝囩垳
     * @param locale 鐠囶叀鈻堟禒锝囩垳
     * @param args 濞戝牊浼呴崣鍌涙殶
     * @return 閸ヤ粙妾崠鏍ф倵閻ㄥ嫭绉烽幁?
     */
    String getMessage(String code, String locale, Object... args);
    
    /**
     * 閼惧嘲褰囬崶浠嬫閸栨牗绉烽幁顖ょ礄娴ｈ法鏁ocale鐎电钖勯敍?
     * 
     * @param code 濞戝牊浼呮禒锝囩垳
     * @param locale Locale鐎电钖?
     * @param args 濞戝牊浼呴崣鍌涙殶
     * @return 閸ヤ粙妾崠鏍ф倵閻ㄥ嫭绉烽幁?
     */
    String getMessage(String code, Locale locale, Object... args);
    
    /**
     * 閼惧嘲褰囬崶浠嬫閸栨牗绉烽幁顖ょ礄鐢箓绮拋銈呪偓纭风礆
     * 
     * @param code 濞戝牊浼呮禒锝囩垳
     * @param defaultMessage 姒涙顓诲☉鍫熶紖
     * @param locale 鐠囶叀鈻堟禒锝囩垳
     * @param args 濞戝牊浼呴崣鍌涙殶
     * @return 閸ヤ粙妾崠鏍ф倵閻ㄥ嫭绉烽幁?
     */
    String getMessage(String code, String defaultMessage, String locale, Object... args);
    
    /**
     * 閼惧嘲褰囬柨娆掝嚖濞戝牊浼?
     * 
     * @param errorCode 闁挎瑨顕ゆ禒锝囩垳
     * @param locale 鐠囶叀鈻堟禒锝囩垳
     * @param args 濞戝牊浼呴崣鍌涙殶
     * @return 閸ヤ粙妾崠鏍ф倵閻ㄥ嫰鏁婄拠顖涚Х閹?
     */
    String getErrorMessage(String errorCode, String locale, Object... args);
    
    /**
     * 濡偓閺屻儲绉烽幁顖涙Ц閸氾箑鐡ㄩ崷?
     * 
     * @param code 濞戝牊浼呮禒锝囩垳
     * @param locale 鐠囶叀鈻堟禒锝囩垳
     * @return 閺勵垰鎯佺€涙ê婀?
     */
    boolean hasMessage(String code, String locale);
}

