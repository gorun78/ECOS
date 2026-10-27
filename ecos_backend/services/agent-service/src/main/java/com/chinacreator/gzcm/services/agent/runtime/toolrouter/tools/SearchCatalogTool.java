package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentToolEndpointResolver;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * {@code SearchCatalog} 工具腿 —— 目录检索改指<b>数据属主</b>目录端点
 * （#146j/B4-3e-91，收口 <b>D-B4-49 余项</b> 只读直连子项）。
 *
 * <p><b>收口前的形态为什么必须废</b>：本腿原为 {@code AgentToolDao#searchCatalog}
 * 在 agent 进程里直查数据属主的目录台账（schema 侧上一轮已收口的
 * {@code datascreen_*} 域表），按属主归属判据 agent 进程不该自己持 SQL——
 * 属主日后改分片／加 RLS 组切分，这条腿永远不知情。</p>
 *
 * <p><b>现口径</b>：①入参键 {@code q} 保持工具契约（API 只增不改），查值经
 * {@link UriUtils#encodeQueryParam} 编码后拼进查询串（LLM 给的关键词可以是任意文本）；
 * ②基址经 {@link AgentToolEndpointResolver#dataBaseUrl()}（属主 {@code datanet}）；
 * ③端点即 V55 工具种子 {@code tool-003} 登记的
 * {@code GET /api/v1/datanet/catalog/search?keyword=…}（属主
 * {@code CatalogController#search}，查询参数名 {@code keyword} 经 live 取证）；
 * ④结论经 {@link OwnerPostLeg#get} 骨架归一成 {@link Outcome}，属主 {@code data}
 * 段原样透传，本腿不再自组 {@code items}/{@code total}（条数归属主侧统计或
 * LLM 自行读数，agent 侧自报一个基于「本页透传行数」的数字是假口径）。</p>
 */
@Component
public class SearchCatalogTool implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(SearchCatalogTool.class);

    /**
     * data-engine 属主端点：目录关键字检索（V55 种子 {@code tool-003} 登记的端点）。
     *
     * <p>属主前缀 {@code /api/v1/datanet} 已在 route-manifest 下登记（owner
     * {@code datanet}），族→属主映射由 {@code AgentToolPathReachabilityTest} 校验。</p>
     */
    static final String PATH_CATALOG_SEARCH = "/api/v1/datanet/catalog/search";

    /** 载荷键：检索关键词（收口前后工具契约同名）。 */
    static final String KEY_KEYWORD = "keyword";

    /** 族名（用于 {@link AgentToolResponses#unreachable(String)} 定位断在哪条腿）。 */
    private static final String FAMILY = "data";

    private final RestTemplate restTemplate;

    private final AgentToolEndpointResolver endpoints;

    public SearchCatalogTool(RestTemplate restTemplate, AgentToolEndpointResolver endpoints) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        Map<String, Object> safe = params == null ? Map.of() : params;
        String keyword = asText(safe.get("q"));
        String query = keyword == null ? "" : keyword;

        Optional<String> base = endpoints.dataBaseUrl();
        if (base.isEmpty()) {
            return Outcome.stateOn(AgentToolResponses.unreachable(FAMILY), Outcome.UNREACHABLE);
        }
        OwnerPostLeg.Reply reply = OwnerPostLeg.get(restTemplate, base.get(),
                PATH_CATALOG_SEARCH + "?keyword="
                        + UriUtils.encodeQueryParam(query, StandardCharsets.UTF_8),
                Outcome.DATA_RETURNED);
        if (!reply.outcome().succeeded()) {
            return reply.payload();
        }
        Map<String, Object> extra = Map.of(KEY_KEYWORD, query);
        return reply.payloadWith(extra);
    }

    @Override
    public boolean supports(String toolCode) {
        return "SearchCatalog".equals(toolCode);
    }

    private static String asText(Object value) {
        return value == null ? null : value.toString().trim();
    }
}
