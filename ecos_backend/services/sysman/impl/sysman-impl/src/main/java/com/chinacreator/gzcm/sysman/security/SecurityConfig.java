package com.chinacreator.gzcm.sysman.security;

import com.chinacreator.gzcm.common.security.anonymous.AnonymousEndpointRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // 详细设计-00 W05 (C.3.2)：匿名端点单一真源 = AnonymousEndpointRegistry。
        // 三处（SecurityConfig permitAll / ClearanceMvcConfig exclude / ClearanceInterceptor 内豁免）
        // 全部由同一份数据生成；新增未登记的 permitAll 由 AnonymousEndpointInventoryTest 判 FAIL。
        List<String> permitAll = AnonymousEndpointRegistry.permitAllPatterns();

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // H9-T1 匿名面：由 AnonymousEndpointRegistry 单源驱动（当前 9 条展开后 ≈ 11 patterns，
                // 含 /api/v1/auth/** 与 /api/auth/** 双形态、health 家族、actuator/health、/error）。
                // 未登记新路径 → 不进 permitAll → anyRequest().authenticated() 默认 DENY。
                .requestMatchers(permitAll.toArray(String[]::new)).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
