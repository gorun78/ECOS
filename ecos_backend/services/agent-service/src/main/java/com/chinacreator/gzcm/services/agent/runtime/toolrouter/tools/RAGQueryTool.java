package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentToolEndpointResolver;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@code RAGQuery} 工具腿 —— 向量检索问答改指<b>知识属主</b>RAG 端点
 * （#146j/B4-3e-91，收口 <b>D-B4-49 余项</b> 只读直连子项）。
 *
 * <p><b>收口前的形态为什么必须废</b>：本腿原为
 * {@code AgentToolDao#searchKnowledgeChunks} 在 agent 进程里直查知识属主的
 * 检索台账（按向量相似度取 topK 文档块），绕开属主的检索管道（分词／重排／
 * 权限过滤都在属主侧）。agent 进程自持这段检索 SQL 等于在进程内复刻了
 * 属主的知识检索管线的一个降级副本。</p>
 *
 * <p><b>现口径</b>：①入参 {@code queryText} 保持工具契约，映射为属主请求体
 * 键 {@code query}；{@code topK} 缺省 5（与属主端点同一缺省，收口前同值）；
 * 关键词为任意文本，走 JSON 请求体不需要 URL 编码；②基址经
 * {@link AgentToolEndpointResolver#knowledgeBaseUrl()}（属主
 * {@code dccheng}，route-manifest {@code /api/v1/knowledge} 下登记）；
 * ③端点即 V55 工具种子 {@code tool-017} 登记的
 * {@code POST /api/v1/knowledge/rag}（属主 {@code RagController#ragQuery}，
 * live 实证 200）；④结论经 {@link OwnerPostLeg#post} 骨架归一成
 * {@link Outcome}，属主 {@code data} 段（检索结果）原样透传。</p>
 *
 * <p><b>检索成败的机读面</b>：属主 {@code code==0} 即
 * {@link Outcome#DATA_RETURNED}（属主的检索管道跑通并返回了结果段，块列表可能
 * 为空属正常无命中）；非零码与 404/400 等状态行由骨架分态如实回传。</p>
 */
@Component
public class RAGQueryTool implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(RAGQueryTool.class);

    /**
     * kb 属主端点：RAG 向量检索（V55 种子 {@code tool-017} 登记的端点）。
     *
     * <p>类前缀 {@code /api/v1/knowledge/rag} 之下挂 {@code @PostMapping("/rag")}；
     * 本走 V55 登记的完整路径形态（无尾重段），与种子逐字符一致。</p>
     */
    static final String PATH_RAG = "/api/v1/knowledge/rag";

    /** 载荷键：检索语句（与收口前 {@code query} 键同名，只增不改）。 */
    static final String KEY_QUERY = "query";

    /** 载荷键：取回块数上限（收口前后同名）。 */
    static final String KEY_TOP_K = "topK";

    /** 属主请求体键：检索语句（属主 {@code RagController#ragQuery} 读此键）。 */
    static final String BODY_QUERY = "query";

    /** 属主请求体键：取回块数上限。 */
    static final String BODY_TOP_K = "topK";

    /** 缺省取回块数（与属主端点缺省、收口前缺省同值）。 */
    static final int DEFAULT_TOP_K = 5;

    /** 族名（用于 {@link AgentToolResponses#unreachable(String)} 定位断在哪条腿）。 */
    private static final String FAMILY = "knowledge";

    private final RestTemplate restTemplate;

    private final AgentToolEndpointResolver endpoints;

    public RAGQueryTool(RestTemplate restTemplate, AgentToolEndpointResolver endpoints) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        Map<String, Object> safe = params == null ? Map.of() : params;
        String queryText = asText(safe.get("queryText"));

        Optional<String> base = endpoints.knowledgeBaseUrl();
        if (base.isEmpty()) {
            return Outcome.stateOn(AgentToolResponses.unreachable(FAMILY), Outcome.UNREACHABLE);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put(BODY_QUERY, queryText == null ? "" : queryText);
        body.put(BODY_TOP_K, topKOf(safe.get("topK")));

        OwnerPostLeg.Reply reply = OwnerPostLeg.post(restTemplate, base.get(), PATH_RAG, body,
                Outcome.DATA_RETURNED);
        if (!reply.outcome().succeeded()) {
            return reply.payload();
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put(KEY_QUERY, queryText == null ? "" : queryText);
        extra.put(KEY_TOP_K, topKOf(safe.get("topK")));
        return reply.payloadWith(extra);
    }

    @Override
    public boolean supports(String toolCode) {
        return "RAGQuery".equals(toolCode);
    }

    /** topK 取值口径与收口前／属主一致：给了数字就用它（上限口径属属主侧，本腿只透传）。 */
    private static int topKOf(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return DEFAULT_TOP_K;
    }

    private static String asText(Object value) {
        return value == null ? null : value.toString().trim();
    }
}
