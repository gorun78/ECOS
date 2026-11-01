package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveRequest;
import com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveResult;
import com.chinacreator.gzcm.runtime.access.git.GitRepoPathResolver;
import com.chinacreator.gzcm.runtime.access.git.GitRepoRegistry;
import com.chinacreator.gzcm.runtime.access.git.GitRepositoryServiceImpl;
import com.chinacreator.gzcm.runtime.access.git.GitServiceImpl;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H4-T4 真 JGit 正路径验收 —— 不 mock {@code GitService}，用 {@link GitServiceImpl} +
 * {@link GitRepositoryServiceImpl} + {@code @TempDir} 真仓库，证明归档确实产生 commit 与 tag。
 *
 * <p> Mockito 侧（{@link OntologyGitServiceImplTest}）只能证明「编排调了对的方法」，无法证明
 * JGit 真的落盘；本类补的正是这段证据，并以 {@code rev-parse HEAD} 与 tag ref 双向核对 commitId 非伪造。
 *
 * <p>#147C 装配口径订正：{@link GitRepositoryServiceImpl} 已去掉内存注册态，注册行一律走
 * {@link GitRepoRegistry}（生产属主 = {@code ecos_control.ecos_git_repo}），{@code localPath}
 * 一律由 {@link GitRepoPathResolver} 拼接（生产根单源 = sys_config {@code ecos_git_repo_root}）。
 * 本测试类只提供<b>测试面</b>假注册表，路径解析仍按「根目录 + 注册表 {@code repo_root} 相对段」
 * 的真实形状走，<b>不</b>把 {@code localPath} 当登记事实写死（那正是原双套路径源的违例形状）。
 */
class OntologyGitServiceImplJgitTest {

    private static final String REPO_ID = "jgit-ontology-it";
    private static final String ASSET_ID = "ont-jgit-001";
    private static final String VERSION_NO = "1.0.0";
    private static final String EXPECTED_TAG = "ontology/" + ASSET_ID + "/" + VERSION_NO;

    /** 注册表 {@code repo_root} = 仓库根下的相对目录名（与 V300 种子口径一致）。 */
    private static final String REPO_ROOT_DIR_NAME = "ontology-model";

    /** 测试面假注册表：只承载注册行的读写，不代表生产属主（生产属主是 DAO 落 {@code ecos_git_repo}）。 */
    private static final class FakeGitRepoRegistry implements GitRepoRegistry {

        private final Map<String, GitRepository> rows = new LinkedHashMap<>();

        @Override
        public GitRepository findByRepositoryId(String repositoryId) {
            GitRepository row = rows.get(repositoryId);
            return row == null || row.isEnabled() ? row : cloneOf(row);
        }

        @Override
        public List<GitRepository> findActive() {
            List<GitRepository> out = new ArrayList<>();
            for (GitRepository row : rows.values()) {
                if (row.isEnabled()) {
                    out.add(cloneOf(row));
                }
            }
            out.sort(Comparator.comparing(GitRepository::getRepositoryId));
            return out;
        }

        @Override
        public int insert(GitRepository repository) {
            rows.put(repository.getRepositoryId(), cloneOf(repository));
            return 1;
        }

        @Override
        public int softDelete(String repositoryId) {
            GitRepository row = rows.get(repositoryId);
            if (row == null) {
                return 0;
            }
            row.setEnabled(false);
            return 1;
        }

        private static GitRepository cloneOf(GitRepository src) {
            GitRepository copy = new GitRepository();
            copy.setRepositoryId(src.getRepositoryId());
            copy.setName(src.getName());
            copy.setLocalPath(src.getLocalPath());
            copy.setRepoRoot(src.getRepoRoot());
            copy.setDefaultBranch(src.getDefaultBranch());
            copy.setEnabled(src.isEnabled());
            return copy;
        }
    }

