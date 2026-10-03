package com.chinacreator.gzcm.engine.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F02-10 验收 — {@code EscalationSchedulerRegisteredInRuntimeTaskTest}（二段之一）。
 * <p>
 * 断言 data-engine/src/main 内<b>无</b> {@code @Scheduled} 自建调度（升级驱动归 runtime-task）。
 * 文本层护栏：任何 {@code @Scheduled(...)} 注解命中即 fail。与 {@link NoDriverManagerArchTest}
 * 同型源码扫描。dotfile 排除（不进业务代码）。
 */
@DisplayName("F02-10 升级驱动归 runtime-task：data-engine 禁 @Scheduled")
class EscalationSchedulerRegisteredInRuntimeTaskTest {

    private static final Pattern SCHEDULED_ANNOTATION =
            Pattern.compile("(?m)^\\s*@(Scheduled|EnableScheduling)\\b");

    @Test
    void escalationDriverNotSelfScheduledInDataEngine() throws IOException {
        List<String> hits = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(moduleSrc())) {
            for (Path p : (Iterable<Path>) walk
                    .filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".java"))
                    .sorted()
                    ::iterator) {
                String text = Files.readString(p, StandardCharsets.UTF_8);
                if (SCHEDULED_ANNOTATION.matcher(text).find()) {
                    hits.add(p.toString());
                }
            }
        }
        assertTrue(hits.isEmpty(),
            "F02-10 违规 —— data-engine 内出现 @Scheduled/@EnableScheduling 自建调度（应归 runtime-task）:\n"
                + String.join("\n", hits));
    }

    private static Path moduleSrc() {
        // 复用 RSL 扫描的模块根解析（同引擎内 guard 系列共享解析策略）
        Path resolved = RlsInjectionGuardArchTest.resolveModuleSrcMainJava();
        assertTrue(Files.isDirectory(resolved),
                "data-engine-impl/src/main/java 不存在: " + resolved);
        return resolved;
    }
}
