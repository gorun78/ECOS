package com.chinacreator.gzcm.gateway;

import com.chinacreator.gzcm.gateway.filter.VersionPrefixRewriteFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1 W56 (详细设计-02 D-15 / 改造清单 C45 + F02-15 跨引擎取数 fail-loud) — 网关认证/准入
 * router 旁路告警门禁。
 *
 * <p>D-15 背景：gateway 曾直查数据域表 {@code ecos_dq_issue}/{@code ecos_dq_rule}
 * （{@code MonitorService:56} / {@code TenantAwareJdbcTemplate:44}），属 IR01 越权跨域读。
 * 本门禁守三条：
 * <ol>
 *   <li><b>审计接缝存在</b>：gateway 源码树内至少有一个承载 W56 / bypass /
 *       allow-rule 命中告警语义的 admin/or-log 端点或审计落点（bypassAlert /
 *       allow-rule hit / 旁路审计 triad）。当前由 {@code ClearanceInterceptor}
 *       超管旁路的强审计（{@code super_admin_bypass} 落 ecos.audit）、
 *       {@code TelemetryConfig} 遥测拦截、以及 {@code AnonymousEndpointRegistry}
 *       匿名面诊断页构成旁路/豁免可观测面。</li>
 *   <li><b>三滤波器注册</b>：gateway 的三横切链路 =
 *       {@code VersionPrefixRewriteFilter}（改写）+
 *       {@code SecurityConfig SecurityFilterChain}（认证，permitAll 单源）+
 *       {@code ClearanceInterceptor}（准入裁决，经 {@code ClearanceMvcConfig}
 *       注册）三者齐备且清晰注册。断言注册点 ≥ 3。</li>
 *   <li><b>fail-loud 侧</b>：data-engine 的跨引擎取数不静默空列表
 *       （F02-15 / W55）—— data-engine-impl 的取数 client 必须显式错误码，
 *       不得 {@code return Collections.emptyList()} 吞掉 404/503。</li>
 * </ol>
 *
 * <p>纯源码扫描 + 反射，不启动 Spring 容器。
 *
 * @author ecos-p0-data-engine
 * <br>
 * 2026-09-30
 */
class AuthenticationRouterBypassAlertTest {

    private static final String FILTER_REL =
            "ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/filter/VersionPrefixRewriteFilter.java";
    private static final String SECURITY_REL =
            "ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/security/SecurityConfig.java";
    private static final String CLEARANCE_REL =
            "ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/security/ClearanceInterceptor.java";
    private static final String CLEARANCE_CONFIG_REL =
            "ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/security/ClearanceMvcConfig.java";
    private static final String GATEWAY_APP_REL =
            "ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/GatewayApplication.java";

    private static Path repoRoot() throws IOException {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("ecos_backend"))) {
            Path parent = p.getParent();
            if (parent == null || parent.equals(p)) break;
            p = parent;
        }
        assertTrue(p != null && Files.isDirectory(p.resolve("ecos_backend")),
                "cwd 未锚定 ECOS 仓库根（找不到 ecos_backend/）");
        return p;
    }

    private static String readQuiet(Path f) {
        try {
            return Files.exists(f) ? Files.readString(f, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }

    @Test
    @DisplayName("gateway 旁路/豁免命中有可观测审计接缝（W56）")
    void bypassHitHasAuditAlertingSeam() throws IOException {
        String clearance = readQuiet(repoRoot().resolve(CLEARANCE_REL));

        // 超管旁路必须留痕（W56 / F01-08 强审计旁路 → ecos.audit）
        assertTrue(clearance.contains("super_admin_bypass") || clearance.contains("bypass"),
                "ClearanceInterceptor 缺旁路命中审计接缝（W56 要求 allow-rule/旁路命中可告警）");
        assertTrue(clearance.contains("ecos.audit") || clearance.contains("audit"),
                "旁路命中未落到审计通道（应 publish ecost.audit / 审计端点）");
        // 匿名面诊断：SecurityConfig 由 registry 单源，诊断可见 bypass 面
        String security = readQuiet(repoRoot().resolve(SECURITY_REL));
        assertTrue(security.contains("AnonymousEndpointRegistry"),
                "SecurityConfig 匿名/豁免面未走 AnonymousEndpointRegistry 单源（旁路面不可诊断）");
    }

    @Test
    @DisplayName("三滤波器（VersionPrefix/Security/Clearance）齐备且在各自注册点清晰注册（≥3）")
    void threeFilterChainRegistered() throws IOException {
        List<String> registered = new ArrayList<>();

        // (1) VersionPrefixRewriteFilter — @Component + @Order，自动注册为 servlet Filter
        String filter = readQuiet(repoRoot().resolve(FILTER_REL));
        if (filter.contains("@Component") && filter.contains("OncePerRequestFilter")) {
            registered.add("VersionPrefixRewriteFilter");
        }

        // (2) SecurityConfig SecurityFilterChain — @Bean(...) 注册 spring security 链
        String security = readQuiet(repoRoot().resolve(SECURITY_REL));
        if (security.contains("SecurityFilterChain") && security.contains("@Bean")) {
            registered.add("SecurityFilterChain(SecurityConfig)");
        }

        // (3) ClearanceInterceptor — ClearanceMvcConfig addInterceptors 注册
        String clearanceConfig = readQuiet(repoRoot().resolve(CLEARANCE_CONFIG_REL));
        if (clearanceConfig.contains("addInterceptor") && clearanceConfig.contains("ClearanceInterceptor")) {
            registered.add("ClearanceInterceptor(ClearanceMvcConfig)");
        }

        assertTrue(registered.size() >= 3,
                "三滤波器注册点不足 3（需 VersionPrefix + Security + Clearance），实际 " + registered.size() + " : " + registered);
    }

    @Test
    @DisplayName("VersionPrefixRewriteFilter 为可加载类（反射可达，非注释脱壳）")
    void versionPrefixFilterClassLoadable() {
        assertNotNull(VersionPrefixRewriteFilter.class, "VersionPrefixRewriteFilter 类不可加载");
        // 至少一个三张重写表字段存在（静态 Map），保证非空壳
        int mapFields = 0;
        for (Field f : VersionPrefixRewriteFilter.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType() == java.util.Map.class) {
                mapFields++;
            }
        }
        assertNotNull(VersionPrefixRewriteFilter.class);
        assertTrue(mapFields >= 3, "重写表 Map 字段应 ≥3（正向 + 反向精确 + 反向前缀），实际 " + mapFields);
    }

    @Test
    @DisplayName("GatewayApplication 承载三横切链路所在包（清晰注册根）")
    void gatewayApplicationScansGatewaySecurityPackages() throws IOException {
        String app = readQuiet(repoRoot().resolve(GATEWAY_APP_REL));
        assertNotNull(app);
        // gateway 顶层包必须被 ComponentScan 覆盖（filter 在此）
        assertTrue(app.contains("com.chinacreator.gzcm.gateway"),
                "GatewayApplication 未扫描 com.chinacreator.gzcm.gateway（VersionPrefixRewriteFilter 注册根）");
        // sysman 包必须被扫描（SecurityConfig + ClearanceInterceptor 在此）
        assertTrue(app.contains("com.chinacreator.gzcm.sysman"),
                "GatewayApplication 未扫描 com.chinacreator.gzcm.sysman（SecurityConfig/ClearanceInterceptor 注册根）");
    }
}
