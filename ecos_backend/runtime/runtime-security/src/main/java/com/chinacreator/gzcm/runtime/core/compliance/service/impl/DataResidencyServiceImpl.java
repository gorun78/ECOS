package com.chinacreator.gzcm.runtime.core.compliance.service.impl;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.chinacreator.gzcm.sysman.compliance.dao.DataResidencyDao;
import com.chinacreator.gzcm.sysman.compliance.entity.DataResidency;
import com.chinacreator.gzcm.sysman.compliance.service.IDataResidencyService;

/**
 * 数据驻留服务实现
 */
public class DataResidencyServiceImpl implements IDataResidencyService {

    private final DataResidencyDao dataResidencyDao;

    public DataResidencyServiceImpl(DataResidencyDao dataResidencyDao) {
        this.dataResidencyDao = dataResidencyDao;
    }

    @Override
    public DataResidency markRegion(String resourceId, String resourceType, String region, 
                                   String tenantId, String operator) throws ComplianceException {
        try {
            // 检查是否已存在
            DataResidency existing = dataResidencyDao.findByResource(resourceId, resourceType);
            if (existing != null) {
                existing.setRegion(region);
                existing.setTenantId(tenantId);
                dataResidencyDao.update(existing);
                return existing;
            }

            // 创建新记录
            DataResidency residency = new DataResidency();
            residency.setResidencyId(UUID.randomUUID().toString());
            residency.setResourceId(resourceId);
            residency.setResourceType(resourceType);
            residency.setRegion(region);
            residency.setTenantId(tenantId);
            residency.setCreatedTime(new Date());
            residency.setCreatedBy(operator);
            dataResidencyDao.insert(residency);
            return residency;
        } catch (Exception e) {
            throw new ComplianceException("标记数据区域失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DataResidency getRegion(String resourceId, String resourceType) throws ComplianceException {
        try {
            return dataResidencyDao.findByResource(resourceId, resourceType);
        } catch (Exception e) {
            throw new ComplianceException("查询数据区域失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean checkResidency(String resourceId, String resourceType, 
                                  List<String> allowedRegions) throws ComplianceException {
        try {
            DataResidency residency = dataResidencyDao.findByResource(resourceId, resourceType);
            if (residency == null) {
                // 未标记区域，根据策略决定（默认拒绝，安全优先）
                return false;
            }
            
            // 检查是否在允许的区域列表中
            if (allowedRegions == null || allowedRegions.isEmpty()) {
                return false; // 没有允许的区域，拒绝
            }
            
            return allowedRegions.contains(residency.getRegion());
        } catch (Exception e) {
            throw new ComplianceException("检查数据驻留失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 验证数据操作是否符合数据驻留要求
     * 
     * @param resourceId 资源ID
     * @param resourceType 资源类型
     * @param operationRegion 操作区域
     * @return true表示符合要求，false表示不符合
     * @throws ComplianceException
     */
    public boolean validateDataOperation(String resourceId, String resourceType, String operationRegion) throws ComplianceException {
        try {
            DataResidency residency = getRegion(resourceId, resourceType);
            if (residency == null) {
                // 未标记区域，需要先标记
                return false;
            }
            
            // 检查操作区域是否与数据驻留区域一致
            return residency.getRegion().equals(operationRegion);
        } catch (Exception e) {
            throw new ComplianceException("验证数据操作失败: " + e.getMessage(), e);
        }
    }
}

