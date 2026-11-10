package com.chinacreator.gzcm.engine.ai.copilot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.engine.ai.copilot.CopilotToolCatalog.Param;
import com.chinacreator.gzcm.engine.ai.copilot.CopilotToolCatalog.Tool;
import com.chinacreator.gzcm.engine.ai.service.ToolExecutorService;
import com.chinacreator.gzcm.engine.ai.service.ToolRegistry;
import com.chinacreator.gzcm.engine.ai.service.ToolSchema;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;

/**
 * Copilot 工具注册器：把 {@link CopilotToolCatalog} 的六域全生命周期工具注册进 {@link ToolRegistry}。
 *
 * <p>统一执行语义（所有工具共用，全部经 ToolExecutorService 策略咽喉）：</p>
 * <ul>
 *   <li>路径 {@code {var}} 占位从入参取值并 URL 编码；GET 其余入参作 query；</li>
 *   <li>POST/PUT 请求体 = {@code payload}（对象，整 DTO 透传）∪ 其余标量入参；</li>
 *   <li>T3（受控执行）必须 {@code confirm=true}，否则返回「需用户确认」失败（模型向用户复述后重试）；</li>
 *   <li>T2/T3 自动附 {@code idempotencyKey}（入参显式优先，缺省按 sessionId+tool+args 派生）；</li>
 *   <li>写工具 {@code ToolSchema.operation=write}（裁决载荷声明优先，防写操作按只读裁决）；</li>
 *   <li>MONOLITH 属主走 :8080 + {@link CopilotToolSupport} 凭据中继；SERVICE 属主直连属主端口。</li>
 * </ul>
 */
@Component
public class CopilotToolRegistrar {

    private static final Logger log = LoggerFactory.getLogger(CopilotToolRegistrar.class);

    private static final Pattern PATH_VAR = Pattern.compile("\\{(\\w+)\\}");

    /** 写工具的公共保留键（不进 query/body 的传输面字段）。 */
    private static final String KEY_PAYLOAD = "payload";
    private static final String KEY_CONFIRM = "confirm";
    private static final String KEY_IDEMPOTENCY = "idempotencyKey";
    private static final String KEY_SESSION = "sessionId";

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private CopilotToolSupport support;

    @PostConstruct
    public void register() {
        if (toolRegistry == null) {
            log.warn("[CopilotTools] ToolRegistry 未装配，Copilot 工具未注册");
            return;
        }
        for (Tool tool : CopilotToolCatalog.all()) {
            registerOne(tool);
        }
        log.info("[CopilotTools] Copilot 六域工具注册完成: {} 个",
                CopilotToolCatalog.all().size());
    }

    private void registerOne(Tool tool) {
        ToolSchema schema = new ToolSchema(tool.name(), tool.desc());
        boolean write = !"T1".equals(tool.tier());
        if (write) {
            schema.operation("write");
        }
        for (Param param : tool.params()) {
            schema.addParam(param.name(),
                    new ToolSchema.ParamDef(param.type(), param.required(), param.desc()));
        }
        if (write) {
            schema.addParam(KEY_PAYLOAD, new ToolSchema.ParamDef("object", false,
                    "请求体 JSON 对象（整 DTO 透传；键名以各端点契约为准）"));
            schema.addParam(KEY_IDEMPOTENCY, new ToolSchema.ParamDef("string", false,
                    "幂等键（缺省由服务端派生）"));
        }
        if ("T3".equals(tool.tier())) {
            schema.addParam(KEY_CONFIRM, new ToolSchema.ParamDef("boolean", false,
                    "受控执行确认：用户已明确同意后传 true，否则调用会被拒绝"));
        }
        toolRegistry.register(schema, args -> execute(tool, args));
    }

    // ─── 通用执行 ────────────────────────────────────────────────

    private ToolExecutorService.ToolResult execute(Tool tool, Map<String, Object> args) {
        long startMs = System.currentTimeMillis();
        Map<String, Object> safeArgs = args == null ? Map.of() : args;

        // T3 受控执行确认门（先于任何外呼）
        if ("T3".equals(tool.tier()) && !support.confirmed(safeArgs)) {
            return support.confirmRequired(tool.name());
        }

        // 路径占位替换
        String path = tool.path();
        Matcher matcher = PATH_VAR.matcher(path);
        StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = safeArgs.get(key);
            if (value == null || String.valueOf(value).isBlank()) {
                return support.fail(tool.name(), "缺少路径参数 " + key, 0L);
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(CopilotToolSupport.encode(String.valueOf(value))));
        }
        matcher.appendTail(resolved);
        final String resolvedPath = resolved.toString();

        ServiceEndpointResolver.RouteMode mode = tool.service()
                ? ServiceEndpointResolver.RouteMode.SERVICE
                : ServiceEndpointResolver.RouteMode.MONOLITH;

        String method = tool.method().toUpperCase();
        if ("GET".equals(method)) {
            Map<String, Object> query = new LinkedHashMap<>();
            for (Param param : tool.params()) {
                if (!param.required() && !resolvedPath.contains("{" + param.name() + "}")) {
                    CopilotToolSupport.putIfPresent(query, param.name(), safeArgs.get(param.name()));
                }
            }
            return support.execute(tool.name(), startMs,
                    () -> support.get(tool.owner(), mode, resolvedPath, query));
        }

        // POST / PUT：body = payload ∪ 其余标量入参（排除保留键与已用于路径的键）
        Map<String, Object> body = new LinkedHashMap<>();
        Object payload = safeArgs.get(KEY_PAYLOAD);
        if (payload instanceof Map<?, ?> payloadMap) {
            for (Map.Entry<?, ?> entry : payloadMap.entrySet()) {
                body.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        for (Param param : tool.params()) {
            if (resolvedPath.contains("{" + param.name() + "}")) {
                continue;
            }
            Object value = safeArgs.get(param.name());
            if (value != null) {
                body.put(param.name(), value);
            }
        }
        if (!"T1".equals(tool.tier())) {
            body.put(KEY_IDEMPOTENCY, CopilotToolSupport.idempotencyKey(
                    safeArgs, tool.name(), String.valueOf(safeArgs.get(KEY_SESSION))));
        }
        if ("PUT".equals(method)) {
            return support.execute(tool.name(), startMs,
                    () -> support.put(tool.owner(), mode, resolvedPath, body));
        }
        return support.execute(tool.name(), startMs,
                () -> support.post(tool.owner(), mode, resolvedPath, body));
    }
}
