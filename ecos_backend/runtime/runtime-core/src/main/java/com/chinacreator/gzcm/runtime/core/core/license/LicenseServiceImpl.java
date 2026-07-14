package com.chinacreator.gzcm.runtime.core.core.license;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 许可证服务实现
 * 提供许可证验证和管理功能
 * 
 * @author CDRC Runtime Team
 */
public class LicenseServiceImpl implements ILicenseService {
    
    // 当前许可证信息
    private LicenseInfo currentLicense = null;
    
    // 许可证状态
    private LicenseStatus licenseStatus = LicenseStatus.VALID;
    
    // 启用的功能列表
    private final List<String> enabledFeatures = new ArrayList<>();
    
    // 许可证属性
    private final ConcurrentMap<String, String> licenseProperties = new ConcurrentHashMap<>();
    
    // 许可证验证时间
    private Date lastValidationTime = null;

    @Override
    public LicenseInfo validateLicense(String licenseKey) throws LicenseException {
        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            throw new LicenseException("License key cannot be null or empty");
        }
        
        // 占位实现：实际应验证许可证密钥的有效性
        // 这里简化为接受任何非空密钥
        LicenseInfo info = new LicenseInfo();
        info.setLicenseId(licenseKey);
        info.setExpireDate(new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)); // 1年后过期
        info.setMaxUsers(100);
        info.setMaxNodes(10);
        info.setIssueDate(new Date());
        
        currentLicense = info;
        licenseStatus = LicenseStatus.VALID;
        lastValidationTime = new Date();
        
        // 设置默认启用的功能
        enabledFeatures.clear();
        enabledFeatures.add("BASIC");
        enabledFeatures.add("ADVANCED");
        info.setEnabledFeatures(new ArrayList<>(enabledFeatures));
        
        // 设置默认属性
        Map<String, String> props = new HashMap<>();
        props.put("license.type", "TRIAL");
        props.put("license.version", "1.0");
        info.setProperties(props);
        licenseProperties.putAll(props);
        
        return info;
    }

    @Override
    public boolean checkFeatureEnabled(String feature) {
        if (feature == null || feature.trim().isEmpty()) {
            return false;
        }
        
        // 检查许可证状态
        if (licenseStatus != LicenseStatus.VALID) {
            return false;
        }
        
        // 检查功能是否在启用列表中
        return enabledFeatures.contains(feature.toUpperCase());
    }

    @Override
    public LicenseStatus getLicenseStatus() {
        // 检查许可证是否过期
        if (currentLicense != null && currentLicense.getExpireDate() != null) {
            if (new Date().after(currentLicense.getExpireDate())) {
                licenseStatus = LicenseStatus.EXPIRED;
            }
        }
        
        return licenseStatus;
    }

    @Override
    public void refreshLicense() throws LicenseException {
        if (currentLicense == null || currentLicense.getLicenseId() == null) {
            throw new LicenseException("No license to refresh");
        }
        
        // 重新验证许可证
        validateLicense(currentLicense.getLicenseId());
    }

    @Override
    public LicenseInfo getLicenseInfo() {
        if (currentLicense == null) {
            // 返回默认的许可证信息
            LicenseInfo info = new LicenseInfo();
            return info;
        }
        
        return currentLicense;
    }

    @Override
    public List<String> getEnabledFeatures() {
        return new ArrayList<>(enabledFeatures);
    }

    @Override
    public String getLicenseProperty(String key) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        
        return licenseProperties.get(key);
    }

    @Override
    public Map<String, String> getAllLicenseProperties() {
        return new HashMap<>(licenseProperties);
    }
    
    /**
     * 添加启用的功能
     */
    public void addEnabledFeature(String feature) {
        if (feature != null && !feature.trim().isEmpty()) {
            String upperFeature = feature.toUpperCase();
            if (!enabledFeatures.contains(upperFeature)) {
                enabledFeatures.add(upperFeature);
            }
        }
    }
    
    /**
     * 移除启用的功能
     */
    public void removeEnabledFeature(String feature) {
        if (feature != null) {
            enabledFeatures.remove(feature.toUpperCase());
        }
    }
    
    /**
     * 设置许可证属性
     */
    public void setLicenseProperty(String key, String value) {
        if (key != null && !key.trim().isEmpty()) {
            if (value == null) {
                licenseProperties.remove(key);
            } else {
                licenseProperties.put(key, value);
            }
        }
    }
}

