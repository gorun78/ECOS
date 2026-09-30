package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1 W43 (详细设计-02 改造清单 C.2.1) — 重写表 / 匿名面禁宣告
 * FastaSynthService / CharterSynthService / WorkflowController 排除。
 *
 * <p>D-3 背景：同一资源 5 套前缀同日同进程并活导致漂移；本门禁锁住两个通路：
 * <ol>
 *   <li>{@code VersionPrefixRewriteFilter} 的三张重写表不得以注释或
 *       代码片段宣告 synth/workflow 端点被排除（例如
 *       {@code // *synth.* excluded}、{@code // workflows excluded}、
 *       {@code satisfies(.startsWith(.*(synth|workflow).*)})）。
 *       历史曾出现 WorkflowController 端点被排除在重写表外导致前端
 *       /api/v1/ecos/workflows 打空的情形；本测试守这条红线不再回归。</li>
 *   <li>{@code SecurityConfig}（实际从 {@code AnonymousEndpointRegistry}
 *       单源读取）不得以注释宣告 {@code workflow%2Fsynth} 匿名绕过。</li>
 * </ol>
 *
 * <p>纯源码 regex 扫描（不启动 Spring）；用户授权范围：filter + SecurityConfig。
 *
 * @author ecos-p0-data-engine
 * <br>
 * 2026-09-30
 */
class DatanetNoExclusionTest {

    private static final String FILTER_REL =
            "ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/filter/VersionPrefixRewriteFilter.java";
    private static final String SECURITY_REL =
            "ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/security/SecurityConfig.java";
    private static final String REGISTRY_REL =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/security/anonymous/AnonymousEndpointRegistry.java";

    private static Path repoRoot() throws IOException {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("ecos_backend"))) {
            Path parent = p.getParent();
            if (parent == null || parent.equals(p)) break;
            p = parent;
        }
        assertTrue(p != null && Files.isDirectory(p.resolve("ecos_backend")),
                "cwd 未锚定 ECOS 仓库根（找不到 ecos_backend/）");
        return p;
    }

    private static String readQuiet(Path f) {
        try {
            return Files.exists(f) ? Files.readString(f, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> linesMatching(String src, Pattern p) {
        return new java.util.ArrayList<>(java.util.stream.Stream
                .of(src.split("\r?\n"))
                .filter(line -> p.matcher(line).find())
                .toList());
    }

    @Test
    @DisplayName("VersionPrefixRewriteFilter 禁止宣告 synth / workflow 排除")
    void filterDoesnotAdvertiseSynthOrWorkflowExclusion() throws IOException {
        String src = readQuiet(repoRoot().resolve(FILTER_REL));
        assertTrue(!src.isEmpty(), "VersionPrefixRewriteFilter 源文件缺失");

        Pattern[] banned = {
                // // *synth.* excluded
                Pattern.compile("(?i)//.*\\bsynth\\b.*\\bexclud"),
                // // workflows excluded
                Pattern.compile("(?i)//.*\\bworkflows?\\b.*\\bexclud"),
                // satisfies(.startsWith(.*(synth|workflow).*))
                Pattern.compile("(?i)satisfies\\s*\\(.*startsWith.*\\((/)(synth|workflow)"),
                // 类名直名 + 排除语义（片语顺序不敏感）
                Pattern.compile("(?i)exclud.*\\b(WorkflowController|FastaSynthService|CharterSynthService)\\b"),
                Pattern.compile("(?i)\\b(WorkflowController|FastaSynthService|CharterSynthService)\\b.*\\bexclud"),
        };
        for (Pattern b : banned) {
            List<String> h = linesMatching(src, b);
            assertTrue(h.isEmpty(),
                    "VersionPrefixRewriteFilter 非法排除宣告 " + b + ":\n" + String.join("\n", h));
        }
    }

    @Test
    @DisplayName("SecurityConfig + AnonymousEndpointRegistry 不得宣告 workflow%synths 匿名绕过")
    void securityDoesNotAdvertiseWorkflowSynthBypass() throws IOException {
        Path root = repoRoot();
        String securitySrc = readQuiet(root.resolve(SECURITY_REL));
        String registrySrc = readQuiet(root.resolve(REGISTRY_REL));
        assertTrue(!securitySrc.isEmpty(), "SecurityConfig 源文件缺失");

        Pattern[] banned = {
                // 注释宣告 bypass（%2F = / 的 URL 编码；slash 也覆盖）
                Pattern.compile("(?i)(workflow%2Fsynth|workflow/synth).*\\b(bypass|permit|anon|exempt)"),
                Pattern.compile("(?i)\\b(bypass|permitAll|anon|exempt).*(workflow%2Fsynth|workflow/synth)"),
        };
        for (Pattern b : banned) {
            assertTrue(linesMatching(securitySrc, b).isEmpty(),
                    "SecurityConfig 宣告 workflow/synth 匿名绕过:\n" + String.join("\n", linesMatching(securitySrc, b)));
            if (!registrySrc.isEmpty()) {
                assertTrue(linesMatching(registrySrc, b).isEmpty(),
                        "AnonymousEndpointRegistry 宣告 workflow/synth 匿名绕过:\n" + String.join("\n", linesMatching(registrySrc, b)));
            }
        }
    }
}
