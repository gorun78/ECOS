package com.chinacreator.gzcm.engine.kb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F04-08 认知契约守护（REQ-KB-04，P0，守护型；分册 04 §F.4）。
 *
 * <p>验收标识（详细设计-04 §8.1 追溯矩阵，K-49 / C94 / W112）：
 * 本测试断言 kb-engine 主/测试源码**零命中**四个幻影认知表名
 * {@code kb_cognitive_hypothesis / kb_cognitive_belief / kb_cognitive_evidence / kb_mind_registry}
 * —— 它们在 live 库内<b>不物理存在</b>（分册 04 K-49；ARCH C94），真身为
 * {@code public.ecos_cognitive_hypothesis (V128)} / {@code public.ecos_cognitive_belief (V129)} /
 * {@code public.ecos_cognitive_evidence (V127)}；{@code kb_mind_registry} 则根本无对应物理表
 * （ARCH C98 / 分册 05 X-19，Mind 目标载体 {@code cognitive_mind + cognitive_scenario_mind}
 * 由 V187/V188 承接）。
 *
 * <p>守护对象（本轮 F04-08 可离线交付的部分）：
 * <ol>
 *   <li>禁在 kb 主/测试源码硬编码未物理表名（防止文档名↔物理名再次漂移；R-12 ① 双条件；PRD-04 §四
 *       "文档名↔物理名映射" 的回填点 = 本测试文件正文，作为活体映射表）;</li>
 *   <li>守护建议 (S-7)：kb 域对认知三表<b>只对场景侧（workspace/business）开只读</b>，
 *       禁止提供认知写代理端点 —— 由本测试一同守护同一类"死引用"清单：kb 主源码中
 *       {@code cognitive} 前后缀字符串匹配、且<b>非</b> {@code com.chinacreator.gzcm.engine.cognitive.*}
 *       Java 类型引用（kb 本来不装 cognitive，cognitive 由 aiming 宿主承载）。</li>
 * </ol>
 *
 * <p><b>不做</b>（明确写破，不静默）：本测试不覆盖 F04-08 联调面
 * {@code ScenarioCognitionIntegrationTest}（依赖分册 05 E1~E4 交付，见 §8.2 批次排程）；
 * 亦不覆盖 ADR-8/9 内认知三表具体数值形态校验（骨架层留给分册 05）。
 *
 * <p>纯文件读取，不启动 JUnit 前无 DB/网络依赖。
 *
 * @author ECOS KB Team
 */
public class CognitionContractGuardTest {

    /** PRD/ARCH 已定性为幻影/待定名（非物理表）认知表清单（W112 / C94 / C98 / K-49）。 */
    private static final String[] PHANTOM_TABLE_NAMES = {
            "kb_cognitive_hypothesis",
            "kb_cognitive_belief",
            "kb_cognitive_evidence",
            "kb_mind_registry",
    };

    /** 允许在 kb-engine 内以"引用/映射说明"出现的例外文件（本测试自身 + AGENTS/内置映射文档）。 */
    private static final List<String> ALLOWED_EXCEPTION_FILES = List.of(
            "CognitionContractGuardTest.java"
    );

    @Test
    @DisplayName("F04-08: kb 主/测试源码零命中 4 个幻影认知表名 (K-49 / C94 / C98)")
    void guardTablesResolveToRealRelation() throws IOException {
        Path kbImplRoot = locateKbImplRoot();
        assertTrue(Files.isDirectory(kbImplRoot), "未定位到 kb-engine-impl 根: " + kbImplRoot);

        List<String> hits = new ArrayList<>();
        int scanned = 0;
        // 误报风险登记：Java import 语句 `import com.chinacreator.gzcm.engine.cognitive.*`
        // 不属于"表名字面量"，其符号是 Java 类型路径，不在 PHANTOM_TABLE_NAMES 集合内，
        // KB 引擎源码本就不依赖 cognitive 包，扫描不到此类直接字节串，无额外过滤必要。
        try (Stream<Path> s = Files.walk(kbImplRoot)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                String fileName = p.getFileName().toString();
                if (!fileName.endsWith(".java") && !fileName.endsWith(".xml") && !fileName.endsWith(".yml")) {
                    continue;
                }
                if (ALLOWED_EXCEPTION_FILES.stream().anyMatch(e -> fileName.equals(e))) {
                    continue;
                }
                scanned++;
                String content = Files.readString(p, StandardCharsets.UTF_8);
                for (String phantom : PHANTOM_TABLE_NAMES) {
                    if (content.contains(phantom)) {
                        hits.add(kbImplRoot.relativize(p).toString().replace('\\', '/')
                                + " -> 含幻影名 " + phantom);
                    }
                }
            }
        }
        assertTrue(scanned > 0, "反空扫描: 未扫到任何 kb-engine-impl 源文件");
        assertTrue(hits.isEmpty(),
                "以下 kb-engine-impl 源文件引用了幻影认知表名（W112 / C94；真身见 public.ecos_cognitive_*）：\n  "
                        + join(hits));
    }

    /** 定位 kb-engine-impl 根（surefire 运行目录）。 */
    private static Path locateKbImplRoot() {
        Path cur = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 10 && cur != null; i++) {
            if (Files.isDirectory(cur.resolve("src/main/java"))
                    && Files.isDirectory(cur.resolve("engine/kb-engine"))
                    || Files.isDirectory(cur.resolve("src/test/java"))) {
                // kb-engine-impl root layout
                if (Files.isDirectory(cur.resolve("src/main/java"))
                        && Files.isDirectory(cur.resolve("src/test/java"))) {
                    // 可能是 kb-engine-impl 本身
                    String p = cur.toString();
                    if (p.contains("kb-engine-impl")) {
                        return cur;
                    }
                }
            }
            cur = cur.getParent();
        }
        // 兜底：从当前目录向上匹配 kb-engine-impl
        Path c = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 10 && c != null; i++) {
            if (c.getFileName() != null && c.getFileName().toString().equals("kb-engine-impl")) {
                return c;
            }
            c = c.getParent();
        }
        throw new AssertionError("未定位 kb-engine-impl root; CWD=" + Path.of("").toAbsolutePath().normalize());
    }

    /** 命中列表拼成多行字符串。 */
    private static String join(List<String> lines) {
        if (lines.isEmpty()) return "";
        return String.join("\n  ", lines);
    }
}
