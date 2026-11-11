package com.chinacreator.gzcm.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 计划 §八 红线：{@code tools/ecos-cli} <b>不得</b>并入 {@code ecos_backend} reactor。
 *
 * <p>原因：并入会直接撞后端模块基线门禁（{@code ArchitectureTest.baselineModules} 与
 * {@code check-legacy-modules.ps1} 的 {@code baselineCount} 均为 12，两处必须同批改），
 * 且让「CLI 独立工程、独立发布节奏」这条设计前提失效。CLI 的 pom 也不应反向依赖
 * backend 的任何一个 artifact。</p>
 *
 * <p>此前这条红线只写在计划与登记册里，靠人记 —— 一次「顺手加个 module」就会静默破线。</p>
 */
class CliNotInBackendReactorTest {

    private static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("tools").resolve("ecos-cli"))) {
            dir = dir.getParent();
        }
        assertNotNull(dir, "未定位到仓根（找不到 tools/ecos-cli）");
        return dir;
    }

    @Test
    @DisplayName("ecos_backend/pom.xml 不得出现 ecos-cli 模块")
    void backendReactorDoesNotContainCli() throws Exception {
        Path pom = repoRoot().resolve("ecos_backend/pom.xml");
        assertTrue(Files.isRegularFile(pom), "后端根 pom 不存在: " + pom);
        String xml = Files.readString(pom, StandardCharsets.UTF_8);
        for (String line : xml.split("\r?\n")) {
            if (line.contains("<module>")) {
                assertFalse(line.contains("ecos-cli") || line.contains("ecos_cli"),
                        "CLI 被并入后端 reactor（撞 baselineModules=12 且破坏独立发布）: " + line.trim());
            }
        }
        assertFalse(xml.contains("../tools/ecos-cli"),
                "后端 pom 以相对路径引用 CLI 目录");
    }

    @Test
    @DisplayName("CLI 的 pom 不得依赖 ecos_backend 任何 artifact")
    void cliPomDoesNotDependOnBackend() throws Exception {
        Path pom = repoRoot().resolve("tools/ecos-cli/pom.xml");
        assertTrue(Files.isRegularFile(pom), "CLI pom 不存在: " + pom);
        String xml = Files.readString(pom, StandardCharsets.UTF_8);
        // 只看 <dependencies>…</dependencies> 块：CLI 自身坐标也带该 groupId，全局搜会误判
        int start = xml.indexOf("<dependencies>");
        assertTrue(start > 0, "CLI pom 未见 <dependencies> 块");
        int end = xml.indexOf("</dependencies>", start);
        assertTrue(end > start, "CLI pom 的 <dependencies> 块未闭合");
        String deps = xml.substring(start, end);
        assertFalse(deps.contains("com.chinacreator.gzcm"),
                "CLI 依赖了后端 artifact（<dependencies> 内出现项目 groupId）");
        // 只判真实路径引用：pom 头部注释明确写着「本 pom 不属于 ecos_backend 根 reactor」，
        // 全局搜字面量会把这条说明当成违例。
        String withoutComments = xml.replaceAll("(?s)<!--.*?-->", "");
        assertFalse(withoutComments.contains("../ecos_backend")
                        || withoutComments.contains("tools/ecos-cli/../ecos_backend"),
                "CLI pom 以相对路径引用 ecos_backend");
    }

    @Test
    @DisplayName("后端任何 pom 都不得把 tools/ecos-cli 作为 module 纳入")
    void noBackendPomDeclaresCliModule() throws Exception {
        Path backend = repoRoot().resolve("ecos_backend");
        try (Stream<Path> walk = Files.walk(backend)) {
            var offenders = walk
                    .filter(p -> p.getFileName().toString().equals("pom.xml"))
                    .filter(p -> !p.toString().contains("target"))
                    .filter(p -> {
                        try {
                            return Files.readString(p, StandardCharsets.UTF_8).contains("tools/ecos-cli")
                                    || Files.readString(p, StandardCharsets.UTF_8).contains("tools\\ecos-cli");
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();
            assertTrue(offenders.isEmpty(), "以下后端 pom 引用了 CLI 目录: " + offenders);
        }
    }
}
