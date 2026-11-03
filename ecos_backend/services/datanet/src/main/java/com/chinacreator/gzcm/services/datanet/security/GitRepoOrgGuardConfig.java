package com.chinacreator.gzcm.services.datanet.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService;

/**
 * Git 属主机构守卫挂载（§2.9.9 G8；#147G8）。
 *
 * <p>datanet 原先没有 MVC 配置类；本类只做两件事：注册守卫到一个路径，和把守卫装配成 Bean。
 * 路径同时覆盖 {@code /api/v1/ecos/git/**} 与 {@code /api/ecos/git/**}（版本前缀重写前后两种形态）。</p>
 *
 * @author ECOS Datanet #147G8
 */
@Configuration
public class GitRepoOrgGuardConfig implements WebMvcConfigurer {

    private final GitRepoOrgGuard guard;

    public GitRepoOrgGuardConfig(org.springframework.beans.factory.ObjectProvider<GitRepositoryService> repoService) {
        this.guard = new GitRepoOrgGuard(repoService);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(guard)
                .addPathPatterns("/api/v1/ecos/git/**", "/api/ecos/git/**");
    }
}
