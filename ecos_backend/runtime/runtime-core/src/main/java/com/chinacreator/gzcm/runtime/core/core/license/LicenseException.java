package com.chinacreator.gzcm.runtime.core.core.license;

/**
 * License 鐩稿叧寮傚父銆?
 */
public class LicenseException extends Exception {

    private static final long serialVersionUID = 1L;

    public LicenseException(String message) {
        super(message);
    }

    public LicenseException(String message, Throwable cause) {
        super(message, cause);
    }
}
