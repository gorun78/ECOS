package com.chinacreator.gzcm.engine.ai.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.chinacreator.gzcm.engine.ai.dao.AiToolRegistryDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 场景受控工具注册表（详细设计-06 F06-11 要点 1）—— 声明单源的<b>读入口</b>，本身不含执行体。
 *
 * <p><b>覆盖关系</b>：{@code ecos_ai.ecos_ai_tool_registry}（V200）的 ACTIVE 行覆盖
 * {@link ScenarioToolCatalog} 的同名代码声明；表未就位（迁移脚本仅落地未实跑）时回落代码声明。
 * 两条来源都必须通过 {@link ScenarioToolDefinition} 的构造校验（后端只允许 REST、operation 只允许
 * read|write、超时有上界），因此表里写进 {@code backend_kind='SQL'} 的行会被<b>拒绝并告警</b>，
 * 而不是生成一条可执行任意 SQL 的工具 —— X-13 的复发面在入口就被关掉。</p>
 *
 * <p><b>无执行体纪律（X-12）</b>：本注册表只提供声明（端点/方法/超时/操作/权限提示/投影字段），
 * 实际执行一律由咽喉 {@code ToolExecutorService} 按声明分发，每次调用恰一条 security-engine 裁决。
 * 与旧 {@code ToolRegistry.register(schema, executor)} 的 lambda 形态相比，这里没有任何可绕过裁决的分支。</p>
 */
