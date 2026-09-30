package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * GitRepoRootResolver — 仓库根路径单源 + path-traversal 拒绝。
 *
 * <p>F02-12（详细设计-02）：拒绝统一改抛 {@link BusinessException}（明确业务错误，
 * 不再裸 500）；新增 symlink 逃逸（②）与写入期存在性严格校验（①）验收。</p>
 */
@ExtendWith(MockitoExtension.class)
class GitRepoRootResolverTest {

    @Mock
    private SysConfigService sysConfigService;

    @TempDir
    Path tmp;

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
    void configuredRootUsed() throws Exception {
        Path root = tmp.resolve("configured-root");          // 完全不存在 → 延迟建仓允许
        when(sysConfigService.getString("ecos_git_repo_root")).thenReturn(root.toString().replace('\\', '/'));
        GitRepoRootResolver resolver = new GitRepoRootResolver(sysConfigService);
        assertEquals(root.normalize().toString(), resolver.resolveRepoRoot());
    }

    @Test
    @DisplayName("非法 repoRoot（含 ..）被拒 → BusinessException")
    void traversalRepoRootRejected() {
        when(sysConfigService.getString("ecos_git_repo_root")).thenReturn("D:/repos/../evil");
        GitRepoRootResolver resolver = new GitRepoRootResolver(sysConfigService);
        assertThrows(BusinessException.class, resolver::resolveRepoRoot);
    }

    @Test
    @DisplayName("非法 repoRoot（相对路径）被拒 → BusinessException")
    void relativeRepoRootRejected() {
        when(sysConfigService.getString("ecos_git_repo_root")).thenReturn("relative/ecos-git-repos");
        GitRepoRootResolver resolver = new GitRepoRootResolver(sysConfigService);
        assertThrows(BusinessException.class, resolver::resolveRepoRoot);
    }

    @Test
    @DisplayName("repoId 路径穿越被拒 → BusinessException")
    void traversalRepoIdRejected() {
        when(sysConfigService.getString("ecos_git_repo_root"))
                .thenReturn(tmp.toString().replace('\\', '/'));
        GitRepoRootResolver resolver = new GitRepoRootResolver(sysConfigService);
        assertThrows(BusinessException.class, () -> resolver.resolveRepoPath("../escape"));
        assertThrows(BusinessException.class, () -> resolver.resolveRepoPath("a/b"));
        assertThrows(BusinessException.class, () -> resolver.resolveRepoPath("D:\\abs"));
    }

    @Test
    @DisplayName("repoId 正常解析为 {repoRoot}/{repoId}")
    void repoIdResolvesUnderRoot() throws Exception {
        when(sysConfigService.getString("ecos_git_repo_root"))
                .thenReturn(tmp.toString().replace('\\', '/'));
        GitRepoRootResolver resolver = new GitRepoRootResolver(sysConfigService);
        assertTrue(resolver.resolveRepoPath("ontology").startsWith(resolver.resolveRepoRoot()));
    }

    @Test
    @DisplayName("越出仓库根的外部 localPath 被拒，根内路径放行")
    void requireInsideRoot() {
        when(sysConfigService.getString("ecos_git_repo_root"))
                .thenReturn(tmp.toString().replace('\\', '/'));
        GitRepoRootResolver resolver = new GitRepoRootResolver(sysConfigService);
        String inside = tmp.resolve("pipeline/t1").toString().replace('\\', '/');
        Path resolved = Paths.get(resolver.requireInsideRoot(inside));
        assertEquals(tmp.resolve("pipeline").resolve("t1"), resolved, "根内路径回显应与入参指向同一路径（Path 等值，分隔符不敏感）");
        assertThrows(BusinessException.class,
                () -> resolver.requireInsideRoot(tmp.getParent().toString()));
    }

    @Test
    @DisplayName("F02-12①：写入入口（resolveRepoRootOrThrow）严格校验存在性")
    void strictWriteEntryRejectsMissingRoot() throws Exception {
        Path missing = tmp.resolve("not-existing");
        when(sysConfigService.getString("ecos_git_repo_root")).thenReturn(missing.toString().replace('\\', '/'));
        GitRepoRootResolver resolver = new GitRepoRootResolver(sysConfigService);
        assertThrows(BusinessException.class, resolver::resolveRepoRootOrThrow);
        // 反证：真实存在的根可以在写入入口放行
        Path ok = tmp.resolve("exists");
        java.nio.file.Files.createDirectories(ok);
        when(sysConfigService.getString("ecos_git_repo_root")).thenReturn(ok.toString().replace('\\', '/'));
        assertEquals(ok.normalize().toString(), new GitRepoRootResolver(sysConfigService).resolveRepoRootOrThrow());
    }

    @Test
    @DisplayName("F02-12②：根内 symlink 指向根外 → 边界拒绝（Windows 集权环境可能无 symlink 能力，则跳过 → 反证场景成立）")
    void symlinkEscapeRejected() throws Exception {
        Path root = tmp.resolve("groot");
        Path outside = tmp.resolve("outside");
        java.nio.file.Files.createDirectories(root);
        java.nio.file.Files.createDirectories(outside);
        Path link = root.resolve("escape");
        boolean linkCreated = false;
        try {
            java.nio.file.Files.createSymbolicLink(link, outside);
            linkCreated = true;
        } catch (java.io.IOException skipped) {
            // Windows 测试机若无 symlink 权限 → 反证：至少逻辑路径在 gitignored 沙盒内（无穿越可发生）
        }
        if (!linkCreated) {
            // 构造一个「以 .. 越界后再回到根名内」的逻辑路径（已被 requireSafeSegment 拒绝），
            // 直接对 requireInsideRoot 传入拼接后的越界路径 → 必须拒绝。
            assertThrows(BusinessException.class,
                    () -> new GitRepoRootResolver(stub(root)).requireInsideRoot(outside.toString()));
            return;
        }
        // 反证：若 symlink 存在于根，向其下的越界读取必须被拒（目标 = outside 真实目录）
        try (var stream = java.nio.file.Files.walk(tmp, 3)) {
            // warm up path cache（部分 FS 首次解析慢）
            stream.filter(java.nio.file.Files::isDirectory).sorted(Comparator.reverseOrder()).findFirst();
        }
        assertThrows(BusinessException.class,
                () -> new GitRepoRootResolver(stub(root)).requireInsideRoot(
                        link.resolve("real-file").toString()));
    }

    private static SysConfigService stub(Path root) {
        SysConfigService cfg = org.mockito.Mockito.mock(SysConfigService.class);
        when(cfg.getString(GitRepoRootResolver.KEY_REPO_ROOT))
                .thenReturn(root.toString().replace('\\', '/'));
        return cfg;
    }
}
