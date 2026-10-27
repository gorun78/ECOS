package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentToolEndpointResolver;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * {@code ListDataSources} 工具腿 —— 列数据源改指<b>数据属主</b>端点
 * （#146j/B4-3e-91，收口 <b>D-B4-49 余项</b> 只读直连子项）。
 *
 * <p><b>收口前的形态为什么必须废</b>：本腿原为
 * {@code AgentToolDao#listDataSources()} 直接在 agent 进程里
 * {@code SELECT ... FROM ecos_datasource}，读的是数据属主的连接登记表
 * （数据源 host/port/credential 之类的连接参数都在这里），绕开 security 裁决与属主
 * 端点的列过滤/脱敏（铁律约束 3／5）；且表内连接参数类目字段以明文进 agent 线程内存
 * 后再原样透给 LLM。</p>
 *
 * <p><b>现口径</b>：基址经 {@link AgentToolEndpointResolver#dataBaseUrl()}（属主
 * {@code datanet}，route-manifest {@code /api/v1/datanet} 下登记）；端点即 V55 工具种子
 * {@code tool-001} 登记的 {@code GET /api/v1/datanet/datasource}（属主
 * {@code DataSourceController#list}）；结论经 {@link OwnerPostLeg#get} 骨架归一成
 * {@link Outcome}，属主 {@code data} 段原样透传。</p>
 */
@Component
public class ListDataSourcesTool implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ListDataSourcesTool.class);

    /**
     * data-engine 属主端点：数据源列表（V55 种子 {@code tool-001} 登记的端点）。
     *
     * <p>属主前缀 {@code /api/v1/datanet} 已在 route-manifest 下登记（owner
     * {@code datanet}），族→属主映射由 {@code AgentToolPathReachabilityTest} 校验；
     * 字段白名单由属主侧的既有读面声明决定（回落凭据、password 等敏感列不出
     * 本腿的显式契约）。</p>
     */
    static final String PATH_DATASOURCE_LIST = "/api/v1/datanet/datasource";

    /** 族名（用于 {@link AgentToolResponses#unreachable(String)} 定位断在哪条腿）。 */
    private static final String FAMILY = "data";

    private final RestTemplate restTemplate;

    private final AgentToolEndpointResolver endpoints;

    public ListDataSourcesTool(RestTemplate restTemplate, AgentToolEndpointResolver endpoints) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        Optional<String> base = endpoints.dataBaseUrl();
        if (base.isEmpty()) {
            return Outcome.stateOn(AgentToolResponses.unreachable(FAMILY), Outcome.UNREACHABLE);
        }
        OwnerPostLeg.Reply reply = OwnerPostLeg.get(restTemplate, base.get(), PATH_DATASOURCE_LIST,
                Outcome.DATA_RETURNED);
        if (!reply.outcome().succeeded()) {
            return reply.payload();
        }
        Map<String, Object> out = reply.payloadWith(null);
        out.putIfAbsent(AgentToolResponses.KEY_AVAILABLE, true);
        return out;
    }

    @Override
    public boolean supports(String toolCode) {
        return "ListDataSources".equals(toolCode);
    }
}
