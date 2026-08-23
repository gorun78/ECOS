// TODO D4: 归位 ge-service（格）
package com.chinacreator.gzcm.engine.data.transform.service.impl;

import com.chinacreator.gzcm.engine.data.transform.TransformChain;
import com.chinacreator.gzcm.engine.data.transform.TransformException;
import com.chinacreator.gzcm.engine.data.transform.TransformStep;
import com.chinacreator.gzcm.engine.data.transform.model.DataFrame;
import com.chinacreator.gzcm.engine.data.transform.model.TransformResult;
import com.chinacreator.gzcm.engine.data.transform.service.ITransformService;

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

