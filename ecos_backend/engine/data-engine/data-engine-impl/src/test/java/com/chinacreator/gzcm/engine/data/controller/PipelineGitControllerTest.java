package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitCommitRequest;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitCommitResultVO;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitLoadRequest;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitOperationResultVO;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitPullRequest;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitSwitchBranchRequest;
import com.chinacreator.gzcm.engine.data.service.PipelineGitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PipelineGitControllerTest — H4-T1：repositoryId 入参契约 + localPath 废弃拒绝 + 路径契约回归。
 */
@ExtendWith(MockitoExtension.class)
class PipelineGitControllerTest {

    @Mock
    private PipelineGitService gitService;

    private PipelineGitController controller;

    @BeforeEach
    void setUp() {
        this.controller = new PipelineGitController(gitService);
    }

    // ─────────────────────────── POST /tasks/{id}/git/commit ─────────────

    @Test
    @DisplayName("POST /tasks/{id}/git/commit — repositoryId 透传 + path 透传 {id}")
    void commitRoutesRepositoryId() throws Exception {
        PipelineGitCommitResultVO sample = new PipelineGitCommitResultVO();
        sample.setTaskId("t-123");
        sample.setBranch("main");
        sample.setCommitId("abc1234");
        sample.setMessage("feat(pipeline): new node");
        when(gitService.commit(eq("t-123"), any(PipelineGitCommitRequest.class))).thenReturn(sample);

        PipelineGitCommitRequest req = new PipelineGitCommitRequest();
        req.setMessage("feat(pipeline): new node");
        req.setBranch("main");
        req.setAuthor("ecos-user");
        req.setRepositoryId("pipeline-repo-1");

        ApiResponse<PipelineGitCommitResultVO> resp = controller.commit("t-123", req);

        assertTrue(resp.isSuccess(), "code=0");
        assertEquals("t-123", resp.getData().getTaskId());
        assertEquals("abc1234", resp.getData().getCommitId());

        ArgumentCaptor<PipelineGitCommitRequest> cap = ArgumentCaptor.forClass(PipelineGitCommitRequest.class);
        verify(gitService).commit(eq("t-123"), cap.capture());
        PipelineGitCommitRequest routed = cap.getValue();
        assertEquals("feat(pipeline): new node", routed.getMessage());
        assertEquals("main", routed.getBranch());
        assertEquals("ecos-user", routed.getAuthor());
        assertEquals("pipeline-repo-1", routed.getRepositoryId());
        assertNull(routed.getLocalPath());
    }

    @Test
    @DisplayName("POST /tasks/{id}/git/commit — 仅传 localPath 返回 not_available（禁伪成功）")
    void commitLocalPathOnlyReturnsNotAvailable() throws Exception {
        PipelineGitCommitResultVO rejected = new PipelineGitCommitResultVO();
        rejected.setTaskId("t-123");
        rejected.setStatus("not_available");
        rejected.setReason("localPath 入参已废弃：请改传 repositoryId");
        when(gitService.commit(eq("t-123"), any(PipelineGitCommitRequest.class))).thenReturn(rejected);

        PipelineGitCommitRequest req = new PipelineGitCommitRequest();
        req.setLocalPath("C:/evil/path");

        ApiResponse<PipelineGitCommitResultVO> resp = controller.commit("t-123", req);
        assertTrue(resp.isSuccess());
        assertEquals("not_available", resp.getData().getStatus());
        assertNotNull(resp.getData().getReason());
    }

    @Test
    @DisplayName("POST /tasks/{id}/git/commit — 非法参数走 400")
    void commitIllegalArgReturnsBadRequest() throws Exception {
        when(gitService.commit(anyString(), any(PipelineGitCommitRequest.class)))
                .thenThrow(new IllegalArgumentException("empty id"));
        PipelineGitCommitRequest req = new PipelineGitCommitRequest();
        ApiResponse<PipelineGitCommitResultVO> resp = controller.commit("", req);
        assertEquals(ApiResponse.CODE_BAD_REQUEST, resp.getCode());
    }

    // ─────────────────────────── POST /tasks/{id}/git/pull ─────────────

    @Test
    @DisplayName("POST /tasks/{id}/git/pull — repositoryId 字段透传到服务")
    void pullDtoRoutesRepositoryId() throws Exception {
        PipelineGitOperationResultVO sample = new PipelineGitOperationResultVO();
        sample.setTaskId("t-123");
        sample.setYamlUpdated(true);
        when(gitService.pull(eq("t-123"), any(PipelineGitPullRequest.class))).thenReturn(sample);

        PipelineGitPullRequest req = new PipelineGitPullRequest();
        req.setRepositoryId("pipeline-repo-1");

        ApiResponse<PipelineGitOperationResultVO> resp = controller.pull("t-123", req);

        assertTrue(resp.isSuccess());
        assertEquals("t-123", resp.getData().getTaskId());
        assertEquals(Boolean.TRUE, resp.getData().getYamlUpdated());
        ArgumentCaptor<PipelineGitPullRequest> cap = ArgumentCaptor.forClass(PipelineGitPullRequest.class);
        verify(gitService).pull(eq("t-123"), cap.capture());
        assertEquals("pipeline-repo-1", cap.getValue().getRepositoryId());
    }

