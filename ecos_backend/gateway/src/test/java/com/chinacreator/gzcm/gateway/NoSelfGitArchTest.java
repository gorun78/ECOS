package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * F08-07 / B8c —— 业务侧（sysman 等）禁自建 Git 操作（架构铁律 §2.5；Git 单出口 = runtime-access {@code GitService}/{@code GitRepositoryService}）。
 *
 * <p>与 {@link NoScheduledAnnotationArchTest} / {@link NoSelfKafkaArchTest} 同族源码护栏。
 * 扫描 engine / services / workspace / runtime，唯一豁免 runtime-access（Git 底座自身）。
 * 判据（精确形态，跳过注释行与 javadoc）：
 * <ul>
 *   <li>JGit 依赖 {@code org.eclipse.jgit.} import（引擎绕过 runtime-access 直连 JGit）；</li>
 *   <li>{@code new ProcessBuilder( … "git" …)} / {@code Runtime.getRuntime().exec( … "git" …)}
 *       以 shell 方式手搓 git 命令。</li>
 * </ul>
 * 走 {@code gitService.commit/push/pull/…} 门面 delegate 引用<b>不</b>命中（这正是唯一合法出口）。
 * 基线冻结 2026-10-07：0（sysman ConfigGitSyncServiceImpl 已全量委托 runtime-access）。
 * 护栏形式：只减不增（任何新增自建 git 即红）。</p>
 *
 * <p>验收：{@code mvn -f ecos_backend/pom.xml -pl gateway -am test -Dtest='NoSelfGitArchTest'}。</p>
 */
class NoSelfGitArchTest {

    static final int BASELINE_SELF_GIT = 0;

    private static final List<Pattern> SELF_GIT = List.of(
            Pattern.compile("import\\s+org\\.eclipse\\.jgit\\."),
            Pattern.compile("new\\s+ProcessBuilder\\s*\\([^)]*\"git\""),
            Pattern.compile("Runtime\\.getRuntime\\(\\)\\.exec\\s*\\([^)]*\"git\""));

    private static final List<String> TOP_LEVEL = List.of("engine", "services", "workspace", "runtime");
    private static final Pattern EXCLUDED_MODULE = Pattern.compile("/runtime-access/");

    @Test
    @DisplayName("B8c 业务侧真实自建 Git（JGit import / shell git 命令）= 0")
    void businessSideMustNotSelfHoldGit() throws IOException {
        Path root = resolveBackendRoot();
        int hit = 0;
        for (String top : TOP_LEVEL) {
            Path base = root.resolve(top);
            if (!Files.isDirectory(base)) {
                throw new IllegalStateException("Git 扫描根目录缺失：" + base);
            }
            try (Stream<Path> walk = Files.walk(base)) {
                for (Path p : walk.filter(Files::isRegularFile)
                        .filter(f -> f.toString().endsWith(".java"))
                        .filter(f -> f.toString().replaceAll("\\\\", "/").contains("/src/main/java/"))
                        .filter(f -> !EXCLUDED_MODULE.matcher(f.toString().replaceAll("\\\\", "/")).find())
                        .toArray(Path[]::new)) {
                    String source = Files.readString(p);
                    for (String raw : source.split("\r?\n")) {
                        String line = stripLineComment(raw).stripLeading();
                        if (line.startsWith("*")) {
                            continue; // javadoc
                        }
                        for (Pattern pat : SELF_GIT) {
                            if (pat.matcher(line).find()) {
                                hit++;
                                System.err.println("[B8c 命中] " + p + "  ->  " + raw.strip());
                                break;
                            }
                        }
                    }
                }
            }
        }
        Assertions.assertEquals(BASELINE_SELF_GIT, hit,
                "B8c 业务侧自建 Git 新增：实测 " + hit
                        + "（铁律 §2.5 Git 单出口 runtime-access GitService；git write 必须经 gitService.commit/push/pull）");
        System.out.println("[NoSelfGitArch] " + TOP_LEVEL.size() + " 顶层域 main 真实自建 Git = " + hit);
    }

    private static Path resolveBackendRoot() {
        Path cur = Path.of("").toAbsolutePath();
        while (cur != null) {
            if (Files.isRegularFile(cur.resolve("pom.xml"))
                    && Files.isDirectory(cur.resolve("gateway"))) {
                return cur.normalize();
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("未能定位 ecos_backend 根（pom.xml + gateway/ 目录）");
    }

    private static String stripLineComment(String line) {
        int idx = line.indexOf("//");
        return idx >= 0 ? line.substring(0, idx) : line;
    }
}
