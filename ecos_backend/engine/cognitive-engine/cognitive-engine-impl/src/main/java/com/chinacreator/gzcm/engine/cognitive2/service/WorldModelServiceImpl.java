package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.WorldModelService;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalEdge;
import com.chinacreator.gzcm.engine.cognitive2.model.Scenario;
import com.chinacreator.gzcm.engine.cognitive2.model.SimulationResult;
import com.chinacreator.gzcm.engine.cognitive2.model.StrategyRecommendation;
import com.chinacreator.gzcm.engine.cognitive2.model.WorldState;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 世界模型 Service — 显式未实现（详细设计-05 F05-16 / X-43）。
 *
 * <p>原实现返回固定 confidence 0.75 / 0.15 / 0.3 与硬编码因果边
 * {@code CustomerSatisfaction→RenewalRate w=0.8}，属"以 200 对外声明虚假现状"。本期无实现，
 * 四个方法一律 fail-loud 抛 {@link CognitiveGuardException}
 * （{@code ECOS-COG-503} → {@code CognitiveErrorAdvice} 映射真实 HTTP 501），
 * 保证任何调用方（含未来新增）都拿不到伪数据；对外 stub 端点见
 * {@code controller.WorldModelController}。</p>
 */
@Service
public class WorldModelServiceImpl implements WorldModelService {

    @Override
    public WorldState getCurrentState() {
        throw notImplemented();
    }

    @Override
    public SimulationResult simulate(Scenario scenario) {
        throw notImplemented();
    }

    @Override
    public StrategyRecommendation recommendStrategy(String goal) {
        throw notImplemented();
    }

    @Override
    public List<CausalEdge> getCausalGraph() {
        throw notImplemented();
    }

    private static CognitiveGuardException notImplemented() {
        return new CognitiveGuardException(
                CognitiveErrorCode.E503_NOT_IMPLEMENTED,
                CognitiveErrorCode.MSG_NOT_IMPLEMENTED);
    }
}
