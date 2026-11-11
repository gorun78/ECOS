package com.chinacreator.gzcm.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 未知参数必须 USAGE 拒绝（计划 §一「绝不伪装成功」）。
 *
 * <p>真实缺陷：{@code ecos capabilities list --bogus-flag x} 曾 exit 0 并照常执行。
 * 危险面在写命令 —— 把 {@code --dry-run} 误拼成 {@code --dry-runn} 时，若解析器静默
 * 丢弃，L3 命令会带着审批凭证真发货。</p>
 *
 * <p>本门禁同时钉住反方向：命令自己声明的参数与全局参数不得被误判为未知
 * （否则正常用法全被拒绝，门禁变成新的故障源）。</p>
 */
class UnknownFlagGateTest {

    private static CliOptions parse(String... argv) {
        return CliOptions.parse(argv, countCommandWords(argv));
    }

    /** 与 Cli#matchCommand 同口径：命令词个数由命令树最长匹配决定。 */
    private static int countCommandWords(String[] argv) {
        for (List<String> seq : CliCommands.sequences()) {
            if (argv.length >= seq.size() && List.of(argv).subList(0, seq.size()).equals(seq)) {
                return seq.size();
            }
        }
        return 0;
    }

    @Test
    @DisplayName("未知参数被拒（USAGE），且报错点名参数与命令")
    void unknownFlagIsRejected() {
        CliOptions options = parse("agent", "inspect", "--run-id", "R", "--bogus-flag", "x");
        CliCommand command = CliCommands.find(CliCommands.AGENT_INSPECT);
        CliException e = assertThrows(CliException.class,
                () -> Cli.rejectUnknownFlags(options, command));
        assertEquals(ExitCodes.USAGE, e.exitCode());
        assertTrue(e.getMessage().contains("--bogus-flag"), "报错必须点名拼错的参数: " + e.getMessage());
        assertTrue(e.getMessage().contains("agent inspect"), "报错必须点名命令: " + e.getMessage());
    }

    @Test
    @DisplayName("拼错 --dry-run 的写命令必须被拒（否则会真实发货）")
    void typoDryRunOnWriteCommandIsRejected() {
        CliOptions options = parse("knowledge", "publish", "--candidate-id", "C",
                "--approval-token", "T", "--dry-runn", "true");
        CliCommand command = CliCommands.find(CliCommands.KNOWLEDGE_PUBLISH);
        assertTrue(command.requiresApproval(), "夹具前提失效：knowledge publish 应为 L3");
        CliException e = assertThrows(CliException.class,
                () -> Cli.rejectUnknownFlags(options, command));
        assertTrue(e.getMessage().contains("--dry-runn"), e.getMessage());
    }

    @Test
    @DisplayName("命令声明参数 + 全局参数 + 路径/体参数一律放行（不得误杀正常用法）")
    void declaredAndGlobalFlagsPass() {
        for (CliCommand command : CliCommands.all()) {
            for (var f : command.flags()) {
                List<String> argv = new java.util.ArrayList<>(List.of(command.name().split(" ")));
                if (!f.parameter().isEmpty()) {
                    argv.add(f.flag());
                    argv.add("v");
                }
                assertDoesNotThrow(() -> Cli.rejectUnknownFlags(
                                parse(argv.toArray(new String[0])), command),
                        command.name() + " 的声明参数 " + f.flag() + " 被误判为未知");
            }
            for (var p : command.pathParams()) {
                List<String> argv = new java.util.ArrayList<>(List.of(command.name().split(" ")));
                argv.add(p.flag());
                argv.add("v");
                assertDoesNotThrow(() -> Cli.rejectUnknownFlags(
                                parse(argv.toArray(new String[0])), command),
                        command.name() + " 的路径参数 " + p.flag() + " 被误判为未知");
            }
            for (var b : command.bodyParams()) {
                List<String> argv = new java.util.ArrayList<>(List.of(command.name().split(" ")));
                argv.add(b.flag());
                argv.add("v");
                assertDoesNotThrow(() -> Cli.rejectUnknownFlags(
                                parse(argv.toArray(new String[0])), command),
                        command.name() + " 的体参数 " + b.flag() + " 被误判为未知");
            }
        }
    }

    @Test
    @DisplayName("全局参数对任意命令都合法")
    void globalFlagsAlwaysAccepted(@org.junit.jupiter.api.io.TempDir java.nio.file.Path home)
            throws Exception {
        // --profile / --token-file 在解析期就要落地真实文件，故给一个临时 HOME 撑住夹具。
        java.nio.file.Path profileDir = home.resolve(".config").resolve("ecos");
        java.nio.file.Files.createDirectories(profileDir);
        java.nio.file.Files.writeString(profileDir.resolve("cli-test.properties"),
                "baseUrl=http://localhost:8080\ntoken=t\n", java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Path tokenFile = home.resolve("token.txt");
        java.nio.file.Files.writeString(tokenFile, "t", java.nio.charset.StandardCharsets.UTF_8);

        String originalHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        try {
            CliCommand command = CliCommands.find(CliCommands.CAPABILITIES_LIST);
            for (String flag : HelpRenderer.GLOBAL_FLAGS) {
                List<String> argv = new java.util.ArrayList<>(List.of("capabilities", "list"));
                argv.add(flag);
                if (!isBoolean(flag)) {
                    argv.add(sampleValue(flag, tokenFile));
                }
                assertDoesNotThrow(() -> Cli.rejectUnknownFlags(parse(argv.toArray(new String[0])), command),
                        "全局参数 " + flag + " 被误判为未知");
            }
        } finally {
            System.setProperty("user.home", originalHome);
        }
    }

    /** 每个取值型全局参数一份合法样例（解析期就会校验枚举/数值/文件存在性）。 */
    private static String sampleValue(String flag, java.nio.file.Path tokenFile) {
        return switch (flag) {
            case "--output" -> "json";
            case "--timeout" -> "1000";
            case "--page-size" -> "10";
            case "--max-pages" -> "2";
            case "--base-url" -> "http://localhost:8080";
            case "--completion" -> "bash";
            case "--profile" -> "test";
            case "--token-file" -> tokenFile.toString();
            default -> "v";
        };
    }

    private static boolean isBoolean(String flag) {
        List<String> booleans = List.of("--help", "-h", "--version", "--dry-run", "--all",
                "--follow", "--show-config", "--no-retry", "--preflight");
        return booleans.contains(flag);
    }

    /**
     * 端到端：必须由 {@link Cli#run} 真正拒。
     *
     * <p>上一组用例直接调 {@code rejectUnknownFlags}，只证明「函数本身有判断」；
     * 若调用点被删掉/注释，函数还在、用例仍绿 —— 门禁就成了摆设。此处走完整入口，
     * 选 {@code capabilities list}（纯本地、零网络）干掉这个盲区。</p>
     */
    @Test
    @DisplayName("端到端：Cli.run 对未知参数返回 USAGE 且不输出命令结果")
    void cliRunRejectsUnknownFlagEndToEnd() {
        int code = Cli.run(new String[] {"capabilities", "list", "--bogus-flag", "x"});
        assertEquals(ExitCodes.USAGE, code, "未知参数必须以 USAGE 退出（实得 " + code + "）");
    }

    @Test
    @DisplayName("端到端：合法参数不被误杀（同一入口必须仍 exit 0）")
    void cliRunAcceptsDeclaredFlagEndToEnd() {
        assertEquals(ExitCodes.SUCCESS, Cli.run(new String[] {"capabilities", "list"}));
    }
}
