package com.chinacreator.gzcm.engine.ai.oag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Node 1: 意图分类器 — 识别用户查询意图。
 *
 * <p>输出意图标签：QUERY(数据查询), ANALYSIS(分析), ACTION(操作),
 * CHAT(闲聊), UNKNOWN(未知)</p>
 */
@Component
public class IntentClassifierNode implements OagNode {

    private static final Logger log = LoggerFactory.getLogger(IntentClassifierNode.class);

    @Override
    public OagPipelineContext execute(OagPipelineContext ctx) {
        ctx.setCurrentNode("IntentClassifier");

        String query = ctx.getUserQuery();
        if (query == null || query.isBlank()) {
            ctx.setIntent("UNKNOWN");
            ctx.getMetadata().put("intentSource", "default");
            log.debug("[OAG:{}] 空查询 → UNKNOWN", ctx.getTraceId());
            return ctx;
        }

        String intent = classify(query);
        ctx.setIntent(intent);
        ctx.getMetadata().put("intentSource", "rule-based");
        log.info("[OAG:{}] 意图={} query='{}'", ctx.getTraceId(), intent, truncate(query));

        return ctx;
    }

    /**
     * 基于规则的关键词意图分类（生产环境可替换为 LLM 分类）。
     */
    private String classify(String query) {
        String lower = query.toLowerCase();

        // 数据查询类关键词
        if (containsAny(lower, "查询", "select", "统计", "count", "sum", "group by",
                "多少", "有哪些", "列出", "list", "find", "where", "汇总", "报表")) {
            return "QUERY";
        }

        // 分析类关键词
        if (containsAny(lower, "分析", "趋势", "对比", "同比", "环比", "占比",
                "趋势图", "排名", "top", "analyze", "analysis", "预测")) {
            return "ANALYSIS";
        }

        // 操作类关键词
        if (containsAny(lower, "创建", "修改", "删除", "新增", "更新", "提交",
                "审批", "create", "update", "delete", "insert", "submit", "执行")) {
            return "ACTION";
        }

        // 闲聊类关键词
        if (containsAny(lower, "你好", "谢谢", "再见", "hello", "hi", "帮助",
                "help", "什么是", "介绍一下", "怎么样", "如何")) {
            return "CHAT";
        }

        return "UNKNOWN";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private String truncate(String s) {
        return s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }
}
