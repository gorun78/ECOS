package com.chinacreator.gzcm.runtime.core.transform;

import java.util.Map;

import com.chinacreator.gzcm.runtime.core.transform.model.DataFrame;

/**
 * 转换步骤接口
 * 定义数据转换的单一处理步骤
 */
public interface TransformStep {
    
    /**
     * 获取步骤名称
     * @return 步骤名称
     */
    String getName();
    
    /**
     * 获取步骤类型
     * @return 步骤类型
     */
    String getType();
    
    /**
     * 执行转换
     * 
     * @param input 输入数据帧
     * @param params 转换参数
     * @return 转换后的数据帧
     * @throws TransformException
     */
    DataFrame transform(DataFrame input, Map<String, Object> params) throws TransformException;

    /**
     * 验证参数
     * @param params 参数
     * @return 是否有效
     */
    default boolean validateParams(Map<String, Object> params) {
        return true;
    }
}
