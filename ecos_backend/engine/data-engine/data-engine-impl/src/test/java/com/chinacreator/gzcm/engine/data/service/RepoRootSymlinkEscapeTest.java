package com.chinacreator.gzcm.engine.data.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;

/**
 * F02-12 ② symlink 逃逸（W63 补充）— 验收守卫测试：
 * 当 {@code repoRoot} 内存在 symlink 指向仓库根竞争外的目录（含真实 secret）时，
 * {@link GitRepoRootResolver#requireInsideRoot} 必须拒绝（返回归真路径不在
 * {@code toRealPath(root)} 内），不允许被静默放行成 Surface 上的可写路径。
 *
 * <p>分支观察（Windows 开发机可能未开 Developer Mode / 无管理员 symlink 权限）：</p>
 * <ul>
 *   <li>symlink 建立成功 → 严格验证 {@code requireInsideRoot} 拒绝 symlink 下的越界目标；</li>
 *   <li>symlink 不支持 → 退化为直接对「不属于 repoRoot 的绝对路径」执行
 *       {@code requireInsideRoot}，断言仍拒绝（逻辑 normalize + startsWith 双层守卫）。</li>
 * </ul>
 *
 * <p>永久守卫：堵死「以 .. 段穿越 + 目标在 root 名内的 symlink 陷阱」这类历史
 * 攻击面 — 未加 {@code toRealPath} 前仅逻辑 normalize 会被绕过，F02-12②
 * 修复后逐段 toRealPath 保证真实路径仍在真实根内。</p>
 *
 * @author ECOS Data Engine Team / F02-12
 */
@DisplayName("F02-12② 边界守卫：symlink 指向 repoRoot 外 → 拒")
class RepoRootSymlinkEscapeTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("symlink 指向 repoRoot 外（能创建时）→ requireInsideRoot 拒绝越界路径")
    void symlinkInsideRootToOutside_isRejected() throws IOException {
        // 场景：repoRoot 下挂一个 symlink，其真实目标在 tmp（根外）里并存放 secret.txt
        Path base = tmp.resolve("groot");
        Path outsideTarget = tmp.resolve("outside_secret");
        Files.createDirectories(base);
        Files.createDirectories(outsideTarget);
        Path secret = outsideTarget.resolve("secret.txt");
        Files.writeString(secret, "top-secret");

        Path link = base.resolve("escape");
        boolean km = false;
        try {
            Files.createSymbolicLink(link, outsideTarget);
            km = Files.isSymbolicLink(link);
        } catch (UnsupportedOperationException | IOException skipped) {
            // Windows 无 symlink 权限（未开 Developer Mode / 无管理员）
            km = false;
        }

        if (km) {
            GitRepoRootResolver r = new GitRepoRootResolver(stub(base));
            // symlink 下的路径必须被拒（toRealPath 后不在 realBase 内）
            assertThrows(BusinessException.class,
                    () -> r.requireInsideRoot(link.resolve("secret.txt").toString()),
                    "symlink 指向根外的路径必须被 requireInsideRoot 拒绝");
            // 直接对 symlink 本身 requireInsideRoot 也必须拒
            assertThrows(BusinessException.class,
                    () -> r.requireInsideRoot(link.toString()),
                    "symlink 路径本身指向根外，必须拒");
        } else {
            // 反证：负向 scene — 直接对「不属于 repoRoot 的绝对路径」requireInsideRoot 必须拒绝，
            // 保证即使 symlink 不支持（Windows 常见情形）逻辑守卫仍完整
            GitRepoRootResolver r = new GitRepoRootResolver(stub(base));
            assertThrows(BusinessException.class,
                    () -> r.requireInsideRoot(outsideTarget.resolve("secret.txt").toString()),
                    "根外绝对路径 regardless 是否 symlink 必须被拒绝");
        }

        // 正对照：根内合法路径仍被放行（保守反证）
        Path inside = base.resolve("pipeline").resolve("t1");
        Files.createDirectories(inside);
        GitRepoRootResolver r2 = new GitRepoRootResolver(stub(base));
        String resolved = r2.requireInsideRoot(inside.toString());
        assertTrue(Files.exists(Path.of(resolved)) && resolved.startsWith(base.toString()),
                "根内合法子路径应解析为仍在根内的真实路径: " + resolved);
    }

    @Test
    @DisplayName("requireInsideRoot 空/空白 localPath → 显式 BusinessException（F02-12①）")
    void requireInsideRoot_blankPath_rejects() throws IOException {
        Path base = tmp.resolve("groot2");
        Files.createDirectories(base);
        GitRepoRootResolver r = new GitRepoRootResolver(stub(base));
        assertThrows(BusinessException.class, () -> r.requireInsideRoot(null), "null 必须拒");
        assertThrows(BusinessException.class, () -> r.requireInsideRoot(""), "空字符串必须拒");
        assertThrows(BusinessException.class, () -> r.requireInsideRoot("   "), "空白必须拒");
    }

    private static SysConfigService stub(Path root) throws IOException {
        Files.createDirectories(root);
        SysConfigService cfg = mock(SysConfigService.class);
        when(cfg.getString(GitRepoRootResolver.KEY_REPO_ROOT))
                .thenReturn(root.toString().replace('\\', '/'));
        return cfg;
    }
}
