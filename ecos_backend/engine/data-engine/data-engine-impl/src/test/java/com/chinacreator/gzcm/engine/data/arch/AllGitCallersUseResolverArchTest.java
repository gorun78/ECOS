package com.chinacreator.gzcm.engine.data.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F02-12 / W63 架构守卫（源码扫描，无 DB）：
 * Git 归档仓根路径 <b>single-source</b> — 所有构建 git 归档路径的调用方
 * （{@code MetadataCollectGitArchive} / {@code PipelineGitService} /
 * datanet {@code GitController}）必须经 {@code GitRepoRootResolver} 单源解析，
 * 禁止各自 {@code Paths.get(System.getProperty("...repoRoot..."))} 一类绕过 resolver
 * 的裸路径拼接（D-22 残留：现 resolver 已被归档侧使用，需 pipeline 与 GitController
 * 侧也全覆盖）。
 *
 * <p>判据（对仓根相对路径的三个已知 git 调用点）：</p>
 * <ol>
 *   <li>每个文件源码必须引用 {@code GitRepoRootResolver}（注入/使用/静态调用）；</li>
 *   <li>每个文件源码必须<b>不</b>出现 {@code Paths.get(System.getProperty(...repoRoot...))}
 *       一类「直接读系统属性拼仓根」的绕过模式。</li>
 * </ol>
 *
 * <p>定位法：{@link #findRepoRoot()} 复用仓内既有用例
 * {@code NoYamlLineageScanArchTest} 的安全模式（user.dir 上溯 .git / pom.xml，
 * 纯 java.nio，No {@code .toURI()} 安全模式、不解 IC C 异常）。</p>
 *
 * @author ECOS Data Engine Team / F02-12
 */
@DisplayName("F02-12/W63 架构守卫：三大 Git 调用点均经 GitRepoRootResolver 单源解析")
class AllGitCallersUseResolverArchTest {

    /** 仓根下三个已知 git 归档调用点的相对路径（源码精确路径）。 */
    private static final String[] GIT_CALLER_REL_PATHS = {
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/metadata/MetadataCollectGitArchive.java",
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/service/PipelineGitService.java",
            "ecos_backend/services/datanet/src/main/java/com/chinacreator/gzcm/services/datanet/controller/GitController.java",
    };

    /** 绕过模式：直接读 System 属性拼仓根（historic 缺陷面，禁复现）。 */
    private static final Pattern RAW_REPO_ROOT_PROP =
            Pattern.compile("Paths\\.get\\s*\\(\\s*System\\.getProperty\\s*\\([^)]*repoRoot",
                    Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("三个 Git 调用点均引用 GitRepoRootResolver 且不裸拼系统属性仓根")
    void allGitCallersRouteThroughResolverAndAvoidRawRepoRootProperty() throws IOException {
        Path repoRoot = findRepoRoot();
        List<String> problems = new ArrayList<>();
        for (String rel : GIT_CALLER_REL_PATHS) {
            Path f = repoRoot.resolve(rel);
            if (!Files.isRegularFile(f)) {
                problems.add("缺调用点源码文件: " + rel);
                continue;
            }
            String src = Files.readString(f, StandardCharsets.UTF_8);
            if (!src.contains("GitRepoRootResolver")) {
                problems.add(rel + " 未引用 GitRepoRootResolver（须经 resolver 单源解析仓根）");
            }
            // 非注释行为判定：逐行检查绕过模式（沿用仓内 source-scan 惯例）
            List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String t = lines.get(i).trim();
                if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) {
                    continue;
                }
                if (RAW_REPO_ROOT_PROP.matcher(t).find()) {
                    problems.add(rel + ":" + (i + 1) + " 裸拼 System.getProperty(...repoRoot...) 绕过 resolver: " + t);
                }
            }
        }
        assertTrue(problems.isEmpty(),
                "F02-12/W63：Git 归档调用点必须经 GitRepoRootResolver 单源解析仓根：\n"
                        + String.join("\n", problems));
    }

    @Test
    @DisplayName("GitRepoRootResolver 本体存在于 data-engine-impl（前置：单源可被引用）")
    void resolverItselfExists() throws IOException {
        Path repoRoot = findRepoRoot();
        Path resolver = repoRoot.resolve(
                "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/service/GitRepoRootResolver.java");
        assertTrue(Files.isRegularFile(resolver), "缺 GitRepoRootResolver 本体: " + resolver);
        String src = Files.readString(resolver, StandardCharsets.UTF_8);
        assertTrue(src.contains("class GitRepoRootResolver"), "resolver 类定义缺失");
        // 单源语义：resolver 必须经 SysConfigService 读 ecos_git_repo_root（sysman 门面），
        // 而非 System.getenv / 硬编码 StringsCache 或其他旁支
        assertTrue(src.contains("SysConfigService"), "resolver 必须经 SysConfigService 门面读仓根配置");
        assertTrue(src.contains("KEY_REPO_ROOT"), "resolver 应集中仓根 key 常量为单一事实源");
    }

    /** 从 user.dir 逐级上溯定位仓根（含 .git 目录或文件），退化为最近 pom.xml 祖先。 */
    static Path findRepoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path cursor = dir;
        while (cursor != null) {
            Path git = cursor.resolve(".git");
            if (Files.isDirectory(git) || Files.isRegularFile(git)) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        cursor = dir;
        while (cursor != null) {
            if (Files.exists(cursor.resolve("pom.xml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("无法从 user.dir=" + dir + " 定位仓根（.git/pom.xml 均缺失）");
    }
}
