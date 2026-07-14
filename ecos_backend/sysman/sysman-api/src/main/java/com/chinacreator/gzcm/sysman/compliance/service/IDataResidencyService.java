package com.chinacreator.gzcm.sysman.compliance.service;

import java.util.List;

import com.chinacreator.gzcm.sysman.compliance.entity.DataResidency;

/**
 * 数据驻留服务接口
 */
public interface IDataResidencyService {

    /**
     * 标记数据区域
     *
     * @param resourceId   资源ID
     * @param resourceType 资源类型
     * @param region       区域
     * @param tenantId     租户ID
     * @param operator     操作者
     * @return 数据驻留记录
     */
    DataResidency markRegion(String resourceId, String resourceType, String region, String tenantId, String operator) throws ComplianceException;

    /**
     * 查询数据区域
     *
     * @param resourceId   资源ID
     * @param resourceType 资源类型
     * @return 数据驻留记录
     */
    DataResidency getRegion(String resourceId, String resourceType) throws ComplianceException;

    /**
     * 检查数据是否在允许的区域
     *
     * @param resourceId   资源ID
     * @param resourceType  资源类型
     * @param allowedRegions 允许的区域列表
     * @return true 在允许区域，false 不在
     */
    boolean checkResidency(String resourceId, String resourceType, List<String> allowedRegions) throws ComplianceException;

    /**
     * 合规异常
     */
    class ComplianceException extends Exception {
        private static final long serialVersionUID = 1L;

        public ComplianceException(String message) {
            super(message);
        }

        public ComplianceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

