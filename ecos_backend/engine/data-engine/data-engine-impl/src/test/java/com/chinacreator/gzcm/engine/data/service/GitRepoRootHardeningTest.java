package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;

/**
 * F02-12（详细设计-02 §二.12）Git 归档加固 验收测试。
 *
 * <p>覆盖设计验收用例：</p>
 * <ul>
 *   <li>{@code RepoRootMissingDirectoryTest} — 配置的仓库根目录不存在 → 严格解析拒（授业务异常而非裸 500）</li>
 *   <li>{@code RepoRootSymlinkEscapeTest} — 仓库根为 symlink → {@code toRealPath} 解析后取真实位置（防 symlink 回转）</li>
 * </ul>
 *
 * <p>shape-only 的老用例（非存在性）见 {@link GitRepoRootResolverTest}，本类专注 F02-12 新增严格路径。</p>
 */
@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GitRepoRootHardeningTest {

    @Mock
    private SysConfigService sysConfigService;

    @TempDir
    Path tempDir;

    @Test
    @Order(1)
    @DisplayName("RepoRootMissingDirectoryTest — 配置根目录不存在 → resolveExistingRepoRoot 抛 IAE")
    void missingDirectory_rejected() {
        Path missing = tempDir.resolve("does-not-exist-xyz");
        when(sysConfigService.getString("ecos_git_repo_root")).thenReturn(missing.toString());
        GitRepoRootResolver resolver = new GitRepoRootResolver(sysConfigService);
        assertThrows(IllegalArgumentException.class, resolver::resolveExistingRepoRoot,
                "不存在的仓库根目录应被严格解析拒绝");
    }

    @Test
    @Order(2)
    @DisplayName("存在且可写的目录 → resolveExistingRepoRoot 返回其真实路径")
    void existingWritableDir_resolves() throws Exception {
        Files.createDirectories(tempDir);
        when(sysConfigService.getString("ecos_git_repo_root")).thenReturn(tempDir.toString());
        GitRepoRootResolver resolver = new GitRepoRootResolver(sysConfigService);
        String real = resolver.resolveExistingRepoRoot();
        assertEquals(tempDir.toRealPath().toString(), real,
                "存在目录应解析为其 toRealPath 真实路径");
    }

    @Test
    @Order(3)
    @DisplayName("RepoRootSymlinkEscapeTest — 仓库根为 symlink → 解析为 symlink 目标真实路径")
    void symlinkRoot_resolvedToRealTarget() throws Exception {
        Path target = tempDir.resolve("real-target");
        Files.createDirectories(target);
        Path link = tempDir.resolve("sym-root");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | java.io.IOException e) {
            // Windows/CI 无 symlink 权限 → 该验收用例在支持 symlink 的环境执行；本地无权限则跳过（不假通过）
            assumeTrue(false, "当前环境不支持创建 symlink（" + e.getClass().getSimpleName() + "）");
            return;
        }
        try {
            when(sysConfigService.getString("ecos_git_repo_root")).thenReturn(link.toString());
            GitRepoRootResolver resolver = new GitRepoRootResolver(sysConfigService);
            String real = resolver.resolveExistingRepoRoot();
            assertEquals(target.toRealPath().toString(), real,
                    "symlink 根应解析到其指向的真实目录（toRealPath），而非 symlink 自身");
            // symlink 基线（link）必须≠ 其 toRealPath 之前的名义路径，才证明解析发生了
            assertTrue(real.equals(target.toRealPath().toString()),
                    "解析后的根应等于 symlink 目标的真实位置");
        } finally {
            try {
                Files.deleteIfExists(link);
            } catch (java.io.IOException ignored) {
                // 清理失败不影响断言
            }
        }
    }
}
