package com.chinacreator.gzcm.sysman.security;

import com.chinacreator.gzcm.common.security.registry.AnonymousEndpointRegistry;
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
                // H9-T1 匿名面清零 + W05（详细设计-00 C.3.2）：permitAll 不再内联清单，
                // 一律由 AnonymousEndpointRegistry 单源生成（ANONYMOUS 全形态），
                // 未登记即默认 DENY；新增匿名面须先登记（理由+批准人+日期），
                // 门禁 = AnonymousEndpointInventoryTest 三处集合比对。
                .requestMatchers(
                    AnonymousEndpointRegistry.permitAllPatterns().toArray(String[]::new)
                ).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
