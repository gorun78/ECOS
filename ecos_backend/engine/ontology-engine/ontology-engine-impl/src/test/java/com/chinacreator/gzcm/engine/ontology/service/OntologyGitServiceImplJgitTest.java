package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveRequest;
import com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveResult;
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
 */
class OntologyGitServiceImplJgitTest {

    private static final String REPO_ID = "jgit-ontology-it";
    private static final String ASSET_ID = "ont-jgit-001";
    private static final String VERSION_NO = "1.0.0";
    private static final String EXPECTED_TAG = "ontology/" + ASSET_ID + "/" + VERSION_NO;

    private GitRepositoryServiceImpl registryWithRealRepo(Path repoDir) throws Exception {
        GitRepository repo = new GitRepository();
        repo.setRepositoryId(REPO_ID);
        repo.setName("JGit IT Ontology Archive");
        repo.setLocalPath(repoDir.toString());
        repo.setDefaultBranch("main");
        repo.setEnabled(true);
        GitRepositoryServiceImpl registry = new GitRepositoryServiceImpl();
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
    void archiveVersion_withRealJGitRepository_producesRealCommitAndTag(@TempDir Path repoDir) throws Exception {
        // GitServiceImpl 不做 Git.init()，仓库必须由调用方预先初始化 —— 这正是生产链路缺注册表时的表现
        try (Git ignored = Git.init().setDirectory(repoDir.toFile()).call()) {
            GitRepositoryServiceImpl registry = registryWithRealRepo(repoDir);
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
            // 注册表刻意保持空 —— 复现生产 P0-F：全仓无 registerRepository 写入方
            OntologyGitServiceImpl svc =
                new OntologyGitServiceImpl(new GitServiceImpl(), new GitRepositoryServiceImpl());

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
    void archiveVersion_withPathTraversalAssetId_isRejectedWithoutWrites(@TempDir Path parentDir) throws Exception {
        Path repoDir = parentDir.resolve("repo");
        Files.createDirectories(repoDir);
        try (Git ignored = Git.init().setDirectory(repoDir.toFile()).call()) {
            GitRepositoryServiceImpl registry = registryWithRealRepo(repoDir);
            OntologyGitServiceImpl svc = new OntologyGitServiceImpl(new GitServiceImpl(), registry);

            OntologyGitArchiveResult result = svc.archiveVersion(request(REPO_ID, "../escape"));

            assertEquals(OntologyGitArchiveResult.STATUS_NOT_AVAILABLE, result.getStatus());
            assertNotNull(result.getReason());
            assertNull(result.getCommitId());
            assertTrue(Files.notExists(parentDir.resolve("escape")), "越目录写入必须被拒绝");
            try (Git git = Git.open(repoDir.toFile())) {
                assertNull(git.getRepository().resolve("HEAD"), "非法路径段不得产生提交");
            }
        }
    }
}
