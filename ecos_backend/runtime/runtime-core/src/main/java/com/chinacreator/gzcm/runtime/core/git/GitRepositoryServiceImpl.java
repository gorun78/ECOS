package com.chinacreator.gzcm.runtime.core.git;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.runtime.core.git.entity.GitRepository;

/**
 * Git仓库服务实现
 * 提供Git仓库管理功能
 * 
 * @author CDRC Runtime Team
 */
@Service
public class GitRepositoryServiceImpl implements GitRepositoryService {
    
    // 仓库存储：repositoryId -> GitRepository
    private final ConcurrentMap<String, GitRepository> repositoryStore = new ConcurrentHashMap<>();
    
    // 默认仓库ID
    private String defaultRepositoryId = null;

    @Override
    public GitRepository getDefaultRepository() throws GitRepositoryException {
        if (defaultRepositoryId == null) {
            // 如果没有默认仓库，返回第一个仓库或创建默认仓库
            if (repositoryStore.isEmpty()) {
            GitRepository defaultRepo = new GitRepository();
            defaultRepo.setRepositoryId("default");
            defaultRepo.setName("Default Repository");
            defaultRepo.setLocalPath("/default/repo");
            registerRepository(defaultRepo);
            defaultRepositoryId = "default";
            } else {
                defaultRepositoryId = repositoryStore.keySet().iterator().next();
            }
        }
        
        return repositoryStore.get(defaultRepositoryId);
    }

    @Override
    public GitRepository getRepositoryById(String repositoryId) throws GitRepositoryException {
        if (repositoryId == null || repositoryId.trim().isEmpty()) {
            throw new GitRepositoryException("Repository ID cannot be null or empty");
        }
        
        GitRepository repository = repositoryStore.get(repositoryId);
        if (repository == null) {
            throw new GitRepositoryException("Repository with ID " + repositoryId + " not found");
        }
        
        return repository;
    }

    @Override
    public List<GitRepository> listRepositories() throws GitRepositoryException {
        return new ArrayList<>(repositoryStore.values());
    }

    @Override
    public void registerRepository(GitRepository repository) throws GitRepositoryException {
        if (repository == null) {
            throw new GitRepositoryException("Repository cannot be null");
        }
        
        if (repository.getRepositoryId() == null || repository.getRepositoryId().trim().isEmpty()) {
            repository.setRepositoryId(UUID.randomUUID().toString());
        }
        
        if (repositoryStore.containsKey(repository.getRepositoryId())) {
            throw new GitRepositoryException("Repository with ID " + repository.getRepositoryId() + " already exists");
        }
        
        repositoryStore.put(repository.getRepositoryId(), repository);
        
        // 如果这是第一个仓库，设置为默认仓库
        if (defaultRepositoryId == null) {
            defaultRepositoryId = repository.getRepositoryId();
        }
    }
    
    /**
     * 删除仓库
     */
    public void unregisterRepository(String repositoryId) throws GitRepositoryException {
        if (repositoryId == null || repositoryId.trim().isEmpty()) {
            throw new GitRepositoryException("Repository ID cannot be null or empty");
        }
        
        GitRepository removed = repositoryStore.remove(repositoryId);
        if (removed == null) {
            throw new GitRepositoryException("Repository with ID " + repositoryId + " not found");
        }
        
        // 如果删除的是默认仓库，重新设置默认仓库
        if (repositoryId.equals(defaultRepositoryId)) {
            defaultRepositoryId = repositoryStore.isEmpty() ? null : repositoryStore.keySet().iterator().next();
        }
    }
    
    /**
     * 设置默认仓库
     */
    public void setDefaultRepository(String repositoryId) throws GitRepositoryException {
        if (repositoryId == null || repositoryId.trim().isEmpty()) {
            throw new GitRepositoryException("Repository ID cannot be null or empty");
        }
        
        if (!repositoryStore.containsKey(repositoryId)) {
            throw new GitRepositoryException("Repository with ID " + repositoryId + " not found");
        }
        
        defaultRepositoryId = repositoryId;
    }
}

