package com.chinacreator.gzcm.sysman.datapermission.service;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.datapermission.entity.DataPermissionPolicy;

public interface IDataPermissionPolicyService {

    DataPermissionPolicy createPolicy(DataPermissionPolicy policy, String operator) throws DataPermissionPolicyException;

    DataPermissionPolicy updatePolicy(DataPermissionPolicy policy, String operator) throws DataPermissionPolicyException;

    void deletePolicy(String policyId) throws DataPermissionPolicyException;

    DataPermissionPolicy getPolicy(String policyId) throws DataPermissionPolicyException;

    List<DataPermissionPolicy> listPolicies(Map<String, Object> condition) throws DataPermissionPolicyException;

    class DataPermissionPolicyException extends Exception {
        private static final long serialVersionUID = 1L;

        public DataPermissionPolicyException(String message) {
            super(message);
        }

        public DataPermissionPolicyException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}


