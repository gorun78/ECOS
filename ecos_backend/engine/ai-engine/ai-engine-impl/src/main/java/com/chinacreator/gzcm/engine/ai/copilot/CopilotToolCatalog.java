package com.chinacreator.gzcm.engine.ai.copilot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Copilot 工具总表（单一清单）。
 *
 * <p>六域全生命周期工具面：T1 只读 / T2 草稿写 / T3 受控执行（confirm=true）；
 * T4（审批、发布/回滚、删除、密钥、冒充、审计写）<b>不出现在本表</b>，由用户在各模块人工完成。</p>
 *
 * <p>本表是三处消费方的唯一来源：① {@code CopilotToolRegistrar} 据此注册进 {@code ToolRegistry}；
 * ② {@code CopilotSeedCatalog} 据此生成各智能体 {@code toolWhitelist}；
 * ③ 前端「工具面」管理面板经 {@code GET /api/v1/agent/copilot/tools} 消费。
 * 端点以源码为准（网关路径 = 属主 Controller 原路径；owner/mode 见各行）。</p>
 */
public final class CopilotToolCatalog {

    // ── 智能体 id（与 CopilotAgentCatalog 对齐） ──
    public static final String DATA = "agent-data";
    public static final String ONTOLOGY = "agent-ontology";
    public static final String KNOWLEDGE = "agent-knowledge";
    public static final String AI = "agent-ai";
    public static final String SCENARIO = "agent-scenario";
    public static final String SECURITY = "agent-security";

    // ── 属主 ──
    private static final String O_DATA = "datanet";
    private static final String O_ONT = "buszhi";
    private static final String O_KB = "dccheng";
    private static final String O_AI = "aiming";
    private static final String O_WS = "workspace";
    private static final String O_SEC = "sysman";

    /** 工具参数定义。 */
    public record Param(String name, String type, boolean required, String desc) {}

    /**
     * 工具定义。
     *
     * @param name    工具名（全局唯一，模块_动词_名词）
     * @param agent   所属智能体 id
     * @param stage   生命周期阶段（工作台分组展示用）
     * @param tier    T1 只读 / T2 草稿写 / T3 受控执行
     * @param method  GET / POST / PUT
     * @param owner   route-manifest 属主
     * @param service true=SERVICE 直连属主端口；false=MONOLITH(:8080，走凭据中继)
     * @param path    端点路径，支持 {var} 占位（从入参取值并 URL 编码）
     * @param params  参数面（写工具自动追加 payload/confirm/idempotencyKey）
     * @param desc    工具描述（≤120 字符）
     */
    public record Tool(String name, String agent, String stage, String tier, String method,
                       String owner, boolean service, String path, List<Param> params, String desc) {
    }

    private static final List<Tool> ALL = build();

    public static List<Tool> all() {
        return ALL;
    }

    public static List<Tool> forAgent(String agentId) {
        return ALL.stream().filter(t -> t.agent().equals(agentId)).toList();
    }

    public static List<String> namesForAgent(String agentId) {
        return forAgent(agentId).stream().map(Tool::name).toList();
    }

    // ═══════════════ 构建辅助 ═══════════════

    private static Param p(String name, String type, boolean required, String desc) {
        return new Param(name, type, required, desc);
    }

    private static Param id(String name) {
        return p(name, "string", true, name + "（必填）");
    }

    private static Param opt(String name) {
        return p(name, "string", false, name + "（可选）");
    }

    private static Param optN(String name) {
        return p(name, "number", false, name + "（可选）");
    }

    private static Tool g(String name, String agent, String stage, String owner, String path,
                          String desc, Param... ps) {
        return new Tool(name, agent, stage, "T1", "GET", owner, isService(owner), path, List.of(ps), desc);
    }

    private static Tool post(String name, String agent, String stage, String tier, String owner, String path,
                             String desc, Param... ps) {
        return new Tool(name, agent, stage, tier, "POST", owner, isService(owner), path, List.of(ps), desc);
    }

