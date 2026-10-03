package com.chinacreator.gzcm.engine.ontology.model;

/**
 * 可加性数值码（F03-01 / V168.1 列 additive SMALLINT，字典 metric_additivity；
 * 非布尔，DR05 is_* 前缀不适用）。
 */
public enum Additivity {
    ADDITIVE(1),   // 可加聚合
    SEMI(2),       // 半可加（如期间内可加、跨主体不可加）
    NON(3);        // 不可加

    private final int code;

    Additivity(int code) { this.code = code; }

    public int code() { return code; }

    public static Additivity of(Integer code) {
        if (code == null) return null;
        for (Additivity a : values()) {
            if (a.code == code) return a;
        }
        return null;
    }
}
