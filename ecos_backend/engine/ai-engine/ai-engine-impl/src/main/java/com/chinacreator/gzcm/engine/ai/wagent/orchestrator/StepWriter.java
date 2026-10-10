package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.AutomationLevel;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.PolicyEffect;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunStepDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunStepDO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Step 落库唯一写闸（详细设计-10 F10-14，REQ-WAG-14；V234 行只经本类产生）。
 *
 * <p>四类硬校验全部在本触发点执行（文档明文"触发点 StepWriter"）：</p>
 * <ul>
 *   <li>{@code automation_level ≤ run.automation_ceiling}——越界 ⇒ {@code E-POLICY 403}（F10-21）；</li>
 *   <li>{@code L3 无自动路径}——档位 3 必须携带非空 {@code approval_token}（F10-21；
 *       token 只存 sha256 引用，禁正文，§6.3）；</li>
 *   <li>UNIQUE(run_id, step_key, attempt) 幂等：{@code (runId, stepKey, attempt)} 已存在即复用
 *       （重试语义 = 调用方以 {@link #nextAttempt} 递增 attempt 写<b>新行</b>，禁改写历史，
 *       StepRetryTest#retryWritesNewAttemptRow）；</li>
 *   <li>引用型留痕：{@code input_ref_text/output_ref_text} 为确定性规范 JSON <b>快照</b>，
 *       {@code input_hash/output_hash} 为 sha256 实列——行内无正文字段
 *       （StepRecorderTest#stepRowContainsRefsAndHashOnly）。</li>
 * </ul>
 *
 * <p>回放一致性（F10-14）：{@link #canonicalJson} 递归按字典序排键 ⇒ 同输入必得同
 * {@code input_hash}；确定性步骤（同 invoker 输出）⇒ 同 {@code output_hash}
 * （ReplayConsistencyTest#sameInputHashYieldsSameOutputHash）。数字不来自 LLM：
 * 哈希/规范序全是确定性计算。</p>
 */
@Component
public class StepWriter {

    /** V234 hash 列宽基准（sha256 hex 64 字符，WAgentHashSupport 同纪律）。 */
    public static final int HASH_LENGTH = 64;

    private final WAgentRunStepDao stepDao;
    private final ObjectMapper objectMapper = new ObjectMapper()
            // 时间快照统一 ISO-8601 字符串（禁 bean 展开），保证跨调用哈希稳定
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public StepWriter(WAgentRunStepDao stepDao) {
        this.stepDao = stepDao;
    }

    /** 写请求（一次一行的全部受控入参；缺项以 null 表达，由本类按纪律补默认）。 */
    public record StepWriteRequest(WAgentRunDO run, WAgentPlan.Step step, int attempt,
                                   String status, Object input, Object output,
                                   PolicyEffect policyEffect, String policyRuleId,
                                   String approvalToken, String taskId,
                                   String errorCode, String errorText,
                                   Map<String, Object> metrics, String operator) {
    }

    /**
     * 唯一写面：校验 → 快照/哈希 → 落 V234。
     *
     * @return 新行 DO（同 (runId, stepKey, attempt) 已存在时返回既有行——幂等复用，禁双写）
     */
    public WAgentRunStepDO write(StepWriteRequest req) {
        WAgentRunDO run = req.run();
        WAgentPlan.Step step = req.step();
        if (run == null || run.getId() == null || step == null) {
            throw WAgentOrchestrationException.validation("StepWriter 缺 run/step（行身份不可缺）");
        }
        int level = step.automationLevel();
        Integer ceiling = run.getAutomationCeiling();
        int effectiveCeiling = ceiling == null ? AutomationLevel.L0.level() : ceiling;
        if (level > effectiveCeiling) {
            Map<String, Object> extras = new LinkedHashMap<>();
            extras.put("stepKey", step.stepKey());
            extras.put("stepLevel", level);
            extras.put("runCeiling", effectiveCeiling);
            throw new WAgentOrchestrationException(WAgentErrorCode.E_POLICY,
                    "步骤等级 " + level + " 超 Run automation_ceiling " + effectiveCeiling, extras);
        }
        if (level == AutomationLevel.L3.level()
                && (req.approvalToken() == null || req.approvalToken().isBlank())) {
            throw new WAgentOrchestrationException(WAgentErrorCode.E_POLICY,
                    "L3 步骤无自动路径：approval_token 必填（F10-21）",
                    Map.of("stepKey", String.valueOf(step.stepKey())));
        }
        java.util.Optional<WAgentRunStepDO> existing =
                stepDao.findByRunStepAttempt(run.getId(), step.stepKey(), req.attempt());
        if (existing.isPresent()) {
            return existing.get(); // 幂等复用（UNIQUE 三维命中的先查面）
        }

        String inputJson = canonicalJson(req.input());
        String outputJson = req.output() == null ? null : canonicalJson(req.output());

        WAgentRunStepDO row = new WAgentRunStepDO();
        row.setId(UUID.randomUUID().toString());
        row.setRunId(run.getId());
        row.setStepKey(step.stepKey());
        row.setAttempt(req.attempt());
        row.setStepType(step.stepType());
        row.setToolName(step.toolName());
        row.setToolVersion(step.toolVersion());
        row.setAutomationLevel(level);
        row.setStatus(req.status());
        row.setInputRefText(inputJson);
        row.setInputHash(WAgentHashSupport.sha256Hex(inputJson));
        row.setOutputRefText(outputJson);
        row.setOutputHash(outputJson == null ? null : WAgentHashSupport.sha256Hex(outputJson));
        row.setPolicyEffect(req.policyEffect() == null ? null : req.policyEffect().value());
        row.setPolicyRuleId(req.policyRuleId());
        row.setTaskId(req.taskId());
        row.setErrorCode(req.errorCode());
        row.setErrorText(req.errorText());
        row.setMetricsJson(req.metrics() == null || req.metrics().isEmpty()
                ? null : canonicalJson(req.metrics()));
        // L3 审批凭证只存引用（sha256），禁 token 正文入行（§6.3 引用原则）
        row.setStartTime(LocalDateTime.now());
        row.setTenantId(run.getTenantId());
        row.setOrgId(run.getOrgId());
        row.setVersionNo("1");
        row.setCreateBy(req.operator());
        row.setUpdateBy(req.operator());
        stepDao.insert(row);
        return row;
    }

    /** 重试递增面：现有 attempt 最大值 +1（UNIQUE(run_id, step_key, attempt) 的写侧节奏）。 */
    public int nextAttempt(String runId, String stepKey) {
        int max = 0;
        for (WAgentRunStepDO row : stepDao.findByRunId(runId)) {
            if (stepKey.equals(row.getStepKey()) && row.getAttempt() != null) {
                max = Math.max(max, row.getAttempt());
            }
        }
        return max + 1;
    }

    /** 审批凭证引用（token 正文不落库，只落 sha256——审计核对在持有方，编排面不留密文）。 */
    public static String approvalTokenRef(String approvalToken) {
        return approvalToken == null || approvalToken.isBlank()
                ? null : WAgentHashSupport.sha256Hex(approvalToken);
    }

    /**
     * 确定性规范 JSON：递归按 key 字典序排序后序列化（Map 迭代序不参与产物 ⇒ 同内容必同串）。
     * LocalDateTime 等非标量转 ISO 字符串；null 保留为 JSON null（哈希语义稳定）。
     */
    public String canonicalJson(Object value) {
        try {
            JsonNode node = objectMapper.valueToTree(value);
            return objectMapper.writeValueAsString(sortNode(node));
        } catch (Exception e) {
            throw WAgentOrchestrationException.validation("输入/输出快照规范化失败（禁降级为对象 toString）");
        }
    }

    private JsonNode sortNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode out = objectMapper.createObjectNode();
            Map<String, JsonNode> sorted = new TreeMap<>();
            Iterator<String> it = node.fieldNames();
            while (it.hasNext()) {
                String field = it.next();
                sorted.put(field, node.get(field));
            }
            List<Map.Entry<String, JsonNode>> entries = new ArrayList<>(sorted.entrySet());
            for (Map.Entry<String, JsonNode> e : entries) {
                out.set(e.getKey(), sortNode(e.getValue()));
            }
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = objectMapper.createArrayNode();
            for (JsonNode child : node) {
                out.add(sortNode(child));
            }
            return out;
        }
        return node;
    }
}
