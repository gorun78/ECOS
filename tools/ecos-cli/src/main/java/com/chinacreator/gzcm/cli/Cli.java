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
                Match helpMatch = matchCommand(args);
                Stdio.println(helpMatch == null ? usage() : usageFor(helpMatch.name()));
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
            if (options.flag("--show-config")) {
                Map<String, Object> out = EnvelopeMapper.local(CliStatus.SUCCESS, "ECOS-CLI-CONFIG",
                        "生效配置（token 只显示是否已配置）", null, List.of(), options.traceId());
                out.put("data", options.effectiveConfig());
                out.put("error", null);
                OutputRenderer.render(out, options.output());
                return ExitCodes.SUCCESS;
            }
            registry = OperationRegistry.load();
            HttpTransport transport = new HttpTransport(options.baseUrl(), options.traceId(),
                    options.timeoutMs(), options.retryPolicy(), false);
            long startedAt = System.nanoTime();
            CliLog.event("command.start", Map.of(
                    "command", match.name(),
                    "traceId", options.traceId(),
                    "baseUrl", options.baseUrl()));

            Map<String, Object> out = switch (match.name()) {
                case "doctor" -> DoctorCommand.run(registry, options, transport);
                case "cognition models list" -> CognitionModelsListCommand.run(registry, options, transport);
                case "capabilities list" -> CapabilitiesCommand.run(registry, options, transport);
                default -> throw new CliException(ExitCodes.USAGE, "未实现的命令: " + match.name());
            };

            CliLog.event("command.finish", Map.of(
                    "command", match.name(),
                    "traceId", String.valueOf(out.get("trace_id")),
                    "status", String.valueOf(out.get("status")),
                    "elapsedMs", (System.nanoTime() - startedAt) / 1_000_000L));
            OutputRenderer.render(out, options.output());
            return ExitCodes.forStatus(CliStatus.valueOf(String.valueOf(out.get("status"))));
        } catch (CliException e) {
            CliLog.error("command.blocked", Map.of("exitCode", String.valueOf(e.exitCode()),
                    "message", String.valueOf(e.getMessage())));
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
            CliLog.error("command.failed", Map.of(
                    "type", e.getClass().getSimpleName(),
                    "message", String.valueOf(e.getMessage())));
            Map<String, Object> out = EnvelopeMapper.local(CliStatus.FAILED, CliErrorCodes.INTERNAL,
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
            case ExitCodes.BLOCKED -> CliErrorCodes.BLOCKED;
            case ExitCodes.UNAVAILABLE -> CliErrorCodes.UNAVAILABLE;
            case ExitCodes.PARTIAL -> CliErrorCodes.PARTIAL;
            default -> CliErrorCodes.FAILED;
        };
    }

    private static String version() {
        String v = Cli.class.getPackage().getImplementationVersion();
        String build = Cli.class.getPackage().getImplementationVersion() == null
                ? null : manifestValue("Implementation-Build");
        String base = v == null ? "1.0.0" : v;
        return build == null ? base : base + " (" + build + ")";
    }

    private static String manifestValue(String key) {
        try (var in = Cli.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {
            if (in == null) {
                return null;
            }
            var props = new java.util.jar.Manifest(in).getMainAttributes();
            String val = props.getValue(key);
            return val == null || val.isBlank() || val.contains("${") ? null : val;
        } catch (Exception e) {
            return null;
        }
    }

    /** 顶层帮助（概览）；带具体命令时输出命令详情。 */
    static String usage() {
        return HelpRenderer.overview();
    }

    private static String usageFor(String commandName) {
        CliCommand cmd = CliCommands.find(commandName);
        if (cmd == null) {
            return HelpRenderer.overview();
        }
        OperationRegistry registry;
        try {
            registry = OperationRegistry.load();
        } catch (RuntimeException e) {
            registry = null;
        }
        return HelpRenderer.detail(cmd, registry);
    }

    private record Match(String name, List<String> words, String[] reordered) {
    }

    private Cli() {
    }
}
