package com.chinacreator.gzcm.cli;

import java.util.List;

/**
 * 命令树单源（详设-10 §10.3.5 的 14 条命令）。
 *
 * <p><b>状态三分</b>：{@code wired=true} 才允许真实调用；{@code wired=false} 的命令仍出现在
 * {@code capabilities list}（命令面可见），但执行面一律输出 {@code UNAVAILABLE} + source_refs，
 * <b>禁</b>返回空集合或伪成功。写命令（{@code requiresApproval=true}）在服务端审批凭证
 * 校验就绪前不接入。</p>
 */
public final class CliCommands {

    public static final String ACCESS_READ = "READ";
    public static final String ACCESS_WRITE = "WRITE";

    // ── 既有 3 条（MVP）────────────────────────────────────────────
    public static final String DOCTOR = "doctor";
    public static final String COGNITION_MODELS_LIST = "cognition models list";
    public static final String CAPABILITIES_LIST = "capabilities list";

    // ── 新增 11 条（详设-10 §10.3.5）───────────────────────────────
    public static final String AGENT_RUN = "agent run";
    public static final String AGENT_INSPECT = "agent inspect";
    public static final String AGENT_STOP = "agent stop";
    public static final String AGENT_READINESS = "agent readiness";
    public static final String ONTOLOGY_VALIDATE = "ontology validate";
    public static final String ONTOLOGY_PUBLISH = "ontology publish";
    public static final String KNOWLEDGE_EXTRACT = "knowledge extract";
    public static final String KNOWLEDGE_VALIDATE = "knowledge validate";
    public static final String KNOWLEDGE_PUBLISH = "knowledge publish";
    public static final String COGNITION_MODEL_VALIDATE = "cognition model validate";
    public static final String COGNITION_MODEL_RUN = "cognition model run";
    public static final String ACTION_COMMIT = "action commit";
    public static final String MODEL_LIST = "model list";
    public static final String MODEL_ROUTE = "model route";

