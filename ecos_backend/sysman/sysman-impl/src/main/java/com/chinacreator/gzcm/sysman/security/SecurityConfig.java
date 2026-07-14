package com.chinacreator.gzcm.sysman.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置类。
 * <p>
 * 策略：
 * - 无状态（不创建 Session）
 * - 禁用 CSRF（使用 Token 认证）
 * - 白名单路径免登录（/auth/**、/api/health 等）
 * - 其余请求需要认证
 * - 注册 JwtAuthenticationFilter
 */
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
            // 禁用 CSRF（无状态 Token 认证无需 CSRF）
            .csrf(csrf -> csrf.disable())

            // 无状态 Session
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 请求权限配置
            .authorizeHttpRequests(auth -> auth
                // 白名单路径 — 无需认证
                .requestMatchers(
                    // ── 认证（免登录）
                    "/auth/**",
                    "/api/v1/auth/**",
                    // ── 公开只读
                    "/api/v1/ecos/**",
                    "/api/v1/security/**",
                    "/api/v1/gsxk/**",
                    "/api/marketplace/**",
                    "/api/portal/**",
                    "/api/agent/**",
                    "/api/agent-mesh/**",
                    "/api/knowledge/**",
                    "/api/glossary/**",
                    "/api/v1/worldmodel/**",
                    "/api/v1/marketplace/**",
                    // ── 系统管理（需认证，由 JWT filter 控制）
                    "/api/v1/system/**",
                    "/api/system/**",
                    "/api/v1/audit/**",
                    "/api/v1/data-masking/**",
                    "/api/v1/dict/**",
                    "/api/v1/system/dict/**",
                    // ── OPA 策略引擎
                    "/api/v1/policy-engine/**",
                    "/api/policy/**",
                    // ── P2-4 因果图
                    "/api/causal/**",
                    // ── P2-5 案例库
                    "/cases/**",
                    // ── P2-6 告警 + WebSocket
                    "/api/alerts/**",
                    "/ws/**",
                    // ── 基础设施
                    "/api/health",
                    "/health",
                    "/actuator/health",
                    "/error",
                    // ── 监控
                    "/api/monitor/**",
                    // ── 安全中心
                    "/api/security/**",
                    // ── 数字孪生
                    "/api/twins/**",
                    // ── Datanet
                    "/datanet/**",
                    "/api/datanet/**",
                    // ── P3 Phase 3 健康检查（免认证）
                    "/api/datalake/health",
                    "/api/workbook/health",
                    "/api/twins/health", "/api/v1/cognitive/health",
                    // ── Pipeline
                    "/api/pipeline/**",
                    "/api/v1/pipeline/**",
                    // ── Data Quality
                    "/api/dq/**",
                    // ── Agent Builder
                    "/api/v1/agents/**",
                    "/api/agent/tools/**",
                    // ── Guardrails
                    "/api/v1/guardrails/**",
                    "/api/v1/aip/**",
                    // ── CeosCompat (ontology/integration)
                    "/api/ontology/**",
                    "/api/integration/**",
                    "/api/metadata/**",
                    "/api/lineage/**",
                    // ── v1 路径别名（架构债清理 T5: 旧端点 v1 映射）
                    "/api/v1/dq/**",
                    "/api/v1/agent-mesh/**",
                    "/api/v1/knowledge/**",
                    "/api/v1/twins/**",
                    "/api/v1/alerts/**",
                    "/api/v1/portal/**",
                    "/api/v1/agent/**",
                    "/api/v1/glossary/**",
                    "/api/v1/datanet/**",
                    // ── 临时: task types 免认证测试
                    "/api/v1/task/types",
                    // ── Workspace 场景管理
                    "/api/v1/workspace/**",
                    // ── Ontology Proposal
                    "/api/v1/ontology/proposals/**",
                    // ── Engine endpoints (四引擎健康检查/配置/状态)
                    "/api/v1/engine/**"
                ).permitAll()
                // 其余路径需要认证（包括 P3 业务端点: /api/datalake/**, /api/workbook/**, /api/pareto/**, /api/twins/**, /api/telemetry/**, /api/tenants/**）
                .anyRequest().authenticated()
            )

            // 注册 JWT 过滤器（在 UsernamePasswordAuthenticationFilter 之前）
            .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
