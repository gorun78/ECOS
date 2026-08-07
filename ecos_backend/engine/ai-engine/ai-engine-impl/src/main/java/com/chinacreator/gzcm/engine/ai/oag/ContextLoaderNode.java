package com.chinacreator.gzcm.engine.ai.oag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Node 2: 上下文加载器 — 加载用户/租户上下文和会话历史。
 *
 * <p>从请求参数和系统配置中组装上下文，为后续节点提供基础信息。</p>
 */
@Component
public class ContextLoaderNode implements OagNode {

    private static final Logger log = LoggerFactory.getLogger(ContextLoaderNode.class);

    @Override
    public OagPipelineContext execute(OagPipelineContext ctx) {
        ctx.setCurrentNode("ContextLoader");

        Map<String, Object> context = new LinkedHashMap<>();
        Map<String, Object> params = ctx.getRequestParams();

        // 用户/租户信息
        context.put("userId", ctx.getUserId());
        context.put("tenantId", ctx.getTenantId());

        // 从请求参数中提取上下文
        if (params != null) {
            context.put("model", params.getOrDefault("model", "deepseek-chat"));
            context.put("temperature", params.getOrDefault("temperature", 0.7));
            context.put("maxTokens", params.getOrDefault("maxTokens", 4096));
            context.put("language", params.getOrDefault("language", "zh-CN"));

            // 可选：携带业务域上下文
            if (params.containsKey("domain")) {
                context.put("domain", params.get("domain"));
            }
            if (params.containsKey("filters")) {
                context.put("filters", params.get("filters"));
            }
        } else {
            context.put("model", "deepseek-chat");
            context.put("temperature", 0.7);
            context.put("maxTokens", 4096);
            context.put("language", "zh-CN");
        }

        // 时间上下文
        context.put("timestamp", System.currentTimeMillis());

        ctx.setContext(context);
        ctx.getMetadata().putAll(context);

        log.info("[OAG:{}] 上下文加载完成 model={} lang={}",
                ctx.getTraceId(), context.get("model"), context.get("language"));

        return ctx;
    }
}