    public static final List<CliCommand> ALL = List.of(
            // ① 既有只读命令
            new CliCommand(
                    DOCTOR,
                    "环境与连通性自检（网关可达 / 认知读面 / 监控 / 凭据有效性）；"
                            + "加 --preflight 走服务端 11 步预检（只报告，不建 Run）；只读",
                    ACCESS_READ, "L0",
                    List.of("getGatewayHealth", "listCognitiveModels", "getMonitorHealth",
                            "getCurrentUserProfile", "runPreflight"),
                    List.of(), List.of(), List.of(), false, true),
            new CliCommand(
                    COGNITION_MODELS_LIST,
                    "认知模型注册表只读列表；参数名取自契约 listCognitiveModels",
                    ACCESS_READ, "L0",
                    List.of("listCognitiveModels"),
                    List.of(
                            new CliCommand.Flag("--model-type", "listCognitiveModels", "modelType",
                                    "模型类型过滤"),
                            new CliCommand.Flag("--registry-state", "listCognitiveModels", "registryState",
                                    "注册表状态过滤（如 PUBLISHED）"),
                            new CliCommand.Flag("--model-kind", "listCognitiveModels", "cognitiveModelType",
                                    "认知模型类型（CM-01~CM-xx）")),
                    List.of(), List.of(), false, true),
            new CliCommand(
                    CAPABILITIES_LIST,
                    "命令发现与自描述（含每个命令的操作映射与实现状态）；只读",
                    ACCESS_READ, "L0",
                    List.of(), List.of(), List.of(), List.of(), false, true),

            // ② Agent 面（Run 生命周期）
            new CliCommand(
                    AGENT_RUN,
                    "启动 Run（--question-id 必填；L1 生成候选，不自动执行副作用）",
                    ACCESS_WRITE, "L1",
                    List.of("startRun"),
                    List.of(),
                    List.of(),
                    List.of(
                            new CliCommand.BodyParam("--question-id", "startRun", "questionId",
                                    "问题 ID（RunStartRequest.questionId，必填）"),
                            new CliCommand.BodyParam("--automation-ceiling", "startRun",
                                    "automationCeiling", "自动化上界（L0~L3）"),
                            new CliCommand.BodyParam("--parent-run-id", "startRun", "parentRunId",
                                    "父 Run（重跑/续跑）"),
                            new CliCommand.BodyParam("--previous-run-id", "startRun", "previousRunId",
                                    "前序 Run")),
                    false, true),
            new CliCommand(
                    AGENT_INSPECT,
                    "Run 详情（状态 + 时间线 + Step 引用与哈希）；只读",
                    ACCESS_READ, "L0",
                    List.of("getRun"),
                    List.of(),
                    List.of(new CliCommand.PathParam("--run-id", "getRun", "runId", "Run ID")),
                    List.of(), false, true),
            new CliCommand(
                    AGENT_STOP,
                    "取消 Run（幂等：终态再取消不报错）",
                    ACCESS_WRITE, "L1",
                    List.of("cancelRun"),
                    List.of(),
                    List.of(new CliCommand.PathParam("--run-id", "cancelRun", "runId", "Run ID")),
                    List.of(), false, true),
            new CliCommand(
                    AGENT_READINESS,
                    "就绪度（等级 + 缺项 + 缺口）；只读",
                    ACCESS_READ, "L0",
                    List.of("getReadiness"),
                    List.of(),
                    List.of(new CliCommand.PathParam("--question-id", "getReadiness", "questionId",
                            "问题 ID")),
                    List.of(), false, true),

            // ③ Ontology 面
            new CliCommand(
                    ONTOLOGY_VALIDATE,
                    "本体候选校验（命名/端点/冲突/兼容四段，回写 validation 并 CAS 推进）",
                    ACCESS_WRITE, "L1",
                    List.of("validateOntologyCandidate"),
                    List.of(),
                    List.of(new CliCommand.PathParam("--candidate-id", "validateOntologyCandidate",
                            "candidateId", "候选 ID")),
                    List.of(), false, true),
            new CliCommand(
                    ONTOLOGY_PUBLISH,
                    "本体候选发布（L3：凭证消费 + 安全裁决 + Git 归档 + 落库）",
                    ACCESS_WRITE, "L3",
                    List.of("publishCandidate"),
                    List.of(),
                    List.of(new CliCommand.PathParam("--candidate-id", "publishCandidate", "candidateId",
                            "候选 ID")),
                    List.of(new CliCommand.BodyParam("--approval-token", "publishCandidate",
                            "approval_token", "服务端审批凭证（必填）")),
                    true, true),

            // ④ Knowledge 面
            new CliCommand(
                    KNOWLEDGE_EXTRACT,
                    "知识抽取（产出候选，不直写知识库；初始状态 generated）",
                    ACCESS_WRITE, "L2",
                    List.of("createWagentCandidate"),
                    List.of(),
                    List.of(),
                    List.of(
                            new CliCommand.BodyParam("--candidate-type", "createWagentCandidate",
                                    "candidateType", "CandidateType 十二类之一（必填）"),
                            new CliCommand.BodyParam("--payload-json", "createWagentCandidate",
                                    "payloadJson", "候选载荷 JSON 串（必填）"),
                            new CliCommand.BodyParam("--target-engine", "createWagentCandidate",
                                    "targetEngine", "属主引擎"),
                            new CliCommand.BodyParam("--basis-json", "createWagentCandidate",
                                    "basisJson", "生成依据（证据/口径引用）"),
                            new CliCommand.BodyParam("--target-object-ref", "createWagentCandidate",
                                    "targetObjectRefJson",
                                    "目标对象引用 JSON 串（validate conflict 段的比对键；键序由服务端规范化）"),
                            new CliCommand.BodyParam("--run-id", "createWagentCandidate",
                                    "generatedByRun", "生成该候选的 Run")),
                    false, true),
            new CliCommand(
                    KNOWLEDGE_VALIDATE,
                    "知识候选五段自检（structure/reference/conflict/impact/regression；任一段 fail 停 validating）",
                    ACCESS_WRITE, "L1",
                    List.of("validateWagentCandidate"),
                    List.of(),
                    List.of(new CliCommand.PathParam("--candidate-id", "validateWagentCandidate",
                            "candidateId", "候选 ID")),
                    List.of(), false, true),
            new CliCommand(
                    KNOWLEDGE_PUBLISH,
                    "知识候选发布（L3：凭证消费 + 安全裁决 + Git 归档 + 落库）",
                    ACCESS_WRITE, "L3",
                    List.of("publishCandidate"),
                    List.of(),
                    List.of(new CliCommand.PathParam("--candidate-id", "publishCandidate", "candidateId",
                            "候选 ID")),
                    List.of(new CliCommand.BodyParam("--approval-token", "publishCandidate",
                            "approval_token", "服务端审批凭证（必填）")),
                    true, true),

            // ⑤ Cognition 面
            new CliCommand(
                    COGNITION_MODEL_VALIDATE,
                    "认知模型校验（四类发布门禁真实裁决；属主 cognitive-engine）",
                    ACCESS_WRITE, "L1",
                    List.of("validateCognitiveModel"),
                    List.of(),
                    List.of(new CliCommand.PathParam("--model-id", "validateCognitiveModel", "modelId",
                            "模型 ID")),
                    List.of(), false, true),
            new CliCommand(
                    COGNITION_MODEL_RUN,
                    "认知模型确定性运行（L1；产物走 data 通道）。执行权按设计 §8.4.2 属外部计算/预测服务，"
                            + "cognitive-engine 仅登记资产且运行结果不入注册表；startCognitiveModelRun "
                            + "status=planned 且无输入体 ⇒ UNAVAILABLE",
                    ACCESS_WRITE, "L1",
                    List.of("startCognitiveModelRun"),
                    List.of(),
                    List.of(new CliCommand.PathParam("--model-id", "startCognitiveModelRun", "modelId",
                            "模型 ID")),
                    List.of(), false, false),

            // ⑥ Action 面
            new CliCommand(
                    ACTION_COMMIT,
                    "行动提交（L3：凭证消费 + 安全裁决 + 属主 Engine 派发 + 落库）",
                    ACCESS_WRITE, "L3",
                    List.of("commitAction"),
                    List.of(),
                    List.of(new CliCommand.PathParam("--action-id", "commitAction", "actionId",
                            "行动 ID")),
                    List.of(new CliCommand.BodyParam("--approval-token", "commitAction",
                            "approval_token", "服务端审批凭证（必填）")),
                    true, true),

            // ⑦ Model 面
            new CliCommand(
                    MODEL_LIST,
                    "LLM 模型目录只读视图；读数不可得时服务端 501，CLI 如实透传",
                    ACCESS_READ, "L0",
                    List.of("listLlmModels"),
                    List.of(
                            new CliCommand.Flag("--capability", "listLlmModels", "capability",
                                    "能力过滤（本面无载体，给定即 400）"),
                            new CliCommand.Flag("--data-classification", "listLlmModels",
                                    "dataClassification", "数据密级过滤（本面无载体，给定即 400）")),
                    List.of(), List.of(), false, true),
            new CliCommand(
                    MODEL_ROUTE,
                    "四判据路由与回退链解析（L0；路由端点未实装 ⇒ UNAVAILABLE，禁伪造路由）",
                    ACCESS_READ, "L0",
                    List.of("resolveModelRoute"),
                    List.of(), List.of(), List.of(), false, false));

