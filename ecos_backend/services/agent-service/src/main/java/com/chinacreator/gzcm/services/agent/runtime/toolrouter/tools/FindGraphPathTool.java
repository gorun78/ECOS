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
 * {@code FindGraphPath} 工具腿 —— 图谱路径查找改指<b>知识属主</b>最短路端点
 * （#146j/B4-3e-91，收口 <b>D-B4-49 余项</b> 只读直连子项）。
 *
 * <p><b>收口前的形态为什么必须废</b>：本腿原为
 * {@code AgentToolDao#listGraphEdgesForPath} 在 agent 进程里扫知识属主的边台账，
 * 按起／止节点名过滤后就回给 LLM，并自挂一个 {@code algorithm="shortest-path"}
 * 的键面条陈——但这里<b>没有任何最短路计算</b>，只是把边筛出来，键面硬报了
 * 「shortest-path」。属主侧本就有真计算（{@code KnowledgeGraphService#getShortestPath}），
 * agent 进程自持 SQL 相当于复刻了一份不计算的假最短路。</p>
 *
 * <p><b>现口径</b>：①入参保持工具契约 {@code from}/{@code to}，两者均过
 * {@link OwnerPostLeg#safePathSegment}（路径段形状判据）——本腿走查询串而非
 * 路径段，形状判据仍须过（两值任一不合法即 {@link Outcome#GUARD_REJECTED}
 * 且不发请求，防止 LLM 传入带 {@code &}／{@code #} 的尾段把查询串改写）；
 * 拼进查询串时再过 {@link UriUtils#encodeQueryParam}；②<b>入参到端点参数
 * 名的一次映射</b>：属主 {@code KnowledgeGraphController#getShortestPath}
 * 读的是 {@code s}/{@code t}（不是 {@code from}/{@code to}），工具契约保留
 * {@code from}/{@code to}（API 只增不改），本腿内部做一对一变量改名——
 * 该映射关系只在此类内说清，不散到属主侧改签名；
 * ③基址经 {@link AgentToolEndpointResolver#knowledgeBaseUrl()}（属主
 * {@code dccheng}，route-manifest {@code /api/v1/knowledge} 下登记）；
 * ④端点 {@code GET /api/v1/knowledge/path?s=…&t=…}（V55 种子 {@code tool-015}
 * 本批订正为真属主路径），结论经 {@link OwnerPostLeg#get} 骨架归一成
 * {@link Outcome}，属主 {@code data} 段（最短路计算结果）原样透传，
 * {@code algorithm} 键面假报已删除。</p>
 */
@Component
public class FindGraphPathTool implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(FindGraphPathTool.class);

    /**
     * kb 属主端点：两节点间最短路（V55 种子 {@code tool-015} 本批订正后的路径；
     * 属主 {@code KnowledgeGraphController#getShortestPath}，查询参数名
     * {@code s}／{@code t}）。
     */
    static final String PATH_GRAPH_PATH = "/api/v1/knowledge/path";

    /** 属主查询参数名：起节点。 */
    static final String QUERY_S = "s";

    /** 属主查询参数名：止节点。 */
    static final String QUERY_T = "t";

    /** 载荷键：起节点（工具契约）。 */
    static final String KEY_FROM = "from";

    /** 载荷键：止节点（工具契约）。 */
    static final String KEY_TO = "to";

    /** 族名（用于 {@link AgentToolResponses#unreachable(String)} 定位断在哪条腿）。 */
    private static final String FAMILY = "knowledge";

    private final RestTemplate restTemplate;

    private final AgentToolEndpointResolver endpoints;

    public FindGraphPathTool(RestTemplate restTemplate, AgentToolEndpointResolver endpoints) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        Map<String, Object> safe = params == null ? Map.of() : params;
        String from = asText(safe.get(KEY_FROM));
        String to = asText(safe.get(KEY_TO));

        if (!OwnerPostLeg.safePathSegment(from) || !OwnerPostLeg.safePathSegment(to)) {
            log.warn("[agent-tool] FindGraphPath 入参形状判据拒绝，未发起调用: from={}, to={}", from, to);
            return Outcome.payload(Outcome.GUARD_REJECTED,
                    "节点 ID 形状不合法（只允许 1-64 位字母/数字/_-.:），本次未发起调用");
        }

        Optional<String> base = endpoints.knowledgeBaseUrl();
        if (base.isEmpty()) {
            return Outcome.stateOn(AgentToolResponses.unreachable(FAMILY), Outcome.UNREACHABLE);
        }

        String query = QUERY_S + "=" + UriUtils.encodeQueryParam(from, StandardCharsets.UTF_8)
                + "&" + QUERY_T + "=" + UriUtils.encodeQueryParam(to, StandardCharsets.UTF_8);

        OwnerPostLeg.Reply reply = OwnerPostLeg.get(restTemplate, base.get(),
                PATH_GRAPH_PATH + "?" + query, Outcome.DATA_RETURNED);
        if (!reply.outcome().succeeded()) {
            return reply.payload();
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put(KEY_FROM, from);
        extra.put(KEY_TO, to);
        return reply.payloadWith(extra);
    }

    @Override
    public boolean supports(String toolCode) {
        return "FindGraphPath".equals(toolCode);
    }

    private static String asText(Object value) {
        return value == null ? null : value.toString().trim();
    }
}
