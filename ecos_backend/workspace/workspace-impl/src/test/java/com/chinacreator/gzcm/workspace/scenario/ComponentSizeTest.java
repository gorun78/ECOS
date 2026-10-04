package com.chinacreator.gzcm.workspace.scenario;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V07 F07-25 前端组件单文件 ≤800 行 反锚点（对应设计 §8.1 「前端 D 批 · 属主分册 08」
 * 的**离线半**：行数红线不依赖 P-3 Playwright / E2E / 前端打标签，纯扫描即可离线证伪）。
 *
 * 设计线：《详细设计-07》line 651 `ComponentSizeTest#noComponentExceeds800Lines`
 *   「对 page/scenario* 全量行数断言」。
 *
 * 反虚假分置：真实 UI 验收（主题 / i18n / 移动端）由前端分册 08 P-3 承接；
 * 本条只守「单文件 ≤800 行」的文件规模红线，防前端分册改造被 long-file 拖走——
 * 一旦某文件超 800 即红，直接指名文件名 + 行数 → 可离线证伪。
 */
class ComponentSizeTest {

    /** 设计 line 651 标注的红线阈值。 */
    private static final int MAX_COMPONENT_LINES = 800;

    @Test
    void noComponentExceeds800Lines() throws IOException {
        Path pagesRoot = monorepoRoot().resolve("ecos_frontend/src/pages");
        assertTrue(Files.isDirectory(pagesRoot),
                "ecos_frontend/src/pages 找不到（monorepo 结构漂移？）: " + pagesRoot);

        List<String> violations = new ArrayList<>();
        for (String sub : new String[]{"scenario", "scenario-sandbox"}) {
            Path dir = pagesRoot.resolve(sub);
            if (!Files.isDirectory(dir)) {
                continue; // 该子目录尚未原子创建时不造假红
            }
            Files.walk(dir)
                    .filter(p -> p.toString().endsWith(".tsx") || p.toString().endsWith(".ts"))
                    .forEach(p -> {
                        try {
                            long lines = Files.lines(p).count();
                            if (lines > MAX_COMPONENT_LINES) {
                                violations.add(dir.relativize(p) + " lines=" + lines);
                            }
                        } catch (IOException ignored) {
                        }
                    });
        }
        assertTrue(violations.isEmpty(),
                "以下场景域前端文件超过 " + MAX_COMPONENT_LINES + " 行：\n  "
                        + String.join("\n  ", violations));
    }

    /** 从 workspace/workspace-impl 上溯三级到 ECOS monorepo 根；支持 ECOS_ROOT 覆盖。 */
    private static Path monorepoRoot() {
        String override = System.getenv("ECOS_ROOT");
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        Path here = Paths.get("").toAbsolutePath();
        // workspace-impl → workspace → ecos_backend → ECOS repo root
        return here.getParent().getParent().getParent();
    }
}
