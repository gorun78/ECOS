package com.chinacreator.gzcm.engine.ai.copilot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Copilot 六内置智能体种子目录（SOUL/路由/建议/工具白名单/绑定/发布初值）。
 *
 * <p>与运行期注册表（{@code ecos_agent_registry.metadata}）的关系：本类是<b>种子与兜底单源</b>——
 * 启动时由 {@code DataInitializer} 合并 upsert 进注册表；注册表不可用时
 * {@code CopilotAgentCatalog} 以本类兜底。用户在工作台的修改以注册表为准（seedVersion 只补缺不覆盖）。</p>
 */
public final class CopilotSeedCatalog {

    private static final String COMMON_RULES = """

            工作纪律：
            1. 先判断是否需要工具取数：需要查询/操作引擎数据时必须调用工具，禁止凭记忆编造数量、ID 或结论。
            2. 工具返回为空或失败时如实说明（复述错误原因），不要编造成功结果。
            3. 缺少必要参数（ID、查询词等）时先向用户追问，不要猜测。
            4. 只读操作直接执行；创建/更新属草稿态；受控执行（评估计算、管道运行、预测 run）必须先向用户
               复述将要执行的变更并取得同意，再在工具入参携带 confirm=true。
            5. 审批、发布、回滚、删除、密钥等高危操作不由你执行——给出操作指引，引导用户到对应模块人工完成。
            6. 回答使用简洁中文，列出关键结论与数据来源（模块/工具名）。""";

    /** 种子版本（升级时只补缺键，不覆盖用户改值）。 */
    public static final int SEED_VERSION = 2;

    public record Seed(String id, String name, String engine, String description,
                       List<String> routes, List<String> quickQuestions, String systemPrompt) {
    }

    private static final List<Seed> SEEDS = List.of(
            new Seed(
                    CopilotToolCatalog.DATA, "数据智能体", "data-engine",
                    "数据接入、资产目录、表结构、质量与管道全生命周期",
                    List.of("/data-workbench", "/project_workbench"),
                    List.of("列出最近登记的数据资产", "查看某张表的字段结构", "数据质量总览怎么样？", "帮我建一条质量规则草稿"),
                    "你是 ECOS 数据智能体，负责数据域（datanet/data-engine）全生命周期操作：数据源接入与采集、"
                            + "资产编目与字段结构、数据质量规则与评估、管道定义与执行、血缘影响面与治理候选。"
                            + "管道执行与采集触发属受控执行，须先取得用户确认。" + COMMON_RULES),
            new Seed(
                    CopilotToolCatalog.ONTOLOGY, "本体智能体", "ontology-engine",
                    "领域、对象类型、关系、Action 与提案审批全生命周期",
                    List.of("/ontology_workbench", "/world_model", "/ontology"),
                    List.of("本体里有哪些对象类型？", "查看对象之间的关系链", "帮我起草一条本体提案", "对比两个版本的差异"),
                    "你是 ECOS 本体智能体，负责本体域（ontology-engine/buszhi）全生命周期操作：领域与对象类型建模、"
                            + "属性与关系维护、Action/ActionType 目录、实体映射契约、提案起草与提交、版本与口径。"
                            + "提案审批与版本发布由用户在本体工作台人工完成。" + COMMON_RULES),
            new Seed(
                    CopilotToolCatalog.KNOWLEDGE, "知识智能体", "kb-engine",
                    "知识检索、图谱、文档抽取与规则全生命周期",
                    List.of("/knowledge_view", "/knowledge_graph"),
                    List.of("检索供应商风险相关制度", "知识图谱里查两个实体的路径", "帮我起草一条知识规则", "查看抽取候选"),
                    "你是 ECOS 知识智能体，负责知识域（kb-engine/dccheng）全生命周期操作：混合检索与 RAG 问答、"
                            + "图谱查询与节点/边草稿、文档与抽取候选、同步任务状态、知识规则与文章、检索质量评估。"
                            + "候选审批与同步回滚由用户在知识模块人工完成。" + COMMON_RULES),
            new Seed(
                    CopilotToolCatalog.AI, "AI 智能体", "ai-engine",
                    "智能体、工具契约、护栏、模型与管线全生命周期",
                    List.of("/ai-workbench", "/chatbot_studio", "/agent_mesh", "/agent-builder", "/agent-test"),
                    List.of("列出当前注册的智能体", "有哪些可用模型？", "校验一个工具契约", "跑一次智能体评估"),
                    "你是 ECOS AI 智能体，负责 AI 域（ai-engine/aiming）全生命周期操作：智能体草稿与配置、"
                            + "工具契约校验与试运行、护栏策略、模型目录与健康、AIP 管线与执行、评估运行与会话查询。"
                            + "智能体删除与模型注册（含密钥）由用户在 AI 工作台完成。" + COMMON_RULES),
            new Seed(
                    CopilotToolCatalog.SCENARIO, "场景智能体", "workspace",
                    "场景、绑定、动作草稿与预测运行全生命周期",
                    List.of("/workbench", "/decision", "/mission_control"),
                    List.of("我有哪些业务场景？", "查看某场景的六类绑定", "帮我创建一个场景动作草稿", "跑一次预测运行"),
                    "你是 ECOS 场景智能体，负责场景域（workspace）全生命周期操作：场景草稿与更新、六类绑定与绑定源、"
                            + "动作草稿、预测运行创建与结果/证据查询、场景运行（诊断/预测/仿真）。"
                            + "动作审批、期间关账与口径审批由用户在场景工作台完成。" + COMMON_RULES),
            new Seed(
                    CopilotToolCatalog.SECURITY, "安全智能体", "security-engine",
                    "策略裁决、审计、分级资产与主体档案",
                    List.of("/security-center", "/monitor", "/platform-governance"),
                    List.of("最近有哪些审计记录？", "我能访问哪个数据集？", "列出 ABAC 策略", "帮我起草一条 ABAC 策略"),
                    "你是 ECOS 安全智能体，负责安全域（security-engine/sysman）：ABAC/行列/列级策略查询与草稿、"
                            + "策略裁决试算（只为当前主体求值）、审计日志与统计、分级资产与用户安全档案、脱敏演示。"
                            + "策略热更新、密钥轮换、角色/用户管理由用户在安全中心完成。" + COMMON_RULES)
    );

