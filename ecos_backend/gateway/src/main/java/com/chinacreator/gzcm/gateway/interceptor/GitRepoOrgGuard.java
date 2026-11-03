package com.chinacreator.gzcm.gateway.interceptor;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.context.ServerOrgAttribute;
import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Git 仓库「属主机构」守卫（§2.9.9 G8 组织权限矩阵的执行面；#147G8）。
 *
 * <p><b>解决什么</b>：{@code ecos_git_repo} 原先只有 create_by/update_by（"谁建的"），
 * 没有任何机构归属；任何拿到 {@code repoId} 的调用方都能对任意仓库读写。V308 补
 * {@code owner_org_id} 载体后，本守卫在**唯一的 Git REST 入口**（{@code /api/v1/ecos/git/**}）
 * 做机构比对。</p>
 *
 * <p><b>四条判定（写死，禁静默放宽）</b>：</p>
 * <ol>
 *   <li>{@code ownerOrgId} 为空 = 全局仓库 ⇒ 放行（V308 之前的既有语义，禁读成"禁止"）；</li>
 *   <li>服务端解析机构 == 属主机构 ⇒ 放行（机构值只取 {@link ServerOrgAttribute}，客户端头已被剥离）；</li>
 *   <li>超管（{@code ROLE_SUPER_ADMIN}/{@code admin}）⇒ 放行（与权限切面同一旁路口径）；</li>
 *   <li>其余（含拿不到机构）⇒ <b>403</b>；注册表不可用时 <b>503</b>——一律 fail-closed，禁"查不到就放行"。</li>
 * </ol>
 *
 * @author ECOS Gateway #147G8
 */
@Component
public class GitRepoOrgGuard implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GitRepoOrgGuard.class);

    /** 拒绝机读码：机构不匹配。 */
    public static final String CODE_ORG_DENIED = "ECOS-GIT-403-ORG";
    /** 拒绝机读码：注册表/属主判定不可用。 */
    public static final String CODE_ORG_UNAVAILABLE = "ECOS-GIT-503-ORG";

    private final ObjectProvider<GitRepositoryService> repositoryService;

    public GitRepoOrgGuard(ObjectProvider<GitRepositoryService> repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String repoId = request.getParameter("repoId");
        if (repoId == null || repoId.isBlank()) {
            return true; // 端点自身会对缺参报 400/404，此处不越俎代庖
        }
        GitRepositoryService service = repositoryService.getIfAvailable();
        if (service == null) {
            return deny(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, CODE_ORG_UNAVAILABLE,
                    "仓库注册表不可用，无法判定机构归属（fail-closed）");
        }
        GitRepository repository;
        try {
            repository = service.getRepositoryById(repoId);
        } catch (Exception e) {
            log.warn("仓库属主判定失败(repoId={})，按不可用拒绝: {}", repoId, e.getMessage());
            return deny(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, CODE_ORG_UNAVAILABLE,
                    "仓库注册表查询失败，无法判定机构归属（fail-closed）");
        }
        String ownerOrgId = repository == null ? null : repository.getOwnerOrgId();
        if (ownerOrgId == null) {
            return true; // 全局仓库：V308 之前的既有语义
        }
        if (isSuperAdmin()) {
            return true;
        }
        String serverOrgId = ServerOrgAttribute.get(request);
        if (serverOrgId != null && serverOrgId.equals(ownerOrgId.trim())) {
            return true;
        }
        log.warn("仓库机构不匹配: repoId={}, owner={}, callerOrg={}", repoId, ownerOrgId, serverOrgId);
        return deny(response, HttpServletResponse.SC_FORBIDDEN, CODE_ORG_DENIED,
                "无权操作该仓库：仓库属主机构不是当前机构");
    }

    private boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String a = authority.getAuthority();
            if ("ROLE_SUPER_ADMIN".equals(a) || "admin".equals(a)) {
                return true;
            }
        }
        return false;
    }

    private boolean deny(HttpServletResponse response, int status, String errorCode, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(ApiResponse.error(status, errorCode, message).withTraceIdFromMdc().toJson());
        return false;
    }
}
