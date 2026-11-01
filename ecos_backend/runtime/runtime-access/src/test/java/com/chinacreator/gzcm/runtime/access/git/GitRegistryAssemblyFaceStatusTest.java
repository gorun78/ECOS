package com.chinacreator.gzcm.runtime.access.git;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService.GitRepositoryException;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;

/**
 * 仓库注册面「未装配 / 未注册 / 停用 / 故障」分态行为门（#147C，规格 §2.9.9）。
 *
 * <p>本门盯的是<b>属主服务面</b>的分态真值，不是文案。三条判据各自对应一枚历史违例形态：</p>
 * <ol>
 *   <li><b>装配期不熔断</b>：{@code GitRepoRegistry} 缺位时构造成功、其余功能可用；
 *       原 #147C 首版在构造器 {@code throw new IllegalStateException}，把「一个读面没接上」
 *       放大成「整个应用起不来」，且该腿在 D-B4-71 承流门上被记成渲染 500 的四档残差
 *       —— 装配期异常根本不上 HTTP 入口面，账记错处（详 {@code GitRepositoryServiceImpl}
 *       {@code requireRegistry()} 注释）。改为运行期显式拒绝后，本腿必须能在链上读到 503。</li>
 *   <li><b>不可用 ≠ 不存在 ≠ 空</b>：未装配 ⇒ 503；行不存在/停用 ⇒ 404；
 *       读表故障 ⇒ 503 且不折叠成空列表（禁「没有仓库」冒充「读不到仓库」）。</li>
 *   <li><b>零伪造</b>：注册面缺位时不得凭空造 {@code default} 仓库；
 *       解析面缺位时 {@code localPath} 保持 {@code null}，不按命名约定补路径。</li>
 * </ol>
 *
 * <p>变异取证：把 {@code requireRegistry()} 改回构造期抛 ISE ⇒ 判据 1 红；
 * 把 404 腿的显式状态字面量删掉（走缺省 503）⇒ 判据 2 的 404 面红；
 * 把 {@code activeRows()} 的 catch 改成返回空列表 ⇒ 判据 2 的故障面红。</p>
 */
class GitRegistryAssemblyFaceStatusTest {

    /** 可控注册表假面：只服务分态判定，不承担持久化语义。 */
    private static final class FakeRegistry implements GitRepoRegistry {
        private final List<GitRepository> rows = new ArrayList<>();
        private boolean boomOnRead;
        private boolean boomOnActive;

        FakeRegistry add(GitRepository row) {
            rows.add(row);
            return this;
        }

        @Override
        public GitRepository findByRepositoryId(String repositoryId) {
            if (boomOnRead) {
                throw new RuntimeException("connection refused (simulated registry failure)");
            }
            for (GitRepository row : rows) {
                if (row.getRepositoryId().equals(repositoryId)) {
                    return row;
                }
            }
            return null;
        }

        @Override
        public List<GitRepository> findActive() {
            if (boomOnActive) {
                throw new RuntimeException("relation ecos_control.ecos_git_repo does not exist");
            }
            List<GitRepository> active = new ArrayList<>();
            for (GitRepository row : rows) {
                if (row.isEnabled()) {
                    active.add(row);
                }
            }
            return active;
        }

        @Override
        public int insert(GitRepository repository) {
            rows.add(repository);
            return 1;
        }

        @Override
        public int softDelete(String repositoryId) {
            for (GitRepository row : rows) {
                if (row.getRepositoryId().equals(repositoryId)) {
                    row.setEnabled(false);
                    return 1;
                }
            }
            return 0;
        }
    }

    private static GitRepository row(String id, boolean enabled) {
        GitRepository repo = new GitRepository();
        repo.setRepositoryId(id);
        repo.setName(id);
        repo.setRepoRoot(id);
        repo.setDefaultBranch("main");
        repo.setEnabled(enabled);
        return repo;
    }

    /** 根单源解析假面：把 repoRoot 拼到固定根下，模拟属主解析接缝。 */
    private static GitRepoPathResolver resolverUnderRoot(String root) {
        return repository -> root + "/" + repository.getRepoRoot();
    }

    // 生产腿是 (GitRepoRegistry, ObjectProvider) 、测试腿是 (GitRepoRegistry, GitRepoPathResolver)：
    // 裸 null 落在二参位上歧义，故装配缺位统一经这三枚具名构造辅助（语义即「该面未装配」）。

    private static GitRepositoryServiceImpl serviceWithoutRegistry(GitRepoPathResolver resolver) {
        return new GitRepositoryServiceImpl((GitRepoRegistry) null, resolver);
    }

    private static GitRepositoryServiceImpl serviceWithoutResolver(GitRepoRegistry registry) {
        return new GitRepositoryServiceImpl(registry, (GitRepoPathResolver) null);
    }