    private static Tool put(String name, String agent, String stage, String owner, String path,
                            String desc, Param... ps) {
        return new Tool(name, agent, stage, "T2", "PUT", owner, isService(owner), path, List.of(ps), desc);
    }

    private static boolean isService(String owner) {
        // datanet / workspace 已切流（route=service），其余 monolith 走 :8080 + 凭据中继
        return O_DATA.equals(owner) || O_WS.equals(owner);
    }

    // ═══════════════ 六域工具表 ═══════════════

    private static List<Tool> build() {
        List<Tool> tools = new ArrayList<>();
        tools.addAll(dataTools());
        tools.addAll(ontologyTools());
        tools.addAll(knowledgeTools());
        tools.addAll(aiTools());
        tools.addAll(scenarioTools());
        tools.addAll(securityTools());
        return List.copyOf(tools);
    }

    // ── 数据域（28） ──────────────────────────────────────────────

    private static List<Tool> dataTools() {
        return List.of(
            g("data_list_datasources", DATA, "接入/采集", O_DATA, "/api/v1/datanet/datasource", "列出已登记数据源"),
            post("data_test_datasource", DATA, "接入/采集", "T3", O_DATA, "/api/v1/datanet/datasource/test", "测试数据源连通性", id("datasourceId")),
            post("data_collect_metadata", DATA, "接入/采集", "T3", O_DATA, "/api/v1/datanet/metadata/collect/{datasourceId}", "触发元数据采集", id("datasourceId")),
            g("data_collect_status", DATA, "接入/采集", O_DATA, "/api/v1/datanet/collect-status/{taskId}", "查询采集任务状态", id("taskId")),
            g("data_schema_changes", DATA, "接入/采集", O_DATA, "/api/v1/engine/data/schema/changes", "查询 schema 变更记录"),
            g("data_list_assets", DATA, "编目/资产", O_DATA, "/api/v1/datanet/assets", "分页检索数据资产", opt("keyword"), optN("pageSize")),
            g("data_get_asset", DATA, "编目/资产", O_DATA, "/api/v1/datanet/assets/{assetId}", "查询单个资产详情", id("assetId")),
            g("data_list_fields", DATA, "编目/资产", O_DATA, "/api/v1/datanet/assets/{assetId}/fields", "查询资产字段结构", id("assetId")),
            g("data_search_catalog", DATA, "编目/资产", O_DATA, "/api/v1/datanet/catalog/search", "目录关键字检索", opt("keyword"), optN("page")),
            g("data_search_field", DATA, "编目/资产", O_DATA, "/api/v1/datanet/catalog/search/field", "字段级检索", opt("fieldName")),
            g("data_get_table_schema", DATA, "编目/资产", O_DATA, "/api/v1/datanet/metadata/fields/{resourceId}", "查询资源表结构", id("resourceId")),
            g("data_layer_rows", DATA, "编目/资产", O_DATA, "/api/v1/engine/data/layers/{layer}/resources/{resourceId}/rows", "查询分层资源行样例", id("layer"), id("resourceId"), optN("limit")),
            g("data_freshness", DATA, "编目/资产", O_DATA, "/api/v1/engine/data/freshness", "查询数据新鲜度"),
            g("data_quality_dashboard", DATA, "质量", O_DATA, "/api/v1/datanet/catalog/dashboard", "数据质量总览看板"),
            g("data_list_dq_rules", DATA, "质量", O_DATA, "/api/v1/engine/data/quality/rules", "列出质量规则"),
            post("data_create_dq_rule", DATA, "质量", "T2", O_DATA, "/api/v1/engine/data/quality/rules", "创建质量规则草稿"),
            put("data_update_dq_rule", DATA, "质量", O_DATA, "/api/v1/engine/data/quality/rules/{ruleId}", "更新质量规则", id("ruleId")),
            post("data_run_dq_check", DATA, "质量", "T3", O_DATA, "/api/v1/engine/data/quality/evaluate", "执行数据质量评估"),
            g("data_list_dq_evaluations", DATA, "质量", O_DATA, "/api/v1/engine/data/quality/evaluations", "查询质量评估记录"),
            g("data_list_pipelines", DATA, "管道", O_DATA, "/api/v1/pipeline/definitions", "列出管道定义"),
            g("data_get_pipeline", DATA, "管道", O_DATA, "/api/v1/pipeline/definitions/{pipelineId}", "查询管道定义详情", id("pipelineId")),
            post("data_create_pipeline", DATA, "管道", "T2", O_DATA, "/api/v1/pipeline/definitions", "创建管道定义草稿"),
            put("data_update_pipeline", DATA, "管道", O_DATA, "/api/v1/pipeline/definitions/{pipelineId}", "更新管道定义", id("pipelineId")),
            post("data_execute_pipeline", DATA, "管道", "T3", O_DATA, "/api/v1/pipeline/definitions/{pipelineId}/execute", "执行管道", id("pipelineId")),
            g("data_pipeline_executions", DATA, "管道", O_DATA, "/api/v1/pipeline/definitions/{pipelineId}/executions", "查询管道执行记录", id("pipelineId")),
            g("data_lineage_impact", DATA, "血缘", O_DATA, "/api/v1/engine/data/lineage/impact", "查询血缘影响面", opt("assetId")),
            g("data_lineage_graph", DATA, "血缘", O_DATA, "/api/v1/engine/data/lineage", "查询血缘图"),
            post("data_governance_suggest", DATA, "治理", "T2", O_DATA, "/api/v1/data/governance/profile", "生成治理建议（字段语义/映射/规则）"),
            post("data_governance_candidate", DATA, "治理", "T2", O_DATA, "/api/v1/data/governance/candidates", "创建治理候选草稿"),
            post("data_governance_submit", DATA, "治理", "T3", O_DATA, "/api/v1/data/governance/candidates/{candidateId}/submit-to-approval", "提交治理候选进入审批", id("candidateId"))
        );
    }

