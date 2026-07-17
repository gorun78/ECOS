package com.chinacreator.gzcm.services.agent.runtime.reflection;

import com.chinacreator.gzcm.services.agent.runtime.model.EvaluationScore;
import com.chinacreator.gzcm.services.agent.runtime.model.ExecutionPlan;
import com.chinacreator.gzcm.services.agent.runtime.model.ExecutionResult;
import com.chinacreator.gzcm.services.agent.runtime.model.ReflectionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReflectionServiceImpl implements ReflectionService {
    private static final Logger log = LoggerFactory.getLogger(ReflectionServiceImpl.class);

    @Override
    public ReflectionResult reflect(ExecutionResult result, EvaluationScore score) {
        log.info("Reflecting on result: {}", result.getId());
        ReflectionResult rr = new ReflectionResult();
        rr.setResultId(result.getId());
        if (score.getOverall() < 70) {
            rr.getGaps().add("Low overall score: " + score.getOverall());
            rr.getImprovements().add("Consider refining task decomposition");
        }
        if (score.getCompleteness() < 60) {
            rr.getGaps().add("Incomplete coverage");
            rr.getImprovements().add("Add more sub-tasks for comprehensive execution");
        }
        return rr;
    }

    @Override
    public ExecutionPlan optimize(ExecutionPlan plan, ReflectionResult reflection) {
        log.info("Optimizing plan {} based on reflection", plan.getId());
        if (!reflection.getGaps().isEmpty()) {
            log.info("Reflection found {} gaps, {} improvements", reflection.getGaps().size(), reflection.getImprovements().size());
        }
        return plan;
    }
}
