package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * F08-05 / B8a —— 业务侧禁自建 {@code @Scheduled}（架构铁律 §2.5 / §2.5-3；调度单出口 = runtime-task）。
 *
 * <p>覆盖域：engine/*-engine-impl / services/* / workspace/* / runtime/*（除 runtime-task 底座自身）。
 * 与 {@link SelfScheduleArchTest}（X-27 自建线程/线程池三类逃逸）同族，本护栏只盯
 * {@code @Scheduled(} 这一 Spring 调度注解：H8-T3 已把 data-engine 的 DQ 扫描 / 告警升级 /
 * Schema 侦测与 kb-engine 的抽取/画像任务全部改为 {@code TaskManagementService.registerParser/
 * registerExecutor}，业务侧不再有自建 @Scheduled。</p>
 *
 * <p><b>基线冻结（2026-10-07 B8 派工，live grep 实测 = 3）</b>：全部集中在
 * runtime-event（Kafka 出事/审计兜底最小闭环的横切底层，兜底延迟任务用 {@code @Scheduled}
 * 是最短路径，属底座合法使用）——
 * <ul>
 *   <li>{@code runtime-event/audit/AuditRetryTask.java:63} 注解 {@code @Scheduled(fixedDelay=60s)}（1）</li>
 *   <li>{@code runtime-event/audit/AuditRetryTask.java:26} javadoc {@code {@code @Scheduled(fixedDelay=60s)}}（1，正则连带命中）</li>
 *   <li>{@code runtime-event/dlq/DltLagChecker.java:66} 注解 {@code @Scheduled(fixedDelay=300s)}（1）</li>
 * </ul>
 * 其余 6 处「@Scheduled」仅在 javadoc 以<b>裸词</b>出现（后面不带 `{@code …(}`），
 * 是本正则 {@code @Scheduled\s*\(} 不命中的历史「已切 runtime-task」说明语，不计入基线。
 * 随 H8-T3 收口批次把这两个兜底 poller 也切换 register 后，基线下调至 0。当前护栏：**只减不增**。</p>
 *
 * <p>验收：{@code mvn -f ecos_backend/pom.xml -pl gateway -am test -Dtest='NoScheduledAnnotationArchTest'}。</p>
 */
class NoScheduledAnnotationArchTest {

    /** 基线冻结 2026-10-07：3 = runtime-event 两兜底 poller（AuditRetryTask 含 1 行 javadoc、DltLagChecker 各 1）。 */
    static final int BASELINE_SCHEDULED = 3;

    private static final Pattern SCHED_ANNOTATION = Pattern.compile("@Scheduled\\s*\\(");

    // 扫四大业务/横切顶层域（engine / services / workspace / runtime），
    // 排除 runtime-task（调度底座自身合法使用 @Scheduled）。
    private static final List<String> TOP_LEVEL = List.of(
            "engine", "services", "workspace", "runtime");

    // 排除 pattern：调度底座自身合法使用 @Scheduled。
    private static final Pattern EXCLUDED_MODULE = Pattern.compile("/runtime-task/");

    @Test
    @DisplayName("B8a 业务侧 @Scheduled( 只减不增（基线 3，全部待 H8-T3 切 runtime-task）")
    void businessScheduledMustNotGrow() throws IOException {
        Path root = resolveBackendRoot();
        int hit = 0;
        for (String top : TOP_LEVEL) {
            Path base = root.resolve(top);
            if (!Files.isDirectory(base)) {
                throw new IllegalStateException("@Scheduled 扫描根目录缺失：" + base);
            }
            try (Stream<Path> walk = Files.walk(base)) {
                for (Path p : walk.filter(Files::isRegularFile)
                        .filter(f -> f.toString().endsWith(".java"))
                        // 只扫 main 生产源码，跳 test / target / archive
                        .filter(f -> f.toString().replaceAll("\\\\", "/").contains("/src/main/java/"))
                        .filter(f -> !EXCLUDED_MODULE.matcher(f.toString().replaceAll("\\\\", "/")).find())
                        .toArray(Path[]::new)) {
                    String source = Files.readString(p);
                    for (String raw : source.split("\r?\n")) {
                        String line = stripLineComment(raw).stripLeading();
                        if (SCHED_ANNOTATION.matcher(line).find()) {
                            hit++;
                            System.err.println("[B8a 命中] " + p + "  ->  " + raw.strip());
                        }
                    }
                }
            }
        }
        Assertions.assertTrue(hit <= BASELINE_SCHEDULED,
                "B8a 业务侧 @Scheduled( 超基线：实测 " + hit + " 超基线 " + BASELINE_SCHEDULED
                        + "（铁律 §2.5 唯一调度出口 runtime-task；请改 TaskManagementService.registerParser/registerExecutor）");
        System.out.println("[NoScheduledAnnotationArch] " + TOP_LEVEL.size()
                + " 顶层域 main @Scheduled( 命中 " + hit + "/" + BASELINE_SCHEDULED);
    }

    private static Path resolveBackendRoot() {
        Path cur = Path.of("").toAbsolutePath();
        while (cur != null) {
            if (Files.isRegularFile(cur.resolve("pom.xml"))
                    && Files.isDirectory(cur.resolve("gateway"))) {
                return cur.normalize();
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("未能定位 ecos_backend 根（pom.xml + gateway/ 目录）");
    }

    private static String stripLineComment(String line) {
        int idx = line.indexOf("//");
        return idx >= 0 ? line.substring(0, idx) : line;
    }
}
