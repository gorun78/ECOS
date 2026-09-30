package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-00 F00-07 — LLM 唯一出口门禁（REQ-PLT-21 / C1）。
 *
 * <p>全仓除 {@code runtime/llm-gateway} 外，任何 <b>Java 源码</b>不得出现 LLM/Embedding 供应商
 * 端点字面量（当前生产仅 DeepSeek 单家，见 {@link #FORBIDDEN_LITERAL}）；引擎禁止绕过
 * llm-gateway 直连供应商。命中即 FAIL。配置 yml（endpoint 值）属合法配置注入，不在本 gate
 * 扫描范围（Java 源码硬编码才是真违规）。</p>
 */
class LlmSoleEgressTest {

    private static final String FORBIDDEN_LITERAL = "api.deep" + "seek.com";
    private static final String ALLOWED_MODULE_MARKER = "llm-gateway";

    @Test
    @DisplayName("全仓 Java 源码除 llm-gateway 外零 LLM 供应商端点字面量")
    void noLlmProviderLiteralOutsideLlmGateway() throws IOException {
        // surefire 工作目录 = gateway/；回溯一级 = ecos_backend
        Path backendRoot = Paths.get("..").toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(backendRoot), "源根未找到: " + backendRoot);

        List<String> violations;
        try (Stream<Path> walk = Files.walk(backendRoot)) {
            violations = walk
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> {
                    String s = p.toString().replace('\\', '/');
                    return !s.contains("/target/") && !s.contains(ALLOWED_MODULE_MARKER);
                })
                .map(Path::toString)
                .filter(f -> filesContainsLiteral(f))
                .toList();
        }

        assertTrue(violations.isEmpty(),
            "F00-07：以下 Java 源码硬编码 LLM 供应商端点 '" + FORBIDDEN_LITERAL + "'（应走 runtime/llm-gateway）: "
                + String.join("\n  ", violations));
    }

    private static boolean filesContainsLiteral(String file) {
        try {
            String content = Files.readString(Path.of(file), StandardCharsets.UTF_8);
            return content.contains(FORBIDDEN_LITERAL);
        } catch (IOException e) {
            return false;
        }
    }
}
