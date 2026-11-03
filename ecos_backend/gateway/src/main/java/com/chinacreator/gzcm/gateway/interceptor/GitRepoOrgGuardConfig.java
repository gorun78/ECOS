package com.chinacreator.gzcm.gateway.interceptor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 仓库属主机构守卫的挂载（§2.9.9 G8；#147G8）。
 *
 * <p>只在 Git REST 唯一入口上生效；两条路径形态都挂（{@code /api/v1/...} 与版本前缀重写前的
 * {@code /api/...}），与网关路由重写表的口径一致。</p>
 *
 * @author ECOS Gateway #147G8
 */
@Configuration
public class GitRepoOrgGuardConfig implements WebMvcConfigurer {

    private final GitRepoOrgGuard guard;

    public GitRepoOrgGuardConfig(GitRepoOrgGuard guard) {
        this.guard = guard;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(guard)
                .addPathPatterns("/api/v1/ecos/git/**", "/api/ecos/git/**");
    }
}
