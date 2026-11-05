package com.chinacreator.gzcm.runtime.access.git;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.access.git.entity.GitCommitView;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;

/**
 * Runtime 统一 Git 服务接口。
 * <p>
 * 全系统（sysman 配置同步 / data-engine Pipeline / cognitive 知识图谱 / aimod AI 场景）
 * 均通过此接口操作 Git，仅信息组织形式不同。
 * <p>
 * 仓库定位一律走 {@link GitRepository}（注册表单源，见 {@link GitRepositoryService}）；
 * 以 {@code localPath} 为入参的扩展方法是 pipeline 工作树（尚未注册为仓库）场景，
 * <b>禁止</b>用于面向用户的仓库版本读面（DI-1）。
 */
public interface GitService {

    // ── Runtime 原有方法 ──

    String commitAllChanges(GitRepository repository, String commitMessage,
                            String authorName, String authorEmail) throws GitException;

    void pull(GitRepository repository, String remote, String branch) throws GitException;

    String createTag(GitRepository repository, String tagName, String message) throws GitException;

    String getCurrentCommitId(GitRepository repository) throws GitException;

    Map<String, Object> diff(GitRepository repository, String fromRef, String toRef) throws GitException;

    /**
     * 读仓库提交历史（#147C 补齐的提交日志腿）。
     * <p>
     * 唯一合法来源 = Git 仓库本身（JGit log）。原 datanet {@code GitController.commits}
     * 以 SQL 查不存在的 {@code td_git_repository} 表，失败后折叠成「空列表 + 200 成功」＝假绿；
     * 本腿接回后，读不到就是显式失败（{@link GitException}），不再返回空面冒充正常。
     *
     * @param repository 注册表行（{@code localPath} 必须已解析，否则显式 {@link GitException}）
     * @param offset     跳过条数（从 HEAD 起，含 merge；&lt;0 视为 0）
     * @param limit      返回条数上限（&lt;=0 视为默认 {@value #DEFAULT_COMMIT_LOG_LIMIT}）
     */
    List<GitCommitView> listCommits(GitRepository repository, int offset, int limit) throws GitException;

    /** {@link #listCommits} 缺省条数（口径单源，消费方不得各写一份）。 */
    int DEFAULT_COMMIT_LOG_LIMIT = 20;

    /** {@link #listCommits} 条数上限（防一次拉全史打满内存）。 */
    int MAX_COMMIT_LOG_LIMIT = 200;

    // ── 扩展方法 (Pipeline/Config/Sysman 共用) ──

    void clone(String remoteUrl, String localPath, String username, String password) throws GitException;

    void pull(String localPath, String remote, String branch) throws GitException;

    void push(String localPath, String username, String password) throws GitException;

    void commit(String localPath, String message, List<String> filePaths) throws GitException;

    void createBranch(String localPath, String branchName) throws GitException;

    void checkoutBranch(String localPath, String branchName) throws GitException;

    void merge(String localPath, String branchName) throws GitException;

    /**
     * 枚举本地仓库分支（#147C DD-3 收口）。
     * <p>
     * 原 data-engine {@code PipelineGitService.listBranchesAtLocalPath} 自带
     * {@code org.eclipse.jgit} 分支枚举腿 = 全仓 main 面第二套 Git 接口（违 DD-3），
     * 由属主承流后消费方只调本腿。分支读不到一律显式 {@link GitException}，
     * <b>禁</b>返回空列表冒充「没有分支」。
     *
     * @param localPath 已解析的仓库绝对路径（空值显式拒绝）
     * @return 短分支名列表（去掉 {@code refs/heads/} 前缀）
     */
    List<String> listBranches(String localPath) throws GitException;

    /**
     * 读「某相对路径」的提交历史短 sha（#147C DD-3 收口：pipeline 版本列表腿归位属主）。
     * <p>
     * 与 {@link #listCommits} 同为 Git 对象库读面；差别只在按路径过滤且只回短 sha。
     * 仓库存在但该路径无提交 ⇒ 返回空列表（真值：尚无归档）；仓库打不开/读失败
     * ⇒ 显式 {@link GitException}（禁把故障折叠成空历史）。
     *
     * @param localPath     已解析的仓库绝对路径（空值显式拒绝）
     * @param relativePath  仓库内相对路径（如 {@code pipelines/<id>/pipeline.yaml}）
     * @param limit         条数上限（&lt;=0 视为默认 {@value #DEFAULT_COMMIT_LOG_LIMIT}，
     *                      上限 {@value #MAX_COMMIT_LOG_LIMIT}）
     */
    List<String> listCommitShasForPath(String localPath, String relativePath, int limit) throws GitException;

    /**
     * 按引用读回「某相对路径」的文件内容（#147E：read-at-ref，回滚/对账的取证腿）。
     *
     * <p><b>为什么必须由属主提供</b>：§2.9.6② 要求回滚走「发布已知历史版本（git ref 检出重应用）」，
     * §2.9.6③ 对账要求把仓库侧内容读回与权威库比对。缺本腿时这两件事都只能报
     * {@code UNVERIFIABLE}/{@code ROLLBACK_PENDING}（#147D 的诚实降级）；本腿到位后，
     * 「按 ref 取证」成为真实能力，不再依赖工作树当前内容（那会把「已被后续发布覆盖」
     * 误当成历史版本内容）。</p>
     *
     * <p><b>三态语义</b>：引用可解析且路径存在 → 返回内容；引用可解析但路径在该 ref 下不存在
     * → 返回 {@link java.util.Optional#empty()}（真值：该版本未归档此文件）；引用解析失败/
     * 仓库打不开/IO 故障 → 显式 {@link GitException}（禁把故障折叠成"文件不存在"）。</p>
     *
     * @param repository   注册表行（{@code localPath} 必须已解析）
     * @param ref          提交号 / 标签 / 分支等可解析引用（空值显式拒绝）
     * @param relativePath 仓库内相对路径（如 {@code ontologies/ont001/1.0.2.json}；空值显式拒绝）
     * @return 文件文本内容；路径在 ref 下不存在时为空
     */
    java.util.Optional<String> readFileAtRef(GitRepository repository, String ref, String relativePath)
            throws GitException;

    class GitException extends Exception {
        private static final long serialVersionUID = 1L;

        /**
         * 失败分类（#147BU · G8 登记缺陷）：决定调用方的状态行分档。
         *
         * <p>{@link #NOT_AVAILABLE}＝服务端侧未就绪（仓库本地目录不在位、localPath 未解析），
         * 换参数重试无用 ⇒ 调用方落 503；{@link #OPERATION_FAILED}＝仓库已就位但对象库操作失败
         * ⇒ 500。缺省保 {@link #OPERATION_FAILED}，既有构造点语义不变。</p>
         */
        public enum Kind {
            OPERATION_FAILED,
            NOT_AVAILABLE
        }

        private final Kind kind;

        public GitException(String message) {
            this(message, Kind.OPERATION_FAILED, null);
        }

        public GitException(String message, Throwable cause) {
            this(message, Kind.OPERATION_FAILED, cause);
        }

        public GitException(String message, Kind kind) {
            this(message, kind, null);
        }

        public GitException(String message, Kind kind, Throwable cause) {
            super(message, cause);
            this.kind = kind == null ? Kind.OPERATION_FAILED : kind;
        }

        public Kind getKind() {
            return kind;
        }
    }
}


