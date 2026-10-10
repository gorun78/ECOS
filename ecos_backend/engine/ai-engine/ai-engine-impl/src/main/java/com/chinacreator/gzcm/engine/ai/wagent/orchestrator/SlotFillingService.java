package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.SlotProvenance;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentQuestionDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentGoalDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentQuestionDO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 槽位来源与澄清配额服务（详细设计-10 F10-06，REQ-WAG-06；§4.4 澄清配额算法）。
 *
 * <p><b>一轮 ≤3 项</b>：{@link #nextClarificationItems} 裁剪（F10-06 明文）。
 * <b>连续两轮无新增信息 ⇒ {@code awaiting_input} 挂起</b>：{@link #recordClarifyRound}
 * 以槽位键集合指纹比对判定"无新增信息"；口径按任务核定文本（设计文档 §4.4 写 "{@code >2} 轮"，
 * 任务口径"连续两轮"更严——取更严者，差异已登记 R4 回写）。</p>
 *
 * <p>轮次计数为单实例内存态（V229 无计数器列，禁为此私改冻结 DDL）：判定结果
 * （{@code awaiting_input}）落 DB 持久，最坏情形是多实例切换时重新计数——方向保守
 * （只会多问不会少问），不构成放行风险。<b>全部槽位为推断（AGENT_INFERRED）⇒ 提醒</b>
 * （附件 AC-02："依据：推断"标注由前端读 {@code slots_source_json}+{@code inferred_count}
 * 渲染，本服务只产告警信号）。</p>
 */
@Service
public class SlotFillingService {

    private static final Logger log = LoggerFactory.getLogger(SlotFillingService.class);

    /** F10-06：澄清一轮最多三项（超出裁剪，禁一次抛满屏问题）。 */
    public static final int MAX_CLARIFICATION_ITEMS_PER_ROUND = 3;

    /** F10-06（任务口径）：连续 N 轮无新增信息 ⇒ awaiting_input。 */
    public static final int NO_NEW_INFO_ROOUNDS_TO_SUSPEND = 2;

    /** 全推断槽位提醒码（前端"口径以推断标注"消费；单源在本类）。 */
    public static final String WARNING_ALL_SLOTS_INFERRED = "WA-SLOTS-ALL-INFERRED";

    private final WAgentQuestionDao questionDao;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** questionId → 上轮槽位键指纹（"新增信息"判定基）。 */
    private final Map<String, String> lastSlotFingerprint = new ConcurrentHashMap<>();
    /** questionId → 连续无新增信息轮数。 */
    private final Map<String, Integer> noNewInfoStreak = new ConcurrentHashMap<>();

    public SlotFillingService(WAgentQuestionDao questionDao) {
        this.questionDao = questionDao;
    }

    /**
     * 本轮澄清项（≤3）：按缺失槽位原序裁剪。超出部分留待下轮（澄清循环由配额封顶，
     * 见 {@link #recordClarifyRound}）。
     */
    public java.util.List<String> nextClarificationItems(java.util.List<String> missingSlots) {
        if (missingSlots == null || missingSlots.isEmpty()) {
            return java.util.List.of();
        }
        return java.util.List.copyOf(missingSlots.subList(0,
                Math.min(MAX_CLARIFICATION_ITEMS_PER_ROUND, missingSlots.size())));
    }

    /**
     * 记录一轮澄清：与上轮槽位键集合比对；无新增信息连续 {@value #NO_NEW_INFO_ROOUNDS_TO_SUSPEND} 轮
     * ⇒ 问题转 {@code awaiting_input}（挂起并输出缺项清单）。
     *
     * @return true = 本轮后已挂起（awaiting_input）
     */
    public boolean recordClarifyRound(WAgentQuestionDO question, Set<String> slotKeysNow) {
        String fingerprint = fingerprint(slotKeysNow);
        String previous = lastSlotFingerprint.put(question.getId(), fingerprint);
        int streak;
        if (previous != null && previous.equals(fingerprint)) {
            streak = noNewInfoStreak.merge(question.getId(), 1, Integer::sum);
        } else {
            noNewInfoStreak.put(question.getId(), 0);
            streak = 0;
        }
        if (streak >= NO_NEW_INFO_ROOUNDS_TO_SUSPEND) {
            questionDao.findById(question.getId()).ifPresent(current -> {
                current.setStatus(WAgentStatus.QuestionStatus.AWAITING_INPUT.value());
                questionDao.update(current);
            });
            log.info("[F10-06] 连续 {} 轮澄清无新增信息 ⇒ awaiting_input: questionId={}",
                    streak, question.getId());
            return true;
        }
        return false;
    }

    /**
     * F10-06/AC-02："全部槽位为推断"提醒——Goal 的 slots_source 非空且逐槽位全为
     * {@link SlotProvenance#AGENT_INFERRED} 时返回告警码（口径变更卡以此标注）。
     */
    public Optional<String> allInferredWarning(WAgentGoalDO goal) {
        Map<String, String> slotsSource = readSlotsSource(goal.getSlotsSourceJson());
        if (slotsSource.isEmpty()) {
            return Optional.empty();
        }
        boolean allInferred = slotsSource.values().stream()
                .allMatch(SlotProvenance.AGENT_INFERRED.value()::equals);
        return allInferred ? Optional.of(WARNING_ALL_SLOTS_INFERRED) : Optional.empty();
    }

    /** 提醒是否命中（便捷形，供 VO 组装）。 */
    public boolean raisesAllInferredWarning(WAgentGoalDO goal) {
        return allInferredWarning(goal).isPresent();
    }

    private Map<String, String> readSlotsSource(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("[F10-06] slots_source_json 不可解析，按缺项处理（禁宽容当全推断）: {}", e.getMessage());
            return Map.of();
        }
    }

    private static String fingerprint(Set<String> slotKeys) {
        return String.join(",", new TreeSet<>(slotKeys == null ? Set.of() : slotKeys));
    }
}