    // ── 本体域（28） ─────────────────────────────────────────────

    private static List<Tool> ontologyTools() {
        return List.of(
            g("ontology_list_domains", ONTOLOGY, "领域/对象", O_ONT, "/api/v1/ontology/domains", "列出本体领域"),
            post("ontology_create_domain", ONTOLOGY, "领域/对象", "T2", O_ONT, "/api/v1/ontology/domains", "创建本体领域草稿"),
            g("ontology_list_objects", ONTOLOGY, "领域/对象", O_ONT, "/api/v1/ecos/entities", "列出对象类型"),
            g("ontology_get_object", ONTOLOGY, "领域/对象", O_ONT, "/api/v1/ecos/entities/{entityId}", "查询对象类型详情", id("entityId")),
            post("ontology_create_object", ONTOLOGY, "领域/对象", "T2", O_ONT, "/api/v1/ecos/entities", "创建对象类型草稿"),
            put("ontology_update_object", ONTOLOGY, "领域/对象", O_ONT, "/api/v1/ecos/entities/{entityId}", "更新对象类型", id("entityId")),
            g("ontology_list_properties", ONTOLOGY, "领域/对象", O_ONT, "/api/v1/ecos/entities/{entityId}/properties", "列出对象属性", id("entityId")),
            post("ontology_create_property", ONTOLOGY, "领域/对象", "T2", O_ONT, "/api/v1/ecos/entities/{entityId}/properties", "创建属性草稿", id("entityId")),
            g("ontology_list_relations", ONTOLOGY, "关系/Action", O_ONT, "/api/v1/ecos/relationships", "列出对象关系"),
            g("ontology_relation_graph", ONTOLOGY, "关系/Action", O_ONT, "/api/v1/ecos/relationships/graph", "查询关系图快照", opt("rootId")),
            post("ontology_create_relation", ONTOLOGY, "关系/Action", "T2", O_ONT, "/api/v1/ecos/relationships", "创建关系草稿"),
            g("ontology_list_actions", ONTOLOGY, "关系/Action", O_ONT, "/api/v1/ecos/actions", "列出 Action 动作", opt("entityId")),
            post("ontology_create_action", ONTOLOGY, "关系/Action", "T2", O_ONT, "/api/v1/ecos/actions", "创建 Action 草稿"),
            g("ontology_list_action_types", ONTOLOGY, "关系/Action", O_ONT, "/api/v1/ontology/action-types", "列出 ActionType 目录"),
            post("ontology_create_action_type", ONTOLOGY, "关系/Action", "T2", O_ONT, "/api/v1/ontology/action-types", "创建 ActionType 草稿"),
            g("ontology_get_mappings", ONTOLOGY, "映射", O_ONT, "/api/v1/ontology/entity-mappings", "查询实体映射契约", opt("ontologyId")),
            g("ontology_list_proposals", ONTOLOGY, "提案/候选", O_ONT, "/api/v1/ontology/proposals", "列出本体提案", opt("status")),
            g("ontology_get_proposal", ONTOLOGY, "提案/候选", O_ONT, "/api/v1/ontology/proposals/{proposalId}", "查询提案详情", id("proposalId")),
            post("ontology_create_proposal", ONTOLOGY, "提案/候选", "T2", O_ONT, "/api/v1/ontology/proposals", "创建本体提案草稿"),
            post("ontology_submit_proposal", ONTOLOGY, "提案/候选", "T3", O_ONT, "/api/v1/ontology/proposals/{proposalId}/submit", "提交提案进入审批", id("proposalId")),
            g("ontology_list_candidates", ONTOLOGY, "提案/候选", O_ONT, "/api/v1/ontology/candidates", "列出本体候选", opt("status")),
            post("ontology_validate_candidate", ONTOLOGY, "提案/候选", "T3", O_ONT, "/api/v1/ontology/candidates/{candidateId}/validate", "校验本体候选", id("candidateId")),
            g("ontology_list_versions", ONTOLOGY, "版本/口径", O_ONT, "/api/v1/ontology/versions", "列出本体版本"),
            g("ontology_get_version", ONTOLOGY, "版本/口径", O_ONT, "/api/v1/ontology/versions/{versionId}", "查询单个版本", id("versionId")),
            post("ontology_diff_versions", ONTOLOGY, "版本/口径", "T1", O_ONT, "/api/v1/ontology/versions/diff", "对比两个版本差异", opt("v1"), opt("v2")),
            g("ontology_list_calibers", ONTOLOGY, "版本/口径", O_ONT, "/api/v1/ontology/calibers", "列出口径清单"),
            g("ontology_get_caliber", ONTOLOGY, "版本/口径", O_ONT, "/api/v1/ontology/calibers/{code}", "查询口径当前版本", id("code")),
            post("ontology_validate_caliber", ONTOLOGY, "版本/口径", "T3", O_ONT, "/api/v1/ontology/calibers/{code}/validate", "校验口径", id("code"))
        );
    }

