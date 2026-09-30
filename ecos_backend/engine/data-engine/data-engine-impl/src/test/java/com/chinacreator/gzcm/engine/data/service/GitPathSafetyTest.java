package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 详细设计-00 F00-06 — Git 唯一出口的路径安全门禁：
 * {@link GitRepoRootResolver} 只暴露 {@code repositoryId} 单参 + 名字段校验
 * （{@code requireSafeSegment}），拒绝路径穿越 / 分隔符 / 盘符 / 空白段。
 *
 * <p>验收：{@code mvn -pl engine/data-engine/data-engine-impl -am test -Dtest='GitPathSafetyTest'}</p>
 */
class GitPathSafetyTest {

    private GitRepoRootResolver resolver;
    @TempDir
    Path tmp;

    @BeforeEach
    void setUp() {
        SysConfigService cfg = mock(SysConfigService.class);
        when(cfg.getString(GitRepoRootResolver.KEY_REPO_ROOT))
                .thenReturn(tmp.toString().replace('\\', '/'));
        resolver = new GitRepoRootResolver(cfg);
    }

    @Test
    @DisplayName("1. 合法 repoId → 落在 root 层下的单层目录")
    void validRepoIdResolvesUnderRoot() {
        assertTrue(resolver.resolveRepoPath("pipeline").startsWith(resolver.resolveRepoRoot()));
    }

    @Test
    @DisplayName("2. 拒绝 '..' 段（路径穿越）")
    void repoIdContainingDotDotRejected() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveRepoPath(".."));
    }

    @Test
    @DisplayName("3. 拒绝含 '/' 或 '\\\\' 或盘符 'c:' 的段")
    void repoIdWithSeparatorOrDriveRejected() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveRepoPath("a/b"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveRepoPath("a\\b"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveRepoPath("c:/x"));
    }

    @Test
    @DisplayName("4. 空段/空白段/null 拒绝")
    void repoIdEmptyOrBlankRejected() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveRepoPath(""));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveRepoPath("   "));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveRepoPath(null));
    }

    @Test
    @DisplayName("5. 多段 resolveUnderRoot 也拒绝 '..' 和分隔符")
    void multiSegmentRequiresSafe() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolveUnderRoot("a", "..", "b"));
        assertTrue(resolver.resolveUnderRoot("a", "b").startsWith(resolver.resolveRepoRoot()));
    }

    @Test
    @DisplayName("6. requireInsideRoot：越出 root 的路径 + 空 localPath 必须拒绝")
    void requireInsideRootRejectsEscape() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.requireInsideRoot(tmp.getParent().toString()),
                "越出 root 的路径必须拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> resolver.requireInsideRoot(""), "空 localPath 必须拒绝");
    }

    @Test
    @DisplayName("7. 静态 requireSafeSegment 边界")
    void staticSegmentGuards() {
        GitRepoRootResolver.requireSafeSegment("ok");              // 合法
        assertThrows(IllegalArgumentException.class,
                () -> GitRepoRootResolver.requireSafeSegment(".."));
        assertThrows(IllegalArgumentException.class,
                () -> GitRepoRootResolver.requireSafeSegment("a/b"));
        assertThrows(IllegalArgumentException.class,
                () -> GitRepoRootResolver.requireSafeSegment("a\\b"));
        assertThrows(IllegalArgumentException.class,
                () -> GitRepoRootResolver.requireSafeSegment("a:b"));
        assertThrows(IllegalArgumentException.class,
                () -> GitRepoRootResolver.requireSafeSegment(""));
        assertThrows(IllegalArgumentException.class,
                () -> GitRepoRootResolver.requireSafeSegment(null));
    }
}
