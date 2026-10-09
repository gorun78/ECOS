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

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /**
     * F06-21-2 / X-80 —— AI/Agent 前缀网关闭约的<b>可判化守卫</b>（yml 源码扫描，无 Spring）。
     *
     * <p>断言：{@code llm.gateway} 块必须显式声明
     * <ul>
     *   <li>{@code ai-prefix-request-timeout: 300s}：AI/Agent 前缀（SSE/长流/多轮循环）专属超时；</li>
     *   <li>{@code request-timeout: 60s}：其余前缀默认；</li>
     *   <li>与 {@code llm.agent-timeout-ms: 300000} 数值同源（300s == 300000ms）—— 即
     *       F06-14 的 LLM_TIMEOUT 判定与网关超时不再"互相打架"。</li>
     * </ul>
     * 若任一缺失或数值漂移，SSE Agent 流式对话要么被 60s 切断（超时不足），要么
     * LLM_TIMEOUT 抛 300s 但网关已 60s 超时（判定失真）。
     */
    @Test
    @DisplayName("F06-21-2/X-80：AI/Agent 前缀 300s vs 默认 60s —— 显式契约且与 agent-timeout-ms 同源")
    void aiAgentPrefixTimeoutContractIsExplicitAndConsistent() throws IOException {
        Path be = backendRoot();
        Path gateway = be.resolve(GATEWAY_YML);
        assertTrue(Files.isRegularFile(gateway), "单源缺失: " + GATEWAY_YML);
        String yml = Files.readString(gateway, StandardCharsets.UTF_8);
        String[] lines = yml.split("\\r?\\n", -1);

        Long agentTimeoutMs = firstScalarValue(lines, "agent-timeout-ms");
        String aiPrefix = firstScalarValueRaw(lines, "ai-prefix-request-timeout");
        String defaultTimeout = firstScalarValueRaw(lines, "request-timeout");

        assertTrue(agentTimeoutMs != null,
                "llm.agent-timeout-ms 未显式声明（F06-21-2 契约缺 AI 侧锚点）");
        assertTrue(aiPrefix != null,
                "llm.gateway.ai-prefix-request-timeout 未显式声明（F06-21-2 契约缺显式前缀档，X-80 漂移点）");
        assertTrue(defaultTimeout != null,
                "llm.gateway.request-timeout 未显式声明（F06-21-2 契约缺默认档）");

        // AI/Agent 前缀 300s == agent-timeout-ms（毫秒）同源
        long aiPrefixSec = parseToSeconds(aiPrefix);
        assertEquals(300L, aiPrefixSec,
                "ai-prefix-request-timeout 应为 300s，实测 " + aiPrefix);
        assertEquals(300_000L, agentTimeoutMs,
                "agent-timeout-ms 应为 300000（300s），实测 " + agentTimeoutMs);
        assertEquals(aiPrefixSec * 1000L, agentTimeoutMs,
                "ai-prefix-request-timeout(" + aiPrefix + ") 与 agent-timeout-ms(" + agentTimeoutMs
                        + ") 数值不同源 — F06-21-2 契约失效（X-80 互打风险）");

        // 默认档仍为 60s（非 AI 前缀不受长流影响）
        assertEquals(60L, parseToSeconds(defaultTimeout),
                "llm.gateway.request-timeout 应为 60s（非 AI 前缀默认），实测 " + defaultTimeout);
    }

    /** 从 yml 非注释行取某键的标量数值（Long）。 */
    private static Long firstScalarValue(String[] lines, String key) {
        String raw = firstScalarValueRaw(lines, key);
        if (raw == null) return null;
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 从 yml 非注释行取某键的标量文本值（跳过嵌套值行 & 注释）。 */
    private static String firstScalarValueRaw(String[] lines, String key) {
        String prefix = key + ":";
        for (String l0 : lines) {
            String line = l0.trim();
            if (line.startsWith("#")) continue;
            if (line.startsWith(prefix)) {
                String val = line.substring(prefix.length()).trim();
                int comment = val.indexOf('#');
                if (comment >= 0) val = val.substring(0, comment).trim();
                if (!val.isEmpty()) return val;
            }
        }
        return null;
    }

    /** 解析 "300s" / "60s" / "30s" 类 → 秒；"300000ms" 系 → ms/1000。 */
    private static long parseToSeconds(String v) {
        String s = v.trim();
        if (s.endsWith("ms")) {
            return Long.valueOf(s.substring(0, s.length() - 2).trim()) / 1000L;
        }
        if (s.endsWith("s")) {
            return Long.valueOf(s.substring(0, s.length() - 1).trim());
        }
        // 裸数字 = 秒
        return Long.valueOf(s);
    }
}
