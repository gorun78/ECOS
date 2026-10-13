package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F06-20 (W/C 20: 三份 agents.md 假记载按实测改写) — AI 域模块文档真实性门禁。
 *
 * <p><b>断言（源码扫描 vs 文档扫描，无 DB / 无 Spring）</b>：三份 AI 域模块文档中，
 * 历史遗留的「实测不符」字符串必须不再出现；命中 = 该处假记载未被按 F06-20 订正。
 *
 * <p>清单（2026-10-09 实测为底）：
 * <ol>
 *   <li>{@code runtime/llm-gateway/agents.md} — 不得再现虚构端点
 *       {@code /agent-loop/run}（llm-gateway 无自有 REST，那是 ai-engine agent 域端点）；</li>
 *   <li>{@code engine/ai-engine/ai-engine-boot/AGENTS.md} — 不得再宣称
 *       "Agent 周期任务（CronJob 调度）"（impl 主源码零 {@code @Scheduled} 注解，
 *       CronJobController 仅代理外部 MCP，不经本 boot 调度）；</li>
 *   <li>{@code engine/ai-engine/ai-engine-impl/AGENTS.md} —
 *     (a) 不得硬写旧值 "27 个 Controller"（实测 {@link #IMPL_CONTROLLER_BASELINE}=44）；
 *     (b) 不得硬写旧值 "6 个 test class … 40 case"（实测见 {@link #countTestClasses()}）；
 *     (c) 不得记载不存在的类 {@code KnowledgeExtractorService}（KAG 抽取承载 =
 *         {@code KnowledgeAgentContext}，非独立 Service 类）；
 *     (d) 不得记载过宽安全承诺 "写操作前调 evaluate + 默认 DENY"
 *         （当前仅 SQL 型工具经 {@code SecurityCheckerNode} 裁决，W140 之外的接线未立）。</li>
 * </ol>
 *
 * <p>若文档把 Controller 数字改写成实测值，(a) 的一致性自校验会进一步断言该数字 = 实测值；
 * 若文档改成非数字表述（区间/百分比/省略），本条只防"回到 27"这一具体回归。
 *
 * <p>验收：{@code mvn -f ecos_backend/pom.xml -pl gateway -am test
 * -Dtest='ModuleDocTruthfulnessTest#aiDomainDocsMatchMeasuredCounts'
 * -Dsurefire.failIfNoSpecifiedTests=false}
 */
class ModuleDocTruthfulnessTest {

    private static final Path LLMGW_MD = Paths.get("runtime/llm-gateway/agents.md");
    private static final Path BOOT_MD =
            Paths.get("engine/ai-engine/ai-engine-boot/AGENTS.md");
    private static final Path IMPL_MD =
            Paths.get("engine/ai-engine/ai-engine-impl/AGENTS.md");
    private static final Path IMPL_MAIN =
            Paths.get("engine/ai-engine/ai-engine-impl/src/main");
    private static final Path IMPL_TEST =
            Paths.get("engine/ai-engine/ai-engine-impl/src/test");

    /**
     * 实测：ai-engine-impl 主源码下 *Controller.java 数量。
     *
     * <p>33（2026-10-09 建基）→ 37：+1 = 补充设计 v1.0 §10 能力目录
     * {@code AiCapabilityController}（本批次），+3 = 详设-10 R2-A {@code WAgentRun/Question/Goal}
     * 三 Controller（该批次 {@code -DskipTests} 提交，本门禁从未实跑，漂移于 2026-10-11
     * 真跑时暴露）。37 → 44：+7 = 详设-10 复盘收口批（2026-10-12）补齐 §5.2 无 Controller 的
     * #11~#24 端点族：{@code WAgentKillSwitch/Readiness/Candidate/EvidenceTrace/DecisionAction/}
     * {@code ToolContract} 六 Controller + SSE 实流 {@code WAgentRunEventStreamController}，
     * 计数口径 = 文件名形态 {@code *Controller.java}，测试侧载体不计入。
     */
    private static final int IMPL_CONTROLLER_BASELINE = 44;

    private static Path backendRoot() throws IOException {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("ecos_backend"))) return p.resolve("ecos_backend");
            Path parent = p.getParent();
            if (parent == null || parent.equals(p)) break;
            p = parent;
        }
        assertTrue(false, "cwd 未锚定 ECOS 仓库根（找不到 ecos_backend/）");
        return null;
    }

    private static String read(Path f) throws IOException {
        return Files.readString(f, StandardCharsets.UTF_8);
    }

    private static int countFilesEnding(Path dir, String suffix) throws IOException {
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> s = Files.walk(dir)) {
            return (int) s.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().matches(suffix))
                    .count();
        }
    }

    private static int countAnnotatedFiles(Path dir, String marker) throws IOException {
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> s = Files.walk(dir)) {
            List<Path> files = s.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().endsWith(".java"))
                    .collect(Collectors.toList());
            int n = 0;
            for (Path f : files) {
                String body = Files.readString(f, StandardCharsets.UTF_8);
                if (body.contains(marker)) n++;
            }
            return n;
        }
    }

    /** 只数注解形态的 {@code @Scheduled(…)}，排除 javadoc 里被 {@code * @Scheduled} 引用的行。 */
    private static int countScheduledAnnotations(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return 0;
        Pattern ann = Pattern.compile("(?m)^\\s*@Scheduled\\s*[(\\n]");
        try (Stream<Path> s = Files.walk(dir)) {
            List<Path> files = s.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().endsWith(".java"))
                    .collect(Collectors.toList());
            int n = 0;
            for (Path f : files) {
                String body = Files.readString(f, StandardCharsets.UTF_8);
                Matcher m = ann.matcher(body);
                while (m.find()) n++;
            }
            return n;
        }
    }

    private static int countTestClasses(Path dir) throws IOException {
        return countAnnotatedFiles(dir, "@ParameterizedTest")
                + Math.max(0, countAnnotatedFiles(dir, "@Test"));
    }

    @Test
    @DisplayName("F06-20：三份 AI 域模块文档无实测不符的假记载")
    void aiDomainDocsMatchMeasuredCounts() throws IOException {
        Path be = backendRoot();

        for (Path md : List.of(LLMGW_MD, BOOT_MD, IMPL_MD)) {
            assertTrue(Files.isRegularFile(be.resolve(md)), "AI 域模块文档缺失: " + md);
        }
        String llmgw = read(be.resolve(LLMGW_MD));
        String boot = read(be.resolve(BOOT_MD));
        String impl = read(be.resolve(IMPL_MD));

        int measuredCtrls = countFilesEnding(be.resolve(IMPL_MAIN), ".*Controller\\.java");
        assertEquals(IMPL_CONTROLLER_BASELINE, measuredCtrls,
                "基线漂移：IMPL_CONTROLLER_BASELINE=" + IMPL_CONTROLLER_BASELINE
                        + "，实测 " + measuredCtrls + "（源码 Controller 增减须同步基线）");
        int measuredScheduled = countScheduledAnnotations(be.resolve(IMPL_MAIN));
        assertEquals(0, measuredScheduled,
                "impl 主源码 @Scheduled 注解数应为 0（调度全归 runtime-task / CronJobController"
                        + " 外部 MCP 代理），实测 " + measuredScheduled
                        + "（若确需新增，先落 runtime-task 再升本断言）");

        // ── ① llm-gateway/agents.md：虚构端点清除 ────────────────────────────
        assertFalse(llmgw.contains("/agent-loop/run"),
                "llm-gateway/agents.md 仍记载虚构端点 `/agent-loop/run`（属 ai-engine 域，非本模块 own REST）");

        // ── ② ai-engine-boot/AGENTS.md：@EnableScheduling 假承诺清除 ─────────
        assertFalse(boot.contains("Agent 周期任务"),
                "boot/AGENTS.md 仍记载 'Agent 周期任务'（impl 实测无 @Scheduled，假承诺）");
        assertFalse(boot.contains("CronJob 调度"),
                "boot/AGENTS.md 仍记载 'CronJob 调度'（CronJobController 仅代理外部 MCP，不内建调度）");

        // ── ③ ai-engine-impl/AGENTS.md：失效数字 + 不存在类 + 假安全承诺 ──────
        assertFalse(impl.matches("(?s).*27\\s*个\\s*Controller.*"),
                "impl/AGENTS.md 仍记载旧值 '27 个 Controller'（实测 " + measuredCtrls + "）");
        assertFalse(impl.contains("6 个 test class"),
                "impl/AGENTS.md 仍记载旧值 '6 个 test class'（实测 " + countTestClasses(be.resolve(IMPL_TEST)) + "）");
        assertFalse(impl.contains("KnowledgeExtractorService"),
                "impl/AGENTS.md 仍记载不存在的类 KnowledgeExtractorService（KAG 抽取 = KnowledgeAgentContext）");
        assertFalse(impl.contains("AgentMemoryTest / LLMProviderServiceTest"),
                "impl/AGENTS.md 仍按旧测试清单 6/40 硬列 test 类名（测试集已扩至 "
                        + countTestClasses(be.resolve(IMPL_TEST)) + " 类，禁再手写闭包清单）");
        assertFalse(impl.matches("(?s).*写操作前调.*evaluate.*默认?\\s*DENY.*"),
                "impl/AGENTS.md 仍记载过宽安全承诺 '写操作前调 evaluate + 默认 DENY'（"
                        + "当前仅 SQL 型工具经 SecurityCheckerNode 裁决，W140 之外接线未立）");

        // ── 一致性自校验：若文档写了 "N 个 Controller"，N 必须 = 实测 ──────────
        Matcher m = Pattern.compile("(\\d+)\\s*个\\s*Controller").matcher(impl);
        while (m.find()) {
            int claim = Integer.parseInt(m.group(1));
            assertEquals(measuredCtrls, claim,
                    "impl/AGENTS.md 'N 个 Controller' 中 N=" + claim + " 与实测 " + measuredCtrls + " 不符");
        }
    }
}
