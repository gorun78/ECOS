package com.chinacreator.gzcm.sysman.compliance.service;

import com.chinacreator.gzcm.sysman.compliance.entity.CrossBorderTransfer;

/**
 * 跨境传输服务接口
 */
public interface ICrossBorderService {

    /**
     * 检查跨境传输是否允许
     *
     * @param sourceRegion 源区域
     * @param targetRegion 目标区域
     * @param resourceId   资源ID
     * @param transferType 传输类型
     * @param operatorId   操作者ID
     * @return 传输记录（包含是否允许）
     */
    CrossBorderTransfer checkTransfer(String sourceRegion, String targetRegion, 
                                     String resourceId, String transferType, String operatorId) throws ComplianceException;

    /**
     * 记录跨境传输
     *
     * @param transfer 传输记录
     */
    void recordTransfer(CrossBorderTransfer transfer) throws ComplianceException;
    
    /**
     * 查询跨境传输记录
     * 
     * @param sourceRegion 源区域（可选）
     * @param targetRegion 目标区域（可选）
     * @param resourceId 资源ID（可选）
     * @param status 状态（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @return 传输记录列表
     * @throws ComplianceException
     */
    java.util.List<CrossBorderTransfer> queryTransfers(String sourceRegion, String targetRegion, 
            String resourceId, String status, java.util.Date startTime, java.util.Date endTime) throws ComplianceException;

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

