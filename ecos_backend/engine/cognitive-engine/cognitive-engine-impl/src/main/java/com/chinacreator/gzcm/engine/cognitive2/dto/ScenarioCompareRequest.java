package com.chinacreator.gzcm.engine.cognitive2.dto;

import java.util.List;
import java.util.Map;

/**
 * 情景对比请求（DI-2 #147EK）。
 *
 * <p>字段：scenarios[]，每项 name / variables（变量包自由 JSON，缺省为空映射）。</p>
 */
public class ScenarioCompareRequest {

    private List<ItemSpec> scenarios;

    public List<ItemSpec> getScenarios() {
        return scenarios;
    }

    public void setScenarios(List<ItemSpec> scenarios) {
        this.scenarios = scenarios;
    }

    /** 对比输入项：name / variables。 */
    public static class ItemSpec {

        private String name;
        private Map<String, Object> variables;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<String, Object> getVariables() {
            return variables;
        }

        public void setVariables(Map<String, Object> variables) {
            this.variables = variables;
        }
    }
}
