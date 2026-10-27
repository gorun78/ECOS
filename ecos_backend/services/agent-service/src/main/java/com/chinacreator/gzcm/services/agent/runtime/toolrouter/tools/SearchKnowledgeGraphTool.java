package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentToolEndpointResolver;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@code SearchKnowledgeGraph} 工具腿 —— 图谱节点检索改指<b>知识属主</b>搜索端点
 * （#146j/B4-3e-91，收口 <b>D-B4-49 余项</b> 只读直连子项）。
 *
 * <p><b>收口前的形态为什么必须废</b>：本腿原是
 * {@code AgentToolDao#searchGraphNodes} ＋ {@code AgentToolDao#searchGraphEdges}
 * 两条 SQL 并扫知识属主的节点与边台账后拼成 {@code nodes}＋{@code edges}
 * 键面。属主侧只登记了节点检索（{@code KnowledgeGraphController#search}，
 * 只回节点列表）——<b>属主没有「按关键词查边」的端点</b>，这正是 agent 进程私带
 * 一张「双腿检索」视图的原因。现口径收敛到属主真值：</p>
 *
 * <p>①入参键 {@code q} 保持工具契约，经 {@link UriUtils#encodeQueryParam}
 * 编码后拼进 {@code ?q=}；②基址经 {@link AgentToolEndpointResolver#knowledgeBaseUrl()}
 * （属主 {@code dccheng}）；③端点 {@code GET /api/v1/knowledge/search?q=…}
 * （属主 {@code KnowledgeGraphController#search}，V55 种子 {@code tool-014}
 * 本批订正为真属主路径）；④结论经 {@link OwnerPostLeg#get} 骨架归一成
 * {@link Outcome}，属主 {@code data} 段（节点列表）原样透传。收口前的
 * {@code edges} 段属主侧无对应读面（<b>删除，不代造</b>）——若后续需要平均长度
 * 或邻边视图，属主侧立端点后由 V55 种子新一行登记，本腿不先在机读面上占位。</p>
 */
@Component
public class SearchKnowledgeGraphTool implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(SearchKnowledgeGraphTool.class);

    /**
     * kb 属主端点：按关键词检索节点（V55 种子 {@code tool-014} 本批订正后的路径；
     * 属主 {@code KnowledgeGraphController#search}，查询参数名 {@code q}）。
     */
    static final String PATH_KG_SEARCH = "/api/v1/knowledge/search";

    /** 属主查询参数名：关键词。 */
    static final String QUERY_Q = "q";

    /** 载荷键：检索关键词（收口前后同名）。 */
    static final String KEY_QUERY = "query";

    /** 族名（用于 {@link AgentToolResponses#unreachable(String)} 定位断在哪条腿）。 */
    private static final String FAMILY = "knowledge";

    private final RestTemplate restTemplate;

    private final AgentToolEndpointResolver endpoints;

    public SearchKnowledgeGraphTool(RestTemplate restTemplate, AgentToolEndpointResolver endpoints) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        Map<String, Object> safe = params == null ? Map.of() : params;
        String query = asText(safe.get("q"));
        String q = query == null ? "" : query;

        Optional<String> base = endpoints.knowledgeBaseUrl();
        if (base.isEmpty()) {
            return Outcome.stateOn(AgentToolResponses.unreachable(FAMILY), Outcome.UNREACHABLE);
        }

        OwnerPostLeg.Reply reply = OwnerPostLeg.get(restTemplate, base.get(),
                PATH_KG_SEARCH + "?" + QUERY_Q + "="
                        + UriUtils.encodeQueryParam(q, StandardCharsets.UTF_8),
                Outcome.DATA_RETURNED);
        if (!reply.outcome().succeeded()) {
            return reply.payload();
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put(KEY_QUERY, q);
        return reply.payloadWith(extra);
    }

    @Override
    public boolean supports(String toolCode) {
        return "SearchKnowledgeGraph".equals(toolCode);
    }

    private static String asText(Object value) {
        return value == null ? null : value.toString().trim();
    }
}
