// TODO D4: 归位 ge-service（格）
package com.chinacreator.gzcm.engine.data.transform.service.impl;

import com.chinacreator.gzcm.engine.data.transform.TransformChain;
import com.chinacreator.gzcm.engine.data.transform.TransformException;
import com.chinacreator.gzcm.engine.data.transform.TransformStep;
import com.chinacreator.gzcm.engine.data.transform.model.DataFrame;
import com.chinacreator.gzcm.engine.data.transform.model.TransformResult;
import com.chinacreator.gzcm.engine.data.transform.service.ITransformService;
import org.springframework.stereotype.Service;

/**
 * 转换服务实现 — 委托 {@link TransformChain} 执行多步骤串联。
 * <p>
 * Wave-2B ge (D→I) 收口：以 Spring Bean 形式注册，
 * 供 {@code TransformController} 注入；步骤内本模块 internal new，
 * 不引入跨引擎依赖。
 */
@Service
public class TransformServiceImpl implements ITransformService {

    @Override
    public TransformResult transform(DataFrame input, TransformChain chain) throws TransformException {
        if (chain == null) {
            TransformResult result = new TransformResult();
            result.setOutput(input);
            return result;
        }
        return chain.execute(input);
    }

    @Override
    public boolean validateChain(TransformChain chain) {
        return chain != null && !chain.getSteps().isEmpty();
    }
}

