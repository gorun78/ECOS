package com.chinacreator.gzcm.engine.cognitive.api;

/**
 * 闭环边界枚举（详细设计-05 F05-03；closedLoopBounds 数组元素仅两值）。
 * 非法值读侧容错为 {@link #missing()} ([] + issue 记录)。
 */
public enum ClosedLoopBound {
    PRE, POST;

    public static ClosedLoopBound fromName(String name) {
        if (name == null) return null;
        for (ClosedLoopBound b : values()) {
            if (b.name().equals(name)) return b;
        }
        return null;
    }
}