    /**
     * 属主侧路径解析接缝的测试实现：仓库根（相当于 sys_config {@code ecos_git_repo_root}）
     * ＋注册表行 {@code repo_root} 相对段 ⇒ 绝对路径；缺 {@code repo_root} 时按 {@code repositoryId}
     * 兜底（与 {@code GitRepoPathResolver} 契约一致）。
     */
    private static GitRepoPathResolver resolverUnderRoot(Path gitRepoRoot) {
        return repository -> {
            if (repository == null) {
                return null;
            }
            String dir = repository.getRepoRoot() != null && !repository.getRepoRoot().isBlank()
                    ? repository.getRepoRoot() : repository.getRepositoryId();
            return dir == null || dir.isBlank() ? null : gitRepoRoot.resolve(dir).toString();
        };
    }

    private GitRepositoryServiceImpl registryWithRealRepo(Path gitRepoRoot) throws Exception {
        GitRepository repo = new GitRepository();
        repo.setRepositoryId(REPO_ID);
        repo.setName("JGit IT Ontology Archive");
        repo.setRepoRoot(REPO_ROOT_DIR_NAME);
        repo.setDefaultBranch("main");
        repo.setEnabled(true);
        GitRepositoryServiceImpl registry =
                new GitRepositoryServiceImpl(new FakeGitRepoRegistry(), resolverUnderRoot(gitRepoRoot));
        registry.registerRepository(repo);
        return registry;
    }

    private OntologyGitArchiveRequest request(String repositoryId, String assetId) {
        OntologyGitArchiveRequest req = new OntologyGitArchiveRequest();
        req.setRepositoryId(repositoryId);
        req.setAssetType("ontology");
        req.setAssetId(assetId);
        req.setVersionNo(VERSION_NO);
        req.setContent("{\"ontologyId\":\"" + assetId + "\",\"versionNo\":\"" + VERSION_NO + "\"}");
        req.setCommitMessage("Publish ontology " + assetId + " " + VERSION_NO);
        return req;
    }

    @Test
    @DisplayName("H4-T4 正路径：真 JGit 产出 commit+tag，commitId == rev-parse HEAD，工作树无残留")
    void archiveVersion_withRealJGitRepository_producesRealCommitAndTag(@TempDir Path gitRoot) throws Exception {
        // GitServiceImpl 不做 Git.init()，仓库必须由调用方预先初始化 —— 这正是生产链路缺注册表时的表现
        Path repoDir = gitRoot.resolve(REPO_ROOT_DIR_NAME);
        Files.createDirectories(repoDir);
        try (Git ignored = Git.init().setDirectory(repoDir.toFile()).call()) {
            GitRepositoryServiceImpl registry = registryWithRealRepo(gitRoot);
            OntologyGitServiceImpl svc = new OntologyGitServiceImpl(new GitServiceImpl(), registry);

            OntologyGitArchiveResult result = svc.archiveVersion(request(REPO_ID, ASSET_ID));

            assertNull(result.getReason(), () -> "正路径不应有 reason，实际: " + result.getReason());
            assertEquals(OntologyGitArchiveResult.STATUS_COMMITTED, result.getStatus(),
                () -> "期望 committed，实际 " + result.getStatus() + " / reason=" + result.getReason());
            assertNotNull(result.getCommitId());
            assertTrue(result.getCommitId().matches("[0-9a-f]{40}"),
                "commitId 必须是真实 40 位 SHA，实际: " + result.getCommitId());
            assertEquals(EXPECTED_TAG, result.getTag(), "tag 必须严格 {assetType}/{assetId}/{versionNo}");

            try (Git git = Git.open(repoDir.toFile())) {
                Repository lib = git.getRepository();

                // 独立复核 1：归档服务报告的 commitId 必须等于仓库真实 HEAD（伪造/缓存都会在此暴露）
                ObjectId head = lib.resolve("HEAD");
                assertNotNull(head, "归档后 HEAD 应可解析");
                assertEquals(result.getCommitId(), head.getName(),
                    "commitId 必须等于 rev-parse HEAD，禁伪造");

                // 独立复核 2：tag 必须作为真实 ref 存在于 refs/tags/ 下
                ObjectId tagged = lib.resolve("refs/tags/" + EXPECTED_TAG);
                assertNotNull(tagged, "tag 必须真实写入 refs/tags/" + EXPECTED_TAG);

                // 独立复核 3：资产内容必须已进入该 commit（工作树无未跟踪残留 = 真被 add+commit）
                Status status = git.status().call();
                assertTrue(status.getUntracked().isEmpty(),
                    "归档文件应已被提交，实际未跟踪: " + status.getUntracked());
                assertTrue(status.isClean(), "归档后工作树应干净，实际: " + status);
            }

            Path archived = repoDir.resolve("ontologies").resolve(ASSET_ID).resolve(VERSION_NO + ".json");
            assertTrue(Files.exists(archived), "归档内容文件必须落盘: " + archived);
            assertEquals("{\"ontologyId\":\"" + ASSET_ID + "\",\"versionNo\":\"" + VERSION_NO + "\"}",
                Files.readString(archived));
        }
    }

