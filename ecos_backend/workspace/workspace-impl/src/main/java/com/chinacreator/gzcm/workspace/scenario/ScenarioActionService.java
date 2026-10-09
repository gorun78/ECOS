package com.chinacreator.gzcm.workspace.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 场景动作闭环编排（卷07 F07-10；只做编排，底座经 {@link DecisionGateway} 复用 cognitive，禁第二套）。
 * <p>五必填服务端校验 + 审批人取自主体 + forecastRunId 存在且 FORMAL（与 F07-09 共用 {@link BaselineReferenceGuard}）。
 * 端到端闭环终验属 09 册，本服务<b>不声称闭环已可用</b>。</p>
 */
@Service
public class ScenarioActionService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioActionService.class);

    private final ActionProposalValidator validator;
    private final BaselineReferenceGuard baselineGuard;
    private final ObjectProvider<DecisionGateway> gatewayProvider;

    public ScenarioActionService(ActionProposalValidator validator,
                                 BaselineReferenceGuard baselineGuard,
                                 ObjectProvider<DecisionGateway> gatewayProvider) {
        this.validator = validator;
        this.baselineGuard = baselineGuard;
        this.gatewayProvider = gatewayProvider;
    }

    public Map<String, Object> propose(String scenarioId, ActionProposal proposal, String approverSubject) {
        validator.validate(proposal, approverSubject);
        assertForecastRunFormal(proposal.getForecastRunId());
        DecisionGateway gateway = requireGateway();
        Map<String, Object> result = gateway.proposeAction(scenarioId, proposal, approverSubject);
        log.info("动作提案 scenario={} by={} actionId={}", scenarioId, approverSubject,
                result == null ? null : result.get("actionId"));
        return result;
    }

    public Map<String, Object> approve(String actionId, String approverSubject) {
        if (isBlank(approverSubject)) {
            throw new ActionValidationException(List.of("approver"));
        }
        return requireGateway().approve(actionId, approverSubject);
    }

    public List<Map<String, Object>> listActions(String scenarioId) {
        return requireGateway().listActions(scenarioId);
    }

    public List<Map<String, Object>> listDecisions(String scenarioId) {
        return requireGateway().listDecisions(scenarioId);
    }

    /** forecastRunId 必须存在且 run_mode=FORMAL（F07-09 共用 guard，run_mode 解析唯一点=守卫）。 */
    void assertForecastRunFormal(String forecastRunId) {
        if (isBlank(forecastRunId)) {
            return;
        }
        RunMode mode = baselineGuard.resolveMode(forecastRunId);
        if (mode == null) {
            return;
        }
        baselineGuard.assertAllowed(RunMode.FORMAL, mode, forecastRunId);
    }

    private DecisionGateway requireGateway() {
        DecisionGateway gateway = gatewayProvider.getIfAvailable();
        if (gateway == null) {
            throw new CognitiveEngineUnavailableException("decision 底座网关未装配（cognitive api 门面/REST 属其它 Agent 接缝）");
        }
        return gateway;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
