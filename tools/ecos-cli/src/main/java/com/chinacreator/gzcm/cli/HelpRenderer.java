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
        sb.append("命令（L = 自动化等级；⏳ = 属主端点/审批凭证未就绪，执行返回 UNAVAILABLE）:").append(nl());
        for (CliCommand c : CliCommands.all()) {
            sb.append("  ").append(pad(c.name(), 28))
                    .append(pad(c.automationLevel(), 4))
                    .append(c.wired() ? "  " : "⏳")
                    .append(c.description()).append(nl());
        }
        if (!CliCommands.aliases().isEmpty()) {
            sb.append(nl()).append("别名（等价入口，命令名以左列为准）:").append(nl());
            CliCommands.aliases().forEach((alias, canonical) ->
                    sb.append("  ").append(pad(alias, 28)).append("= ").append(canonical).append(nl()));
        }
        sb.append(nl()).append("统一参数:").append(nl())
                .append("  --output json|table    输出格式（默认 json，面向智能体）").append(nl())
                .append("  --base-url URL         网关地址（默认 http://localhost:8080；禁指向 service 端口）").append(nl())
                .append("  --token / --token-file / --profile   凭据来源（凭据永不回显）").append(nl())
                .append("  --trace-id ID          全链 trace 透传").append(nl())
                .append("  --timeout MS           超时（1~120000，默认 30000）").append(nl())
                .append("  --dry-run              只展示调用计划，不发请求").append(nl())
                .append("  --no-retry             关闭自动重试（默认 GET 最多 3 次）").append(nl())
                .append("  --all / --page-size N / --max-pages N   列表全量翻页（单页上限 500）").append(nl())
                .append("  --idempotency-key K    写命令幂等键（缺省自动生成并回显）").append(nl())
                .append("  --approval-token T     L3 命令审批凭证（服务端校验；缺省即 BLOCKED）").append(nl())
                .append("  --body-json JSON       写命令请求体（字段形态由属主契约定义）").append(nl())
                .append("  --follow               流式跟随（SSE）：agent inspect --follow 走 streamRunEvents").append(nl())
                .append("  --last-event-id ID     SSE 续传游标（配合 --follow；服务端未就绪时如实 UNAVAILABLE）").append(nl())
                .append("  --show-config          打印生效配置（token 只显示是否已配置）").append(nl())
                .append("  --preflight            doctor 改走服务端 11 步预检（只报告，不建 Run）").append(nl())
                .append("  --completion bash|ps1  由命令树生成补全脚本（输出到 stdout）").append(nl())
                .append(nl()).append("退出码: 0 SUCCESS / 1 FAILED / 2 BLOCKED / 3 UNAVAILABLE / 4 PARTIAL / 64 用法错误");
        return sb.toString();
    }

    /** 命令详情：命令词、路径参数、flag 与对应契约参数。 */
    public static String detail(CliCommand command, OperationRegistry registry) {
        StringBuilder sb = new StringBuilder();
        sb.append("ecos ").append(command.name()).append(nl()).append(nl())
                .append("  ").append(command.description()).append(nl()).append(nl());
        sb.append("访问级别: ").append(command.accessMode())
                .append("    自动化等级: ").append(command.automationLevel())
                .append(command.wired() ? "" : "    ⏳ 未接入（执行返回 UNAVAILABLE）").append(nl());
        if (command.requiresApproval()) {
            sb.append("审批: 必须 --approval-token（服务端校验 + 一次性消费 + 四眼）").append(nl());
        }
        sb.append("契约 operationId: ").append(String.join(", ", command.operationIds())).append(nl());
        if (registry != null) {
            List<OperationRegistry.PathParam> pathParams =
                    List.copyOf(registry.pathParamsOf(command.name()).values());
            if (!pathParams.isEmpty()) {
                sb.append(nl()).append("路径参数:").append(nl());
                for (OperationRegistry.PathParam p : pathParams) {
                    sb.append("  ").append(pad(p.flag(), 22))
                            .append("→ 契约路径参数 ").append(p.parameter())
                            .append("（").append(p.description()).append("）").append(nl());
                }
            }
            List<OperationRegistry.Flag> flags =
                    List.copyOf(registry.flagsOf(command.name()).values());
            if (!flags.isEmpty()) {
                sb.append(nl()).append("命令参数:").append(nl());
                for (OperationRegistry.Flag f : flags) {
                    sb.append("  ").append(pad(f.flag(), 22))
                            .append("→ 契约参数 ").append(f.parameter()).append(nl());
                }
            }
        }
        sb.append(nl()).append("通用参数见 ecos --help");
        return sb.toString();
    }

    /** 补全脚本的候选参数集（与 {@link #overview()} 的「统一参数」同源）。 */
    static final List<String> GLOBAL_FLAGS = List.of(
            "--output", "--base-url", "--token", "--token-file", "--profile", "--trace-id",
            "--timeout", "--dry-run", "--no-retry", "--all", "--page-size", "--max-pages",
            "--idempotency-key", "--approval-token", "--body-json", "--follow", "--last-event-id",
            "--show-config", "--preflight", "--help", "--version", "--completion");

    /**
     * 由命令树<b>生成</b>补全脚本（禁手写，避免命令增删后补全过期）。
     *
     * <p>行尾固定 {@code \n}：同一份产物要能跨平台比对，不随构建机变化。</p>
     */
    public static String completion(String shell) {
        String s = shell == null ? "" : shell.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (s) {
            case "bash" -> bashCompletion();
            case "ps1", "powershell" -> powershellCompletion();
            default -> throw new CliException(ExitCodes.USAGE,
                    "未知的 shell: " + shell + "（支持 bash | ps1）");
        };
    }

    private static List<String> topWords() {
        List<String> words = new java.util.ArrayList<>();
        for (CliCommand c : CliCommands.all()) {
            String top = c.name().split(" ")[0];
            if (!words.contains(top)) {
                words.add(top);
            }
        }
        for (String alias : CliCommands.aliases().keySet()) {
            String top = alias.split(" ")[0];
            if (!words.contains(top)) {
                words.add(top);
            }
        }
        words.add("help");
        words.add("version");
        return words;
    }

    /** 一级词 → 该组命令的二级词（保持命令树声明顺序，去重）。 */
    private static java.util.Map<String, List<String>> subWords() {
        java.util.Map<String, List<String>> groups = new java.util.LinkedHashMap<>();
        for (CliCommand c : CliCommands.all()) {
            String[] parts = c.name().split(" ");
            if (parts.length < 2) {
                continue;
            }
            List<String> subs = groups.computeIfAbsent(parts[0], k -> new java.util.ArrayList<>());
            for (int i = 1; i < parts.length; i++) {
                if (!subs.contains(parts[i])) {
                    subs.add(parts[i]);
                }
            }
        }
        for (String alias : CliCommands.aliases().keySet()) {
            String[] parts = alias.split(" ");
            if (parts.length < 2) {
                continue;
            }
            List<String> subs = groups.computeIfAbsent(parts[0], k -> new java.util.ArrayList<>());
            for (int i = 1; i < parts.length; i++) {
                if (!subs.contains(parts[i])) {
                    subs.add(parts[i]);
                }
            }
        }
        return groups;
    }

    private static String bashCompletion() {
        String flags = String.join(" ", GLOBAL_FLAGS);
        StringBuilder sb = new StringBuilder();
        sb.append("# ecos CLI — bash 补全（由 `ecos --completion bash` 生成，请勿手改）\n");
        sb.append("_ecos_completion() {\n");
        sb.append("    local cur prev\n");
        sb.append("    COMPREPLY=()\n");
        sb.append("    cur=\"${COMP_WORDS[COMP_CWORD]}\"\n");
        sb.append("    prev=\"${COMP_WORDS[COMP_CWORD-1]}\"\n\n");
        sb.append("    if [[ ${COMP_CWORD} -eq 1 ]]; then\n");
        sb.append("        COMPREPLY=( $(compgen -W \"")
                .append(String.join(" ", topWords())).append(" ").append(flags)
                .append("\" -- \"${cur}\") )\n");
        sb.append("        return 0\n");
        sb.append("    fi\n\n");
        sb.append("    case \"${COMP_WORDS[1]}\" in\n");
        for (var e : subWords().entrySet()) {
            sb.append("        ").append(e.getKey()).append(")")
                    .append(" COMPREPLY=( $(compgen -W \"")
                    .append(String.join(" ", e.getValue()))
                    .append("\" -- \"${cur}\") ); return 0 ;;\n");
        }
        sb.append("    esac\n\n");
        sb.append("    case \"${prev}\" in\n");
        sb.append("        --output)  COMPREPLY=( $(compgen -W \"json table\" -- \"${cur}\") ); return 0 ;;\n");
        sb.append("        --token-file|--body-json) COMPREPLY=( $(compgen -f -- \"${cur}\") ); return 0 ;;\n");
        sb.append("    esac\n\n");
        sb.append("    COMPREPLY=( $(compgen -W \"").append(flags).append("\" -- \"${cur}\") )\n");
        sb.append("    return 0\n");
        sb.append("}\n");
        sb.append("complete -F _ecos_completion ecos\n");
        return sb.toString();
    }

    private static String powershellCompletion() {
        List<String> candidates = new java.util.ArrayList<>(topWords());
        subWords().values().forEach(candidates::addAll);
        candidates.addAll(GLOBAL_FLAGS);
        StringBuilder sb = new StringBuilder();
        sb.append("# ecos CLI — PowerShell 补全（由 `ecos --completion ps1` 生成，请勿手改）\n");
        sb.append("Register-ArgumentCompleter -Native -CommandName ecos -ScriptBlock {\n");
        sb.append("    param($wordToComplete, $commandAst, $cursorPosition)\n");
        sb.append("    $candidates = @(\n");
        for (String c : candidates) {
            sb.append("        '").append(c).append("',\n");
        }
        sb.append("    )\n");
        sb.append("    $candidates | Where-Object { $_ -like \"$wordToComplete*\" } | ForEach-Object {\n");
        sb.append("        [System.Management.Automation.CompletionResult]::new($_, $_, 'ParameterValue', $_)\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String pad(String s, int width) {
        return s.length() >= width ? s + " " : s + " ".repeat(width - s.length());
    }

    private static String nl() {
        return System.lineSeparator();
    }
}
