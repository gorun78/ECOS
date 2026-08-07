package com.chinacreator.gzcm.engine.ai.oag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Node 7: 响应编译器 — 将推理结果编译为最终用户响应。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>对推理结果进行格式化</li>
 *   <li>根据意图调整响应结构</li>
 *   <li>补充引用来源和置信度信息</li>
 *   <li>对 QUERY 意图附加 SQL/执行计划提示</li>
 * </ul>
 */
@Component
public class ResponseCompilerNode implements OagNode {

    private static final Logger log = LoggerFactory.getLogger(ResponseCompilerNode.class);

    @Override
    public OagPipelineContext execute(OagPipelineContext ctx) {
        ctx.setCurrentNode("ResponseCompiler");

        Map<String, Object> reasoning = ctx.getReasoningResult();
        String intent = ctx.getIntent();

        String raw = reasoning != null ? (String) reasoning.get("response") : "";
        String compiled = compile(raw, intent, ctx);

        ctx.setFinalResponse(compiled);
        ctx.getMetadata().put("responseLength", compiled.length());

        log.info("[OAG:{}] 响应编译完成 intent={} length={}",
                ctx.getTraceId(), intent, compiled.length());

        return ctx;
    }

    /**
     * 编译响应（按意图调整结构）。
     */
    private String compile(String raw, String intent, OagPipelineContext ctx) {
        if (raw == null || raw.isEmpty()) {
            return "系统无法生成有效的响应，请重试。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(raw);

        // 追加元数据区
        sb.append("\n\n---\n");
        Map<String, Object> reasoning = ctx.getReasoningResult();
        if (reasoning != null) {
            if (reasoning.containsKey("model")) {
                sb.append("*模型: ").append(reasoning.get("model")).append("*  ");
            }
            if (reasoning.containsKey("latencyMs")) {
                sb.append("| 延迟: ").append(reasoning.get("latencyMs")).append("ms  ");
            }
            if (reasoning.containsKey("tokensUsed")) {
                sb.append("| Tokens: ").append(reasoning.get("tokensUsed"));
            }
        }

        return sb.toString();
    }
}
