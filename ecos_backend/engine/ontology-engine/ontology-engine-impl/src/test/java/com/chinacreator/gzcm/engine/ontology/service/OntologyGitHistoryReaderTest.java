package com.chinacreator.gzcm.engine.ontology.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.engine.ontology.model.OntologyVersion;
import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService;
import com.chinacreator.gzcm.runtime.access.git.GitService;
import com.chinacreator.gzcm.runtime.access.git.entity.GitCommitView;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;

/**
 * DS-4 本体版本 Git 真源读面（2026-11-08）。
 *
 * <p>钉住：只认 {@code Publish ontology <id> <version>} 提交、内容按 commit 读回、
 * 缺归档文件/仓库不可用必须显式失败（禁空历史假绿）、id = {@code git-<commitId>}。</p>
 */
class OntologyGitHistoryReaderTest {

    private static final String SHA_1 = "9b03ceec80deebc56ec84bcd3b7a094567bd52a1";
    private static final String SHA_2 = "fcfdee94aa9cbd0f906ec61a44da644a2c8b48a0";

    private final GitService git = mock(GitService.class);
    private final GitRepositoryService repos = mock(GitRepositoryService.class);
    private final GitRepository repo = new GitRepository();
    private OntologyGitHistoryReader reader;

    @BeforeEach
    void setUp() throws Exception {
        repo.setRepositoryId("ontology-model");
        repo.setLocalPath("/tmp/ecos-git-repos/ontology");
        repo.setEnabled(true);
        when(repos.getRepositoryById("ontology-model")).thenReturn(repo);
        reader = new OntologyGitHistoryReader(git, repos);
    }

    private static GitCommitView commit(String sha, String msg, Date at) {
        return new GitCommitView(sha, msg, "admin", "admin@ecos.local", at);
    }

    private void commits(GitCommitView... views) throws Exception {
        when(git.listCommits(eq(repo), anyInt(), anyInt())).thenReturn(List.of(views));
    }

    private void archive(String sha, String version, String content) throws Exception {
        when(git.readFileAtRef(eq(repo), eq(sha), eq("ontologies/ont_entmgmt/" + version + ".json")))
                .thenReturn(Optional.of(content));
    }

    @Test
    void listsPublishedVersionsNewestFirstFromGitWithoutDbIndex() throws Exception {
        commits(commit(SHA_2, "Publish ontology ont_entmgmt 1.0.1", new Date(2_000L)),
                commit(SHA_1, "Publish ontology ont_entmgmt 1.0.0", new Date(1_000L)));
        archive(SHA_2, "1.0.1", "{\"v\":\"1.0.1\"}");
        archive(SHA_1, "1.0.0", "{\"v\":\"1.0.0\"}");

        List<OntologyVersion> rows = reader.listPublished("ont_entmgmt");

        assertEquals(2, rows.size());
        assertEquals("1.0.1", rows.get(0).getVersionNo());
        assertEquals("git-" + SHA_2, rows.get(0).getId());
        assertEquals("{\"v\":\"1.0.1\"}", rows.get(0).getSnapshot());
        assertEquals(SHA_2, rows.get(0).getGitRef());
        assertEquals("admin", rows.get(0).getPublisher());
        assertEquals("Published", rows.get(0).getStatus());
    }

    @Test
    void filtersOtherOntologiesAndUnrelatedCommits() throws Exception {
        commits(commit(SHA_2, "Publish ontology other 2.0.0", new Date(2_000L)),
                commit(SHA_1, "Publish ontology ont_entmgmt 1.0.0", new Date(1_000L)),
                commit("deadbeef", "chore: unrelated", new Date(500L)));
        archive(SHA_1, "1.0.0", "{}");

        List<OntologyVersion> rows = reader.listPublished("ont_entmgmt");

        assertEquals(1, rows.size());
        assertEquals("1.0.0", rows.get(0).getVersionNo());
    }

    @Test
    void findByIdResolvesGitPrefixedId() throws Exception {
        commits(commit(SHA_2, "Publish ontology ont_entmgmt 1.0.1", new Date(2_000L)));
        archive(SHA_2, "1.0.1", "{}");

        assertTrue(reader.findById("git-" + SHA_2).isPresent());
        assertTrue(reader.findById("ver5001").isEmpty(), "库内 id 不归 Git 读面");
        assertTrue(reader.findById(null).isEmpty());
    }

    @Test
    void missingArchiveFileFailsLoudInsteadOfSkipping() throws Exception {
        commits(commit(SHA_1, "Publish ontology ont_entmgmt 1.0.0", new Date(1_000L)));
        when(git.readFileAtRef(eq(repo), eq(SHA_1), anyString())).thenReturn(Optional.empty());

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> reader.listPublished("ont_entmgmt"));
        assertTrue(e.getMessage().contains("归档文件缺失"), e.getMessage());
    }

    @Test
    void unregisteredRepositoryFailsLoud() throws Exception {
        when(repos.getRepositoryById("ontology-model"))
                .thenThrow(new GitRepositoryService.GitRepositoryException("not registered"));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> reader.listPublished("ont_entmgmt"));
        assertTrue(e.getMessage().contains("未注册"), e.getMessage());
    }
}
