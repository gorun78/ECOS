package com.chinacreator.gzcm.engine.ai.controller;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.llm.LLMGatewayService;
import com.chinacreator.gzcm.runtime.llm.scheduler.AgentResult;
import com.chinacreator.gzcm.engine.ai.dto.NlqResultVO;
import com.chinacreator.gzcm.engine.ai.dto.ObjectQlText;
import com.chinacreator.gzcm.engine.ai.nlq.SemanticQueryService;
import com.chinacreator.gzcm.engine.ai.nlq.ObjectQLParser;
import com.chinacreator.gzcm.engine.ai.service.NLQService;

/**
 * 自然语言查询 (NLQ) 控制器 — 将中文业务短语翻译为 ObjectQL 并执行。
 *
 * <h3>端点</h3>
 * <pre>
 * POST /api/query/nlq
 * Body: {"question": "华东高价值客户有哪些"}
 * Response: {
 *   "code": 0,
 *   "data": {
 *     "translated": "{\"entity\":\"Customer\",\"filter\":...}",
 *     "results": [{"id": "c1", "name": "某某公司", ...}]
 *   }
 * }
 * </pre>
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>接收自然语言问题</li>
 *   <li>调用 {@link SemanticQueryService#translate(String)} → ObjectQL JSON</li>
 *   <li>translate 返回 null → 调 Agent Runtime 解析（LLMGatewayService fallback）</li>
 *   <li>{@link ObjectQLParser#parse(String)} → SQL → JDBC 执行</li>
 *   <li>返回翻译结果 + 数据</li>
 * </ol>
 *
 * <p>DI-2 #147EA：回执改 {@link NlqResultVO}（{@code translated/results[/agent_answer][/message]}，
 * 结果列名转小写）；ObjectQL 的规范化与 JSON 文本化收口 {@link ObjectQlText}
 * （DSL 动态形态不冻结）。</p>
 */
@RestController
@RequestMapping("/api/v1/query")
public class NLQController {

    private final NLQService nlqService;
    private static final Logger log = LoggerFactory.getLogger(NLQController.class);
    private final SemanticQueryService semanticQueryService;
    private final LLMGatewayService llmGatewayService;

    public NLQController(NLQService nlqService, LLMGatewayService llmGatewayService) {
        this.nlqService = nlqService;
        this.llmGatewayService = llmGatewayService;
        this.semanticQueryService = new SemanticQueryService();
    }

    /**
     * 自然语言查询。
     *
     * @param body {"question": "华东高价值客户"}
     * @return 翻译后的 ObjectQL + 查询结果
     */
    @PostMapping("/nlq")
    public ApiResponse<NlqResultVO> nlq(@RequestBody Map<String, Object> body) {
        // F06-15：控制器禁 catch(Exception) —— 未预期异常交由 AiErrorAdvice 分类渲染（errorCode + traceId）；
        // 表缺失/SQL 失败一律 5xx，绝不回落 200 伪装成功（X-50）
        // 1. 提取问题
        Object questionObj = body.get("question");
        if (questionObj == null || questionObj.toString().trim().isEmpty()) {
            return ApiResponse.badRequest("缺少 question 字段");
        }
        String question = questionObj.toString().trim();
        log.info("NLQ 查询: {}", question);

        // 2. 语义翻译 → ObjectQL
        var objectQL = semanticQueryService.translate(question);

        // 3. 词典无匹配 → Agent Runtime fallback（调用 LLMGatewayService）
        if (objectQL == null) {
            log.warn("NLQ 词典无匹配: {}，进入 Agent Runtime fallback", question);
            AgentResult agentResult = llmGatewayService.execute("sysman", "nlq-fallback", question);
            if (agentResult.isSuccess()) {
                log.info("Agent Runtime fallback 成功: session={}, duration={}ms",
                        agentResult.getSessionId(), agentResult.getDurationMs());
                return ApiResponse.success(new NlqResultVO(
                        null, List.of(), agentResult.getContent(),
                        "词典无匹配，已通过 Agent Runtime 解析。"));
            }
            log.warn("Agent Runtime fallback 失败: {}", agentResult.getErrorMsg());
            return ApiResponse.success(new NlqResultVO(
                    null, List.of(), null, "词典无匹配，Agent Runtime 解析失败"));
        }

        // 4. 规范化 ObjectQL 格式（复合 filter → filters 数组）
        var normalizedQL = ObjectQlText.normalize(objectQL);
        log.info("NLQ normalized QL: {}", normalizedQL);
        String objectQLJson = ObjectQlText.toJson(normalizedQL);
        log.info("NLQ ObjectQL JSON: {}", objectQLJson);
        ObjectQLParser.ParsedQuery pq = ObjectQLParser.parse(objectQLJson);
        log.info("NLQ → ObjectQL: {} | SQL: {} | params={}", objectQLJson, pq.getSql(), pq.getParams());

        // 5. 执行 SQL（表不存在等故障交由 AiErrorAdvice 渲染 5xx，不再 200 掩蔽）
        var rows = pq.getParams().isEmpty()
                ? nlqService.queryForList(pq.getSql())
                : nlqService.queryForList(pq.getSql(), pq.getParamsArray());

        // 6. 字段名转小写并组装响应
        return ApiResponse.success(NlqResultVO.of(objectQLJson, rows));
    }
}
