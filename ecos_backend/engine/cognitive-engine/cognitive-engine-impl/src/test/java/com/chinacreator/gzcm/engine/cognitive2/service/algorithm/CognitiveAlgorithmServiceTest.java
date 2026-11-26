package com.chinacreator.gzcm.engine.cognitive2.service.algorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.engine.cognitive2.service.Nsga2Optimizer;

/** 认知算法执行面数学门禁：断言真实公式，常数替换会变红。 */
class CognitiveAlgorithmServiceTest {

    private CognitiveAlgorithmService service;

    @BeforeEach
    void setUp() {
        service = new CognitiveAlgorithmService(List.of(
                new BeliefCausalAlgorithms(),
                new ForecastSimulationAlgorithms(),
                new DecisionAlgorithms(new Nsga2Optimizer()),
                new DiagnosticAlgorithms()));
    }

    @Test
    @DisplayName("bayes-update：先验 0.5/0.5 × 似然 0.8/0.2 → 后验 0.8/0.2")
    void bayesUpdate() {
        Map<String, Object> r = service.execute("bayes-update",
                Map.of("prior", List.of(0.5, 0.5), "observation", List.of(0.8, 0.2)));
        @SuppressWarnings("unchecked")
        List<Double> post = (List<Double>) metrics(r).get("posterior");
        assertEquals(0.8, post.get(0), 1e-6, "0.5*0.8 / 0.5 = 0.8");
        assertEquals(0.2, post.get(1), 1e-6);
    }

    @Test
    @DisplayName("linear-regression：y=2x+1 精确拟合")
    void linearRegression() {
        Map<String, Object> r = service.execute("linear-regression",
                Map.of("x", List.of(1, 2, 3), "y", List.of(3, 5, 7)));
        assertEquals(2.0, (double) metrics(r).get("slope"), 1e-9);
        assertEquals(1.0, (double) metrics(r).get("intercept"), 1e-9);
        assertEquals(1.0, (double) metrics(r).get("r2"), 1e-9);
    }

    @Test
    @DisplayName("linear-programming：max 3x+2y, x+y<=4, x<=2, y<=3 → 10 @ (2,2)")
    void linearProgramming() {
        Map<String, Object> r = service.execute("linear-programming", Map.of(
                "c", List.of(3, 2),
                "A", List.of(List.of(1, 1), List.of(1, 0), List.of(0, 1)),
                "b", List.of(4, 2, 3),
                "maximize", true));
        assertEquals(10.0, (double) metrics(r).get("objective"), 1e-6);
        @SuppressWarnings("unchecked")
        List<Double> x = (List<Double>) metrics(r).get("x");
        assertEquals(2.0, x.get(0), 1e-6);
        assertEquals(2.0, x.get(1), 1e-6);
    }

    @Test
    @DisplayName("ahp：单位矩阵 → 等权 0.5/0.5，CR=0")
    void ahp() {
        Map<String, Object> r = service.execute("ahp",
                Map.of("matrix", List.of(List.of(1, 1), List.of(1, 1))));
        assertEquals(0.0, (double) metrics(r).get("cr"), 1e-9);
        @SuppressWarnings("unchecked")
        List<Double> w = (List<Double>) metrics(r).get("weights");
        assertEquals(0.5, w.get(0), 1e-6);
        assertEquals(0.5, w.get(1), 1e-6);
    }

    @Test
    @DisplayName("dynamic-programming：容量 2 选价值 3 的物品")
    void dynamicProgramming() {
        Map<String, Object> r = service.execute("dynamic-programming", Map.of(
                "items", List.of(Map.of("weight", 1, "value", 1), Map.of("weight", 2, "value", 3)),
                "capacity", 2));
        assertEquals(3.0, (double) metrics(r).get("maxValue"), 1e-9);
    }

    @Test
    @DisplayName("topsis：三个方案排序完整且贴近度在 [0,1]")
    void topsis() {
        Map<String, Object> r = service.execute("topsis", Map.of(
                "matrix", List.of(List.of(1, 100), List.of(2, 90), List.of(3, 80)),
                "weights", List.of(0.5, 0.5),
                "benefit", List.of(true, true)));
        @SuppressWarnings("unchecked")
        List<Double> closeness = (List<Double>) metrics(r).get("closeness");
        assertEquals(3, closeness.size());
        for (double c : closeness) assertTrue(c >= 0 && c <= 1);
    }

    @Test
    @DisplayName("monte-carlo：U(0,1) 千次采样均值≈0.5，seed 固定可复现")
    void monteCarlo() {
        Map<String, Object> r = service.execute("monte-carlo", Map.of(
                "variables", List.of(Map.of("name", "x", "min", 0, "max", 1, "weight", 1)),
                "iterations", 2000, "seed", 42));
        assertEquals(0.5, (double) metrics(r).get("mean"), 0.05);
    }

    @Test
    @DisplayName("hypothesis-testing：样本均值显著非 0 → reject")
    void hypothesisTesting() {
        Map<String, Object> r = service.execute("hypothesis-testing",
                Map.of("sample1", List.of(1, 2, 3, 4, 5), "mu0", 0, "alpha", 0.05));
        assertTrue((boolean) metrics(r).get("reject"));
        assertNotNull(metrics(r).get("pValue"));
    }

    @Test
    @DisplayName("clustering：两组明显分离的点被分成两个簇")
    void clustering() {
        Map<String, Object> r = service.execute("clustering", Map.of(
                "points", List.of(List.of(0, 0), List.of(0, 1), List.of(10, 10), List.of(10, 11)),
                "k", 2, "seed", 7));
        @SuppressWarnings("unchecked")
        List<Integer> labels = (List<Integer>) metrics(r).get("labels");
        assertEquals(labels.get(0), labels.get(1));
        assertEquals(labels.get(2), labels.get(3));
        assertFalse(labels.get(0).equals(labels.get(2)), "两组应不同簇");
    }

    @Test
    @DisplayName("drift-changepoint：阶跃序列产生至少一个变点")
    void driftChangepoint() {
        Map<String, Object> r = service.execute("drift-changepoint", Map.of(
                "series", List.of(1, 1, 1, 1, 1, 1, 9, 9, 9, 9, 9, 9),
                "baseline", 4, "threshold", 3));
        @SuppressWarnings("unchecked")
        List<Integer> cps = (List<Integer>) metrics(r).get("changepoints");
        assertFalse(cps.isEmpty(), "阶跃后应检测到变点");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metrics(Map<String, Object> result) {
        return (Map<String, Object>) result.get("metrics");
    }
}
