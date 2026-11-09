package com.chinacreator.gzcm.cli;

import com.chinacreator.gzcm.cli.commands.CapabilitiesCommand;
import com.chinacreator.gzcm.cli.commands.CognitionModelsListCommand;
import com.chinacreator.gzcm.cli.commands.DoctorCommand;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * {@code ecos} — 统一 CLI 入口（详设第 16 篇）。
 *
 * <p>定位：领域 API 的调用入口，<b>不承载业务逻辑</b>；只经 gateway 发 HTTP；
 * 每条命令映射契约 operationId；L3 写命令需服务端审批（本 MVP 只读，无写命令）。</p>
 */
public final class Cli {

    private static final List<List<String>> COMMAND_SEQUENCES = List.of(
            List.of("doctor"),
            List.of("cognition", "models", "list"),
            List.of("capabilities", "list"));

    public static void main(String[] argv) {
        int code = run(argv);
        System.exit(code);
    }

    static int run(String[] argv) {
        String[] args = argv == null ? new String[0] : argv;
        OperationRegistry registry = null;
        try {
            if (args.length == 0 || Arrays.asList(args).contains("--help") || "help".equals(args[0])) {
                Stdio.println(usage());
                return ExitCodes.SUCCESS;
            }
            if (Arrays.asList(args).contains("--version") || "version".equals(args[0])) {
                Stdio.println("ecos " + version());
                return ExitCodes.SUCCESS;
            }

            Match match = matchCommand(args);
            if (match == null) {
                Stdio.error(Redactor.redact("无法识别的命令: " + String.join(" ", args)));
                Stdio.error(usage());
                return ExitCodes.USAGE;
            }

            CliOptions options = CliOptions.parse(match.reordered(), match.words().size());
            registry = OperationRegistry.load();
            HttpTransport transport = new HttpTransport(options.baseUrl(), options.traceId(), options.timeoutMs());

            Map<String, Object> out = switch (match.name()) {
                case "doctor" -> DoctorCommand.run(registry, options, transport);
                case "cognition models list" -> CognitionModelsListCommand.run(registry, options, transport);
                case "capabilities list" -> CapabilitiesCommand.run(registry, options, transport);
                default -> throw new CliException(ExitCodes.USAGE, "未实现的命令: " + match.name());
            };

            OutputRenderer.render(out, options.output());
            return ExitCodes.forStatus(CliStatus.valueOf(String.valueOf(out.get("status"))));
        } catch (CliException e) {
            if (e.exitCode() == ExitCodes.USAGE) {
                Stdio.error(Redactor.redact(e.getMessage()));
                Stdio.error(usage());
                return ExitCodes.USAGE;
            }
            CliStatus status = statusFor(e.exitCode());
            Map<String, Object> out = EnvelopeMapper.local(status, codeFor(e.exitCode()),
                    e.getMessage(), null, List.of(), TraceIds.next());
            OutputRenderer.render(out, "json");
            return e.exitCode();
        } catch (RuntimeException e) {
            Map<String, Object> out = EnvelopeMapper.local(CliStatus.FAILED, "ECOS-CLI-INTERNAL",
                    Redactor.redact(e.getClass().getSimpleName() + ": " + e.getMessage()), null, List.of(),
                    TraceIds.next());
            OutputRenderer.render(out, "json");
            return ExitCodes.FAILED;
        }
    }

    private static Match matchCommand(String[] args) {
        for (int i = 0; i < args.length; i++) {
            for (List<String> sequence : COMMAND_SEQUENCES) {
                if (i + sequence.size() > args.length) {
                    continue;
                }
                boolean hit = true;
                for (int j = 0; j < sequence.size(); j++) {
                    if (!args[i + j].equals(sequence.get(j))) {
                        hit = false;
                        break;
                    }
                }
                if (hit) {
                    List<String> reordered = new ArrayList<>(sequence);
                    for (int k = 0; k < args.length; k++) {
                        if (k < i || k >= i + sequence.size()) {
                            reordered.add(args[k]);
                        }
                    }
                    return new Match(String.join(" ", sequence), sequence, reordered.toArray(new String[0]));
                }
            }
        }
        return null;
    }

    private static CliStatus statusFor(int exitCode) {
        return switch (exitCode) {
            case ExitCodes.BLOCKED -> CliStatus.BLOCKED;
            case ExitCodes.UNAVAILABLE -> CliStatus.UNAVAILABLE;
            case ExitCodes.PARTIAL -> CliStatus.PARTIAL;
            default -> CliStatus.FAILED;
        };
    }

    private static String codeFor(int exitCode) {
        return switch (exitCode) {
            case ExitCodes.BLOCKED -> "ECOS-CLI-BLOCKED";
            case ExitCodes.UNAVAILABLE -> "ECOS-CLI-UNAVAILABLE";
            case ExitCodes.PARTIAL -> "ECOS-CLI-PARTIAL";
            default -> "ECOS-CLI-FAILED";
        };
    }

    private static String version() {
        String v = Cli.class.getPackage().getImplementationVersion();
        return v == null ? "1.0.0" : v;
    }

    static String usage() {
        return String.join(System.lineSeparator(),
                "ecos — ECOS 统一 CLI（只读 MVP，只经 gateway）",
                "",
                "用法:",
                "  ecos doctor [--output json|table] [--base-url URL] [--token T | --token-file F | --profile P]",
                "  ecos cognition models list [--model-type T] [--registry-state S] [--model-kind K]",
                "  ecos capabilities list",
                "  ecos --version | --help",
                "",
                "统一参数:",
                "  --output json|table   输出格式（默认 json，面向智能体）",
                "  --base-url URL        网关地址（默认 http://localhost:8080；禁指向 service 端口）",
                "  --token / --token-file / --profile   凭据来源（凭据永不回显）",
                "  --trace-id ID         全链 trace 透传",
                "  --timeout MS          超时（1~120000，默认 30000）",
                "  --dry-run             只展示调用计划，不发请求",
                "",
                "退出码: 0 SUCCESS / 1 FAILED / 2 BLOCKED / 3 UNAVAILABLE / 4 PARTIAL / 64 用法错误");
    }

    private record Match(String name, List<String> words, String[] reordered) {
    }

    private Cli() {
    }
}
