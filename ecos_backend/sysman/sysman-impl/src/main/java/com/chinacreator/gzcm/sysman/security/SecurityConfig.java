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
                .requestMatchers(
                    // ── Auth (public)
                    "/auth/**",
                    "/api/v1/auth/**",
                    "/api/security/**",
                    // ── Public read-only domains
                    "/api/v1/ecos/**",
                    "/api/v1/security/**",
                    "/api/v1/marketplace/**",
                    "/api/v1/agent/**",
                    "/api/v1/agent-loop/**",
                    "/api/v1/agent-mesh/**",
                    "/api/v1/knowledge/**",
                    "/api/v1/glossary/**",
                    "/api/v1/catalog/**",
                    "/api/v1/oag/**",
                    // ── System management
                    "/api/v1/system/**",
                    "/api/v1/audit/**",
                    "/api/v1/data-masking/**",
                    "/api/v1/dict/**",
                    "/api/v1/policy-engine/**",
                    "/api/v1/pipeline/**",
                    "/api/v1/dq/**",
                    "/api/v1/query/**",
                    "/api/v1/causal/**",
                    "/api/v1/monitor/**",
                    "/api/v1/twins/**",
                    "/api/v1/pareto/**",
                    "/api/v1/portal/**",
                    "/api/v1/integration/**",
                    "/api/v1/ontology/**",
                    "/api/v1/lineage/**",
                    "/api/v1/guardrails/**",
                    "/api/v1/aip/**",
                    "/api/v1/agents/**",
                    "/api/v1/abac/**",
                    "/api/v1/data-permission/**",
                    "/api/v1/mfa/**",
                    "/api/v1/privacy/**",
                    "/api/v1/world-model/**",
                    "/api/v1/worldmodel/**",
                    "/api/v1/agent-runtime/**",
                    "/api/v1/evolution/**",
                    "/api/v1/cognitive/**",
                    "/api/v1/engine/**",
                    // ── Cases
                    "/api/v1/kb/**",
                    "/api/v1/rules/**",
                    "/cases/**",
                    // ── Alerts + WebSocket
                    "/api/v1/alerts/**",
                    "/api/alerts/**",
                    "/ws/**",
                    // ── Infrastructure
                    "/api/health",
                    "/health",
                    "/actuator/health",
                    "/error",
                    // ── Datanet (proxy path, no /api/ prefix)
                    "/datanet/**",
                    "/api/v1/datanet/**",
                    // ── Data Lake / Workbook
                    "/api/datalake/**",
                    "/api/workbook/**",
                    // ── Task
                    "/api/v1/task/**",
                    // ── Workspace
                    "/api/v1/workspace/**",
                    // ── Agent tools
                    "/api/agent/tools/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
