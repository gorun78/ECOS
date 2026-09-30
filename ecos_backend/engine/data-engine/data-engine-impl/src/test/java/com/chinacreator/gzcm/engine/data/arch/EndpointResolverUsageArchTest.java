package com.chinacreator.gzcm.engine.data.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * F02-15（详细设计-02 P0）— {@code EndpointResolverUsageArchTest} 源码门禁：
 * “两态寻址单源”。
 *
 * <p>守卫命题：<b>活跃生产代码里不得再出现硬编码 {@code http://localhost:1808x} 服务 base URL 字面量</b>。
 * 跨引擎取数一律经 runtime-access {@code ServiceEndpointResolver} 两态寻址
 * （route-manifest 单源，monolith :8080 / service :18082 等），禁止各引擎自拼端口。</p>
 *
 * <p>扫描范围 = 全仓 ecos_backend 下所有 src/main 的 .java 文件，
 * 逐行读入，<b>跳过注释行</b>（trim 后以 // 、星号、-- 开头）。</p>
 *
 * <p><b>豁免基线（frozen，命中即存量、非本门禁管辖）</b>：
 * 全仓现行 {@code @Value("${...:http://localhost:1808xx}")} 端口默认值与 legacy 常量分散在
 * security(:18081)/kbengine(:18086)/ontology(:18083)/认知(:18084) 等<b>非 datanet F02-15 面</b>，
 * 由其它批次分头收口（口径与 gateway {@code CrossEngineEndpointArchTest.LOCALHOST_180XX_DENYLED_BASELINE}
 * 一致）。本清单<b>冻结</b>：新增任何一枚非基线文件的 {@code http://localhost:1808x} 字面量
 * （非注释行）→ 门禁 {@code FAIL}，逼其改走 {@code ServiceEndpointResolver}。
 * 基线文件本身治理完毕后，从清单删除并同步收紧。</p>
 */
@DisplayName("F02-15：全仓主源码禁硬编码 http://localhost:1808x base URL（非注释行，两态寻址单源）")
class EndpointResolverUsageArchTest {

    /** 硬编码跨引擎服务 base URL 前缀字面量（F02-15 ① 禁用面）。 */
    private static final String FORBIDDEN_LITERAL = "http://localhost:180";

    /**
     * 存量豁免基线（相对 {@code ecos_backend/} 根、正斜杠规范化）。
     * 口径 = 现行硬编码 {@code http://localhost:1808x} 的<b>文件级</b>登记（非 datanet 18082 面，
     * 归 security/kbengine/ontology/认知批次 W32/B 收口 + services-ghost 归档 legacy）。
     * 新增文件命中 = 门禁 FAIL。
     */
    private static final Set<String> PORT_LITERAL_BASELINE = new LinkedHashSet<>(List.of(
            // security-engine(:18081) 各引擎客户端
            "engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/security/AgentToolSqlSecurityGate.java",
            "engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/security/AiSecurityEngineClient.java",
            "engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/security/KnowledgeNavSecurityEngineClient.java",
            "engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/security/SecurityEngineClient.java",
            // kbengine(:18086) / ontology(:18083) / 认知(:18084)
            "engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/event/EcosOntologyEventConsumer.java",
            "engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/client/KbEngineGraphSyncClient.java",
            // workspace 场景层（非 engine，跨引擎聚合 base-url，待 workspace 批次收口）
            "workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/knowledge/service/KnowledgeConfigAggregator.java",
            "workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/security/WorkspaceSecurityEngineClient.java",
            "workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/controller/ScenarioOptionsController.java",
            // services-ghost 归档 legacy（只停写不迁移，R9 / 非 reactor）
            "archive/legacy/services-ghost/api-gateway/src/main/java/com/chinacreator/gzcm/services/apigateway/graphql/EcosGraphQLController.java"
    ));

    /**
     * 从 {@code user.dir} 上溯仓库根（含 {@code .git} 的目录），再取电商后端根
     * {@code <repo>/ecos_backend}。data-engine-impl 的 surefire user.dir = 模块目录
     * （{@code .../ecos_backend/engine/data-engine/data-engine-impl}），上溯即可命中仓库根。
     */
    private static Path findBackendRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path cur = dir;
        while (cur != null) {
            if (Files.isRegularFile(cur.resolve(".git")) || Files.isDirectory(cur.resolve(".git"))) {
                Path backend = cur.resolve("ecos_backend");
                if (Files.isDirectory(backend)) {
                    return backend.normalize();
                }
            }
            cur = cur.getParent();
        }
        // 兜底：user.dir 本身或其上级即 ecos_backend（少见部署形态）
        cur = dir;
        while (cur != null) {
            if (cur.getFileName() != null
                    && cur.getFileName().toString().equals("ecos_backend")
                    && Files.isRegularFile(cur.resolve("pom.xml"))) {
                return cur.normalize();
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("未能定位 ecos_backend 根（自 user.dir=" + dir + " 上溯未命中 .git / ecos_backend）");
    }

    @Test
    @DisplayName("全仓 ecos_backend/**/src/main/** 非注释行 0 处 http://localhost:1808x（豁免基线外）")
    void noHardcodedServiceBaseUrlInMainSources() throws IOException {
        Path backendRoot = findBackendRoot();
        assertTrue(Files.isDirectory(backendRoot), "backend 根不存在: " + backendRoot);

        // 命中记录：relPath(正斜杠) → 违规行号列表
        Set<String> unexpectedFiles = new LinkedHashSet<>();
        List<String> samples = new ArrayList<>();
        int totalSrcFiles = 0;

        try (Stream<Path> walk = Files.walk(backendRoot)) {
            List<Path> javaMains = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/target/"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/"))
                    .toList();
            totalSrcFiles = javaMains.size();

            for (Path p : javaMains) {
                List<String> lines;
                try {
                    lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException("读取失败: " + p, e);
                }
                String rel = backendRoot.relativize(p).toString().replace('\\', '/');

                boolean violation = false;
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    String t = line.trim();
                    // 跳过注释行（与 NoDriverManagerArchTest 同款口径）
                    if (t.startsWith("//") || t.startsWith("*") || t.startsWith("--")
                            || t.startsWith("/*")) {
                        continue;
                    }
                    if (line.contains(FORBIDDEN_LITERAL)) {
                        violation = true;
                        if (samples.size() < 40) {
                            samples.add(rel + ":" + (i + 1) + ": " + t);
                        }
                    }
                }
                if (violation && !PORT_LITERAL_BASELINE.contains(rel)) {
                    unexpectedFiles.add(rel);
                }
            }
        }

        assertTrue(totalSrcFiles > 0,
                "搜索结果异常：src/main 下未找到任何 .java（backendRoot=" + backendRoot + "）");
        assertEquals(List.of(), List.copyOf(unexpectedFiles),
                "F02-15 两态寻址单源违规——新增硬编码 http://localhost:1808x base URL（非注释行，"
                        + "须改走 runtime-access ServiceEndpointResolver，或先登记豁免基线）：\n"
                        + String.join("\n", samples));
    }
}
