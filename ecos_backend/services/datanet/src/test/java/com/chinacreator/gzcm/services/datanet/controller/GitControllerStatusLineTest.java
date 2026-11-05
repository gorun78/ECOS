package com.chinacreator.gzcm.services.datanet.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService;
import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService.GitRepositoryException;
import com.chinacreator.gzcm.runtime.access.git.GitService;
import com.chinacreator.gzcm.runtime.access.git.entity.GitCommitView;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;

/**
 * Git 版本管理 API 的 HTTP 状态行保真门（#147C，D-B4-59／D-B4-71 族属主侧收口）。
 *
 * <p>本门只盯一件事：<b>状态行与信封同号、且分态不被抹平</b>。原腿全部
 * {@code return ApiResponse.error(404|500|…)} 裸回 ⇒ Spring 按返回类型渲染，
 * HTTP 状态行恒 200，而 body 写着 404/500 —— 调用方按状态行判成败即读到假绿（D-B4-59）。
 * 现改 {@code ResponseEntity} 承载，本门把「状态行值 = body.code = 语义分档」钉成行为断言，
 * 静态棘轮门（{@code SuccessStatusFailureEnvelopeRatchetTest}）只数得到腿，判不了档。</p>
 *
 * <p>#147BT 出入参强类型化后，本门断言面从 {@code Map.get(键)} 改为 VO getter——
 * <b>判定语义零放宽</b>（分档、同号、机读码、not_available 不吞定位失败四件套全部保留）；
 * 键面是否逐字不变由同批新增的 {@code GitControllerWireShapeFidelityTest} 独立取证。</p>
 *
 * <p>变异取证：把 {@code GitController.failure(body)} 改回 {@code ResponseEntity.ok(body)} ⇒
 * 本门全部失败用例红；把 {@code repoUnavailable} 的 404 判定删掉（一律 503）⇒
 * 「未注册=404」用例红；把 {@code branches} 的 {@code localPath} 未解析腿改回 404 ⇒
 * {@code unparsedLocalPathIs503} 红。</p>
 */
class GitControllerStatusLineTest {

    // ── 测试双桩：注册面（可控分态）+ Git 底座（按方法名给值/抛） ──

    /** 注册行；{@code thrown} 非空即该次定位按属主口径拒绝。 */
    private static final class FakeRegistry implements GitRepositoryService {
        private GitRepository row;
        private GitRepositoryException thrown;

        @Override
        public GitRepository getDefaultRepository() throws GitRepositoryException {
            if (thrown != null) {
                throw thrown;
            }
            return row;
        }

        @Override
        public GitRepository getRepositoryById(String repositoryId) throws GitRepositoryException {
            if (thrown != null) {
                throw thrown;
            }
            return row;
        }

        @Override
        public List<GitRepository> listRepositories() throws GitRepositoryException {
            if (thrown != null) {
                throw thrown;
            }
            return List.of(row);
        }

        @Override
        public void registerRepository(GitRepository repository) throws GitRepositoryException {
            if (thrown != null) {
                throw thrown;
            }
            row = repository;
        }

        @Override
        public void unregisterRepository(String repositoryId) throws GitRepositoryException {
            if (thrown != null) {
                throw thrown;
            }
            row = null;
        }
    }

