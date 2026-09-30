package com.chinacreator.gzcm.engine.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

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

/**
 * F02-13 / ARCH-06 架构守卫（源码扫描，无 DB）：
 * data-engine-impl {@code src/main} <b>禁</b> {@code java.sql.DriverManager}（import 与
 * {@code DriverManager.getConnection} 调用），唯一合法建连白名单 =
 * {@code runtime/runtime-access/**} 的 {@code JdbcConnector}。
 *
 * <p>命中违规 = FAIL（D-12 历史缺口 = 5 处 DriverManager，W60 已清偿到
 * {@code AdapterConnectionBridge} + {@code JdbcConnector}；本测试永久守卫）。
 * 注释行（{@code //} / {@code *} / {@code /*} 开头）忽略。</p>
 */
@DisplayName("F02-13 / ARCH-06：data-engine 主源码禁 java.sql.DriverManager")
class NoDriverManagerArchTest {

    private static final Path MODULE_SRC_MAIN = Path.of(
            "src/main/java/com/chinacreator/gzcm/engine/data");

    private static final Pattern DRM_IMPORT =
            Pattern.compile("import\\s+java\\.sql\\.DriverManager");
    private static final Pattern DRM_GETCONN =
            Pattern.compile("DriverManager\\.getConnection\\s*\\(");

    @Test
    @DisplayName("data-engine-impl main 源码 0 处 java.sql DriverManager 使用")
    void noDriverManagerInDataEngineMain() throws IOException {
        Path root = moduleRoot();
        Path srcMain = root.resolve("src/main");
        assertTrue(Files.isDirectory(srcMain), "缺 data-engine-impl/src/main: " + srcMain);
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(srcMain)) {
            for (Path f : (List<Path>) files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                int lin = 0;
                for (String raw : lines) {
                    lin++;
                    String t = raw.trim();
                    if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) {
                        continue;
                    }
                    if (DRM_IMPORT.matcher(raw).find()
                            || DRM_GETCONN.matcher(raw).find()) {
                        violations.add(f.getFileName() + ":" + lin + ": " + t);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "data-engine-impl 主源码禁 java.sql.DriverManager（F02-13 / ARCH-06；"
                        + "合法白名单 = runtime/runtime-access 的 JdbcConnector）：\n"
                        + String.join("\n", violations));
    }

    /** 从 user.dir 上溯仓库根，定位 data-engine-impl 模块根（含 src/main + pom.xml）。 */
    private static Path moduleRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path repo = dir;
        while (repo != null) {
            if (Files.isRegularFile(repo.resolve(".git")) || Files.isDirectory(repo.resolve(".git"))) {
                return repo.resolve("ecos_backend/engine/data-engine/data-engine-impl");
            }
            repo = repo.getParent();
        }
        // 兜底：从 user.dir 上溯找含 src/main + pom.xml 的模块根
        Path cur = dir;
        while (cur != null) {
            if (Files.isDirectory(cur.resolve("src/main")) && Files.isRegularFile(cur.resolve("pom.xml"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("未能定位 data-engine-impl 模块根（缺 .git / src/main + pom.xml）");
    }
}
