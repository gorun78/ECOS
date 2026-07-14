package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;
import java.util.Map;

/**
 * 认知引擎健康检查响应。
 */
public class HealthResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 服务名称 */
    private String service;
    /** 服务状态：UP / DEGRADED / DOWN */
    private String status;
    /** 版本号 */
    private String version;
    /** 推理器状态 */
    private ReasonerStatus reasoners;
    /** 依赖服务状态 */
    private Map<String, String> dependencies;
    /** 运行时长 */
    private String uptime;

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public ReasonerStatus getReasoners() { return reasoners; }
    public void setReasoners(ReasonerStatus reasoners) { this.reasoners = reasoners; }
    public Map<String, String> getDependencies() { return dependencies; }
    public void setDependencies(Map<String, String> dependencies) { this.dependencies = dependencies; }
    public String getUptime() { return uptime; }
    public void setUptime(String uptime) { this.uptime = uptime; }

    /**
     * 推理器状态明细。
     */
    public static class ReasonerStatus implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 规则引擎状态 */
        private ReasonerDetail ruleEngine;
        /** 因果推理器状态 */
        private ReasonerDetail causalReasoner;
        /** NSGA-II 优化器状态 */
        private ReasonerDetail nsgaIIOptimizer;

        public ReasonerDetail getRuleEngine() { return ruleEngine; }
        public void setRuleEngine(ReasonerDetail ruleEngine) { this.ruleEngine = ruleEngine; }
        public ReasonerDetail getCausalReasoner() { return causalReasoner; }
        public void setCausalReasoner(ReasonerDetail causalReasoner) { this.causalReasoner = causalReasoner; }
        public ReasonerDetail getNsgaIIOptimizer() { return nsgaIIOptimizer; }
        public void setNsgaIIOptimizer(ReasonerDetail nsgaIIOptimizer) { this.nsgaIIOptimizer = nsgaIIOptimizer; }
    }

    /**
     * 单个推理器详情。
     */
    public static class ReasonerDetail implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 状态：UP / DOWN */
        private String status;
        /** 已加载规则数 (RuleEngine 专用) */
        private Integer rulesLoaded;
        /** 最近重载时间 (RuleEngine 专用) */
        private String lastReloadAt;
        /** 缓存的知识图谱节点数 (CausalReasoner 专用) */
        private Integer kgNodesCached;
        /** 知识图谱连接状态 (CausalReasoner 专用) */
        private String kgConnection;
        /** 活跃会话数 (NsgaIIOptimizer 专用) */
        private Integer activeSessions;
        /** 错误信息（DOWN 时） */
        private String error;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getRulesLoaded() { return rulesLoaded; }
        public void setRulesLoaded(Integer rulesLoaded) { this.rulesLoaded = rulesLoaded; }
        public String getLastReloadAt() { return lastReloadAt; }
        public void setLastReloadAt(String lastReloadAt) { this.lastReloadAt = lastReloadAt; }
        public Integer getKgNodesCached() { return kgNodesCached; }
        public void setKgNodesCached(Integer kgNodesCached) { this.kgNodesCached = kgNodesCached; }
        public String getKgConnection() { return kgConnection; }
        public void setKgConnection(String kgConnection) { this.kgConnection = kgConnection; }
        public Integer getActiveSessions() { return activeSessions; }
        public void setActiveSessions(Integer activeSessions) { this.activeSessions = activeSessions; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}
