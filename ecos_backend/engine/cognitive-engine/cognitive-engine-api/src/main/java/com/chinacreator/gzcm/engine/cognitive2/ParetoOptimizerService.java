package com.chinacreator.gzcm.engine.cognitive2;

import com.chinacreator.gzcm.engine.cognitive2.model.StrategyRecommendation;

public interface ParetoOptimizerService {
    StrategyRecommendation optimize(String goal, java.util.List<String> constraints);
}
