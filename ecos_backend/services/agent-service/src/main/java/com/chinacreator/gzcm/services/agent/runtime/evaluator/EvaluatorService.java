package com.chinacreator.gzcm.services.agent.runtime.evaluator;

import com.chinacreator.gzcm.services.agent.runtime.model.EvaluationScore;
import com.chinacreator.gzcm.services.agent.runtime.model.ExecutionResult;
import com.chinacreator.gzcm.services.agent.runtime.model.Goal;
import java.util.List;

public interface EvaluatorService {
    EvaluationScore evaluate(ExecutionResult result, Goal goal);
    List<EvaluationScore> batchEvaluate(List<ExecutionResult> results, Goal goal);
}
