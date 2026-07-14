package com.chinacreator.gzcm.runtime.core.config;

import java.util.Properties;

/**
 * 閰嶇疆绠＄悊鏈嶅姟鎺ュ彛銆?
 */
public interface IConfigService {

    String getProperty(String key);

    String getProperty(String key, String defaultValue);

    int getIntProperty(String key, int defaultValue);

    boolean getBooleanProperty(String key, boolean defaultValue);

    long getLongProperty(String key, long defaultValue);

    Properties getAllProperties();

    Properties getPropertiesByPrefix(String prefix);

    void setProperty(String key, String value);

    void reload() throws ConfigException;

    void validate() throws ConfigException;

    String getEnvironment();

    void setEnvironment(String environment);

    void addConfigListener(ConfigListener listener);

    void removeConfigListener(ConfigListener listener);
}