    // ── 知识域（22） ─────────────────────────────────────────────

    private static List<Tool> knowledgeTools() {
        return List.of(
            post("knowledge_search", KNOWLEDGE, "检索/RAG", "T1", O_KB, "/api/v1/knowledge/retrieval/search", "知识库混合检索（带引用与降级位）", p("query", "string", true, "检索问题")),
            post("knowledge_answer", KNOWLEDGE, "检索/RAG", "T1", O_KB, "/api/v1/knowledge/retrieval/answer", "知识问答（流式摘要）", p("query", "string", true, "问题")),
            post("knowledge_rag", KNOWLEDGE, "检索/RAG", "T1", O_KB, "/api/v1/knowledge/rag/rag", "RAG TopK 片段检索", p("query", "string", true, "检索词"), optN("topK")),
            g("knowledge_index_status", KNOWLEDGE, "检索/RAG", O_KB, "/api/v1/knowledge/index-status", "查询向量索引状态"),
            g("knowledge_graph_get", KNOWLEDGE, "图谱", O_KB, "/api/v1/knowledge/graph", "查询图谱快照"),
            g("knowledge_node_get", KNOWLEDGE, "图谱", O_KB, "/api/v1/knowledge/nodes/{nodeId}", "查询单个图谱节点", id("nodeId")),
            post("knowledge_graph_search", KNOWLEDGE, "图谱", "T1", O_KB, "/api/v1/knowledge/graph/search", "图谱关键字搜索", p("keyword", "string", true, "关键词"), optN("limit")),
            g("knowledge_graph_path", KNOWLEDGE, "图谱", O_KB, "/api/v1/knowledge/path", "查询两节点最短路径", id("s"), id("t")),
            g("knowledge_graph_neighbors", KNOWLEDGE, "图谱", O_KB, "/api/v1/knowledge/neighbors/{nodeId}", "查询节点邻域", id("nodeId"), optN("d")),
            post("knowledge_create_node", KNOWLEDGE, "图谱", "T2", O_KB, "/api/v1/knowledge/nodes", "创建图谱节点草稿"),
            post("knowledge_create_edge", KNOWLEDGE, "图谱", "T2", O_KB, "/api/v1/knowledge/edges", "创建图谱边草稿"),
            g("knowledge_list_documents", KNOWLEDGE, "文档/抽取", O_KB, "/api/v1/knowledge/ingest/documents", "列出已登记文档"),
            g("knowledge_extract_files", KNOWLEDGE, "文档/抽取", O_KB, "/api/v1/knowledge/extract/files", "列出抽取文件"),
            g("knowledge_extract_candidates", KNOWLEDGE, "文档/抽取", O_KB, "/api/v1/knowledge/extract/candidates/{fileId}", "列出抽取候选", id("fileId")),
            post("knowledge_extract_dry_run", KNOWLEDGE, "文档/抽取", "T1", O_KB, "/api/v1/knowledge/extract/dry-run", "抽取试运行（不落库）"),
            g("knowledge_sync_status", KNOWLEDGE, "同步", O_KB, "/api/v1/knowledge/sync/status", "查询图谱同步状态"),
            g("knowledge_sync_jobs", KNOWLEDGE, "同步", O_KB, "/api/v1/knowledge/sync/jobs", "列出同步任务"),
            g("knowledge_list_rules", KNOWLEDGE, "规则/文章", O_KB, "/api/v1/knowledge/rules", "列出知识规则"),
            post("knowledge_create_rule", KNOWLEDGE, "规则/文章", "T2", O_KB, "/api/v1/knowledge/rules", "创建规则草稿"),
            g("knowledge_search_articles", KNOWLEDGE, "规则/文章", O_KB, "/api/v1/knowledge/articles/search", "知识文章检索", opt("keyword")),
            g("knowledge_list_assets", KNOWLEDGE, "资产/评估", O_KB, "/api/v1/knowledge/assets", "列出知识资产", opt("status")),
            post("knowledge_eval_run", KNOWLEDGE, "资产/评估", "T3", O_KB, "/api/v1/knowledge/eval/run", "运行检索质量评估")
        );
    }

