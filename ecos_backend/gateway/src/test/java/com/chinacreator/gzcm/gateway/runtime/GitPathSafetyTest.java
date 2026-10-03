package com.chinacreator.gzcm.gateway.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F00-06（docs/30-设计/详细设计-00-平台入口与横切底座-2026-09-28.md §F00-06，M1）
 * — Git 走 repositoryId 单参；Controller 层不得接受裸文件路径作为 Git 操作输入。
 *
 * <p>扫描 data-engine/ontology-engine 的 Git Controller，
 * 断言请求 DTO 字段中不存在 {@code path}/{@code fileUri}/{@code repoPath}
 * 等裸路径字段（只允许 {@code repositoryId}）。
 *
 * <p>运行：{@code mvn -pl gateway -am test -Dtest='GitPathSafetyTest'}
 */
class GitPathSafetyTest {

    /** Git 操作 Controller（相对 backend 根） */
    private static final String[] GIT_CONTROLLERS = {
            "engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/controller/PipelineGitController.java",
            "engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/controller/OntologyGitController.java",
    };

    /** Git 服务接口/实现（路径字面量禁泄露） */
    private static final String[] GIT_SERVICES = {
            "engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/service/PipelineGitService.java",
            "engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/service/GitRepoRootResolver.java",
    };

    /** 裸路径字段名黑名单（Controller 请求体不得出现） */
    private static final Pattern BARE_PATH_FIELD =
            Pattern.compile("\\b(path|fileUri|repoPath|fsPath|filePath)\\s*\\(");

    /** processBuilder / Runtime.exec 在 Git 服务中禁用（防命令行注入） */
    private static final Pattern SHELL_EXEC =
            Pattern.compile("ProcessBuilder|Runtime\\.getRuntime\\(\\)\\.exec|Files\\.exec");

    private static Path backendRoot() {
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path guess = cwd;
        while (guess != null) {
            if (Files.isDirectory(guess.resolve("gateway/src/main/java"))
                    && Files.isDirectory(guess.resolve("engine/data-engine"))) {
                return guess;
            }
            guess = guess.getParent();
        }
        return cwd;
    }

    private static String read(Path root, String relative) {
        try {
            return Files.readString(root.resolve(relative), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取失败: " + relative, e);
        }
    }

    @Test
    @DisplayName("Git Controller DTO 不得含裸路径字段（只允许 repositoryId）")
    void controllerDtosHaveNoBarePathField() {
        Path root = backendRoot();
        List<String> violations = new ArrayList<>();
        for (String rel : GIT_CONTROLLERS) {
            String source = read(root, rel);
            Matcher m = BARE_PATH_FIELD.matcher(source);
            while (m.find()) {
                violations.add(rel + ": 裸路径参数 @ " + m.group(1));
            }
        }
        assertTrue(violations.isEmpty(),
                "Git Controller 存在裸路径参数（F00-06：只允许 repositoryId 单参）：\n  "
                        + String.join("\n  ", violations));
    }

    @Test
    @DisplayName("Git 服务实现不得直接调用 ProcessBuilder / Runtime.exec（防 shell 注入）")
    void gitServiceNoShellExec() {
        Path root = backendRoot();
        List<String> violations = new ArrayList<>();
        for (String rel : GIT_SERVICES) {
            String source = read(root, rel);
            Matcher m = SHELL_EXEC.matcher(source);
            while (m.find()) {
                violations.add(rel + ": shell exec @ " + m.group());
            }
        }
        assertTrue(violations.isEmpty(),
                "Git 服务存在 shell exec 调用（F00-06 安全红线）：\n  "
                        + String.join("\n  ", violations));
    }

    @Test
    @DisplayName("Pipeline Git Service 方法签名含 repositoryId 参数（单参模式）")
    void pipelineGitServiceUsesRepositoryId() {
        Path root = backendRoot();
        String source = read(root,
                "engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/service/PipelineGitService.java");
        assertFalse(source.isBlank(), "PipelineGitService 源不存在");
        assertTrue(source.contains("repositoryId"),
                "PipelineGitService 必须使用 repositoryId 模式（F00-06 单参约定）");
    }
}
