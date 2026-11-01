package com.chinacreator.gzcm.runtime.access.git;

import java.util.List;

import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;

/**
 * Runtime 统一底座 · Git 仓库注册门面（横切「器」层，架构铁律 §2.5 基础设施收敛 runtime-access）。
 *
 * <p>仓库定位的<b>单一事实源</b>：注册行持久化在 {@code ecos_control.ecos_git_repo}
 * （属主读面 {@link GitRepoRegistry}），仓库根路径持久化在 sys_config
 * {@code ecos_git_repo_root}（属主解析面 {@link GitRepoPathResolver}）。
 * 任何消费方（data-engine Pipeline / ontology 资产归档 / datanet Git API）
 * 只允许传 {@code repositoryId} 定位仓库，禁止自建第二套注册表或按命名约定推导路径（DI-1/DS-3）。</p>
 *
 * <p>#147C 订正（原注释为 GBK 乱码，一并勘定）：本门面<b>不再</b>提供任何进程内内存态——
 * 原实现以 {@code ConcurrentMap} 冒充注册表、且全仓无写入方，导致生产侧恒空
 * （归档链路恒 {@code not_available} 的 P0-F 根因）。</p>
 */
public interface GitRepositoryService {

    /**
     * 取唯一启用的注册行（fail-closed 语义，#147C 订正）。
     *
     * <p>原实现在零注册时会凭空造出 {@code repositoryId="default" / localPath="/default/repo"}
     * 的假仓库（伪成功）。现改为：零枚启用行 ⇒ 显式抛
     * {@link GitRepositoryException}；多枚启用行且未指定 repositoryId ⇒ 同样抛出并指引调用方
     * 显式传 repositoryId，<b>不猜</b>默认仓库。</p>
     */
    GitRepository getDefaultRepository() throws GitRepositoryException;

    /** 按 repositoryId 取注册行；未注册 ⇒ 显式抛 {@link GitRepositoryException}（禁兜底推导路径）。 */
    GitRepository getRepositoryById(String repositoryId) throws GitRepositoryException;

    /** 列出全部启用且未逻辑删的注册行（按 repositoryId 升序）。 */
    List<GitRepository> listRepositories() throws GitRepositoryException;

    /**
     * 注册仓库（落库）。
     *
     * <p>#147C 订正：{@code repositoryId} 是语义键、必填——原实现缺失时用
     * {@code UUID.randomUUID()} 补位（MC01 违例），使「注册了但没人知道 id」成为常态。</p>
     */
    void registerRepository(GitRepository repository) throws GitRepositoryException;

    /** 注销仓库：注册表逻辑删（{@code is_deleted = 1}），禁物理 DELETE（IR03）。 */
    void unregisterRepository(String repositoryId) throws GitRepositoryException;

    /**
     * 注册面显式拒绝（§2.9.9「未装配/未注册 ⇒ 显式拒绝」的载体）。
     *
     * <p>#147C 补 {@code httpStatus} 承载位：一条异常类型同时承载「该仓库未注册（404）」
     * 「注册面未装配／库故障（503）」「参数或调用歧义（400）」三种分态，出口层若只能看到
     * 类型就只能一刀切一个状态行——把不可用说成不存在（或反向）正是 D-B4-59／D-B4-73 族
     * 定性过的错账形态。缺省取 {@code 503}（最保守读法：拒绝服务而不是谎报资源缺失），
     * 资源级缺席腿显式传 404。属主端点据此推导状态行，禁在出口层再写第二处状态字面量。</p>
     */
    class GitRepositoryException extends Exception {
        private static final long serialVersionUID = 1L;

        /** 缺省分态＝不可用（未装配/注册表故障/未登记任何启用行）。 */
        private static final int DEFAULT_HTTP_STATUS = 503;

        private final int httpStatus;

        public GitRepositoryException(String message) {
            this(message, DEFAULT_HTTP_STATUS);
        }

        public GitRepositoryException(String message, int httpStatus) {
            super(message);
            this.httpStatus = httpStatus;
        }

        public GitRepositoryException(String message, Throwable cause) {
            this(message, DEFAULT_HTTP_STATUS, cause);
        }

        public GitRepositoryException(String message, int httpStatus, Throwable cause) {
            super(message, cause);
            this.httpStatus = httpStatus;
        }

        /** 出口层唯一状态行来源（404＝该仓库未注册/已停用；503＝注册面未装配或读表故障）。 */
        public int getHttpStatus() {
            return httpStatus;
        }
    }
}
