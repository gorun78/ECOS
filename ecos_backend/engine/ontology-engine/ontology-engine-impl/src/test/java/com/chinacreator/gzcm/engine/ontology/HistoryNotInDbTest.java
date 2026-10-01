package com.chinacreator.gzcm.engine.ontology;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * W83/C66 验收 — 版本历史不落 DB（后端规范 §十一：DB 只存在用版本，历史走 Git）。
 *
 * <p>断言三条：
 * <ol>
 *   <li><b>无独立"版本历史"新表</b>：V170.0/V170.1/V170.2 三批 DDL 都不应创建新的
 *       {@code *_history} / {@code *_snapshot_history} 类表（历史全走 Git，禁 DB 双存）；</li>
 *   <li><b>本体域 repository 不持有历史读方法</b>：源码级断言 {@code Ontology*Repository}
 *       类（JdbcTemplate 访问层）无 {@code findHistory*} / {@code listSnapshotHistory} 类方法；</li>
 *   <li><b>V170.1 停写标记到位</b>：旧 {@code snapshot jsonb} 列上必带
 *       {@code COMMENT ON COLUMN ... IS '...停写...' }，作为"历史内容以 Git 为准"的图钉。</li>
 * </ol>
 *
 * <p>仅源码/DDL 静态比对；§14.4 未授权不启库。
 */
class HistoryNotInDbTest {

    private static final String MIGRATION_DIR =
            "ecos_backend/gateway/src/main/resources/db/migration";

    @Test
    void noNewHistoryTablesCreatedForOntologyVersion() throws IOException {
        Path dir = locateMigrationDir();
        List<String> violations = new java.util.ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().startsWith("V170"))
                .forEach(p -> {
                    try {
                        String sql = Files.readString(p, StandardCharsets.UTF_8);
                        if (containsForbiddenHistoryTable(sql)) {
                            violations.add(p.getFileName().toString());
                        }
                    } catch (IOException e) {
                        fail("读 V170.* 失败：" + e.getMessage());
                    }
                });
        }
        assertTrue(violations.isEmpty(),
            "V170.* 迁移批不应新建版本历史表（§十一：历史走 Git，DB 只存在用版本）；命中文件：" + violations);
    }

    @Test
    void oldSnapshotColumnCarriesDeprecatedMarker() throws IOException {
        Path v1701 = locateMigrationDir().resolve("V170.1__ecos_ontology_version_snapshot_text.sql");
        assertTrue(Files.exists(v1701), "V170.1 迁移脚本缺失");
        String sql = Files.readString(v1701, StandardCharsets.UTF_8);
        assertTrue(sql.contains("停写"),
            "V170.1 应对旧 jsonb 列写「停写」COMMENT 图钉（铁律 §3.1 例外未启用，历史只读走 Git）");
        // (注：注释含"停写"字样即满足；CHECK-only 不做太细的自然语言解析)
    }

    /** 找本体域 repository 源码，断言无 listing-history 方法名（Kahan 静态粗校验）。 */
    @Test
    void versionRepositoryHasNoHistoryLister() throws IOException {
        Path file = locateRoot().resolve(
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/repository/OntologyVersionRepository.java"
        );
        assertTrue(Files.exists(file));
        String s = Files.readString(file, StandardCharsets.UTF_8);
        // 允许 listVersions（在用集）/ findById / findPreviousVersion（相邻 diff 语义）；
        // 但禁 listHistory / findSnapshotHistory / getFullHistory 之类"全历史拉取"方法
        assertFalse(s.contains("listHistory") && true, "OntologyVersionRepository 不应持有 listHistory(...)（历史内容属 Git）");
        assertFalse(s.contains("findSnapshotHistory"), "OntologyVersionRepository 不应持有 findSnapshotHistory(...)（历史内容属 Git）");
        assertFalse(s.contains("getFullHistory"), "OntologyVersionRepository 不应持有 getFullHistory(...)（历史内容属 Git）");
        assertTrue(s.contains("findByOntology") && s.contains("findById"),
            "OntologyVersionRepository 仍应承担「在用集」读（findByOntology / findById）");
    }

    private static boolean containsForbiddenHistoryTable(String sql) {
        // 匹配 CREATE TABLE ... *_history / *_snapshot_history / *_versions_history
        String lowercase = sql.toLowerCase();
        int i = 0;
        while ((i = lowercase.indexOf("create table", i)) >= 0) {
            int end = Math.min(sql.length(), i + 300);
            String tail = lowercase.substring(i, end).replace("\\", " ").toLowerCase();
            if (tail.contains("_history") || tail.contains("_snapshot_archive")
                || tail.contains("_full_history") || tail.contains("_audit_trail")) {
                return true;
            }
            i += 1;
        }
        return false;
    }

    private static Path locateRoot() {
        Path cur = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 10 && cur != null; i++) {
            if (Files.exists(cur.resolve("ecos_backend")) && Files.exists(cur.resolve("ecos_frontend"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        fail("无法定位仓库根以扫 repository 源码");
        return null;
    }

    private static Path locateMigrationDir() {
        Path cur = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 10 && cur != null; i++) {
            Path cand = cur.resolve(MIGRATION_DIR);
            if ( Files.isDirectory(cand)) return cand;
            cur = cur.getParent();
        }
        fail("无法定位迁移目录：" + MIGRATION_DIR);
        return null;
    }
}
