package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-06 §八 接缝义务 3 —— <b>Tool Contract 禁 lambda 注册</b>（X-12，REQ-AI-07）。
 *
 * <p>X-12 的病灶：工具"注册即内置 Java lambda"使权限不可审计 —— 执行体可在
 * 咽喉 {@code ToolExecutorService} 的裁决之外自行取数/拼装任意 SQL/URL。修复形态是
 * <b>声明式</b>工具契约（{@code ScenarioToolDefinition}）：只声明端点/方法/超时/操作/
 * 权限提示，实际执行一律经咽喉分发、每次调用恰一条 security-engine 裁决。
 *
 * <p>守卫判据（对三个场景声明文件 {@code ScenarioToolDefinition} /
 * {@code ScenarioToolCatalog} / {@code ScenarioToolRegistry}，剥除注释后的可执行代码）：
 * 不得出现可执行体的携带形态 —— 老 lambda 执行器类型 {@code Function<}、自建线程/线程池
 * （{@code Executors} / {@code ExecutorService} / {@code new Thread} / {@code Runnable} /
 * {@code CompletableFuture}）、或 {@code @Scheduled}（Agent 任务自建定时，C237）。
 * 命中任一即"注册了不可审计执行体"复发 ⇒ 接缝违约。</p>
 *
 * <p>注意：本守卫只异议<b>工具执行体</b>，不禁止声明读取逻辑里的普通 lambda 表达式
 * （如 {@code Optional.map(def -> ...)}）—— 后者无 Throwable/IO/线程，不构成可取数执行面。</p>
 *
 * <p>验收：{@code mvn -Dtest=ToolContractRegistryTest#lambdaRegisteredToolRejected}</p>
 */
public class ToolContractRegistryTest {

    /** 场景受控工具的三个声明文件（相对 ai-engine-impl 的 tool 包）。 */
    private static final List<String> DECLARATION_FILES = List.of(
            "ScenarioToolDefinition.java",
            "ScenarioToolCatalog.java",
            "ScenarioToolRegistry.java");

    /**
     * 可执行体携带形态（X-12 / C237 复发判据）。
     * 以词边界/子串匹配可执行代码；这些 token 正常只应出现在 {@code ToolExecutorService}
     * 咽喉侧，绝不应出现在工具"声明"文件里。
     */
    private static final String[] FORBIDDEN_EXECUTABLE_TOKENS = {
            "Function<",
            "Executors",
            "ExecutorService",
            "new Thread",
            "Runnable",
            "CompletableFuture",
            "@Scheduled",
    };

    @Test
    void lambdaRegisteredToolRejected() throws IOException {
        Path backend = backendRoot();
        for (String file : DECLARATION_FILES) {
            Path src = locateDeclaration(backend, file);
            assertTrue(Files.isRegularFile(src), "找不到场景工具声明文件: " + src);
            String executable = stripComments(new String(Files.readAllBytes(src), StandardCharsets.UTF_8));
            for (String token : FORBIDDEN_EXECUTABLE_TOKENS) {
                assertTrue(!executable.contains(token),
                        file + " 出现可执行体携带形态 \"" + token + "\"（接缝义务 3：场景工具必须纯声明，"
                                + "执行一律经咽喉 ToolExecutorService 分发，禁内置 lambda/线程/定时执行体）");
            }
        }
    }

    // ── helpers ────────────────────────────────────────────────────────

    /** 在 ai-engine-impl 的 tool 包下定位声明文件（精确文件名，避免误配 impl 侧同名类）。 */
    private static Path locateDeclaration(Path backend, String file) {
        Path pkg = backend.resolve("engine/ai-engine/ai-engine-impl/src/main/java")
                .resolve("com/chinacreator/gzcm/engine/ai/tool").normalize();
        return pkg.resolve(file);
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

    /** 剥除 Java 块注释与行注释，保留字符串/字符字面量原文。 */
    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            char next = i + 1 < n ? src.charAt(i + 1) : 0;
            if (c == '/' && next == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && next == '*') {
                i += 2;
                while (i < n && !(src.charAt(i) == '*' && i + 1 < n && src.charAt(i + 1) == '/')) {
                    if (src.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
                i = i + 2 <= n ? i + 2 : n;
            } else if (c == '"') {
                out.append(c);
                i++;
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
                    if (sc == '"') {
                        break;
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
                    if (cc == '\'') {
                        break;
                    }
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