    public static List<CliCommand> all() {
        return ALL;
    }

    /** 按命令全名查元数据；未知返回 null。 */
    public static CliCommand find(String name) {
        for (CliCommand c : ALL) {
            if (c.name().equals(name)) {
                return c;
            }
        }
        return null;
    }

    /** 命令词序列（多词命令按空格切分，供参数重排匹配）。 */
    public static List<List<String>> sequences() {
        List<List<String>> out = new java.util.ArrayList<>(
                ALL.stream().map(c -> List.of(c.name().split(" "))).toList());
        for (String alias : ALIASES.keySet()) {
            out.add(List.of(alias.split(" ")));
        }
        return out;
    }

    /**
     * 设计册别名 → 规范命令名（详设-10 §10.3.5 用 {@code runtime doctor} / {@code cognition run}；
     * 已发布命令名保持不变，别名只做入口兼容，不新增命令）。
     */
    private static final java.util.Map<String, String> ALIASES = java.util.Map.of(
            "runtime doctor", DOCTOR,
            "cognition run", COGNITION_MODEL_RUN);

    /** 别名归一：命中别名返回规范命令名，否则原样返回。 */
    public static String canonical(String name) {
        return ALIASES.getOrDefault(name, name);
    }

    /** 别名表（只读视图；供概览与补全展示，保证可执行的口径在帮助里可见）。 */
    public static java.util.Map<String, String> aliases() {
        return ALIASES;
    }

    private CliCommands() {
    }
}
