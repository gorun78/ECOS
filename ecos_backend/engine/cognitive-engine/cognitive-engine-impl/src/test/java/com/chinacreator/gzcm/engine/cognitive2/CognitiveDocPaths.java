package com.chinacreator.gzcm.engine.cognitive2;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 分册05 离线护栏公共走查件（零 live 库、不 Spring 容器）——统一仓根定位与认知域
 * 主源码 / 迁移单源 / 前端树的定位与正则计数，供本册各离线验收测试复用，避免各测试
 * 重复上溯仓根 + 各自正则口径漂移。
 *
 * <p>口径纪律（沿 04 册护栏）：仓根须同时命中 {@code docs/} 与 {@code ecos_backend/}
 * 双目录防误锁；主源码计数按<b>原文</b>（含注释）——与先期实测采数口径一致；DDL 走查
 * 需剥离行注释与 SQL 字符串字面量后精确子串/正则匹配。</p>
 */
final class CognitiveDocPaths {

    private CognitiveDocPaths() {
    }

    /** 认知引擎主源码根（impl 侧 {@code cognitive2} 包）。 */
    static Path cognitiveMain() {
        Path dir = repoRoot().resolve(
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java"
            + "/com/chinacreator/gzcm/engine/cognitive2");
        assertTrue(Files.isDirectory(dir), "认知主源码目录不可达: " + dir);
        return dir;
    }

    /** 认知引擎主源码的 Java 文件集（递归）。 */
    static List<Path> cognitiveMainJava() throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(cognitiveMain())) {
            walk.filter(f -> f.toString().endsWith(".java")).forEach(out::add);
        }
        return out;
    }

    /** 迁移单源目录。 */
    static Path migrationDir() throws IOException {
        Path dir = repoRoot().resolve("ecos_backend/gateway/src/main/resources/db/migration");
        assertTrue(Files.isDirectory(dir), "迁移单源目录不可达: " + dir);
        return dir;
    }

    /** 按文件名前缀 {@code Vxxx__} + {@code .sql} 读迁移脚本；不存在返回 null。 */
    static String migrationOrNull(int v) throws IOException {
        try (Stream<Path> walk = Files.list(migrationDir())) {
            for (Path p : walk.toList()) {
                String n = p.getFileName().toString();
                if (n.startsWith("V" + v + "__") && n.endsWith(".sql")) {
                    return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    /** 递归上溯定位仓根（docs/ + ecos_backend/ 双目录）。 */
    static Path repoRoot() {
        Path cur = Path.of("").toAbsolutePath();
        for (int i = 0; i < 16 && cur != null; i++, cur = cur.getParent()) {
            if (Files.isDirectory(cur.resolve("docs")) && Files.isDirectory(cur.resolve("ecos_backend"))) {
                return cur;
            }
        }
        fail("repoRoot 不可达（docs/ + ecos_backend/ 双目录未命中）");
        return null;
    }

    /** 在文件集内对原文（含注释）累计匹配次数。 */
    static int countHits(List<Path> files, Pattern p) throws IOException {
        int total = 0;
        for (Path f : files) {
            String body = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
            var m = p.matcher(body);
            while (m.find()) total++;
        }
        return total;
    }

    /** 在文件集内返回含指定模式的文件名（basename）。 */
    static List<String> filesWith(List<Path> files, Pattern p) throws IOException {
        List<String> out = new ArrayList<>();
        for (Path f : files) {
            String body = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
            if (p.matcher(body).find()) out.add(f.getFileName().toString());
        }
        return out;
    }

    /** 剔除 SQL 行注释（{@code --}）与 Java 风格块/行注释；保留字符串字面量。 */
    static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0, n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') i++;
            } else if (c == '-' && i + 1 < n && src.charAt(i + 1) == '-') {
                while (i < n && src.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) i++;
                i += 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** 单行内剔除 SQL 单引号字符串字面量（{@code ''} 转义），保留 DDL 结构句。 */
    static String stripSqlStringLiterals(String line) {
        StringBuilder out = new StringBuilder(line.length());
        boolean inStr = false;
        int i = 0, n = line.length();
        while (i < n) {
            char c = line.charAt(i);
            if (inStr) {
                if (c == '\'') {
                    if (i + 1 < n && line.charAt(i + 1) == '\'') { i += 2; continue; }
                    inStr = false; i++; continue;
                }
                i++; continue;
            }
            if (c == '\'') { inStr = true; i++; continue; }
            out.append(c); i++;
        }
        return out.toString();
    }

    /** 前端树根（{@code ecos_frontend/src}），供 F05-08 死调用差集 / F05-17 单源断言复用。 */
    static Path frontendRoot() {
        Path dir = repoRoot().resolve("ecos_frontend/src");
        assertTrue(Files.isDirectory(dir), "前端树不可达: " + dir);
        return dir;
    }
}
