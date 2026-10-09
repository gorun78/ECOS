package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 场景状态迁移编排（卷07 F07-07 N12，PATCH /{id}/status 唯一入口）。
 * <p>权限（evaluate，默认 DENY）+ 状态机（非法 409）+ 激活前置（完整度/孤岛/认知可达）+ 落库 + 审计。
 * 主体取自 JWT，绝不读请求体 userId。</p>
 */
@Service
public class ScenarioStatusTransitionService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioStatusTransitionService.class);

    private final ScenarioService scenarioService;
    private final ScenarioStatusMachine statusMachine;
    private final CompletenessDataLoader loader;
    private final ScenarioCompletenessService completenessService;
    private final WorkspaceSecurityAudit audit;
    private final WorkspaceDataDao dao;

    public ScenarioStatusTransitionService(ScenarioService scenarioService,
                                           ScenarioStatusMachine statusMachine,
                                           CompletenessDataLoader loader,
                                           ScenarioCompletenessService completenessService,
                                           WorkspaceSecurityAudit audit,
                                           WorkspaceDataDao dao) {
        this.scenarioService = scenarioService;
        this.statusMachine = statusMachine;
        this.loader = loader;
        this.completenessService = completenessService;
        this.audit = audit;
        this.dao = dao;
    }

    /** 执行迁移；返回迁移后状态 + allowedTransitions（前端按钮态）。 */
    @Transactional(rollbackFor = Exception.class)
    public TransitionResult transition(String scenarioId, String targetStatus) {
        ScenarioStatus from = ScenarioStatus.fromNullable(currentStatus(scenarioId));
        ScenarioStatus to = ScenarioStatus.fromNullable(targetStatus);
        if (to == null) {
            throw IllegalScenarioTransitionException.illegal(str(from), targetStatus);
        }
        TransitionContext ctx = buildContext(scenarioId, to);
        statusMachine.assertTransition(from, to, ctx);

        ScenarioSaveDTO dto = new ScenarioSaveDTO();
        dto.setStatus(to.name());
        scenarioService.update(scenarioId, dto);

        List<ScenarioStatus> allowed = statusMachine.allowedTransitions(to, buildContext(scenarioId, to));
        audit.record("scenario:transition:" + to.name(), scenarioId, "OK");
        log.info("场景状态迁移 scenario={} {}->{} by={}", scenarioId, str(from), to, ctx.getSubject());
        return new TransitionResult(to.name(), statusNames(allowed));
    }

    /** 详情响应的 allowedTransitions（结构可达态，服务端计算，前端按钮态唯一来源）。 */
    public List<String> allowedTransitionsFor(String scenarioId) {
        ScenarioStatus from = ScenarioStatus.fromNullable(currentStatus(scenarioId));
        TransitionContext ctx = buildContext(scenarioId, null);
        return statusNames(statusMachine.allowedTransitions(from, ctx));
    }

    private TransitionContext buildContext(String scenarioId, ScenarioStatus target) {
        List<BindingNode> bindings = loader.bindingsOf(scenarioId);
        List<LinkEdge> links = loader.linksOf(scenarioId);
        CompletenessReport report = completenessService.compute(scenarioId, bindings, links);
        boolean cognitiveReachable = report.getPresentEdges().stream()
                .anyMatch(p -> EdgeType.COGNITION.name().equals(p.getType()));
        boolean formalRun = hasFormalRun(scenarioId);
        return TransitionContext.builder()
                .islands(report.getIslands())
                .verdict(report.getVerdict())
                .cognitiveReachable(cognitiveReachable || target != ScenarioStatus.ACTIVE)
                .formalRunExists(formalRun)
                .subject(currentSubject())
                .build();
    }

    private String currentStatus(String scenarioId) {
        return scenarioService.get(scenarioId).getStatus();
    }

    private boolean hasFormalRun(String scenarioId) {
        List<Map<String, Object>> runs = dao.listRunHistory(scenarioId, 100);
        // 过渡期：旧 run 表无 run_mode 列，任何成功运行按 FORMAL 处理（V209 新表落地后改读 run_mode='FORMAL'，见 Mapper 收口接缝）。
        return runs != null && !runs.isEmpty();
    }

    private List<String> statusNames(List<ScenarioStatus> statuses) {
        List<String> names = new java.util.ArrayList<>(statuses.size());
        for (ScenarioStatus s : statuses) {
            names.add(s.name());
        }
        return names;
    }

    private String currentSubject() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                return auth.getName();
            }
        } catch (Exception e) {
            log.debug("读取 SecurityContext 主体失败: {}", e.getMessage());
        }
        return "anonymous";
    }

    private String str(ScenarioStatus s) {
        return s == null ? "null" : s.name();
    }

    /** 迁移结果（{status, allowedTransitions[]}）。 */
    public static final class TransitionResult {
        private final String status;
        private final List<String> allowedTransitions;

        public TransitionResult(String status, List<String> allowedTransitions) {
            this.status = status;
            this.allowedTransitions = allowedTransitions;
        }

        public String getStatus() {
            return status;
        }

        public List<String> getAllowedTransitions() {
            return allowedTransitions;
        }
    }
}
