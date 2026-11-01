package com.chinacreator.gzcm.runtime.access.git;

import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 最后一跳的仓库路径解析（#147E；仅在属主适配 Bean 缺席时由门面内部使用）。
 *
 * <p><b>为什么需要</b>：{@link GitRepoPathResolver} 的生产实现挂在 data-engine
 * （读 sysman 配置门面的 {@code ecos_git_repo_root}），只有扫到 {@code engine.data.service}
 * 的宿主才有该 Bean。kb/cognitive/ai 三个服务宿主不扫该包 ⇒ 注册行 {@code localPath} 恒 null，
 * 归档腿被如实判成 {@code NOT_AVAILABLE}——「注册了却永远不工作」，与「全域接入」相反。</p>
 *
 * <p><b>口径（不让它变成第二套路径源）</b>：</p>
 * <ol>
 *   <li>属主适配 Bean 在场时永不使用本类（门面 ObjectProvider 优先）；</li>
 *   <li>根按 sys_config 同一默认值 {@code {user.home}/ecos-git-repos}，并允许运维用
 *       {@code -Decos.git.repo.root} / {@code ECOS_GIT_REPO_ROOT} 覆盖——仅当配置门面
 *       不可用时的部署兜底，不与 sys_config 抢"单一事实源"地位；</li>
 *   <li>逐段 path-traversal 守卫 + 归一化后必须仍在根内（与 {@code GitRepoRootResolver} 同口径）。</li>
 * </ol>
 *
 * @author ECOS Runtime底座 #147E
 */
public final class DefaultGitRepoPathResolver implements GitRepoPathResolver {

    private static final Logger log = LoggerFactory.getLogger(DefaultGitRepoPathResolver.class);

    /** 与 GitRepoRootResolver 相同的默认根（{@code {user.home}/ecos-git-repos}）。 */
    public static final String PROP_REPO_ROOT = "ecos.git.repo.root";
    public static final String ENV_REPO_ROOT = "ECOS_GIT_REPO_ROOT";

    @Override
    public String resolveLocalPath(GitRepository repository) {
        if (repository == null) {
            return null;
        }
        String dir = firstNonBlank(repository.getRepoRoot(), repository.getRepositoryId());
        if (dir == null) {
            return null;
        }
        String root = repoRoot();
        try {
            Path base = Paths.get(root).toAbsolutePath().normalize();
            requireSafeSegment(dir);
            Path target = base.resolve(dir).normalize();
            if (!target.startsWith(base)) {
                log.warn("仓库相对目录越出根范围，拒绝解析: repoRoot={} root={}", dir, root);
                return null;
            }
            return target.toString();
        } catch (IllegalArgumentException e) {
            // InvalidPathException 是 IllegalArgumentException 子类，一并覆盖
            log.warn("仓库路径解析失败: repoRoot={} err={}", dir, e.getMessage());
            return null;
        }
    }

    /** 根解析：系统属性 → 环境变量 → 与 GitRepoRootResolver 一致的默认值。 */
    public static String repoRoot() {
        String prop = trimToNull(System.getProperty(PROP_REPO_ROOT));
        if (prop != null) {
            return prop;
        }
        String env = trimToNull(System.getenv(ENV_REPO_ROOT));
        if (env != null) {
            return env;
        }
        return Paths.get(System.getProperty("user.home"), "ecos-git-repos").toString();
    }

    private static void requireSafeSegment(String segment) {
        if (segment.contains("..") || segment.contains("/") || segment.contains("\\")
                || segment.indexOf(':') >= 0) {
            throw new IllegalArgumentException("非法仓库目录段: " + segment);
        }
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

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