    // ── AI 域（18） ──────────────────────────────────────────────

    private static List<Tool> aiTools() {
        return List.of(
            g("ai_list_agents", AI, "智能体", O_AI, "/api/v1/aip/agents", "列出已注册智能体"),
            g("ai_get_agent", AI, "智能体", O_AI, "/api/v1/aip/agents/{agentId}", "查询智能体详情", id("agentId")),
            g("ai_list_agent_templates", AI, "智能体", O_AI, "/api/v1/aip/agent-templates", "列出智能体模板"),
            post("ai_create_agent", AI, "智能体", "T2", O_AI, "/api/v1/aip/agents", "创建智能体草稿"),
            put("ai_update_agent", AI, "智能体", O_AI, "/api/v1/aip/agents/{agentId}", "更新智能体配置", id("agentId")),
            g("ai_list_tools", AI, "工具契约", O_AI, "/api/v1/agent/tools", "列出工具契约目录"),
            post("ai_validate_tool", AI, "工具契约", "T1", O_AI, "/api/v1/agent/tools/{name}/validate", "校验工具契约", id("name")),
            post("ai_dry_run_tool", AI, "工具契约", "T1", O_AI, "/api/v1/agent/tools/{name}/dry-run", "工具试运行（不执行）", id("name")),
            g("ai_list_guardrails", AI, "护栏/模型", O_AI, "/api/v1/aip/guardrails", "列出护栏策略"),
            post("ai_create_guardrail", AI, "护栏/模型", "T2", O_AI, "/api/v1/aip/guardrails", "创建护栏策略草稿"),
            g("ai_list_models", AI, "护栏/模型", O_AI, "/api/v1/aip/models", "列出模型目录"),
            g("ai_model_health", AI, "护栏/模型", O_AI, "/api/v1/aip/models/{modelId}/health", "查询模型健康", id("modelId")),
            g("ai_list_pipelines", AI, "管线/评估", O_AI, "/api/v1/aip/pipelines", "列出 AIP 管线"),
            post("ai_execute_pipeline", AI, "管线/评估", "T3", O_AI, "/api/v1/aip/studio/pipelines/{pipelineId}/execute", "执行 AIP 管线", id("pipelineId")),
            post("ai_run_eval", AI, "管线/评估", "T3", O_AI, "/api/v1/aip/eval/run", "运行智能体评估"),
            g("ai_eval_report", AI, "管线/评估", O_AI, "/api/v1/aip/eval/{evalId}/report", "查询评估报告", id("evalId")),
            g("ai_get_session", AI, "会话/目录", O_AI, "/api/v1/agent-loop/sessions/{sessionId}", "查询会话详情", id("sessionId")),
            g("ai_list_mesh_agents", AI, "会话/目录", O_AI, "/api/agent-mesh/agents", "列出 Mesh 智能体注册表")
        );
    }

