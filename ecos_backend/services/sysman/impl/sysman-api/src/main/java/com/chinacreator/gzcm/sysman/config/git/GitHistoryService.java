package com.chinacreator.gzcm.sysman.config.git;

import java.util.List;

import com.chinacreator.gzcm.sysman.config.git.entity.GitCommit;

/**
 * Git提交历史查询服务接口
 * 
 * @author CDRC Design Team
 */
public interface GitHistoryService {
    
    /**
     * 查询提交历史列表
     * 
     * @param repositoryId 仓库ID
     * @param branchName 分支名称（可选）
     * @param limit 限制数量
     * @return 提交列表
     * @throws GitHistoryException 历史查询异常
     */
    List<GitCommit> getCommitHistory(String repositoryId, String branchName, int limit) throws GitHistoryException;
    
    /**
     * 获取提交详情
     * 
     * @param repositoryId 仓库ID
     * @param commitId 提交ID
     * @return 提交详情
     * @throws GitHistoryException 历史查询异常
     */
    GitCommit getCommitDetail(String repositoryId, String commitId) throws GitHistoryException;
    
    /**
     * 查询文件变更历史
     * 
     * @param repositoryId 仓库ID
     * @param filePath 文件路径
     * @param limit 限制数量
     * @return 提交列表
     * @throws GitHistoryException 历史查询异常
     */
    List<GitCommit> getFileHistory(String repositoryId, String filePath, int limit) throws GitHistoryException;
    
    /**
     * 获取提交间差）?
     * 
     * @param repositoryId 仓库ID
     * @param commitId1 提交ID1
     * @param commitId2 提交ID2
     * @return 差异内容
     * @throws GitHistoryException 历史查询异常
     */
    String getDiff(String repositoryId, String commitId1, String commitId2) throws GitHistoryException;
    
    /**
     * 获取文件差异
     * 
     * @param repositoryId 仓库ID
     * @param filePath 文件路径
     * @param commitId1 提交ID1
     * @param commitId2 提交ID2
     * @return 差异内容
     * @throws GitHistoryException 历史查询异常
     */
    String getFileDiff(String repositoryId, String filePath, String commitId1, String commitId2) throws GitHistoryException;
    
    /**
     * Git历史查询异常
     */
    class GitHistoryException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public GitHistoryException(String message) {
            super(message);
        }
        
        public GitHistoryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}


