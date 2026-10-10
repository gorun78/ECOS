package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentMissionDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentMissionDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 计划构建器（详细设计-10 F10-12 主干：Playbook 优先 → 探索规划兜底）。
 *
 * <p>选择逻辑（§4.2 {@code PlaybookSelector → PlanBuilder → PlanValidator(7)}）：</p>
 * <ol>
 *   <li>Mission（V228）存在且 {@code playbook_git_ref + playbook_version} 双列齐备
 *       ⇒ 经 {@link PlaybookContentSource} 读 Git 归档模板（DB 不存正文，铁律 §3.1）；
 *       {@link PlaybookRenderer#fill} 以槽位参数确定性填充 → {@link WAgentPlan.Plan}（PLAYBOOK）；</li>
 *   <li>引用缺失、内容源未装配（#131 前常态）或该引用不可得 ⇒ {@link ExplorationPlanner}
 *       生成 EXPLORED 计划 + 缺口事件（<b>禁伪称 Playbook 命中</b>）。</li>
 * </ol>
 */
@Component
public class PlanBuilder {

    private static final Logger log = LoggerFactory.getLogger(PlanBuilder.class);

    private final WAgentMissionDao missionDao;
    private final PlaybookRenderer playbookRenderer;
    private final ExplorationPlanner explorationPlanner;
    private final ObjectProvider<PlaybookContentSource> contentSourceProvider;

    public PlanBuilder(WAgentMissionDao missionDao, PlaybookRenderer playbookRenderer,
                       ExplorationPlanner explorationPlanner,
                       ObjectProvider<PlaybookContentSource> contentSourceProvider) {
        this.missionDao = missionDao;
        this.playbookRenderer = playbookRenderer;
        this.explorationPlanner = explorationPlanner;
        this.contentSourceProvider = contentSourceProvider;
    }

    /** 构建输入（missionId 可空——空即直接 EXPLORED；slots 值按字符串消费进模板占位）。 */
    public record BuildRequest(String tenantId, String runId, String missionId, String intent,
                               Map<String, Object> slots, String operator) {
    }

    public WAgentPlan.Plan build(BuildRequest req) {
        WAgentPlan.Plan playbook = tryPlaybook(req);
        if (playbook != null) {
            return playbook;
        }
        return explorationPlanner.plan(req.tenantId(), req.runId(), req.missionId(),
                req.intent(), req.slots(), req.operator());
    }

    private WAgentPlan.Plan tryPlaybook(BuildRequest req) {
        if (req.missionId() == null || req.missionId().isBlank()) {
            return null;
        }
        WAgentMissionDO mission = missionDao.findById(req.missionId()).orElse(null);
        if (mission == null || isBlank(mission.getPlaybookGitRef()) || isBlank(mission.getPlaybookVersion())) {
            return null; // 无在用版本引用 ⇒ 缺 Playbook（事件由 EXPLORED 路径登记）
        }
        PlaybookContentSource source = contentSourceProvider.getIfAvailable();
        if (source == null) {
            log.info("[F10-12] Playbook 内容源未装配（Git 归档通道属 #131），落 EXPLORED: missionId={} ref={}",
                    req.missionId(), mission.getPlaybookGitRef());
            return null;
        }
        String template = source.loadActiveTemplate(req.tenantId(),
                mission.getPlaybookGitRef(), mission.getPlaybookVersion()).orElse(null);
        if (template == null) {
            log.info("[F10-12] Playbook 引用不可得（归档缺席），落 EXPLORED: ref={} version={}",
                    mission.getPlaybookGitRef(), mission.getPlaybookVersion());
            return null;
        }
        String filled = playbookRenderer.fill(template, stringify(req.slots()));
        WAgentPlan.Plan parsed = WAgentPlan.PlanCodec.fromJson(filled);
        return new WAgentPlan.Plan(com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.PlanSource.PLAYBOOK,
                mission.getPlaybookGitRef(), mission.getPlaybookVersion(), parsed.steps());
    }

    private static Map<String, String> stringify(Map<String, Object> slots) {
        Map<String, String> out = new LinkedHashMap<>();
        if (slots != null) {
            slots.forEach((k, v) -> out.put(k, v == null ? "" : String.valueOf(v)));
        }
        return out;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
