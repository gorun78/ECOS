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
 * {@code RunQualityCheck} 工具腿 —— 把「跑一次质量规则评估」交给<b>数据引擎属主</b>，
 * 不再在 agent 进程里往一张<b>并不存在的表</b>插一行（#142/B4-3e-22，收口差距 <b>D-B4-49 余项</b>写腿之二）。
 *
 * <p><b>收口前的形态为什么必须废</b>（三处实测，不是推断）：本腿此前是
 * {@code checkId = UUID.randomUUID(); dao.insertQualityCheck(...); return "status":"triggered"}，
 * DAO 的实现是
 * {@code INSERT INTO ecos_quality_check (id, rule_id, datasource_id, table_name, sample_size, status, created_at) VALUES(...,'RUNNING',NOW())}。
 * ①<b>{@code ecos_quality_check} 是一张幽灵表</b>：DDL 单源目录
 * {@code docs/50-db_script/02-flyway-migration/} 全量 {@code V*.sql} 中 {@code CREATE TABLE ... ecos_quality_check}
 * 实扫 <b>0 枚</b>，全仓除本 DAO 外也没有任何读写方——即这条腿要么在运行时抛「表不存在」、
 * 要么在某个手工建过表的库里写一张没人读的台账；②<b>它根本没跑评估</b>：插一行 {@code RUNNING}
 * 不产生任何检查结果，却回 {@code "triggered"}，向 LLM 伪造成功；③<b>DQ 的评估台账属主是 data-engine</b>
 * （{@code QualityDao} 写 {@code ecos_quality_evaluation}），绕开属主写通道违反铁律约束 5。</p>
 *
 * <p><b>现口径</b>：①入参 {@code ruleId} 必须过路径段形状判据（{@link OwnerPostLeg#safePathSegment}），
 * 不过即 {@link Outcome#GUARD_REJECTED} 且不发请求；②基址经
 * {@link AgentToolEndpointResolver#dataBaseUrl()}（属主 {@code datanet}）；③端点即 V55 工具种子为
 * {@code RunQualityCheck} 登记的 {@code POST /api/v1/engine/data/quality/rules/{id}/evaluate}
 * （{@code V55__ecos_tool_seed.sql} 第 10 行 {@code tool-006}）；④请求体沿用种子的入参键面
 * {@code datasourceId}／{@code tableName}，另带 {@code sampleSize}（缺省 1000，与收口前一致，只增不改）；
 * ⑤结论按 {@link Outcome} 分态、由 {@link OwnerPostLeg} 归一，属主的 {@code data} 段原样透传，
 * 本腿不再自造 {@code checkId}。</p>
 *
 * <p><b>本腿今天在真环境里的实际结局（照登，不粉饰）</b>：属主
 * {@code QualityController#evaluateRule} 已按 PMO-48-A T4 下线为
 * {@code ApiResponse.error(410, "GONE", ...)}（DQ legacy 写通道封堵），所以真环境结论是
 * {@link Outcome#OWNER_CHANNEL_CLOSED}。这是<b>有意保留的如实回报</b>而不是待补的洞：
 * 质量域的新评估写通道归谁、按什么契约开，属 D-B4-49 余项里尚未裁决的部分，
 * 本批不开新端点（开一条属主写通道是架构级新增，需先定契约与台账归属）。</p>
 *
 * <p><b>机读面的有意订正</b>：收口前的 {@code status:"triggered"} 与自造的 {@code checkId} 均属伪造字段，
 * 本批删除，统一为族内单源键面 {@code available}／{@code state}／{@code message}；
 * 回显键 {@code ruleId}／{@code datasourceId}／{@code tableName}／{@code sampleSize} 只增不改。</p>
 */
@Component
public class RunQualityCheckTool implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(RunQualityCheckTool.class);

    /**
     * data-engine 属主端点：对单条质量规则做一次评估（V55 种子 {@code tool-006} 登记的端点）。
     *
     * <p>占位符段保持与种子、与属主 {@code @PostMapping("/rules/{ruleId}/evaluate")} 同一形态，
     * 使 {@code AgentToolPathReachabilityTest} 的模板归一能把本腿与真 Controller 对上
     * （写成 {@code %s} 会被判成幻影路径）。填入的值必须已过
     * {@link OwnerPostLeg#safePathSegment}；族→属主映射（{@code /api/v1/engine/data} →
     * {@code datanet}）由该门按 route-manifest 校验。</p>
     */
    static final String PATH_RULE_EVALUATE = "/api/v1/engine/data/quality/rules/{ruleId}/evaluate";

    /** 路径占位符（与 {@link #PATH_RULE_EVALUATE} 配对，禁在调用点重复写字面量）。 */
    static final String SEGMENT_RULE_ID = "{ruleId}";

    /** 族名（用于 {@link AgentToolResponses#unreachable(String)} 定位断在哪条腿）。 */
    private static final String FAMILY = "data";

    /** 缺省抽样规模（与收口前 {@code params} 缺省值一致，不改口径）。 */
    static final int DEFAULT_SAMPLE_SIZE = 1000;

    /** 载荷键：规则 ID（收口前后同名）。 */
    static final String KEY_RULE_ID = "ruleId";

    /** 载荷键：数据源 ID（收口前后同名）。 */
    static final String KEY_DATASOURCE_ID = "datasourceId";

    /** 载荷键：目标表（收口前后同名）。 */
    static final String KEY_TABLE_NAME = "tableName";

    /** 载荷键：抽样规模（收口前后同名）。 */
    static final String KEY_SAMPLE_SIZE = "sampleSize";

    private final RestTemplate restTemplate;

    private final AgentToolEndpointResolver endpoints;

    public RunQualityCheckTool(RestTemplate restTemplate, AgentToolEndpointResolver endpoints) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        Map<String, Object> safe = params == null ? Map.of() : params;
        String ruleId = asText(safe.get("ruleId"));
        String datasourceId = asText(safe.get("datasourceId"));
        String tableName = asText(safe.get("tableName"));
        int sampleSize = sampleSizeOf(safe.get("sampleSize"));

        if (!OwnerPostLeg.safePathSegment(ruleId)) {
            log.warn("[agent-tool] RunQualityCheck 入参形状判据拒绝，未发起调用: ruleId={}", ruleId);
            return Outcome.payload(Outcome.GUARD_REJECTED,
                    "规则 ID 形状不合法（只允许 1-64 位字母/数字/_-.:），本次未发起调用");
        }

        Optional<String> base = endpoints.dataBaseUrl();
        if (base.isEmpty()) {
            return Outcome.stateOn(AgentToolResponses.unreachable(FAMILY), Outcome.UNREACHABLE);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put(KEY_RULE_ID, ruleId);
        body.put(KEY_DATASOURCE_ID, datasourceId);
        body.put(KEY_TABLE_NAME, tableName);
        body.put(KEY_SAMPLE_SIZE, sampleSize);

        OwnerPostLeg.Reply reply = OwnerPostLeg.post(restTemplate, base.get(),
                PATH_RULE_EVALUATE.replace(SEGMENT_RULE_ID, ruleId), body, Outcome.ACCEPTED);
        if (!reply.outcome().succeeded()) {
            return reply.payload();
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put(KEY_RULE_ID, ruleId);
        extra.put(KEY_DATASOURCE_ID, datasourceId);
        extra.put(KEY_TABLE_NAME, tableName);
        extra.put(KEY_SAMPLE_SIZE, sampleSize);
        return reply.payloadWith(extra);
    }

    @Override
    public boolean supports(String toolCode) {
        return "RunQualityCheck".equals(toolCode);
    }

    /** 抽样规模取值口径与收口前一致：给了数字就用它，否则用缺省值（不因非法值而伪报成功）。 */
    private static int sampleSizeOf(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return DEFAULT_SAMPLE_SIZE;
    }

    private static String asText(Object value) {
        return value == null ? null : value.toString().trim();
    }
}
