package com.chinacreator.gzcm.services.datanet.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Git 仓库「属主机构」守卫 —— 落在<b>真实宿主</b> datanet（§2.9.9 G8 执行面；#147G8）。
 *
 * <p><b>为什么在 datanet</b>：{@code /api/v1/ecos/git/**} 的 Controller 在 {@code services/datanet}
 * （gateway 不扫该包，实测经网关直查为 404）——守卫挂在 gateway 等于永不执行（已按「禁假绿」撤回）。
 * 网关现经转发面把请求送到本服务，并注入受信内部头（见 gateway {@code GitDownstreamForwardFilter}）。</p>
 *
 * <p><b>机构值只认内部头</b>：{@link #INTERNAL_ORG_HEADER} 由上游（gateway）在服务端解析后注入，
 * 客户端 {@code X-Org-Id} 在网关已被剥离；本服务<b>不</b>自行解析机构（无 sysman 判据面），
 * 也<b>不</b>接受任何客户端传入的 org 值。缺头 = 拿不到机构 ⇒ 对非全局仓库 <b>403</b>（fail-closed）。</p>
 *
 * <p><b>四条判定</b>：① {@code ownerOrgId} 空 = 全局仓库 ⇒ 放行；② 内部头机构 == 属主 ⇒ 放行；
 * ③ 超管（{@code ROLE_SUPER_ADMIN}/{@code admin}）⇒ 放行；④ 其余 403；注册表不可用 503。禁「查不到就放行」。</p>
 *
 * @author ECOS Datanet #147G8
 */
public class GitRepoOrgGuard implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GitRepoOrgGuard.class);

    /** 受信内部机构头：仅限服务间内网传递（ADR-7），网关注入、下游只读。 */
    public static final String INTERNAL_ORG_HEADER = "X-ECOS-Server-Org";

    public static final String CODE_ORG_DENIED = "ECOS-GIT-403-ORG";
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
            return true;
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
            return true;
        }
        if (isSuperAdmin()) {
            return true;
        }
        String internalOrg = request.getHeader(INTERNAL_ORG_HEADER);
        if (internalOrg != null && internalOrg.trim().equals(ownerOrgId.trim())) {
            return true;
        }
        log.warn("仓库机构不匹配: repoId={}, owner={}, internalOrg={}", repoId, ownerOrgId, internalOrg);
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