    /**
     * Git 底座假面：只答本门用到的腿，其余腿返回类型零值。
     * <p>{@code throwOn} 命中的方法名 ⇒ 抛 {@link GitService.GitException}（受检、声明在接口上，
     * 走代理不包 UndeclaredThrowable）。</p>
     */
    private static GitService fakeGitService(Set<String> throwOn, Map<String, Object> answers) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (throwOn.contains(method.getName())) {
                throw new GitService.GitException("simulated jgit failure on " + method.getName());
            }
            if (answers.containsKey(method.getName())) {
                return answers.get(method.getName());
            }
            Class<?> rt = method.getReturnType();
            if (void.class.equals(rt)) {
                return null;
            }
            if (List.class.isAssignableFrom(rt)) {
                return List.of();
            }
            if (Map.class.isAssignableFrom(rt)) {
                return Map.of();
            }
            if (String.class.equals(rt)) {
                return "0000000";
            }
            return null;
        };
        return (GitService) Proxy.newProxyInstance(GitService.class.getClassLoader(),
                new Class<?>[] { GitService.class }, handler);
    }

    private static GitRepository registeredRepo(String localPath) {
        GitRepository repo = new GitRepository();
        repo.setRepositoryId("pipeline-core");
        repo.setName("Pipeline Core");
        repo.setRepoRoot("pipeline-core");
        repo.setLocalPath(localPath);
        repo.setDefaultBranch("main");
        repo.setEnabled(true);
        return repo;
    }

    private static GitRepositoryException notRegistered() {
        return new GitRepositoryException("Repository with ID pipeline-core not found（未在 ecos_control.ecos_git_repo 注册）", 404);
    }

    private static GitRepositoryException registryUnassembled() {
        return new GitRepositoryException("GitRepoRegistry 未装配：禁止以内存 Map 或空注册面替代（按显式不可用处理）");
    }

    // ── 强类型请求体夹具（#147BT）──

    private static GitController.GitCommitRequest commitRequest(String message) {
        GitController.GitCommitRequest r = new GitController.GitCommitRequest();
        r.setMessage(message);
        return r;
    }

    private static GitController.GitTagRequest tagRequest(String name) {
        GitController.GitTagRequest r = new GitController.GitTagRequest();
        r.setName(name);
        return r;
    }

    private static GitController.GitRollbackRequest rollbackRequest(String commitId) {
        GitController.GitRollbackRequest r = new GitController.GitRollbackRequest();
        r.setCommitId(commitId);
        return r;
    }

    private static GitController.GitBranchSwitchRequest branchRequest(String branch) {
        GitController.GitBranchSwitchRequest r = new GitController.GitBranchSwitchRequest();
        r.setBranch(branch);
        return r;
    }

    /**
     * 状态行 + 信封同号 + 机读码三件一起判。
     * <p>成功面口径：{@code ApiResponse.CODE_SUCCESS = 0}（业务码位）配 HTTP 200，
     * <b>不</b>要求同号；同号性只约束失败面（400/404/500/503 的 body.code 与状态行一致，
     * 承 D-B4-56 口径）。把成功面 0 也当成违例会是本门的假违例，故在此显式分档。</p>
     */
    private static void assertStatusAndSameCode(ResponseEntity<?> response,
                                                int expectedStatus, String expectedErrorCode) {
        assertEquals(expectedStatus, response.getStatusCode().value(), "HTTP 状态行");
        ApiResponse<?> body = (ApiResponse<?>) response.getBody();
        assertNotNull(body, "响应体不得为空");
        if (expectedStatus >= 400) {
            assertEquals(expectedStatus, body.getCode(), "失败态：状态行与信封 code 必须同号（D-B4-56 口径）");
        } else {
            assertEquals(ApiResponse.CODE_SUCCESS, body.getCode(), "成功态：业务码位保持 CODE_SUCCESS");
        }
        assertEquals(expectedErrorCode, body.getErrorCode(), "机读码面");
    }

    // ── 成功面 ──

    @Nested
    class HappyPath {
        @Test
        @DisplayName("status：注册行在位 ⇒ 200 + 真实 commit")
        void statusOk() {
            FakeRegistry registry = new FakeRegistry();
            registry.row = registeredRepo("/data/ecos-git-repos/pipeline-core");
            GitController controller = new GitController(
                    fakeGitService(Set.of(), Map.of("getCurrentCommitId", "deadbeef1", "diff", Map.of("files", List.of()))),
                    registry);

            ResponseEntity<ApiResponse<GitController.GitStatusVO>> response = controller.status("pipeline-core");
            assertStatusAndSameCode(response, 200, null);
            assertEquals("deadbeef1", response.getBody().getData().getCurrentCommit());
            assertEquals("Pipeline Core", response.getBody().getData().getRepoName());
        }

        @Test
        @DisplayName("commits：多取一条判 hasMore；未到尽头不伪造 total")
        void commitsDoNotFakeTotal() {
            FakeRegistry registry = new FakeRegistry();
            registry.row = registeredRepo("/data/ecos-git-repos/pipeline-core");
            List<GitCommitView> six = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                six.add(new GitCommitView("sha" + i, "msg" + i, "author", "a@e.local", new Date()));
            }
            GitController controller = new GitController(
                    fakeGitService(Set.of(), Map.of("listCommits", six)), registry);

            ResponseEntity<ApiResponse<GitController.GitCommitsVO>> response =
                    controller.commits("pipeline-core", 0, 5);
            GitController.GitCommitsVO data = response.getBody().getData();
            assertEquals(200, response.getStatusCode().value());
            assertEquals(5, data.getItems().size(), "多取的一条只用于判位，不外泄");
            assertEquals(Boolean.TRUE, data.getHasMore());
            assertNull(data.getTotal(), "未读到尽头时不得伪造总数（原腿的 total 来自不存在的表）");
        }

        @Test
        @DisplayName("commits：读到尽头 ⇒ total 为确切值")
        void commitsGiveExactTotalAtEnd() {
            FakeRegistry registry = new FakeRegistry();
            registry.row = registeredRepo("/data/ecos-git-repos/pipeline-core");
            List<GitCommitView> two = List.of(
                    new GitCommitView("sha1", "m1", "a", "a@e.local", new Date()),
                    new GitCommitView("sha2", "m2", "a", "a@e.local", new Date()));
            GitController controller = new GitController(
                    fakeGitService(Set.of(), Map.of("listCommits", two)), registry);

            GitController.GitCommitsVO data = controller.commits("pipeline-core", 0, 20).getBody().getData();
            assertEquals(Boolean.FALSE, data.getHasMore());
            assertEquals(Integer.valueOf(2), data.getTotal());
        }
    }

    // ── 失败面：状态行必须真实，且四档互不抹平 ──

    @Nested
    class FailureStatusLines {

        @Test
        @DisplayName("未注册 ⇒ 404 + ECOS_GIT_REPO_UNAVAILABLE（状态行不再恒 200）")
        void notRegisteredIs404() {
            FakeRegistry registry = new FakeRegistry();
            registry.thrown = notRegistered();
            GitController controller = new GitController(fakeGitService(Set.of(), Map.of()), registry);

            for (ResponseEntity<?> r : List.of(
                    controller.status("pipeline-core"),
                    controller.commits("pipeline-core", 0, 20),
                    controller.diff("pipeline-core", null, null),
                    controller.branches("pipeline-core"),
                    controller.rollback("pipeline-core", rollbackRequest("sha1")),
                    controller.switchBranch("pipeline-core", branchRequest("dev")))) {
                assertStatusAndSameCode(r, 404, "ECOS_GIT_REPO_UNAVAILABLE");
            }
        }

        @Test
        @DisplayName("注册面未装配 ⇒ 503 + ECOS_GIT_UNAVAILABLE（不得说成「你没注册」）")
        void unassembledRegistryIs503() {
            FakeRegistry registry = new FakeRegistry();
            registry.thrown = registryUnassembled();
            GitController controller = new GitController(fakeGitService(Set.of(), Map.of()), registry);

            assertStatusAndSameCode(controller.status("pipeline-core"), 503, "ECOS_GIT_UNAVAILABLE");
            assertStatusAndSameCode(controller.commits("pipeline-core", 0, 20), 503, "ECOS_GIT_UNAVAILABLE");
        }

        @Test
        @DisplayName("Git 底座故障 ⇒ 500 + ECOS_GIT_OPERATION_FAILED，且不被折叠成空列表")
        void gitOperationFailureIs500() {
            FakeRegistry registry = new FakeRegistry();
            registry.row = registeredRepo("/data/ecos-git-repos/pipeline-core");
            GitController controller = new GitController(
                    fakeGitService(Set.of("getCurrentCommitId", "listCommits", "diff", "listBranches"), Map.of()),
                    registry);

            assertStatusAndSameCode(controller.status("pipeline-core"), 500, "ECOS_GIT_OPERATION_FAILED");
            assertStatusAndSameCode(controller.commits("pipeline-core", 0, 20), 500, "ECOS_GIT_OPERATION_FAILED");
            assertStatusAndSameCode(controller.diff("pipeline-core", "HEAD~1", "HEAD"), 500, "ECOS_GIT_OPERATION_FAILED");
            assertStatusAndSameCode(controller.branches("pipeline-core"), 500, "ECOS_GIT_OPERATION_FAILED");
        }

        @Test
        @DisplayName("参数缺失 ⇒ 400 + 各腿专属机读码（不借 500 兜底）")
        void blankParametersAre400() {
            FakeRegistry registry = new FakeRegistry();
            registry.row = registeredRepo("/data/ecos-git-repos/pipeline-core");
            GitController controller = new GitController(fakeGitService(Set.of(), Map.of()), registry);

            assertStatusAndSameCode(controller.commit("pipeline-core", commitRequest("   ")),
                    400, "ECOS_GIT_COMMIT_MESSAGE_REQUIRED");
            assertStatusAndSameCode(controller.tag("pipeline-core", tagRequest(null)),
                    400, "ECOS_GIT_TAG_NAME_REQUIRED");
            assertStatusAndSameCode(controller.rollback("pipeline-core", rollbackRequest("")),
                    400, "ECOS_GIT_ROLLBACK_TARGET_REQUIRED");
        }

        @Test
        @DisplayName("branches：localPath 未解析（仓根配置缺位）⇒ 503，不再冒充 404 未注册")
        void unparsedLocalPathIs503() {
            FakeRegistry registry = new FakeRegistry();
            registry.row = registeredRepo(null);
            GitController controller = new GitController(fakeGitService(Set.of(), Map.of()), registry);

            assertStatusAndSameCode(controller.branches("pipeline-core"), 503, "ECOS_GIT_UNAVAILABLE");
        }
    }

    // ── 能力未实现面：显式声明，禁伪成功但也不谎报故障 ──

    @Nested
    class NotAvailableCapability {

        @Test
        @DisplayName("rollback／branch 切换：200 + status=not_available（如实回报，禁伪成功）")
        void notAvailableStaysHonest() {
            FakeRegistry registry = new FakeRegistry();
            registry.row = registeredRepo("/data/ecos-git-repos/pipeline-core");
            GitController controller = new GitController(fakeGitService(Set.of(), Map.of()), registry);

            ResponseEntity<ApiResponse<GitController.GitRollbackResultVO>> rollback =
                    controller.rollback("pipeline-core", rollbackRequest("sha123"));
            assertEquals(200, rollback.getStatusCode().value());
            assertEquals("not_available", rollback.getBody().getData().getStatus());
            assertNotNull(rollback.getBody().getData().getReason(), "未实现必须给出原因，禁空面冒充");
            assertEquals("sha123", rollback.getBody().getData().getRollbackTo(), "目标 commit 必须回显，禁静默丢位");

            GitController.GitBranchSwitchResultVO branch =
                    controller.switchBranch("pipeline-core", branchRequest("dev")).getBody().getData();
            assertEquals("not_available", branch.getStatus());
            assertNotNull(branch.getReason());
            assertEquals("dev", branch.getRequestedBranch());

            // #147P：push/pull 端点从「网关 404」改为本层显式能力分态
            ResponseEntity<ApiResponse<GitController.GitSyncResultVO>> push =
                    controller.push("pipeline-core", null);
            assertEquals(200, push.getStatusCode().value());
            assertEquals("not_available", push.getBody().getData().getStatus());
            assertNotNull(push.getBody().getData().getReason());

            ResponseEntity<ApiResponse<GitController.GitSyncResultVO>> pull =
                    controller.pull("pipeline-core", null);
            assertEquals(200, pull.getStatusCode().value());
            assertEquals("not_available", pull.getBody().getData().getStatus());
            assertNotNull(pull.getBody().getData().getReason());
        }

        @Test
        @DisplayName("能力未实现面不得把仓库定位失败吞成 not_available（try 范围收窄回归锚）")
        void unavailableRepoNeverMasksAsNotAvailable() {
            FakeRegistry registry = new FakeRegistry();
            registry.thrown = notRegistered();
            GitController controller = new GitController(fakeGitService(Set.of(), Map.of()), registry);

            ResponseEntity<ApiResponse<GitController.GitRollbackResultVO>> rollback =
                    controller.rollback("pipeline-core", rollbackRequest("sha123"));
            assertEquals(404, rollback.getStatusCode().value(),
                    "未注册 ⇒ 404；若把 not_available 声明包进同一段 try，这里会红着 200 回来");

            ResponseEntity<ApiResponse<GitController.GitBranchSwitchResultVO>> branch =
                    controller.switchBranch("pipeline-core", branchRequest("dev"));
            assertEquals(404, branch.getStatusCode().value());
        }
    }
}
