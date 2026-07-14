package com.chinacreator.gzcm.runtime.core.git;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.git.entity.GitRepository;

/**
 * Runtime 统一 Git 服务接口。
 * <p>
 * 全系统（sysman 配置同步 / data-engine Pipeline / cognitive 知识图谱 / aimod AI 场景）
 * 均通过此接口操作 Git，仅信息组织形式不同。
 */
public interface GitService {

    // ── Runtime 原有方法 ──

    String commitAllChanges(GitRepository repository, String commitMessage,
                            String authorName, String authorEmail) throws GitException;

    void pull(GitRepository repository, String remote, String branch) throws GitException;

    String createTag(GitRepository repository, String tagName, String message) throws GitException;

    String getCurrentCommitId(GitRepository repository) throws GitException;

    Map<String, Object> diff(GitRepository repository, String fromRef, String toRef) throws GitException;

    // ── 扩展方法 (Pipeline/Config/Sysman 共用) ──

    void clone(String remoteUrl, String localPath, String username, String password) throws GitException;

    void pull(String localPath, String remote, String branch) throws GitException;

    void push(String localPath, String username, String password) throws GitException;

    void commit(String localPath, String message, List<String> filePaths) throws GitException;

    void createBranch(String localPath, String branchName) throws GitException;

    void checkoutBranch(String localPath, String branchName) throws GitException;

    void merge(String localPath, String branchName) throws GitException;

    class GitException extends Exception {
        private static final long serialVersionUID = 1L;

        public GitException(String message) {
            super(message);
        }

        public GitException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}


