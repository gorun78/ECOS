package com.chinacreator.gzcm.workspace.object;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService;
import com.chinacreator.gzcm.runtime.access.git.GitService;
import com.chinacreator.gzcm.runtime.access.git.archive.ArchiveOutcome;
import com.chinacreator.gzcm.runtime.access.git.archive.DesignAssetArchiver;
import com.chinacreator.gzcm.runtime.access.git.entity.GitCommitView;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;

/**
 * 对象版本 Git 端口（DS-4 对象运行时域）：提交即版本、按提交重建历史、缺仓/缺内容显式处理。
 */
class WorkspaceObjectVersionArchivePortTest {

    private static final String OBJ = "obj-1";
    private static final String SHA1 = "aaa1111111111111111111111111111111111111";
    private static final String SHA2 = "bbb2222222222222222222222222222222222222";

    private final DesignAssetArchiver archiver = mock(DesignAssetArchiver.class);
    private final GitService git = mock(GitService.class);
    private final GitRepositoryService repos = mock(GitRepositoryService.class);
    private final GitRepository repo = new GitRepository();
    private WorkspaceObjectVersionArchivePort port;

    @BeforeEach
    void setUp() throws Exception {
        repo.setRepositoryId("ontology-model");
        repo.setLocalPath("/tmp/ecos-git-repos/ontology");
        repo.setEnabled(true);
        when(repos.getRepositoryById("ontology-model")).thenReturn(repo);
        port = new WorkspaceObjectVersionArchivePort(archiver, git, repos);
    }

    private static GitCommitView commit(String sha, String msg, long at) {
        return new GitCommitView(sha, msg, "admin", "admin@ecos.local", new Date(at));
    }

    @Test
    void archiveWritesThroughDesignAssetArchiver() {
        when(archiver.archive(eq("ontology-model"), eq("objects"), eq(OBJ), eq("3"),
                anyString(), anyString(), anyString()))
                .thenReturn(ArchiveOutcome.archived(SHA1, null));

        Optional<String> commit = port.archive(OBJ, "Customer", 3, "{}", "update", "admin");

        assertTrue(commit.isPresent());
        assertEquals(SHA1, commit.get());
    }

    @Test
    void historyIsRebuiltNewestFirstWithEntity() throws Exception {
        when(git.listCommits(eq(repo), anyInt(), anyInt())).thenReturn(List.of(
                commit(SHA2, "Object snapshot obj-1 v2 entity=Customer", 2000L),
                commit(SHA1, "Object snapshot obj-1 v1 entity=Customer", 1000L)));

        List<ObjectVersionRecordAlias> rows = port.list(OBJ).stream()
                .map(r -> new ObjectVersionRecordAlias(r.id(), r.versionNo(), r.entityCode(), r.createdAt()))
                .toList();

        assertEquals(2, rows.size());
        assertEquals(2, rows.get(0).versionNo());
        assertEquals("git-" + SHA2, rows.get(0).id());
        assertEquals("Customer", rows.get(0).entityCode());
        assertEquals(2, port.maxVersion(OBJ));
    }

    @Test
    void getReadsSnapshotAtCommit() throws Exception {
        when(git.listCommits(eq(repo), anyInt(), anyInt())).thenReturn(List.of(
                commit(SHA1, "Object snapshot obj-1 v1 entity=Customer", 1000L)));
        when(archiver.readAtRef(eq("ontology-model"), eq("objects"), eq(OBJ), eq("1"), eq(SHA1)))
                .thenReturn(Optional.of("{\"name\":\"a\"}"));

        var v = port.get(OBJ, 1);

        assertTrue(v.isPresent());
        assertEquals("{\"name\":\"a\"}", v.get().snapshot());
        assertTrue(port.get(OBJ, 9).isEmpty());
    }

    @Test
    void unregisteredRepositoryFailsLoud() throws Exception {
        when(repos.getRepositoryById("ontology-model"))
                .thenThrow(new GitRepositoryService.GitRepositoryException("not registered"));

        assertThrows(IllegalStateException.class, () -> port.list(OBJ));
    }

    private record ObjectVersionRecordAlias(String id, int versionNo, String entityCode, Long createdAt) {
    }
}
