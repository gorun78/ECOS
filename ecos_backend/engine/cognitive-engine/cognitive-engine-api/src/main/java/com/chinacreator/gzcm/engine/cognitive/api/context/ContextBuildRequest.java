package com.chinacreator.gzcm.engine.cognitive.api.context;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Context Builder 请求 DTO（《认知域与智能域补充详细设计 v1.0》§5.2 请求示例）。
 *
 * <p>端点：{@code POST /api/v1/cognitive/context/build}（常量
 * {@link com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths#CONTEXT_BUILD}）。
 * Context Pack 为请求态 DTO，<b>不新建 cognitive_context 表</b>（§5.2 重要约束）。</p>
 *
 * <p>纯 POJO：仅 Lombok 自委托注解，无 Spring 依赖（api 模块纪律）。</p>
 */
@Data
public class ContextBuildRequest {

    /** 本次构建请求的追踪 ID（响应 body 回显；与 §12.2 traceId 维度同源）。 */
    private String requestId;

    /** 场景 ID（§12.2 统一 trace 维度）。 */
    private String scenarioId;

    /** 场景蓝图引用（blueprintId + version；版本失配 → 409 E409_VERSION_STALE）。 */
    private BlueprintRef blueprintRef;

    /** 目标（goal.type / statement / successCriteria；成功条件是 gap 生成依据）。 */
    private Goal goal;

    /** 范围（租户 / 业务 / 周期 / 口径 / 数据时点 asOfTime）。 */
    private Scope scope;

    /** 必需能力清单（DATA_PROFILE / ONTOLOGY_CANDIDATE / COGNITIVE_DIAGNOSE 等）。 */
    private List<String> requiredCapabilities = new ArrayList<>();

    /**
     * 权威源清单（步骤 2「建立权威源清单」输入）：
     * 从 Blueprint 依赖与任务类型推导的必需资产，<b>不得无界检索全平台</b>（§5.4-2）。
     */
    private List<ContextSourceRef> requiredAssets = new ArrayList<>();

    /** 素材包含开关（dataAssets / ontology / knowledge / cognitiveAssets / modelCapabilities）。 */
    private Include include = new Include();

    /** 预算（maxTokens / maxSources / maxLatencyMs；超预算 → 429 E429_BUDGET_EXCEEDED）。 */
    private Budget budget = new Budget();

    /** 请求方声明的期望数据时点（ISO-8601 UTC）。非空 = 开启严格版本检查：
     *  任一<b>必需</b>源读得的 asOfTime 早于该时点 ⇒ 409 上下文版本过期（§5.4-5 / §5.5 版本不匹配）。 */
    private String expectedAsOfTime;

    /** Context Pack 可用时长（毫秒）；null/≤0 ⇒ 服务缺省 300000（5 分钟，§5.4-10 TTL 缺省）。 */
    private Long ttlMillis;

    @lombok.Data
    public static final class BlueprintRef {
        private String blueprintId;
        private String version;
    }

    @lombok.Data
    public static final class Goal {
        private String type;
        private String statement;
        private List<String> successCriteria = new ArrayList<>();
        /** 停止条件（§5.1 goal/successCriteria/stopConditions 三元组；允许缺省空集）。 */
        private List<String> stopConditions = new ArrayList<>();
    }

    @lombok.Data
    public static final class Scope {
        private String tenantId;
        private List<String> projectIds = new ArrayList<>();
        private List<String> departmentIds = new ArrayList<>();
        private List<String> periods = new ArrayList<>();
        /** 指标口径 ID（Outcome Evaluator 对齐口径的锚点，§9.2-2）。 */
        private String caliberId;
        /** 数据时点（ISO-8601 UTC）：Pack 内所有引用时间维度的基准。 */
        private String asOfTime;
    }

    @lombok.Data
    public static final class Include {
        private boolean dataAssets = true;
        private boolean ontology = true;
        private boolean knowledge = true;
        private boolean cognitiveAssets = true;
        private boolean modelCapabilities = true;
    }

    @lombok.Data
    public static final class Budget {
        /** 上下文素材 Token 上限（按来源条目估算；超限 ⇒ 429）。 */
        private long maxTokens;
        /** 来源条目上限；null/≤0 不约束。 */
        private Integer maxSources;
        /** 组装墙钟上限（ms）；由 Provider 侧读取预算自行限流，Builder 不做线程切分。 */
        private Long maxLatencyMs;
    }

    /**
     * 规范化字段序视图（固定序小写化，只读于摘要计算入口的引用清单对齐）。
     * 注：本方法不参与 contextDigest（digest 对 {@link ContextPack} 输出结构计算），
     * 仅作请求侧调试/审计的可复现快照。
     */
    public Map<String, Object> normalizedRequestView() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requestId", requestId);
        m.put("scenarioId", scenarioId);
        m.put("blueprintRef", blueprintRef);
        m.put("goal", goal);
        m.put("scope", scope);
        m.put("requiredCapabilities", requiredCapabilities);
        m.put("requiredAssets", requiredAssets);
        m.put("include", include);
        m.put("budget", budget);
        m.put("expectedAsOfTime", expectedAsOfTime);
        m.put("ttlMillis", ttlMillis);
        return m;
    }
}
