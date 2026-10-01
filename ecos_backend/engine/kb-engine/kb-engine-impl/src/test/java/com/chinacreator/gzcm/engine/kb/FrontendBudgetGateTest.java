package com.chinacreator.gzcm.engine.kb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * F04-17 前端知识域工程约束门禁（详细设计-04 B.9 "组件 ≤800 行"）。
 *
 * <p>扫描 {@code ecos_frontend/src/pages/knowledge/**} 的 .ts/.tsx（排除 *.test.*），
 * 断言无单文件超过 800 行 —— 防止知识域组件再次膨胀为不可维护的单文件巨物
 * （K-58：ClassificationTab 915 / GraphExplorerTab 810）。
 *
 * <p>路径凭 user.dir 向上遍历定位同时含 ecos_frontend 与 ecos_backend 的仓库根，
 * 与 {@code KnowledgeRouteIntegrityTest#locateRepoRoot} 同口径；目录缺失 = 重构未落地，直接 fail-loud。
 */
@DisplayName("F04-17 前端知识域组件 ≤800 行预算门禁")
class FrontendBudgetGateTest {

    private static final int MAX_LINES = 800;

    @Test
    @DisplayName("知识域所有组件 ≤800 行")
    void noKnowledgeComponentExceeds800Lines() throws IOException {
        Path root = locateRepoRoot();
        Path knowledgeRoot = root.resolve("ecos_frontend").resolve("src").resolve("pages").resolve("knowledge");
        if (!Files.isDirectory(knowledgeRoot)) {
            fail("前端知识域目录不存在: " + knowledgeRoot
                    + "（F04-17 B 章重构落地前置未达成）");
        }
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(knowledgeRoot)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> {
                    String n = p.getFileName().toString();
                    return (n.endsWith(".ts") || n.endsWith(".tsx")) && !n.contains(".test.");
                })
                .forEach(p -> {
                    try {
                        long lines = Files.readAllLines(p).size();
                        if (lines > MAX_LINES) {
                            offenders.add(p + " : " + lines + " 行（> " + MAX_LINES + "）");
                        }
                    } catch (IOException ignored) {
                        // 读文件失败不记入违例（由其它 CI 环节兜底）
                    }
                });
        }
        assertTrue(offenders.isEmpty(),
                "F04-17 B.9 组件 ≤800 行门禁违反：\n"
                        + String.join("\n", offenders)
                        + "\n 修正方向：按容器/展示/表格/表单四段拆分，每 Tab 独立文件。");
    }

    private static Path locateRepoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 12 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("ecos_frontend"))
                    && Files.isDirectory(dir.resolve("ecos_backend"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "无法定位仓库根：自 user.dir 向上 12 级未找到同时含 ecos_frontend 与 ecos_backend 的目录");
    }
}
