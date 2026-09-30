package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** GitRepoRootResolver — 仓库根路径单源（sys_config ecos_git_repo_root）+ path-traversal 拒绝。 */
@ExtendWith(MockitoExtension.class)
class GitRepoRootResolverTest {

    @Mock
    private SysConfigService sysConfigService;

    @Test
    @DisplayName("缺省（未配置）回落 {user.home}/ecos-git-repos")
    void blankConfigFallsBackToUserHomeDefault() {
        when(sysConfigService.getString("ecos_git_repo_root")).thenReturn(null);
        GitRepoRootResolver resolver = new GitRepoRootResolver(sysConfigService);
        String root = resolver.resolveRepoRoot();
        assertEquals(Paths.get(System.getProperty("user.home"), "ecos-git-repos").toString(), root);
    }

    @Test
    @DisplayName("配置值生效")
    void configuredRootUsed() {
        when(sysConfigService.getString("ecos_git_repo_root")).thenReturn(System.getProperty("java.io.tmpdir") + "/ecos-git-root");
        GitRepoRootResolver resolver = new GitRepoRootResolver(sysConfigService);
        assertEquals(Paths.get(System.getProperty("java.io.tmpdir"), "ecos-git-root").normalize().toString(),
                resolver.resolveRepoRoot());
    }

    @Test
    @DisplayName("非法 repoRoot（含 ..）被拒")
    void traversalRepoRootRejected() {
        when(sysConfigService.getString("ecos_git_repo_root")).thenReturn("D:/repos/../evil");
        GitRepoRootResolver resolver = new GitRepoRootResolver(sysConfigService);
        assertThrows(IllegalArgumentException.class, resolver::resolveRepoRoot);
    }

    @Test
    @DisplayName("非法 repoRoot（相对路径）被拒")
    void relativeRepoRootRejected() {
        when(sysConfigService.getString("ecos_git_repo_root")).thenReturn("relative/ecos-git-repos");
        GitRepoRootResolver resolver = new GitRepoRootResolver(sysConfigService);
        assertThrows(IllegalArgumentException.class, resolver::resolveRepoRoot);
    }

    @Test
    @DisplayName("repoId 路径穿越被拒")
    void traversalRepoIdRejected() {
        when(sysConfigService.getString("ecos_git_repo_root")).thenReturn(System.getProperty("java.io.tmpdir") + "/root");
        GitRepoRootResolver resolver = new GitRepoRootResolver(sysConfigService);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveRepoPath("../escape"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveRepoPath("a/b"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveRepoPath("D:\\abs"));
    }

    @Test
    @DisplayName("repoId 正常解析为 {repoRoot}/{repoId}")
    void repoIdResolvesUnderRoot() {
        when(sysConfigService.getString("ecos_git_repo_root")).thenReturn(System.getProperty("java.io.tmpdir") + "/root");
        GitRepoRootResolver resolver = new GitRepoRootResolver(sysConfigService);
        assertEquals(Paths.get(System.getProperty("java.io.tmpdir"), "root", "ontology").toString(),
                resolver.resolveRepoPath("ontology"));
    }

    @Test
    @DisplayName("越出仓库根的外部 localPath 被拒，根内路径放行")
    void requireInsideRoot() {
        String root = Paths.get(System.getProperty("java.io.tmpdir"), "root").toString();
        when(sysConfigService.getString("ecos_git_repo_root")).thenReturn(root);
        GitRepoRootResolver resolver = new GitRepoRootResolver(sysConfigService);
        assertEquals(Paths.get(root, "pipeline", "t1").toString(),
                resolver.requireInsideRoot(Paths.get(root, "pipeline", "t1").toString()));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.requireInsideRoot(Paths.get(System.getProperty("java.io.tmpdir"), "outside").toString()));
    }
}
