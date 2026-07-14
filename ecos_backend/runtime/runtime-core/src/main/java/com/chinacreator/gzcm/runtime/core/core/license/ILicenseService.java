package com.chinacreator.gzcm.runtime.core.core.license;

import java.util.List;
import java.util.Map;

/**
 * License 绠＄悊鏈嶅姟鎺ュ彛銆?
 */
public interface ILicenseService {

    /**
     * 鏍￠獙骞跺姞杞?License銆?
     */
    LicenseInfo validateLicense(String licenseKey) throws LicenseException;

    /**
     * 妫€鏌ユ寚瀹氱壒鎬ф槸鍚﹀凡鎺堟潈鍚敤銆?
     */
    boolean checkFeatureEnabled(String feature);

    /**
     * 鑾峰彇褰撳墠 License 鐘舵€併€?
     */
    LicenseStatus getLicenseStatus();

    /**
     * 閲嶆柊鍔犺浇 License 鏂囦欢骞跺埛鏂扮姸鎬併€?
     */
    void refreshLicense() throws LicenseException;

    /**
     * 鑾峰彇褰撳墠 License 淇℃伅銆?
     */
    LicenseInfo getLicenseInfo();

    /**
     * 鑾峰彇鎵€鏈夊凡鍚敤鐨勭壒鎬у垪琛ㄣ€?
     */
    List<String> getEnabledFeatures();

    /**
     * 鑾峰彇 License 涓殑鑷畾涔夊睘鎬с€?
     */
    String getLicenseProperty(String key);

    /**
     * 鑾峰彇 License 涓殑鍏ㄩ儴鑷畾涔夊睘鎬с€?
     */
    Map<String, String> getAllLicenseProperties();
}
