package com.chinacreator.gzcm.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 补全脚本门禁（计划 §二 末行：补全必须「生成」，不得手写）。
 *
 * <p>钉住三点：①每个命令词都出现在生成的脚本里；②仓库内 {@code dist/completions/*}
 * 与生成结果逐字一致（命令树改了却忘记重生成 ⇒ 直接红）；③未知 shell 走 USAGE。</p>
 */
class CompletionScriptTest {

    private static String normalized(String s) {
        return s.replace("\r\n", "\n");
    }

    @Test
    @DisplayName("bash/ps1 生成结果覆盖命令树全部命令词")
    void generatedScriptsCoverEveryCommand() {
        String bash = HelpRenderer.completion("bash");
        String ps1 = HelpRenderer.completion("ps1");
        for (CliCommand c : CliCommands.all()) {
            for (String word : c.name().split(" ")) {
                assertTrue(bash.contains(word), "bash 补全缺命令词: " + word);
                assertTrue(ps1.contains("'" + word + "'"), "ps1 补全缺命令词: " + word);
            }
        }
        for (String flag : HelpRenderer.GLOBAL_FLAGS) {
            assertTrue(bash.contains(flag), "bash 补全缺全局参数: " + flag);
            assertTrue(ps1.contains("'" + flag + "'"), "ps1 补全缺全局参数: " + flag);
        }
    }

    @Test
    @DisplayName("仓库内补全文件必须与生成结果一致（防手工改动与过期）")
    void checkedInCompletionsAreUpToDate() throws Exception {
        assertEquals(normalized(HelpRenderer.completion("bash")),
                normalized(Files.readString(Path.of("dist", "completions", "ecos.bash"),
                        StandardCharsets.UTF_8)),
                "dist/completions/ecos.bash 与生成结果不一致：请用 ecos --completion bash 重新生成");
        assertEquals(normalized(HelpRenderer.completion("ps1")),
                normalized(Files.readString(Path.of("dist", "completions", "ecos.ps1"),
                        StandardCharsets.UTF_8)),
                "dist/completions/ecos.ps1 与生成结果不一致：请用 ecos --completion ps1 重新生成");
    }

    @Test
    @DisplayName("别名必须可执行，且同时出现在 --help 与补全里（否则文档写了用户也敲不出来）")
    void aliasesAreExecutableAndDiscoverable() {
        String bash = HelpRenderer.completion("bash");
        String ps1 = HelpRenderer.completion("ps1");
        String help = HelpRenderer.overview();
        for (Map.Entry<String, String> e : CliCommands.aliases().entrySet()) {
            String alias = e.getKey();
            String canonicalName = e.getValue();
            assertTrue(CliCommands.ALL.stream().anyMatch(c -> c.name().equals(canonicalName)),
                    "别名指向了不存在的命令: " + alias + " → " + canonicalName);
            for (String word : alias.split(" ")) {
                assertTrue(bash.contains(word), "bash 补全缺别名词: " + word);
                assertTrue(ps1.contains("'" + word + "'"), "ps1 补全缺别名词: " + word);
            }
            assertTrue(help.contains(alias), "--help 未列出别名: " + alias);
        }
        // 别名必须真能路由到规范命令（匹配层归一）
        for (String alias : CliCommands.aliases().keySet()) {
            assertEquals(CliCommands.canonical(alias),
                    CliCommands.all().stream()
                            .map(CliCommand::name)
                            .filter(n -> n.equals(CliCommands.canonical(alias)))
                            .findFirst()
                            .orElseThrow(),
                    "别名归一后必须命中命令树");
        }
    }

    @Test
    @DisplayName("未知 shell ⇒ USAGE(64)，不给半成品脚本")
    void unknownShellIsUsageError() {
        CliException e = assertThrows(CliException.class, () -> HelpRenderer.completion("zsh"));
        assertEquals(ExitCodes.USAGE, e.exitCode());
    }
}
