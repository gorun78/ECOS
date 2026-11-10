package com.chinacreator.gzcm.cli;

import java.util.List;

/** 分级帮助渲染（{@code ecos --help} 概览 / {@code ecos <cmd> --help} 详情）。 */
public final class HelpRenderer {

    private HelpRenderer() {
    }

    /** 顶层概览：命令清单 + 统一参数 + 退出码。 */
    public static String overview() {
        StringBuilder sb = new StringBuilder();
        sb.append("ecos — ECOS 统一 CLI（只经 gateway；命令 → 契约 operationId）").append(nl()).append(nl());
        sb.append("用法: ecos <命令> [参数]").append(nl()).append(nl());
        sb.append("命令:").append(nl());
        for (CliCommand c : CliCommands.all()) {
            sb.append("  ").append(pad(c.name(), 30)).append(c.description()).append(nl());
        }
        sb.append(nl()).append("统一参数:").append(nl())
                .append("  --output json|table    输出格式（默认 json，面向智能体）").append(nl())
                .append("  --base-url URL         网关地址（默认 http://localhost:8080；禁指向 service 端口）").append(nl())
                .append("  --token / --token-file / --profile   凭据来源（凭据永不回显）").append(nl())
                .append("  --trace-id ID          全链 trace 透传").append(nl())
                .append("  --timeout MS           超时（1~120000，默认 30000）").append(nl())
                .append("  --dry-run              只展示调用计划，不发请求").append(nl())
                .append("  --no-retry             关闭自动重试（默认 GET 最多 3 次）").append(nl())
                .append("  --all / --page-size N / --max-pages N   列表分页（单页上限 500）").append(nl())
                .append("  --idempotency-key K    写命令幂等键（缺省自动生成并回显）").append(nl())
                .append("  --follow               流式跟随（SSE，遇 streamRunEvents 生效）").append(nl())
                .append("  --show-config          打印生效配置（token 只显示是否已配置）").append(nl())
                .append(nl()).append("退出码: 0 SUCCESS / 1 FAILED / 2 BLOCKED / 3 UNAVAILABLE / 4 PARTIAL / 64 用法错误");
        return sb.toString();
    }

    /** 命令详情：命令词、所需位置参数、flag 与对应契约参数。 */
    public static String detail(CliCommand command, OperationRegistry registry) {
        StringBuilder sb = new StringBuilder();
        sb.append("ecos ").append(command.name()).append(nl()).append(nl())
                .append("  ").append(command.description()).append(nl()).append(nl());
        sb.append("访问级别: ").append(command.accessMode()).append(nl());
        sb.append("契约 operationId: ").append(String.join(", ", command.operationIds())).append(nl());
        List<OperationRegistry.Flag> flags = registry == null
                ? List.of() : List.copyOf(registry.flagsOf(command.name()).values());
        if (!flags.isEmpty()) {
            sb.append(nl()).append("命令参数:").append(nl());
            for (OperationRegistry.Flag f : flags) {
                sb.append("  ").append(pad(f.flag(), 22))
                        .append("→ 契约参数 ").append(f.parameter()).append(nl());
            }
        }
        sb.append(nl()).append("通用参数见 ecos --help");
        return sb.toString();
    }

    private static String pad(String s, int width) {
        return s.length() >= width ? s + " " : s + " ".repeat(width - s.length());
    }

    private static String nl() {
        return System.lineSeparator();
    }
}
