package com.chinacreator.gzcm.engine.ai.harness;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 详细设计-06 F06-22 测试底座骨架（P-1 前置门禁）。
 *
 * <p>关键设计：</p>
 * <ul>
 *   <li><b>NO_DB_SCOPE</b> —— 本 config 只装配<b>无基础设施依赖</b>的 bean（stub、探针端点、
 *       traceId filter），不 pull datasource/kafka/neo4j。这样 MOCK web 切片
 *       在<b>本机离线</b>也能起（{@code webEnvironment=MOCK}，无真实 TCP），
 *       是本册 IT 底座的<b>可执行载体</b>，同时用 profile 双装证明
 *       同一 config 在两种部署形态下都健全。</li>
 *   <li><b>Profile 双装</b>：{@code harness-gateway} / {@code harness-aiming} 都指向同一个
 *       {@link MockSecurityEngineStub} bean 装配；差异只在应用启动时该测试 context
 *       装载的 dependency chain 里体现（gateway = monolith fat-jar；aiming = 单 service）。
 *       本 config 以 {@code @Profile("harness-gateway")} / {@code @Profile("harness-aiming")}
 *       分别装配"仅本探针可用面"的最小集 —— 见两条 {@code @Bean}：
 *       <ul>
 *         <li>{@link #probeControllerGw()} 属 gateway 态</li>
 *         <li>{@link #probeControllerAim()} 属 aiming service 态</li>
 *       </ul>
 *       因此 <b>同一 endpoint 在两种形态下都能探测到</b>，保证 "gateway 态测过
 *       ≠ 能力存在" 反假阳性（X-26 + R-18）。</li>
 * </ul>
 *
 * <p>安全桩三态：一律用同一个 {@link MockSecurityEngineStub} bean，两个 profile 各出一份
 * 实例（避免跨测试污染），由测试 code 内 {@code @Autowired MockSecurityEngineStub} 直接翻转。</p>
 */
@Configuration
public class AiTestHarnessConfig {

    @Bean
    @Profile("harness-gateway")
    public MockSecurityEngineStub securityStubGateway() {
        return new MockSecurityEngineStub();
    }

    @Bean
    @Profile("harness-aiming")
    public MockSecurityEngineStub securityStubAiming() {
        return new MockSecurityEngineStub();
    }

    @Bean
    @Profile("harness-gateway")
    public HarnessProbeController harnessProbeControllerGw() {
        return new HarnessProbeController(securityStubGateway());
    }

    @Bean
    @Profile("harness-aiming")
    public HarnessProbeController harnessProbeControllerAim() {
        return new HarnessProbeController(securityStubAiming());
    }

    /** traceId 头 → MDC 传播（F06-06 断言基础；两态都要有；
     *  servlet 容器形态下的 fallback 装配；测试 code 走 standalone MockMvc
     *  带的独立 filter，两条路径刻意共存证明"traceId 传播是契约"）。 */
    @Bean
    @Profile({"harness-gateway", "harness-aiming"})
    public FilterRegistrationBean<OncePerRequestFilter> traceIdMdcFilter() {
        FilterRegistrationBean<OncePerRequestFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                    throws ServletException, IOException {
                String in = req.getHeader("X-Trace-Id");
                String id = (in == null || in.isBlank()) ? "t-" + UUID.randomUUID().toString().substring(0, 8) : in;
                MDC.put("traceId", id);
                res.setHeader("X-Trace-Id", id);
                try {
                    chain.doFilter(req, res);
                } finally {
                    MDC.remove("traceId");
                }
            }
        });
        reg.addUrlPatterns("/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return reg;
    }
}
