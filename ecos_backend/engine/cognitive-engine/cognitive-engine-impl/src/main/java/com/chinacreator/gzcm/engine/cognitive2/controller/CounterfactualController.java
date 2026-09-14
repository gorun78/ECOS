package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive2.dto.counterfactual.CounterfactualRequest;
import com.chinacreator.gzcm.engine.cognitive2.dto.counterfactual.CounterfactualResult;
import com.chinacreator.gzcm.engine.cognitive2.service.mental.CounterfactualSimulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 反事实推演 REST API — 心智层推演核心（PMO-59 P3a / ADR-9）。
 *
 * <pre>
 * POST /api/v1/cognitive/counterfactual — do(A) 干预式反事实推演（蒙特卡洛 N 次采样，
 *        输出风险四指标 expectedBenefit/maxDrawdown/lossProbability/volatilityRange
 *        + 敏感性 Top3 + 假设前提留痕；纯 Java 数值，LLM 零参与；同 seed 双跑可复现）
 * </pre>
 *
 * <p>三滤波器（PMO-59 P3a 指令 §实现决策 2 核对，零新增登记）：
 * {@code /api/v1/cognitive/**} 已被 SecurityConfig permitAll + ClearanceInterceptor 豁免
 * + auth.whitelist 既有通配覆盖；VersionPrefixRewriteFilter V1_REWRITE_MAP 无 cognitive 条目=KEEP。
 * 推演仅读不写心智状态；审计经 EventBus 发 Kafka {@code ecos.audit}（Simulator 内单点）。</p>
 */
@RestController
@RequestMapping("/api/v1/cognitive")
public class CounterfactualController {

    private static final Logger log = LoggerFactory.getLogger(CounterfactualController.class);

    private final CounterfactualSimulator simulator;

    public CounterfactualController(CounterfactualSimulator simulator) {
        this.simulator = simulator;
    }

    /**
     * 反事实推演（强类型 DTO；domain/variableName 必填；sampleCount 默认 1000 上限 5000）。
     *
     * <p>语义：读 variableName + interventions 各变量的 belief 当前版本分布作为输入，
     * do(A) 干预（SET 定点 / DELTA 平移）→ 蒙特卡洛配对采样 → 四指标 + 敏感性 Top3；
     * 假设有效性过滤随结果透出（assumptionRefs / excludedAssumptions）。</p>
     */
    @PostMapping("/counterfactual")
    public ApiResponse<CounterfactualResult> counterfactual(@RequestBody CounterfactualRequest request) {
        log.info("反事实推演请求: domain={}, variable={}, N={}, seed={}",
                request == null ? null : request.getDomain(),
                request == null ? null : request.getVariableName(),
                request == null ? null : request.getSampleCount(),
                request == null ? null : request.getSeed());
        return ApiResponse.success(simulator.simulate(request));
    }
}
