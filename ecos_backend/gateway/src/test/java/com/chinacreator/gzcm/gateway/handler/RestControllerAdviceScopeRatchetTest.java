package com.chinacreator.gzcm.gateway.handler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * #142/B4-3e-15（差距 D-B4-52 (b) 解除条件）— <b>全局 advice 域棘轮</b>门。
 *
 * <p>Spring 的 {@code ExceptionHandlerExceptionResolver} 先按 advice 的 Order 再按
 * 「最具体异常类型」选处理器。当两支 advice 都<b>未限定包域</b>且都声明处理同一根异常
 * （{@code DataBridgeException}）时，命中哪支属未定义行为：收口前 {@code KbErrorAdvice}
 * 的注解是裸 {@code @RestControllerAdvice}（注释却写着"仅在 kb 包范围内生效"），
 * 于是<b>别域</b>的 403／503 可能被改写为 502 + {@code ECOS-KB-501} —— 跨域改写 5xx 的不确定面。</p>
 *
 * <p>本门把「advice 必须显式限定域」钉成静态契约：除 allowlist 中的组合面兜底
 * （gateway 版 / sysman-boot 版 GlobalExceptionHandler）外，任何 {@code @RestControllerAdvice}
 * 必须声明 {@code basePackages}/{@code basePackageClasses}/{@code assignableTypes}/{@code value}
 * 之一。allowlist <b>只减不增</b>。</p>
 *
 * <p>判据只看代码行：注释里的 {@code @RestControllerAdvice} 提及（如 {@code KbErrorCodes} 类注释）
 * 不计入 —— 注解在源码里恒置于行首，注释行以 {@code *} 或 {@code /} 起始。跨行书写的注解
 * （如 {@code CognitiveErrorAdvice}／{@code WAgentOrchestrationAdvice}）按括号配对续行读取，
 * 避免把已限定域的类误判成违例。{@code @Order} 不参与 advice 识别，只对 KbErrorAdvice 专项断言。</p>
 */
class RestControllerAdviceScopeRatchetTest {

    /** 组合面兜底 advice：有意全局（catch-all 语义），只允许减少。 */
    private static final Set<String> GLOBAL_ADVICE_ALLOWLIST = Set.of(
            "ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/handler/GlobalExceptionHandler.java",
            "ecos_backend/services/sysman/impl/sysman-boot/src/main/java/com/chinacreator/gzcm/sysman/boot/handler/GlobalExceptionHandler.java");

    /** 限定域 advice 的基线清单（防"文件被删/改名 ⇒ 扫描集变小 ⇒ 假绿"）。 */
    private static final Set<String> SCOPED_ADVICE_BASELINE = Set.of(
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/controller/AiErrorAdvice.java",
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/wagent/web/WAgentOrchestrationAdvice.java",
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/controller/ContextBuildErrorAdvice.java",
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/controller/scenario/CognitiveErrorAdvice.java",
            "ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/support/KbErrorAdvice.java",
            "ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/exception/WorkspaceExceptionHandler.java");

    private static final String[] SCOPE_ATTRS =
            {"basePackages", "basePackageClasses", "assignableTypes", "value", "packages"};

    @Test
    @Timeout(120)
    @DisplayName("除 allowlist 兜底外，每支 @RestControllerAdvice 必须显式限定包域（D-B4-52 跨域改写面）")
    void everyNonAllowlistedAdviceDeclaresExplicitScope() throws IOException {
        List<String> unscoped = new ArrayList<>();
        Set<String> found = new LinkedHashSet<>();

        for (Path file : backendMainSources()) {
            String rel = relativize(file);
            if (!hasActiveAdviceAnnotation(file)) {
                continue;
            }
            found.add(rel);
            boolean scoped = declaresScopeAttr(file);
            if (!scoped && !GLOBAL_ADVICE_ALLOWLIST.contains(rel)) {
                unscoped.add(rel);
            }
        }

        assertTrue(unscoped.isEmpty(),
                "发现未限定域的全局 advice（与 gateway 兜底同声明处理 DataBridgeException ⇒ 命中顺序未定义，"
                        + "别域 403/503 可被改写 5xx）：" + unscoped);

        // 扫描集必须与基线同号：少一支＝文件消失/改名（门禁失效），多一支＝新增未登记 advice
        Set<String> expected = new LinkedHashSet<>(GLOBAL_ADVICE_ALLOWLIST);
        expected.addAll(SCOPED_ADVICE_BASELINE);
        assertEquals(expected, found,
                "advice 组合面清单与基线不一致（基线属主需同批订正，禁默默放宽）");
    }