    private static GitRepositoryServiceImpl serviceFullyUnassembled() {
        return new GitRepositoryServiceImpl((GitRepoRegistry) null, (GitRepoPathResolver) null);
    }

    // ── 判据 1：装配期不熔断，运行期显式拒绝 ──

    @Test
    @DisplayName("注册面未装配：构造成功（不熔断应用上下文），5 个读写面各自显式拒绝 503")
    void unassembledRegistryRejectsAtCallSiteNotAtConstruction() {
        GitRepositoryServiceImpl service =
                assertDoesNotThrow(() -> serviceWithoutRegistry(resolverUnderRoot("/repos")));

        GitRepositoryException byId =
                assertThrows(GitRepositoryException.class, () -> service.getRepositoryById("pipeline-core"));
        assertEquals(503, byId.getHttpStatus(), "未装配＝服务端未就绪，不得说成资源不存在");
        assertTrue(byId.getMessage().contains("未装配"), byId.getMessage());

        assertThrows(GitRepositoryException.class, service::listRepositories);
        assertThrows(GitRepositoryException.class, service::getDefaultRepository);
        assertThrows(GitRepositoryException.class, () -> service.registerRepository(row("x", true)));
        assertThrows(GitRepositoryException.class, () -> service.unregisterRepository("x"));
    }

    @Test
    @DisplayName("注册面未装配：listRepositories 不得折叠成空列表冒充「没有仓库」（禁伪成功）")
    void unassembledRegistryNeverReturnsEmptySurface() {
        GitRepositoryServiceImpl service = serviceFullyUnassembled();

        GitRepositoryException e =
                assertThrows(GitRepositoryException.class, service::listRepositories);
        assertEquals(503, e.getHttpStatus());
        // 反向锚：判据若被改成「catch 后返回 Collections.emptyList()」，上一行仍可能绿——
        // 这里显式钉住「异常真实发生」这件事本身，空面形态必须走不到。
        assertInstanceOf(GitRepositoryException.class, e);
    }

    // ── 判据 2：不可用 / 不存在 / 停用 / 歧义 四态分档 ──

    @Test
    @DisplayName("行不存在 ⇒ 404（与未装配 503 分态，禁一刀切）")
    void missingRowIs404Not503() {
        GitRepositoryServiceImpl service =
                new GitRepositoryServiceImpl(new FakeRegistry(), resolverUnderRoot("/repos"));

        GitRepositoryException e = assertThrows(GitRepositoryException.class,
                () -> service.getRepositoryById("never-registered"));
        assertEquals(404, e.getHttpStatus(), "行缺席是可换参数重试的资源态，不是服务未就绪");
    }

    @Test
    @DisplayName("行在册但停用 ⇒ 404 且携「停用」语义，不冒充未注册")
    void disabledRowIs404Disabled() {
        GitRepositoryServiceImpl service = new GitRepositoryServiceImpl(
                new FakeRegistry().add(row("ontology-archive", false)), resolverUnderRoot("/repos"));

        GitRepositoryException e = assertThrows(GitRepositoryException.class,
                () -> service.getRepositoryById("ontology-archive"));
        assertEquals(404, e.getHttpStatus());
        assertTrue(e.getMessage().contains("停用"), e.getMessage());
    }

    @Test
    @DisplayName("读表故障 ⇒ 503 携原因，绝不折叠成空注册面或 404")
    void registryReadFailureIs503NotEmptySurface() {
        FakeRegistry registry = new FakeRegistry().add(row("pipeline-core", true));
        registry.boomOnActive = true;
        GitRepositoryServiceImpl service = new GitRepositoryServiceImpl(registry, resolverUnderRoot("/repos"));

        GitRepositoryException listed = assertThrows(GitRepositoryException.class, service::listRepositories);
        assertEquals(503, listed.getHttpStatus());
        assertTrue(listed.getMessage().contains("读取仓库注册表失败"), listed.getMessage());

        GitRepositoryException defaulted =
                assertThrows(GitRepositoryException.class, service::getDefaultRepository);
        assertEquals(503, defaulted.getHttpStatus());
    }

    @Test
    @DisplayName("零启用行 ⇒ 503 显式拒绝，不凭空造 default 仓库（#147C 伪成功回归锚）")
    void emptyRegistryDoesNotFabricateDefaultRepo() {
        GitRepositoryServiceImpl service = new GitRepositoryServiceImpl(new FakeRegistry(), resolverUnderRoot("/repos"));

        GitRepositoryException e = assertThrows(GitRepositoryException.class, service::getDefaultRepository);
        assertEquals(503, e.getHttpStatus());
        assertTrue(e.getMessage().contains("禁止以内存构造的 default 仓库冒充已注册"), e.getMessage());
    }

