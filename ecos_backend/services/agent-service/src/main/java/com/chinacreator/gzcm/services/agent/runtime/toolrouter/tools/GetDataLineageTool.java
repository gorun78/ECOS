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
 * {@code GetDataLineage} 工具腿 —— 管道血缘改指<b>数据属主</b>血缘端点
 * （#146j/B4-3e-91，收口 <b>D-B4-49 余项</b> 只读直连子项）。
 *
 * <p><b>收口前的形态为什么必须废</b>：本腿原是
 * {@code AgentToolDao#findPipeline} ＋ {@code AgentToolDao#listPipelineSteps}
 * 在 agent 进程里直查数据属主的管道定义与步骤台账 {@code ecos_pipeline_task}／
 * {@code ecos_pipeline_step}，并把两段结果拼成 {@code pipeline}＋{@code steps}
 * 键面——而属主侧本就没有这一对绑定键的读端点契约，是 agent 进程私自带的一张
 * 「血缘视图」。属主此后要改表结构／加行过滤，这条腿照样不知情。</p>
 *
 * <p><b>现口径</b>：①入参 {@code id} 必须过路径段形状判据
 * （{@link OwnerPostLeg#safePathSegment}），不过即 {@link Outcome#GUARD_REJECTED}
 * 且不发起调用——id 要拼进 URL 路径，带 {@code /} 或 {@code ?} 就是让 LLM 决定
 * 这条请求打到哪个端点；②基址经 {@link AgentToolEndpointResolver#dataBaseUrl()}
 * （属主 {@code datanet}）；③端点即属主
 * {@code DataLineageController#pipelineLineage} 的
 * {@code GET /api/v1/engine/data/lineage/pipeline/{taskId}}（占位符段与属主
 * {@code @GetMapping("/pipeline/{taskId}")} 同一形态，使
 * {@code AgentToolPathReachabilityTest} 的模板归一能把本腿与真 Controller 对上）；
 * ④结论经 {@link OwnerPostLeg#get} 骨架归一成 {@link Outcome}，属主 {@code data}
 * 段原样透传，本腿不再自组 {@code pipeline}／{@code steps} 键面。</p>
 */
@Component
public class GetDataLineageTool implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(GetDataLineageTool.class);

    /**
     * data-engine 属主端点：按管道任务 ID 取血缘（属主
     * {@code DataLineageController}，族前缀 {@code /api/v1/engine/data} 已在
     * route-manifest 登记 owner {@code datanet}）。
     */
    static final String PATH_LINEAGE_PIPELINE = "/api/v1/engine/data/lineage/pipeline/{taskId}";

    /** 路径占位符（与 {@link #PATH_LINEAGE_PIPELINE} 配对，禁在调用点重复写字面量）。 */
    static final String SEGMENT_TASK_ID = "{taskId}";

    /** 载荷键：管道任务 ID（收口前后同名）。 */
    static final String KEY_ID = "id";

    /** 族名（用于 {@link AgentToolResponses#unreachable(String)} 定位断在哪条腿）。 */
    private static final String FAMILY = "data";

    private final RestTemplate restTemplate;

    private final AgentToolEndpointResolver endpoints;

    public GetDataLineageTool(RestTemplate restTemplate, AgentToolEndpointResolver endpoints) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        Map<String, Object> safe = params == null ? Map.of() : params;
        String pipelineId = asText(safe.get("id"));

        if (!OwnerPostLeg.safePathSegment(pipelineId)) {
            log.warn("[agent-tool] GetDataLineage 入参形状判据拒绝，未发起调用: id={}", pipelineId);
            return Outcome.payload(Outcome.GUARD_REJECTED,
                    "管道 ID 形状不合法（只允许 1-64 位字母/数字/_-.:），本次未发起调用");
        }

        Optional<String> base = endpoints.dataBaseUrl();
        if (base.isEmpty()) {
            return Outcome.stateOn(AgentToolResponses.unreachable(FAMILY), Outcome.UNREACHABLE);
        }
        OwnerPostLeg.Reply reply = OwnerPostLeg.get(restTemplate, base.get(),
                PATH_LINEAGE_PIPELINE.replace(SEGMENT_TASK_ID, pipelineId), Outcome.DATA_RETURNED);
        if (!reply.outcome().succeeded()) {
            return reply.payload();
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put(KEY_ID, pipelineId);
        return reply.payloadWith(extra);
    }

    @Override
    public boolean supports(String toolCode) {
        return "GetDataLineage".equals(toolCode);
    }

    private static String asText(Object value) {
        return value == null ? null : value.toString().trim();
    }
}
