package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitPullRequest;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitOperationResultVO;
import com.chinacreator.gzcm.runtime.core.crypto.SecurityCryptoEgress;
import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService;
import com.chinacreator.gzcm.runtime.access.git.GitService;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** PipelineGitService H4-T1/T3 — repositoryId 解析、localPath 拒绝、gitUrl 允许域注册表。 */
@ExtendWith(MockitoExtension.class)
class PipelineGitServiceH4Test {

    @Mock
    private GitService gitService;
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private SysConfigService sysConfigService;
    @Mock
    private GitRepositoryService gitRepositoryService;
    @Mock
    private GitRepoRootResolver repoRootResolver;
    @Mock
    private ObjectProvider<SecurityCryptoEgress> cryptoEgressProvider;

    private PipelineGitService service;

    @BeforeEach
    void setUp() {
        service = new PipelineGitService(gitService, jdbc, sysConfigService,
                gitRepositoryService, repoRootResolver, cryptoEgressProvider);
    }

    @Test
    @DisplayName("仅传已废弃 localPath → not_available + reason，不执行 Git 操作")
    void legacyLocalPathOnlyIsRejected() {
        PipelineGitService.LocalPathResolution res = service.resolveLocalPath("t1", null, "C:/evil/path");
        assertTrue(res.unavailable());
        assertEquals("not_available", res.status());
        assertTrue(res.reason().contains("repositoryId"));
        assertNull(res.localPath());
    }

    @Test
    @DisplayName("pull 仅传 localPath → VO status=not_available（禁伪成功）")
    void pullWithLegacyLocalPathReturnsNotAvailable() throws Exception {
        PipelineGitPullRequest req = new PipelineGitPullRequest();
        req.setLocalPath("/tmp/whatever");
        PipelineGitOperationResultVO vo = service.pull("t1", req);
        assertEquals("not_available", vo.getStatus());
        assertNotNull(vo.getReason());
        assertNull(vo.getYamlUpdated());
    }

    @Test
    @DisplayName("repositoryId 命中 runtime 注册表 → 用注册路径")
    void repositoryIdUsesRuntimeRegistry() throws Exception {
        GitRepository registered = new GitRepository();
        registered.setRepositoryId("pipeline-repo-1");
        registered.setLocalPath("/srv/ecos-git-repos/pipeline-repo-1");
        when(gitRepositoryService.getRepositoryById("pipeline-repo-1")).thenReturn(registered);

        PipelineGitService.LocalPathResolution res = service.resolveLocalPath("t1", "pipeline-repo-1", null);
        assertFalse(res.unavailable());
        assertEquals("/srv/ecos-git-repos/pipeline-repo-1", res.localPath());
    }

    @Test
    @DisplayName("repositoryId 未注册 → sys_config 单源根路径兜底解析")
    void repositoryIdFallsBackToRootResolver() throws Exception {
        when(gitRepositoryService.getRepositoryById("p-2"))
                .thenThrow(new GitRepositoryService.GitRepositoryException("not found"));
        when(repoRootResolver.resolveRepoPath("p-2")).thenReturn("/root/p-2");

        PipelineGitService.LocalPathResolution res = service.resolveLocalPath("t1", "p-2", null);
        assertFalse(res.unavailable());
        assertEquals("/root/p-2", res.localPath());
    }

    @Test
    @DisplayName("缺省路径落 {repoRoot}/pipeline/{id}（tmpdir 缺省已废除）")
    void defaultPathUsesSysConfigSingleSource() {
        when(repoRootResolver.resolveUnderRoot("pipeline", "t9")).thenReturn("/root/pipeline/t9");
        PipelineGitService.LocalPathResolution res = service.resolveLocalPath("t9", null, null);
        assertFalse(res.unavailable());
        assertEquals("/root/pipeline/t9", res.localPath());
    }

    @Test
    @DisplayName("gitUrl 内网/链路本地/元数据地址一律拒绝")
    void gitUrlBlockedForInternalHosts() {
        assertThrows(IllegalArgumentException.class, () -> service.validateGitUrl("https://169.254.169.254/r.git"));
        assertThrows(IllegalArgumentException.class, () -> service.validateGitUrl("http://127.0.0.1:8080/r.git"));
        assertThrows(IllegalArgumentException.class, () -> service.validateGitUrl("http://10.1.2.3/r.git"));
        assertThrows(IllegalArgumentException.class, () -> service.validateGitUrl("http://192.168.0.9/r.git"));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateGitUrl("http://metadata.google.internal/r.git"));
        assertThrows(IllegalArgumentException.class, () -> service.validateGitUrl("http://localhost:2222/r.git"));
        assertThrows(IllegalArgumentException.class, () -> service.validateGitUrl("file:///etc/passwd"));
    }

    @Test
    @DisplayName("允许域注册表缺省拒绝；命中注册域放行（含子域）")
    void gitUrlAllowlistDeniesByDefault() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validateGitUrl("https://gitlab.example.com/r.git"));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateGitUrl("https://evil.com/?h=github.com"));
        when(sysConfigService.getString("ecos.git.allowed_hosts", "")).thenReturn("github.com");
        assertDoesNotThrow(() -> service.validateGitUrl("https://github.com/ecos/r.git"));
        assertDoesNotThrow(() -> service.validateGitUrl("https://sub.github.com/ecos/r.git"));
    }

    @Test
    @DisplayName("scp 式 git@host:path 主机可解析并入注册表校验")
    void scpStyleGitUrlSupported() {
        assertEquals("github.com", PipelineGitService.extractHost("git@github.com:ecos/repo.git"));
        when(sysConfigService.getString("ecos.git.allowed_hosts", "")).thenReturn("github.com");
        assertDoesNotThrow(() -> service.validateGitUrl("git@github.com:ecos/repo.git"));
    }

    @Test
    @DisplayName("凭据不再来自请求体：sys_config 未配置时按匿名处理")
    void credentialsResolveToAnonymousWhenUnset() {
        String[] creds = service.resolveGitCredentials();
        assertNull(creds[0]);
        assertNull(creds[1]);
    }

    @Test
    @DisplayName("listBranches 空 repositoryId → 指引改用 repositoryId")
    void listBranchesRequiresRepositoryId() {
        assertThrows(IllegalArgumentException.class, () -> service.listBranches(null));
        assertThrows(IllegalArgumentException.class,
                () -> service.listBranchesByLegacyLocalPath("C:/git-cache"));
    }

    @Test
    @DisplayName("本地路径安全段校验：.. 与分隔符拒绝")
    void safeSegmentGuards() {
        assertThrows(IllegalArgumentException.class, () -> GitRepoRootResolver.requireSafeSegment("../x"));
        assertThrows(IllegalArgumentException.class, () -> GitRepoRootResolver.requireSafeSegment("a/b"));
        assertThrows(IllegalArgumentException.class, () -> GitRepoRootResolver.requireSafeSegment("a\\b"));
        assertThrows(IllegalArgumentException.class, () -> GitRepoRootResolver.requireSafeSegment(null));
        assertDoesNotThrow(() -> GitRepoRootResolver.requireSafeSegment("pipeline-001"));
    }
}
