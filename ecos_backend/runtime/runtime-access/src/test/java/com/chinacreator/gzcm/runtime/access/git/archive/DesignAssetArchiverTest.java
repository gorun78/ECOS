package com.chinacreator.gzcm.runtime.access.git.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.chinacreator.gzcm.runtime.access.git.GitRepoPathResolver;
import com.chinacreator.gzcm.runtime.access.git.GitRepoRegistry;
import com.chinacreator.gzcm.runtime.access.git.GitRepositoryServiceImpl;
import com.chinacreator.gzcm.runtime.access.git.GitService;
import com.chinacreator.gzcm.runtime.access.git.entity.GitCommitView;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;

/**
 * 设计资产统一归档器行为门（#147E；§2.9.5② / §2.9.7 全域接入）。
 *
 * <p>钉三件事：① 归档落盘路径与 tag 口径；② 未注册/禁用/工作树未解析 ⇒
 * {@code NOT_AVAILABLE}（合法现状态，禁伪成功）；③ 路径穿越 ⇒ {@code FAILED}。</p>
 */
class DesignAssetArchiverTest {

    /** 最小注册表假面。 */
    private static final class FakeRegistry implements GitRepoRegistry {
        private final List<GitRepository> rows = new ArrayList<>();

        FakeRegistry add(GitRepository row) {
            rows.add(row);
            return this;
        }

        @Override
        public GitRepository findByRepositoryId(String repositoryId) {
            return rows.stream().filter(r -> r.getRepositoryId().equals(repositoryId)).findFirst().orElse(null);
        }

        @Override
        public List<GitRepository> findActive() {
            return rows.stream().filter(GitRepository::isEnabled).toList();
        }

        @Override
        public int insert(GitRepository repository) {
            rows.add(repository);
            return 1;
        }

        @Override
        public int softDelete(String repositoryId) {
            return 0;
        }
    }

    /** 记录调用的 Git 桩（只实现本门需要的四条腿）。 */
    private static final class RecordingGit implements GitService {
        String lastCommitMessage;
        String lastTag;
        Optional<String> readContent = Optional.empty();

        @Override
        public String commitAllChanges(GitRepository repository, String commitMessage,
                                       String authorName, String authorEmail) {
            this.lastCommitMessage = commitMessage;
            return "sha-" + repository.getRepositoryId();
        }

        @Override
        public void pull(GitRepository repository, String remote, String branch) {
        }

        @Override
        public String createTag(GitRepository repository, String tagName, String message) {
            this.lastTag = tagName;
            return tagName;
        }

        @Override
        public String getCurrentCommitId(GitRepository repository) {
            return "sha-" + repository.getRepositoryId();
        }

        @Override
        public Map<String, Object> diff(GitRepository repository, String fromRef, String toRef) {
            return Map.of();
        }

        @Override
        public List<GitCommitView> listCommits(GitRepository repository, int offset, int limit) {
            return List.of();
        }

        @Override
        public void clone(String remoteUrl, String localPath, String username, String password) {
        }

        @Override
        public void pull(String localPath, String remote, String branch) {
        }

        @Override
        public void push(String localPath, String username, String password) {
        }

        @Override
        public void commit(String localPath, String message, List<String> filePaths) {
        }

        @Override
        public void createBranch(String localPath, String branchName) {
        }

        @Override
        public void checkoutBranch(String localPath, String branchName) {
        }

        @Override
        public void merge(String localPath, String branchName) {
        }

        @Override
        public List<String> listBranches(String localPath) {
            return List.of();
        }

        @Override
        public List<String> listCommitShasForPath(String localPath, String relativePath, int limit) {
            return List.of();
        }

        @Override
        public Optional<String> readFileAtRef(GitRepository repository, String ref, String relativePath) {
            return readContent;
        }
    }

    private static GitRepository repo(String id, String root, boolean enabled) {
        GitRepository r = new GitRepository();
        r.setRepositoryId(id);
        r.setName(id);
        r.setRepoRoot(root);
        r.setDefaultBranch("main");
        r.setEnabled(enabled);
        return r;
    }

    private static DesignAssetArchiver archiver(FakeRegistry registry, Path root, RecordingGit git) {
        GitRepoPathResolver resolver = repository -> root.toAbsolutePath().toString();
        return new DesignAssetArchiver(new GitRepositoryServiceImpl(registry, resolver), git);
    }

