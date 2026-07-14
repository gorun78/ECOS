package com.chinacreator.gzcm.runtime.core.transform.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.transform.TransformChain;
import com.chinacreator.gzcm.runtime.core.transform.TransformException;
import com.chinacreator.gzcm.runtime.core.transform.TransformStep;
import com.chinacreator.gzcm.runtime.core.transform.model.DataFrame;
import com.chinacreator.gzcm.runtime.core.transform.model.TransformResult;

public class TransformChainImpl implements TransformChain {

    private final List<TransformStep> steps = new ArrayList<>();
    private final List<Map<String, Object>> stepParams = new ArrayList<>();

    @Override
    public TransformChain addStep(TransformStep step) {
        return addStep(step, null);
    }

    @Override
    public TransformChain addStep(TransformStep step, Map<String, Object> params) {
        steps.add(step);
        stepParams.add(params);
        return this;
    }

    @Override
    public List<TransformStep> getSteps() {
        return steps;
    }

    @Override
    public TransformResult execute(DataFrame input) throws TransformException {
        DataFrame current = input;
        for (int i = 0; i < steps.size(); i++) {
            TransformStep step = steps.get(i);
            Map<String, Object> params = stepParams.get(i);
            current = step.transform(current, params);
        }
        TransformResult result = new TransformResult();
        result.setOutput(current);
        result.setSuccess(true);
        return result;
    }

    @Override
    public void clear() {
        steps.clear();
        stepParams.clear();
    }
}

