package com.chinacreator.gzcm.runtime.core.config;

import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

public class ConfigServiceImpl implements IConfigService {

    private final Properties props = new Properties();
    private final CopyOnWriteArrayList<ConfigListener> listeners = new CopyOnWriteArrayList<>();
    private String environment = "default";

    @Override
    public String getProperty(String key) {
        return props.getProperty(key);
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    @Override
    public int getIntProperty(String key, int defaultValue) {
        String v = props.getProperty(key);
        try {
            return v == null ? defaultValue : Integer.parseInt(v);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    @Override
    public boolean getBooleanProperty(String key, boolean defaultValue) {
        String v = props.getProperty(key);
        return v == null ? defaultValue : Boolean.parseBoolean(v);
    }

    @Override
    public long getLongProperty(String key, long defaultValue) {
        String v = props.getProperty(key);
        try {
            return v == null ? defaultValue : Long.parseLong(v);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    @Override
    public Properties getAllProperties() {
        Properties copy = new Properties();
        copy.putAll(props);
        return copy;
    }

    @Override
    public Properties getPropertiesByPrefix(String prefix) {
        Properties result = new Properties();
        props.forEach((k, v) -> {
            if (k instanceof String && ((String) k).startsWith(prefix)) {
                result.put(k, v);
            }
        });
        return result;
    }

    @Override
    public void setProperty(String key, String value) {
        String old = props.getProperty(key);
        props.setProperty(key, value);
        listeners.forEach(l -> l.onConfigChanged(key, value, old));
    }

    @Override
    public void reload() throws ConfigException {
        // no-op for stub
    }

    @Override
    public void validate() throws ConfigException {
        // no-op for stub
    }

    @Override
    public String getEnvironment() {
        return environment;
    }

    @Override
    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    @Override
    public void addConfigListener(ConfigListener listener) {
        listeners.addIfAbsent(listener);
    }

    @Override
    public void removeConfigListener(ConfigListener listener) {
        listeners.remove(listener);
    }
}

