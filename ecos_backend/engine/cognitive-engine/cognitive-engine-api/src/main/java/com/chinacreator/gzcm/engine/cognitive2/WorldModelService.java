package com.chinacreator.gzcm.engine.cognitive2;

import com.chinacreator.gzcm.engine.cognitive2.model.WorldState;
import com.chinacreator.gzcm.engine.cognitive2.model.Scenario;
import com.chinacreator.gzcm.engine.cognitive2.model.SimulationResult;
import com.chinacreator.gzcm.engine.cognitive2.model.StrategyRecommendation;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalEdge;
import java.util.List;

public interface WorldModelService {
    WorldState getCurrentState();
    SimulationResult simulate(Scenario scenario);
    StrategyRecommendation recommendStrategy(String goal);
    List<CausalEdge> getCausalGraph();
}
