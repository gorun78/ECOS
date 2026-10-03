package com.chinacreator.gzcm.engine.data;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

/**
 * F02-15 / C.2.3 / D-14（详细设计-02 W55，P0）"跨引擎取数两态寻址 + 禁静默空值" 源码护栏。
 *
 * <p>验收用例 {@code EndpointResolverUsageArchTest}：引擎内<b>实际代码</b>禁止硬编码
 * {@code http://localhost:1808x} 服务地址字面量（走 {@code ServiceEndpointResolver} 两态寻址，
 * monolith :8080 / service :180xx，由 {@code ecos.route.<service>} 控制，默认 monolith）。
 * 禁硬编码字面量是让 D-14"默认指向未运行的 :18082 并静默降级"结构性不可能复现的红线守卫。</p>
 *
 * <p>剔除注释后扫描（复用 {@link RlsInjectionGuardArchTest#stripComments}）：Javadoc / 行注释里
 * 的 {@code localhost:1808x} 说明（如 DqRcaService 的头注释）不算"实际代码"，不触发；只有
 * 非注释代码中出现即 FAIL。</p>
 */
class EndpointResolverUsageArchTest {

    /** D-14/F02-15 专用红线：引擎内代码（非注释）禁止出现 {@code http://localhost:1808x} 服务地址
     *  字面量——这是"跨引擎取数默认 :18082 未运行"的可辨识硬编码形式（{@code ServiceEndpointResolver}
     *  两态寻址：monolith→:8080 / service→:180xx，由 {@code ecos.route.<service>} 控制，默认 monolith）。
     *
     *  <p>本护栏刻意匹配 <b>{@code :180[0-9]{2}}</b>（不是 :8080）——:8080 对应 monolith 端口，
     *  表明该调用点已在正确态（默认 monolith）或是既有 {@code @Value} 默认值场景（另有 lint）；
     *  而 {@code :180xx} 出现在 literal/默认值处则正是 D-14 症状："跨引擎取数默认指向未运行的
     *  :18082 并静默降级"（W55 P0）。</p>
     */
    private static final Pattern HARDCODED_SERVICE_URL =
            Pattern.compile("http[s]?://localhost:180[0-9]{2}");

    @Test
    @DisplayName("EndpointResolverUsageArchTest — 引擎代码（非注释）禁 http://localhost:8080|:1808x 字面量")
    void noHardcodedServiceUrlInEngineCode() throws IOException {
        Path src = RlsInjectionGuardArchTest.resolveModuleSrcMainJava();
        assertNotNull(src, "engine/data-engine/data-engine-impl/src/main/java 不可解析，扫描路径异常");

        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(src)) {
            List<Path> javas = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            assertTrue(!javas.isEmpty(), "src/main 未找到任何 .java，扫描路径异常");
            for (Path p : javas) {
                String raw = Files.readString(p, StandardCharsets.UTF_8);
                String code = RlsInjectionGuardArchTest.stripComments(raw);
                if (HARDCODED_SERVICE_URL.matcher(code).find()) {
                    String rel = p.toString().replace(src.toString(), "").replace('\\', '/');
                    hits.add(rel + " : 硬编码 http://localhost:8080|:1808x 服务地址（应走 ServiceEndpointResolver）");
                }
            }
        }
        assertTrue(hits.isEmpty(),
                "发现 W55 违规（跨引擎取数硬编码服务地址，未走两态寻址）：\n" + String.join("\n", hits));
    }
}
