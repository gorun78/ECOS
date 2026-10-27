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
 * {@code ExecuteExpertRule} 工具腿 —— 执行专家规则改指<b>知识属主</b>规则执行端点
 * （#146j/B4-3e-91，收口 <b>D-B4-49 余项</b> 只读直连子项）。
 *
 * <p><b>收口前的形态为什么必须废</b>（两件事同时错）：本腿原是
 * {@code AgentToolDao#findExpertRule} 直读知识属主的规则台账后<b>只回显规则元数据</b>，
 * 却同时挂上自造的 {@code status="executed"} 与 {@code evaluatedAt=now()} 两个字段
 * ——<b>没有执行任何规则却报了「已执行」</b>，向 LLM 伪造成功（写腿收口
 * #142/B4-3e-22 关掉的是「插行冒充触发」，这边是「不执行冒充已评」，同一族 
 * 假执行面）。本批把 {@code status}/{@code evaluatedAt} 伪字段删除：结论一律
 * 由属主应答归型。</p>
 *
 * <p><b>现口径</b>：①入参 {@code ruleId} 必须过路径段形状判据
 * （{@link OwnerPostLeg#safePathSegment}），不过即 {@link Outcome#GUARD_REJECTED}
 * 且不发请求；②基址经 {@link AgentToolEndpointResolver#knowledgeBaseUrl()}
 * （属主 {@code dccheng}）；③端点即 V55 工具种子 {@code tool-016} 订正后登记的
 * {@code POST /api/v1/knowledge/rules/{ruleId}/execute}（属主
 * {@code ExpertRuleController#executeRule}，请求体为规则上下文 {@code Map}，
 * 本腿传空上下文＝按规则自足判据执行）；④结论经 {@link OwnerPostLeg#post}
 * 骨架归一成 {@link Outcome}，属主 {@code data} 段（规则执行产出）原样透传，
 * 本腿不自造任何执行凭据。</p>
 */
@Component
public class ExecuteExpertRuleTool implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ExecuteExpertRuleTool.class);

    /**
     * kb 属主端点：执行单条专家规则（V55 种子 {@code tool-016} 注册的端点）。
     *
     * <p>占位符段与属主 {@code @PostMapping("/{ruleId}/execute")} 同一形态
     * （{@code {ruleId}}），使 {@code AgentToolPathReachabilityTest} 模板归一能把
     * 本腿与真 Controller 对上。</p>
     */
    static final String PATH_RULE_EXECUTE = "/api/v1/knowledge/rules/{ruleId}/execute";

    /** 路径占位符（与 {@link #PATH_RULE_EXECUTE} 配对，禁在调用点重复写字面量）。 */
    static final String SEGMENT_RULE_ID = "{ruleId}";

    /** 载荷键：规则 ID（收口前后同名）。 */
    static final String KEY_RULE_ID = "ruleId";

    /** 族名（用于 {@link AgentToolResponses#unreachable(String)} 定位断在哪条腿）。 */
    private static final String FAMILY = "knowledge";

    private final RestTemplate restTemplate;

    private final AgentToolEndpointResolver endpoints;

    public ExecuteExpertRuleTool(RestTemplate restTemplate, AgentToolEndpointResolver endpoints) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        Map<String, Object> safe = params == null ? Map.of() : params;
        String ruleId = asText(safe.get("ruleId"));

        if (!OwnerPostLeg.safePathSegment(ruleId)) {
            log.warn("[agent-tool] ExecuteExpertRule 入参形状判据拒绝，未发起调用: ruleId={}", ruleId);
            return Outcome.payload(Outcome.GUARD_REJECTED,
                    "规则 ID 形状不合法（只允许 1-64 位字母/数字/_-.:），本次未发起调用");
        }

        Optional<String> base = endpoints.knowledgeBaseUrl();
        if (base.isEmpty()) {
            return Outcome.stateOn(AgentToolResponses.unreachable(FAMILY), Outcome.UNREACHABLE);
        }

        OwnerPostLeg.Reply reply = OwnerPostLeg.post(restTemplate, base.get(),
                PATH_RULE_EXECUTE.replace(SEGMENT_RULE_ID, ruleId), Map.of(), Outcome.DATA_RETURNED);
        if (!reply.outcome().succeeded()) {
            return reply.payload();
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put(KEY_RULE_ID, ruleId);
        return reply.payloadWith(extra);
    }

    @Override
    public boolean supports(String toolCode) {
        return "ExecuteExpertRule".equals(toolCode);
    }

    private static String asText(Object value) {
        return value == null ? null : value.toString().trim();
    }
}
