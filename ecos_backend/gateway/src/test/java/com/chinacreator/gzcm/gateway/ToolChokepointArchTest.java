package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * F06-01⑤（X-19/X-20 咽喉单入口履约）：工具执行咽喉的「无双入口」守卫。
 *
 * <p>REQ-AI-01 §1.2 把裁决咽喉定在<b>既有事实咽喉</b>
 * {@code ToolExecutorService.execute(String, Map, String)} 上，明确<b>禁新建
 * {@code ToolDispatcher}</b>（咽喉若出现第二个入口，绕过面反而扩大）。
 * 咽喉内的唯一出工具执行点是 {@code ToolRegistry#execute}；DB 注册 BUILTIN 工具
 * 经 {@code ToolExecutorService#executeBuiltin} 派发（本类内私有）。
 *
 * <p>本护栏（源码级，与 {@link SelfScheduleArchTest} 同族）冻结「吞吐咽喉可判化」
 * 的下限：在 ai-engine-impl 的 main 侧，除 {@code ToolExecutorService.java} 自身外，
 * 任何类<b>不得</b>直接调用 {@code toolRegistry.execute(...)}，也<b>不得</b>直接调用
 * {@code executeBuiltin(...)}（后者在源码里是 {@code ToolExecutorService} 的私有方法，
 * 外部类本就无法调用，此断言防的是把该私有派发口升为 protected/public 或复制派发逻辑
 * 的绕行形态）。</p>
 *
 * <p>2026-10-04 实测：两条外咽喉调用在 main 侧均为 0（既有唯一执行路径即
 * {@code ToolExecutorService:129 toolRegistry.execute} 与 {@code :468 executeBuiltin}
 * 派发，都在咽喉类内部；{@code AgentToolController} 仅调 listAll/get 只读内省，
 * {@code AgentLoopService:1093} 走 {@code toolExecutorService.execute} 即咽喉本身）。
 * 基线冻 0 = 只减不增（新增一处外咽喉外调即红）。</p>
 *
 * <h3>诚实边界（同 SelfScheduleArchTest 的诚实口径）</h3>
 * <p>本 ratchet 以「接收字段名 == {@code toolRegistry}」为判据匹配 {@code execute}
 * 咽喉（源码无类型信息，不能按接收者类型断言；{@code execute} 名字被 TaskExecutor/
 * MissionEngine/OagPipeline 等大量同名方法占用，字节码/ArchUnit 断言会假阳性爆表，
 * 故采用源码精确串匹配）。若未来有人用<b>异名</b>局部变量持有 {@code ToolRegistry}
 * 并直接 {@code myRegistry.execute(...)} 绕过，本 ratchet 抓不到 —— 该形态靠
 * F06-01③ 的「{@code *ToolExecutor#executeBuiltin} 禁止他类调用」ArchUnit 字节码规则
 * 兜底（批次内随咽喉改造同批落）。</p>
 */
class ToolChokepointArchTest {

    // ── 冻结基线：2026-10-04 实测（main 侧，去注释后，除 ToolExecutorService.java 外） ──
    // toolRegistry.execute( — 外咽喉调用（咽喉类内部 :129 属正确执行点，排除）
    private static final int BASELINE_TOOLREG_EXECUTE = 0;
    // executeBuiltin( — 外咽喉派发调用（咽喉类内部 :468 属正确派发点，排除；
    //   AgentDelegationService:20 是 Javadoc {@link ToolExecutorService#executeBuiltin}，
    //   pattern 要求紧邻 `(` 故不命中）
    private static final int BASELINE_EXECUTEBUILTIN = 0;

    private static final Pattern TOOLREG_EXECUTE =
            Pattern.compile("\\btoolRegistry\\s*\\.\\s*execute\\s*\\(");
    /** 要求 {@code executeBuiltin} 紧跟 `(`（Javadoc {@link ...#executeBuiltin} 无 `(` 不命中）。 */
    private static final Pattern EXECUTEBUILTIN_CALL =
            Pattern.compile("\\bexecuteBuiltin\\s*\\(");

    private static final Path AI_IMPL_MAIN =
            resolveBackendRoot().resolve(
                    "engine/ai-engine/ai-engine-impl/src/main/java");
    private static final String CHOKEPOINT_FILENAME = "ToolExecutorService.java";

    /** 向上探测 ecos_backend 根（与同族 SelfScheduleArchTest 一致约定）。 */
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
    @DisplayName("F06-01⑤ noBypassOfToolExecutorService — 除咽喉类外无 toolRegistry.execute / executeBuiltin 直接调用")
    void noBypassOfToolExecutorService() throws IOException {
        int toolregExec = countOutsideChokepoint(TOOLREG_EXECUTE);
        int builtinCall = countOutsideChokepoint(EXECUTEBUILTIN_CALL);

        Assertions.assertTrue(toolregExec <= BASELINE_TOOLREG_EXECUTE,
                "F06-01⑤ 检测到咽喉外 toolRegistry.execute 直接调用：实测 " + toolregExec
                        + " > 基线 " + BASELINE_TOOLREG_EXECUTE
                        + "（X-19 双入口面：任何工具执行必须经 ToolExecutorService.execute 咽喉）");
        Assertions.assertTrue(builtinCall <= BASELINE_EXECUTEBUILTIN,
                "F06-01⑤ 检测到咽喉外 executeBuiltin 直接调用：实测 " + builtinCall
                        + " > 基线 " + BASELINE_EXECUTEBUILTIN
                        + "（X-20 BUILTIN 派发仅能经 ToolExecutorService 内部完成）");
        System.out.println("[ToolChokepointArch] outside-chokepoint main-side — "
                + "toolRegistry.execute: " + toolregExec + "/" + BASELINE_TOOLREG_EXECUTE
                + "  executeBuiltin call: " + builtinCall + "/" + BASELINE_EXECUTEBUILTIN);
    }

    /** 扫 ai-engine-impl main，只统计<b>咽喉类文件之外</b>的命中（去掉注释按行）。 */
    private int countOutsideChokepoint(Pattern pattern) throws IOException {
        int count = 0;
        try (Stream<Path> walk = Files.walk(AI_IMPL_MAIN)) {
            for (Path p : walk.filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> !f.getFileName().toString().equals(CHOKEPOINT_FILENAME))
                    .toArray(Path[]::new)) {
                String source = Files.readString(p);
                for (String line : source.split("\r?\n")) {
                    if (pattern.matcher(stripComments(line)).find()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** 只去「行内注释」段（行尾 {@code //} 与单行块注释）。跨行 Javadoc 靠「整行以 * 开头则跳」处理。 */
    private static String stripComments(String line) {
        // Javadoc 续行（{@code /** */} 块的中间行）形如 {@code * ...} 或 {@code * @tag ...}，
        // 与代码文本在语义上隔离 —— 整行跳过（否则 ToolRegistry.java 的文件级 Javadoc 里
        // {@code * ToolExecutorService.ToolResult result = toolRegistry.execute(...)}
        // 会误命中外咽喉；本 ratchet 的目的是「代码里咽喉被绕过」，不是文档引用）。
        String trimmed = line.stripLeading();
        if (trimmed.startsWith("*")) {
            return "";
        }
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