@Component
public class ScenarioToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ScenarioToolRegistry.class);

    /** 表读取失败或行不合规时的回落单源。 */
    private final Map<String, ScenarioToolDefinition> catalog = new LinkedHashMap<>();

    private final ObjectProvider<AiToolRegistryDao> registryDaoProvider;

    public ScenarioToolRegistry(ObjectProvider<AiToolRegistryDao> registryDaoProvider) {
        this.registryDaoProvider = registryDaoProvider;
        for (ScenarioToolDefinition def : ScenarioToolCatalog.all()) {
            catalog.put(def.toolKey(), def);
        }
    }

    /**
     * 声明查询：表内 ACTIVE 行优先，其余回落代码声明。
     *
     * @return 空 Optional 表示该名称不是场景受控工具（调用方继续走通用工具路径）
     */
    public Optional<ScenarioToolDefinition> find(String toolKey) {
        if (toolKey == null || toolKey.isBlank()) return Optional.empty();
        AiToolRegistryDao dao = registryDao();
        if (dao != null) {
            try {
                Map<String, Object> row = dao.findActive(toolKey);
                ScenarioToolDefinition fromRow = toDefinition(row);
                if (fromRow != null) return Optional.of(fromRow);
            } catch (Exception e) {
                // 表不可读 ≠ 无声明：回落代码单源，但必须留痕（禁静默掩蔽成"工具不存在"）
                log.warn("F06-11 工具注册表读取失败，回落代码声明: tool={} reason={}", toolKey, e.getMessage());
            }
        }
        return Optional.ofNullable(catalog.get(toolKey));
    }

    public boolean isScenarioTool(String toolKey) {
        return find(toolKey).isPresent();
    }

    /** 声明的 read|write（裁决载荷 {@code action.operation} 的唯一取值来源，禁按参数猜测）。 */
    public Optional<String> declaredOperation(String toolKey) {
        return find(toolKey).map(ScenarioToolDefinition::operation);
    }

    /** 全部在册声明（供目录面板与启动自检；表覆盖后逐项生效）。 */
    public List<ScenarioToolDefinition> declarations() {
        Map<String, ScenarioToolDefinition> merged = new LinkedHashMap<>(catalog);
        AiToolRegistryDao dao = registryDao();
        if (dao != null) {
            try {
                for (Map<String, Object> row : dao.listByStatus("ACTIVE")) {
                    ScenarioToolDefinition def = toDefinition(row);
                    if (def != null) merged.put(def.toolKey(), def);
                }
            } catch (Exception e) {
                log.warn("F06-11 工具注册表全量读取失败，回落代码声明: reason={}", e.getMessage());
            }
        }
        return List.copyOf(merged.values());
    }

    /** LLM function-calling 目录（声明式参数模式，与内置工具同形态）。 */
    public List<Map<String, Object>> functionCallSchemas() {
        List<Map<String, Object>> schemas = new ArrayList<>();
        for (ScenarioToolDefinition def : declarations()) {
            schemas.add(def.toFunctionCallSchema());
        }
        return schemas;
    }

    /**
     * 写工具派发载荷（F06-11 要点 2 + PRD-06 §4.1"仅创建 DRAFT 态"）：
     * 状态强制 DRAFT、身份字段剔除，其余原样透传。
     */
    public Map<String, Object> dispatchPayload(ScenarioToolDefinition def, Map<String, Object> arguments) {
        return ScenarioToolCatalog.enforceWritePayload(def, arguments);
    }

    /**
     * 场景 Agent 白名单启动自检（F06-11 要点 3）：白名单里出现被禁的通用工具即非法。
     *
     * <p>白名单语义从"能力清单"升级为"经裁决的权限视图"——通用 {@code query_db}/{@code invoke_rest}
     * 允许 LLM 自由生成 SQL/URL，与"只经五个受控入口取数"的场景约束互斥，故在场景侧显式禁用。</p>
     */
    public List<String> illegalWhitelistEntries(List<String> whitelist) {
        return ScenarioToolCatalog.illegalEntriesForScenario(whitelist);
    }

    /**
     * 单次工具调用的入参地板校验：返回缺失的必填参数名。
     *
     * <p>这是咽喉上的<b>本地</b>地板（在裁决之前），缺参即 {@code INVALID_ARGUMENT}，
     * 不进后端调用 —— 省一次 502，也避免把注定失败的载荷写进审计流。</p>
     */
    public List<String> missingRequiredArgs(String toolKey, Map<String, Object> arguments) {
        return find(toolKey).map(def -> def.missingRequiredArgs(arguments)).orElse(List.of());
    }

    private AiToolRegistryDao registryDao() {
        return registryDaoProvider == null ? null : registryDaoProvider.getIfAvailable();
    }

    /**
     * 表行 → 声明。非法行（缺字段、{@code backend_kind} 非 REST、operation 越界）返回 {@code null}
     * 并告警：宁可不认这条声明，也不认一条能越权的声明（fail-closed）。
     */
    private ScenarioToolDefinition toDefinition(Map<String, Object> row) {
        if (row == null || row.isEmpty()) return null;
        String toolKey = text(row.get("tool_key"));
        if (toolKey == null) return null;
        try {
            String endpoint = text(row.get("backend_endpoint"));
            ScenarioToolDefinition base = catalog.get(toolKey);
            String httpMethod = text(row.get("http_method"));
            if (httpMethod == null) {
                // V200 无 http_method 列：GET 与 POST 属"参数与后端映射"的一部分，只在代码声明里冻结
                httpMethod = base == null ? "GET" : base.httpMethod();
            }
            return new ScenarioToolDefinition(
                    toolKey,
                    textOr(row.get("name"), base == null ? toolKey : base.name()),
                    textOr(row.get("description"), base == null ? null : base.description()),
                    base == null ? Map.of("query", new com.chinacreator.gzcm.engine.ai.service.ToolSchema
                            .ParamDef("string", true, "查询条件")) : base.argsSchema(),
                    textOr(row.get("backend_kind"), ScenarioToolDefinition.BACKEND_KIND_REST),
                    base == null ? ScenarioToolCatalog.SERVICE_WORKSPACE : base.backendService(),
                    textOr(endpoint, base == null ? null : base.backendEndpoint()),
                    httpMethod,
                    textOr(row.get("operation"), base == null ? ScenarioToolDefinition.OPERATION_READ
                            : base.operation()),
                    textOr(row.get("permission_hint"), base == null ? null : base.permissionHint()),
                    intOr(row.get("timeout_ms"), base == null ? 3_000 : base.timeoutMs()),
                    textOr(row.get("resource_scope_projection"),
                            base == null ? null : base.resourceScopeProjection()));
        } catch (IllegalArgumentException rejected) {
            log.error("F06-11 注册表行不合规，本条声明被拒绝（fail-closed，不回落成可执行任意 SQL 的工具）: "
                    + "tool={} reason={}", toolKey, rejected.getMessage());
            return null;
        }
    }

    private static String text(Object v) {
        String s = v == null ? null : String.valueOf(v).trim();
        return s == null || s.isEmpty() ? null : s;
    }

    private static String textOr(Object v, String fallback) {
        String s = text(v);
        return s != null ? s : fallback;
    }

    private static int intOr(Object v, int fallback) {
        if (v instanceof Number n) return n.intValue();
        String s = text(v);
        if (s == null) return fallback;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
