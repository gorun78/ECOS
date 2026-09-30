package com.chinacreator.gzcm.sysman.config.service.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService;
import com.chinacreator.gzcm.runtime.access.git.GitService;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;
import com.chinacreator.gzcm.sysman.config.dao.ConfigDao;
import com.chinacreator.gzcm.sysman.config.entity.Config;
import com.chinacreator.gzcm.sysman.config.service.IConfigGitSyncService;
import com.chinacreator.gzcm.sysman.config.service.IConfigService;

/**
 * Git同步服务实现（H4-T3：委托 runtime 统一 Git 底座，不再自建第二套 Git 抽象）。
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
            GitRepository repository = gitRepositoryService.getRepositoryById(repositoryId);

            gitService.pull(repository.getLocalPath(), "origin", null);

            if (branchName != null && !branchName.isEmpty()) {
                gitService.checkoutBranch(repository.getLocalPath(), branchName);
            }

            // 同步Git仓库中的配置文件到数据库
            // TODO: 实现同步Git仓库中的配置文件到数据库
            List<Config> syncedConfigs = new ArrayList<>();

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

            GitRepository repository = gitRepositoryService.getRepositoryById(repositoryId);

            String configPath = getConfigPath(repository.getLocalPath(), config);
            writeConfigToFile(configPath, config);

            List<String> filePaths = new ArrayList<>();
            filePaths.add(configPath);
            gitService.commit(repository.getLocalPath(), commitMessage, filePaths);

            // 凭据不再由仓库实体明文携带，匿名/凭据注入由 security-engine 加密存储侧统一处理
            gitService.push(repository.getLocalPath(), null, null);

            logger.info("提交配置文件到Git仓库: configId={}, repositoryId={}", configId, repositoryId);

        } catch (GitService.GitException e) {
            logger.error("提交配置文件到Git仓库失败: {}", e.getMessage(), e);
            throw new GitSyncException("提交配置文件到Git仓库失败: " + e.getMessage(), e);
        } catch (GitRepositoryService.GitRepositoryException e) {
            logger.error("提交配置文件到Git仓库失败: {}", e.getMessage(), e);
            throw new GitSyncException("提交配置文件到Git仓库失败: " + e.getMessage(), e);
        } catch (GitSyncException e) {
            throw e;
        } catch (Exception e) {
            logger.error("提交配置文件到Git仓库失败: {}", e.getMessage(), e);
            throw new GitSyncException("提交配置文件到Git仓库失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void scheduleSyncFromGit(String repositoryId, long intervalSeconds) {
        // 定时同步逻辑待接入 runtime-task 统一调度（禁止本模块自建调度器）
        logger.info("定时同步Git仓库中的配置文件到数据库: repositoryId={}, interval={}", repositoryId, intervalSeconds);
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

        java.nio.file.Files.write(
                java.nio.file.Paths.get(filePath),
                config.getConfigContent().getBytes("UTF-8"));
    }
}
