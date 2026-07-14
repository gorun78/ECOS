package com.chinacreator.gzcm.runtime.core.transform.service;

import com.chinacreator.gzcm.runtime.core.transform.TransformChain;
import com.chinacreator.gzcm.runtime.core.transform.TransformException;
import com.chinacreator.gzcm.runtime.core.transform.TransformStep;
import com.chinacreator.gzcm.runtime.core.transform.model.DataFrame;
import com.chinacreator.gzcm.runtime.core.transform.model.TransformResult;

/**
 * 转换服务接口
 *
 * @author GZCM Runtime Team
 */
public interface ITransformService {
    
    /**
     * 执行转换
     *
     * @param input 输入数据框
     * @param chain 转换链
     * @return 转换结果
     * @throws TransformException
     */
    TransformResult transform(DataFrame input, TransformChain chain) throws TransformException;
    
    /**
     * 验证转换链
     *
     * @param chain 转换链
     * @return true表示转换链有效
     */
    boolean validateChain(TransformChain chain);
}

