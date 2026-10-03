package com.chinacreator.gzcm.gateway.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F00-08（docs/30-设计/详细设计-00-平台入口与横切底座-2026-09-28.md §F00-08 行，M1）
 * — 禁引擎/gateway 内 {@code @Scheduled} 新增：任务入口唯一收 runtime-task（{@code ITaskExecutor}），
 * 引擎/部署 jar 自建线程/定时器 = 违反统一调度底座。
 *
 * <p><b>白名单语义（Q5 裁决"存量只定性不清洗"）：</b>2026-09-30 实测 8 个存量
 * {@code @Scheduled} 类纳入白名单：
 * <ul>
 *   <li>runtime-event：{@code AuditRetryTask}（F00-12 审计兜底重放）、
 *       {@code DltLagChecker}（F00-10 DLQ lag 告警）：</li>
 *   <li>data-engine（DqScheduledTask / SchemaChangeDetector / PipelineDebugService）：
 *       存量既定 whitelisted 保留。</li>
 *   <li>gateway telemetry/twin：UsageCollector / DeviceSimulatorService / DigitalTwinService：
 *       存量 whitelisted 保留。</li>
 * </ul>
 * 拟新增任何 {@code @Scheduled} 类 — 必须先收敛 runtime-task（{@code ITaskExecutor}）—
 * 否则本测试 FAIL，防止新增 ad-hoc scheduler 污染统一调度底座。
 *
 * <p>运行：{@code mvn -pl gateway -am test -Dtest='NoAdHocSchedulerTest'}
 */
class NoAdHocSchedulerTest {

    /**
     * 白名单（相对 backend 根，路径分隔符 {@code /}）：2026-09-30 实测 8 个文件；
     * 新增任何 {@code @Scheduled} 类必须先进 runtime-task 或更新本白名单。
     */
    private static final Set<String> SCHEDULED_WHITELIST = Set.of(
            "engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/pipeline/PipelineDebugService.java",
            "engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/scheduler/DqScheduledTask.java",
            "engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/scheduler/SchemaChangeDetector.java",
            "gateway/src/main/java/com/chinacreator/gzcm/gateway/telemetry/UsageCollector.java",
            "gateway/src/main/java/com/chinacreator/gzcm/gateway/twin/DeviceSimulatorService.java",
            "gateway/src/main/java/com/chinacreator/gzcm/gateway/twin/DigitalTwinService.java",
            "runtime/runtime-event/src/main/java/com/chinacreator/gzcm/runtime/audit/AuditRetryTask.java",
            "runtime/runtime-event/src/main/java/com/chinacreator/gzcm/runtime/dlq/DltLagChecker.java"
    );

    private static final Pattern SCHEDULED_ANNOTATION = Pattern.compile("@Scheduled\\s*[(\\n]");

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

    @Test
    @DisplayName("F00-08：backend 主源码新增 @Scheduled 类必须经 runtime-task 收编（白名单外 → FAIL）")
    void noNewScheduledClassesOutsideWhitelist() {
        Path root = backendRoot();
        List<Path> scanRoots = List.of(
                root.resolve("engine"), root.resolve("services"),
                root.resolve("workspace"), root.resolve("gateway"),
                root.resolve("runtime")
        );
        List<String> offenders = new ArrayList<>();
        for (Path base : scanRoots) {
            if (!Files.isDirectory(base)) continue;
            try (var walk = Files.walk(base)) {
                walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("target"))
                    .filter(p -> !p.toString().contains("/test/"))
                    .filter(p -> !p.toString().contains("archive"))
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p, StandardCharsets.UTF_8);
                            Matcher m = SCHEDULED_ANNOTATION.matcher(content);
                            if (m.find()) {
                                String rel = root.relativize(p).toString().replace('\\', '/');
                                if (!SCHEDULED_WHITELIST.contains(rel)) {
                                    offenders.add(rel);
                                }
                            }
                        } catch (IOException ignored) { }
                    });
            } catch (IOException e) {
                throw new java.io.UncheckedIOException("walk 失败 " + base, e);
            }
        }
        assertTrue(offenders.isEmpty(),
                "F00-08 红线：发现白名单外新增 @Scheduled 类（收敛 runtime-task，禁引擎/gateway 自建定时器）：\n  "
                        + String.join("\n  ", offenders));
    }
}
