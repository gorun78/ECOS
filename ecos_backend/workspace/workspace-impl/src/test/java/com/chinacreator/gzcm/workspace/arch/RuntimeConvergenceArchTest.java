package com.chinacreator.gzcm.workspace.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 卷07 F07-17（W181 / C163）底座收口验收门禁 —— workspace 自建出口清零。
 *
 * <p>与 gateway {@code ModuleDependencyArchTest}（字节码面，存量基线只减不增）互补，
 * 本类是 workspace 模块<b>源码面</b>的收口断言：收敛完成后，业务代码不得再出现
 * 自建 HTTP 出口 / 裸线程 / common pool 异步原语，端口字面量不得入任何制品：</p>
 *
 * <ol>
 *   <li>{@code new RestTemplate(} —— 业务文件 0 次（{@code workspace/config/} 工厂包除外，
 *       与 gateway 门禁同口径：底座 config 集中构造共享 Bean 是唯一允许形态，
 *       先例 cognitive {@code CognitiveRestClientConfig}）；</li>
 *   <li>{@code new Thread(} —— main 全域 0 次（并发一律走命名执行器）；</li>
 *   <li>{@code CompletableFuture.runAsync(} / {@code supplyAsync(} —— 0 次
 *       （含带 executor 参数形态：workspace 异步统一走 workspaceTaskExecutor）；</li>
 *   <li>{@code localhost:1808*} / {@code localhost:18090} 端口字面量 —— *.java 与 *.yml
 *       均 0 次（基址单真源 = route-manifest + ServiceEndpointResolver，禁个人环境入库）。</li>
 * </ol>
 *
 * <p>口径与 gateway 规则 D/E 一致：剥注释后匹配（Javadoc 里的 {@code new Thread(...)}
 * 属文档陈述，不计违约）。</p>
 */
class RuntimeConvergenceArchTest {

    /** 允许出现 RestTemplate 构造的底座工厂包（gateway 门禁同豁免口径，相对 workspace/ 根）。 */
    private static final String CONFIG_DIR = "config/";

    @Test
    void businessCodeMustNotConstructRestTemplate() throws IOException {
        List<String> hits = scanJavaMain((rel, code) ->
                code.contains("new RestTemplate(") && !rel.contains(CONFIG_DIR));
        assertTrue(hits.isEmpty(),
                "F07-17 违规：workspace 业务代码自建 RestTemplate（必须注入 "
                        + "WorkspaceRestClientConfig 共享 Bean，业务文件 0 自建）: " + hits);
    }

    @Test
    void mustNotSpawnRawThreads() throws IOException {
        List<String> hits = scanJavaMain((rel, code) -> code.contains("new Thread("));
        assertTrue(hits.isEmpty(),
                "F07-17 违规：workspace 源码出现裸 new Thread(（并发一律走 "
                        + "WorkspaceTaskExecutorConfig 命名执行器）: " + hits);
    }

    @Test
    void mustNotUseCommonPoolCompletableFuture() throws IOException {
        List<String> hits = scanJavaMain((rel, code) ->
                code.contains("CompletableFuture.runAsync(") || code.contains("CompletableFuture.supplyAsync("));
        assertTrue(hits.isEmpty(),
                "F07-17 违规：workspace 源码使用 CompletableFuture 异步原语（含 common pool 形态，"
                        + "统一迁命名执行器）: " + hits);
    }

    @Test
    void mustNotHardcodeServicePortLiteralsInJava() throws IOException {
        List<String> hits = scanJavaMain((rel, code) ->
                code.contains("localhost:1808") || code.contains("localhost:18090"));
        assertTrue(hits.isEmpty(),
                "F07-17 违规：workspace 源码硬编码 localhost:1808x/18090 端口字面量"
                        + "（基址单源 route-manifest + ServiceEndpointResolver）: " + hits);
    }

    @Test
    void mustNotHardcodeServicePortLiteralsInYml() throws IOException {
        Path workspaceDir = mainSourceRoot().getParent().getParent().getParent().getParent();
        List<Path> resourceRoots = new ArrayList<>();
        for (String rel : List.of(
                "workspace-impl/src/main/resources",
                "workspace-impl/src/test/resources",
                "workspace-service/src/main/resources",
                "workspace-service/src/test/resources")) {
            Path p = workspaceDir.resolve(rel.replace('/', java.io.File.separatorChar));
            if (Files.isDirectory(p)) resourceRoots.add(p);
        }
        List<String> hits = new ArrayList<>();
        for (Path root : resourceRoots) {
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path f : walk.filter(p -> {
                            String n = p.toString().toLowerCase();
                            return n.endsWith(".yml") || n.endsWith(".yaml");
                        }).toList()) {
                    String text = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
                    for (String line : text.split("\r?\n")) {
                        String t = line.trim();
                        if (t.startsWith("#")) continue; // 注释行=文档陈述
                        if (t.contains("localhost:1808") || t.contains("localhost:18090")) {
                            hits.add(f + ": " + t);
                        }
                    }
                }
            }
        }
        assertTrue(hits.isEmpty(),
                "F07-17 违规：workspace yml 制品硬编码 1808x/18090 端口（个人环境禁止入库，"
                        + "默认值走 ecos.endpoints.* → route-manifest 解析链）: " + hits);
    }

    // ═══════════════ 辅助 ═══════════════

    /** 谓词：(相对路径, 剥注释后的源码) → 是否命中违规。 */
    private interface CodePredicate {
        boolean test(String relPath, String code);
    }

    /** 扫描 workspace-impl src/main/java 全部 .java，剥注释后按谓词收集命中文件。 */
    private static List<String> scanJavaMain(CodePredicate predicate) throws IOException {
        Path srcRoot = mainSourceRoot();
        List<String> hits = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(srcRoot)) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                String code = stripComments(new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
                String rel = relativize(srcRoot, p);
                if (predicate.test(rel, code)) {
                    hits.add(rel);
                }
            }
        }
        return hits;
    }

    /** 剥离行注释与块注释（同 WorkspaceLayeringArchTest 口径）。 */
    private static String stripComments(String src) {
        return src.replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", " ")
                .replaceAll("//[^\\r\\n]*", " ");
    }

    private static Path mainSourceRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            Path cand = p.resolve("workspace/workspace-impl/src/main/java");
            if (Files.isDirectory(cand)) return cand.normalize();
            Path self = p.resolve("src/main/java");
            if (Files.isDirectory(self)
                    && Files.exists(self.resolve("com/chinacreator/gzcm/workspace"))) {
                return self.normalize();
            }
            p = p.getParent();
        }
        throw new IllegalStateException("找不到 workspace-impl/src/main/java，守门测试无法定位源码根");
    }

    private static String relativize(Path root, Path file) {
        Path rel = root.resolve("com/chinacreator/gzcm/workspace").relativize(file);
        return rel.toString().replace('\\', '/');
    }
}
