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
 * datanet {@code GitController}）必须经<b>唯一属主</b>解析，
 * 禁止各自 {@code Paths.get(System.getProperty("...repoRoot..."))} 一类绕过 resolver
 * 的裸路径拼接（D-22 残留：现 resolver 已被归档侧使用，需 pipeline 与 GitController
 * 侧也全覆盖）。
 *
 * <p>判据（#147C 后按「域内／跨域」两档，方向相反）：</p>
 * <ol>
 *   <li><b>域内调用点</b>（{@code MetadataCollectGitArchive} / {@code PipelineGitService}）
 *       必须引用 {@code GitRepoRootResolver}（属主本体在同域）；</li>
 *   <li><b>跨域消费点</b>（datanet {@code GitController}）必须<b>不</b>引用该 resolver、
 *       且必须引用 {@code GitRepositoryService}。原「三枚一律 contains resolver」的口径把跨域
 *       消费点推成了第二套路径源：控制器自行 {@code resolveRepoPath(repoId)} 拼
 *       {@code {root}/{repoId}}，与注册表 {@code repo_root}（V300 种子 {@code dw-pipeline→pipeline}）
 *       分叉＝DI-1 违例；收口后本地路径只由属主 {@code GitRepoPathResolver} 填充；</li>
 *   <li>两档共同红线：不得出现「直接读系统属性拼仓根」的绕过模式。</li>
 * </ol>
 *
 * <p><b>反假绿</b>：调用点文件缺失单记一条问题（缺文件 ≠ 合规）；两档判据互为反向，
 * 引用被删空或被加回来都会变红。三处 {@code contains} 判据一律走 {@link #codeOnly(String)}
 * ——收口后的类 Javadoc 里<b>保留</b>着对原违例的叙述，读原文会把「写了证据」判成「还有违例」。</p>
 *
 * <p>定位法：{@link #findRepoRoot()} 复用仓内既有用例
 * {@code NoYamlLineageScanArchTest} 的安全模式（user.dir 上溯 .git / pom.xml，
 * 纯 java.nio，No {@code .toURI()} 安全模式、不解 IC C 异常）。</p>
 *
 * @author ECOS Data Engine Team / F02-12
 */
@DisplayName("F02-12/W63 架构守卫：Git 归档路径单源（域内引用 resolver／跨域只走注册表属主）")
class AllGitCallersUseResolverArchTest {

    /** 域内调用点：必须引用 {@code GitRepoRootResolver}（属主本体在同域）。 */
    private static final String[] IN_DOMAIN_CALLER_REL_PATHS = {
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/metadata/MetadataCollectGitArchive.java",
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/service/PipelineGitService.java",
    };

    /** 跨域消费点：必须<b>不</b>引用 resolver，只经 {@code GitRepositoryService} 定位仓库（#147C/DI-1）。 */
    private static final String[] CROSS_DOMAIN_CALLER_REL_PATHS = {
            "ecos_backend/services/datanet/src/main/java/com/chinacreator/gzcm/services/datanet/controller/GitController.java",
    };

    /** 绕过模式：直接读 System 属性拼仓根（historic 缺陷面，禁复现）。 */
    private static final Pattern RAW_REPO_ROOT_PROP =
            Pattern.compile("Paths\\.get\\s*\\(\\s*System\\.getProperty\\s*\\([^)]*repoRoot",
                    Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("域内调用点引用 resolver、跨域消费点不引用 resolver，三者均不裸拼系统属性仓根")
    void allGitCallersRouteThroughResolverAndAvoidRawRepoRootProperty() throws IOException {
        Path repoRoot = findRepoRoot();
        List<String> problems = new ArrayList<>();
        for (String rel : IN_DOMAIN_CALLER_REL_PATHS) {
            String src = readCaller(repoRoot, rel, problems);
            if (src == null) {
                continue;
            }
            if (!codeOnly(src).contains("GitRepoRootResolver")) {
                problems.add(rel + " 未引用 GitRepoRootResolver（域内调用点须经 resolver 单源解析仓根）");
            }
        }
        for (String rel : CROSS_DOMAIN_CALLER_REL_PATHS) {
            String src = readCaller(repoRoot, rel, problems);
            if (src == null) {
                continue;
            }
            if (codeOnly(src).contains("GitRepoRootResolver")) {
                problems.add(rel + " 引用了 GitRepoRootResolver：跨域消费点自行拼 {root}/{repoId}＝第二套路径源"
                        + "（仓库定位只许走 GitRepositoryService 的注册表属主解析，#147C/DI-1）");
            }
            if (!codeOnly(src).contains("GitRepositoryService")) {
                problems.add(rel + " 未引用 GitRepositoryService（跨域消费点必须经注册表属主定位仓库）");
            }
        }
        List<String> allCallers = new ArrayList<>();
        allCallers.addAll(List.of(IN_DOMAIN_CALLER_REL_PATHS));
        allCallers.addAll(List.of(CROSS_DOMAIN_CALLER_REL_PATHS));
        for (String rel : allCallers) {
            Path f = repoRoot.resolve(rel);
            if (!Files.isRegularFile(f)) {
                continue;
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
                "F02-12/W63：Git 归档路径必须单源（域内经 resolver／跨域经注册表属主，禁裸拼系统属性仓根）：\n"
                        + String.join("\n", problems));
    }

    /** 读取调用点源码；文件不在位时记一条问题并返回 {@code null}（缺文件 ≠ 合规）。 */
    private static String readCaller(Path repoRoot, String rel, List<String> problems) throws IOException {
        Path f = repoRoot.resolve(rel);
        if (!Files.isRegularFile(f)) {
            problems.add("缺调用点源码文件: " + rel);
            return null;
        }
        return Files.readString(f, StandardCharsets.UTF_8);
    }

    /**
     * 只保留代码行的源码文本（判据口径＝「代码里是否真的引用」，不是「文档里是否提到」）。
     *
     * <p><b>为什么必须排除注释</b>：#147C 收口后，datanet {@code GitController} 的类 Javadoc 里
     * <b>故意保留</b>了对原违例的叙述（「原 commits 腿用 {@code GitRepoRootResolver.resolveRepoPath}
     * 自行拼路径＝第二套路径源」）。若判据读原文，这条<b>说明性文字</b>会把已收口的跨域消费点
     * 报成违例，逼后来者「为了让门变绿而删掉证据」＝反向激励。</p>
     *
     * <p><b>本口径的盲区为何在本判据上不成立</b>：仓内更严的单源剥离器
     * {@code SourceCommentStripper}（状态机、字面量感知）之所以必要，是因为朴素剥离会把
     * {@code "http://…"} 这类字符串里的 {@code //} 当注释吃掉，从而把<b>真实违例藏起来</b>。
     * 本门只按「整行首个非空白字符是 {@code //}／{@code *}／<code>/*</code>」跳过行——
     * 合法的 Java 代码行不可能以 {@code *} 或 <code>/*</code> 开头，而本判据的目标串
     * （{@code GitRepoRootResolver}／{@code GitRepositoryService}／{@code Paths.get(System.getProperty}）
     * 是标识符与调用形态，被跳过的只是注释行；<b>误判方向只会是「把注释当代码多计」，
     * 不会是「把代码当注释漏计」</b>，故不会造成假绿。需要精确行号定位的绕过模式判定另走
     * 逐行循环，与本方法同口径。</p>
     */
    private static String codeOnly(String src) {
        StringBuilder out = new StringBuilder(src.length());
        for (String line : src.split("\n", -1)) {
            String t = line.trim();
            if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    @Test
    @DisplayName("GitRepoRootResolver 本体存在于 data-engine-impl（前置：单源可被引用）")
    void resolverItselfExists() throws IOException {
        Path repoRoot = findRepoRoot();
        Path resolver = repoRoot.resolve(
                "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/service/GitRepoRootResolver.java");
        assertTrue(Files.isRegularFile(resolver), "缺 GitRepoRootResolver 本体: " + resolver);
        String src = codeOnly(Files.readString(resolver, StandardCharsets.UTF_8));
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
