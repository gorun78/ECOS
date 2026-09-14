package com.chinacreator.gzcm.sysman.iam.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.sysman.iam.dao.TenantConfigDao;
import com.chinacreator.gzcm.sysman.iam.entity.TenantConfig;
import com.chinacreator.gzcm.sysman.iam.service.ITenantConfigService;

@Service
public class TenantConfigServiceImpl implements ITenantConfigService {

    private final TenantConfigDao tenantConfigDao;

    @Autowired
    public TenantConfigServiceImpl(TenantConfigDao tenantConfigDao) {
        this.tenantConfigDao = tenantConfigDao;
    }

    @Override
    public void setConfig(String tenantId, String key, String value) throws TenantConfigException {
        try {
            TenantConfig existing = tenantConfigDao.findByTenantAndKey(tenantId, key);
            if (existing == null) {
                TenantConfig config = new TenantConfig();
                config.setConfigId(UUID.randomUUID().toString());
                config.setTenantId(tenantId);
                config.setConfigKey(key);
                config.setConfigValue(value);
                tenantConfigDao.insert(config);
            } else {
                existing.setConfigValue(value);
                tenantConfigDao.update(existing);
            }
        } catch (Exception e) {
            throw new TenantConfigException("设置租户配置失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getConfig(String tenantId, String key) throws TenantConfigException {
        try {
            TenantConfig existing = tenantConfigDao.findByTenantAndKey(tenantId, key);
            return existing != null ? existing.getConfigValue() : null;
        } catch (Exception e) {
            throw new TenantConfigException("获取租户配置失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TenantConfig> listConfigs(String tenantId) throws TenantConfigException {
        try {
            return tenantConfigDao.listByTenantId(tenantId);
        } catch (Exception e) {
            throw new TenantConfigException("查询租户配置失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteConfig(String tenantId, String key) throws TenantConfigException {
        try {
            TenantConfig existing = tenantConfigDao.findByTenantAndKey(tenantId, key);
            if (existing != null) {
                tenantConfigDao.delete(existing.getConfigId());
            }
        } catch (Exception e) {
            throw new TenantConfigException("删除租户配置失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, String> getAllAsMap(String tenantId) throws TenantConfigException {
        List<TenantConfig> list = listConfigs(tenantId);
        Map<String, String> map = new HashMap<>();
        for (TenantConfig c : list) {
            map.put(c.getConfigKey(), c.getConfigValue());
        }
        return map;
    }
}


