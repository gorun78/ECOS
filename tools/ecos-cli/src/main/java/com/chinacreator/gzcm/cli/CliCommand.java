package com.chinacreator.gzcm.cli;

import java.util.List;

/**
 * CLI 命令元数据（生成 contract/ops.json 的单源）。
 *
 * <p>硬约束：命令只持有 operationId，<b>不持有 URL/path</b>——路径一律由契约清单解析，
 * 保证「每条命令映射到一个 OpenAPI operation，禁手写 URL」（ADR-21 §2.2）。</p>
 *
 * <p>{@code wired=false} 表示命令已在命令树登记、但执行面尚未接入（如 L3 写命令等审批凭证
 * 服务端就绪）。此类命令一律输出 {@code UNAVAILABLE} + source_refs，<b>禁</b>伪造成功。</p>
 */
public record CliCommand(
        String name,
        String description,
        String accessMode,
        String automationLevel,
        List<String> operationIds,
        List<Flag> flags,
        List<PathParam> pathParams,
        List<BodyParam> bodyParams,
        boolean requiresApproval,
        boolean wired) {

    /** 命令参数 → 契约 query 参数名的映射（参数名必须与契约逐字一致）。 */
    public record Flag(String flag, String operationId, String parameter, String description) {
    }

    /** 命令参数 → 契约路径参数名的映射（flag 值填充 path 模板占位）。 */
    public record PathParam(String flag, String operationId, String parameter, String description) {
    }

    /**
     * 命令参数 → 请求体字段名（仅校验契约该 operation 声明了 requestBody；
     * 具体属性名由属主契约给出，CLI 不猜结构）。
     */
    public record BodyParam(String flag, String operationId, String parameter, String description) {
    }

    /** 便捷构造：只读、无路径/体参数、已接入。 */
    public static CliCommand read(String name, String description, String operationId,
                                  List<Flag> flags) {
        return new CliCommand(name, description, CliCommands.ACCESS_READ, "L0",
                List.of(operationId), flags, List.of(), List.of(), false, true);
    }
}
