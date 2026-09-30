package com.chinacreator.gzcm.sysman.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // PMO-74 H9-T1 匿名面清零（铁律 §1.2① + §2.4-6 默认 DENY）：
                // 原 87 条 permitAll 收敛为 8 条，仅登录/刷新 + 健康检查 + error；
                // 其余（ecos/git、privacy、mfa、sysconfig、datasource、pipeline、llm、
                // policy-engine、task、workspace、integration、kb、ontology、dq…）一律
                // anyRequest().authenticated()。内部无凭证 RestTemplate 调用方（kb-engine
                // /api/v1/llm/{chat,embedding}、data/ontology/kb 的 policy-engine/evaluate、
                // agent-service ontology/graph）须由 H10 补服务凭证，不在此放开匿名。
                .requestMatchers(
                    "/auth/**",
                    "/api/v1/auth/**",
                    "/api/v1/engine/*/health",
                    "/api/v1/knowledge/health",
                    "/api/health",
                    "/health",
                    "/actuator/health",
                    "/error"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
