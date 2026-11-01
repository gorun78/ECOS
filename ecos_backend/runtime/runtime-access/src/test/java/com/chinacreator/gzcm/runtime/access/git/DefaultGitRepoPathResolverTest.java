package com.chinacreator.gzcm.runtime.access.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;

/** 最后一跳仓库路径解析（#147E）：根兜底、repo_root 优先、穿越拒绝。 */
class DefaultGitRepoPathResolverTest {

    private static GitRepository repo(String id, String root) {
        GitRepository r = new GitRepository();
        r.setRepositoryId(id);
        r.setRepoRoot(root);
        return r;
    }

    @Test
    void resolvesUnderConfiguredRootAndPrefersRepoRoot() {
        System.setProperty(DefaultGitRepoPathResolver.PROP_REPO_ROOT, "/tmp/ecos-repos");
        try {
            String path = new DefaultGitRepoPathResolver().resolveLocalPath(repo("kb-asset-tree", "kb"));
            assertTrue(path.replace('\\', '/').endsWith("/tmp/ecos-repos/kb"),
                    "repo_root 优先于 repositoryId: " + path);
        } finally {
            System.clearProperty(DefaultGitRepoPathResolver.PROP_REPO_ROOT);
        }
    }

    @Test
    void fallsBackToRepositoryIdWhenRepoRootBlank() {
        System.setProperty(DefaultGitRepoPathResolver.PROP_REPO_ROOT, "/tmp/r");
        try {
            String path = new DefaultGitRepoPathResolver().resolveLocalPath(repo("scenario-def", " "));
            assertTrue(path.replace('\\', '/').endsWith("/tmp/r/scenario-def"));
        } finally {
            System.clearProperty(DefaultGitRepoPathResolver.PROP_REPO_ROOT);
        }
    }

    @Test
    void rejectsTraversalSegments() {
        System.setProperty(DefaultGitRepoPathResolver.PROP_REPO_ROOT, "/tmp/r");
        try {
            DefaultGitRepoPathResolver resolver = new DefaultGitRepoPathResolver();
            assertNull(resolver.resolveLocalPath(repo("x", "../etc")));
            assertNull(resolver.resolveLocalPath(repo("x", "a/b")));
            assertNull(resolver.resolveLocalPath(null));
        } finally {
            System.clearProperty(DefaultGitRepoPathResolver.PROP_REPO_ROOT);
        }
    }

    @Test
    void defaultRootIsUserHomeEcosGitRepos() {
        System.clearProperty(DefaultGitRepoPathResolver.PROP_REPO_ROOT);
        assertEquals(java.nio.file.Paths.get(System.getProperty("user.home"), "ecos-git-repos").toString(),
                DefaultGitRepoPathResolver.repoRoot());
    }
}
