package com.chinacreator.gzcm.runtime.core.transform.service.impl;

import com.chinacreator.gzcm.runtime.core.transform.TransformChain;
import com.chinacreator.gzcm.runtime.core.transform.TransformException;
import com.chinacreator.gzcm.runtime.core.transform.TransformStep;
import com.chinacreator.gzcm.runtime.core.transform.model.DataFrame;
import com.chinacreator.gzcm.runtime.core.transform.model.TransformResult;
import com.chinacreator.gzcm.runtime.core.transform.service.ITransformService;

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

