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
 * {@code GetTableSchema} 工具腿 —— 取资源字段列表改指<b>数据属主</b>元数据端点
 * （#146j/B4-3e-91，收口 <b>D-B4-49 余项</b> 只读直连子项）。
 *
 * <p><b>收口前的形态为什么必须废</b>：本腿原为 {@code AgentToolDao#listTableSchemaFields}
 * 直读 {@code ecos_metadata_field}（数据属主 metadata 台账），SQL 文本里就用
 * {@code WHERE resource_id = ?}，属主归属判据被 agent 进程绕过——
 * 数据属主日后若改单表分片／不管名／加 RLS 组切分，这条腿就再也不知情。
 * （DDL 侧另有一并登记的表名漂移面：该表在 DDL 单源里 {@code CREATE TABLE ... ecos_metadata_field}
 * 实扫 0 枚，归 P0-9 表名漂移缺陷，非本批 DDL 范围。）</p>
 *
 * <p><b>现口径</b>：①入参 {@code resourceId} 必须过路径段形状判据
 * （{@link OwnerPostLeg#safePathSegment}），不过即 {@link Outcome#GUARD_REJECTED}
 * 且不发起调用；②基址经 {@link AgentToolEndpointResolver#dataBaseUrl()}（属主
 * {@code datanet}，route-manifest {@code /api/v1/datanet} 下登记）；③端点即 V55
 * 工具种子 {@code tool-002} 登记的
 * {@code GET /api/v1/datanet/metadata/fields/{resourceId}}（属主
 * {@code MetadataController#fields}）；④结论经 {@link OwnerPostLeg#get} 骨架归一成
 * {@link Outcome}，属主 {@code data} 段原样透传。</p>
 */
@Component
public class GetTableSchemaTool implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(GetTableSchemaTool.class);

    /**
     * data-engine 属主端点：按资源 ID 列字段（V55 种子 {@code tool-002} 登记的端点）。
     *
     * <p>占位符段刻意保持与属主 {@code @GetMapping("/fields/{resourceId}")} 同一形态
     * （{@code {resourceId}}），使 {@code AgentToolPathReachabilityTest} 的模板归一能把本腿
     * 与真 Controller 对上（写成 {@code %s} 会被判成幻影路径）。</p>
     */
    static final String PATH_METADATA_FIELDS = "/api/v1/datanet/metadata/fields/{resourceId}";

    /** 路径占位符（与 {@link #PATH_METADATA_FIELDS} 配对，禁在调用点重复写字面量）。 */
    static final String SEGMENT_RESOURCE_ID = "{resourceId}";

    /** 载荷键：资源 ID（收口前后同名）。 */
    static final String KEY_RESOURCE_ID = "resourceId";

    /** 族名（用于 {@link AgentToolResponses#unreachable(String)} 定位断在哪条腿）。 */
    private static final String FAMILY = "data";

    private final RestTemplate restTemplate;

    private final AgentToolEndpointResolver endpoints;

    public GetTableSchemaTool(RestTemplate restTemplate, AgentToolEndpointResolver endpoints) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        Map<String, Object> safe = params == null ? Map.of() : params;
        String resourceId = asText(safe.get("resourceId"));

        if (!OwnerPostLeg.safePathSegment(resourceId)) {
            log.warn("[agent-tool] GetTableSchema 入参形状判据拒绝，未发起调用: resourceId={}", resourceId);
            return Outcome.payload(Outcome.GUARD_REJECTED,
                    "资源 ID 形状不合法（只允许 1-64 位字母/数字/_-.:），本次未发起调用");
        }

        Optional<String> base = endpoints.dataBaseUrl();
        if (base.isEmpty()) {
            return Outcome.stateOn(AgentToolResponses.unreachable(FAMILY), Outcome.UNREACHABLE);
        }
        OwnerPostLeg.Reply reply = OwnerPostLeg.get(restTemplate, base.get(),
                PATH_METADATA_FIELDS.replace(SEGMENT_RESOURCE_ID, resourceId), Outcome.DATA_RETURNED);
        if (!reply.outcome().succeeded()) {
            return reply.payload();
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put(KEY_RESOURCE_ID, resourceId);
        return reply.payloadWith(extra);
    }

    @Override
    public boolean supports(String toolCode) {
        return "GetTableSchema".equals(toolCode);
    }

    private static String asText(Object value) {
        return value == null ? null : value.toString().trim();
    }
}
