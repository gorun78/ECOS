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
 * {@code TriggerPipeline} 工具腿 —— 把「触发一条管道执行」交给<b>数据引擎属主</b>，
 * 不再在 agent 进程里往属主的运行台账插一行（#142/B4-3e-22，收口差距 <b>D-B4-49 余项</b>写腿之一）。
 *
 * <p><b>收口前的形态为什么必须废</b>（HEAD 实测，不是推断）：本腿此前是
 * {@code runId = UUID.randomUUID(); dao.insertPipelineRun(runId, taskId); return Map.of("status","triggered")}，
 * 而 DAO 的实现是 {@code INSERT INTO ecos_pipeline_run (id, task_id, status, created_at) VALUES(?,?,'PENDING',NOW())}。
 * 三件事同时错：①<b>越权写属主台账</b>——{@code ecos_pipeline_run} 的真实属主是 data-engine
 * （{@code PipelineRunDao}／{@code PipelineTaskDao}／{@code PipelineTaskDao} 都在写它），
 * agent 进程绕开属主的写通道与状态机，自己造一个 {@code PENDING} 行，违反铁律约束 5；
 * ②<b>根本没触发任何执行</b>——插行不等于跑管道，属主的调度器不会因此动一步，
 * 而本腿却回 {@code "triggered"}，等于向 LLM 伪造成功；③<b>自造 runId</b>——
 * 台账主键由调用方生成，属主侧的 run 与其 {@code tasks/{id}/runs} 视图对不上号。</p>
 *
 * <p><b>现口径</b>：①入参 {@code id} 必须过路径段形状判据（{@link OwnerPostLeg#safePathSegment}），
 * 不过即 {@link Outcome#GUARD_REJECTED} <b>且不发请求</b>——id 要拼进 URL 路径，
 * 带 {@code /} 或 {@code ?} 就是让 LLM 决定这条请求打到哪个端点；②基址经
 * {@link AgentToolEndpointResolver#dataBaseUrl()}（属主 {@code datanet}，禁内联 {@code localhost:8080}）；
 * ③端点即 V55 工具种子里为 {@code TriggerPipeline} 登记的那一条
 * {@code POST /api/v1/engine/data/pipeline/tasks/{id}/run}（{@code docs/50-db_script/02-flyway-migration/gateway/V55__ecos_tool_seed.sql}
 * 第 11 行 {@code tool-007}），收口前的实现反而没有走它，与 #142/B4-3e-16 对 {@code QueryPhysicalTable}
 * 的裁决依据同形；④结论一律按 {@link Outcome} 分态、由 {@link OwnerPostLeg} 归一，
 * <b>属主的受理凭据（runId 等）只透传、不自造</b>。</p>
 *
 * <p><b>本腿今天在真环境里的实际结局（照登，不粉饰）</b>：属主
 * {@code PipelineTaskController#triggerRun} 已是<b>只读桩</b>——它无条件
 * {@code throw readonlyConflict("POST /tasks/{id}/run")}（YAML 管道模型已只读，执行归
 * JSON DAG 模型 {@code /definitions/{id}/execute}），所以本腿在真环境的结论是
 * {@link Outcome#OWNER_CHANNEL_CLOSED}（409），不是成功、也不是无权限。
 * 这正是改法的价值：<b>把「这条变更通道已按裁决下线」如实报给调用方</b>，
 * 而不是像收口前那样插一行幽灵台账再回 {@code triggered}。
 * 若属主日后接回执行通道，本腿无需改动即自动变成 {@link Outcome#ACCEPTED}。</p>
 *
 * <p><b>机读面的有意订正</b>：收口前的 {@code status:"triggered"} 是伪造字段，本批删除，
 * 统一为族内单源键面 {@code available}／{@code state}／{@code message}（{@link AgentToolResponses}）。
 * 入参键 {@code id} 与成功时透传的属主 {@code data} 段只增不改。</p>
 */
@Component
public class TriggerPipelineTool implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(TriggerPipelineTool.class);

    /**
     * data-engine 属主端点：按任务触发一次管道执行（V55 种子 {@code tool-007} 登记的端点）。
     *
     * <p>占位符段刻意保持与种子、与属主 {@code @PostMapping("/tasks/{id}/run")} <b>同一形态</b>
     * （{@code {taskId}}），这样 {@code AgentToolPathReachabilityTest} 的模板归一能把它和真
     * Controller 路径对上——换成 {@code %s} 形态会被该门判成幻影路径（可达性判据按花括号归一）。
     * 填入的值必须已过 {@link OwnerPostLeg#safePathSegment}；族→属主映射
     * （{@code /api/v1/engine/data} → {@code datanet}）由该门按 route-manifest 校验。</p>
     */
    static final String PATH_TASK_RUN = "/api/v1/engine/data/pipeline/tasks/{taskId}/run";

    /** 路径占位符（与 {@link #PATH_TASK_RUN} 配对，禁在调用点重复写字面量）。 */
    static final String SEGMENT_TASK_ID = "{taskId}";

    /** 触发来源标注（属主 {@code triggeredBy} 查询参数有默认值 {@code manual}，本腿如实署明来源）。 */
    private static final String QUERY_TRIGGERED_BY = "?triggeredBy=agent-tool";

    /** 族名（用于 {@link AgentToolResponses#unreachable(String)} 定位断在哪条腿）。 */
    private static final String FAMILY = "data";

    /** 载荷键：任务 ID（与收口前同名，只增不改）。 */
    static final String KEY_TASK_ID = "taskId";

    private final RestTemplate restTemplate;

    private final AgentToolEndpointResolver endpoints;

    public TriggerPipelineTool(RestTemplate restTemplate, AgentToolEndpointResolver endpoints) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        Map<String, Object> safe = params == null ? Map.of() : params;
        String taskId = asText(safe.get("id"));

        if (!OwnerPostLeg.safePathSegment(taskId)) {
            log.warn("[agent-tool] TriggerPipeline 入参形状判据拒绝，未发起调用: id={}", taskId);
            return Outcome.payload(Outcome.GUARD_REJECTED,
                    "任务 ID 形状不合法（只允许 1-64 位字母/数字/_-.:），本次未发起调用");
        }

        Optional<String> base = endpoints.dataBaseUrl();
        if (base.isEmpty()) {
            return Outcome.stateOn(AgentToolResponses.unreachable(FAMILY), Outcome.UNREACHABLE);
        }

        OwnerPostLeg.Reply reply = OwnerPostLeg.post(restTemplate, base.get(),
                PATH_TASK_RUN.replace(SEGMENT_TASK_ID, taskId) + QUERY_TRIGGERED_BY,
                Map.of(), Outcome.ACCEPTED);
        if (!reply.outcome().succeeded()) {
            return reply.payload();
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put(KEY_TASK_ID, taskId);
        return reply.payloadWith(extra);
    }

    @Override
    public boolean supports(String toolCode) {
        return "TriggerPipeline".equals(toolCode);
    }

    private static String asText(Object value) {
        return value == null ? null : value.toString().trim();
    }
}
