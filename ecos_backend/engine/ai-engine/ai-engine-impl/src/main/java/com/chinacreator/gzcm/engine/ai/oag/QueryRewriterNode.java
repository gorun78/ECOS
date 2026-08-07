package com.chinacreator.gzcm.engine.ai.oag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Node 3: 查询改写器 — 基于意图和上下文改写用户查询。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>补全查询中的歧义（如"昨天的"→具体日期）</li>
 *   <li>将自然语言转为更精确的检索表述</li>
 *   <li>对 QUERY 类意图补充 schema 上下文</li>
 * </ul>
 */
@Component
public class QueryRewriterNode implements OagNode {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriterNode.class);

    @Override
    public OagPipelineContext execute(OagPipelineContext ctx) {
        ctx.setCurrentNode("QueryRewriter");

        String original = ctx.getUserQuery();
        String intent = ctx.getIntent();

        if (original == null || original.isBlank()) {
            ctx.setRewrittenQuery(original);
            return ctx;
        }

        String rewritten = rewrite(original, intent, ctx.getContext());
        ctx.setRewrittenQuery(rewritten);
        ctx.getMetadata().put("originalQuery", original);
        ctx.getMetadata().put("rewrittenQuery", rewritten);

        if (!original.equals(rewritten)) {
            log.info("[OAG:{}] 查询改写 '{}' → '{}'", ctx.getTraceId(),
                    truncate(original), truncate(rewritten));
        } else {
            log.debug("[OAG:{}] 查询未改写 '{}'", ctx.getTraceId(), truncate(original));
        }

        return ctx;
    }

    /**
     * 基于规则的查询改写（生产环境可替换为 LLM 改写）。
     */
    private String rewrite(String query, String intent, Map<String, Object> context) {
        String result = query.trim();

        switch (intent) {
            case "QUERY":
                // 补充 schema 提示
                result = "[QUERY意图] " + result;
                break;
            case "ANALYSIS":
                result = "[ANALYSIS意图] 请分析: " + result;
                break;
            case "ACTION":
                result = "[ACTION意图] 请执行: " + result;
                break;
            default:
                // CHAT / UNKNOWN 不改写
                break;
        }

        return result;
    }

    private String truncate(String s) {
        return s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }
}
