package com.chinacreator.gzcm.engine.kb.route;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F04-01/02 路由完整性验收（F04-04 五枚举反证）— 纯单元样式，无 Spring 上下文。
 *
 * <p>锁定四件事：
 * <ol>
 *   <li>{@code knowledge-bases} 双路径（v1 + 裸）并存（API 只增不改）；</li>
 *   <li>knowledge 前缀唯一所属 kb 引擎，工作台旧路径迁到 {@code /api/v1/scenarios/knowledge} 且已 {@code @Deprecated}；</li>
 *   <li>dev 代理 {@code /api/v1/knowledge} 指向 kb(:18086) 而非 ai(:18084)；</li>
 *   <li>方法级 mapping 值不再叠加 {@code /api/}（防 class 前缀 + 方法绝对路径的双重嵌套）。</li>
 * </ol>
 */
class KnowledgeRouteIntegrityTest {

    /** 随 kb-engine-impl 类路径可用的 knowledge 家族 kb 控制器 FQN 基线。 */
    private static final String[] KB_CONTROLLER_FQNS = {
            "com.chinacreator.gzcm.engine.kb.controller.EntityLinkController",
            "com.chinacreator.gzcm.engine.kb.controller.KnowledgeSettingsController",
            "com.chinacreator.gzcm.engine.kb.controller.KnowledgeListController",
    };

    @Test
    @DisplayName("knowledge-bases 类的 @RequestMapping 必须同时含 v1 与裸两条路径")
    void everyKnowledgeMappingHasV1AndBarePair() throws Exception {
        Class<?> clz = Class.forName("com.chinacreator.gzcm.engine.kb.controller.KnowledgeListController");
        RequestMapping rm = clz.getAnnotation(RequestMapping.class);
        assertNotNull(rm, "KnowledgeListController 应带类级 @RequestMapping");
        List<String> paths = new ArrayList<>();
        addStrings(rm.value(), paths);
        addStrings(rm.path(), paths);
        assertTrue(paths.contains("/api/v1/knowledge-bases"),
                "必须保留 v1 正典路径 /api/v1/knowledge-bases，实际：" + paths);
        assertTrue(paths.contains("/api/knowledge-bases"),
                "API 只增不改：应新增裸路径 /api/knowledge-bases，实际：" + paths);
    }

    @Test
    @DisplayName("knowledge 前缀唯一所属 kb 引擎 + 工作台迁移到 scenarios/knowledge 且 @Deprecated")
    void knowledgePrefixHasSingleOwningModule() throws Exception {
        // 1) 已知 kb 控制器 FQN 全部落在 com.chinacreator.gzcm.engine.kb 包
        for (String fqn : KB_CONTROLLER_FQNS) {
            Class<?> c = Class.forName(fqn);
            String pkg = c.getPackage() != null ? c.getPackage().getName() : "";
            assertTrue(pkg.startsWith("com.chinacreator.gzcm.engine.kb"),
                    fqn + " 应归属 kb 引擎包 com.chinacreator.gzcm.engine.kb，实际：" + pkg);
        }

        // 2) 工作台旧路径（workspace 模块，不在 kb 类路径上）改走源码文本断言：
        //    必须含场景路径 /api/v1/scenarios/knowledge 且已 @Deprecated
        Path src = locateRepoRoot()
                .resolve("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/knowledge/KnowledgeWorkbenchController.java");
        assertTrue(Files.isRegularFile(src), "未找到 KnowledgeWorkbenchController 源码：" + src);
        String text = Files.readString(src);
        assertTrue(text.contains("/api/v1/scenarios/knowledge"),
                "工作台应新增场景迁移路径 /api/v1/scenarios/knowledge");
        assertTrue(text.contains("@Deprecated"),
                "工作台旧路径 handler 应整体 @Deprecated（含类或方法）");
    }

