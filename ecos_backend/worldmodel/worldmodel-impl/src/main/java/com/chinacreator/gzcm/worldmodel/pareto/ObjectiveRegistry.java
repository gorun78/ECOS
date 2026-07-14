package com.chinacreator.gzcm.worldmodel.pareto;

import java.util.*;

/**
 * 目标函数注册表 — 单例，注册 5 个常用函数用于 NSGA-II 寻优。
 * <p>
 * 新增目标函数只需在此注册即可，ParetoController 通过名称(不区分大小写)查找。
 */
public class ObjectiveRegistry {

    private static final ObjectiveRegistry INSTANCE = new ObjectiveRegistry();

    @FunctionalInterface
    public interface EvalFunc {
        double evaluate(Individual ind);
    }

    private final Map<String, EvalFunc> registry = new LinkedHashMap<>();

    private ObjectiveRegistry() {
        // ── ZDT1: f1=x0², f2=g*(1-√(x0/g)), g=1+9*∑[i>0](xi)/(n-1) ──
        // 标准是 n 维，这里简化：变量 x,y → f1=x², f2=(x-2)²（仅用第一个变量）
        registry.put("zdt1_f1", ind -> {
            double x0 = ind.getVariable("x");
            return x0 * x0;
        });
        registry.put("zdt1_f2", ind -> {
            double x0 = ind.getVariable("x");
            double g = 1.0 + 9.0 * sumOfOthers(ind, "x") / (ind.getVariables().size() - 1 + 1e-9);
            return g * (1.0 - Math.sqrt(x0 / g));
        });

        // ── ZDT2: f1=x0², f2=g*(1-(x0/g)²) ──
        registry.put("zdt2_f1", ind -> {
            double x0 = ind.getVariable("x");
            return x0 * x0;
        });
        registry.put("zdt2_f2", ind -> {
            double x0 = ind.getVariable("x");
            double g = 1.0 + 9.0 * sumOfOthers(ind, "x") / (ind.getVariables().size() - 1 + 1e-9);
            return g * (1.0 - Math.pow(x0 / g, 2));
        });

        // ── f1 = x²   (经典)
        registry.put("f1", ind -> {
            double x0 = ind.getVariable("x");
            return x0 * x0;
        });

        // ── f2 = (x-2)²   (经典 ZDT)
        registry.put("f2", ind -> {
            double x0 = ind.getVariable("x");
            double t = x0 - 2.0;
            return t * t;
        });

        // ── 数据质量评分(模拟): 多维加权 ──
        registry.put("data-quality-score", ind -> {
            // 模拟：变量值越高质量越好（归一化 0-100）
            double sum = 0;
            for (double v : ind.getVariables().values()) sum += v;
            // 我们想要 MAX，但 NSGA-II 默认最小化，所以返回负值或通过 direction 反转
            return 100.0 - sum * 10.0 / ind.getVariables().size(); // 越小越好 → 质量低
        });

        // ── 计算成本(模拟): 多维加权 ──
        registry.put("compute-cost", ind -> {
            double sum = 0;
            for (double v : ind.getVariables().values()) sum += v * v;
            return sum / ind.getVariables().size();
        });

        // ── 覆盖率(模拟): 多维加权 ──
        registry.put("coverage", ind -> {
            double prod = 1.0;
            for (double v : ind.getVariables().values()) {
                prod *= Math.min(v / 10.0, 1.0);
            }
            return 100.0 * (1.0 - prod); // 越小越好 → 覆盖率低
        });
    }

    public static ObjectiveRegistry get() { return INSTANCE; }

    /**
     * 按名称评估（不区分大小写）。
     * 若未找到已注册函数，则回退为默认：f1→x², f2→(x-2)²，其余返回 0。
     */
    public double evaluate(String funcName, Individual ind) {
        EvalFunc f = registry.get(funcName.toLowerCase());
        if (f != null) return f.evaluate(ind);

        // 回退
        if ("f1".equalsIgnoreCase(funcName) || funcName.toLowerCase().contains("f1")) {
            double x0 = ind.getVariable("x");
            return x0 * x0;
        }
        if ("f2".equalsIgnoreCase(funcName) || funcName.toLowerCase().contains("f2")) {
            double x0 = ind.getVariable("x");
            double t = x0 - 2.0;
            return t * t;
        }
        return 0.0;
    }

    /** 注册自定义函数 */
    public void register(String name, EvalFunc func) {
        registry.put(name.toLowerCase(), func);
    }

    /** 所有已注册函数名 */
    public Set<String> names() {
        return new LinkedHashSet<>(registry.keySet());
    }

    // ── 内部工具 ──

    /** 除指定变量外的其他变量值之和 */
    static double sumOfOthers(Individual ind, String exclude) {
        double sum = 0.0;
        for (Map.Entry<String, Double> e : ind.getVariables().entrySet()) {
            if (!e.getKey().equals(exclude)) sum += e.getValue();
        }
        return sum;
    }
}
