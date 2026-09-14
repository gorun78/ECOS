package com.chinacreator.gzcm.sysman.policy.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 策略决策结果
 */
public class PolicyDecision {
    /**
     * 决策结果枚举
     */
    public enum Decision {
        PERMIT,      // 允许
        DENY,        // 拒绝
        NOT_APPLICABLE, // 不适用
        INDETERMINATE   // 不确定（评估出错）
    }

    private Decision decision;
    private List<Obligation> obligations;  // 义务（如脱敏、审计）
    private String reason;                  // 决策原因
    private String policyId;                // 匹配的策略ID
    private long timestamp;                 // 决策时间戳

    public PolicyDecision(Decision decision) {
        this.decision = decision;
        this.obligations = new ArrayList<>();
        this.timestamp = System.currentTimeMillis();
    }

    public Decision getDecision() {
        return decision;
    }

    public void setDecision(Decision decision) {
        this.decision = decision;
    }

    public List<Obligation> getObligations() {
        return obligations;
    }

    public void setObligations(List<Obligation> obligations) {
        this.obligations = obligations;
    }

    public void addObligation(Obligation obligation) {
        if (obligations == null) {
            obligations = new ArrayList<>();
        }
        obligations.add(obligation);
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * 义务：策略决策后需要执行的操作
     */
    public static class Obligation {
        private String obligationId;    // 义务ID
        private String obligationType;  // 义务类型（如MASK、AUDIT）
        private String obligationFulfillment; // 义务履行方式（JSON格式）

        public Obligation(String obligationId, String obligationType, String obligationFulfillment) {
            this.obligationId = obligationId;
            this.obligationType = obligationType;
            this.obligationFulfillment = obligationFulfillment;
        }

        public String getObligationId() {
            return obligationId;
        }

        public void setObligationId(String obligationId) {
            this.obligationId = obligationId;
        }

        public String getObligationType() {
            return obligationType;
        }

        public void setObligationType(String obligationType) {
            this.obligationType = obligationType;
        }

        public String getObligationFulfillment() {
            return obligationFulfillment;
        }

        public void setObligationFulfillment(String obligationFulfillment) {
            this.obligationFulfillment = obligationFulfillment;
        }
    }
}

