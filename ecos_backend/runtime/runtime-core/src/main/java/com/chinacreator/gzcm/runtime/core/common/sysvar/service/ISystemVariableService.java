package com.chinacreator.gzcm.runtime.core.common.sysvar.service;

import com.chinacreator.gzcm.runtime.core.common.sysvar.bean.SystemVariable;

public interface ISystemVariableService {
    /**
     * 查找系统变量
     * @param varCode 变量编码
     * @param scopeId 作用域ID
     * @param nodeId 节点ID
     * @return 系统变量对象，如果不存在则返回null
     * @throws Exception
     */
    SystemVariable findVariable(String varCode, String scopeId, String nodeId) throws Exception;
}