    @Test
    void archiveWritesFileAndReturnsRealCommitId(@TempDir Path root) throws Exception {
        FakeRegistry registry = new FakeRegistry().add(repo("kb-asset-tree", "kb-Assets", true));
        RecordingGit git = new RecordingGit();
        DesignAssetArchiver archiver = archiver(registry, root, git);

        ArchiveOutcome outcome = archiver.archive("kb-asset-tree", "rules", "r-1", "2.0",
                "{\"name\":\"credit-rule\"}", "archive rule", "alice");

        assertEquals(ArchiveOutcome.Status.ARCHIVED, outcome.status());
        assertEquals("sha-kb-asset-tree", outcome.commitId());
        assertEquals("rules/r-1/2.0", outcome.tag());
        assertEquals("rules/r-1/2.0", git.lastTag);
        Path file = root.resolve("rules/r-1/2.0.json");
        assertTrue(Files.isRegularFile(file), "归档文件必须落盘");
        assertEquals("{\"name\":\"credit-rule\"}", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void unregisteredOrDisabledRepoIsNotAvailableNotFailure(@TempDir Path root) {
        FakeRegistry registry = new FakeRegistry()
                .add(repo("cognitive-model", "cognitive", false));
        RecordingGit git = new RecordingGit();
        DesignAssetArchiver archiver = archiver(registry, root, git);

        assertEquals(ArchiveOutcome.Status.NOT_AVAILABLE,
                archiver.archive("cognitive-model", "models", "m1", "1", "{}", null, null).status());
        assertEquals(ArchiveOutcome.Status.NOT_AVAILABLE,
                archiver.archive("mystery-repo", "models", "m1", "1", "{}", null, null).status());
    }

    @Test
    void unresolvedWorkingTreeIsNotAvailable(@TempDir Path root) {
        FakeRegistry registry = new FakeRegistry().add(repo("agent-collab", "agents", true));
        GitService noResolver = new RecordingGit();
        DesignAssetArchiver archiver = new DesignAssetArchiver(
                new GitRepositoryServiceImpl(registry, (GitRepoPathResolver) null), noResolver);

        ArchiveOutcome outcome = archiver.archive("agent-collab", "agents", "a1", "1", "{}", null, null);
        assertEquals(ArchiveOutcome.Status.NOT_AVAILABLE, outcome.status());
        assertTrue(outcome.reason().contains("工作树未解析"));
    }

    @Test
    void pathTraversalIsRejectedAsFailure(@TempDir Path root) {
        FakeRegistry registry = new FakeRegistry().add(repo("scenario-def", "scenarios", true));
        DesignAssetArchiver archiver = archiver(registry, root, new RecordingGit());

        ArchiveOutcome outcome = archiver.archive("scenario-def", "scenarios", "../escape", "1", "{}", null, null);
        assertEquals(ArchiveOutcome.Status.FAILED, outcome.status());
        assertFalse(Files.exists(root.resolve("escape")));
    }

    @Test
    void readAtRefDelegatesAndEmptyMeansMissing(@TempDir Path root) {
        FakeRegistry registry = new FakeRegistry().add(repo("kb-asset-tree", "kb", true));
        RecordingGit git = new RecordingGit();
        DesignAssetArchiver archiver = archiver(registry, root, git);

        assertTrue(archiver.readAtRef("kb-asset-tree", "rules", "r1", "1.0", "abc").isEmpty());

        git.readContent = Optional.of("{\"v\":1}");
        assertEquals(Optional.of("{\"v\":1}"),
                archiver.readAtRef("kb-asset-tree", "rules", "r1", "1.0", "abc"));
    }

    @Test
    void includeExcludeListsGateArchive(@TempDir Path root) {
        GitRepository restricted = repo("kb-asset-tree", "kb", true);
        // include 清单非空 ⇒ 只放行命中前缀；exclude 前缀即使也在 include 内也被拒（排除优先）
        restricted.setIncludePaths(java.util.List.of("rules", "models"));
        restricted.setExcludePaths(java.util.List.of("rules"));
        FakeRegistry registry = new FakeRegistry().add(restricted);
        DesignAssetArchiver archiver = archiver(registry, root, new RecordingGit());

        ArchiveOutcome excluded = archiver.archive("kb-asset-tree", "rules", "r1", "1.0", "{}", null, null);
        assertEquals(ArchiveOutcome.Status.FAILED, excluded.status(), "排除优先于纳入");
        assertTrue(excluded.reason().contains("排除"));

        assertEquals(ArchiveOutcome.Status.ARCHIVED,
                archiver.archive("kb-asset-tree", "models", "m1", "1.0", "{}", null, null).status(),
                "纳入前缀命中");

        ArchiveOutcome notIncluded = archiver.archive("kb-asset-tree", "agents", "a1", "1.0", "{}", null, null);
        assertEquals(ArchiveOutcome.Status.FAILED, notIncluded.status(),
                "纳入清单非空且不命中即拒");
    }
}
