package com.chinacreator.gzcm.runtime.access.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;

/**
 * 注册表纳入/排除清单载体（§2.9.5③ / V304；#147Q）。
 *
 * <p>钉四件事：① 实体 {@code allows} 语义（排除优先、纳入非空需命中）；② 逗号串解析；
 * ③ DAO 读列含 include_paths/exclude_paths（剥注释、SQL 面）；④ V304 列与实体字段同批在位。</p>
 */
class GitRepoIncludeExcludeGateTest {

    private static Path repoRoot() {
        Path cur = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path p = cur; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("ecos_backend"))) {
                return p;
            }
        }
        return fail("无法定位仓库根: user.dir=" + cur);
    }

    @Test
    void allowsSemanticsExcludeWinsOverInclude() {
        GitRepository repo = new GitRepository();
        repo.setExcludePaths(List.of("rules/sensitive", "secrets"));
        assertFalse(repo.allows("rules/sensitive/r1.json"), "命中排除前缀必拒");
        assertFalse(repo.allows("secrets/token.json"));

        repo.setIncludePaths(List.of("rules"));
        assertTrue(repo.allows("rules/r1.json"), "纳入前缀命中");
        assertFalse(repo.allows("models/m1.json"), "纳入清单非空且不命中即拒");

        repo.setIncludePaths(List.of("rules"));
        repo.setExcludePaths(List.of("rules/sensitive"));
        assertTrue(repo.allows("rules/r1.json"));
        assertFalse(repo.allows("rules/sensitive/r1.json"), "排除优先于纳入");

        GitRepository open = new GitRepository();
        assertTrue(open.allows("anything/a.json"), "清单为空=全量纳入（现状兼容）");
        assertFalse(open.allows(""), "空路径拒绝");
    }

    @Test
    void splitCsvHandlesBlanksAndTrimming() {
        assertEquals(List.of("a", "b"), GitRepository.splitCsv(" a , b,, "));
        assertTrue(GitRepository.splitCsv(null).isEmpty());
        assertTrue(GitRepository.splitCsv("   ").isEmpty());
    }

    @Test
    void daoReadsBothColumnsAndV304CarriesThem() throws IOException {
        Path dao = repoRoot().resolve("ecos_backend/runtime/runtime-access/src/main/java/com/"
                + "chinacreator/gzcm/runtime/access/git/dao/GitRepoRegistryDao.java");
        String daoText = Files.readString(dao, StandardCharsets.UTF_8);
        assertTrue(daoText.contains("include_paths"), "DAO 读列缺 include_paths");
        assertTrue(daoText.contains("exclude_paths"), "DAO 读列缺 exclude_paths");

        Path ddl = repoRoot().resolve("docs/50-db_script/02-flyway-migration/gateway/"
                + "V304__git_repo_include_exclude_paths.sql");
        assertTrue(Files.isRegularFile(ddl), "V304 不在位");
        String sql = Files.readString(ddl, StandardCharsets.UTF_8);
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS include_paths"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS exclude_paths"));
    }
}