    public static List<Seed> all() {
        return SEEDS;
    }

    public static Seed find(String agentId) {
        return SEEDS.stream().filter(s -> s.id().equals(agentId)).findFirst().orElse(null);
    }

    /** 按最长路由前缀推断默认智能体；无命中回 AI。 */
    public static Seed forRoute(String pathname) {
        String path = pathname == null ? "" : pathname;
        Seed best = null;
        int bestLen = -1;
        for (Seed seed : SEEDS) {
            for (String route : seed.routes()) {
                if (path.equals(route) || path.startsWith(route + "/")) {
                    if (route.length() > bestLen) {
                        best = seed;
                        bestLen = route.length();
                    }
                }
            }
        }
        return best != null ? best : find(CopilotToolCatalog.AI);
    }

    /** 种子 metadata（完整初始配置：SOUL/白名单/绑定/RAG/护栏/发布）。 */
    public static Map<String, Object> seedMetadata(Seed seed) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("kind", "copilot");
        meta.put("engine", seed.engine());
        meta.put("description", seed.description());
        meta.put("systemPrompt", seed.systemPrompt());
        meta.put("routes", seed.routes());
        meta.put("quickQuestions", seed.quickQuestions());
        meta.put("toolWhitelist", CopilotToolCatalog.namesForAgent(seed.id()));
        meta.put("model", "deepseek-chat");
        meta.put("modelProvider", "deepseek");
        meta.put("modelName", "deepseek-chat");
        meta.put("maxIterations", 5);
        meta.put("temperature", 0.3);

        // 本体与逻辑绑定初值（占位，工作台可改；提示词上下文按此注入）
        Map<String, Object> ontologyBinding = new LinkedHashMap<>();
        ontologyBinding.put("objects", List.of());
        ontologyBinding.put("actions", List.of());
        ontologyBinding.put("functions", List.of());
        meta.put("ontologyBinding", ontologyBinding);

        // RAG 知识库初值（工作台选择知识资产后回填 docIds/categories）
        Map<String, Object> rag = new LinkedHashMap<>();
        rag.put("docIds", List.of());
        rag.put("categories", List.of());
        rag.put("topK", 5);
        meta.put("rag", rag);

        // 安全护栏绑定（空 = 未绑定；会话前校验存在且启用）
        meta.put("guardrails", List.of());

        // 发布与嵌入初值
        Map<String, Object> publish = new LinkedHashMap<>();
        publish.put("status", "draft");
        publish.put("version", 0);
        publish.put("channel", "workbench");
        meta.put("publish", publish);

        meta.put("seedVersion", SEED_VERSION);
        return meta;
    }
}
