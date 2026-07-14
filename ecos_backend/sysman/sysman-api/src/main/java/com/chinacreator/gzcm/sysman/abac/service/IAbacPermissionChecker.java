package com.chinacreator.gzcm.sysman.abac.service;

import com.chinacreator.gzcm.sysman.abac.model.AbacContext;

public interface IAbacPermissionChecker {

    enum Decision {
        PERMIT, DENY, NOT_APPLICABLE
    }

    Decision check(AbacContext context) throws PolicyEvaluationException;

    class PolicyEvaluationException extends Exception {
        private static final long serialVersionUID = 1L;
        public PolicyEvaluationException(String message) { super(message); }
        public PolicyEvaluationException(String message, Throwable cause) { super(message, cause); }
    }
}


