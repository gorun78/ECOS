package com.chinacreator.gzcm.engine.cognitive2.service.framework;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.framework.CognitiveFrameworkDefinition;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认知方法目录（详细设计-认知引擎 v1.0 §1.1/§5；方法与框架职责区分）。
 *
 * <p>方法不是可独立部署的模型，也不新建注册表：内置方法字典 + 由已发布框架
 * {@code methods[]} 声明形成 {@code usedBy} 关联。方法只作为分析手段被框架步骤引用；
 * 未经校准的方法评分不得解释为概率。</p>
 */
@Service
public class CognitiveMethodCatalogService {

    private final CognitiveFrameworkRegistryService registry;

    public CognitiveMethodCatalogService(CognitiveFrameworkRegistryService registry) {
        this.registry = registry;
    }

    /** 内置方法字典（MVP 五种；只读，不参与执行）。 */
    static List<Map<String, Object>> builtins() {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(method("ABDUCTION", "溯因推理", "由异常事实提出竞争性原因假设", "ABDUCTIVE_REASONING"));
        out.add(method("FALSIFICATION", "反证检查", "为假设寻找反证与反例", "ABDUCTIVE_REASONING"));
        out.add(method("VARIANCE_DECOMPOSITION", "差异分解", "按项目/部门/期间/因素分解偏差", "ANALYTIC"));
        out.add(method("BAYESIAN_UPDATE", "贝叶斯更新", "按证据更新离散信念分布（需校准来源）", "PROBABILISTIC"));
        out.add(method("SCENARIO_SENSITIVITY", "情景与敏感性分析", "对结构化假设做情景对比与敏感性排序", "SCENARIO_PLANNING"));
        return out;
    }

    /** 方法目录：内置方法 + 已发布框架声明的方法（去重后带 usedBy）。 */
    public List<Map<String, Object>> catalog() {
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> m : builtins()) {
            byId.put(String.valueOf(m.get("methodId")), new LinkedHashMap<>(m));
            byId.get(String.valueOf(m.get("methodId"))).put("usedBy", new ArrayList<Map<String, Object>>());
        }
        for (CognitiveFrameworkDefinition f : registry.list(null, null, "PUBLISHED")) {
            for (Map<String, Object> m : nullSafe(f.getMethods())) {
                String id = str(m.get("methodId"));
                if (id == null) {
                    continue;
                }
                Map<String, Object> entry = byId.computeIfAbsent(id, k -> {
                    Map<String, Object> created = new LinkedHashMap<>();
                    created.put("methodId", k);
                    created.put("name", str(m.get("name")));
                    created.put("purpose", str(m.get("purpose")));
                    created.put("category", "FRAMEWORK_DECLARED");
                    created.put("calibrated", false);
                    created.put("usedBy", new ArrayList<Map<String, Object>>());
                    return created;
                });
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> usedBy = (List<Map<String, Object>>) entry.get("usedBy");
                usedBy.add(Map.of(
                        "frameworkId", f.getFrameworkId(),
                        "version", f.getVersion(),
                        "stepId", m.getOrDefault("stepId", "")));
            }
        }
        return new ArrayList<>(byId.values());
    }

    public Map<String, Object> get(String methodId) {
        for (Map<String, Object> m : catalog()) {
            if (methodId != null && methodId.equals(m.get("methodId"))) {
                return m;
            }
        }
        throw new CognitiveGuardException(CognitiveErrorCode.E404_MODEL_ROW,
                "认知方法不存在: " + methodId, methodId);
    }

    private static Map<String, Object> method(String id, String name, String purpose, String category) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("methodId", id);
        m.put("name", name);
        m.put("purpose", purpose);
        m.put("category", category);
        m.put("executorType", "controlled_reasoning");
        m.put("calibrated", false);
        m.put("probabilitySemantics", "NONE");
        return m;
    }

    private static List<Map<String, Object>> nullSafe(List<Map<String, Object>> in) {
        return in == null ? List.of() : in;
    }

    private static String str(Object o) {
        return o == null || String.valueOf(o).isBlank() ? null : String.valueOf(o);
    }
}
