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

    /**
     * 数据面五件事（§2.9.5③；#147AE）：V307 必须为六枚仓库播种排除清单、
     * 不得改写纳入清单、必须保留「运维已填不覆盖」的 NULL 守卫，
     * 且播种值喂给 {@code allows()} 后确实拦得住敏感路径。载体存在而数据为空
     * （V304 之后的真实状态）等于没有这条红线。
     */
    @Test
    void v307SeedsExcludePathsForAllSixRepositoriesAndItActuallyFilters() throws IOException {
        Path seed = repoRoot().resolve("docs/50-db_script/02-flyway-migration/gateway/"
                + "V307__git_repo_exclude_paths_seed.sql");
        assertTrue(Files.isRegularFile(seed), "V307 数据面脚本不在位（列有载体、数据为空＝红线无载体）");
        String sql = Files.readString(seed, StandardCharsets.UTF_8);

        List<String> pinned = List.of("agent-collab", "cognitive-model", "dw-pipeline",
                "kb-asset-tree", "ontology-model", "scenario-def");
        java.util.Set<String> seeded = new java.util.TreeSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("WHERE repository_id = '([a-z-]+)'").matcher(sql);
        while (m.find()) {
            seeded.add(m.group(1));
        }
        assertEquals(new java.util.TreeSet<>(pinned), seeded,
                "V307 必须且只能播种 §2.9.5③ 的六枚仓库；实测=" + seeded);

        assertTrue(sql.contains("exclude_paths IS NULL"),
                "缺 NULL 守卫 ⇒ 重跑会覆盖运维已自定义的排除清单");
        assertFalse(sql.contains("include_paths ="),
                "V307 不得改纳入清单：纳入非空即「未命中即拒」，写错会让归档腿整域 FAILED（本批裁决只播排除）");

        GitRepository seededRepo = new GitRepository();
        seededRepo.setExcludePaths(GitRepository.splitCsv(
                ".env,credential,secrets,private-key,session"));
        assertFalse(seededRepo.allows("secrets/token.json"), "播种后敏感目录必须被消费面拦住");
        assertFalse(seededRepo.allows("credential/db.yml"));
        assertFalse(seededRepo.allows(".env"));
        assertTrue(seededRepo.allows("rules/r1.json"), "业务归档路径不得被误伤");
        assertTrue(seededRepo.allows("cognitive-models/m1.json"));
    }
}
