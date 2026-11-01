package com.chinacreator.gzcm.runtime.access.git;

import java.util.List;

import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;

/**
 * Git 仓库注册表读面（#147C）。
 *
 * <p>单一事实源 = {@code ecos_control.ecos_git_repo}（V300 建表 + 6 枚 repositoryId 种子，
 * DQ-2 裁决定名；原 {@code td_git_repository} 作废）。本接口只声明属主读/写面，
 * 由 {@link com.chinacreator.gzcm.runtime.access.git.dao.GitRepoRegistryDao} 以 JdbcTemplate
 * 落地；<b>禁止</b>任何进程内 Map 冒充注册表（原实现的内存态在容器重启即丢、
 * 且全仓无写入方 ⇒ 生产侧恒空 = 归档链路恒 not_available 的 P0-F 根因）。</p>
 */
public interface GitRepoRegistry {

    /** 注册表 {@code status} 值域：启用（唯一读面放行值）。 */
    String STATUS_ACTIVE = "ACTIVE";

    /** 注册表 {@code status} 值域：停用（行仍在册，读面判 {@code enabled=false} 后由调用方拒绝）。 */
    String STATUS_DISABLED = "DISABLED";

    /** 按 repositoryId 取存量行（含禁用行，由调用方判 {@code enabled}）；未命中返回 null。 */
    GitRepository findByRepositoryId(String repositoryId);

    /** 全部 ACTIVE 且未逻辑删的行，按 repositoryId 升序（默认排序口径，禁 iterated keySet 随机序）。 */
    List<GitRepository> findActive();

    /** 新注册行落库（主键取 repositoryId 语义键；重复注册由属主表唯一索引兜住）。 */
    int insert(GitRepository repository);

    /** 逻辑删（{@code is_deleted = 1}），不做物理 DELETE。 */
    int softDelete(String repositoryId);
}
