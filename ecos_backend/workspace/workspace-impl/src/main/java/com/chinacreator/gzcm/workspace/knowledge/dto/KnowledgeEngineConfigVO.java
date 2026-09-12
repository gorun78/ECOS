package com.chinacreator.gzcm.workspace.knowledge.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 知识工作台引擎配置 VO — E-A#3 产物。
 *
 * <p>scope 分发聚合：knowledge（kb 内 settings）/ cognitive（ai 侧 cognitive config）/
 * all（两者 + 本地 sys_config 域基线）。读侧只读聚合；写侧 PUT 单次更新。
 *
 * @author ecos-factory
 * @since PMO-55 批次 E-A
 */
public class KnowledgeEngineConfigVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 生效的 scope：knowledge / cognitive / all */
    private String scope;

    /**
     * 配置键映射（config_key → config_value 字符串值）。
     * PUT 入参用同一结构；GET 返回原样。
     */
    private Map<String, String> config = new LinkedHashMap<>();

    /** 本次配置项条数（便于前端展示 "N 项生效"） */
    private Integer count;

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Map<String, String> getConfig() {
        return config;
    }

    public void setConfig(Map<String, String> config) {
        this.config = config == null ? new LinkedHashMap<>() : config;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }
}
