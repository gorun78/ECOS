package com.chinacreator.gzcm.cli;

import java.util.List;

/** 已批准的首批只读命令（R-71 ①）。新增命令必须先登记 operationId 再写实现。 */
public final class CliCommands {

    public static final String ACCESS_READ = "READ";
    public static final String ACCESS_WRITE = "WRITE";

    public static final String DOCTOR = "doctor";
    public static final String COGNITION_MODELS_LIST = "cognition models list";
    public static final String CAPABILITIES_LIST = "capabilities list";

    public static final List<CliCommand> ALL = List.of(
            new CliCommand(
                    DOCTOR,
                    "环境与连通性自检（网关可达 / 认知读面 / 监控 / 凭据有效性）；只读",
                    ACCESS_READ,
                    List.of("getGatewayHealth", "listCognitiveModels", "getMonitorHealth", "getCurrentUserProfile"),
                    List.of()),
            new CliCommand(
                    COGNITION_MODELS_LIST,
                    "认知模型注册表只读列表；参数名取自契约 listCognitiveModels",
                    ACCESS_READ,
                    List.of("listCognitiveModels"),
                    List.of(
                            new CliCommand.Flag("--model-type", "listCognitiveModels", "modelType", "模型类型过滤"),
                            new CliCommand.Flag("--registry-state", "listCognitiveModels", "registryState",
                                    "注册表状态过滤（如 PUBLISHED）"),
                            new CliCommand.Flag("--model-kind", "listCognitiveModels", "cognitiveModelType",
                                    "认知模型类型（CM-01~CM-xx）"))),
            new CliCommand(
                    CAPABILITIES_LIST,
                    "命令发现与自描述（含每个命令的操作映射与实现状态）；只读",
                    ACCESS_READ,
                    List.of(),
                    List.of()));

    private CliCommands() {
    }
}
