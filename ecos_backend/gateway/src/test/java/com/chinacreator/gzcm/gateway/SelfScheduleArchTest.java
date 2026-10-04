package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * F06-17②（X-25 / X-27 → 门禁可判化，本册门禁地基之一）。
 *
 * <p>铁律 §2.5-3：业务侧（含 llm-gateway 底座自身）禁自建线程/线程池/一次性异步执行器，
 * 唯一出口是 runtime-task。既有 {@code ModuleDependencyArchTest#businessMustNotSelfSchedule}
 * 谓词只查 {@code @Scheduled} 注解与 {@code ScheduledExecutorService} 类型，对以下
 * <b>三种逃逸形态全部漏检</b>（X-27）：</p>
 * <ul>
 *   <li>{@code java.util.concurrent.Executors} 工厂方法（返回 {@code ExecutorService}，
 *       并非 {@code ScheduledExecutorService}，类型断言抓不到）；</li>
 *   <li>{@code CompletableFuture.runAsync/supplyAsync}（默认走共享 ForkJoinPool，
 *       不建任何受控类型）；</li>
 *   <li>裸 {@code new Thread(...)}（含 {@code ThreadFactory} 里工厂造 Thread，
 *       无 {@code Executor} 类型可抓）。</li>
 * </ul>
 *
 * <p>本护栏与 分册 05 {@code CognitiveLlmGatewayTest} 同型，只做<b>棘轮</b>
 *（不越界代改存量）：以 2026-10-04 实测基线冻结 ai-engine + llm-gateway 两域 main 侧
 * 的三类自建调度命中数，任何<b>新增</b>（超基线）即红，只减不增。
 * 存量 site（X-25 逐处点名）F06-17 改造批次将逐项切 runtime-task，届时基线下调。</p>
 *
 * <p>选「源码 Pattern 扫描」而非字节码断言的原因：三种逃逸形态的字节码目标类
 *（{@code java.util.concurrent.Executors}/{@code CompletableFuture}/{@code java.lang.Thread}）
 * 均为 JDK class，无法按「方法名 = newSingleThreadExecutor / runAsync」做稳定的类型级谓词
 *（需逐方法手写白名单，且新形态仍会漏）。源码判据对三形态全覆盖，
 * 与既有 ratchet 一致，阈值断言语义（只减不增）等价。</p>
 *
 * <p>验收：{@code mvn -f ecos_backend/pom.xml -pl gateway -am test -Dtest='SelfScheduleArchTest'}。
 * 依赖前置：{@code ModuleDependencyArchTest#llmGatewayIsInsideTheUniverse}（X-26 盲区已修，
 * llm-gateway 方可进 ArchUnit 宇宙）——两测同一 F06-17①/② 门禁包。</p>
 */
class SelfScheduleArchTest {

    // ── 冻结基线：2026-10-04 实测（git grep + 源码逐行核对，仅 main 侧、去注释） ─────
    // Executors. 工厂方法 —— 3
    //   AgentDelegationService.java:120   newSingleThreadExecutor（每调用新建，兼资源泄漏面）
    //   AgentStudioService.java:64        newFixedThreadPool(PIPELINE_EXECUTOR)
    //   ToolExecutorService.java:441      newSingleThreadExecutor（每调用新建）
    private static final int BASELINE_EXECUTORS = 3;
    // CompletableFuture.runAsync / supplyAsync（代码行，非注释） —— 4
    //   AgentLoopController.java:143      runAsync（SSE 流式）
    //   OagController.java:136            runAsync
    //   AgentStudioService.java:308       runAsync（:307 为注释行，不计）
    //   llm-gateway AgentSchedulerImpl.java:100  supplyAsync（底座内部 X-25 点名）
    private static final int BASELINE_COMPLETABLE = 4;
    // 裸 new Thread( —— 4
    //   AgentEvaluator.java:281           new Thread(...).start()
    //   AgentMetricsCollector.java:34     ThreadFactory 里 new Thread
    //   AgentStudioService.java:67        ThreadFactory 里 new Thread
    //   llm-gateway AgentSchedulerImpl.java:56  ThreadFactory 里 new Thread（底座自身）
    private static final int BASELINE_RAW_THREAD = 4;

    private static final Pattern NEW_EXECUTORS_FACTORY =
            Pattern.compile("\\bExecutors\\s*\\.\\s*new[A-Z]\\w*\\s*\\(");
    private static final Pattern NEW_COMPLETABLE_FACTORIES =
            Pattern.compile("\\bCompletableFuture\\s*\\.\\s*(?:runAsync|supplyAsync)\\s*\\(");
    private static final Pattern NEW_RAW_THREAD =
            Pattern.compile("\\bnew\\s+Thread\\s*\\(");

    private static final Path AI_IMPL_MAIN =
            resolveBackendRoot().resolve(
                    "engine/ai-engine/ai-engine-impl/src/main/java");
    private static final Path LLM_GW_MAIN =
            resolveBackendRoot().resolve(
                    "runtime/llm-gateway/src/main/java");

    /**
     * 向上探测 ecos_backend 根（与本模块 ModuleDependencyArchTest 相同约定）：
     * 首个同时含 pom.xml 与 gateway/ 的目录。测试运行时 cwd =
     * {@code ecos_backend/gateway}（Maven JVM 工作目录），需向上 2 级；
     * 但不能假设 —— 走目录探测更稳，且与本測試同族的 ModuleDependencyArchTest 一致。
     */
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

    @Test
    @DisplayName("F06-17② 含 llm-gateway：三形态自建调度合计只减不增（冻结基线 Executors=3 + CF=4 + Thread=4）")
    void noExecutorFactoryNoRunAsyncNoRawThreadInBusiness() throws IOException {
        List<Path> roots = List.of(AI_IMPL_MAIN, LLM_GW_MAIN);
        int exec = countHits(roots, NEW_EXECUTORS_FACTORY);
        int cf = countHits(roots, NEW_COMPLETABLE_FACTORIES);
        int th = countHits(roots, NEW_RAW_THREAD);

        Assertions.assertTrue(exec <= BASELINE_EXECUTORS,
                "F06-17② Executors 工厂自建新增：实测 " + exec
                        + " 超基线 " + BASELINE_EXECUTORS
                        + "（铁律 §2.5-3 唯一出口 runtime-task；存量 F06-17 批次切 runtime-task 后下调）");
        Assertions.assertTrue(cf <= BASELINE_COMPLETABLE,
                "F06-17② CompletableFuture.runAsync/supplyAsync 自建新增：实测 " + cf
                        + " 超基线 " + BASELINE_COMPLETABLE);
        Assertions.assertTrue(th <= BASELINE_RAW_THREAD,
                "F06-17② 裸 new Thread 自建新增：实测 " + th
                        + " 超基线 " + BASELINE_RAW_THREAD);
        System.out.println("[SelfScheduleArch] baselines (ai-engine + llm-gateway, main) — "
                + "Executors: " + exec + "/" + BASELINE_EXECUTORS
                + " CF.run/supplyAsync: " + cf + "/" + BASELINE_COMPLETABLE
                + " raw new Thread: " + th + "/" + BASELINE_RAW_THREAD);
    }

    // ── 源码扫描工具（与 CognitiveDocPaths 同族；本 ratchet 自携，避跨模块 test 依赖） ──

    private static int countHits(List<Path> roots, Pattern pattern) throws IOException {
        int count = 0;
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                throw new IllegalStateException("源码根目录不存在（工作树路径变更？）：" + root);
            }
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path p : walk.filter(Files::isRegularFile)
                        .filter(f -> f.toString().endsWith(".java"))
                        .toArray(Path[]::new)) {
                    String source = Files.readString(p);
                    for (String line : source.split("\r?\n")) {
                        String t = stripComments(line);
                        if (pattern.matcher(t).find()) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    /** 只去「行内注释」段以剔除 Javadoc / 行尾注释中的伪命中（块注释跨行场景本域不存在）。 */
    private static String stripComments(String line) {
        int lineComment = line.indexOf("//");
        if (lineComment >= 0) {
            line = line.substring(0, lineComment);
        }
        int blockStart = line.indexOf("/*");
        if (blockStart >= 0) {
            int blockEnd = line.indexOf("*/");
            if (blockEnd > blockStart) {
                line = line.substring(0, blockStart) + line.substring(blockEnd + 2);
            } else {
                line = line.substring(0, blockStart);
            }
        }
        return line;
    }
}
