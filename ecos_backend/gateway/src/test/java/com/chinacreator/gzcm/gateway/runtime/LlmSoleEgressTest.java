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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F00-07（docs/30-设计/详细设计-00-平台入口与横切底座-2026-09-28.md §F00-07 行 / C1，M1）
 * — 全仓除 runtime/llm-gateway 外零 {@code api.deepseek.com} 字面量；
 * 引擎侧 LLM 调用一律经 llm-gateway 门面，直连外部 API 是 C1 红线。
 *
 * <p>扫描范围：ecos_backend 下所有 .java 文件（排除 llm-gateway 模块 + target）。
 * yml 配置允许（运行时 URL 注入，非代码直连）。
 *
 * <p>运行：{@code mvn -pl gateway -am test -Dtest='LlmSoleEgressTest'}
 */
class LlmSoleEgressTest {

    private static final String FORBIDDEN = "api.deepseek.com";

    private static Path backendRoot() {
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path guess = cwd;
        while (guess != null) {
            if (Files.isDirectory(guess.resolve("gateway/src/main/java"))
                    && Files.isDirectory(guess.resolve("runtime/llm-gateway"))) {
                return guess;
            }
            guess = guess.getParent();
        }
        return cwd;
    }

    @Test
    @DisplayName("F00-07：引擎/服务 Java 源码零 api.deepseek.com 字面量（唯一出口 = llm-gateway）")
    void noDeepseekDirectInEngineJava() {
        Path root = backendRoot();
        Path engineRoot = root.resolve("engine");
        Path servicesRoot = root.resolve("services");
        List<String> offenders = new ArrayList<>();

        for (Path base : List.of(engineRoot, servicesRoot)) {
            if (!Files.isDirectory(base)) continue;
            try (Stream<Path> walk = Files.walk(base)) {
                walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("target"))
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p, StandardCharsets.UTF_8);
                            if (content.contains(FORBIDDEN)) {
                                String rel = root.relativize(p).toString();
                                offenders.add(rel);
                            }
                        } catch (IOException ignored) { }
                    });
            } catch (IOException e) {
                throw new UncheckedIOException("walk 失败 " + base, e);
            }
        }

        assertTrue(offenders.isEmpty(),
                "F00-07 红线：引擎/服务 Java 直含 api.deepseek.com 字面量（C1: LLM 唯一出口必须经 llm-gateway）：\n  "
                        + String.join("\n  ", offenders));
    }
}