    @Test
    @DisplayName("allowlist 每一项必须仍是真实存在且未限定域的兜底 advice（陈旧豁免＝假绿）")
    void allowlistEntriesAreStillRealGlobalAdvices() throws IOException {
        for (String rel : GLOBAL_ADVICE_ALLOWLIST) {
            Path p = repoFile(rel);
            assertTrue(hasActiveAdviceAnnotation(p), rel + " 必须仍是带 @RestControllerAdvice 的类");
            assertTrue(!declaresScopeAttr(p), rel + " 已被限定域 ⇒ 应移出 allowlist（棘轮只减不增）");
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("KbErrorAdvice 专项：basePackages 限 kb 包 + @Order 显式（注释承诺须由注解兑现）")
    void kbErrorAdviceCarriesBothScopeAndOrder() throws IOException {
        String rel = "ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/support/KbErrorAdvice.java";
        Path file = repoFile(rel);
        String code = String.join(" | ", adviceAnnotationBlocks(file));

        assertTrue(code.contains("com.chinacreator.gzcm.engine.kb"),
                "KbErrorAdvice 必须把生效域限定在 kb 包，实际注解：" + code);
        assertTrue(orderLines(file).contains("@Order"),
                "KbErrorAdvice 必须显式 @Order（与 CognitiveErrorAdvice/ContextBuildErrorAdvice 先例一致），"
                        + "否则与 gateway 兜底的相对顺序仍属未定义");
    }

    // ── 辅助 ────────────────────────────────────────────────────────────────

    /** 逐行取「行首即 advice 注解」的行，并把跨行注解按括号配对续读完整。 */
    private static List<String> adviceAnnotationBlocks(Path file) throws IOException {
        List<String> raw = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<String> blocks = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            String head = raw.get(i).stripLeading();
            if (!head.startsWith("@RestControllerAdvice") && !head.startsWith("@ControllerAdvice")) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            int depth = 0;
            for (int j = i; j < raw.size(); j++) {
                String s = raw.get(j).trim();
                sb.append(s).append(' ');
                for (int k = 0; k < s.length(); k++) {
                    char c = s.charAt(k);
                    if (c == '(') {
                        depth++;
                    } else if (c == ')') {
                        depth--;
                    }
                }
                if (depth <= 0) {
                    break;
                }
            }
            blocks.add(sb.toString());
        }
        return blocks;
    }

    /** 行首 {@code @Order} 行（仅用于 KbErrorAdvice 专项，不计入 advice 识别）。 */
    private static String orderLines(Path file) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String t = line.stripLeading();
            if (t.startsWith("@Order")) {
                sb.append(t).append('\n');
            }
        }
        return sb.toString();
    }

    private static boolean hasActiveAdviceAnnotation(Path file) throws IOException {
        return !adviceAnnotationBlocks(file).isEmpty();
    }

    private static boolean declaresScopeAttr(Path file) throws IOException {
        for (String block : adviceAnnotationBlocks(file)) {
            for (String attr : SCOPE_ATTRS) {
                if (block.contains(attr + " =") || block.contains(attr + "=")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<Path> backendMainSources() throws IOException {
        Path root = repoRoot().resolve("ecos_backend");
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> norm(p).contains("/src/main/java/"))
                    .filter(p -> !norm(p).contains("/target/"))
                    .filter(p -> !norm(p).contains("/archive/"))
                    .forEach(files::add);
        }
        return files;
    }

    private static String norm(Path p) {
        return p.toAbsolutePath().toString().replace('\\', '/');
    }

    /** 仓根相对路径：不得调用 toAbsolutePath（相对路径会被解析到 surefire 的 user.dir＝模块目录，产生 doubled 路径）。 */
    private static String relativize(Path p) {
        return repoRoot().relativize(p.toAbsolutePath()).toString().replace('\\', '/');
    }

    private static Path repoFile(String relative) {
        Path p = repoRoot().resolve(relative);
        if (!Files.isRegularFile(p)) {
            fail("门禁依赖的文件不存在，不得当作\"无违例\": " + relative);
        }
        return p;
    }

    private static Path repoRoot() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 12 && start != null; i++) {
            if (Files.isDirectory(start.resolve("ecos_backend"))
                    && Files.isDirectory(start.resolve("ecos-docker"))) {
                return start;
            }
            start = start.getParent();
        }
        return fail("未能从 user.dir 定位仓根（需同时含 ecos_backend 与 ecos-docker），本门禁不得默认通过");
    }
}
