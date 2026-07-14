package com.chinacreator.gzcm.runtime.core.config;

/**
 * 閰嶇疆鍙樻洿鐩戝惉鍣ㄣ€?
 */
public interface ConfigListener {

    /**
     * 褰撻厤缃」鍙戠敓鍙樺寲鏃跺洖璋冦€?
     */
    void onConfigChanged(String key, String oldValue, String newValue);
}