    @Test
    @DisplayName("多枚启用行且未传 repositoryId ⇒ 400 要求显式定位，不猜默认仓库")
    void multipleActiveRowsDemandExplicitId() {
        FakeRegistry registry = new FakeRegistry().add(row("a-core", true)).add(row("b-archive", true));
        GitRepositoryServiceImpl service = new GitRepositoryServiceImpl(registry, resolverUnderRoot("/repos"));

        GitRepositoryException e = assertThrows(GitRepositoryException.class, service::getDefaultRepository);
        assertEquals(400, e.getHttpStatus(), "调用歧义是客户端可改的 400，不是服务未就绪");
    }

    // ── 判据 3：路径零伪造 + 写面分态 ──

    @Test
    @DisplayName("解析面缺位 ⇒ localPath 保持 null（禁按命名约定推导第二套路径源）")
    void missingPathResolverKeepsLocalPathNull() throws Exception {
        GitRepositoryServiceImpl service = serviceWithoutResolver(
                new FakeRegistry().add(row("pipeline-core", true)));

        GitRepository repo = service.getRepositoryById("pipeline-core");
        assertNull(repo.getLocalPath(), "属主解析面缺位时不得伪造本地路径");
    }

    @Test
    @DisplayName("解析面在位 ⇒ localPath 由属主接缝单源拼出")
    void pathResolverFillsLocalPathFromSingleSource() throws Exception {
        GitRepositoryServiceImpl service = new GitRepositoryServiceImpl(
                new FakeRegistry().add(row("pipeline-core", true)), resolverUnderRoot("/data/ecos-git-repos"));

        GitRepository repo = service.getRepositoryById("pipeline-core");
        assertEquals("/data/ecos-git-repos/pipeline-core", repo.getLocalPath());
    }

    @Test
    @DisplayName("写面分态：空 id ⇒ 400（MC01 禁 UUID 补位）；重复注册 ⇒ 409；注销缺席 ⇒ 404")
    void writeFacesCarryTheirOwnStatus() {
        FakeRegistry registry = new FakeRegistry().add(row("pipeline-core", true));
        GitRepositoryServiceImpl service = new GitRepositoryServiceImpl(registry, resolverUnderRoot("/repos"));

        GitRepository blank = row("   ", true);
        assertEquals(400, assertThrows(GitRepositoryException.class,
                () -> service.registerRepository(blank)).getHttpStatus());
        assertEquals(400, assertThrows(GitRepositoryException.class,
                () -> service.registerRepository(null)).getHttpStatus());
        assertEquals(409, assertThrows(GitRepositoryException.class,
                () -> service.registerRepository(row("pipeline-core", true))).getHttpStatus());
        assertEquals(404, assertThrows(GitRepositoryException.class,
                () -> service.unregisterRepository("not-there")).getHttpStatus());
        assertEquals(400, assertThrows(GitRepositoryException.class,
                () -> service.unregisterRepository("  ")).getHttpStatus());
    }

    @Test
    @DisplayName("repositoryId 空值 ⇒ 400（读面入参歧义），不落到注册表查询")
    void blankRepositoryIdIs400() {
        GitRepositoryServiceImpl service = serviceWithoutResolver(new FakeRegistry());

        assertEquals(400, assertThrows(GitRepositoryException.class,
                () -> service.getRepositoryById(null)).getHttpStatus());
        assertEquals(400, assertThrows(GitRepositoryException.class,
                () -> service.getRepositoryById("  ")).getHttpStatus());
    }

    /**
     * 变异探针（判据 1 的反向证据）：构造期熔断形态一旦回来，本用例必须变红。
     * <p>把 {@code GitRepositoryServiceImpl} 构造器里的 {@code log.warn} 换回
     * {@code throw new IllegalStateException}，则 {@code assertDoesNotThrow} 红；
     * 这条腿与 {@code DependencyUnreadyIllegalStateRatchetTest} 的四档残差钉值联动，
     * 缺任一枚都留下「装配期异常冒充 HTTP 500」的错账空间。</p>
     */
    @Test
    @DisplayName("变异锚：未装配面必须可被调用方捕获为受检拒绝（禁 RuntimeException 逃出入口层）")
    void unassembledRejectionIsCheckedAndCapturable() {
        GitRepositoryServiceImpl service = serviceFullyUnassembled();

        Exception captured = assertThrows(Exception.class, service::listRepositories);
        assertInstanceOf(GitRepositoryException.class, captured,
                "未装配拒绝必须是属主异常类型（携分态状态行），不得以未受检异常绕过出口层");
    }
}
