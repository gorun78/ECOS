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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F04-17 前端工程约束落地门禁（B.9：组件 ≤ 800 行）。
 *
 * <p>本门禁从 kb 引擎侧扫描**前端** {@code ecos_frontend/src/pages/knowledge/**} 下所有
 * 组件源文件（{@code .tsx} / {@code .ts}，排除 {@code .test.*} 与 {@code *.i18n.test.*}），
 * 断言任一组件行数 ≤ 800（铁律 · 前端规范组件 ≤ 800 行）。K-58（ClassificationTab 915 /
 * GraphExplorerTab 810）已在本册拆分为多段（≤750），本测试守住不回升。
 *
 * <p>纯文件读取，不启动前端、不联网；surviveCWD：由 surefire 模块目录向仓库根
 * 递归 10 级定位 {@code ecos_frontend/src/pages/knowledge}。
 *
 * @author ECOS KB Team
 */
public class FrontendBudgetGateTest {

    /** 前端组件行数硬上限（铁律 §前端：每 Tab / 每组件 ≤ 800 行）。 */
    private static final int MAX_LINES = 800;

    /** 知识域前端根：{@code <repo>/ecos_frontend/src/pages/knowledge}。 */
    private static final Path KNOWLEDGE_FRONTEND_ROOT = resolveKnowledgeFrontendRoot();

    @Test
    @DisplayName("F04-17: 知识域前端任一组件 ≤ 800 行（B.9 拆段防回升）")
    void noKnowledgeComponentExceeds800Lines() throws IOException {
        assertTrue(Files.isDirectory(KNOWLEDGE_FRONTEND_ROOT),
                "未定位到知识域前端根目录: " + KNOWLEDGE_FRONTEND_ROOT);

        List<pathLineCount> offenders = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> s = Files.walk(KNOWLEDGE_FRONTEND_ROOT)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                String fn = p.getFileName().toString();
                if (!(fn.endsWith(".tsx") || fn.endsWith(".ts"))) {
                    continue;
                }
                if (fn.endsWith(".test.tsx") || fn.endsWith(".test.ts") || fn.endsWith(".i18n.test.tsx")) {
                    continue;
                }
                scanned++;
                List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                if (lines.size() > MAX_LINES) {
                    offenders.add(new pathLineCount(
                            KNOWLEDGE_FRONTEND_ROOT.relativize(p).toString().replace('\\', '/'),
                            lines.size()));
                }
            }
        }
        assertTrue(scanned > 0, "反空扫描失败：未在任何知识域前端目录下扫到组件源文件");
        assertTrue(offenders.isEmpty(),
                "以下知识域前端组件超过 " + MAX_LINES + " 行（铁律 · 组件 ≤ 800 行，B.9 要求四段拆分）：\n  "
                        + offenders.stream().map(o -> o.path + " → " + o.lines + " 行")
                        .reduce((a, b) -> a + "\n  " + b).orElse(""));
    }

    /** 从 surefire 工作目录向上递归定位 {@code ecos_frontend/src/pages/knowledge}。 */
    private static Path resolveKnowledgeFrontendRoot() {
        Path cur = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 10 && cur != null; i++) {
            Path candidate = cur.resolve("ecos_frontend/src/pages/knowledge").normalize();
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            cur = cur.getParent();
        }
        throw new AssertionError(
                "未找到 ecos_frontend/src/pages/knowledge；从 "
                        + Path.of("").toAbsolutePath().normalize() + " 向上递归 10 级均失败");
    }

    /** 违规组件的相对路径与行数（用于失败信息）。 */
    private record pathLineCount(String path, int lines) {}
}
