package com.chinacreator.gzcm.runtime.access.git.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.access.git.GitRepoRegistry;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;

/**
 * Git 仓库注册表数据访问（#147C，runtime-access 属主落点）。
 *
 * <p>单源表 = {@code ecos_control.ecos_git_repo}（V300 建表 + 6 枚 repositoryId 种子）。
 * 纪律：SQL 全参数化（IR01/EN01，禁字面量拼接）；读腿一律 {@code is_deleted = 0}（#147B
 * 同族缺陷的防回潮：写逻辑删而读面不过滤 ⇒ 已删行恒可见）；删除只做逻辑删（IR03 只加不删）；
 * 主键 {@code id} 直接取 {@code repository_id} 字面语义键（MC01，禁库端/应用端随机 UUID——
 * 原 {@code GitRepositoryServiceImpl} 用 {@code UUID.randomUUID()} 补 id 即本条违例）。</p>
 *
 * <p>本 DAO 不解析 {@code localPath}：注册表按 DQ-2 口径只存 {@code repo_root}
 * （根下相对目录名），绝对路径由属主侧 {@code GitRepoPathResolver} 与 sys_config
 * 单源根拼接（见 {@link GitRepository#getLocalPath()} 字段口径）。</p>
 */
@Repository
public class GitRepoRegistryDao extends JdbcTemplateDaoSupport implements GitRepoRegistry {

    private static final String TABLE = "ecos_control.ecos_git_repo";

    private static final String SELECT_COLUMNS =
            "SELECT repository_id, repo_name, repo_root, branch_default, status, "
          + "       create_time, update_time, create_by, update_by ";

    private static final String ACTIVE_LIST_SQL =
            SELECT_COLUMNS + " FROM " + TABLE
          + " WHERE is_deleted = 0 AND status = ?"
          + " ORDER BY repository_id";

    private static final RowMapper<GitRepository> ROW_MAPPER = (rs, rowNum) -> mapRow(rs);

    public GitRepoRegistryDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 行 → 实体（包内可见，供本类 RowMapper 与门禁测试直测映射口径）。 */
    static GitRepository mapRow(ResultSet rs) throws SQLException {
        GitRepository repo = new GitRepository();
        repo.setRepositoryId(rs.getString("repository_id"));
        repo.setName(rs.getString("repo_name"));
        repo.setRepoRoot(rs.getString("repo_root"));
        repo.setDefaultBranch(rs.getString("branch_default"));
        repo.setEnabled(STATUS_ACTIVE.equals(rs.getString("status")));
        repo.setCreatedTime(toDate(rs.getTimestamp("create_time")));
        repo.setUpdatedTime(toDate(rs.getTimestamp("update_time")));
        repo.setCreatedBy(rs.getString("create_by"));
        repo.setUpdatedBy(rs.getString("update_by"));
        return repo;
    }

    private static Date toDate(Timestamp ts) {
        return ts == null ? null : new Date(ts.getTime());
    }

    @Override
    public GitRepository findByRepositoryId(String repositoryId) {
        List<GitRepository> rows = jdbcTemplate.query(
                SELECT_COLUMNS + " FROM " + TABLE + " WHERE repository_id = ? AND is_deleted = 0",
                ROW_MAPPER, repositoryId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public List<GitRepository> findActive() {
        return jdbcTemplate.query(ACTIVE_LIST_SQL, ROW_MAPPER, STATUS_ACTIVE);
    }

    @Override
    public int insert(GitRepository repository) {
        String id = repository.getRepositoryId();
        return jdbcTemplate.update(
                "INSERT INTO " + TABLE
              + " (id, repository_id, repo_name, repo_root, branch_default, status,"
              + "  create_by, update_by, create_time, update_time)"
              + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                id,
                id,
                repository.getName(),
                repository.getRepoRoot(),
                repository.getDefaultBranch(),
                repository.isEnabled() ? STATUS_ACTIVE : STATUS_DISABLED,
                repository.getCreatedBy(),
                repository.getUpdatedBy());
    }

    @Override
    public int softDelete(String repositoryId) {
        return jdbcTemplate.update(
                "UPDATE " + TABLE + " SET is_deleted = 1, update_time = NOW()"
              + " WHERE repository_id = ? AND is_deleted = 0",
                repositoryId);
    }
}
