// TODO D4: 归位 ge-service（格）
package com.chinacreator.gzcm.engine.data.transform.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.engine.data.transform.TransformChain;
import com.chinacreator.gzcm.engine.data.transform.TransformException;
import com.chinacreator.gzcm.engine.data.transform.TransformStep;
import com.chinacreator.gzcm.engine.data.transform.model.DataFrame;
import com.chinacreator.gzcm.engine.data.transform.model.TransformResult;

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
        long inputCount = current.size(); // P0-2: rowCount = data.size()
        for (int i = 0; i < steps.size(); i++) {
            TransformStep step = steps.get(i);
            Map<String, Object> params = stepParams.get(i);
            current = step.transform(current, params);
        }
        long outputCount = current.size();
        TransformResult result = new TransformResult();
        result.setOutput(current);
        result.setSuccess(true);
        // P0-2 (Wave-4.2): 补齐 statistics 真实值
        TransformResult.TransformStatistics stats = result.getStatistics();
        stats.setInputCount(inputCount);
        stats.setOutputCount(outputCount);
        stats.setFilteredCount(Math.max(0, inputCount - outputCount));
        return result;
    }

    @Override
    public void clear() {
        steps.clear();
        stepParams.clear();
    }
}

