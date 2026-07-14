package com.chinacreator.gzcm.runtime.core.core.util;

/**
 * Component - 组件枚举
 * 用于标识不同的系统组件
 */
public enum Component {
    BUS("bus"),
    STANDARD("standard"),
    RUNTIME("runtime"),
    CHENG("cheng"),
    MONITOR("monitor"),
    METADATA("metadata"),
    QUALITY("quality"),
    RESCATALOG("rescatalog");
    
    private final String name;
    
    Component(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
}