    // ─────────────────────────── POST /git/load ────────────────────────

    @Test
    @DisplayName("POST /git/load — repositoryId/branch/taskId 字段契约透传")
    void loadFromGitDtoRoutesClientFields() throws Exception {
        PipelineGitOperationResultVO sample = new PipelineGitOperationResultVO();
        sample.setTaskId("new-task");
        sample.setPipelineId("pipeline-77");
        sample.setGitUrl("https://github.com/ecos/repo.git");
        when(gitService.loadFromGit(any(PipelineGitLoadRequest.class))).thenReturn(sample);

        PipelineGitLoadRequest req = new PipelineGitLoadRequest();
        req.setRepositoryId("pipeline-repo-1");
        req.setBranch("dev");
        req.setTaskId("task-1");
        req.setGitUrl("https://github.com/ecos/repo.git");
        req.setPipelineId("pipeline-77");

        ApiResponse<PipelineGitOperationResultVO> resp = controller.loadFromGit(req);

        assertTrue(resp.isSuccess());
        assertEquals("pipeline-77", resp.getData().getPipelineId());
        ArgumentCaptor<PipelineGitLoadRequest> cap = ArgumentCaptor.forClass(PipelineGitLoadRequest.class);
        verify(gitService).loadFromGit(cap.capture());
        assertEquals("pipeline-repo-1", cap.getValue().getRepositoryId());
        assertEquals("dev", cap.getValue().getBranch());
        assertEquals("task-1", cap.getValue().getTaskId());
    }

    // ─────────────────────────── GET /git/branches ───────────────────────

    @Test
    @DisplayName("GET /git/branches?repositoryId= — 新入参透传")
    void listBranchesPassesRepositoryId() throws Exception {
        when(gitService.listBranches("pipeline-repo-1")).thenReturn(List.of("main", "dev"));
        ApiResponse<List<String>> resp = controller.listBranches(null, "pipeline-repo-1");
        assertTrue(resp.isSuccess());
        assertEquals(2, resp.getData().size());
    }

    @Test
    @DisplayName("GET /git/branches?localPath= — 废弃入参单独传入走 400 指引")
    void listBranchesLegacyLocalPathRejected() throws Exception {
        when(gitService.listBranchesByLegacyLocalPath("C:/git-cache"))
                .thenThrow(new IllegalArgumentException("localPath 入参已废弃，请改传 repositoryId"));
        ApiResponse<List<String>> resp = controller.listBranches("C:/git-cache", null);
        assertEquals(ApiResponse.CODE_BAD_REQUEST, resp.getCode());
    }

    // ─────────────────────────── POST /git/branch ───────────────────────

    @Test
    @DisplayName("POST /git/branch — repositoryId/branchName/taskId 字段契约透传")
    void switchBranchDtoRoutesClientFields() throws Exception {
        PipelineGitOperationResultVO sample = new PipelineGitOperationResultVO();
        sample.setBranchName("dev");
        when(gitService.switchBranch(any(PipelineGitSwitchBranchRequest.class))).thenReturn(sample);

        PipelineGitSwitchBranchRequest req = new PipelineGitSwitchBranchRequest();
        req.setRepositoryId("pipeline-repo-1");
        req.setBranchName("dev");
        req.setTaskId("task-1");

        ApiResponse<PipelineGitOperationResultVO> resp = controller.switchBranch(req);

        assertTrue(resp.isSuccess());
        assertEquals("dev", resp.getData().getBranchName());
        ArgumentCaptor<PipelineGitSwitchBranchRequest> cap =
                ArgumentCaptor.forClass(PipelineGitSwitchBranchRequest.class);
        verify(gitService).switchBranch(cap.capture());
        assertEquals("pipeline-repo-1", cap.getValue().getRepositoryId());
        assertEquals("dev", cap.getValue().getBranchName());
        assertEquals("task-1", cap.getValue().getTaskId());
    }

    // ─────────────────────────── GET /git/versions/{id} ─────────────────

    @Test
    @DisplayName("GET /git/versions/{id} — 首次 commit 之前返回空列表（200 路径契约）")
    void listVersionsEmptyWhenNotInitialized() throws Exception {
        when(gitService.versions("t-456")).thenReturn(List.of());
        ApiResponse<List<String>> resp = controller.listVersions("t-456");
        assertTrue(resp.isSuccess());
        assertTrue(resp.getData().isEmpty());
    }

    @Test
    @DisplayName("GET /git/versions/{id} — 服务异常走 500")
    void listVersionsServiceErrorReturnsInternalError() throws Exception {
        when(gitService.versions("t-err"))
                .thenThrow(new RuntimeException("git sha walk failed"));
        ApiResponse<List<String>> resp = controller.listVersions("t-err");
        assertEquals(ApiResponse.CODE_INTERNAL_ERROR, resp.getCode());
    }
}