    // ── 场景域（14） ─────────────────────────────────────────────

    private static List<Tool> scenarioTools() {
        return List.of(
            g("scenario_list", SCENARIO, "场景/绑定", O_WS, "/api/v1/workspace/scenarios", "列出业务场景"),
            g("scenario_get", SCENARIO, "场景/绑定", O_WS, "/api/v1/workspace/scenarios/{scenarioId}", "查询场景详情", id("scenarioId")),
            post("scenario_create", SCENARIO, "场景/绑定", "T2", O_WS, "/api/v1/workspace/scenarios", "创建场景草稿"),
            put("scenario_update", SCENARIO, "场景/绑定", O_WS, "/api/v1/workspace/scenarios/{scenarioId}", "更新场景", id("scenarioId")),
            g("scenario_get_bindings", SCENARIO, "场景/绑定", O_WS, "/api/v1/workspace/scenarios/{scenarioId}/bindings", "查询场景六类绑定", id("scenarioId")),
            g("scenario_binding_options", SCENARIO, "场景/绑定", O_WS, "/api/v1/workspace/scenarios/available/{kind}", "查询可用绑定源", p("kind", "string", true, "objects|datasets|knowledge|agents|security|interfaces")),
            post("scenario_create_binding_link", SCENARIO, "场景/绑定", "T2", O_WS, "/api/v1/workspace/scenarios/{scenarioId}/binding-links", "创建绑定草稿", id("scenarioId")),
            g("scenario_binding_catalog", SCENARIO, "场景/绑定", O_WS, "/api/v1/workspace/binding-catalog", "查询绑定目录"),
            g("scenario_list_actions", SCENARIO, "动作/预测", O_WS, "/api/v1/workspace/scenarios/{scenarioId}/actions", "列出场景动作", id("scenarioId")),
            post("scenario_create_action_draft", SCENARIO, "动作/预测", "T2", O_WS, "/api/v1/workspace/scenarios/{scenarioId}/actions", "创建场景动作草稿", id("scenarioId")),
            post("scenario_create_forecast_run", SCENARIO, "动作/预测", "T3", O_WS, "/api/v1/workspace/forecast-runs", "创建预测运行"),
            g("scenario_get_forecast_run", SCENARIO, "动作/预测", O_WS, "/api/v1/workspace/forecast-runs/{runId}", "查询预测运行状态", id("runId")),
            g("scenario_forecast_results", SCENARIO, "动作/预测", O_WS, "/api/v1/workspace/forecast-runs/{runId}/results", "查询预测结果", id("runId")),
            post("scenario_create_run", SCENARIO, "运行/认知", "T3", O_WS, "/api/v1/workspace/scenarios/{scenarioId}/runs", "创建场景运行（诊断/预测/仿真）", id("scenarioId"))
        );
    }

