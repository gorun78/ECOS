package com.chinacreator.gzcm.services.datanet.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService;
import com.chinacreator.gzcm.runtime.access.git.GitService;
import com.chinacreator.gzcm.runtime.access.git.entity.GitCommitView;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Git API 键面保真门（#147BT：出入参由 {@code Map<String,Object>} 改强类型 DTO/VO 的等价性取证）。
 *
 * <p><b>为什么单独建门</b>：DI-2 的清退只允许「换承载类型」，不允许「换契约」。
 * {@code Map} 时代 wire 键由 {@code data.put("x", …)} 逐行写出，改成 VO 后键名改由字段名派生——
 * 一次手抖（漏 put→漏 setter、{@code id}/{@code commitId} 同值双键被"顺手合并"、
 * {@code total} 的「只在读到尽头时出现」被改成恒出现）前端 {@code services/gitService.ts}
 * 与既有调用方会静默读到 undefined，而状态行仍是 200。本门把 9 个端点的 wire 键集
 * 与 {@code Map} 时代的字面键集<b>逐枚等值</b>比对（不是子集），VO 加键/丢键即红。</p>
 *
 * <p>断言取的是 {@code ApiResponse.data} 的 Jackson 序列化结果（与真实响应同一渲染路径），
 * 因此 {@code @JsonInclude(NON_NULL)} 的省略行为也一并被钉住。</p>
 *
 * <p>变异自证（本批改 VO 字段名即红）：把 {@code GitCommitItemVO.id} 改名 → 「commits 单条」红；
 * 把 {@code GitCommitsVO.total} 改成恒赋值 → 「未到尽头不含 total」红；
 * 删 {@code @JsonInclude} → 同上两枚红。</p>
 */
class GitControllerWireShapeFidelityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── 夹具：注册面 + Git 底座（只答键面用到的腿）──

    private static GitRepository registeredRepo() {
        GitRepository repo = new GitRepository();
        repo.setRepositoryId("pipeline-core");
        repo.setName("Pipeline Core");
        repo.setRepoRoot("pipeline-core");
        repo.setLocalPath("/data/ecos-git-repos/pipeline-core");
        repo.setDefaultBranch("main");
        repo.setEnabled(true);
        return repo;
    }

    private static GitRepositoryService registry() {
        return (GitRepositoryService) Proxy.newProxyInstance(
                GitRepositoryService.class.getClassLoader(), new Class<?>[] { GitRepositoryService.class },
                (proxy, method, args) -> "getRepositoryById".equals(method.getName()) ? registeredRepo() : null);
    }

    private static GitService git(Object... answersKeyValuePairs) {
        Map<String, Object> answers = new LinkedHashMap<>();
        for (int i = 0; i + 1 < answersKeyValuePairs.length; i += 2) {
            answers.put((String) answersKeyValuePairs[i], answersKeyValuePairs[i + 1]);
        }
        return (GitService) Proxy.newProxyInstance(GitService.class.getClassLoader(),
                new Class<?>[] { GitService.class }, (proxy, method, args) -> {
                    if (answers.containsKey(method.getName())) {
                        return answers.get(method.getName());
                    }
                    Class<?> rt = method.getReturnType();
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
                });
    }

    /** 业务体的 wire 键集（真实序列化路径，NON_NULL 省略一并生效）。 */
    private static Set<String> wireKeys(Object data) {
        return wireKeys(MAPPER.valueToTree(data));
    }

    private static Set<String> wireKeys(JsonNode node) {
        Set<String> names = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static Set<String> keysOf(String... names) {
        return new LinkedHashSet<>(Arrays.asList(names));
    }

    // ── 9 个端点的键面 ──

    @Test
    @DisplayName("GET /status 键面 = repoId/repoName/currentCommit/branch（与 Map 时代逐字相等）")
    void statusKeysUnchanged() {
        GitController controller = new GitController(git("getCurrentCommitId", "deadbeef1"), registry());
        Set<String> keys = wireKeys(controller.status("pipeline-core").getBody().getData());
        assertEquals(keysOf("repoId", "repoName", "currentCommit", "branch"), keys);
    }

    @Test
    @DisplayName("GET /commits 键面 = repoId/items/offset/limit/hasMore（未到尽头不出 total）")
    void commitsKeysUnchangedWithoutTotal() {
        List<GitCommitView> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            six.add(new GitCommitView("sha" + i, "msg" + i, "author", "a@e.local", new Date()));
        }
        GitController controller = new GitController(git("listCommits", six), registry());
        GitController.GitCommitsVO data = controller.commits("pipeline-core", 0, 5).getBody().getData();
        assertEquals(keysOf("repoId", "items", "offset", "limit", "hasMore"), wireKeys(data));

        JsonNode first = MAPPER.valueToTree(data.getItems().get(0));
        assertEquals(keysOf("id", "commitId", "message", "author", "committedAt"), wireKeys(first),
                "单条提交的双键（id 与 commitId 同值）是 Map 时代的既有契约，禁「顺手合并」成一枚");
        assertEquals(first.get("id").asText(), first.get("commitId").asText(), "两键必须同值，非两个语义");
    }

    @Test
    @DisplayName("GET /commits 读到尽头 ⇒ total 键出现（省略/出现两态均由本门钉住）")
    void commitsKeysUnchangedWithTotal() {
        List<GitCommitView> two = List.of(
                new GitCommitView("sha1", "m1", "a", "a@e.local", new Date()),
                new GitCommitView("sha2", "m2", "a", "a@e.local", new Date()));
        GitController controller = new GitController(git("listCommits", two), registry());
        GitController.GitCommitsVO data = controller.commits("pipeline-core", 0, 20).getBody().getData();
        assertEquals(keysOf("repoId", "items", "offset", "limit", "hasMore", "total"), wireKeys(data));
    }

    @Test
    @DisplayName("GET /diff 键面 = repoId/repository/from/to/filesChanged/changedCount（属主动态 Map 的白名单映射）")
    void diffKeysUnchanged() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("changeType", "MODIFIED");
        row.put("oldPath", "a.json");
        row.put("newPath", "a.json");
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("repository", "/data/ecos-git-repos/pipeline-core");
        raw.put("from", "HEAD~1");
        raw.put("to", "HEAD");
        raw.put("filesChanged", List.of(row));
        raw.put("changedCount", 1);

        GitController controller = new GitController(git("diff", raw), registry());
        GitController.GitDiffVO data = controller.diff("pipeline-core", null, null).getBody().getData();
        assertEquals(keysOf("repoId", "repository", "from", "to", "filesChanged", "changedCount"), wireKeys(data));
        assertEquals(1, data.getChangedCount().intValue(), "属主计数必须透传，禁本地重算冒充");

        JsonNode file = MAPPER.valueToTree(data.getFilesChanged().get(0));
        assertEquals(keysOf("changeType", "oldPath", "newPath"), wireKeys(file));
        assertEquals("MODIFIED", file.get("changeType").asText());
    }

    @Test
    @DisplayName("POST /commit 与 /tag 键面（commitId/tag 为属主真实回执键）")
    void commitAndTagKeysUnchanged() {
        GitController controller = new GitController(
                git("commitAllChanges", "cafe1234", "createTag", "v1.0.0"), registry());

        GitController.GitCommitRequest commit = new GitController.GitCommitRequest();
        commit.setMessage("release");
        commit.setAuthorName("bot");
        commit.setAuthorEmail("bot@ecos.local");
        Set<String> commitKeys = wireKeys(controller.commit("pipeline-core", commit).getBody().getData());
        assertEquals(keysOf("repoId", "commitId", "message", "committedAt"), commitKeys);

        GitController.GitTagRequest tag = new GitController.GitTagRequest();
        tag.setName("v1.0.0");
        Set<String> tagKeys = wireKeys(controller.tag("pipeline-core", tag).getBody().getData());
        assertEquals(keysOf("repoId", "tag", "message", "createdAt"), tagKeys);
    }

    @Test
    @DisplayName("能力未接面键面：rollback/branch/push/pull 均含 status+reason（禁把不可用报成无字段成功）")
    void notAvailableKeysUnchanged() {
        GitController controller = new GitController(git(), registry());

        GitController.GitRollbackRequest rollback = new GitController.GitRollbackRequest();
        rollback.setCommitId("sha123");
        GitController.GitRollbackResultVO rb = controller.rollback("pipeline-core", rollback).getBody().getData();
        assertEquals(keysOf("repoId", "rollbackTo", "status", "reason"), wireKeys(rb));
        assertEquals("not_available", rb.getStatus());

        GitController.GitBranchSwitchRequest branch = new GitController.GitBranchSwitchRequest();
        branch.setBranch("dev");
        Set<String> brKeys = wireKeys(controller.switchBranch("pipeline-core", branch).getBody().getData());
        assertEquals(keysOf("repoId", "requestedBranch", "status", "reason"), brKeys);

        Set<String> pushKeys = wireKeys(controller.push("pipeline-core", null).getBody().getData());
        assertEquals(keysOf("repoId", "status", "reason"), pushKeys);
        Set<String> pullKeys = wireKeys(controller.pull("pipeline-core", null).getBody().getData());
        assertEquals(keysOf("repoId", "status", "reason"), pullKeys);
    }

    @Test
    @DisplayName("GET /branches 键面 = repoId/current/branches/status")
    void branchesKeysUnchanged() {
        GitController controller = new GitController(git("listBranches", List.of("main", "dev")), registry());
        GitController.GitBranchesVO data = controller.branches("pipeline-core").getBody().getData();
        assertEquals(keysOf("repoId", "current", "branches", "status"), wireKeys(data));
        assertEquals(List.of("main", "dev"), data.getBranches());
    }

    // ── 请求绑定面：JSON 键 → DTO 字段（旧 Map 键名不变）──

    @Test
    @DisplayName("请求体 JSON 键面与旧 Map 读取的键名逐一对齐（改名即断绑定）")
    void requestBindingsMatchLegacyJsonKeys() throws Exception {
        GitController.GitCommitRequest commit = MAPPER.readValue(
                "{\"message\":\"m\",\"authorName\":\"n\",\"authorEmail\":\"e\"}",
                GitController.GitCommitRequest.class);
        assertEquals("m", commit.getMessage());
        assertEquals("n", commit.getAuthorName());
        assertEquals("e", commit.getAuthorEmail());

        GitController.GitTagRequest tag = MAPPER.readValue("{\"name\":\"t\",\"message\":\"tm\"}",
                GitController.GitTagRequest.class);
        assertEquals("t", tag.getName());
        assertEquals("tm", tag.getMessage());

        GitController.GitRollbackRequest rollback = MAPPER.readValue("{\"commitId\":\"sha\"}",
                GitController.GitRollbackRequest.class);
        assertEquals("sha", rollback.getCommitId());

        GitController.GitBranchSwitchRequest branch = MAPPER.readValue("{\"branch\":\"dev\"}",
                GitController.GitBranchSwitchRequest.class);
        assertEquals("dev", branch.getBranch());

        GitController.GitPushPullRequest sync = MAPPER.readValue("{\"message\":\"psuh\"}",
                GitController.GitPushPullRequest.class);
        assertEquals("psuh", sync.getMessage());
    }

    @Test
    @DisplayName("信封面未被本次改造动过：成功 code=0 且 data 键在 data 位下")
    void envelopeShapeUntouched() {
        GitController controller = new GitController(git("getCurrentCommitId", "abc"), registry());
        ApiResponse<GitController.GitStatusVO> body = controller.status("pipeline-core").getBody();
        assertEquals(0, body.getCode());
        assertEquals(keysOf("repoId", "repoName", "currentCommit", "branch"), wireKeys(body.getData()));
    }
}
