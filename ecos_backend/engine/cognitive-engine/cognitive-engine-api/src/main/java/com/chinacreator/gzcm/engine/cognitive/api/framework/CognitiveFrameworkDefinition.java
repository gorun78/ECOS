package com.chinacreator.gzcm.engine.cognitive.api.framework;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 认知框架定义（详细设计-认知引擎 v1.0 §5.1 字段面）。
 *
 * <p>这是"如何组织认知过程"的版本化治理元数据，不是业务事实、不是 K 类知识副本。
 * YAML/JSON 中的 {@code executorType} 必须映射白名单执行器；框架本身不持有副作用能力。</p>
 */
@Data
public class CognitiveFrameworkDefinition {
    private String frameworkId;
    private String version;
    private String name;
    private String category;
    private String purpose;
    private String status;
    private List<String> problemTypes;
    private List<String> domains;
    private List<String> preconditions;
    private String inputSchemaRef;
    private String outputSchemaRef;
    private List<Map<String, Object>> steps;
    /** 框架引用的认知方法（methodId/name/stepId）；步骤=过程，方法=分析手段，职责分离。 */
    private List<Map<String, Object>> methods;
    private List<String> requiredCapabilities;
    private String validationProfileRef;
    private String budgetPolicyRef;
    private List<String> stopConditions;
    private Map<String, Object> governance;
    private boolean contractResolvable;
    private String contentHash;
    private String createdBy;
    private String createdAt;
    private String publishedBy;
    private String publishedAt;
}
