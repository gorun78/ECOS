package com.chinacreator.gzcm.sysman.config.git;

import java.util.List;

/**
 * Git服务接口
 * 提供Git仓库的基本操作功）?
 * 
 * @author CDRC Design Team
 */
public interface GitService {
    
    /**
     * 克隆远程仓库到本地路）?
     * 
     * @param remoteUrl 远程仓库URL
     * @param localPath 本地路径
     * @param username 用户名（可选）
     * @param password 密码（可选）
     * @throws GitException Git操作异常
     */
    void clone(String remoteUrl, String localPath, String username, String password) throws GitException;
    
    /**
     * 拉取远程更新
     * 
     * @param localPath 本地仓库路径
     * @throws GitException Git操作异常
     */
    void pull(String localPath) throws GitException;
    
    /**
     * 推送本地更改到远程
     * 
     * @param localPath 本地仓库路径
     * @param username 用户名（可选）
     * @param password 密码（可选）
     * @throws GitException Git操作异常
     */
    void push(String localPath, String username, String password) throws GitException;
    
    /**
     * 提交更改
     * 
     * @param localPath 本地仓库路径
     * @param message 提交信息
     * @param filePaths 要提交的文件路径列表（null表示提交所有更改）
     * @throws GitException Git操作异常
     */
    void commit(String localPath, String message, List<String> filePaths) throws GitException;
    
    /**
     * 创建分支
     * 
     * @param localPath 本地仓库路径
     * @param branchName 分支名称
     * @throws GitException Git操作异常
     */
    void createBranch(String localPath, String branchName) throws GitException;
    
    /**
     * 切换分支
     * 
     * @param localPath 本地仓库路径
     * @param branchName 分支名称
     * @throws GitException Git操作异常
     */
    void checkoutBranch(String localPath, String branchName) throws GitException;
    
    /**
     * 合并分支
     * 
     * @param localPath 本地仓库路径
     * @param branchName 要合并的分支名称
     * @throws GitException Git操作异常
     */
    void merge(String localPath, String branchName) throws GitException;
    
    /**
     * Git操作异常
     */
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


