package com.chinacreator.gzcm.workspace.it;

import com.chinacreator.gzcm.workspace.controller.ScenarioController;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 卷07 F07-01 / F07-24（W165+W188）—— workspace 端点<b>承流口径</b>可达性与鉴权 IT。
 *
 * <p>与 {@code GatewayRouteIntegrityTest} 的分工：后者只看源码声明（制品口径），
 * 本类看 {@link RequestMappingHandlerMapping} 的<b>运行期注册表</b>（承流口径），
 * 端点清单来自反射枚举而非 regex —— 这正是 X-12 假阳性的解药：
 * gateway fat-JAR 用 REGEX 排除 {@code workspace.controller..*}（ADR-15），
 * 制品在 classpath 但注册表里没有，静态扫描照样"命中"。</p>
 *
 * <p><b>状态：未执行</b>（§八.4 前置件 P-2）。跑通它需要 7-JAR 活栈
 * （gateway :8080 + workspace :18090 + PG/Neo4j/Kafka/MinIO 容器 + 真实 JWT），
 * 本机离线 {@code mvn -o} 无该条件。类与方法必须存在且编译通过，
 * 但任何"跑过了"的声称都违例。</p>
 *
 * <p><b>已记录的口径缺口（两条，交主 Agent 定）</b>：
 * <ol>
 *   <li>本类位于 workspace-impl 测试源集，而 workspace-impl 不依赖 gateway 模块
 *       （gateway 反向依赖 workspace-impl，加依赖即成环），
 *       因此 {@code @SpringBootTest} 只能起 workspace 侧切片，
 *       "throughGateway" 那一腿必须把本类移到 {@code gateway/src/test/java}
 *       （classes=GatewayApplication.class）才名副其实。</li>
 *   <li>C-4 矩阵要求的是 {@code /api/v1/workspace/**}；workspace 进程实际还承载
 *       {@code /api/v1/ecos/objects}、{@code /api/query}、{@code /api/workbook}、
 *       {@code /api/v1/knowledge} 四组前缀（R-29 已裁决"留在 workspace 进程"，共 36 端点），
 *       但这四组前缀与 gateway 在册的其他引擎 controller <b>同前缀冲突</b>
 *       （如 {@code /api/v1/ecos/**} 由 ontology-engine-impl 承载），
 *       故 gateway 反向代理只能按 {@code /api/v1/workspace/**} 单前缀切流。</li>
 * </ol>
 */
@SpringBootTest(classes = WorkspaceRouteReachabilityIT.WorkspaceReachabilityTestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    // 承流面自检不需要真实库；排除 DataSource/MyBatis，端点注册表照样完整
    "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration"
})
@Disabled("NOT-EXECUTED: live gateway+workspace container stack required; harness compiles only — see §8.4 P-2")
class WorkspaceRouteReachabilityIT {

    private static final String WORKSPACE_PREFIX = "/api/v1/workspace";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private Environment env;

    /** 反射枚举：注册表里 workspace 前缀下的端点必须非空且逐个可请求（GET 探针禁 404）。 */
    @Test
    void everyWorkspaceEndpointIsReachableThroughGatewayAndNot404() {
        List<String> endpoints = enumeratedWorkspaceEndpoints();
        assertFalse(endpoints.isEmpty(),
            "RequestMappingHandlerMapping 未注册任何 " + WORKSPACE_PREFIX + " 端点 —— "
                + "制品在 classpath 不等于承流（ADR-15 排除的直接后果）");

        List<String> notFound = new ArrayList<>();
        for (String path : endpoints) {
            ResponseEntity<String> resp = rest.exchange(path, HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), String.class);
            // 401/403/400/405/409/503 都是"路由已接通"的证据；404 才是断链
            if (resp.getStatusCode() == HttpStatus.NOT_FOUND) {
                notFound.add(path);
            }
        }
        assertTrue(notFound.isEmpty(), "以下 workspace 端点在承流面上 404: " + notFound);
    }

    /** 鉴权等价红线：匿名请求必须被拒（401/403），<b>不接受 200</b>（X-19 裸奔）。 */
    @Test
    void unauthenticatedWorkspaceRequestIsDenied() {
        List<String> endpoints = enumeratedWorkspaceEndpoints();
        assertFalse(endpoints.isEmpty(), "无端点可测");
        List<String> leaked = new ArrayList<>();
        for (String path : endpoints) {
            ResponseEntity<String> resp = rest.getForEntity(path, String.class);
            boolean denied = resp.getStatusCode() == HttpStatus.UNAUTHORIZED
                || resp.getStatusCode() == HttpStatus.FORBIDDEN;
            if (!denied) {
                leaked.add(path + " -> " + resp.getStatusCode());
            }
        }
        assertTrue(leaked.isEmpty(),
            "匿名请求未被拒绝（:18090 排除 SecurityAutoConfiguration / header-auth 误置的表征）: " + leaked);
    }

    /**
     * 同一端点：无 token → 403，带 token → 非 401/403。
     * token 需真实登录链路，故本方法在活栈下才可能有意义。
     */
    @Test
    void anonymousRequestIs403AndTokenRequestIs200() {
        String path = WORKSPACE_PREFIX + "/scenarios";
        ResponseEntity<String> anon = rest.getForEntity(path, String.class);
        assertEquals(HttpStatus.FORBIDDEN, anon.getStatusCode(),
            "无 Authorization 头的场景列表请求应 403（默认 DENY），实际: " + anon.getStatusCode());

        String token = obtainTokenFromLiveStack();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> authed =
            rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertTrue(authed.getStatusCode() != HttpStatus.FORBIDDEN
                && authed.getStatusCode() != HttpStatus.UNAUTHORIZED,
            "带合法 JWT 的请求不应被鉴权层拒绝，实际: " + authed.getStatusCode());
    }

    /** 承流面就绪后由真实登录端点提供；此处保持显式不可用，禁止伪造 token 让用例假绿。 */
    private String obtainTokenFromLiveStack() {
        throw new IllegalStateException(
            "P-2 活栈未就绪：token 必须由 " + env.getProperty("server.port", "?") + " 侧真实登录链路签发");
    }

    /** 端点清单 = handlerMapping 反射枚举（禁源码 regex，X-12）。 */
    private List<String> enumeratedWorkspaceEndpoints() {
        assertNotNull(handlerMapping, "RequestMappingHandlerMapping bean 必须存在");
        Set<RequestMappingInfo> infos = handlerMapping.getHandlerMethods().keySet();
        List<String> out = new ArrayList<>();
        for (RequestMappingInfo info : infos) {
            if (info.getPathPatternsCondition() == null) continue;
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (pattern.startsWith(WORKSPACE_PREFIX)) {
                    // 路径变量无法直接请求，跳过模板段（探针只走可解析路径）
                    if (!pattern.contains("{")) {
                        out.add(pattern);
                    }
                }
            }
        }
        assertTrue(!out.isEmpty(), "枚举结果为空，说明 controller 包未被注册");
        return out;
    }

    /**
     * workspace 侧承流切片：只扫 {@code workspace.controller} 包（即 gateway 被排除的那一层），
     * 用来证明"注册表里有没有"这件事与 datasource/kafka 无关。
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackageClasses = ScenarioController.class)
    static class WorkspaceReachabilityTestApp {
    }
}
