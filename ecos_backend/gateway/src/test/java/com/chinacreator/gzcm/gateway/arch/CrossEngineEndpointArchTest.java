package com.chinacreator.gzcm.gateway.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * F02-15 验收（详细设计-02 P0）— {@code EndpointResolverUsageArchTest} 源码门禁。
 *
 * <p>snippet：
 * <ol>
 *   <li>F02-15 三个 datanet 消费方（DataNetResourceClient / KbEntityInstanceExtractionService /
 *       KnowledgeDocIngestService）：禁 {@code http://localhost:18082} 字面量，
 *       且必须引用 {@code ServiceEndpointResolver} 两态寻址；</li>
 *   <li>broader 门禁：全 engine 模块 main 源码禁 {@code localhost:18082}（datanet 端口硬编码）；</li>
 *   <li>broader 门禁：全 engine 模块 main 源码 {@code localhost:180xx} 字面量只允许出现在登记的
 *       存量豁免清单内（security 18081 / kbengine 18086 / ontology 18083 属各自批次 W32/B 批次，
 *       不在本 F02-15 收口范围）。</li>
 * </ol>
 *
 * <p>surefire 工作目录 = {@code gateway/}，sibling 模块在 {@code ../engine/...}。</p>
 */
class CrossEngineEndpointArchTest {

    private static final String BACKEND_ROOT = "..";  // surefire cwd = gateway/

    /** F02-15 三个 datanet 消费方（相对 backend root）。 */
    private static final String[] F02_15_FILES = {
            "engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/client/DataNetResourceClient.java",
            "engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/service/KbEntityInstanceExtractionService.java",
            "engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/service/KnowledgeDocIngestService.java"
    };

    /**
     * 存量豁免（baseline 2026-09-28，归 W32/B 等批次收口，非 F02-15 datanet 面）：
     * 命中 localhost:180xx（非 18082）的引擎 main 文件。新增命中 = 门禁 FAIL。
     */
    private static final Set<String> LOCALHOST_180XX_DENYLED_BASELINE = Set.of(
            "engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/security/AiSecurityEngineClient.java",
            "engine/data-engine/data-engine-api/src/main/java/com/chinacreator/gzcm/engine/data/quality/DqRcaService.java",
            "engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/event/EcosOntologyEventConsumer.java",
            "engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/security/KnowledgeNavSecurityEngineClient.java",
            "engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/security/SecurityEngineClient.java",
            "engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/client/KbEngineGraphSyncClient.java",
            // 详设-06 F06-11（commit 4940048c, 2026-10-09）遗留 workspace(:18090) 场景 base-url 默认值；
            // 2026-10-11 真跑 mvn test 才暴露。与 data-engine EndpointResolverUsageArchTest 同文件同口径登记，
            // 归 design-06 收尾批次改走 ServiceEndpointResolver 后从本清单删除。
            "engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/service/ToolExecutorService.java"
    );

    private static Path backendRoot() {
        Path root = Paths.get(BACKEND_ROOT).toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(root), "backend root 未找到: " + root);
        return root;
    }

    @Test
    @DisplayName("F02-15 三个消费方：禁 localhost:18082 字面量 + 必引用 ServiceEndpointResolver")
    void f0215FilesUseResolverAndNoHardcodedDatanetPort() throws IOException {
        Path root = backendRoot();
        for (String rel : F02_15_FILES) {
            Path file = root.resolve(rel);
            assertTrue(Files.isRegularFile(file), "F02-15 消费方文件缺失: " + file);
            String src = Files.readString(file);
            assertFalse(src.contains("http://localhost:18082"),
                    "禁硬编码 datanet 端口字面量（F02-15 ①）: " + rel);
            assertTrue(src.contains("ServiceEndpointResolver"),
                    "必须经 ServiceEndpointResolver 两态寻址（F02-15 ①）: " + rel);
            assertFalse(src.contains("ecos.datanet.base-url"),
                    "禁继续消费 ecos.datanet.base-url（默认值 :18082 已删，F02-15 ①）: " + rel);
        }
    }

    @Test
    @DisplayName("全 engine 模块 main 源码零 localhost:18082（datanet 端口禁硬编码）")
    void noDatanetPortLiteralInEngineMainSources() throws IOException {
        Path engineRoot = backendRoot().resolve("engine");
        Set<String> violations = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(engineRoot)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.toString().contains("target"))
                .filter(p -> p.toString().contains("/src/main/"))
                .forEach(p -> {
                    try {
                        if (Files.readString(p).contains("localhost:18082")) {
                            violations.add(backendRoot().relativize(p).toString());
                        }
                    } catch (IOException e) {
                        fail("读取失败: " + p);
                    }
                });
        }
        assertEquals(List.of(), List.copyOf(violations),
                "引擎 main 源码禁出现 datanet 端口字面量: " + violations);
    }

    @Test
    @DisplayName("全 engine 模块 main 源码 localhost:180xx 命中 ⊆ 存量豁免清单")
    void engineLocalhost180xxWithinDenyList() throws IOException {
        Path engineRoot = backendRoot().resolve("engine");
        Set<String> hits = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(engineRoot)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.toString().contains("target"))
                .filter(p -> p.toString().contains("/src/main/"))
                .forEach(p -> {
                    try {
                        if (Files.readString(p).contains("localhost:180")) {
                            hits.add(backendRoot().relativize(p).toString().replace('\\', '/'));
                        }
                    } catch (IOException e) {
                        fail("读取失败: " + p);
                    }
                });
        }
        Set<String> unexpected = new LinkedHashSet<>(hits);
        unexpected.removeAll(LOCALHOST_180XX_DENYLED_BASELINE);
        assertEquals(List.of(), List.copyOf(unexpected),
                "新增 localhost:180xx 硬编码（须走 ServiceEndpointResolver，先登记豁免）: " + unexpected);
    }
}
