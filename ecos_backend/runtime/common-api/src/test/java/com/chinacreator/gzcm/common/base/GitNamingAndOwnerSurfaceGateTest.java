package com.chinacreator.gzcm.common.base;

import com.chinacreator.gzcm.common.testscan.SourceCommentStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Git 归档命名与属主面 <b>零容差仓扫门</b>（#147C，收口《ECOS补充详细设计说明书-R7至R11-2026-10-16.md》
 * §2.9.5①/DQ-2、§2.9.5④、§3.1 DoD 的 DD-3／DI-1 三面）。
 *
 * <p><b>差距形态（收口前实跑取证）</b>：
 * <ol>
 *   <li><b>幻影表名（IR01）</b>：datanet {@code GitController.commits} 腿以 SQL 查
 *       {@code td_git_repository}——该表<b>从未存在</b>（注册表按 DQ-2 定名
 *       {@code ecos_control.ecos_git_repo}，且它记「仓库」不记「提交」）。SQL 必抛异常，
 *       再被泛 {@code catch} 折叠成 {@code items=[] + total=0 + success}＝假绿。</li>
 *   <li><b>第二套 Git 接口（DD-3）</b>：属主之外的模块直接 {@code import org.eclipse.jgit.*}
 *       自行开仓库，与 runtime-access 各写一套读写面。</li>
 *   <li><b>第二套路径源（DI-1）</b>：控制器用 {@code GitRepoRootResolver.resolveRepoPath(repoId)}
 *       自行拼 {@code {root}/{repoId}} 再去定位仓库，与注册表 {@code repo_root} 分叉
 *       ——V300 种子 {@code dw-pipeline → pipeline}、{@code ontology-model → ontology}
 *       即<b>实证</b>：目录名不等于 repositoryId，两套推导必然给出不同路径。</li>
 *   <li><b>死 SPI / 幻影 DAO 面</b>：sysman {@code IConfigGitSyncService}/{@code ConfigGitSyncServiceImpl}
 *       与 datanet {@code GitQueryService}/{@code GitQueryDao} 四条腿无任何调用方，
 *       以「接口存在」冒充「通道落地」＝账实不符。</li>
 * </ol>
 *
 * <p><b>本门钉五件事</b>（逐条对上一段形态，每条独立用例便于归因）：
 * <ol>
 *   <li>幻影表名在 <b>Java 代码面</b>（{@code src/main/java} 剥注释后）按<b>裸名</b>零命中，
 *       在 <b>资源面</b>（{@code src/main/resources} 的 sql/xml/yml/properties，剥 SQL 与 XML 注释后）
 *       按 <b>SQL 动词紧邻</b>零命中。两面口径不同是刻意的：V300 的 {@code COMMENT ON TABLE …
 *       IS '…td_git_repository 作废…'} 是把 DQ-2 改名裁决留在建表脚本里的<b>证据</b>，
 *       按裸名判红等于要求删证据。</li>
 *   <li>{@code org.eclipse.jgit} 引用在 main 面<b>恰等于</b>属主集
 *       （现 {@code GitServiceImpl} 一枚），并<b>回读</b>属主仍持有该引用——白名单陈旧与
 *       「两边都删了所以零命中」都要变红。</li>
 *   <li>四枚死面文件名在 ecos_backend（含 test 面）不复现；新增同名文件＝通道复活，须先裁决。</li>
 *   <li>命名单源锚点在位：属主 DAO 的表名常量 = {@code ecos_control.ecos_git_repo}；
 *       仓根 key 常量 = {@code ecos_git_repo_root} 且经 {@code SysConfigService} 门面读取
 *       （铁律：配置只经 sysman api 门面）；V300 DDL 建的是前者，不是幻影表。</li>
 *   <li><b>判据能变红</b>：合成夹具在仓外 {@code @TempDir} 取证（违例串落在字符串字面量里＝计入；
 *       落在 Javadoc 里＝不计），不往仓库塞违例文件。</li>
 * </ol>
 *
 * <p><b>假绿防护</b>：① 扫描根解析失败即 {@code fail}，main 面实扫文件数低于
 * {@link #MIN_SCANNED_MAIN_FILES} 即红（读不到 ≠ 无违例）；② Java 面一律经
 * {@link SourceCommentStripper} 剥注释——本门自己的 Javadoc 大量提到 {@code td_git_repository}
 * 与 {@code import org.eclipse.jgit}，朴素行首判定既会把说明文字计成违例（反向激励「删证据」），
 * 也会因 {@code "http://…"} 这类字面量把真实违例吃掉（D-B4-40 已登记盲区，故复用单源剥器）。</p>
 *
 * @author ECOS Data Workbench #147C
 */
@Timeout(120)
class GitNamingAndOwnerSurfaceGateTest {

    /**
     * 幻影提交表名（DQ-2 裁决作废；§2.9.5④ 清理范围＝代码 + 文档）。
     *
     * <p><b>Java 面用裸名判据</b>：剥注释后仍出现该串，只能是「查不存在的表」的 SQL 腿或
     * 遗留常量——合规侧没有任何理由在代码里写它。</p>
     */
    private static final Pattern PHANTOM_TABLE =
            Pattern.compile("td_git_repository", Pattern.CASE_INSENSITIVE);

    /**
     * SQL／资源面的<b>可执行位</b>判据（动词紧邻）：{@code FROM}/{@code INTO}/{@code UPDATE}/
     * {@code JOIN}/{@code CREATE TABLE}/{@code DROP TABLE} 后紧跟该表名才算违例。
     *
     * <p><b>为什么 Java 面与 SQL 面口径不同</b>：V300 的
     * {@code COMMENT ON TABLE ecos_control.ecos_git_repo IS '…td_git_repository 作废…'} 是
     * <b>DQ-2 改名裁决留在 DDL 里的证据</b>，属命名作废声明而非引用；若按裸名判红，等于要求
     * 「把裁决依据从建表脚本里删掉」。该串与动词之间隔着表名与 {@code IS}，本判据读不到它，
     * 而 {@code FROM td_git_repository} 这类真引用一律入账——由
     * {@link #fixtureProvesBothJudgementsCanTurnRed} 的两条夹具分别取证。</p>
     */
    private static final Pattern PHANTOM_TABLE_SQL_REF = Pattern.compile(
            "(?i)\\b(from|into|update|join|table)\\s+(if\\s+not\\s+exists\\s+)?"
                    + "[a-z0-9_\"]*\\.?[a-z0-9_\"]*td_git_repository\\b");

    /** 第二套 Git 接口判据：直接 import JGit 的行（剥注释后再匹配）。 */
    private static final Pattern JGIT_IMPORT =
            Pattern.compile("(?m)^\\s*import\\s+org\\.eclipse\\.jgit\\.");

    /**
     * DI-1 补面判据（#147S）：控制器把客户端查询参数绑成 {@code localPath} 的形态。
     * <p>{@code [^)]*\)} 只吃注解自身的括号，不吃方法体。</p>
     */
    private static final Pattern LOCALPATH_REQUEST_BINDING =
            Pattern.compile("@RequestParam[^)]*\\)\\s*(?:final\\s+)?String\\s+localPath\\b");

    /**
     * DI-1 补面判据（#147S）：请求体侧读客户端 localPath 的两形态
     * （裸 Map 键 {@code "localPath"} 与强类型 DTO 的 {@code req.getLocalPath()}）。
     * <p>注册表实体 {@code repository.getLocalPath()}（服务端解析面，合规）不以 {@code req.} 起头，
     * 故不入本判据——把两类混成一条正则会误伤属主面（详见用例 Javadoc「口径为什么这样切」）。</p>
     */
    private static final Pattern LOCALPATH_REQUEST_CONSUME =
            Pattern.compile("\\breq\\.getLocalPath\\(\\)|\"localPath\"");

    /** 唯一登记在册的废弃入参承流文件（只进拒绝路径，见用例断言）。 */
    private static final String PIPELINE_GIT_SERVICE =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/service/PipelineGitService.java";

    /** JGit 唯一属主文件（runtime-access）。新增成员须先按 DD-3 裁决，禁默认放行。 */
    private static final String GIT_SERVICE_IMPL =
            "ecos_backend/runtime/runtime-access/src/main/java/com/chinacreator/gzcm/runtime/access/git/GitServiceImpl.java";

    /** 属主集由 {@link #GIT_SERVICE_IMPL} 单源派生（白名单与回读锚点不可能各说各话）。 */
    private static final Set<String> JGIT_OWNERS = Set.of(GIT_SERVICE_IMPL);

    /** 死 SPI／幻影 DAO 文件名（已按「无调用方 ⇒ 下线」裁决删除，禁复现）。 */
    private static final Set<String> DEAD_SURFACE_FILE_NAMES = Set.of(
            "GitQueryService.java",
            "GitQueryDao.java",
            "IConfigGitSyncService.java",
            "ConfigGitSyncServiceImpl.java");

    private static final String REGISTRY_DAO =
            "ecos_backend/runtime/runtime-access/src/main/java/com/chinacreator/gzcm/runtime/access/git/dao/GitRepoRegistryDao.java";
    private static final String REPO_ROOT_RESOLVER =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/service/GitRepoRootResolver.java";
    private static final String V300_DDL =
            "docs/50-db_script/02-flyway-migration/gateway/V300__ontology_rule_git_cols_and_ecos_git_repo.sql";

    /** 扫描面阈值：main 面实扫 java 文件数下限（路径变更／读不到 ≠ 零违例）。 */
    private static final int MIN_SCANNED_MAIN_FILES = 2000;

    @Test
    @DisplayName("幻影表 td_git_repository 在代码面零命中（Java 剥注释后按裸名；资源面按 SQL 动词紧邻）")
    void phantomCommitTableNeverAppearsInCodeFace() throws IOException {
        Path root = locateRepoRoot();
        List<Path> javaFiles = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), javaFiles);
        assertScanFaceTrustworthy(javaFiles);

        List<String> hits = new ArrayList<>();
        for (Path f : javaFiles) {
            String rel = relativize(root, f);
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            if (PHANTOM_TABLE.matcher(stripped).find()) {
                hits.add(rel + "（Java 代码面）");
            }
        }
        for (Path f : collectResourcesUnder(root.resolve("ecos_backend"))) {
            String rel = relativize(root, f);
            String text = stripSqlAndXmlComments(Files.readString(f, StandardCharsets.UTF_8));
            if (PHANTOM_TABLE_SQL_REF.matcher(text).find()) {
                hits.add(rel + "（" + suffix(f) + " 资源面可执行位）");
            }
        }
        assertEquals(List.of(), hits,
                "幻影提交表 td_git_repository 在代码面复现（§2.9.5①/DQ-2：注册表单源 = "
                        + "ecos_control.ecos_git_repo，td_git_repository 从不存在，查它必抛异常并被折叠成"
                        + "空列表假成功＝IR01 违例）。Java 面按裸名判、资源面按 SQL 动词紧邻判。命中清单=" + hits);
    }

    @Test
    @DisplayName("JGit 引用在 main 面恰等于登记属主集，且属主回读仍在位（DD-3 无第二套 Git 接口）")
    void jgitIsConfinedToRegisteredOwners() throws IOException {
        Path root = locateRepoRoot();
        List<Path> javaFiles = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), javaFiles);
        assertScanFaceTrustworthy(javaFiles);

        Set<String> actualOwners = new TreeSet<>();
        for (Path f : javaFiles) {
            String rel = relativize(root, f);
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            if (JGIT_IMPORT.matcher(stripped).find()) {
                actualOwners.add(rel);
            }
        }
        assertEquals(new TreeSet<>(JGIT_OWNERS), actualOwners,
                "main 面 JGit 引用面与登记属主集不一致：多出的枚＝第二套 Git 接口（DD-3，"
                        + "Git 一律收敛 runtime-access）；少掉的枚＝属主已迁走而白名单陈旧，"
                        + "「其余文件零命中」随之失去意义。实跑=" + actualOwners);

        String ownerStripped = SourceCommentStripper.strip(readRequired(root, GIT_SERVICE_IMPL));
        assertTrue(JGIT_IMPORT.matcher(ownerStripped).find(),
                "属主 GitServiceImpl 已无 JGit import 锚点：底座被架空或改道，本门须同批改判据，禁静默失配");
    }

    /**
     * DI-1 补面（#147S）：客户端 {@code localPath} 入参<b>零生效面</b>——绑定面与消费面各钉一枚白名单，
     * 并回读「拒绝腿」真在位。
     *
     * <p><b>口径为什么不是「grep 字面量命中 0」</b>：补充设计册 §2.1 I-1 把兼容性判成
     * <b>破坏性</b>，处置手段写死为「旧入参由 {@code PipelineGitService} <b>拒绝</b>并返
     * {@code not_available}」，而不是「从签名里抹掉该参数」（根 {@code AGENTS.md}「API 只增不改」＋
     * 铁律 §2.5 第 8 项「接口禁止接收客户端传入的 localPath」的<b>语义</b>是禁止其参与寻址，
     * 不是禁止出现该形参）。因此本腿的判据是三条可复算的量：</p>
     * <ol>
     *   <li>请求绑定形态（{@code @RequestParam … String localPath}）＝<b>恰 1 处</b>，且只在
     *       {@code PipelineGitController#listBranches}（兼容外壳）；多一枚＝新端点又把客户端路径当寻址入参；</li>
     *   <li>请求体消费形态（{@code req.getLocalPath()} 与裸 Map 键 {@code "localPath"}）＝
     *       <b>恰 6 处且全部落在 {@code PipelineGitService} 一枚文件</b>；</li>
     *   <li>该文件必须同时持有拒绝锚点（{@code LOCALPATH_DEPRECATED_REASON} 抛出腿 +
     *       {@code resolveLocalPath} 的 not_available 分支），控制器侧必须把兼容形参<b>只</b>交给
     *       {@code listBranchesByLegacyLocalPath(localPath)}——三处任一被摘掉，白名单里的入参就变成
     *       「真能寻址」，本腿同批变红（禁「白名单还在、拒绝腿已空」的假绿）。</li>
     * </ol>
     *
     * <p><b>行为面不在本腿重复取证</b>：{@code PipelineGitControllerTest}／
     * {@code PipelineGitServiceH4Test} 已对「只传 localPath ⇒ 400 指引」与
     * 「只传 localPath ⇒ status=not_available」逐枚实跑；本腿只补静态面，防新增承流点绕过它们。</p>
     */
    @Test
    @DisplayName("客户端 localPath 入参零生效面：绑定恰 1 处／消费恰 6 处且拒绝腿在位（DI-1）")
    void legacyLocalPathInputIsBoundOnlyForRejection() throws IOException {
        Path root = locateRepoRoot();
        List<Path> javaFiles = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), javaFiles);
        assertScanFaceTrustworthy(javaFiles);

        Set<String> bindingFiles = new TreeSet<>();
        int bindingSites = 0;
        Set<String> consumeFiles = new TreeSet<>();
        int consumeSites = 0;
        for (Path f : javaFiles) {
            String rel = relativize(root, f);
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            int b = count(LOCALPATH_REQUEST_BINDING, stripped);
            if (b > 0) {
                bindingFiles.add(rel);
                bindingSites += b;
            }
            int c = count(LOCALPATH_REQUEST_CONSUME, stripped);
            if (c > 0) {
                consumeFiles.add(rel);
                consumeSites += c;
            }
        }

        assertEquals(Set.of("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/"
                        + "com/chinacreator/gzcm/engine/data/controller/PipelineGitController.java"),
                bindingFiles,
                "main 面把客户端 localPath 绑成请求参数的文件集与登记面不等：多出的枚＝新端点重新接收"
                        + "客户端路径（铁律 §2.5 第 8 项／DI-1）；缺失＝兼容外壳已撤而本腿台账未同批改");
        assertEquals(1, bindingSites, "废弃入参绑定面应恰 1 处，实跑=" + bindingSites);
        assertEquals(Set.of(PIPELINE_GIT_SERVICE), consumeFiles,
                "请求体侧读客户端 localPath 的文件集与登记面不等（实跑=" + consumeFiles + "）："
                        + "该面只允许存在于 PipelineGitService 的拒绝分支，任何第二文件＝第二套寻址");
        assertEquals(6, consumeSites, "请求体侧 localPath 消费面应恰 6 处，实跑=" + consumeSites);

        String svc = SourceCommentStripper.strip(readRequired(root, PIPELINE_GIT_SERVICE));
        assertTrue(svc.contains("LOCALPATH_DEPRECATED_REASON"),
                "PipelineGitService 不再持有 LOCALPATH_DEPRECATED_REASON：白名单里的入参已成无拒绝腿的空壳");
        assertTrue(svc.contains("legacyLocalPath != null && !legacyLocalPath.trim().isEmpty()"),
                "resolveLocalPath 的废弃入参分支被改写：客户端路径可能已参与真实寻址");
        assertTrue(svc.contains("throw new IllegalArgumentException(LOCALPATH_DEPRECATED_REASON)"),
                "listBranchesByLegacyLocalPath 的显式拒绝腿缺失：废弃入参被静默放行");
        String ctrl = SourceCommentStripper.strip(readRequired(root,
                "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/"
                        + "com/chinacreator/gzcm/engine/data/controller/PipelineGitController.java"));
        assertTrue(ctrl.contains("listBranchesByLegacyLocalPath(localPath)"),
                "控制器兼容形参不再只交给拒绝腿：绑定面与消费面之间的路由被改道（DI-1）");
    }

    @Test
    @DisplayName("四枚死 SPI／幻影 DAO 文件不得复现（死通道以接口存在冒充落地＝账实不符）")
    void deadGitSurfacesStayDeleted() throws IOException {
        Path root = locateRepoRoot();
        Path backend = root.resolve("ecos_backend");
        List<String> resurrected = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(backend)) {
            walk.filter(Files::isRegularFile)
                    .map(p -> p.toAbsolutePath().normalize())
                    .forEach(p -> {
                        String s = p.toString().replace('\\', '/');
                        if (s.contains("/target/") || s.contains("/archive/")) {
                            return;
                        }
                        String name = p.getFileName().toString();
                        if (DEAD_SURFACE_FILE_NAMES.contains(name)) {
                            resurrected.add(name + " @ " + s.substring(s.indexOf("ecos_backend/")));
                        }
                    });
        }
        assertEquals(List.of(), resurrected,
                "已裁决下线的死 SPI／幻影 DAO 文件复现：" + resurrected
                        + "。本批取证＝全仓无调用方（sysman 配置→Git 通道从未落地，datanet GitQuery 腿查幻影表）；"
                        + "要重建通道须先接承流并同批改本门判据。");
    }

    @Test
    @DisplayName("命名与配置单源锚点在位：属主 DAO 表名 / 仓根 key 经 sysman 门面 / V300 DDL 建表")
    void registryNamingAnchorsInPlace() throws IOException {
        Path root = locateRepoRoot();

        String dao = SourceCommentStripper.strip(readRequired(root, REGISTRY_DAO));
        assertTrue(dao.contains("ecos_control.ecos_git_repo"),
                "属主 DAO 不再引用注册表 ecos_control.ecos_git_repo（DQ-2 定名）——表名单源漂移");
        assertTrue(dao.contains("repository_id"),
                "属主 DAO 的仓库定位键 repository_id 缺失：repositoryId 单源（DI-1）不再成立");

        String resolver = SourceCommentStripper.strip(readRequired(root, REPO_ROOT_RESOLVER));
        assertTrue(resolver.contains("KEY_REPO_ROOT"),
                "仓根 key 常量不再单源（GitRepoRootResolver.KEY_REPO_ROOT）");
        assertTrue(resolver.contains("ecos_git_repo_root"),
                "仓根配置名单源漂移：应为 sys_config ecos_git_repo_root（§2.9.5①）");
        assertTrue(resolver.contains("SysConfigService"),
                "仓根配置必须经 sysman api 门面读取（铁律：配置与字典只经 sysman api 门面），禁旁支");

        Path ddl = root.resolve(V300_DDL);
        assertTrue(Files.isRegularFile(ddl),
                "缺 V300 建表脚本（注册表 DDL 单源）: " + V300_DDL + "——DDL 面漂移不得由「代码零命中」代替");
        String ddlText = Files.readString(ddl, StandardCharsets.UTF_8);
        assertTrue(ddlText.contains("CREATE TABLE IF NOT EXISTS ecos_control.ecos_git_repo"),
                "V300 未创建 ecos_control.ecos_git_repo：注册表建表腿缺失＝运行时只可能靠内存态或幻影表");
        assertTrue(!PHANTOM_TABLE_SQL_REF.matcher(stripSqlAndXmlComments(ddlText)).find(),
                "V300 的可执行位引用了幻影表 td_git_repository（建表/查询动词紧邻；作废声明留在注释或 "
                        + "COMMENT ON 串内不计）");
    }

    /**
     * 判据自证（合成夹具，仓外 {@code @TempDir}）：同一条违例串落在<b>字符串字面量</b>里必须被
     * 计入，落在 <b>Javadoc／行注释</b>里必须不被计入；JGit 引用面必须能标记非属主文件。
     *
     * <p>没有本用例，前五腿的「零命中」既可能意味着「合规」，也可能意味着「判据压根读不到」。</p>
     */
    @Test
    @DisplayName("合成夹具：幻影表与 JGit 判据能变红，且注释里的证据文字不计成违例")
    void fixtureProvesBothJudgementsCanTurnRed(@TempDir Path tmp) throws IOException {
        String violating = "class Probe {\n"
                + "    private static final String T = \"td_git_repository\";\n"
                + "    // private static final String ALSO = \"td_git_repository\";\n"
                + "}\n";
        String documented = "/**\n * 原腿查 td_git_repository（幻影表），本批删除。\n */\n"
                + "class Clean {\n}\n";

        assertEquals(1, count(PHANTOM_TABLE, SourceCommentStripper.strip(violating)),
                "夹具失效：字面量内的幻影表未被计成命中（判据变红能力丢失＝假绿）");
        assertEquals(0, count(PHANTOM_TABLE, SourceCommentStripper.strip(documented)),
                "夹具失效：注释里的说明文字被计成违例（会把「写了证据」判成「还有违例」，反向激励删文档）");

        String secondOwner = "package p;\nimport org.eclipse.jgit.api.Git;\nclass SecondFace {}\n";
        assertTrue(JGIT_IMPORT.matcher(SourceCommentStripper.strip(secondOwner)).find(),
                "夹具失效：第二套 Git 接口面未被标记（DD-3 判据失明）");
        assertTrue(!JGIT_IMPORT.matcher(SourceCommentStripper.strip(documented)).find(),
                "夹具失效：未 import JGit 的文档被判成违例面");

        // 资源面口径：动词紧邻＝真引用（红）；COMMENT ON 串内的作废声明＝证据文字（不计）
        String mapperViolation = "<mapper>\n  <select id=\"list\">SELECT * FROM ecos_control.td_git_repository</select>\n</mapper>\n";
        assertTrue(PHANTOM_TABLE_SQL_REF.matcher(stripSqlAndXmlComments(mapperViolation)).find(),
                "夹具失效：Mapper 里查幻影表的 SQL 腿未被标记（IR01 判据失明）");
        String ddlEvidence = "COMMENT ON TABLE ecos_control.ecos_git_repo IS\n"
                + "    'Git 仓库注册表（DQ-2 定名；td_git_repository 作废）。';\n";
        assertTrue(!PHANTOM_TABLE_SQL_REF.matcher(stripSqlAndXmlComments(ddlEvidence)).find(),
                "夹具失效：DDL 注释串里的命名作废声明被判成引用（会逼着删裁决证据）");
    }

    /**
     * 资源面（sql／xml／yml／properties）的注释剥离：{@code --} 行注释、{@code /* *}{@code /} 块注释、
     * {@code <!-- -->} XML 注释、{@code #} 行注释（properties/yml）。
     */
    private static String stripSqlAndXmlComments(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?s)<!--.*?-->", " ")
                .replaceAll("(?m)^\\s*--[^\\n]*", " ")
                .replaceAll("(?m)^\\s*#[^\\n]*", " ");
    }

    private static int count(Pattern p, String s) {
        int n = 0;
        Matcher m = p.matcher(s);
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static void assertScanFaceTrustworthy(List<Path> javaFiles) {
        assertTrue(javaFiles.size() >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + javaFiles.size() + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无违例）");
    }

    private static String readRequired(Path root, String rel) throws IOException {
        Path f = root.resolve(rel);
        if (!Files.isRegularFile(f)) {
            fail("登记属主文件不在位（改名/挪目录须同批改本门判据）：" + rel);
        }
        return Files.readString(f, StandardCharsets.UTF_8);
    }

    private static String relativize(Path root, Path f) {
        return root.relativize(f).toString().replace('\\', '/');
    }

    private static String suffix(Path f) {
        String n = f.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot < 0 ? n : n.substring(dot + 1);
    }

    /** main 面 java 文件（排除 target/ 与 archive/，与同族仓扫门一致口径）。 */
    private static void collectJavaUnder(Path dir, List<Path> out) throws IOException {
        collectUnder(dir, out, "/src/main/java/", ".java");
    }

    /** main 资源面（xml／yml／sql；幻影表的 Mapper 腿若复活会落在这里）。 */
    private static List<Path> collectResourcesUnder(Path dir) throws IOException {
        List<Path> out = new ArrayList<>();
        for (String ext : List.of(".xml", ".yml", ".yaml", ".sql", ".properties")) {
            collectUnder(dir, out, "/src/main/resources/", ext);
        }
        return out;
    }

    private static void collectUnder(Path dir, List<Path> out, String pathFragment, String extension)
            throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(extension))
                    .map(p -> p.toAbsolutePath().normalize())
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return s.contains(pathFragment) && !s.contains("/target/") && !s.contains("/archive/");
                    })
                    .forEach(out::add);
        }
    }

    /** 从 user.dir 逐级上溯定位仓根（判据面须能读到 ecos_backend/）。 */
    private static Path locateRepoRoot() {
        Path cur = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path p = cur; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("ecos_backend"))) {
                return p;
            }
        }
        return fail("无法定位仓库根（找不到 ecos_backend/），扫描面不可信：user.dir=" + cur);
    }
}
