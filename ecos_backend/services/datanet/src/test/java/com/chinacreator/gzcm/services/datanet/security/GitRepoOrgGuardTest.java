package com.chinacreator.gzcm.services.datanet.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;

/**
 * 仓库属主机构守卫门（§2.9.9 G8 执行面；#147G8）。
 *
 * <p>七条：无 {@code repoId} 放行 / 全局仓库放行 / 内部头机构匹配放行 / 不匹配 403 /
 * 缺内部头 403（fail-closed）/ 注册表不可用或异常 503 / 超管旁路；外加「客户端 {@code X-Org-Id}
 * 不参与判定」反证（守卫只读内部头）。</p>
 *
 * @author ECOS Datanet #147G8
 */
class GitRepoOrgGuardTest {

    private static final class FakeRepoService implements GitRepositoryService {
        private final GitRepository repository;
        private final boolean broken;

        FakeRepoService(GitRepository repository, boolean broken) {
            this.repository = repository;
            this.broken = broken;
        }

        @Override
        public GitRepository getDefaultRepository() {
            return null;
        }

        @Override
        public GitRepository getRepositoryById(String repositoryId) throws GitRepositoryException {
            if (broken) {
                throw new GitRepositoryException("db down");
            }
            return repository;
        }

        @Override
        public List<GitRepository> listRepositories() {
            return List.of();
        }

        @Override
        public void registerRepository(GitRepository repository) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void unregisterRepository(String repositoryId) {
            throw new UnsupportedOperationException("not used");
        }
    }

    private static ObjectProvider<GitRepositoryService> provider(GitRepositoryService service) {
        return new ObjectProvider<>() {
            @Override
            public GitRepositoryService getObject(Object... args) {
                return service;
            }

            @Override
            public GitRepositoryService getObject() {
                return service;
            }

            @Override
            public GitRepositoryService getIfAvailable() {
                return service;
            }

            @Override
            public GitRepositoryService getIfUnique() {
                return service;
            }
        };
    }

    private static GitRepository repo(String ownerOrgId) {
        GitRepository r = new GitRepository();
        r.setRepositoryId("ontology-model");
        r.setOwnerOrgId(ownerOrgId);
        return r;
    }

    private static MockHttpServletRequest request(String repoId, String internalOrg, String clientOrgId) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/ecos/git/status");
        if (repoId != null) {
            req.setParameter("repoId", repoId);
        }
        if (internalOrg != null) {
            req.addHeader(GitRepoOrgGuard.INTERNAL_ORG_HEADER, internalOrg);
        }
        if (clientOrgId != null) {
            req.addHeader("X-Org-Id", clientOrgId);
        }
        return req;
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("无 repoId：放行（交给端点自报 400/404）")
    void passesWithoutRepoId() throws Exception {
        GitRepoOrgGuard guard = new GitRepoOrgGuard(provider(null));
        assertTrue(guard.preHandle(request(null, null, null), new MockHttpServletResponse(), new Object()));
    }

    @Test
    @DisplayName("全局仓库（owner 空）：放行")
    void passesForGlobalRepository() throws Exception {
        GitRepoOrgGuard guard = new GitRepoOrgGuard(provider(new FakeRepoService(repo(null), false)));
        assertTrue(guard.preHandle(request("ontology-model", null, null), new MockHttpServletResponse(), new Object()));
    }

    @Test
    @DisplayName("内部头机构匹配：放行")
    void passesWhenInternalOrgMatches() throws Exception {
        GitRepoOrgGuard guard = new GitRepoOrgGuard(provider(new FakeRepoService(repo("org-1"), false)));
        assertTrue(guard.preHandle(request("ontology-model", "org-1", null), new MockHttpServletResponse(), new Object()));
    }

    @Test
    @DisplayName("机构不匹配：403 + 机读码；客户端 X-Org-Id 不参与判定")
    void deniesOnMismatchAndIgnoresClientHeader() throws Exception {
        GitRepoOrgGuard guard = new GitRepoOrgGuard(provider(new FakeRepoService(repo("org-1"), false)));
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean allowed = guard.preHandle(request("ontology-model", "org-2", "org-1"), res, new Object());

        assertFalse(allowed, "客户端头即便写成属主机构也不得改变判定");
        assertEquals(403, res.getStatus());
        assertTrue(res.getContentAsString().contains(GitRepoOrgGuard.CODE_ORG_DENIED));
    }

    @Test
    @DisplayName("缺内部头：403（拿不到机构不得放行）")
    void deniesWithoutInternalHeader() throws Exception {
        GitRepoOrgGuard guard = new GitRepoOrgGuard(provider(new FakeRepoService(repo("org-1"), false)));
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertFalse(guard.preHandle(request("ontology-model", null, "org-1"), res, new Object()));
        assertEquals(403, res.getStatus());
    }

    @Test
    @DisplayName("注册表不可用/异常：503（禁查不到就放行）")
    void deniesWith503WhenRegistryUnavailable() throws Exception {
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        assertFalse(new GitRepoOrgGuard(provider(null))
                .preHandle(request("ontology-model", "org-1", null), res1, new Object()));
        assertEquals(503, res1.getStatus());

        MockHttpServletResponse res2 = new MockHttpServletResponse();
        assertFalse(new GitRepoOrgGuard(provider(new FakeRepoService(null, true)))
                .preHandle(request("ontology-model", "org-1", null), res2, new Object()));
        assertEquals(503, res2.getStatus());
    }

    @Test
    @DisplayName("超管旁路：与权限切面同口径")
    void superAdminBypasses() throws Exception {
        GitRepoOrgGuard guard = new GitRepoOrgGuard(provider(new FakeRepoService(repo("org-9"), false)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));

        assertTrue(guard.preHandle(request("ontology-model", "org-1", null), new MockHttpServletResponse(), new Object()));
    }
}
