package com.chinacreator.gzcm.gateway.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 源码「必须可被版本控制」门（#147Z-4，2026-11-04 事故驱动）。
 *
 * <p><b>事故</b>：{@code ecos_backend/.gitignore} 曾有一条 {@code *target*}。本仓 Windows 侧
 * {@code core.ignorecase=true}，该规则按<b>文件名不区分大小写</b>匹配，于是把源码
 * {@code .../rule/dispatch/RuleLayerTarget.java} 整个吞掉：文件在磁盘上、在工作树里编得过、跑得绿，
 * 却<b>从未进过 git</b>。commit {@code 787edc34}（#147F~#147K 五层分发批）把引用它的
 * {@code RuleLayerDispatcher}/{@code RuleDispatchResult}/{@code RuleRegistry} 全提交了 ⇒
 * 干净 checkout（{@code git worktree add} / clone / CI）从那一笔起<b>编不过 ontology-engine-impl</b>
 * （实测报 {@code 找不到符号 类 RuleLayerTarget}）。同批登记在 DoD DF-4 的
 * {@code RuleLayerDispatcherTest} 绿，因此只在作者本机工作树成立——属「假绿」的一种：
 * 构建面绿 ≠ 提交面可构建。</p>
 *
 * <p><b>为什么既有门禁抓不到</b>：所有仓扫门都遍历磁盘工作树，看到的永远是含该文件的世界；
 * 只有从 git 索引出发（干净 checkout）才暴露。本门因此直接读 git 的索引/忽略面，而不是读磁盘。</p>
 *
 * <p><b>三条腿</b>：</p>
 * <ol>
 *   <li>{@link #noSourceFileIsGitIgnored()}：{@code git ls-files --others --ignored} 里不得出现
 *       任何源码面文件（后端 {@code &#42;/src/} 之下、前端 {@code src/} 之下、DDL 单源的 {@code .sql}）——
 *       这一条抓「已经存在的受害者」；</li>
 *   <li>{@link #ignoredSurfaceIsNotEmpty()}：忽略面条目数下限（防 git 调用静默失败把门变空转）；</li>
 *   <li>{@link #broadWildcardRulesCannotSwallowSourceFiles()}：用<b>合成</b>源码路径喂
 *       {@code git check-ignore --stdin}，抓「还没产生受害者但规则已埋雷」——
 *       事故复现时此腿必红（已实测：恢复 {@code *target*} 后两条合成路径同时被点名并给出规则行号）。</li>
 * </ol>
 *
 * <p><b>红了怎么处置</b>：收紧那条忽略规则（构建目录用 {@code target/} 这种目录形态，禁写
 * {@code *关键词*} 宽匹配），再 {@code git add} 补漏文件；<b>不许</b>用 {@code git add -f} 把源码
 * 塞进被忽略状态继续开发——那只会让下一次干净 checkout 再次编不过。同类事故先例见根
 * {@code .gitignore} 的 {@code *win_tasks*} 订正注记。</p>
 *
 * @author ECOS Gateway #147Z-4
 */
class SourceNotIgnoredGateTest {

    /** 忽略面条目下限：本仓构建产物众多，远低于此说明 git 调用或扫描根出了问题（防空转假绿）。 */
    private static final int MIN_IGNORED_ENTRIES = 500;

    /** 构建/依赖目录段：出现在这些段之后的路径本就该被忽略，不属源码面。 */
    private static final List<String> BUILD_SEGMENTS = List.of("/target/", "/node_modules/", "/dist/",
            "/reports/", "/generated-sources/", "/.qoder/");

    /** 事故锚点：被宽匹配吞掉、且引用者已入库的那枚源码文件。 */
    private static final String INCIDENT_FILE = "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/rule/dispatch/RuleLayerTarget.java";

    /** 合成探针路径：只验「规则会不会吞源码」，与文件是否存在无关（{@code git check-ignore} 不要求路径实存）。 */
    private static final List<String> SYNTHETIC_SOURCE_PATHS = List.of(
            "ecos_backend/engine/probe/src/main/java/demo/RuleLayerTarget.java",
            "ecos_backend/engine/probe/src/main/java/demo/DispatchTarget.java",
            "ecos_backend/engine/probe/src/test/java/demo/TargetProbeTest.java",
            "ecos_backend/gateway/src/main/resources/probe-target.yml",
            "ecos_frontend/src/api/targetProbe.ts",
            "docs/50-db_script/02-flyway-migration/gateway/V99999__probe_target.sql");

    @Test
    @DisplayName("源码面零容忍：任何后端 src/ 、前端 src/、DDL 单源文件都不得处于 git 忽略态")
    void noSourceFileIsGitIgnored() throws IOException, InterruptedException {
        GitResult ignored = git(List.of("ls-files", "--others", "--ignored", "--exclude-standard"), "");
        assertEquals(0, ignored.exit, "git ls-files 调用失败，门禁无法取证：" + ignored.stderr);

        List<String> victims = new ArrayList<>();
        for (String line : ignored.outLines()) {
            String path = normalize(line);
            if (isSourceSurface(path)) {
                victims.add(path);
            }
        }
        assertTrue(victims.isEmpty(),
                "以下源码文件处于 git 忽略态＝永远不会被提交＝干净 checkout 编不过/找不到资源"
                        + "（#147Z-4 RuleLayerTarget 事故同型）："
                        + "\n" + String.join("\n", victims)
                        + "\n复现命令：git ls-files --others --ignored --exclude-standard | grep -E '"
                        + "(ecos_backend/.*/src/|ecos_frontend/src/|\\.sql)$'"
                        + "\n处置：收紧 .gitignore 里的宽匹配规则后 git add；禁 git add -f 继续把源码放在忽略态。");
    }

    @Test
    @DisplayName("取证面不为空：忽略条目数不得低于下限（防 git 调用静默失败变空转）")
    void ignoredSurfaceIsNotEmpty() throws IOException, InterruptedException {
        GitResult ignored = git(List.of("ls-files", "--others", "--ignored", "--exclude-standard"), "");
        assertEquals(0, ignored.exit, "git ls-files 调用失败：" + ignored.stderr);
        assertTrue(ignored.outLines().size() >= MIN_IGNORED_ENTRIES,
                "忽略面只有 " + ignored.outLines().size() + " 条 < 下限 " + MIN_IGNORED_ENTRIES
                        + "：要么 git 调用/CWD 漂了，要么忽略规则被整体删除——两种情况都会让上一腿"
                        + "在「看不见受害者」时假绿，故本门必须拒绝空扫描面。");
    }

    @Test
    @DisplayName("规则形态自证：合成源码路径不得被任何忽略规则命中（事故规则恢复即红）")
    void broadWildcardRulesCannotSwallowSourceFiles() throws IOException, InterruptedException {
        String stdin = String.join("\n", SYNTHETIC_SOURCE_PATHS) + "\n";
        GitResult probed = git(List.of("check-ignore", "-v", "--stdin"), stdin);
        // git check-ignore：0=有命中，1=全无命中，其余=用法/仓库错误
        assertTrue(probed.exit == 0 || probed.exit == 1,
                "git check-ignore 返回非预期退出码 " + probed.exit + "：" + probed.stderr);
        assertEquals("", probed.stdoutTrim(),
                "忽略规则命中了源码文件名形态（尚未产生受害者，但下一个 *Target.java 就是）："
                        + "\n" + probed.stdoutTrim()
                        + "\n左侧给出「规则文件:行号	模式	路径」，请把那行改成目录形态（如 target/）。");
    }

    @Test
    @DisplayName("事故锚点回读：RuleLayerTarget.java 确已入库（判据腿可被删空，锚点不行）")
    void incidentFileIsTrackedInGitIndex() throws IOException, InterruptedException {
        GitResult tracked = git(List.of("ls-files", "--error-unmatch", INCIDENT_FILE), "");
        assertEquals(0, tracked.exit,
                "事故文件未进版本控制索引（" + INCIDENT_FILE + "）：" + tracked.stderr
                        + " ⇒ 引用它的 RuleLayerDispatcher 一族已在库内，干净 checkout 必然编不过。");
        GitResult referrers = git(List.of("ls-files", "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/rule/dispatch/"),
                "");
        List<String> files = referrers.outLines().stream().map(SourceNotIgnoredGateTest::normalize).toList();
        assertTrue(files.stream().anyMatch(f -> f.endsWith("RuleLayerDispatcher.java")),
                "属主目录索引里缺 RuleLayerDispatcher.java（回读失败，扫描面漂了？）：" + files);
    }

    // ================= 判定与 git 调用 =================

    /** 源码面：后端模块的 src 目录之下、前端 src 目录之下、DDL 单源的 .sql；构建/依赖段之后不算。 */
    private static boolean isSourceSurface(String path) {
        for (String seg : BUILD_SEGMENTS) {
            if (path.contains(seg)) {
                return false;
            }
        }
        if (path.startsWith("docs/") && path.endsWith(".sql")) {
            return path.startsWith("docs/50-db_script/");
        }
        if (path.startsWith("ecos_frontend/src/")) {
            return true;
        }
        return path.startsWith("ecos_backend/") && path.contains("/src/");
    }

    private static String normalize(String line) {
        String s = line.trim().replace('\\', '/');
        while (s.startsWith("./")) {
            s = s.substring(2);
        }
        return s;
    }

    private record GitResult(int exit, String stdout, String stderr) {
        List<String> outLines() {
            List<String> list = new ArrayList<>();
            for (String line : stdout.split("\n")) {
                if (!line.isBlank()) {
                    list.add(line);
                }
            }
            return list;
        }

        String stdoutTrim() {
            return stdout.replaceAll("\r?\n$", "").trim();
        }
    }

    private static GitResult git(List<String> args, String stdin) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(repoRoot().toFile());
        pb.redirectErrorStream(false);
        Process p = pb.start();
        if (stdin != null && !stdin.isEmpty()) {
            try (OutputStream os = p.getOutputStream()) {
                os.write(stdin.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        }
        String out = readFully(p.getInputStream());
        String err = readFully(p.getErrorStream());
        assertTrue(p.waitFor(90, TimeUnit.SECONDS), "git 调用超时：" + cmd);
        int exit = p.exitValue();
        return new GitResult(exit, out, err.toLowerCase(Locale.ROOT).isBlank() ? "" : err);
    }

    private static String readFully(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) > 0) {
            buf.write(chunk, 0, n);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    /** Surefire CWD 漂移：向上探测同时含 ecos_backend/ 与 ecos-docker/ 的仓库根。 */
    private static Path repoRoot() {
        Path cur = Paths.get("").toAbsolutePath().normalize();
        for (int i = 0; i < 8 && cur != null; i++) {
            if (Files.isDirectory(cur.resolve("ecos_backend")) && Files.isDirectory(cur.resolve("ecos-docker"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("未找到仓库根，CWD=" + Paths.get("").toAbsolutePath());
    }
}
