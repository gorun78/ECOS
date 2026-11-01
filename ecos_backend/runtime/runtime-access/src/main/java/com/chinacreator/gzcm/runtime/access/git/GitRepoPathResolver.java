package com.chinacreator.gzcm.runtime.access.git;

import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;

/**
 * 仓库本地路径组装接缝（#147C）。
 *
 * <p>根路径的单一事实源仍是 sys_config key {@code ecos_git_repo_root}（由
 * data-engine {@code GitRepoRootResolver} 承载读取 + 路径穿越守卫），运行时底座
 * <b>不</b>自建第二套缺省根；故本接缝由属主侧实现（data-engine 提供 Bean），
 * runtime-access 只按注册表行的 {@code repo_root}（根下相对目录）索取绝对路径。</p>
 *
 * <p>未装配该接缝时返回 {@code null} ⇒ 调用方得到「缺少 localPath」显式不可用态，
 * <b>禁止</b>回退到进程内伪造路径（禁伪成功口径，§2.9.9 / C-4）。</p>
 */
@FunctionalInterface
public interface GitRepoPathResolver {

    /**
     * 注册表行 → 仓库绝对本地路径。
     *
     * @param repository 注册表行（读 {@code repoRoot} 相对目录；{@code repoRoot} 为空时
     *                   实现方须按 {@code repositoryId} 兜底并保证仍落在单源根内）
     * @return 绝对路径；无法解析时返回 {@code null}（调用方显式拒绝，不伪造）
     */
    String resolveLocalPath(GitRepository repository);
}
