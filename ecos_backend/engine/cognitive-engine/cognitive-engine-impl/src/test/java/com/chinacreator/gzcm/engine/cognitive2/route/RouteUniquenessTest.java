package com.chinacreator.gzcm.engine.cognitive2.route;

import com.chinacreator.gzcm.engine.cognitive2.controller.CognitiveEngineOpenHealthController;
import com.chinacreator.gzcm.engine.cognitive2.controller.scenario.ScenarioCognitionController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F05-01/F05-08 路由唯一性（对 Controller 实际注解断言，非源码 regex 近似）：
 * <ul>
 *   <li>{@code /api/v1/engine/cognitive} 前缀按部署态唯一属主：源面 = ai 侧
 *       {@code AiEngineStatusController} + cognitive 侧 {@link CognitiveEngineOpenHealthController}
 *       双类（PMO-55 E-C 定版），gateway/aiming 宿主经 excludeFilters 排 cognitive 新副本
 *       （PMO-57 T5 裁定，生效属主 = ai 侧；cognitive 副本次生效于 standalone 态）；</li>
 *   <li>场景认知四端点以 Spring URI 模板 {@code {scenarioId}} 可寻址（禁 {@code %s} 混入 mapping）；</li>
 *   <li>replay 签名定版唯一（GET 只读，禁 GET/POST 双套并存，X-48）。</li>
 * </ul>
 */
class RouteUniquenessTest {

    private static final Path BACKEND = Path.of("..", "..", "..", "..").toAbsolutePath().normalize()
            .resolve("ecos_backend");

    @Test
    @DisplayName("/api/v1/engine/cognitive 前缀按部署态唯一属主（双源类 + 宿主 excludeFilters 收口）")
    void engineCognitivePrefixHasSingleOwner() throws IOException {
        Pattern p = Pattern.compile("@RequestMapping\\s*\\(\\s*\"/api/v1/engine/cognitive\"");
        List<String> owners = new ArrayList<>();
        for (Path f : mainControllers()) {
            if (p.matcher(Files.readString(f)).find()) {
                owners.add(f.getFileName().toString());
            }
        }
        // 源类面恰为两个（禁第三方撞前缀）；PMO-57 T5 裁定的双源共存不在此断言范围
        assertEquals(Set.of("AiEngineStatusController.java", "CognitiveEngineOpenHealthController.java"),
                new HashSet<>(owners),
                "engine/cognitive 前缀属主集合漂移（Ambiguous mapping 风险）：" + owners);
        // 部署态唯一属主：两个扫描 cognitive2 的生产宿主都必须 excludeFilters 排掉 cognitive 新副本，
        // 保证 gateway(:8080)/aiming(:18084) 实际生效属主唯一 = AiEngineStatusController
        for (String host : List.of("GatewayApplication.java", "AimingServiceApplication.java")) {
            Path hostFile = findFirstByName(host);
            assertTrue(hostFile != null, "宿主应用类缺失：" + host);
            String src = Files.readString(hostFile);
            assertTrue(src.contains("excludeFilters"), host + " 无 excludeFilters 块");
            assertTrue(src.contains("CognitiveEngineOpenHealthController.class"),
                    host + " 未排除 CognitiveEngineOpenHealthController — 部署态双属主 Ambiguous mapping（PMO-57 T5 裁定漂移）");
        }
        // 类级注解实际值二次确认（编译期常量 → 运行时注解）
        RequestMapping ann = CognitiveEngineOpenHealthController.class.getAnnotation(RequestMapping.class);
        assertTrue(ann != null && List.of(ann.value()).contains("/api/v1/engine/cognitive"));
    }

