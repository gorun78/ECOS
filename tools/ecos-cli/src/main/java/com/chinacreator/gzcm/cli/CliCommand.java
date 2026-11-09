package com.chinacreator.gzcm.cli;

import java.util.List;

/**
 * CLI 命令元数据（生成 contract/ops.json 的单源）。
 *
 * <p>硬约束：命令只持有 operationId，<b>不持有 URL/path</b>——路径一律由契约清单解析，
 * 保证「每条命令映射到一个 OpenAPI operation，禁手写 URL」（ADR-21 §2.2）。</p>
 */
public record CliCommand(
        String name,
        String description,
        String accessMode,
        List<String> operationIds,
        List<Flag> flags) {

    /** 命令参数 → 契约 query 参数名的映射（参数名必须与契约逐字一致）。 */
    public record Flag(String flag, String operationId, String parameter, String description) {
    }
}
