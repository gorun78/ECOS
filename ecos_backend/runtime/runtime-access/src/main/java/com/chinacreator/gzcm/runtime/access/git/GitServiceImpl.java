package com.chinacreator.gzcm.runtime.access.git;

import com.chinacreator.gzcm.runtime.access.git.entity.GitCommitView;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * JGit 实现 — 全系统统一 Git 出口。
 * <p>
 * 基于 org.eclipse.jgit 纯 Java Git 库，实现 runtime GitService 全部方法，
 * 替换原有内存占位桩，供 sysman 配置同步 / data-engine Pipeline / cognitive 知识图谱 共用。
 * <p>
 * #147C 加固（DD-3 属主豁免登记：全仓 main 面唯一 {@code org.eclipse.jgit.*} import 落点）：
 * <ul>
 *   <li>凡以 {@link GitRepository} 定位的读/写腿，一律先过 {@link #requireLocalPath}：
 *       注册表行缺失或 {@code localPath} 未解析 ⇒ 显式 {@link GitException}，
 *       禁止把 {@code new File(null)} 的 NPE 或「空结果 + 成功」冒充正常（§2.9.9 核心原则）；</li>
 *   <li>{@link #listCommits} 补齐提交历史读腿（提交史唯一来源 = Git 对象库，非数据库表）；</li>
 *   <li>{@link #diff} 由恒空桩改为 JGit 真实两引用树比对。</li>
 * </ul>
 */
@Service
public class GitServiceImpl implements GitService {

    private static final Logger log = LoggerFactory.getLogger(GitServiceImpl.class);

    /** 短 sha 位数（沿用 pipeline 版本列表既有 7 位口径，改由属主单源承载）。 */
    private static final int SHORT_SHA_LENGTH = 7;

    // ── Runtime 原有方法 (JGit 实现) ──

    @Override
    public String commitAllChanges(GitRepository repository, String commitMessage,
                                    String authorName, String authorEmail) throws GitException {
        String localPath = requireLocalPath(repository);
        log.info("JGit commitAll: path={}, msg='{}', author={}", localPath, commitMessage, authorName);
        try (Git git = Git.open(new File(localPath))) {
            git.add().addFilepattern(".").call();
            var commit = git.commit()
                .setMessage(commitMessage)
                .setAuthor(authorName, authorEmail)
                .call();
            String commitId = commit.getName();
            log.info("JGit commitAll 完成: {}, commitId={}", localPath, commitId);
            return commitId;
        } catch (IOException | GitAPIException e) {
            throw new GitException("commitAllChanges 失败: " + localPath, e);
        }
    }

    @Override
    public void pull(GitRepository repository, String remote, String branch) throws GitException {
        String localPath = requireLocalPath(repository);
        log.info("JGit pull: path={}, remote={}, branch={}", localPath, remote, branch);
        try (Git git = Git.open(new File(localPath))) {
            git.pull().setRemote(remote).setRemoteBranchName(branch).call();
            log.info("JGit pull 完成: {}", localPath);
        } catch (IOException | GitAPIException e) {
            throw new GitException("pull 失败: " + localPath, e);
        }
    }

    @Override
    public String createTag(GitRepository repository, String tagName, String message) throws GitException {
        String localPath = requireLocalPath(repository);
        log.info("JGit createTag: path={}, tag={}", localPath, tagName);
        try (Git git = Git.open(new File(localPath))) {
            git.tag().setName(tagName).setMessage(message).call();
            log.info("JGit tag 创建完成: {}", tagName);
            return tagName;
        } catch (IOException | GitAPIException e) {
            throw new GitException("createTag 失败: " + tagName, e);
        }
    }

    @Override
    public String getCurrentCommitId(GitRepository repository) throws GitException {
        String localPath = requireLocalPath(repository);
        try (Git git = Git.open(new File(localPath))) {
            var head = git.getRepository().findRef("HEAD");
            return head != null && head.getObjectId() != null ? head.getObjectId().getName() : "";
        } catch (IOException e) {
            throw new GitException("getCurrentCommitId 失败: " + localPath, e);
        }
    }

    /**
     * 两引用真实树比对（#147C 由恒空桩改实装）。
     * <p>响应键面保持既有契约（{@code repository}/{@code from}/{@code to}/{@code filesChanged}），
     * 仅把「恒空列表」换成 JGit 真值；引用解析不出＝显式 {@link GitException}，不返回空面冒充「无差异」。</p>
     */
    @Override
    public Map<String, Object> diff(GitRepository repository, String fromRef, String toRef) throws GitException {
        String localPath = requireLocalPath(repository);
        if (isBlank(fromRef) || isBlank(toRef)) {
            throw new GitException("diff 需要可解析的 from/to 引用: from=" + fromRef + ", to=" + toRef);
        }
        log.info("JGit diff: path={}, {}..{}", localPath, fromRef, toRef);
        List<Map<String, Object>> changed = new ArrayList<>();
        try (Git git = Git.open(new File(localPath))) {
            Repository repo = git.getRepository();
            ObjectId fromId = repo.resolve(fromRef.trim());
            ObjectId toId = repo.resolve(toRef.trim());
            if (fromId == null || toId == null) {
                throw new GitException("diff 引用无法解析: from=" + fromRef + "(" + (fromId == null ? "MISSING" : "ok")
                        + "), to=" + toRef + "(" + (toId == null ? "MISSING" : "ok") + ")");
            }
            try (RevWalk walk = new RevWalk(repo);
                 ByteArrayOutputStream sink = new ByteArrayOutputStream();
                 DiffFormatter formatter = new DiffFormatter(sink)) {
                formatter.setRepository(repo);
                RevCommit fromCommit = walk.parseCommit(fromId);
                RevCommit toCommit = walk.parseCommit(toId);
                for (DiffEntry entry : formatter.scan(fromCommit.getTree(), toCommit.getTree())) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("changeType", entry.getChangeType().name());
                    row.put("oldPath", entry.getOldPath());
                    row.put("newPath", entry.getNewPath());
                    changed.add(row);
                }
            }
        } catch (IOException e) {
            throw new GitException("diff 失败: " + localPath + " " + fromRef + ".." + toRef, e);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("repository", localPath);
        result.put("from", fromRef.trim());
        result.put("to", toRef.trim());
        result.put("filesChanged", changed);
        result.put("changedCount", changed.size());
        return result;
    }

    /**
     * 提交历史读腿（#147C 新增）：唯一数据源 = 仓库对象库（JGit log）。
     * <p>空仓库（无 HEAD）由 JGit 抛 {@code NoHeadException} ⇒ 归入本方法
     * {@link GitException}（显式失败），不折叠成「空列表 + 成功」。</p>
     */
    @Override
    public List<GitCommitView> listCommits(GitRepository repository, int offset, int limit) throws GitException {
        String localPath = requireLocalPath(repository);
        int skip = Math.max(offset, 0);
        int maxCount = limit <= 0 ? DEFAULT_COMMIT_LOG_LIMIT : Math.min(limit, MAX_COMMIT_LOG_LIMIT);
        List<GitCommitView> views = new ArrayList<>();
        try (Git git = Git.open(new File(localPath))) {
            for (RevCommit commit : git.log().setSkip(skip).setMaxCount(maxCount).call()) {
                if (commit == null) {
                    continue;
                }
                PersonIdent author = commit.getAuthorIdent();
                PersonIdent committer = commit.getCommitterIdent();
                PersonIdent who = author != null ? author : committer;
                views.add(new GitCommitView(
                        commit.getName(),
                        commit.getShortMessage(),
                        who != null ? who.getName() : null,
                        who != null ? who.getEmailAddress() : null,
                        committer != null ? committer.getWhen() : null));
            }
        } catch (IOException | GitAPIException e) {
            throw new GitException("listCommits 失败: " + localPath + " - " + e.getMessage(), e);
        }
        log.info("JGit listCommits: path={}, offset={}, limit={} → {} 条", localPath, skip, maxCount, views.size());
        return views;
    }

    /**
     * 注册表行 → 可用本地路径（#147C 显式失败守卫）。
     * <p>{@code localPath} 是 {@link GitRepoPathResolver} 的解析产物；未装配/解析不出时为
     * {@code null}（见 {@code GitRepositoryServiceImpl} 禁伪造路径口径）——此处必须拒绝执行，
     * 禁止 {@code new File(null)} 的 NPE 或空结果冒充成功。</p>
     */
    private String requireLocalPath(GitRepository repository) throws GitException {
        if (repository == null) {
            throw new GitException("Git 仓库未定位：repository 为 null（须先经 ecos_git_repo 注册表按 repositoryId 解析）");
        }
        String localPath = repository.getLocalPath();
        if (isBlank(localPath)) {
            throw new GitException("Git 仓库本地路径未解析：repositoryId=" + repository.getRepositoryId()
                    + "（检查 sys_config:ecos_git_repo_root 与注册表 repo_root，禁回退进程内伪造路径）");
        }
        return localPath.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * 扩展方法（pipeline 工作树）的路径守卫：空路径 ⇒ 显式 {@link GitException}，
     * 不再让 {@code new File(null)} 抛 NPE 冒充「Git 失败」。（DI-1：面向用户的仓库读面
     * 禁止走本组以 localPath 定位的方法，一律走 {@link GitRepository} 注册表腿。）
     */
    private String requirePath(String localPath, String operation) throws GitException {
        if (isBlank(localPath)) {
            throw new GitException(operation + " 失败：localPath 为空（pipeline 工作树须由服务端按单源根路径解析，禁空值下发）");
        }
        return localPath.trim();
    }

    // ── 扩展方法 (Pipeline 常用) ──

    @Override
    public void clone(String remoteUrl, String localPath, String username, String password) throws GitException {
        localPath = requirePath(localPath, "clone");
        log.info("JGit clone: {} → {}", remoteUrl, localPath);
        try {
            Git.cloneRepository()
                .setURI(remoteUrl)
                .setDirectory(new File(localPath))
                .setCredentialsProvider(creds(username, password))
                .call()
                .close();
            log.info("Git clone 完成: {}", localPath);
        } catch (GitAPIException e) {
            throw new GitException("clone 失败: " + remoteUrl, e);
        }
    }

    @Override
    public void pull(String localPath, String remote, String branch) throws GitException {
        localPath = requirePath(localPath, "pull");
        log.info("JGit pull: path={}, remote={}, branch={}", localPath, remote, branch);
        try (Git git = Git.open(new File(localPath))) {
            git.pull().setRemote(remote).setRemoteBranchName(branch).call();
            log.info("Git pull 完成: {}", localPath);
        } catch (IOException | GitAPIException e) {
            throw new GitException("pull 失败: " + localPath, e);
        }
    }

    @Override
    public void push(String localPath, String username, String password) throws GitException {
        localPath = requirePath(localPath, "push");
        log.info("JGit push: {}", localPath);
        try (Git git = Git.open(new File(localPath))) {
            git.push().setCredentialsProvider(creds(username, password)).call();
            log.info("Git push 完成: {}", localPath);
        } catch (IOException | GitAPIException e) {
            throw new GitException("push 失败: " + localPath, e);
        }
    }

    @Override
    public void commit(String localPath, String message, List<String> filePaths) throws GitException {
        localPath = requirePath(localPath, "commit");
        log.info("JGit commit: path={}, msg='{}', files={}", localPath, message, filePaths);
        try (Git git = Git.open(new File(localPath))) {
            if (filePaths != null && !filePaths.isEmpty()) {
                for (String p : filePaths) git.add().addFilepattern(p).call();
            } else {
                git.add().addFilepattern(".").call();
            }
            git.commit().setMessage(message).call();
            log.info("Git commit 完成: {}", localPath);
        } catch (IOException | GitAPIException e) {
            throw new GitException("commit 失败: " + localPath, e);
        }
    }

    @Override
    public void createBranch(String localPath, String branchName) throws GitException {
        localPath = requirePath(localPath, "createBranch");
        log.info("JGit createBranch: path={}, branch={}", localPath, branchName);
        try (Git git = Git.open(new File(localPath))) {
            git.branchCreate().setName(branchName).call();
            log.info("Git branch 创建完成: {}", branchName);
        } catch (IOException | GitAPIException e) {
            throw new GitException("createBranch 失败: " + branchName, e);
        }
    }

    @Override
    public void checkoutBranch(String localPath, String branchName) throws GitException {
        localPath = requirePath(localPath, "checkoutBranch");
        log.info("JGit checkout: path={}, branch={}", localPath, branchName);
        try (Git git = Git.open(new File(localPath))) {
            git.checkout().setName(branchName).call();
            log.info("Git checkout 完成: {}", branchName);
        } catch (IOException | GitAPIException e) {
            throw new GitException("checkoutBranch 失败: " + branchName, e);
        }
    }

    @Override
    public void merge(String localPath, String branchName) throws GitException {
        localPath = requirePath(localPath, "merge");
        log.info("JGit merge: path={}, branch={}", localPath, branchName);
        try (Git git = Git.open(new File(localPath))) {
            git.merge().include(git.getRepository().findRef(branchName)).call();
            log.info("Git merge 完成: {}", branchName);
        } catch (IOException | GitAPIException e) {
            throw new GitException("merge 失败: " + branchName, e);
        }
    }

    private UsernamePasswordCredentialsProvider creds(String username, String password) {
        if (username != null && !username.isEmpty()) {
            return new UsernamePasswordCredentialsProvider(username, password != null ? password : "");
        }
        return null;
    }

    /**
     * 分支枚举（#147C DD-3 收口：原 data-engine {@code PipelineGitService} 自带 jgit
     * {@code branchList} 腿 = main 面第二套 Git 接口，现由属主承流）。
     *
     * <p>读不到 ⇒ 显式 {@link GitException}；仓库无 HEAD/无分支返回空列表是真值，
     * 但「目录打不开」绝不折叠成空列表。</p>
     */
    @Override
    public List<String> listBranches(String localPath) throws GitException {
        localPath = requirePath(localPath, "listBranches");
        try (Git git = Git.open(new File(localPath))) {
            List<String> names = new ArrayList<>();
            for (Ref ref : git.branchList().call()) {
                names.add(stripHeadPrefix(ref.getName()));
            }
            return names;
        } catch (IOException | GitAPIException e) {
            throw new GitException("listBranches 失败: " + localPath + " - " + e.getMessage(), e);
        }
    }

    /** 路径过滤的提交短 sha 历史（#147C DD-3 收口：pipeline 版本列表腿归位属主）。 */
    @Override
    public List<String> listCommitShasForPath(String localPath, String relativePath, int limit)
            throws GitException {
        localPath = requirePath(localPath, "listCommitShasForPath");
        if (isBlank(relativePath)) {
            throw new GitException("listCommitShasForPath 失败：relativePath 为空（路径过滤读面必须有明确路径）");
        }
        int maxCount = limit <= 0 ? DEFAULT_COMMIT_LOG_LIMIT : Math.min(limit, MAX_COMMIT_LOG_LIMIT);
        try (Git git = Git.open(new File(localPath))) {
            List<String> shas = new ArrayList<>();
            for (RevCommit commit : git.log().addPath(relativePath.trim())
                    .setMaxCount(maxCount).call()) {
                shas.add(commit.getName().substring(0, SHORT_SHA_LENGTH));
            }
            return shas;
        } catch (IOException | GitAPIException e) {
            throw new GitException(
                    "listCommitShasForPath 失败: " + localPath + " path=" + relativePath + " - " + e.getMessage(), e);
        }
    }

    private static String stripHeadPrefix(String refName) {
        return refName != null && refName.startsWith("refs/heads/")
                ? refName.substring("refs/heads/".length())
                : refName;
    }
}
