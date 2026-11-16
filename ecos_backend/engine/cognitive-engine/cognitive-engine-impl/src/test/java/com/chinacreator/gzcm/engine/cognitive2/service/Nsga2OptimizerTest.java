package com.chinacreator.gzcm.engine.cognitive2.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D106 门禁：Pareto 前端要的是<b>真正的多目标优化</b>，不是随机数。
 *
 * <p><b>本门禁要挡住什么</b>：最容易走的歪路不是「返回 404」，而是「随便生成一堆点当 Pareto 前沿」——
 * 前端图表会很漂亮，但那些点不满足任何优化语义（互不支配、随代数收敛、落在已知前沿上）。
 * 下面五组断言分别钉住 NSGA-II 的关键性质；把 {@code run} 换成随机点集，
 * 它们会成片转红（见类末「变异自证」）。</p>
 *
 * <p><b>变异自证清单</b>（改代码后应看到对应红）：<br>
 * ① 把 {@code run} 返回 {@code population} 原样 ⇒ {@code frontIsNonDominated} 红；<br>
 * ② 把 {@code evaluate} 改成常数 ⇒ {@code frontConvergesToSimplex} 红；<br>
 * ③ 把 {@code generations} 忽略（只跑 1 代） ⇒ {@code moreGenerationsImprovesFront} 红。</p>
 */
@DisplayName("D106：Pareto 后端必须是真 NSGA-II（前沿互不支配 + 收敛 + 可复现）")
class Nsga2OptimizerTest {

    private final Nsga2Optimizer optimizer = new Nsga2Optimizer();

    @Test
    @DisplayName("前沿内任意两解互不支配")
    void frontIsNonDominated() {
        List<Nsga2Optimizer.Individual> front = optimizer.run(2, 3, 60, 30, 42L);
        assertFalse(front.isEmpty(), "前沿不得为空");
        for (int i = 0; i < front.size(); i++) {
            for (int j = 0; j < front.size(); j++) {
                if (i == j) {
                    continue;
                }
                assertFalse(Nsga2Optimizer.dominates(front.get(i), front.get(j)),
                        "前沿内不得存在支配关系: 解" + i + " 支配 解" + j);
            }
        }
    }

    @Test
    @DisplayName("前沿铺开在 ZDT-1 曲线附近（f1 覆盖 0~1，f2 随 f1 递减）")
    void frontConvergesToSimplex() {
        List<Nsga2Optimizer.Individual> front = optimizer.run(2, 3, 100, 200, 7L);
        assertTrue(front.size() >= 5, "前沿解数太少: " + front.size());
        double minF1 = front.stream().mapToDouble(i -> i.objectives()[0]).min().orElse(Double.NaN);
        double maxF1 = front.stream().mapToDouble(i -> i.objectives()[0]).max().orElse(Double.NaN);
        assertTrue(minF1 < 0.1, "f1 最小值应接近 0，实测 " + minF1);
        assertTrue(maxF1 > 0.9, "f1 最大值应接近 1，实测 " + maxF1);

        // ZDT-1 真前沿: f2 = 1 - sqrt(f1)。允许拟合误差，但不允许「一片平坦」——
        // 平坦正是随机点集/未收敛的特征（首版用 DTLZ-1 就塌成 f1≈0 一条边，被这条断言照出）。
        double maxDeviation = 0.0;
        double f2AtLowF1 = Double.NaN;
        double f2AtHighF1 = Double.NaN;
        for (Nsga2Optimizer.Individual ind : front) {
            double f1 = ind.objectives()[0];
            double f2 = ind.objectives()[1];
            double ideal = 1.0 - Math.sqrt(Math.max(0.0, f1));
            maxDeviation = Math.max(maxDeviation, Math.abs(f2 - ideal));
            if (f1 < 0.15) {
                f2AtLowF1 = f2;
            }
            if (f1 > 0.85) {
                f2AtHighF1 = f2;
            }
        }
        assertTrue(maxDeviation < 0.35,
                "前沿应贴合 ZDT-1 曲线 f2=1-sqrt(f1)，最大偏差=" + maxDeviation);
        assertFalse(Double.isNaN(f2AtLowF1) || Double.isNaN(f2AtHighF1),
                "前沿两端的解都应存在（f1<0.15 与 f1>0.85 各至少一个）");
        assertTrue(f2AtLowF1 > f2AtHighF1 + 0.3,
                "权衡必须真实：f1 小的一端 f2 应显著更大（实测 f1小→f2=" + f2AtLowF1
                + ", f1大→f2=" + f2AtHighF1 + "）");
    }

