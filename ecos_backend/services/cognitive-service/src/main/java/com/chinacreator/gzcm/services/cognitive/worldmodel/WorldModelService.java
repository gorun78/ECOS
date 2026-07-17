package com.chinacreator.gzcm.services.cognitive.worldmodel;

import com.chinacreator.gzcm.services.cognitive.model.CausalEdge;
import com.chinacreator.gzcm.services.cognitive.model.Scenario;
import com.chinacreator.gzcm.services.cognitive.model.SimulationResult;
import com.chinacreator.gzcm.services.cognitive.model.StrategyRecommendation;
import com.chinacreator.gzcm.services.cognitive.model.WorldState;
import java.util.List;

public interface WorldModelService {
    WorldState getCurrentState();
    SimulationResult simulate(Scenario scenario);
    StrategyRecommendation recommendStrategy(String goal);
    List<CausalEdge> getCausalGraph();
}
