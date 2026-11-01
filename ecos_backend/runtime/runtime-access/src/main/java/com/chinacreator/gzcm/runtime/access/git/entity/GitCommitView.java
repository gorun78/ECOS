package com.chinacreator.gzcm.runtime.access.git.entity;

import java.util.Date;

/**
 * Git 提交历史视图（#147C，runtime 统一出口 {@code GitService.listCommits} 的返回载体）。
 *
 * <p>只承载 Git 仓库自身可读的事实（commitId / 短消息 / 作者 / 提交时间），
 * <b>不含</b>任何数据库自增或注册表列——提交历史不是 DB 表（原 datanet
 * {@code GitController.commits} 去查不存在的 {@code td_git_repository}
 * 提交记录表，是命名与载体的双重错位：注册表记「仓库」，提交史在 Git 对象库）。</p>
 */
public class GitCommitView {

    /** 完整 commit id（40 位十六进制） */
    private String commitId;

    /** 提交短消息（subject 行） */
    private String message;

    /** 作者名 */
    private String authorName;

    /** 作者邮箱 */
    private String authorEmail;

    /** 提交时间（Git committer time） */
    private Date committedAt;

    public GitCommitView() {
    }

    public GitCommitView(String commitId, String message, String authorName,
                         String authorEmail, Date committedAt) {
        this.commitId = commitId;
        this.message = message;
        this.authorName = authorName;
        this.authorEmail = authorEmail;
        this.committedAt = committedAt;
    }

    public String getCommitId() {
        return commitId;
    }

    public void setCommitId(String commitId) {
        this.commitId = commitId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorEmail() {
        return authorEmail;
    }

    public void setAuthorEmail(String authorEmail) {
        this.authorEmail = authorEmail;
    }

    public Date getCommittedAt() {
        return committedAt;
    }

    public void setCommittedAt(Date committedAt) {
        this.committedAt = committedAt;
    }
}
