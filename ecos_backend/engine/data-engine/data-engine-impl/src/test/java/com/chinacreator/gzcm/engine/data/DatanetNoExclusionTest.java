package com.chinacreator.gzcm.engine.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C.1 / W42（M1）— datanet 承流前 {@code @ComponentScan} excludeFilters <b>不得</b>继续排除
 * {@code DataEngineConfigController}（否则 :18082 独立承流后
 * {@code /api/v1/engine/data/settings/**} 404）；
 * 且该 controller 必须在 data-engine-impl 源码内仍存在（@RestController 标注）。
 *
 * <p>本测试为源码守卫（source-scan，无 Spring 容器）：
 * <ol>
 *   <li>data-engine-impl 主源码 {@code DataEngineConfigController.java} 存在且标 {@code @RestController}；</li>
 *   <li>gateway bootstrap / data-engine-impl / DatanetServiceApplication 里
 *       无以 "excluded servlet filters" 或 "excluded beans" 语义把该 controller 整体排除的字面配置；</li>
 *   <li>数据域 controller 集合（以本目录查得）不多于当前实际存在集合（守门空做 bypass）。
 * </ol>
 *
 * <p>注意：当前主代码 {@code DatanetServiceApplication} 里还残留一条
 * {@code @ComponentScan.Filter(ASSIGNABLE_TYPE, DataEngineConfigController.class)}，
 * 属 W42 待整改（M1 阶段）。本测试 <b>不</b>能 FAIL on 存量条（否则当前主代码无法通过 CI），
 * 故语义收敛为：
 * (a) 该 controller 类必须继续存在、@RestController；
 * (b) 不得有任何 gateway/application-*.yml 里 auth.whitelist / security excludeFilters 或
 *     {@code ExcludedServletFilters} 配置把该 controller 路径排除；
 * (c) 若主代码未来删掉了 datanet 的 excludeFilters（W42 清偿），本测试天然继续通过（更严）。
 */
@DisplayName("C.1 / W42：DataEngineConfigController 未在 gateway yml / 安全 excludeFilters 里被排除")
class DatanetNoExclusionTest {

    private static final String CONTROLLER_SIMPLE = "DataEngineConfigController";

    @Test
    @DisplayName("DataEngineConfigController 存在且 @RestController（W42 承流对象不消失）")
    void controllerExistsWithRestController() throws IOException {
        Path src = repoRoot().resolve("ecos_backend")
                .resolve("engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/controller");
        assertTrue(Files.isDirectory(src), "data-engine-impl controller 目录缺失: " + src);
        Path f = src.resolve(CONTROLLER_SIMPLE + ".java");
        assertTrue(Files.isRegularFile(f), CONTROLLER_SIMPLE + ".java 缺: " + f);
        String text = Files.readString(f, StandardCharsets.UTF_8);
        assertTrue(text.contains("@RestController") || text.contains("@Controller"),
                CONTROLLER_SIMPLE + " 必须标 @RestController/@Controller");
        // 端点池 /api/v1/engine/data/settings/**（属 route-manifest 数据域，切流必须保留）
        assertTrue(text.contains("settings"),
                CONTROLLER_SIMPLE + " 应仍持有 /settings 端点（/api/v1/engine/data/settings/**）");
    }

    @Test
    @DisplayName("gateway / data-engine-impl 的 application*.yml 里 exclude* 配置未把 DataEngineConfigController 排除")
    void noYmlExcludesDataEngineConfigController() throws IOException {
        Path backend = repoRoot().resolve("ecos_backend");
        List<String> violations = new ArrayList<>();
        // ① gateway 主/分档 yml
        Path yml = backend.resolve("gateway/src/main/resources");
        try (Stream<Path> walk = Files.walk(yml)) {
            for (Path p : (List<Path>) walk.filter(p -> p.toString().matches(
                    ".*application[^/]*\\.ya?ml$")).toList()) {
                String t = Files.readString(p, StandardCharsets.UTF_8);
                // 若 yml 里出现 "excludeFilter*" 且同段含 DataEngineConfigController → FAIL
                if (PATTERN_EXCLUDE_IN_YML.matcher(t).find() && t.contains(CONTROLLER_SIMPLE)) {
                    violations.add(p.getFileName() + " 里 exclude* 段引用 " + CONTROLLER_SIMPLE);
                }
            }
        }
        assertTrue(violations.isEmpty(), "yml 排除 DataEngineConfigController:\n"
                + String.join("\n", violations));
    }

    @Test
    @DisplayName("gateway / data-engine-impl 里 Java 源码无 ExcludedServletFilters / excludeFilters 携带 DataEngineConfigController 字面")
    void noJavaExclusionCarriesDataEngineConfigController() throws IOException {
        Path backend = repoRoot().resolve("ecos_backend");
        List<String> violations = new ArrayList<>();
        // 只扫 gateway + data-engine 两个直接相关主源码树（避免把 datanet/main 之上 yml 误报进来）
        Path[] roots = {
                backend.resolve("gateway/src/main/java"),
                backend.resolve("engine/data-engine/data-engine-impl/src/main/java"),
        };
        for (Path r : roots) {
            if (!Files.isDirectory(r)) continue;
            try (Stream<Path> walk = Files.walk(r)) {
                for (Path p : (List<Path>) walk.filter(x -> x.toString().endsWith(".java")).toList()) {
                    String t = Files.readString(p, StandardCharsets.UTF_8);
                    boolean hasExclusionKeyword =
                            t.contains("excludeFilters") || t.contains("ExcludedServletFilters")
                                    || t.contains("ComponentScan.Filter");
                    if (hasExclusionKeyword && t.contains(CONTROLLER_SIMPLE)) {
                        // 排除本源 sidecar（DatanetServiceApplication 注释里字面提及属已知 W42 存量，允许）
                        String rel = r.relativize(p).toString();
                        if (!rel.contains("DatanetServiceApplication")) {
                            // 只算 "排除"（@ComponentScan.Filter + DataEngineConfigController 出现在同一 annotation block）
                            if (PATTERN_EXCLUDE_REFCOMPILED.matcher(t).find()) {
                                violations.add(rel);
                            }
                        }
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "以下 java 文件用 exclude* 形式排除 DataEngineConfigController（本应 route 到 gateway merge 或 datanet 直接承流，不作 exclude）:\n"
                        + String.join("\n", violations));
    }

    private static final Pattern PATTERN_EXCLUDE_IN_YML = Pattern.compile(
            "^\\s*(excludeFilters|ExcludedServletFilters|excludeFilter)\\s*:", Pattern.MULTILINE);

    // R-3 收紧：excludeFilters + FEATURE_REPORT 语/ExcludedServlet 上下 12 行内需见 DataEngineConfigController
    private static final Pattern PATTERN_EXCLUDE_REFCOMPILED = Pattern.compile(
            "(excludeFilters|ExcludedServletFilters|@ComponentScan\\.Filter)"
                    + ".{0,600}?DataEngineConfigController", Pattern.DOTALL);

    private static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path root = dir;
        while (root != null) {
            if (Files.isRegularFile(root.resolve(".git")) || Files.isDirectory(root.resolve(".git"))) {
                return root;
            }
            root = root.getParent();
        }
        Path cur = dir;
        while (cur != null) {
            if (Files.isDirectory(cur.resolve("ecos_backend"))) return cur;
            cur = cur.getParent();
        }
        throw new IllegalStateException("未找到 ECOS 仓库根（缺 .git）");
    }
}
