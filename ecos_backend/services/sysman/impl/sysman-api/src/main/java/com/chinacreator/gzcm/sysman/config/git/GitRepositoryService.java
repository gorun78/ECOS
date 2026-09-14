package com.chinacreator.gzcm.sysman.config.git;

import java.util.List;

import com.chinacreator.gzcm.sysman.config.git.entity.GitRepository;

/**
 * Git仓库管理服务接口
 * 
 * @author CDRC Design Team
 */
public interface GitRepositoryService {
    
    /**
     * 创建Git仓库
     * 
     * @param repository Git仓库信息
     * @return 创建的仓）?
     * @throws GitRepositoryException 仓库操作异常
     */
    GitRepository createRepository(GitRepository repository) throws GitRepositoryException;
    
    /**
     * 初始化本地仓）?
     * 
     * @param localPath 本地路径
     * @throws GitRepositoryException 仓库操作异常
     */
    void initRepository(String localPath) throws GitRepositoryException;
    
    /**
     * 配置远程仓库
     * 
     * @param repositoryId 仓库ID
     * @param remoteUrl 远程URL
     * @param username 用户）?
     * @param password 密码
     * @throws GitRepositoryException 仓库操作异常
     */
    void configureRemote(String repositoryId, String remoteUrl, String username, String password) throws GitRepositoryException;
    
    /**
     * 获取仓库信息
     * 
     * @param repositoryId 仓库ID
     * @return 仓库信息
     * @throws GitRepositoryException 仓库操作异常
     */
    GitRepository getRepository(String repositoryId) throws GitRepositoryException;
    
    /**
     * 获取所有仓库列）?
     * 
     * @return 仓库列表
     * @throws GitRepositoryException 仓库操作异常
     */
    List<GitRepository> listRepositories() throws GitRepositoryException;
    
    /**
     * 获取分支列表
     * 
     * @param repositoryId 仓库ID
     * @return 分支名称列表
     * @throws GitRepositoryException 仓库操作异常
     */
    List<String> listBranches(String repositoryId) throws GitRepositoryException;
    
    /**
     * 删除分支
     * 
     * @param repositoryId 仓库ID
     * @param branchName 分支名称
     * @throws GitRepositoryException 仓库操作异常
     */
    void deleteBranch(String repositoryId, String branchName) throws GitRepositoryException;
    
    /**
     * Git仓库操作异常
     */
    class GitRepositoryException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public GitRepositoryException(String message) {
            super(message);
        }
        
        public GitRepositoryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}


