package com.chinacreator.gzcm.gateway.interceptor;

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

import com.chinacreator.gzcm.common.context.ServerOrgAttribute;
import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;

/**
 * 仓库属主机构守卫门（§2.9.9 G8；#147G8）。
 *
 * <p>gateway 测试类路径没有 Mockito（只有 junit / spring-test / archunit），故用最小手写假实现。
 * 覆盖六条：① 无 {@code repoId} ⇒ 放行；② 全局仓库（owner 空）⇒ 放行；③ 机构匹配 ⇒ 放行；
 * ④ 机构不匹配 ⇒ 403 + 机读码；⑤ 拿不到服务端机构 ⇒ 403（fail-closed）；
 * ⑥ 注册表不可用/查询异常 ⇒ 503（禁「查不到就放行」）；外加超管旁路与「客户端头不作依据」反证。</p>
 *
 * @author ECOS Gateway #147G8
 */
class GitRepoOrgGuardTest {

    /** 最小假实现：只实现守卫实际调用的 getRepositoryById，其余按未装配口径抛。 */
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

    private static MockHttpServletRequest request(String repoId, String serverOrgId, String clientOrgHeader) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/ecos/git/status");
        if (repoId != null) {
            req.setParameter("repoId", repoId);
        }
        if (serverOrgId != null) {
            ServerOrgAttribute.set(req, serverOrgId);
        }
        if (clientOrgHeader != null) {
            req.addHeader("X-Org-Id", clientOrgHeader);
        }
        return req;
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private static void asSuperAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
    }

    @Test
    @DisplayName("无 repoId 参数：放行，交给端点自己报 400/404")
    void passesWhenRepoIdAbsent() throws Exception {
        GitRepoOrgGuard guard = new GitRepoOrgGuard(provider(null));
        assertTrue(guard.preHandle(request(null, null, null), new MockHttpServletResponse(), new Object()));
    }

    @Test
    @DisplayName("全局仓库（owner 空）：放行——V308 之前语义，禁读成禁止")
    void passesForGlobalRepository() throws Exception {
        GitRepoOrgGuard guard = new GitRepoOrgGuard(provider(new FakeRepoService(repo(null), false)));
        assertTrue(guard.preHandle(request("ontology-model", null, null), new MockHttpServletResponse(), new Object()));
    }

    @Test
    @DisplayName("机构匹配：放行")
    void passesWhenOrgMatches() throws Exception {
        GitRepoOrgGuard guard = new GitRepoOrgGuard(provider(new FakeRepoService(repo("org-1"), false)));
        assertTrue(guard.preHandle(request("ontology-model", "org-1", null), new MockHttpServletResponse(), new Object()));
    }

    @Test
    @DisplayName("机构不匹配：403 + 机读码（客户端头不得成为依据）")
    void deniesWhenOrgMismatches() throws Exception {
        GitRepoOrgGuard guard = new GitRepoOrgGuard(provider(new FakeRepoService(repo("org-1"), false)));
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean allowed = guard.preHandle(request("ontology-model", "org-2", "org-1"), res, new Object());

        assertFalse(allowed);
        assertEquals(403, res.getStatus());
        assertTrue(res.getContentAsString().contains(GitRepoOrgGuard.CODE_ORG_DENIED),
                "响应须带机读码，禁止空 403");
    }

    @Test
    @DisplayName("拿不到服务端机构：403（fail-closed，不因缺上下文放行）")
    void deniesWhenServerOrgMissing() throws Exception {
        GitRepoOrgGuard guard = new GitRepoOrgGuard(provider(new FakeRepoService(repo("org-1"), false)));
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertFalse(guard.preHandle(request("ontology-model", null, "org-1"), res, new Object()));
        assertEquals(403, res.getStatus());
    }

    @Test
    @DisplayName("注册表不可用/查询异常：503（禁「查不到就放行」）")
    void deniesWith503WhenRegistryUnavailable() throws Exception {
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        assertFalse(new GitRepoOrgGuard(provider(null))
                .preHandle(request("ontology-model", "org-1", null), res1, new Object()));
        assertEquals(503, res1.getStatus());

        MockHttpServletResponse res2 = new MockHttpServletResponse();
        assertFalse(new GitRepoOrgGuard(provider(new FakeRepoService(null, true)))
                .preHandle(request("ontology-model", "org-1", null), res2, new Object()));
        assertEquals(503, res2.getStatus());
        assertTrue(res2.getContentAsString().contains(GitRepoOrgGuard.CODE_ORG_UNAVAILABLE));
    }

    @Test
    @DisplayName("超管旁路：与权限切面同口径")
    void superAdminBypassesOrgCheck() throws Exception {
        GitRepoOrgGuard guard = new GitRepoOrgGuard(provider(new FakeRepoService(repo("org-9"), false)));
        asSuperAdmin();

        assertTrue(guard.preHandle(request("ontology-model", "org-1", null), new MockHttpServletResponse(), new Object()));
    }
}
