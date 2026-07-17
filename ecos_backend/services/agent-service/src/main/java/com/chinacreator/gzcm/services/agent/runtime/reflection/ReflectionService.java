package com.chinacreator.gzcm.services.agent.runtime.reflection;

import com.chinacreator.gzcm.services.agent.runtime.model.EvaluationScore;
import com.chinacreator.gzcm.services.agent.runtime.model.ExecutionPlan;
import com.chinacreator.gzcm.services.agent.runtime.model.ExecutionResult;
import com.chinacreator.gzcm.services.agent.runtime.model.ReflectionResult;

public interface ReflectionService {
    ReflectionResult reflect(ExecutionResult result, EvaluationScore score);
    ExecutionPlan optimize(ExecutionPlan plan, ReflectionResult reflection);
}