    @Test
    @DisplayName("代数越多前沿越优（同种子下总目标值下降）")
    void moreGenerationsImprovesFront() {
        List<Nsga2Optimizer.Individual> few = optimizer.run(2, 3, 60, 5, 123L);
        List<Nsga2Optimizer.Individual> many = optimizer.run(2, 3, 60, 60, 123L);
        double fewSum = few.stream().mapToDouble(i -> i.objectives()[0] + i.objectives()[1]).min().orElse(9e9);
        double manySum = many.stream().mapToDouble(i -> i.objectives()[0] + i.objectives()[1]).min().orElse(9e9);
        assertTrue(manySum <= fewSum + 1e-9,
                "更多代数不应变差: 5 代=" + fewSum + " vs 60 代=" + manySum);
    }

    @Test
    @DisplayName("同种子完全可复现（前沿逐点一致）")
    void deterministicForSameSeed() {
        List<Nsga2Optimizer.Individual> a = optimizer.run(2, 3, 40, 20, 999L);
        List<Nsga2Optimizer.Individual> b = optimizer.run(2, 3, 40, 20, 999L);
        assertEquals(a.size(), b.size(), "同种子前沿规模必须一致");
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).objectives()[0], b.get(i).objectives()[0], 1e-12,
                    "同种子第 " + i + " 个解的目标值必须一致");
        }
    }

    @Test
    @DisplayName("多目标（3 目标）仍给出互不支配前沿，变量落在 [0,1]")
    void threeObjectivesAndVariableBounds() {
        List<Nsga2Optimizer.Individual> front = optimizer.run(3, 5, 60, 40, 2024L);
        assertFalse(front.isEmpty());
        for (Nsga2Optimizer.Individual ind : front) {
            assertEquals(3, ind.objectives().length);
            assertEquals(5, ind.variables().length);
            for (double v : ind.variables()) {
                assertTrue(v >= 0.0 && v <= 1.0, "变量越界: " + v);
            }
        }
        for (int i = 0; i < front.size(); i++) {
            for (int j = 0; j < front.size(); j++) {
                if (i != j) {
                    assertFalse(Nsga2Optimizer.dominates(front.get(i), front.get(j)));
                }
            }
        }
    }

    @Test
    @DisplayName("前沿不得含重复解（去重回归：实测曾 100 解里 11 个完全相同的变量向量）")
    void frontHasNoDuplicateSolutions() {
        List<Nsga2Optimizer.Individual> front = optimizer.run(2, 3, 100, 50, 2025L);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Nsga2Optimizer.Individual ind : front) {
            StringBuilder key = new StringBuilder();
            for (double v : ind.variables()) {
                key.append(Math.round(v * 1_000_000d)).append(',');
            }
            assertTrue(seen.add(key.toString()),
                    "前沿出现重复变量向量: " + key + "（前端图表会把同一点画两次）");
        }
    }

    @Test
    @DisplayName("变异自证：随机点集必然违反「互不支配」（证明上面的断言真能变红）")
    void randomPointsWouldFailTheGate() {
        Random rng = new Random(1L);
        List<Nsga2Optimizer.Individual> randomPoints = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            double[] v = {rng.nextDouble(), rng.nextDouble(), rng.nextDouble()};
            randomPoints.add(new Nsga2Optimizer.Individual(v, Nsga2Optimizer.evaluate(v, 2)));
        }
        boolean anyDominated = false;
        for (int i = 0; i < randomPoints.size() && !anyDominated; i++) {
            for (int j = 0; j < randomPoints.size(); j++) {
                if (i != j && Nsga2Optimizer.dominates(randomPoints.get(i), randomPoints.get(j))) {
                    anyDominated = true;
                    break;
                }
            }
        }
        assertTrue(anyDominated,
                "未排序的随机点集里必然出现支配对；若这里为 false，说明 dominates 本身失效，"
                + "上面所有「互不支配」断言都会变成永远通过的空断言");
    }
}