    @Test
    @DisplayName("H4-T4 诚实空状态：仓库未注册 → not_available + reason，绝不伪成功、绝不写盘")
    void archiveVersion_withUnregisteredRepository_returnsNotAvailable(@TempDir Path repoDir) throws Exception {
        try (Git ignored = Git.init().setDirectory(repoDir.toFile()).call()) {
            // 注册表刻意保持空 —— 复现生产 P0-F：注册表零枚启用行（#147C 后已无内存态可冒充）
            GitRepositoryServiceImpl emptyRegistry =
                new GitRepositoryServiceImpl(new FakeGitRepoRegistry(), resolverUnderRoot(repoDir));
            OntologyGitServiceImpl svc =
                new OntologyGitServiceImpl(new GitServiceImpl(), emptyRegistry);

            OntologyGitArchiveResult result = svc.archiveVersion(request("never-registered", ASSET_ID));

            assertEquals(OntologyGitArchiveResult.STATUS_NOT_AVAILABLE, result.getStatus());
            assertNotNull(result.getReason(), "not_available 必须带 reason");
            assertNull(result.getCommitId(), "禁伪成功：不得返回 commitId");
            try (Git git = Git.open(repoDir.toFile())) {
                assertNull(git.getRepository().resolve("HEAD"), "未注册仓库不得产生任何提交");
            }
            assertTrue(Files.list(repoDir).allMatch(p -> p.getFileName().toString().equals(".git")),
                "失败路径不得向工作树写入资产文件");
        }
    }

    @Test
    @DisplayName("H4-T4 安全红线：assetId 含 path-traversal → not_available 且越目录无写入")
    void archiveVersion_withPathTraversalAssetId_isRejectedWithoutWrites(@TempDir Path gitRoot) throws Exception {
        Path repoDir = gitRoot.resolve(REPO_ROOT_DIR_NAME);
        Files.createDirectories(repoDir);
        try (Git ignored = Git.init().setDirectory(repoDir.toFile()).call()) {
            GitRepositoryServiceImpl registry = registryWithRealRepo(gitRoot);
            OntologyGitServiceImpl svc = new OntologyGitServiceImpl(new GitServiceImpl(), registry);

            OntologyGitArchiveResult result = svc.archiveVersion(request(REPO_ID, "../escape"));

            assertEquals(OntologyGitArchiveResult.STATUS_NOT_AVAILABLE, result.getStatus());
            assertNotNull(result.getReason());
            assertNull(result.getCommitId());
            assertTrue(Files.notExists(gitRoot.resolve("escape")), "越目录写入必须被拒绝");
            try (Git git = Git.open(repoDir.toFile())) {
                assertNull(git.getRepository().resolve("HEAD"), "非法路径段不得产生提交");
            }
        }
    }
}
