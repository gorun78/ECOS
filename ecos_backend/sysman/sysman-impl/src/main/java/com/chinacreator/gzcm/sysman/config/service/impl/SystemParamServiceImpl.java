package com.chinacreator.gzcm.sysman.config.service.impl;


import java.util.Date;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.config.dao.SystemParamDao;
import com.chinacreator.gzcm.sysman.config.entity.SystemParam;
import com.chinacreator.gzcm.sysman.config.service.ISystemParamService;

/**
 * 系统参数服务实现
 */
public class SystemParamServiceImpl implements ISystemParamService {
    
    private SystemParamDao systemParamDao;
    
    public SystemParamServiceImpl(SystemParamDao systemParamDao) {
        this.systemParamDao = systemParamDao;
    }
    
    @Override
    public SystemParam createParam(SystemParam param, String operator) throws SystemParamException {
        // 检查参数名是否已存在
        SystemParam existing = systemParamDao.findByName(param.getParamName());
        if (existing != null) {
            throw new SystemParamException("系统参数名称已存在: " + param.getParamName());
        }
        
        param.setCreatedBy(operator);
        param.setCreatedTime(new Date());
        systemParamDao.insert(param);
        return param;
    }
    
    @Override
    public SystemParam updateParam(String paramId, SystemParam param, String operator) throws SystemParamException {
        SystemParam existing = systemParamDao.findById(paramId);
        if (existing == null) {
            throw new SystemParamException("系统参数不存在: " + paramId);
        }
        
        param.setParamId(paramId);
        param.setUpdatedBy(operator);
        param.setUpdatedTime(new Date());
        systemParamDao.update(param);
        return param;
    }
    
    @Override
    public void deleteParam(String paramId, String operator) throws SystemParamException {
        SystemParam existing = systemParamDao.findById(paramId);
        if (existing == null) {
            throw new SystemParamException("系统参数不存在: " + paramId);
        }
        systemParamDao.delete(paramId);
    }
    
    @Override
    public SystemParam getParam(String paramId) throws SystemParamException {
        SystemParam param = systemParamDao.findById(paramId);
        if (param == null) {
            throw new SystemParamException("系统参数不存在: " + paramId);
        }
        return param;
    }
    
    @Override
    public SystemParam getParamByName(String paramName) throws SystemParamException {
        SystemParam param = systemParamDao.findByName(paramName);
        if (param == null) {
            throw new SystemParamException("系统参数不存在: " + paramName);
        }
        return param;
    }
    
    @Override
    public List<SystemParam> listParams(Map<String, Object> condition) throws SystemParamException {
        return systemParamDao.findByCondition(condition);
    }
    
    @Override
    public String getParamValue(String paramName) throws SystemParamException {
        SystemParam param = systemParamDao.findByName(paramName);
        if (param == null) {
            return null;
        }
        return param.getParamContent();
    }
}

