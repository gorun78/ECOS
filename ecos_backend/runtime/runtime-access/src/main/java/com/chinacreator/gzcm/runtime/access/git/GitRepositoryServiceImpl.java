package com.chinacreator.gzcm.runtime.access.git;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;

/**
 * Git 仓库注册门面实现（runtime-access 属主，#147C 持久化改造）。
 *
 * <p>三条硬口径（与原内存实现的差异即本批收口的违例）：</p>
 * <ol>
 *   <li><b>无内存态</b>：注册行一律经 {@link GitRepoRegistry} 读写
 *       {@code ecos_control.ecos_git_repo}。原 {@code ConcurrentMap} 实现重启即丢，
 *       且全仓从未有 {@code registerRepository} 写入方 ⇒ 生产侧恒空，
 *       ontology/data-engine 归档链路恒 {@code not_available}（P0-F 根因）。</li>
 *   <li><b>零伪造</b>：不再凭空造 {@code repositoryId="default" / localPath="/default/repo"}
 *       的假仓库；注册面未装配、注册行缺失/停用、读表故障时抛 {@link GitRepositoryException}
 *       （携 {@code httpStatus} 分态：404＝该仓库未注册/已停用，503＝注册面未装配或读表故障，
 *       400/409＝参数与调用歧义），由属主端点转真实状态行（§2.9.9 核心原则「未装配/未注册 ⇒
 *       显式拒绝」，与 #142/B4-3e-52/53 同族判据）。
 *       多枚启用行时<b>不猜</b>默认仓库，要求调用方显式传 repositoryId。</li>
 *   <li><b>路径单源</b>：{@code localPath} 只由属主解析接缝 {@link GitRepoPathResolver}
 *       （根 = sys_config {@code ecos_git_repo_root}）拼接得出；解析面未装配 ⇒ 保持 null，
 *       由消费方判空后显式拒绝。禁止消费方各自按命名约定推导（原双套路径源）。</li>
 * </ol>
 *
 * <p>装配口径：生产构造器唯一带 {@code @Autowired}（多构造器场景必须显式指定，
 * 防 #142/B4-3e-75 同型的注入歧义回归）；另一枚仅用于测试/非 Spring 场景的显式装配。</p>
 */
@Service
public class GitRepositoryServiceImpl implements GitRepositoryService {

    private static final Logger log = LoggerFactory.getLogger(GitRepositoryServiceImpl.class);

    private final GitRepoRegistry registry;

    /** 可为 null：解析面未装配时 localPath 一律保持 null（显式拒绝，不伪造路径）。 */
    private final GitRepoPathResolver pathResolver;

    @Autowired
    public GitRepositoryServiceImpl(GitRepoRegistry registry,
                                    ObjectProvider<GitRepoPathResolver> pathResolverProvider) {
        this(registry, pathResolverProvider != null ? pathResolverProvider.getIfAvailable() : null);
    }

    /** 显式装配接缝（测试 / 非 Spring 场景）；{@code registry}/{@code pathResolver} 传 null 即该面缺位。 */
    public GitRepositoryServiceImpl(GitRepoRegistry registry, GitRepoPathResolver pathResolver) {
        this.registry = registry;
        this.pathResolver = pathResolver;
        if (registry == null) {
            log.warn("GitRepoRegistry 未装配：注册表读面按调用点显式拒绝（GitRepositoryException），"
                    + "不在构造期熔断整个应用上下文，也不以内存 Map 或空注册面冒充");
        }
        if (pathResolver == null) {
            log.warn("GitRepoPathResolver 未装配：注册行 localPath 将为 null，"
                    + "Git 归档/读面按显式不可用处理（仓库根单源=sys_config ecos_git_repo_root）");
        }
    }

    @Override
    public GitRepository getDefaultRepository() throws GitRepositoryException {
        List<GitRepository> active = activeRows();
        if (active.isEmpty()) {
            throw new GitRepositoryException(
                    "仓库注册表未登记任何启用行（ecos_control.ecos_git_repo 为空或全部停用）："
                            + "请先注册 repositoryId，禁止以内存构造的 default 仓库冒充已注册");
        }
        if (active.size() > 1) {
            List<String> ids = new ArrayList<>();
            for (GitRepository repo : active) {
                ids.add(repo.getRepositoryId());
            }
            throw new GitRepositoryException(
                    "注册表存在 " + active.size() + " 枚启用仓库 " + ids
                            + "，无法在不猜的前提下取默认仓库；请显式传 repositoryId", 400);
        }
        return resolvePath(active.get(0));
    }

