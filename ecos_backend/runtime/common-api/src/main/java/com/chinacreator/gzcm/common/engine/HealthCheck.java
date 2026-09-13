package com.chinacreator.gzcm.common.engine;

import java.util.Collections;
import java.util.Map;

/**
 * 引擎健康检查结果。
 *
 * @author ECOS PMO
 * @since 1.0.0
 */
public class HealthCheck {

    /** 整体状态："UP" 或 "DOWN" */
    private final String status;

    /** 各子系统状态，如 {"neo4j": "UP", "db": "UP", ...} */
    private final Map<String, Object> components;

    /**
     * 构造健康检查结果。
     *
     * @param status     整体状态，"UP" 或 "DOWN"
     * @param components 各子系统状态快照
     */
    public HealthCheck(String status, Map<String, Object> components) {
        this.status = status;
        this.components = components != null
                ? Collections.unmodifiableMap(components)
                : Collections.emptyMap();
    }

    public String getStatus() {
        return status;
    }

    public Map<String, Object> getComponents() {
        return components;
    }

    @Override
    public String toString() {
        return "HealthCheck{status='" + status + "', components=" + components + '}';
    }
}
