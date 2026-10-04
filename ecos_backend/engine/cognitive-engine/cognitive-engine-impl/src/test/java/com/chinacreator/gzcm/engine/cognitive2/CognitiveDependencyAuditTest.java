package com.chinacreator.gzcm.engine.cognitive2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分册05 B-2 铁律守护之「不引入规则引擎」离线护栏。
 *
 * <p>认知 engine impl / api 两模块的 pom.xml 中<b>零</b>依赖规则引擎成分
 * （drools / kie / easy-rules / mvel / juel / spel 专用 component）；新引入即红。</p>
 */
final class CognitiveDependencyAuditTest {

    private static final Pattern RULE_ENGINE_ARTIFACT = Pattern.compile(
        "(?:org\\.drools|org\\.kie|org\\.jbpm|easy-rules|decisiontables-juel|"
            + "com\\.github\\.mifmif|mvel|net\\.beanshell|org\\.mvel)",
        Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("cognitive impl / api 两 pom 零规则引擎依赖（B-2 铁律守护）")
    void noRuleEngineOnClasspath() throws IOException {
        Path root = CognitiveDocPaths.repoRoot().resolve("ecos_backend/engine/cognitive-engine");
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path pom : walk.filter(p -> p.getFileName().toString().equals("pom.xml")).toList()) {
                String body = Files.readString(pom, StandardCharsets.UTF_8);
                assertFalse(RULE_ENGINE_ARTIFACT.matcher(body).find(),
                    "规则引擎依赖出现在 %s（B-2 铁律：认知不引入规则引擎）".formatted(pom));
            }
        }
        assertTrue(true); // 走查 0 命中即通过
    }
}
