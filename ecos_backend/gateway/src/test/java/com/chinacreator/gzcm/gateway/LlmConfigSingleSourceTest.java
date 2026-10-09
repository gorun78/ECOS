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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F06-20⑤ (W/C 20: {@code llm.*} 收敛到 gateway 单源) — LLM 配置单源门禁。
 *
 * <p><b>断言（yml 源码扫描，无 DB / 无 Spring）</b>：{@code llm.*} 配置的<b>唯一事实源</b>
 * 是 {@code gateway/application.yml}（llm-gateway 随 gateway fat-JAR 承流，见 AGENTS.md
 * 「代理双轨」+ F06-05）。<b>除 gateway 之外</b>的任何服务/底座 {@code application*.yml}
 * 都不得再声明任何 {@code llm.*} 叶子键 —— 否则就是「三处重复」重演（X-71~X-73），
 * 令配置随服务漂移、单源失效。
 *
 * <p>等价表述：以 {@code gateway/application.yml} 为唯一允许声明 {@code llm:} 键的 yml；
 * 其余服务的 {@code llm:} 块允许存在但<b>只能含注释</b>（指向 gateway 单源的引用说明），
 * 不允许出现任何非注释的 {@code key: value} 声明行。
 *
 * <p>{@code runtime/llm-gateway/application-llm.yml} 属 F06-20④ 死配置（无 profile/include
 * 激活它），本轮已删除 —— 若被回滚复活，本扫描会因它在非 gateway 目录声明 {@code llm.} 叶子键而 FAIL。
 *
 * <p>验收：{@code mvn -f ecos_backend/pom.xml -pl gateway -am test
 * -Dtest='LlmConfigSingleSourceTest#noDuplicateLlmKeysOutsideGatewayYml'
 * -Dsurefire.failIfNoSpecifiedTests=false}
 */
class LlmConfigSingleSourceTest {

    private static final Path GATEWAY_YML = Paths.get("gateway/src/main/resources/application.yml");

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

    private static List<Path> allApplicationYml(Path backend) throws IOException {
        try (Stream<Path> s = Files.walk(backend)) {
            return s.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().matches("application.*\\.yml"))
                    .filter(f -> !f.toString().contains("target"))
                    .filter(f -> !f.startsWith(backend.resolve("gateway")))
                    .collect(Collectors.toList());
        }
    }

    /** 收集某 yml 顶层 {@code llm:} 块内的非注释叶子声明行（相对键路径 + 原文，用于报错）。 */
    private static List<String> llmLeafDeclarations(String yml) {
        String[] lines = yml.split("\\r?\\n", -1);
        List<String> leaves = new ArrayList<>();
        int i = 0;
        while (i < lines.length) {
            String raw = lines[i];
            // 顶层 llm: （无缩进，且不是注释）
            if (raw.startsWith("llm:")) {
                i++;
                while (i < lines.length) {
                    String line = lines[i];
                    // 块结束 = 出现非空且无缩进的下一个顶层键
                    if (!line.isBlank() && !line.startsWith(" ") && !line.startsWith("\t")) break;
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")
                            && trimmed.contains(":")) {
                        // 只有「有值」的声明行才算叶子；`gateway:` 这类无值父键映射到嵌套块，跳过
                        int colon = trimmed.indexOf(':');
                        String value = trimmed.substring(colon + 1).trim();
                        if (!value.isEmpty()) leaves.add(trimmed);
                    }
                    i++;
                }
                continue;
            }
            i++;
        }
        return leaves;
    }

    /**
     * 允许例外：{@code default-api-key-ref: ${LLM_GATEWAY_DEFAULT_API_KEY_REF:...}}
     * —— 该键是进程级 env 注入边界（service 侧不解析密钥，只是把 env 变量原样上送 gate），
     * 属运行时部署形态而非"配置漂移"（X-71 语境下允许保留在 aiming；sysman-boot 因不承 llm-gateway 而无此键）。
     */
    private static boolean isAllowedRuntimeBoundaryExemption(String leaf) {
        return leaf.startsWith("default-api-key-ref:")
                && leaf.contains("LLM_GATEWAY_DEFAULT_API_KEY_REF");
    }

    @Test
    @DisplayName("F06-20⑤：gateway 之外无 llm.* 重复声明（单源 = gateway/application.yml）")
    void noDuplicateLlmKeysOutsideGatewayYml() throws IOException {
        Path be = backendRoot();

        // 单源本身必须存在且确实声明了 llm.*（否则"单源"名不副实）
        Path gateway = be.resolve(GATEWAY_YML);
        assertTrue(Files.isRegularFile(gateway), "单源缺失: " + GATEWAY_YML);
        List<String> gatewayLeaves = llmLeafDeclarations(Files.readString(gateway, StandardCharsets.UTF_8));
        assertTrue(!gatewayLeaves.isEmpty(),
                "gateway/application.yml 未声明任何 llm.* 键 —— 单源失效？");

        // 除 gateway 外，任一 application*.yml 不得声明 llm.* 叶子键（除 runtime 边界 env 注入）
        List<Path> others = allApplicationYml(be);
        assertTrue(!others.isEmpty(), "未扫到任何非 gateway application*.yml（扫描路径异常？）");

        List<String> violations = new ArrayList<>();
        for (Path yml : others) {
            String body = Files.readString(yml, StandardCharsets.UTF_8);
            List<String> real = new ArrayList<>();
            for (String leaf : llmLeafDeclarations(body)) {
                if (!isAllowedRuntimeBoundaryExemption(leaf)) real.add(leaf);
            }
            if (!real.isEmpty()) {
                violations.add(yml.toString().replace('\\', '/') + " 声明了 " + real.size()
                        + " 个 llm.* 叶子键: " + real);
            }
        }
        assertTrue(violations.isEmpty(),
                "gateway 之外出现 llm.* 重复声明（违 F06-20⑤ 单源，X-71~X-73）:\n"
                        + String.join("\n", violations));
    }
}
