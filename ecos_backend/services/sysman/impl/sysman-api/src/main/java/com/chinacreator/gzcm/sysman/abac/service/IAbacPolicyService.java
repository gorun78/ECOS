package com.chinacreator.gzcm.sysman.abac.service;

import java.util.List;

import com.chinacreator.gzcm.sysman.abac.model.AbacPolicy;

public interface IAbacPolicyService {

    AbacPolicy createPolicy(AbacPolicy policy) throws AbacException;

    AbacPolicy updatePolicy(AbacPolicy policy) throws AbacException;

    void deletePolicy(String policyId) throws AbacException;

    AbacPolicy getPolicy(String policyId) throws AbacException;

    List<AbacPolicy> listPolicies() throws AbacException;

    class AbacException extends Exception {
        private static final long serialVersionUID = 1L;
        public AbacException(String message) { super(message); }
        public AbacException(String message, Throwable cause) { super(message, cause); }
    }
}


