package com.chinacreator.gzcm.engine.data.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;

/**
 * F02-12 ① 存在性校验守卫 — 验收测试：
 * 仓库根路径不存在 / 不是目录时，写入入口 {@link GitRepoRootResolver#resolveRepoRootOrThrow()}
 * 必须抛 {@link BusinessException}（明确业务错误），而非 NPE / 悄悄通过；
 * 读取入口 {@link GitRepoRootResolver#resolveRepoRoot()} 允许延迟建仓（父目录可读即 WARN 放行）。
 *
 * <p>核心不变量：</p>
 * <ul>
 *   <li>resolveRepoRootOrThrow()：根不存在 → BusinessException（arity isDirectory 检查生效）；</li>
 *   <li>resolveRepoRoot()：根不存在但父目录存在可读 → 允许返回（延迟建仓语义）；</li>
 *   <li>任何路径缺失场景都不得抛 NullPointerException / 裸 500。</li>
 * </ul>
 *
 * @author ECOS Data Engine Team / F02-12
 */
@DisplayName("F02-12① 守卫：repoRoot 缺失/非目录 → BusinessException 而非 NPE")
class RepoRootMissingDirectoryTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("写入入口：根目录不存在 → BusinessException（非 NPE），消息含明确原因")
    void writeEntry_missingRoot_throwsBusinessNotNpe() throws Exception {
        Path missing = tmp.resolve("does-not-exist-yet");
        GitRepoRootResolver r = new GitRepoRootResolver(stub(missing));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> r.resolveRepoRootOrThrow(),
                "resolveRepoRootOrThrow 对缺失根必须抛业务异常，不得 NPE 或静默通过");
        assertNotNull(ex.getMessage(), "业务异常必须带可读消息（不裸 500）");
        assertTrue(ex.getMessage().chars().anyMatch(c -> Character.isLetterOrDigit(c)),
                "异常消息应含描述性文本: " + ex.getMessage());
    }

    @Test
    @DisplayName("根是文件（非目录）→ BusinessException")
    void writeEntry_rootIsFile_rejects() throws Exception {
        Path fileNotDir = tmp.resolve("a-file.txt");
        Files.writeString(fileNotDir, "not a directory");
        GitRepoRootResolver r = new GitRepoRootResolver(stub(fileNotDir));
        // 读取入口 requireUsableRoot 也对非目录拒绝
        assertThrows(BusinessException.class, r::resolveRepoRoot, "根指向普通文件必须拒");
    }

    @Test
    @DisplayName("读取入口：根不存在但父目录可读 → 延迟建仓允许返回（不抛 NPE）")
    void readEntry_missingRootButParentRunnable_allowsDeferredRepoCreation() throws Exception {
        Path missing = tmp.resolve("deferred-repo"); // 完全不存在，父 = tmp 且可读
        GitRepoRootResolver r = new GitRepoRootResolver(stub(missing));
        String root = r.resolveRepoRoot();
        assertNotNull(root, "延迟建仓：读取入口允许返回根路径");
        assertTrue(root.replace('\\', '/').startsWith(tmp.toString().replace('\\', '/')),
                "返回根应落在配置的延迟建仓路径下: " + root);
    }

    @Test
    @DisplayName("写入入口：真实存在目录 → 放行（反证：存在性校验不误杀合法根）")
    void writeEntry_existingDir_allowed() throws Exception {
        Path ok = tmp.resolve("exists");
        Files.createDirectories(ok);
        GitRepoRootResolver r = new GitRepoRootResolver(stub(ok));
        String root = r.resolveRepoRootOrThrow();
        assertTrue(Files.isDirectory(Path.of(root)), "真实存在目录应放行并返回可用根");
    }

    private static SysConfigService stub(Path root) {
        SysConfigService cfg = mock(SysConfigService.class);
        when(cfg.getString(GitRepoRootResolver.KEY_REPO_ROOT))
                .thenReturn(root.toString().replace('\\', '/'));
        return cfg;
    }
}
