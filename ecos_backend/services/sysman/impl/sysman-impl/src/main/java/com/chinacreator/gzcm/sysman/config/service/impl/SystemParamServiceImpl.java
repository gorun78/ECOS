package com.chinacreator.gzcm.sysman.config.service.impl;


import java.util.Date;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.config.dao.SystemParamDao;
import com.chinacreator.gzcm.sysman.config.entity.SystemParam;
import com.chinacreator.gzcm.sysman.config.service.ISystemParamService;

/**
 * 系统参数服务实现
 *
 * <p><b>刻意不注册为 Bean（D42，2026-11-13 验证批实测）</b>：本类依赖的 DAO 操作
 * {@code td_system_param} 表，而该表在本库**既不存在也无任何 DDL**（{@code pg_tables} 实测零命中，
 * {@code docs/50-db_script} 全域搜不到 {@code CREATE TABLE ... td_system_param}）；{@code td_} 前缀
 * 属 v1 legacy 命名，与 v2 的 {@code sys_config} 单表分组不是同一套。
 *
 * <p>因此补 {@code @Service} 只会把「显式 503 未装配」换成「装配后每次查询必 SQL 报错」——
 * 前者是诚实的不可用态，后者是把结构缺失伪装成运行时故障。真正的修法是先裁决该能力的归属
 * （并入 {@code sys_config} 还是新建表并补迁移），裁决前保持 {@code ObjectProvider} 取不到 Bean。</p>
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

