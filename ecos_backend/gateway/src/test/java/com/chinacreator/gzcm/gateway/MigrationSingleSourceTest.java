package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F06-18 详细设计-06 数据层归位 — DDL 单源 + 版本唯一门禁。
 *
 * <p><b>断言（源码扫描，无 DB）</b>：AI 域新增 DDL（V200~V204）必须满足单源纪律：
 * <ol>
 *   <li><b>版本唯一</b>：V200~V204 每个版本键在迁移单源目录
 *       {@code docs/50-db_script/02-flyway-migration/gateway} 中恰好出现 1 次
 *       （Flyway 当前禁用，但一旦启用，同版本号多文件即确定性崩溃 —— 本门禁
 *       只管辖 AI 域新增段，存量 V50/V150/V155 重复版本属 R-22 裁决项，不在本域断言面）；</li>
 *   <li><b>无第二 DDL 源</b>：第二 DDL 源
 *       {@code docs/50-db_script/01-ddl-多方言/migration/12_to_8_schema.sql}
 *       （X-60）不得 {@code CREATE TABLE} 出任何一张 AI 新表；</li>
 *   <li><b>无模块级平行迁移</b>：任一模块
 *       {@code src/main/resources/db/migration/} 目录不得残存承载 AI 新表的
 *       {@code .sql}（各模块迁移目录当前应为空 —— DDL 自 V200 治理起收敛至单一目录）。</li>
 * </ol>
 *
 * <p>验收：{@code mvn -f ecos_backend/pom.xml -pl gateway -am test
 * -Dtest='MigrationSingleSourceTest#noSecondDdlSourceNoDuplicateVersion'}
 */
class MigrationSingleSourceTest {

    private static final Path SINGLE_SOURCE_REL =
            Paths.get("docs/50-db_script/02-flyway-migration/gateway");
    private static final Path SECOND_SOURCE_REL =
            Paths.get("docs/50-db_script/01-ddl-多方言/migration/12_to_8_schema.sql");
    private static final int[] AI_VERSIONS = {200, 201, 202, 203, 204};

    /** 四张 AI 新表（V202 是 ALTER-only，无表名 DDL，故不在此列单）。 */
    private static final Set<String> AI_NEW_TABLES = Set.of(
            "ecos_ai_tool_registry",
            "ecos_ai_tool_permission_binding",
            "ecos_ai_compression_batch",
            "ecos_ai_guardrail_decision_ledger"
    );

    private static Path repoRoot() throws IOException {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("ecos_backend"))) return p;
            Path parent = p.getParent();
            if (parent == null || parent.equals(p)) break;
            p = parent;
        }
        assertTrue(false, "cwd 未锚定 ECOS 仓库根（找不到 ecos_backend/）");
        return null;
    }

    @Test
    @DisplayName("AI DDL 无第二源 + 版本唯一（V200~V204 单源、无版本键碰撞）")
    void noSecondDdlSourceNoDuplicateVersion() throws IOException {
        Path repo = repoRoot();
        Path singleSource = repo.resolve(SINGLE_SOURCE_REL);
        assertTrue(Files.isDirectory(singleSource), "迁移单源目录缺失: " + singleSource);

        // (a) 版本唯一：V200~V204 每个版本键恰好 1 个文件
        Set<String> fileList = listSqlNames(singleSource);
        for (int v : AI_VERSIONS) {
            List<String> matches = new ArrayList<>();
            for (String n : fileList) {
                if (n.startsWith("V" + v + "__")) matches.add(n);
            }
            assertEquals(1, matches.size(),
                    "版本键 V" + v + " 应只对应 1 个迁移脚本（Flyway 版本唯一），实测 "
                            + matches.size() + ": " + matches);
        }

        // (b) 无第二 DDL 源：12_to_8_schema.sql 不得建出任何 AI 新表
        //     （若第二源文件不存在 = 已被 R-22 作废，直接视为 PASS）
        Path second = repo.resolve(SECOND_SOURCE_REL);
        if (Files.isRegularFile(second)) {
            String secondSql = Files.readString(second, StandardCharsets.UTF_8)
                    .replaceAll("(?m)^\\s*--.*$", "");
            for (String table : AI_NEW_TABLES) {
                String pattern = "(?i)CREATE\\s+TABLE[\\s\\S]{0,80}?" + table;
                assertTrue(!pattern0(pattern).matcher(secondSql).find(),
                        "第二 DDL 源 12_to_8_schema.sql 重复建出 AI 新表 `" + table + "`"
                                + "（违 DDL 单源，违 §3.1）");
            }
        }

        // (c) 无模块级平行迁移：任一模块 db/migration 目录不得含承载 AI 新表的 .sql
        Path backend = repo.resolve("ecos_backend");
        List<Path> moduleMigrationDirs;
        try (Stream<Path> walk = Files.walk(backend)) {
            moduleMigrationDirs = walk
                    .filter(Files::isDirectory)
                    .filter(path -> path.endsWith(Paths.get("src", "main", "resources", "db", "migration")))
                    .collect(Collectors.toList());
        }
        Set<String> violations = new HashSet<>();
        for (Path dir : moduleMigrationDirs) {
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> s = Files.list(dir)) {
                List<Path> files = s.filter(Files::isRegularFile)
                        .filter(f -> f.getFileName().toString().endsWith(".sql"))
                        .collect(Collectors.toList());
                for (Path f : files) {
                    String body = Files.readString(f, StandardCharsets.UTF_8)
                            .replaceAll("(?m)^\\s*--.*$", "");
                    for (String table : AI_NEW_TABLES) {
                        if (pattern0("(?i)CREATE\\s+TABLE[\\s\\S]{0,80}?" + table)
                                .matcher(body).find()) {
                            violations.add(dir + "/" + f.getFileName());
                        }
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "模块级 db/migration 出现承载 AI 新表的平行 DDL（违 DDL 单源，应归并至 "
                        + SINGLE_SOURCE_REL + "）:\n" + String.join("\n", violations));
    }

    private static java.util.regex.Pattern pattern0(String regex) {
        return java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE);
    }

    private static Set<String> listSqlNames(Path dir) throws IOException {
        Set<String> names = new HashSet<>();
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> files = s.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().endsWith(".sql"))
                    .collect(Collectors.toList());
            for (Path f : files) {
                names.add(f.getFileName().toString());
            }
        }
        return names;
    }
}