    @Test
    @DisplayName("场景认知四端点 mapping 可寻址：URI 模板 {scenarioId} + 四后缀 + GET 只读")
    void scenarioCognitionFourEndpointsAreAddressable() {
        String base = "/api/v1/business/scenarios/{scenarioId}/cognition";
        Set<String> expected = new HashSet<>(Set.of(
                base + "/detect", base + "/operation-eval", base + "/hypotheses", base + "/beliefs"));
        Set<String> got = new HashSet<>();
        int getPathCount = 0;
        int postPathCount = 0;
        for (Method m : ScenarioCognitionController.class.getDeclaredMethods()) {
            GetMapping g = m.getAnnotation(GetMapping.class);
            PostMapping pm = m.getAnnotation(PostMapping.class);
            if (g != null) { getPathCount++; collect(g.value(), got, m); }
            if (pm != null) { postPathCount++; collect(pm.value(), got, m); }
        }
        assertEquals(expected, got, "四端点 mapping 与冻结契约不一致");
        assertEquals(4, getPathCount, "四端点必须全 GET（只读，B-2）");
        assertEquals(0, postPathCount, "禁 POST 变体（X-48 定版）");
    }

    private static void collect(String[] values, Set<String> sink, Method m) {
        for (String v : values) {
            assertTrue(!v.contains("%s"), "mapping 混入 String.format 占位符（不可寻址）：" + v);
            if (v.contains("{scenarioId}")) {
                boolean hasBind = false;
                for (Annotation[] pas : m.getParameterAnnotations()) {
                    for (Annotation a : pas) if (a instanceof PathVariable) hasBind = true;
                }
                assertTrue(hasBind, "URI 模板占位缺 @PathVariable 绑定：" + v);
            }
            sink.add(v);
        }
    }

    @Test
    @DisplayName("cognitive replay 签名定版唯一（GET 只读；禁 GET/POST 互斥双套）")
    void cognitiveReplaySingleSignature() throws IOException {
        List<String> replays = new ArrayList<>();
        // X-48 断言范围 = 认知域 replay（cognitive-engine 模块 Controller）；
        // runtime DLQ replay 属告警域，不在认知契约面
        try (Stream<Path> walk = Files.walk(BACKEND.resolve("engine").resolve("cognitive-engine"))) {
            walk.filter(f -> f.getFileName().toString().endsWith("Controller.java"))
                    .filter(f -> !f.toString().replace('\\', '/').contains("/target/"))
                    .filter(f -> f.toString().replace('\\', '/').contains("/src/main/java/"))
                    .forEach(f -> {
                        try {
                            String src = Files.readString(f);
                            for (String line : src.split("\n")) {
                                String t = line.trim();
                                if (t.startsWith("@GetMapping") || t.startsWith("@PostMapping")) {
                                    if (t.contains("replay")) {
                                        replays.add(f.getFileName().toString() + " :: " + t
                                                + (t.startsWith("@PostMapping") ? " [POST]" : " [GET]"));
                                    }
                                }
                            }
                        } catch (IOException ignored) {}
                    });
        }
        assertEquals(1, replays.size(), "认知域 replay 映射必须唯一签名，实到：" + replays);
        assertTrue(replays.get(0).contains("[GET]"), "契约定版 GET 只读，实到：" + replays);
    }

    private static List<Path> mainControllers() throws IOException {
        try (Stream<Path> walk = Files.walk(BACKEND)) {
            return walk
                    .filter(f -> f.getFileName().toString().endsWith("Controller.java"))
                    .filter(f -> !f.toString().replace('\\', '/').contains("/target/"))
                    .filter(f -> !f.toString().replace('\\', '/').contains("/archive/"))
                    .filter(f -> f.toString().replace('\\', '/').contains("/src/main/java/"))
                    .toList();
        }
    }

    private static Path findFirstByName(String fileName) throws IOException {
        try (Stream<Path> walk = Files.walk(BACKEND)) {
            return walk
                    .filter(f -> f.getFileName().toString().equals(fileName))
                    .filter(f -> !f.toString().replace('\\', '/').contains("/target/"))
                    .filter(f -> !f.toString().replace('\\', '/').contains("/archive/"))
                    .findFirst()
                    .orElse(null);
        }
    }
}
