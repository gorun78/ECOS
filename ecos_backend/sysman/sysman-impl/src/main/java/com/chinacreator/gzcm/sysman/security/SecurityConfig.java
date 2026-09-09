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
                    // ── P0-1: /api/security/** 已移出 permitAll（需认证）
                    // ── Public read-only domains (GET on reference domains only)
                    // M0 改造 (2026-09-01): 移除 3 条过宽 permitAll (暴露敏感数据 + 违反默认 DENY):
                    //   - /api/v1/system/**       → Tenant/Roles/Users/Permissions 不可匿名 (QA T2-003/004/005, T4-005, T5-007/008 5 项)
                    //   - /datanet/** + /api/v1/datanet/**  → 数据源连接池凭据 (username/password) 不可匿名 (QA T3-006)
                    //   - /api/v1/knowledge/**    → 知识正文含敏感业务, 不可匿名 (QA T5-007)
                    // 当前 public 范围: ecos 公开元数据 + marketplace 商品目录 + catalog 元数据 + glossary + oag 公开 query.
                    "/api/v1/ecos/**",
                    "/api/v1/marketplace/**",
                    "/api/v1/agent/**",
                    "/api/v1/agent-loop/**",
                    "/api/v1/agent-mesh/**",
                    // "◄ /api/v1/knowledge/** — 移除 (敏感正文, QA T5-007)"
                    "/api/v1/glossary/**",
                    "/api/v1/catalog/**",
                    "/api/v1/oag/**",
                    // ── System management (M0 改造 移除 /api/v1/system/**)
                    "/api/v1/dict/**",
                    "/api/v1/pipeline/**",
                    "/api/pipeline/**",
                    // PMO-WX 断点调试 Panel：调试会话 / 日志 SSE 端点（T1+T3）
                    "/api/v1/pipeline/debug/**",
                    "/api/pipeline/debug/**",
                    "/api/v1/dq/**",
                    "/api/v1/query/**",
                    "/api/v1/causal/**",
                    "/api/v1/monitor/**",
                    "/api/v1/twins/**",
                    "/api/v1/pareto/**",
                    "/api/v1/portal/**",
                    "/api/portal/**",
                    "/api/cognitive/**",
                    "/api/v1/integration/**",
                    "/api/v1/ontology/**",
                    "/api/v1/lineage/**",
                    "/api/v1/guardrails/**",
                    "/api/v1/aip/**",
                    "/api/v1/agent-metrics/**",
                    "/api/agent-metrics/**",
                    "/api/v1/agents/**",
                    "/api/v1/mfa/**",
                    "/api/v1/privacy/**",
                    "/api/v1/world-model/**",
                    "/api/v1/worldmodel/**",
                    "/api/v1/agent-runtime/**",
                    "/api/v1/evolution/**",
                    "/api/v1/cognitive/**",
                    "/api/v1/engine/**",
                    // ── PMO-38 T5: 新增 4 条 permitAll (sysman/knowledge-bases/sysconfig 三组) ──
                    // ── PMO-39 T5: 双路径全覆盖 — 5 新端点的 /api/ 与 /api/v1/ ──
                    // cognitive T1: /plan, /plan/{id}, /optimize
                    "/api/cognitive/plan",
                    "/api/cognitive/plan/**",
                    "/api/cognitive/optimize",
                    // ontology T2: workflow definitions
                    "/api/engine/ontology/workflow/definitions",
                    "/api/engine/ontology/workflow/definitions/**",
                    // ontology T3: versions diff
                    "/api/ontology/versions/diff",
                    "/api/ontology/versions/diff/**",
                    // portal T4: search
                    "/api/portal/search",
                    "/api/portal/search/**",
                    "/api/v1/knowledge-bases",
                    "/api/knowledge-bases",
                    "/api/v1/sysconfig/**",
                    "/api/sysconfig/**",
                    // ── PMO-40 T5: agent-metrics 双路径 (已有) + ontology auto-discover/preview 裸路径 ──
                    // agent-metrics 双路径已在上方 (第 66-67 行)
                    // ontology sources (GET /api/v1/ecos/ontology/sources) 已被 /api/v1/ecos/** 覆盖
                    // ontology auto-discover preview (POST /api/v1/ecos/domains/{code}/auto-discover/preview) 已被 /api/v1/ecos/** 覆盖
                    // auto-discover preview 裸路径 /api/ecos/domains/ 需单独加
                    "/api/ecos/domains/**",
                    "/api/v1/ecos/domains/**",
                    // PMO-40 T2: metadata strategy 裸路径双路径（v1 路径 /api/v1/datanet/metadata/** 已被 Controller 直接映射）
                    "/api/datanet/metadata/**",
                    // ── PMO-45 T5: datasource 三路径 (PMO45DataSourceController 双路径 /api/v1/datasource + /datasource) ──
                    // Wave2: 补全 /api/v1/datasource/** v1 路径 permitAll (Wave1 只放行 /api/datasource + /datasource)
                    "/api/v1/datasource/**",
                    "/api/datasource/**",
                    "/datasource/**",
                    // ── P0-1: 安全端点移出 permitAll（需认证）
                    // /api/v1/abac/**, /api/v1/audit/**, /api/v1/data-masking/**,
                    // /api/v1/policy-engine/**, /api/v1/data-permission/**,
                    // /api/security/**, /api/v1/security/** — 全部需认证
                    // ── Cases
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
                    // ── Datanet (M0 改造 移除 permits, 数据源凭据不可匿名)
                    // "◄ /datanet/**, /api/v1/datanet/** — 移除 (QA T3-006)"
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
