package com.chinacreator.gzcm.engine.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F02-12（详细设计-02 §二.12 P1）—— Git 归档路径<b>单源</b>经
 * {@link com.chinacreator.gzcm.engine.data.service.GitRepoRootResolver} 解析，
 * 对齐文档验收「AllGitCallersUseResolverArchTest（源码禁 {@code Paths.get(repoRoot)}
 * 于 resolver 之外）」的字面规定。任何业务代码出现
 * {@code Paths.get(repoRoot, ...)} / {@code Paths.get(root, ...)} 字面即违反单源收口，
 * 应改为 {@code repoRootResolver.resolveUnderRoot("...", "...")}。
 *
 * <p>例外仅 1 个文件：{@code GitRepoRootResolver.java} 自身（合法 resolver 内部实现）。</p>
 *
 * <p>本测试只拦"字面 {@code Paths.get(repoRoot|root, ...)}"的越界拼接，不拦合法的
 * {@code resolver.resolveRepoRoot()} 直接取用（后者返回的是仓库根本身，对应 F02-12 原文的
 * "根路径"，不构成"根下子路径拼接"）。真实违规样例 = F02-12 改造前
 * MetadataCollectGitArchive 的 {@code Paths.get(repoRoot, "metadata", dsId)} 4 处。</p>
 */
@DisplayName("F02-12 Git 归档路径单源护栏（源码 arch，P1）")
class AllGitCallersUseResolverArchTest {

    /** 合法触发：任何字面 {@code Paths.get(repoRoot, ...)} / {@code Paths.get(root, ...)}。 */
    private static final Pattern PATHS_GET_REPO_ROOT =
            Pattern.compile("Paths\\.get\\(\\s*(repoRoot|root)\\b");

    @Test
    @DisplayName("AllGitCallersUseResolverArchTest — 业务代码禁 Paths.get(repoRoot|root, ...) 字面拼接（resolver 内部除外）")
    void businessCodeMustNotPathsGetRepoRoot() throws IOException {
        Path root = RlsInjectionGuardArchTest.resolveModuleSrcMainJava();
        List<String> violations = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path f : (Iterable<Path>) walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals("GitRepoRootResolver.java"))
                    .sorted()::iterator) {
                String src = Files.readString(f, StandardCharsets.UTF_8);
                String rel = root.relativize(f).toString();
                Matcher m = PATHS_GET_REPO_ROOT.matcher(src);
                while (m.find()) {
                    violations.add(String.format("%s:%d  %s", rel, lineOf(src, m.start()),
                            surrounding(src, m.start(), m.end())));
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "F02-12 违规 —— 出现直接拼接 Git 仓库根的字面（字面应改为 repoRootResolver.resolveUnderRoot(...)）:\n"
                        + String.join("\n", violations));
    }

    private static String surrounding(String src, int start, int end) {
        int lo = Math.max(0, start - 15);
        int hi = Math.min(src.length(), end + 30);
        return src.substring(lo, hi).trim().replaceAll("\\s+", " ");
    }

    private static int lineOf(String src, int idx) {
        int line = 1;
        for (int i = 0; i < idx && i < src.length(); i++) {
            if (src.charAt(i) == '\n') line++;
        }
        return line;
    }
}
