package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-06 §八 接缝义务 2 —— <b>单一 Runtime / 禁双套 Agent Loop</b>（ADR-19 §2.2，随 R-39）。
 *
 * <p>本册（ai-engine + llm-gateway）必须持有<b>唯一</b>的 Agent Loop 实现
 * （think→act→observe 循环）；其余域（W Agent 编排 / Hermes 外部 provider）只能经
 * {@code AgentRuntimeProvider} SPI 注入"部件"（memory/planner/executor/toolRouter/mcp/evolution），
 * <b>不得</b>各自再写一套循环。
 *
 * <p>守卫判据（源扫描，抽掉注释后）：全 reactor 主源码里，以
 * {@code AgentLoopResult run(AgentLoopConfig ...)} 形态声明的公有循环入口<b>恰好 1 处</b>，
 * 且位于唯一承主类 {@code AgentLoopService}。出现第二处即"双套 loop"复发（接缝违约）。</p>
 *
 * <p>验收：{@code mvn -Dtest=AgentRuntimeUniquenessTest#onlyOneLoopImplementationInReactor}</p>
 */
public class AgentRuntimeUniquenessTest {

    /** 循环唯一承主类（简化类名）：唯一 Agent Loop 实现必须落在此类。 */
    private static final String SOLE_LOOP_CLASS = "AgentLoopService";

    /** 循环驱动方法返回类型（唯一定义类）。 */
    private static final Pattern LOOP_ENTRY_SIG =
            Pattern.compile("\\bAgentLoopResult\\s+run\\s*\\(\\s*AgentLoopConfig\\b");

    /**
     * D25（2026-11-13 验证）：本门原按「{@code AgentLoopResult run(AgentLoopConfig ...} 签名出现次数」
     * 计数，而 AgentLoopService 自 f8748c45 起有 <b>同一类内的两个重载</b>：
     * {@code run(config,msg,session)} 委托给 {@code run(config,msg,session,deltaSink)}，
     * 后者再委托给唯一实现 {@code doRun(...)}。两者是同一套 loop 的入口重载，不是「双套 loop」，
     * 但旧口径把它们计成 ×2 ⇒ 恒红。
     *
     * <p>现改为<b>按类去重</b>：junction 的真实语义是「循环实现只能落在一个类里」。
     * 同一类的多个重载 / 委托入口不再触发；跨类出现第二处入口仍然失败（原防线不变）。</p>
     */
    @Test
    void onlyOneLoopImplementationInReactor() throws IOException {
        Path backend = backendRoot();
        List<Path> mainSources = mainSources(backend);
        assertTrue(!mainSources.isEmpty(), "未找到任何主源码，后端根定位异常: " + backend);

        List<String> classesWithLoopEntry = new ArrayList<>();
        for (Path src : mainSources) {
            String rel = backend.relativize(src).toString();
            if (rel.contains("AgentLoopResult")) {
                // 结果值对象自身（工厂方法）不参与"循环入口"计数
                continue;
            }
            String executable = stripComments(new String(Files.readAllBytes(src), StandardCharsets.UTF_8));
            boolean hasLoopEntry = false;
            for (String line : executable.split("\n")) {
                if (LOOP_ENTRY_SIG.matcher(line).find()) {
                    hasLoopEntry = true;
                    break;
                }
            }
            if (hasLoopEntry) {
                classesWithLoopEntry.add(rel);
            }
        }

        assertEquals(1, classesWithLoopEntry.size(),
                "Agent Loop 实现必须唯一（junction 义务 2 禁双套 loop）：期望恰 1 个类承载循环入口"
                        + "（同一类的重载入口不算第二套），实得 " + classesWithLoopEntry.size()
                        + " 个类：" + classesWithLoopEntry);

        assertTrue(classesWithLoopEntry.get(0).contains(SOLE_LOOP_CLASS),
                "唯一 Agent Loop 必须落在 " + SOLE_LOOP_CLASS + "，实得：" + classesWithLoopEntry);
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static List<Path> mainSources(Path backend) throws IOException {
        try (Stream<Path> walk = Files.walk(backend)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .sorted()
                    .toList();
        }
    }

    private static Path backendRoot() {
        Path cur = Paths.get("").toAbsolutePath();
        while (cur != null) {
            if (Files.isDirectory(cur.resolve("gateway")) && Files.isRegularFile(cur.resolve("pom.xml"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("定位不到 ecos_backend 根（pom.xml + gateway/）");
    }

    /** 剥除 Java 块注释与行注释，保留字符串/字符字面量原文（签名匹配不依赖字符串内容，故可全剥）。 */
    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            char next = i + 1 < n ? src.charAt(i + 1) : 0;
            if (c == '/' && next == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && next == '*') {
                i += 2;
                while (i < n && !(src.charAt(i) == '*' && i + 1 < n && src.charAt(i + 1) == '/')) {
                    if (src.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
                i = i + 2 <= n ? i + 2 : n;
            } else if (c == '"') {
                out.append(c);
                i++;
                while (i < n) {
                    char sc = src.charAt(i);
                    out.append(sc);
                    if (sc == '\\') {
                        if (i + 1 < n) {
                            out.append(src.charAt(i + 1));
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    i++;
                    if (sc == '"') {
                        break;
                    }
                }
            } else if (c == '\'') {
                out.append(c);
                i++;
                while (i < n) {
                    char cc = src.charAt(i);
                    out.append(cc);
                    if (cc == '\\') {
                        if (i + 1 < n) {
                            out.append(src.charAt(i + 1));
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    i++;
                    if (cc == '\'') {
                        break;
                    }
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
