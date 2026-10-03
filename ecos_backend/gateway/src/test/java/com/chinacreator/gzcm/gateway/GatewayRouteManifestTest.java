package com.chinacreator.gzcm.gateway;

import com.chinacreator.gzcm.gateway.routing.ServiceEndpointResolver;
import com.chinacreator.gzcm.gateway.routing.ServiceEndpointResolver.RouteMode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F00-01/W01（详细设计-00 C.4，M0）— route-manifest 单源完整性扩展断言。
 *
 * <p>与既有 {@link GatewayRouteIntegrityTest}（5 前缀承载）互补：本类校验
 * <b>route-manifest.json 是唯一真源</b>——每条 entry 的 mode 有值、
 * prefix 在 gateway 源码树确实有 Controller 承载（非 404）或已切流，
 * 且 classpath 副本与 docs 真源副本一致（防三处漂移）。
 *
 * <p>运行：{@code mvn -pl gateway -am test -Dtest=GatewayRouteManifestTest}
 */
public class GatewayRouteManifestTest {

    private static ServiceEndpointResolver resolver;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream is = GatewayRouteManifestTest.class
                .getResourceAsStream("/route/route-manifest.json")) {
            assertNotNull(is, "classpath 缺 route/route-manifest.json（构建期未拷贝）");
            resolver = new ServiceEndpointResolver(is, 8080, "127.0.0.1");
        }
    }

    private static Path repoRoot() {
        Path root = Paths.get("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("ecos_backend"))) {
            root = root.getParent();
        }
        assertNotNull(root, "cwd 未定位到 ECOS 仓库根");
        return root;
    }

    @Test
    void manifestEveryEntryHasMode() {
        assertFalse(resolver.entries().isEmpty(), "manifest entries 为空");
        for (ServiceEndpointResolver.Entry e : java.util.List.copyOf(resolver.entries())) {
            assertEquals("monolith", e.mode(),
                "S3 切流前所有 entry mode 必须为 monolith（ADR-15），前缀 " + e.prefix());
            assertTrue(e.owner() != null && !e.owner().isBlank(),
                "entry 缺 owner：" + e.prefix());
        }
    }

    @Test
    void resolverMonolithAndServiceAddressing() {
        // datanet：monolith :8080 / service :18082（C.6.1）
        assertEquals("http://127.0.0.1:8080", resolver.resolve("datanet", RouteMode.monolith));
        assertEquals("http://127.0.0.1:18082", resolver.resolve("datanet", RouteMode.service));
        assertEquals("http://127.0.0.1:18086", resolver.resolve("dccheng", RouteMode.service));
        // 未登记 owner → 明确报错（禁自拼 base-url 兜底）
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> resolver.resolve("__not_a_service__", RouteMode.monolith));
    }

    /** 每条 manifest 前缀在 gateway 源码树须有 @RequestMapping 承载（非 404） */
    @Test
    void everyManifestPrefixHasControllerBacking() throws IOException {
        Set<String> loaded = new HashSet<>(RequestMappingLiterals(repoRoot().resolve("ecos_backend")));

        java.util.List<String> notCovered = new java.util.ArrayList<>();
        for (Object e0 : resolver.entries()) {
            @SuppressWarnings("unchecked")
            String prefix = ((ServiceEndpointResolver.Entry) e0).prefix();
            boolean covered = loaded.stream().anyMatch(l ->
                    l.equals(prefix) || l.startsWith(prefix + "/") || prefix.startsWith(l));
            if (!covered) notCovered.add(prefix);
        }
        assertTrue(notCovered.isEmpty(),
            "以下 manifest 前缀在源码树无 @*Mapping 承载（404 风险）：\n" + notCovered);
    }

    /** classpath 副本与 docs/10-架构/refs 真源副本 byte-equal（防漂移） */
    @Test
    void classpathManifestMatchesDocsSource() throws IOException {
        byte[] cp = new byte[0];
        try (InputStream is = ServiceEndpointResolver.class
                .getResourceAsStream("/route/route-manifest.json")) {
            cp = is.readAllBytes();
        }
        Path docs = repoRoot().resolve("docs/10-架构/refs/route-manifest.json");
        assertTrue(Files.exists(docs), "docs 真源 route-manifest.json 缺失");
        assertTrue(Files.readAllBytes(docs) == cp || FileBytesEqual(Files.readAllBytes(docs), cp),
            "classpath 与 docs 真源副本不一致（三处漂移，C.4 一致性违规）");
    }

    private static boolean FileBytesEqual(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }

    /** 扫 backend 源码树所有 @*(Request|Get|…)Mapping("…") 字面量 */
    private static Set<String> RequestMappingLiterals(Path backendRoot) throws IOException {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "@(Request|Get|Post|Put|Delete|Patch)Mapping\\(\\s*\"([^\"]+)\"");
        Set<String> out = new HashSet<>();
        try (var walk = Files.walk(backendRoot)) {
            walk.filter(f -> f.toString().endsWith(".java"))
                .filter(f -> !f.toString().contains("target"))
                .forEach(f -> {
                    try {
                        java.util.regex.Matcher m = p.matcher(
                            new String(Files.readAllBytes(f), StandardCharsets.UTF_8));
                        while (m.find()) {
                            String lit = m.group(2);
                            out.add(lit.startsWith("/") ? lit : "/" + lit);
                        }
                    } catch (Exception ignored) { }
                });
        }
        return out;
    }
}
