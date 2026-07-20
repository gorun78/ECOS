package com.chinacreator.gzcm.engine.cognitive2;

import com.chinacreator.gzcm.engine.cognitive2.model.Scenario;
import com.chinacreator.gzcm.engine.cognitive2.model.SimulationResult;

public interface ScenarioSimulatorService {
    SimulationResult runSimulation(Scenario scenario);
}
