package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveRequest;
import com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveResult;
import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService;
import com.chinacreator.gzcm.runtime.access.git.GitService;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H4-T4 / VG-2 — 本体 Git 归档真实编排单测（Mockito 模拟 runtime {@link GitService}）。
 *
 * <p>断言三条：
 * <ol>
 *   <li>真实 commitId 透传（来源 {@code getCurrentCommitId}，非伪造）；</li>
 *   <li>tag 名称格式严格 {@code {assetType}/{assetId}/{versionNo}}；</li>
 *   <li>仓库未注册 → {@code not_available}，绝不伪成功、绝不触碰 git 写操作。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OntologyGitServiceImplTest {

    @Mock private GitService gitService;
    @Mock private GitRepositoryService gitRepositoryService;

    private GitRepository registeredRepo(Path workingTree) {
        GitRepository repo = new GitRepository();
        repo.setRepositoryId("ontology");
        repo.setName("Ontology Archive");
        repo.setLocalPath(workingTree.toString());
        repo.setEnabled(true);
        repo.setDefaultBranch("main");
        return repo;
    }

    @Test
    @DisplayName("archiveVersion: commit+tag+真实 commitId，tag 格式 {assetType}/{assetId}/{versionNo}")
    void archiveVersion_happyPath_propagatesRealCommitIdAndTag(@TempDir Path workingTree) throws Exception {
        GitRepository repo = registeredRepo(workingTree);
        when(gitRepositoryService.getRepositoryById("ontology")).thenReturn(repo);
        when(gitService.getCurrentCommitId(repo)).thenReturn("cafe1234567890abcdef");

        OntologyGitArchiveRequest req = new OntologyGitArchiveRequest();
        req.setRepositoryId("ontology");
        req.setAssetType("ontology");
        req.setAssetId("ont001");
        req.setVersionNo("1.0.2");
        req.setContent("{\"entities\":[]}");
        req.setCommitMessage("Publish ontology ont001 1.0.2");

        OntologyGitServiceImpl svc = new OntologyGitServiceImpl(gitService, gitRepositoryService);
        OntologyGitArchiveResult result = svc.archiveVersion(req);

        // 1. 真实 commitId 透传（非伪造，等于 getCurrentCommitId 的返回值）
        assertEquals(OntologyGitArchiveResult.STATUS_COMMITTED, result.getStatus());
        assertEquals("cafe1234567890abcdef", result.getCommitId());
        assertNull(result.getReason());

        // 2. tag 名称格式严格 {assetType}/{assetId}/{versionNo}
        ArgumentCaptor<String> tagCap = ArgumentCaptor.forClass(String.class);
        verify(gitService, times(1)).createTag(eq(repo), tagCap.capture(), anyString());
        assertEquals("ontology/ont001/1.0.2", tagCap.getValue());
        assertEquals("ontology/ont001/1.0.2", result.getTag());

        // commit 真实调用一次
        verify(gitService, times(1))
            .commitAllChanges(eq(repo), eq("Publish ontology ont001 1.0.2"), any(), any());

        // 内容确实写入工作树
        assertTrue(Files.exists(workingTree.resolve("ontologies").resolve("ont001").resolve("1.0.2.json")),
            "版本内容应写入仓库工作树 ontologies/{assetId}/{versionNo}.json");
    }

    @Test
    @DisplayName("archiveVersion: 仓库未注册 → not_available，禁止伪成功且不触碰 git")
    void archiveVersion_unregisteredRepository_returnsNotAvailable(
            @TempDir Path workingTree) throws Exception {
        when(gitRepositoryService.getRepositoryById("missing"))
            .thenThrow(new GitRepositoryService.GitRepositoryException("Repository with ID missing not found"));

        OntologyGitArchiveRequest req = new OntologyGitArchiveRequest();
        req.setRepositoryId("missing");
        req.setAssetType("ontology");
        req.setAssetId("ont001");
        req.setVersionNo("1.0.0");
        req.setContent("{\"entities\":[]}");

        OntologyGitServiceImpl svc = new OntologyGitServiceImpl(gitService, gitRepositoryService);
        OntologyGitArchiveResult result = svc.archiveVersion(req);

        assertEquals(OntologyGitArchiveResult.STATUS_NOT_AVAILABLE, result.getStatus());
        assertNull(result.getCommitId(), "未注册仓库不得返回任何 commitId（禁伪成功）");
        assertNotNull(result.getReason());
        verify(gitService, never()).commitAllChanges(any(), any(), any(), any());
        verify(gitService, never()).createTag(any(), any(), any());
        verify(gitService, never()).getCurrentCommitId(any());
    }

    @Test
    @DisplayName("commit(Map): 委托 archiveVersion；缺 repositoryId → not_available（禁伪成功）")
    void commitMap_missingRepositoryId_returnsNotAvailable() throws Exception {
        OntologyGitServiceImpl svc = new OntologyGitServiceImpl(gitService, gitRepositoryService);

        Map<String, Object> r = svc.commit("ont001", Map.of(
            "content", "{\"a\":1}", "versionNo", "1.0.0", "message", "m"));

        assertEquals("not_available", r.get("status"));
        assertTrue(String.valueOf(r.get("reason")).contains("repositoryId"));
        verify(gitService, never()).commitAllChanges(any(), any(), any(), any());
    }

    @Test
    @DisplayName("commit(Map): 委托成功路径返回 committed + 真实 commitId + tag")
    void commitMap_delegatesCommitted(@TempDir Path workingTree) throws Exception {
        GitRepository repo = registeredRepo(workingTree);
        when(gitRepositoryService.getRepositoryById("ontology")).thenReturn(repo);
        when(gitService.getCurrentCommitId(repo)).thenReturn("beef0001");

        OntologyGitServiceImpl svc = new OntologyGitServiceImpl(gitService, gitRepositoryService);
        Map<String, Object> r = svc.commit("ont777", Map.of(
            "repositoryId", "ontology",
            "assetType", "ontology",
            "versionNo", "2.1.0",
            "content", "{\"b\":2}"));

        assertEquals("committed", r.get("status"));
        assertEquals("beef0001", r.get("commitId"));
        assertEquals("ontology/ont777/2.1.0", r.get("tag"));
        verify(gitService, times(1)).createTag(eq(repo), eq("ontology/ont777/2.1.0"), any());
    }

    @Test
    @DisplayName("load(Map): 客户端 url 被忽略；缺 repositoryId → not_available（禁 SSRF/gitUrl 注入）")
    void load_neverTrustedClientUrl_returnsNotAvailable() throws Exception {
        OntologyGitServiceImpl svc = new OntologyGitServiceImpl(gitService, gitRepositoryService);

        Map<String, Object> r = svc.load(Map.of("url", "http://evil.internal/repo.git"));

        assertEquals("not_available", r.get("status"));
        // 绝不因客户端 url 触发任何 clone / pull
        verify(gitService, never()).clone(any(), any(), any(), any());
        verify(gitService, never()).pull(any(GitRepository.class), any(), any());
    }
}