    @Override
    public GitRepository getRepositoryById(String repositoryId) throws GitRepositoryException {
        if (repositoryId == null || repositoryId.trim().isEmpty()) {
            throw new GitRepositoryException("repositoryId 不能为空（仓库定位只认注册表语义键）", 400);
        }
        String id = repositoryId.trim();
        GitRepository row = requireRegistry().findByRepositoryId(id);
        if (row == null) {
            throw new GitRepositoryException(
                    "Repository with ID " + id + " not found（未在 ecos_control.ecos_git_repo 注册）", 404);
        }
        if (!row.isEnabled()) {
            throw new GitRepositoryException("仓库 " + id + " 已停用（注册表 status != ACTIVE）", 404);
        }
        return resolvePath(row);
    }

    @Override
    public List<GitRepository> listRepositories() throws GitRepositoryException {
        List<GitRepository> rows = activeRows();
        List<GitRepository> out = new ArrayList<>(rows.size());
        for (GitRepository row : rows) {
            out.add(resolvePath(row));
        }
        return out;
    }

    @Override
    public void registerRepository(GitRepository repository) throws GitRepositoryException {
        if (repository == null) {
            throw new GitRepositoryException("Repository cannot be null", 400);
        }
        String id = repository.getRepositoryId();
        if (id == null || id.trim().isEmpty()) {
            throw new GitRepositoryException(
                    "repositoryId 必填：注册表主键是语义键（MC01），禁止以随机 UUID 补位", 400);
        }
        id = id.trim();
        repository.setRepositoryId(id);
        if (repository.getName() == null || repository.getName().trim().isEmpty()) {
            repository.setName(id);
        }
        if (requireRegistry().findByRepositoryId(id) != null) {
            throw new GitRepositoryException("Repository with ID " + id + " already exists", 409);
        }
        requireRegistry().insert(repository);
        resolvePath(repository);
        log.info("Git 仓库已注册: repositoryId={}, repoRoot={}", id, repository.getRepoRoot());
    }

    @Override
    public void unregisterRepository(String repositoryId) throws GitRepositoryException {
        if (repositoryId == null || repositoryId.trim().isEmpty()) {
            throw new GitRepositoryException("repositoryId 不能为空", 400);
        }
        String id = repositoryId.trim();
        if (requireRegistry().findByRepositoryId(id) == null) {
            throw new GitRepositoryException("Repository with ID " + id + " not found", 404);
        }
        int updated = requireRegistry().softDelete(id);
        if (updated == 0) {
            throw new GitRepositoryException("仓库 " + id + " 逻辑删未生效（并发注销或读写字段不匹配）");
        }
        log.info("Git 仓库已注销（逻辑删）: repositoryId={}", id);
    }

    private List<GitRepository> activeRows() throws GitRepositoryException {
        try {
            return requireRegistry().findActive();
        } catch (RuntimeException e) {
            // 注册表读失败绝不折叠成「没有仓库」——库故障与未注册是两个分态（禁伪不可用/禁伪成功）
            throw new GitRepositoryException("读取仓库注册表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 注册面装配守卫（§2.9.9「未装配 ⇒ 显式拒绝」的调用点承载）。
     *
     * <p><b>为什么不在构造期熔断</b>：#147C 首版在此 {@code throw new IllegalStateException}，
     * 于是本腿被 D-B4-71 承流门按「无 advice／无承载出口／非死面」默认记成四档残差＝渲染 500，
     * 而它实际根本不上 HTTP 入口面（装配期抛＝整个应用上下文起不来）。这正是 D-B4-72 定性的
     * 错账形态。改为运行期显式拒绝后，「未装配」在链上表现为 {@link GitRepositoryException}，
     * 由属主端点 {@code GitController} 落 503＋机读码（不可用 ≠ 无权限 ≠ 内部错误），
     * 读得到、也起得来其余功能。</p>
     */
    private GitRepoRegistry requireRegistry() throws GitRepositoryException {
        GitRepoRegistry dao = registry;
        if (dao == null) {
            throw new GitRepositoryException(
                    "GitRepoRegistry 未装配：仓库注册表必须持久化于 ecos_control.ecos_git_repo，"
                            + "禁止以内存 Map 或空注册面替代（按显式不可用处理）");
        }
        return dao;
    }

    /** 属主侧解析 localPath：解析面缺位或解析不出 ⇒ 保持 null（调用方判空后显式拒绝）。 */
    private GitRepository resolvePath(GitRepository repository) {
        if (repository == null) {
            return null;
        }
        if (pathResolver == null) {
            return repository;
        }
        try {
            repository.setLocalPath(pathResolver.resolveLocalPath(repository));
        } catch (RuntimeException e) {
            log.warn("仓库 localPath 解析失败，按未解析处理: repositoryId={}, err={}",
                    repository.getRepositoryId(), e.getMessage());
            repository.setLocalPath(null);
        }
        return repository;
    }
}
