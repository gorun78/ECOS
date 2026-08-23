// TODO D4: 归位 ge-service（格）
package com.chinacreator.gzcm.engine.data.transform.service;

import com.chinacreator.gzcm.engine.data.transform.TransformChain;
import com.chinacreator.gzcm.engine.data.transform.TransformException;
import com.chinacreator.gzcm.engine.data.transform.TransformStep;
import com.chinacreator.gzcm.engine.data.transform.model.DataFrame;
import com.chinacreator.gzcm.engine.data.transform.model.TransformResult;

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

