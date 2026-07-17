package com.chinacreator.gzcm.services.agent.runtime.evaluator;

import com.chinacreator.gzcm.services.agent.runtime.model.EvaluationScore;
import com.chinacreator.gzcm.services.agent.runtime.model.ExecutionResult;
import com.chinacreator.gzcm.services.agent.runtime.model.Goal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EvaluatorServiceImpl implements EvaluatorService {
    private static final Logger log = LoggerFactory.getLogger(EvaluatorServiceImpl.class);

    @Override
    public EvaluationScore evaluate(ExecutionResult result, Goal goal) {
        log.info("Evaluating result: {}", result.getId());
        EvaluationScore score = new EvaluationScore();
        score.setResultId(result.getId());
        double base = result.isSuccess() ? 80.0 : 40.0;
        score.setCorrectness(base);
        score.setCompleteness(base - 5);
        score.setSafety(95.0);
        score.setEfficiency(base + 5);
        score.setOverall((score.getCorrectness() + score.getCompleteness() + score.getSafety() + score.getEfficiency()) / 4);
        score.setFeedback(result.isSuccess() ? "Goal achieved" : "Goal not fully achieved");
        return score;
    }

    @Override
    public List<EvaluationScore> batchEvaluate(List<ExecutionResult> results, Goal goal) {
        List<EvaluationScore> scores = new ArrayList<>();
        for (ExecutionResult r : results) {
            scores.add(evaluate(r, goal));
        }
        return scores;
    }
}
