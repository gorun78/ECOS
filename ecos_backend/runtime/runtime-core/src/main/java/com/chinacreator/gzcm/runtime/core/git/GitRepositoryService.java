package com.chinacreator.gzcm.runtime.core.git;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.git.entity.GitRepository;

/**
 * Runtime 閸忣剙鍙?Git 娴犳挸绨辩粻锛勬倞閹恒儱褰涢妴?
 */
public interface GitRepositoryService {

    GitRepository getDefaultRepository() throws GitRepositoryException;

    GitRepository getRepositoryById(String repositoryId) throws GitRepositoryException;

    List<GitRepository> listRepositories() throws GitRepositoryException;

    void registerRepository(GitRepository repository) throws GitRepositoryException;

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


