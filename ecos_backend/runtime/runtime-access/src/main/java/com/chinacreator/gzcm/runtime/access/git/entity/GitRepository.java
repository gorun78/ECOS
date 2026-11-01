package com.chinacreator.gzcm.runtime.access.git.entity;

import java.util.Date;

/**
 * Git 仓库注册行（runtime 统一底座 · 横切「器」层）。
 * <p>
 * 单一事实源 = {@code ecos_control.ecos_git_repo}（V300 建表 + repositoryId 种子；
 * 补充设计册 §2.9.5①/DQ-2 裁决定名，原 {@code td_git_repository} 命名作废）。
 * 供 data-engine Pipeline 归档 / ontology 本体资产归档 / kb 知识资产归档共用，
 * 各域只是信息组织形式不同，仓库定位一律只认 {@code repositoryId}。
 * <p>
 * 字段口径（#147C 订正，注释乱码勘定 + 单源对齐）：
 * <ul>
 *   <li>{@code repoRoot} = 仓库根<b>下的相对目录名</b>（注册表列 {@code repo_root}）；
 *       绝对仓库根仍是 sys_config {@code ecos_git_repo_root} 单源，由属主侧
 *       {@link com.chinacreator.gzcm.runtime.access.git.GitRepoPathResolver} 拼接解析；</li>
 *   <li>{@code localPath} 是<b>解析产物</b>而非登记事实：只由注册表属主侧填充
 *       （见 {@link GitRepositoryServiceImpl}），消费方不得自行按命名约定推导，
 *       解析不出即为 null（调用方显式拒绝，禁伪造路径）；</li>
 *   <li>{@code remoteUrl} 无注册表列，注册面不承载远程地址与任何凭据
 *       （DI-1：客户端只传 repositoryId，禁 localPath / 凭据入参）。</li>
 * </ul>
 *
 * @author CDRC Runtime Team
 */
public class GitRepository {

    /** 注册标识（= 注册表主键与 {@code repository_id} 字面值，语义键非随机 UUID） */
    private String repositoryId;

    /** 仓库显示名 */
    private String name;

    /** 解析后的本地绝对路径（属主侧填充，未解析为 null） */
    private String localPath;

    /** 远程地址（注册表不承载，恒 null；仅 clone 等显式入参场景使用） */
    private String remoteUrl;

    /** 仓库根下相对目录名（注册表 {@code repo_root} 列） */
    private String repoRoot;

    /** 默认分支（如 main） */
    private String defaultBranch;

    /** 是否启用（注册表 {@code status = 'ACTIVE'}） */
    private boolean enabled;

    /** 纳入清单（V304 新增，逗号分隔相对路径前缀；非空时仅允许命中前缀，空=全量） */
    private java.util.List<String> includePaths = java.util.List.of();

    /** 排除清单（V304 新增；命中即拒，优先级高于纳入） */
    private java.util.List<String> excludePaths = java.util.List.of();

    /** 创建时间 */
    private Date createdTime;

    /** 创建人 */
    private String createdBy;

    /** 最近更新时间 */
    private Date updatedTime;

    /** 最近更新人 */
    private String updatedBy;

    public String getRepositoryId() {
        return repositoryId;
    }

    public void setRepositoryId(String repositoryId) {
        this.repositoryId = repositoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public String getRemoteUrl() {
        return remoteUrl;
    }

    public void setRemoteUrl(String remoteUrl) {
        this.remoteUrl = remoteUrl;
    }

    public String getRepoRoot() {
        return repoRoot;
    }

    public void setRepoRoot(String repoRoot) {
        this.repoRoot = repoRoot;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Date getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Date createdTime) {
        this.createdTime = createdTime;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Date getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(Date updatedTime) {
        this.updatedTime = updatedTime;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public java.util.List<String> getIncludePaths() {
        return includePaths;
    }

    public void setIncludePaths(java.util.List<String> includePaths) {
        this.includePaths = includePaths == null ? java.util.List.of() : java.util.List.copyOf(includePaths);
    }

    public java.util.List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(java.util.List<String> excludePaths) {
        this.excludePaths = excludePaths == null ? java.util.List.of() : java.util.List.copyOf(excludePaths);
    }

    /**
     * 路径是否允许纳入归档（§2.9.5③；V304）。
     *
     * <p>判据：命中 {@code excludePaths} 任前缀 ⇒ 拒；{@code includePaths} 非空且不命中任前缀 ⇒ 拒；
     * 其余 ⇒ 允许。排除优先于纳入——敏感目录在 include 之外仍可被 exclude 明确拒绝。</p>
     */
    public boolean allows(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return false;
        }
        String p = relativePath.replace('\\', '/');
        for (String ex : excludePaths) {
            if (p.startsWith(ex)) {
                return false;
            }
        }
        if (includePaths.isEmpty()) {
            return true;
        }
        for (String in : includePaths) {
            if (p.startsWith(in)) {
                return true;
            }
        }
        return false;
    }

    /** 逗号分隔串 → 列表（去空白段；空串/纯空白 ⇒ 空列表）。 */
    public static java.util.List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