    @Test
    @DisplayName("dev 代理 /api/v1/knowledge 指向 kb(:18086)，而非 ai(:18084) — 事实源为 route-manifest 单源")
    void devProxyTargetsKbPort() throws IOException {
        // 2026-10-10 详设-08 W194/C176（R-54 ①）把 dev 代理表从 vite.config.ts 迁入
        // config/route-manifest.ts 单源，本断言的读取面随之改到该单源；意图不变：
        // knowledge 前缀唯一所属 kb 引擎、端口 18086、不得回落到 ai 的 18084。
        Path vite = locateRepoRoot().resolve("ecos_frontend/vite.config.ts");
        assertTrue(Files.isRegularFile(vite), "未找到前端 vite.config.ts：" + vite);
        List<String> viteLines = Files.readAllLines(vite);
        assertTrue(viteLines.stream().anyMatch(l -> l.contains("buildViteDevProxyTable")),
                "vite.config.ts 必须由 route-manifest 派生 dev 代理表（W194 单源），实际未见 buildViteDevProxyTable");
        assertTrue(viteLines.stream().anyMatch(l -> l.contains("config/route-manifest")),
                "vite.config.ts 必须从 ./config/route-manifest 导入，实际未导入");
        assertTrue(viteLines.stream().anyMatch(l -> l.contains("proxy: devProxyTable")),
                "vite.config.ts 的 server.proxy 必须由单源派生的 devProxyTable 提供（W194 单源），实际未见");
        assertTrue(viteLines.stream().noneMatch(l -> l.matches(".*target:\\s*.*180\\d\\d.*")),
                "vite.config.ts 不得再手写在用服务端口 target，代理目标只准来自 route-manifest 单源");

        Path manifest = locateRepoRoot().resolve("ecos_frontend/config/route-manifest.ts");
        assertTrue(Files.isRegularFile(manifest), "未找到路由单源：" + manifest);
        List<String> knowledgeEntries = Files.readAllLines(manifest).stream()
                .filter(l -> l.contains("prefix: \"/api/v1/knowledge\""))
                .toList();
        assertEquals(1, knowledgeEntries.size(),
                "/api/v1/knowledge 在单源里必须恰有一条（禁双写漂移），实际：" + knowledgeEntries);
        String line = knowledgeEntries.get(0);
        assertTrue(line.contains("engine: \"kb\""), "knowledge 前缀应归属 kb 引擎，实际行：" + line.trim());
        assertTrue(line.contains("port: 18086"), "knowledge dev 代理应指向 18086，实际行：" + line.trim());
        assertTrue(!line.contains("18084"), "knowledge dev 代理不得仍指向 ai 的 18084，实际行：" + line.trim());
    }

    @Test
    @DisplayName("kb 控制器的方法级 mapping 值不得再含 /api/（防双重 /api/v1 嵌套）")
    void noNestedApiPrefixInHandlerMapping() throws Exception {
        for (String fqn : KB_CONTROLLER_FQNS) {
            Class<?> c = Class.forName(fqn);
            for (Method m : c.getMethods()) {
                List<String> values = new ArrayList<>();
                GetMapping gm = m.getAnnotation(GetMapping.class);
                if (gm != null) { addStrings(gm.value(), values); addStrings(gm.path(), values); }
                PostMapping pm = m.getAnnotation(PostMapping.class);
                if (pm != null) { addStrings(pm.value(), values); addStrings(pm.path(), values); }
                PutMapping putm = m.getAnnotation(PutMapping.class);
                if (putm != null) { addStrings(putm.value(), values); addStrings(putm.path(), values); }
                DeleteMapping dm = m.getAnnotation(DeleteMapping.class);
                if (dm != null) { addStrings(dm.value(), values); addStrings(dm.path(), values); }
                PatchMapping patchm = m.getAnnotation(PatchMapping.class);
                if (patchm != null) { addStrings(patchm.value(), values); addStrings(patchm.path(), values); }
                RequestMapping rm = m.getAnnotation(RequestMapping.class);
                if (rm != null) { addStrings(rm.value(), values); addStrings(rm.path(), values); }
                for (String v : values) {
                    assertTrue(!v.contains("/api/"),
                            fqn + "#" + m.getName() + " 的方法路径不得绝对嵌套 /api/ 前缀，实际：" + v);
                }
            }
        }

        // 显式断言 EntityLinkController 已收敛为相对路径 "link"
        Class<?> elc = Class.forName("com.chinacreator.gzcm.engine.kb.controller.EntityLinkController");
        boolean hasRelativeLink = false;
        for (Method m : elc.getMethods()) {
            PostMapping pm = m.getAnnotation(PostMapping.class);
            if (pm != null) {
                List<String> vals = new java.util.ArrayList<>();
                addStrings(pm.value(), vals);
                addStrings(pm.path(), vals);
                for (String v : vals) {
                    if ("link".equals(v)) { hasRelativeLink = true; }
                }
            }
        }
        assertTrue(hasRelativeLink, "EntityLinkController 方法路径应收敛为相对 'link'（新有效路径 <prefix>/link）");
    }

    // ── 辅助 ─────────────────────────────────────────

    private static void addStrings(String[] arr, List<String> out) {
        if (arr != null) { out.addAll(Arrays.asList(arr)); }
    }

    /** 从运行目录向上遍历，定位同时含 ecos_frontend 与 ecos_backend 的仓库根。 */
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
