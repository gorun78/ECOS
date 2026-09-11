package com.chinacreator.gzcm.sysman.config.service.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chinacreator.gzcm.sysman.config.dao.ConfigDao;
import com.chinacreator.gzcm.sysman.config.entity.Config;
import com.chinacreator.gzcm.sysman.config.service.IConfigGitSyncService;
import com.chinacreator.gzcm.sysman.config.service.IConfigService;
import com.chinacreator.gzcm.sysman.config.git.GitRepositoryService;
import com.chinacreator.gzcm.sysman.config.git.GitService;
import com.chinacreator.gzcm.sysman.config.git.entity.GitRepository;

/**
 * Git同步服务实现
 * 
 * @author CDRC Design Team
 */
public class ConfigGitSyncServiceImpl implements IConfigGitSyncService {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigGitSyncServiceImpl.class);
    
    private final GitService gitService;
    private final GitRepositoryService gitRepositoryService;
    private final ConfigDao configDao;
    private final IConfigService configService;
    
    public ConfigGitSyncServiceImpl(
            GitService gitService,
            GitRepositoryService gitRepositoryService,
            ConfigDao configDao,
            IConfigService configService) {
        this.gitService = gitService;
        this.gitRepositoryService = gitRepositoryService;
        this.configDao = configDao;
        this.configService = configService;
    }
    
    @Override
    public List<Config> syncFromGit(String repositoryId, String branchName) throws GitSyncException {
        try {
            GitRepository repository = gitRepositoryService.getRepository(repositoryId);
            
            // 从Git仓库拉取最新代码
            gitService.pull(repository.getLocalPath());
            
            // 如果指定分支，则切换到指定分支
            if (branchName != null && !branchName.isEmpty()) {
                gitService.checkoutBranch(repository.getLocalPath(), branchName);
            }
            
            // 同步Git仓库中的配置文件到数据库
            // 绠€鍖栧疄鐜帮細瀹為檯搴旇閬嶅巻repository鐩綍涓嬬殑閰嶇疆鏂囦欢
            List<Config> syncedConfigs = new ArrayList<>();
            
            // TODO: 实现同步Git仓库中的配置文件到数据库
            // 1. 获取Git仓库中的配置文件
            // 2. 解析YAML/JSON配置文件
            // 3. 创建或更新Config对象
            // 4. 保存Config对象到数据库
            
            logger.info("同步Git仓库中的配置文件到数据库: repositoryId={}, branchName={}", repositoryId, branchName);
            
            return syncedConfigs;
            
        } catch (GitService.GitException e) {
                logger.error("同步Git仓库中的配置文件到数据库失败: {}", e.getMessage(), e);
            throw new GitSyncException("同步Git仓库中的配置文件到数据库失败: " + e.getMessage(), e);
        } catch (GitRepositoryService.GitRepositoryException e) {
            logger.error("同步Git仓库中的配置文件到数据库失败: {}", e.getMessage(), e);
            throw new GitSyncException("同步Git仓库中的配置文件到数据库失败: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("同步Git仓库中的配置文件到数据库失败: {}", e.getMessage(), e);
            throw new GitSyncException("同步Git仓库中的配置文件到数据库失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void syncToGit(String configId, String repositoryId, String commitMessage) throws GitSyncException {
        try {
            Config config = configDao.findById(configId);
            if (config == null) {
                throw new GitSyncException("配置不存在: " + configId);
            }
            
            GitRepository repository = gitRepositoryService.getRepository(repositoryId);
            
            // 将配置文件写入Git仓库
            String configPath = getConfigPath(repository.getLocalPath(), config);
            writeConfigToFile(configPath, config);
            
            // 提交配置文件到Git仓库
            List<String> filePaths = new ArrayList<>();
            filePaths.add(configPath);
            gitService.commit(repository.getLocalPath(), commitMessage, filePaths);
            
            // 推送配置文件到Git仓库
            gitService.push(repository.getLocalPath(), repository.getUsername(), repository.getPassword());
            
            logger.info("提交配置文件到Git仓库: configId={}, repositoryId={}", configId, repositoryId);
            
        } catch (GitService.GitException e) {
            logger.error("提交配置文件到Git仓库失败: {}", e.getMessage(), e);
            throw new GitSyncException("提交配置文件到Git仓库失败: " + e.getMessage(), e);
        } catch (GitRepositoryService.GitRepositoryException e) {
            logger.error("提交配置文件到Git仓库失败: {}", e.getMessage(), e);
            throw new GitSyncException("提交配置文件到Git仓库失败: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("提交配置文件到Git仓库失败: {}", e.getMessage(), e);
            throw new GitSyncException("提交配置文件到Git仓库失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void scheduleSyncFromGit(String repositoryId, long intervalSeconds) {
        // 定时同步Git仓库中的配置文件到数据库
        logger.info("定时同步Git仓库中的配置文件到数据库: repositoryId={}, interval={}", repositoryId, intervalSeconds);
        // TODO: 瀹炵幇瀹氭椂鍚屾閫昏緫
    }
    
    /**
     * 获取配置文件路径
     */
    private String getConfigPath(String repositoryPath, Config config) {
        String typeDir = config.getConfigType().toLowerCase().replace("_", "-");
        return repositoryPath + File.separator + typeDir + File.separator + config.getConfigName() + ".yaml";
    }
    
    /**
        * 将
     */
    private void writeConfigToFile(String filePath, Config config) throws Exception {
        File file = new File(filePath);
        File parentDir = file.getParentFile();
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        // 将配置文件写入文件
        java.nio.file.Files.write(
                java.nio.file.Paths.get(filePath),
                config.getConfigContent().getBytes("UTF-8"));
    }
}


