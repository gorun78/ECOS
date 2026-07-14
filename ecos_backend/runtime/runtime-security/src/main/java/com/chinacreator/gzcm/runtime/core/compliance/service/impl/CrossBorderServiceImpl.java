package com.chinacreator.gzcm.runtime.core.compliance.service.impl;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.chinacreator.gzcm.sysman.compliance.dao.CrossBorderTransferDao;
import com.chinacreator.gzcm.sysman.compliance.entity.CrossBorderTransfer;
import com.chinacreator.gzcm.sysman.compliance.service.ICompliancePolicyService;
import com.chinacreator.gzcm.sysman.compliance.service.ICrossBorderService;
import com.chinacreator.gzcm.sysman.compliance.service.IDataResidencyService;

/**
 * 跨境传输服务实现
 * 实现数据跨境传输控制和合规检查
 */
public class CrossBorderServiceImpl implements ICrossBorderService {

    private final CrossBorderTransferDao transferDao;
    private final IDataResidencyService dataResidencyService;
    private final ICompliancePolicyService compliancePolicyService;

    public CrossBorderServiceImpl(CrossBorderTransferDao transferDao,
                                 IDataResidencyService dataResidencyService) {
        this(transferDao, dataResidencyService, null);
    }
    
    public CrossBorderServiceImpl(CrossBorderTransferDao transferDao,
                                 IDataResidencyService dataResidencyService,
                                 ICompliancePolicyService compliancePolicyService) {
        this.transferDao = transferDao;
        this.dataResidencyService = dataResidencyService;
        this.compliancePolicyService = compliancePolicyService;
    }

    @Override
    public CrossBorderTransfer checkTransfer(String sourceRegion, String targetRegion,
                                            String resourceId, String transferType, 
                                            String operatorId) throws ComplianceException {
        try {
            CrossBorderTransfer transfer = new CrossBorderTransfer();
            transfer.setTransferId(UUID.randomUUID().toString());
            transfer.setSourceRegion(sourceRegion);
            transfer.setTargetRegion(targetRegion);
            transfer.setResourceId(resourceId);
            transfer.setTransferType(transferType);
            transfer.setOperatorId(operatorId);
            transfer.setTransferTime(new Date());

            // 检查是否为跨境传输
            if (sourceRegion != null && targetRegion != null && !sourceRegion.equals(targetRegion)) {
                // 跨境传输，需要检查合规策略
                boolean allowed = checkCrossBorderPolicy(sourceRegion, targetRegion, resourceId, transferType);
                
                if (allowed) {
                    transfer.setStatus("ALLOWED");
                    transfer.setReason("符合跨境传输策略");
                } else {
                    transfer.setStatus("DENIED");
                    transfer.setReason("违反跨境传输策略");
                }
            } else {
                // 同区域传输，允许
                transfer.setStatus("ALLOWED");
                transfer.setReason("同区域传输");
            }

            // 记录传输
            recordTransfer(transfer);
            return transfer;
        } catch (Exception e) {
            throw new ComplianceException("检查跨境传输失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查跨境传输策略
     */
    private boolean checkCrossBorderPolicy(String sourceRegion, String targetRegion, 
                                          String resourceId, String transferType) {
        try {
            if (compliancePolicyService == null) {
                // 如果没有合规策略服务，默认拒绝跨境传输（安全优先）
                return false;
            }
            
            // 构建检查上下文
            Map<String, Object> context = new HashMap<>();
            context.put("sourceRegion", sourceRegion);
            context.put("targetRegion", targetRegion);
            context.put("resourceId", resourceId);
            context.put("transferType", transferType);
            
            // 检查合规策略
            String contextJson = convertToJson(context);
            return compliancePolicyService.checkCompliance("CROSS_BORDER", contextJson);
        } catch (Exception e) {
            // 检查失败，默认拒绝（安全优先）
            System.err.println("跨境传输策略检查失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 转换为JSON（简化实现）
     */
    private String convertToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(entry.getKey()).append("\":");
            if (entry.getValue() instanceof String) {
                json.append("\"").append(entry.getValue()).append("\"");
            } else {
                json.append(entry.getValue());
            }
            first = false;
        }
        json.append("}");
        return json.toString();
    }

    @Override
    public void recordTransfer(CrossBorderTransfer transfer) throws ComplianceException {
        try {
            transferDao.insert(transfer);
        } catch (Exception e) {
            throw new ComplianceException("记录跨境传输失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public java.util.List<CrossBorderTransfer> queryTransfers(String sourceRegion, String targetRegion, 
            String resourceId, String status, java.util.Date startTime, java.util.Date endTime) throws ComplianceException {
        try {
            Map<String, Object> condition = new HashMap<>();
            if (sourceRegion != null) {
                condition.put("sourceRegion", sourceRegion);
            }
            if (targetRegion != null) {
                condition.put("targetRegion", targetRegion);
            }
            if (resourceId != null) {
                condition.put("resourceId", resourceId);
            }
            if (status != null) {
                condition.put("status", status);
            }
            if (startTime != null) {
                condition.put("startTime", startTime);
            }
            if (endTime != null) {
                condition.put("endTime", endTime);
            }
            return transferDao.query(condition);
        } catch (Exception e) {
            throw new ComplianceException("查询跨境传输记录失败: " + e.getMessage(), e);
        }
    }
}

