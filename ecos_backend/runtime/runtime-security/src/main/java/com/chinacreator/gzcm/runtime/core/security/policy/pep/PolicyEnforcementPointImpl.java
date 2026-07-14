package com.chinacreator.gzcm.runtime.core.security.policy.pep;

import com.chinacreator.gzcm.sysman.datapermission.service.IDataPermissionEnforcer;
import com.chinacreator.gzcm.sysman.policy.model.AccessRequest;
import com.chinacreator.gzcm.sysman.policy.model.PolicyDecision;
import com.chinacreator.gzcm.sysman.policy.pdp.PolicyDecisionPoint;
import com.chinacreator.gzcm.sysman.policy.pep.PolicyEnforcementPoint;

/**
 * 策略执行点实现：拦截请求并执行策略决策
 */
public class PolicyEnforcementPointImpl implements PolicyEnforcementPoint {

    private final PolicyDecisionPoint pdp;
    private final IDataPermissionEnforcer dataPermissionEnforcer;

    public PolicyEnforcementPointImpl(PolicyDecisionPoint pdp,
                                      IDataPermissionEnforcer dataPermissionEnforcer) {
        this.pdp = pdp;
        this.dataPermissionEnforcer = dataPermissionEnforcer;
    }

    @Override
    public PolicyDecision enforce(AccessRequest request) throws PolicyEnforcementException {
        try {
            // 调用PDP进行决策
            PolicyDecision decision = pdp.evaluate(request);

            // 根据决策结果执行相应操作
            switch (decision.getDecision()) {
                case PERMIT:
                    // 允许：执行义务后继续
                    fulfillObligations(decision, request);
                    break;
                case DENY:
                    // 拒绝：直接返回
                    break;
                case NOT_APPLICABLE:
                    // 不适用：默认拒绝（安全策略）
                    decision.setDecision(PolicyDecision.Decision.DENY);
                    decision.setReason("无匹配策略，默认拒绝");
                    break;
                case INDETERMINATE:
                    // 不确定：拒绝
                    decision.setDecision(PolicyDecision.Decision.DENY);
                    decision.setReason("策略评估不确定，拒绝访问");
                    break;
            }

            return decision;
        } catch (PolicyDecisionPoint.PolicyEvaluationException e) {
            throw new PolicyEnforcementException("策略执行失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void fulfillObligations(PolicyDecision decision, AccessRequest request) {
        // 执行义务（如数据脱敏、审计日志等）
        for (PolicyDecision.Obligation obligation : decision.getObligations()) {
            switch (obligation.getObligationType()) {
                case "DATA_MASKING":
                    // TODO: 执行数据脱敏
                    break;
                case "AUDIT":
                    // TODO: 记录审计日志
                    break;
                case "ROW_LEVEL_SECURITY":
                    // TODO: 应用行级安全
                    break;
                case "COLUMN_LEVEL_SECURITY":
                    // TODO: 应用列级安全
                    break;
                default:
                    // 忽略未知义务类型
                    break;
            }
        }
    }
}

