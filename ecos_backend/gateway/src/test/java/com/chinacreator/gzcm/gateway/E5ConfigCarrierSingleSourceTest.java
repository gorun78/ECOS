package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 附录E 偏差 E.5 — 两枚配置键的属主单源门禁（#146ax）。
 *
 * <p>台账《实现差距分析》附录E.5 判定缺口：
 * <ul>
 *   <li>{@code ecos.kb.graph.neo4j.enabled} — 台账原文指认缺失；<b>工程裁决=禁造键</b>：
 *       kb 图谱形态的真载体是 profile yml 的 {@code ecos.graph.provider}
 *       （standard=pg / enterprise+ultimate=neo4j，由 runtime-access
 *       {@code Neo4jConfig}=
 *       &#64;ConditionalOnProperty(name="ecos.graph.provider", havingValue="neo4j") 消费），
 *       再造一个同名布尔开关即双开关假象。本门把「该键不存在」收成显式走廊，
 *       一旦有人把幻影键写进 yml / 用 &#64;Value 接线即红。</li>
 *   <li>{@code ecos.route.<svc> = monolith|service} — 已由 #146ax 在 runtime-access
 *       实装（{@code EcosRouteOverride} 闭集绑定 + {@code ServiceEndpointResolver.effectiveMode}
 *       三层裁决 + manifest route 上限）。本门锚定承流载体在位与既有
 *       {@code ecos.route.datanet-base-url} URL 覆写位不因排除而静默消失。</li>
 * </ul>
 *
 * <p>断言全部是源码/yml 静态扫描（无 DB / 无 Spring），变异方向：
 * 把任一载体行删掉、把幻影键写进任何 yml 或 @Value，任一腿即红。
 */
class E5ConfigCarrierSingleSourceTest {

    /** 幻影键：yml 形态 —— 与 provider 同缩进的 neo4j 平级块内出现 enabled:（仿现形 neo4j-uri 平级键）。 */
    private static final Pattern PHANTOM_YAML = Pattern.compile(
            "(?m)^([ ]*)provider\\s*:.*\\R\\1neo4j\\s*:[ \\t]*\\R\\1[ \\t]+enabled\\s*:");

    @Test
    @DisplayName("E.5 幻影键走廊：ecos.kb.graph.neo4j.enabled 在任何 yml/Java 主面都不存在")
    void phantomKbNeo4jEnabledKeyStaysAbsent() throws IOException {
        Path backend = backendRoot();
        List<String> hits = new ArrayList<>();
        try (Stream<Path> s = Files.walk(backend)) {
            for (Path f : s.filter(Files::isRegularFile)
                    .filter(f -> !f.toString().contains("target"))
                    .filter(f -> !f.toString().contains("\\test\\") && !f.toString().contains("/test/"))
                    .filter(f -> f.toString().endsWith(".yml") || f.toString().endsWith(".java"))
                    .collect(Collectors.toList())) {
                String src = Files.readString(f, StandardCharsets.UTF_8);
                if (src.contains("ecos.kb.graph.neo4j.enabled")
                        || PHANTOM_YAML.matcher(src).find()) {
                    hits.add(f.toString());
                }
            }
        }
        assertTrue(hits.isEmpty(),
                "E.5 禁造键走廊破口：eco" + "s.kb.graph.neo4j.enabled 被写入 " + hits
                        + "（真载体 = ecos.graph.provider，见 Neo4jConfig @ConditionalOnProperty）");
    }

    @Test
    @DisplayName("E.5 属主载体在位：ecos.graph.provider 三档 yml 声明且形态闭集为 {pg, neo4j}")
    void graphProviderCarrierIsPerEditionYaml() throws IOException {
        Path gateway = backendRoot().resolve("gateway/src/main/resources");
        expectProvider(gateway.resolve("application-standard.yml"), "pg", 1);
        expectProvider(gateway.resolve("application-enterprise.yml"), "neo4j", 1);
        expectProvider(gateway.resolve("application-ultimate.yml"), "neo4j", 1);
    }

    private static void expectProvider(Path yml, String expected, int count) throws IOException {
        assertTrue(Files.exists(yml), "缺 " + yml);
        String src = Files.readString(yml, StandardCharsets.UTF_8);
        long found = src.lines()
                .filter(l -> l.strip().equals("provider: " + expected))
                .count();
        assertTrue(found >= count, yml.getFileName() + " 中未见 graph 块 "
                + "provider: " + expected);
    }

    @Test
    @DisplayName("E.5 属主载体在位：runtime-access 两态覆写三件套 + datanet-base-url 覆写位不被排除掉失")
    void routeOverrideCarriersInPlace() throws IOException {
        Path backend = backendRoot();
        Path ep = backend.resolve(
                "runtime/runtime-access/src/main/java/com/chinacreator/gzcm/runtime/access/endpoint");
        String override = read(ep.resolve("EcosRouteOverride.java"));
        assertTrue(override.contains("LITERAL_MONOLITH") && override.contains("LITERAL_SERVICE"),
                "EcosRouteOverride 闭集字面量面失锚");
        assertTrue(override.contains("IllegalStateException")
                && override.contains("monolith|service"),
                "EcosRouteOverride fail-loud 腿失锚");
        String resolver = read(ep.resolve("ServiceEndpointResolver.java"));
        assertTrue(resolver.contains("effectiveMode") && resolver.contains("attachOverride"),
                "ServiceEndpointResolver 两态裁决/覆写挂载面失锚");
        String cfg = read(ep.resolve("EcosRouteOverrideConfig.java"));
        assertTrue(cfg.contains("datanet-base-url"),
                "LEGACY_URL_KEY 排除位失锚（该 URL 覆写位仍被 6 引擎 @Value 消费）");
        // 既有 URL 覆写消费面至少尚存一枚 @Value 引用，排除腿才有可保护对象
        List<String> consumers = new ArrayList<>();
        try (Stream<Path> s = Files.walk(backend)) {
            for (Path f : s.filter(Files::isRegularFile)
                    .filter(f -> !f.toString().contains("target"))
                    .filter(f -> (f.toString().contains("/main/") || f.toString().contains("\\main\\"))
                            && f.toString().endsWith(".java"))
                    .collect(Collectors.toList())) {
                if (Files.readString(f, StandardCharsets.UTF_8)
                        .contains("eco" + "s.route.datanet-base-url:")) {
                    consumers.add(f.toString());
                }
            }
        }
        assertFalse(consumers.isEmpty(),
                "eco" + "s.route.datanet-base-url 无任何 @Value 消费方——排除腿前提已变，须重裁");
    }

    private static String read(Path p) throws IOException {
        assertTrue(Files.exists(p), "缺属主文件 " + p);
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    private static Path backendRoot() throws IOException {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("ecos_backend"))) return p.resolve("ecos_backend");
            Path parent = p.getParent();
            if (parent == null || parent.equals(p)) break;
            p = parent;
        }
        assertTrue(false, "cwd 未锚定 ECOS 仓库根（找不到 ecos_backend/）");
        return null;
    }
}
