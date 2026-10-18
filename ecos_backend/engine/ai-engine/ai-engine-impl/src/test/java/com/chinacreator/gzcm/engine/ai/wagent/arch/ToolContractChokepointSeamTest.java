package com.chinacreator.gzcm.engine.ai.wagent.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 静态接缝门（差距分析 B5 行「工具目录双轨」/ 任务 #143-B5-1）。
 *
 * <p><b>病灶</b>：W 契约目录（{@code ecos_ai.ecos_wagent_tool_contract}，V236+V266）与咽喉
 * 工具目录（{@code ecos_ai.ecos_tool_definition} + 场景声明 + 内存 fallback）零交集，
 * 且 {@code EngineEndpointInvoker} 已按 {@code name+version} 校完 active 再调
 * {@code toolExecutorService.execute(new ToolCall(callId, toolName, input))} —— <b>版本在这一跳被丢弃</b>，
 * 咽喉只按名字解析 ⇒ F10-19 的 12 工具在执行回路恒得 {@code INVALID_ARGUMENT}「工具未找到」。
 * 双轨不收敛，编排在 live 永远走不通，而单测里两侧各自都"绿"。</p>
 *
 * <p>为什么是静态门：行为用例引用的是<b>修复后才存在的 API</b>，修复前只表现为编译失败，
 * 拿不到"跑出来的红"。本类只读源码文本、不依赖新 API，因此<b>能在落码前先红一次</b>，
 * 落码后转绿；并配一条把同一判据喂给"无接缝形状"的自检，守住判据不恒真。</p>
 *
 * <p>验收：{@code mvn -Dtest=ToolContractChokepointSeamTest#chokepointWiresContractCatalogSource}</p>
 */
class ToolContractChokepointSeamTest {

    private static final String SERVICE_SRC =
            "engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/service/ToolExecutorService.java";
    private static final String INVOKER_SRC =
            "engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/wagent/tool/EngineEndpointInvoker.java";
    private static final String CONTRACT_SOURCE_SRC =
            "engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/wagent/tool/WAgentContractToolSource.java";

    /** 咽喉侧必须存在的接缝形态（缺任一 ⇒ 双轨未收敛）。 */
    private static final List<String> CHOKEPOINT_TOKENS = List.of(
            "interface IContractToolSource",
            "Optional<ContractToolDeclaration> findActive(String toolName, String toolVersion)",
            "ToolDefinitionEntry loadToolDefinition(String toolName, String toolVersion)",
            "contractToolSource.findActive(toolName, toolVersion)",
            "entry.contract = declared",
            "if (def.contract != null)",
            "executeContractRest(def, arguments)",
            "def.contract.operation()");

    /** 编排侧必须把版本送进咽喉（否则契约仍解析不到）。 */
    private static final List<String> INVOKER_TOKENS = List.of(
            "new ToolCall(callId, toolName, input, toolVersion)");

    @Test
    @DisplayName("B5-1 接缝静态门：咽喉含契约目录源接缝、编排含版本透传、wagent 侧含实现类")
    void chokepointWiresContractCatalogSource() throws IOException {
        Path backend = backendRoot();
        String service = executableCode(backend.resolve(SERVICE_SRC));
        assertEquals(List.of(), missingTokens(service, CHOKEPOINT_TOKENS),
                "ToolExecutorService 缺工具目录接缝 ⇒ W 契约与咽喉仍是双轨（F10-19 工具恒「工具未找到」）");

        String invoker = executableCode(backend.resolve(INVOKER_SRC));
        assertEquals(List.of(), missingTokens(invoker, INVOKER_TOKENS),
                "EngineEndpointInvoker 未把 toolVersion 送进咽喉 ⇒ 版本在调用边被丢弃");

        Path impl = backend.resolve(CONTRACT_SOURCE_SRC);
        assertTrue(Files.isRegularFile(impl), "W 契约目录的接缝实现类不存在: " + CONTRACT_SOURCE_SRC);
        String code = executableCode(impl);
        assertTrue(code.contains("WAgentToolContractDao"), "接缝实现必须读契约目录 DAO");
        assertTrue(code.contains("ToolContractStatus.ACTIVE.value()"),
                "接缝必须复用 ToolContractStatus 词表单源判 active（禁自造状态字面量）");
        assertTrue(code.contains("isLiveServicePrefix"),
                "接缝必须做在位 Engine 前缀归属校验（F10-02/F10-03 红线，与注册面同判据）");
    }

    @Test
    @DisplayName("静态门非空洞自检：同一判据对「双轨原形」（无接缝、名字单参解析）必须报缺")
    void seamJudgeRejectsPreFixShape() {
        String preFixShape = """
                ToolDefinitionEntry loadToolDefinition(String toolName) {
                    ToolDefinitionEntry scenario = loadScenarioDefinition(toolName);
                    if (scenario != null) return scenario;
                    ToolDefinitionEntry def = loadFromDatabase(toolName);
                    return def != null ? def : fallbackTools.get(toolName);
                }
                """;
        List<String> missing = missingTokens(preFixShape, CHOKEPOINT_TOKENS);
        assertFalse(missing.isEmpty(), "判据恒真：双轨原形也被判为已接接缝");
        // 原形缺的是"全部"必需 token —— 一条都没命中才说明清单确实指向新增形态
        assertEquals(CHOKEPOINT_TOKENS.size(), missing.size());

        String nameOnlyCall = "raw = toolExecutorService.execute(new ToolCall(callId, toolName, input));";
        assertEquals(INVOKER_TOKENS.size(), missingTokens(nameOnlyCall, INVOKER_TOKENS).size(),
                "判据恒真：丢版本的调用形态也被判为已透传");
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static List<String> missingTokens(String code, List<String> required) {
        List<String> missing = new ArrayList<>();
        for (String token : required) {
            if (!code.contains(token)) {
                missing.add(token);
            }
        }
        return missing;
    }

    /** 读源码并剥注释（保留字符串字面量），使"注释里提到"不构成通过依据。 */
    private static String executableCode(Path source) throws IOException {
        return stripComments(new String(Files.readAllBytes(source), StandardCharsets.UTF_8));
    }

    private static Path backendRoot() {
        Path cur = Paths.get("").toAbsolutePath();
        while (cur != null) {
            if (Files.isDirectory(cur.resolve("gateway")) && Files.isRegularFile(cur.resolve("pom.xml"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("定位不到 ecos_backend 根（pom.xml + gateway/）");
    }

    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            char next = i + 1 < n ? src.charAt(i + 1) : 0;
            if (c == '/' && next == '/') {
                while (i < n && src.charAt(i) != '\n') i++;
            } else if (c == '/' && next == '*') {
                i += 2;
                while (i < n && !(src.charAt(i) == '*' && i + 1 < n && src.charAt(i + 1) == '/')) {
                    if (src.charAt(i) == '\n') out.append('\n');
                    i++;
                }
                i = i + 2 <= n ? i + 2 : n;
            } else if (c == '"') {
                out.append(c);
                i++;
                boolean textBlock = src.startsWith("\"\"\"", i - 1);
                if (textBlock) {
                    // Java 文本块：原文照抄到结束三引号（自检用例里的形状文本不能被判据切碎）
                    i += 2;
                    while (i < n && !src.startsWith("\"\"\"", i)) {
                        out.append(src.charAt(i));
                        i++;
                    }
                    out.append("\"\"\"");
                    i += 3;
                } else {
                    while (i < n) {
                        char sc = src.charAt(i);
                        out.append(sc);
                        if (sc == '\\') {
                            if (i + 1 < n) {
                                out.append(src.charAt(i + 1));
                                i += 2;
                                continue;
                            }
                            i++;
                            break;
                        }
                        i++;
                        if (sc == '"') break;
                    }
                }
            } else if (c == '\'') {
                out.append(c);
                i++;
                while (i < n) {
                    char cc = src.charAt(i);
                    out.append(cc);
                    if (cc == '\\') {
                        if (i + 1 < n) {
                            out.append(src.charAt(i + 1));
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    i++;
                    if (cc == '\'') break;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
