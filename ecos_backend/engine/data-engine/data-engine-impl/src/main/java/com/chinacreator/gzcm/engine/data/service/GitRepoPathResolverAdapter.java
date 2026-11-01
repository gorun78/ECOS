package com.chinacreator.gzcm.engine.data.service;

import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.runtime.access.git.GitRepoPathResolver;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;

/**
 * {@link GitRepoPathResolver} 的属主侧装配（#147C 路径单源收口）。
 *
 * <p>仓库根的单一事实源是 {@link GitRepoRootResolver}（sys_config key
 * {@code ecos_git_repo_root} + 路径穿越/symlink 守卫）；runtime-access 只声明接缝、
 * 不自建第二套缺省根（架构铁律：Git 收敛 runtime，但配置读取必须经 sysman api 门面，
 * runtime 侧无该门面 ⇒ 由 data-engine 提供实现 Bean，与 {@code PipelineGitService}
 * 同一根解析器，杜绝「注册表 repo_root」与「消费方按命名约定拼路径」两套路径源并存）。</p>
 *
 * <p>解析规则（与 V300 注册表种子一致）：注册行 {@code repo_root} 存「根下相对目录名」
 * （如 {@code pipeline}、{@code ontology}），故 {@code dw-pipeline} 的工作树落
 * {@code {root}/pipeline} 而非 {@code {root}/dw-pipeline}。{@code repo_root} 为空时才按
 * {@code repositoryId} 兜底，且仍经同一守卫保证落在单源根内。任一解析失败由调用方
 * （runtime 门面）折叠为 {@code localPath=null} ⇒ 消费侧显式不可用，禁伪造路径。</p>
 */
@Component
public class GitRepoPathResolverAdapter implements GitRepoPathResolver {

    private final GitRepoRootResolver repoRootResolver;

    public GitRepoPathResolverAdapter(GitRepoRootResolver repoRootResolver) {
        this.repoRootResolver = repoRootResolver;
    }

    @Override
    public String resolveLocalPath(GitRepository repository) {
        if (repository == null) {
            return null;
        }
        String dir = firstNonBlank(repository.getRepoRoot(), repository.getRepositoryId());
        if (dir == null) {
            return null;
        }
        return repoRootResolver.resolveUnderRoot(dir);
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.trim().isEmpty()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.trim().isEmpty()) {
            return fallback.trim();
        }
        return null;
    }
}