    // ── 安全域（14） ─────────────────────────────────────────────

    private static List<Tool> securityTools() {
        return List.of(
            post("security_evaluate", SECURITY, "裁决", "T1", O_SEC, "/api/v1/policy-engine/evaluate", "ABAC 策略评估（为当前主体求值）"),
            post("security_decide", SECURITY, "裁决", "T1", O_SEC, "/api/v1/security/decision", "资产访问裁决（带行列/列级策略）"),
            g("security_list_abac_policies", SECURITY, "策略", O_SEC, "/api/v1/security/abac/policies", "列出 ABAC 策略"),
            g("security_get_abac_policy", SECURITY, "策略", O_SEC, "/api/v1/security/abac/policies/{policyId}", "查询单条 ABAC 策略", id("policyId")),
            post("security_create_abac_policy", SECURITY, "策略", "T2", O_SEC, "/api/v1/security/abac/policies", "创建 ABAC 策略草稿"),
            put("security_update_abac_policy", SECURITY, "策略", O_SEC, "/api/v1/security/abac/policies/{policyId}", "更新 ABAC 策略", id("policyId")),
            g("security_list_rls_policies", SECURITY, "策略", O_SEC, "/api/v1/security/rls/policies", "列出行级策略"),
            g("security_list_cls_policies", SECURITY, "策略", O_SEC, "/api/v1/security/cls/policies", "列出列级策略"),
            g("security_audit_logs", SECURITY, "审计/分级", O_SEC, "/api/v1/security/audit/logs", "查询审计日志", opt("userId"), optN("page")),
            g("security_audit_stats", SECURITY, "审计/分级", O_SEC, "/api/v1/security/audit/stats", "查询审计统计"),
            g("security_list_classified_assets", SECURITY, "审计/分级", O_SEC, "/api/v1/security/assets", "列出分级资产"),
            put("security_update_asset_level", SECURITY, "审计/分级", O_SEC, "/api/v1/security/assets/{assetKey}", "更新资产分级", id("assetKey")),
            g("security_get_user_profile", SECURITY, "主体档案", O_SEC, "/api/v1/security/profile/user/{userId}", "查询用户安全档案", id("userId")),
            post("security_mask_apply", SECURITY, "脱敏", "T1", O_SEC, "/api/security/mask", "对给定值应用脱敏策略")
        );
    }

    // ═══════════════ 派生面 ═══════════════

    /** 各智能体工具白名单初值（= 该域全部 T1~T3 工具名）。 */
    public static Map<String, List<String>> whitelistsByAgent() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (String agent : new String[]{DATA, ONTOLOGY, KNOWLEDGE, AI, SCENARIO, SECURITY}) {
            map.put(agent, namesForAgent(agent));
        }
        return map;
    }

    /** 线形状（工作台工具面管理用；含 stage/tier/method/path 便于分组展示与风险提示）。 */
    public static Map<String, Object> toWire(Tool tool) {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("name", tool.name());
        wire.put("agent", tool.agent());
        wire.put("stage", tool.stage());
        wire.put("tier", tool.tier());
        wire.put("method", tool.method());
        wire.put("path", tool.path());
        wire.put("desc", tool.desc());
        List<Map<String, Object>> params = new ArrayList<>();
        for (Param param : tool.params()) {
            Map<String, Object> pw = new LinkedHashMap<>();
            pw.put("name", param.name());
            pw.put("type", param.type());
            pw.put("required", param.required());
            pw.put("desc", param.desc());
            params.add(pw);
        }
        wire.put("params", params);
        return wire;
    }
}
