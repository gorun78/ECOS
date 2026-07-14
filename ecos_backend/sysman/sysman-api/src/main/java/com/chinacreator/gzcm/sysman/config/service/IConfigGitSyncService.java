package com.chinacreator.gzcm.sysman.config.service;

import java.util.List;

import com.chinacreator.gzcm.sysman.config.entity.Config;

/**
 * 配置Git同步服务接口
 * 
 * @author CDRC Design Team
 */
public interface IConfigGitSyncService {
    
    /**
     * 从Git拉取配置
     */
    List<Config> syncFromGit(String repositoryId, String branchName) throws GitSyncException;
    
    /**
     * 推送配置到Git
     */
    void syncToGit(String configId, String repositoryId, String commitMessage) throws GitSyncException;
    
    /**
     * 定时同步（从Git拉取））
     */
    void scheduleSyncFromGit(String repositoryId, long intervalSeconds);
    
    /**
     * Git同步异常
     */
    class GitSyncException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public GitSyncException(String message) {
            super(message);
        }
        
        public GitSyncException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}


