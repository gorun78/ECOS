package com.chinacreator.gzcm.runtime.access.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.chinacreator.gzcm.runtime.access.git.GitService.GitException;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;

/**
 * Git 底座失败分类门（#147BU · §2.9.9 G8 登记缺陷「本地仓库目录缺失返 500」收口）。
 *
 * <p><b>钉的是什么</b>：同样是「打不开仓库」，两件事必须分档，调用方才知道该重试还是该找运维——
 * <ul>
 *   <li>本地目录<b>不在位</b> / {@code localPath} 未解析 ＝ 服务端未就绪 ⇒
 *       {@link GitException.Kind#NOT_AVAILABLE}（控制器落 503）；</li>
 *   <li>目录在位但<b>不是可用仓库</b> / 对象库操作异常 ＝ 操作失败 ⇒
 *       {@link GitException.Kind#OPERATION_FAILED}（控制器落 500）。</li>
 * </ul>
 * 修前两态都由 JGit {@code RepositoryNotFoundException} 折叠成「操作失败」500，客户端无法区分。</p>
 *
 * <p><b>变异自证</b>：把 {@code GitServiceImpl.requireDirectory} 的目录检查删掉 ⇒ 本门
 * 「目录不在位」四例全部改判 {@code OPERATION_FAILED} 并红；把 {@code requireLocalPath}
 * 的空路径分支改回缺省 kind ⇒ 空路径用例红。</p>
 */
class GitServiceUnavailableKindTest {

    private static GitRepository repoAt(Path localPath) {
        GitRepository repo = new GitRepository();
        repo.setRepositoryId("pipeline-core");
        repo.setName("Pipeline Core");
        repo.setRepoRoot("pipeline-core");
        repo.setLocalPath(localPath == null ? null : localPath.toString());
        repo.setDefaultBranch("main");
        return repo;
    }

    private static void assertNotAvailable(String leg, ExecutableCall call) {
        GitException e = assertThrows(GitException.class, call::run, leg + " 应抛 GitException");
        assertEquals(GitException.Kind.NOT_AVAILABLE, e.getKind(),
                leg + " 应判「服务端未就绪」而非「操作失败」；实际消息=" + e.getMessage());
    }

    @FunctionalInterface
    private interface ExecutableCall {
        void run() throws GitException;
    }

    @Test
    @DisplayName("本地目录不在位：读/写/检回各腿一律 NOT_AVAILABLE（禁折叠成 500 操作失败）")
    void missingDirectoryIsNotAvailable(@TempDir Path tmp) {
        GitServiceImpl git = new GitServiceImpl();
        Path missing = tmp.resolve("never-created");
        GitRepository repo = repoAt(missing);

        assertNotAvailable("status/getCurrentCommitId", () -> git.getCurrentCommitId(repo));
        assertNotAvailable("commits/listCommits", () -> git.listCommits(repo, 0, 5));
        assertNotAvailable("diff", () -> git.diff(repo, "HEAD~1", "HEAD"));
        assertNotAvailable("commit/commitAllChanges", () -> git.commitAllChanges(repo, "m", "a", "a@e.local"));
        assertNotAvailable("tag/createTag", () -> git.createTag(repo, "v1", "m"));
        assertNotAvailable("readAtRef", () -> git.readFileAtRef(repo, "HEAD", "a.json"));
        assertNotAvailable("branches/listBranches", () -> git.listBranches(missing.toString()));
        assertNotAvailable("commits/listCommitShasForPath",
                () -> git.listCommitShasForPath(missing.toString(), "a.json", 5));

        GitException e = assertThrows(GitException.class, () -> git.getCurrentCommitId(repo));
        assertTrue(e.getMessage().contains("不在位"), "消息须点名「目录不在位」，实际=" + e.getMessage());
    }

    @Test
    @DisplayName("localPath 未解析：NOT_AVAILABLE（仓根配置缺位＝未就绪，不是操作失败）")
    void blankLocalPathIsNotAvailable() {
        GitServiceImpl git = new GitServiceImpl();
        assertNotAvailable("blank localPath", () -> git.getCurrentCommitId(repoAt(null)));
    }

    @Test
    @DisplayName("目录在位但不是仓库：仍是 OPERATION_FAILED（分档不得一刀切）")
    void existingNonGitDirectoryStaysOperationFailed(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("empty-dir"));
        GitServiceImpl git = new GitServiceImpl();
        GitRepository repo = repoAt(tmp.resolve("empty-dir"));

        GitException e = assertThrows(GitException.class, () -> git.getCurrentCommitId(repo));
        assertEquals(GitException.Kind.OPERATION_FAILED, e.getKind(),
                "目录存在但打不开仓库应记操作失败（500 档），实际=" + e.getMessage());
    }

    @Test
    @DisplayName("repository 为 null：编程错误记 OPERATION_FAILED，不冒充「未就绪」")
    void nullRepositoryStaysOperationFailed() {
        GitServiceImpl git = new GitServiceImpl();
        GitException e = assertThrows(GitException.class, () -> git.getCurrentCommitId(null));
        assertEquals(GitException.Kind.OPERATION_FAILED, e.getKind());
    }

    @Test
    @DisplayName("既有构造点缺省 kind：语义不变的向后兼容锚")
    void legacyConstructorsDefaultToOperationFailed() {
        assertEquals(GitException.Kind.OPERATION_FAILED, new GitException("x").getKind());
        assertEquals(GitException.Kind.OPERATION_FAILED, new GitException("x", new RuntimeException()).getKind());
        assertEquals(GitException.Kind.OPERATION_FAILED,
                new GitException("x", (GitException.Kind) null).getKind());
        assertEquals(GitException.Kind.NOT_AVAILABLE,
                new GitException("x", GitException.Kind.NOT_AVAILABLE).getKind());
    }
}
