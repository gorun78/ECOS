package com.chinacreator.gzcm.gateway.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 计划 §三.3 / §八 红线门禁：CLI 端点<b>不得</b>进匿名白名单。
 *
 * <p>原文：「三滤波器：{@code /api/v1/} 走既有 L1 兜底，<b>无需加匿名白名单</b>
 * （不得新增 {@code AnonymousEndpointRegistry} 条目）」。</p>
 *
 * <p>为什么需要门禁：这条要求此前只写在台账里，靠人记。而匿名白名单一旦被加上，
 * CLI 预检/写命令就会绕过 JWT 与准入裁决 —— 属于「合代码时无感、上线后是安全洞」的
 * 典型形态。故此处扫单源注册表本身，任何指向 {@code /cli} 的条目直接 FAIL。</p>
 *
 * <p>反方向也钉：{@code /api/v1/cli/preflight} 必须真的不在豁免名录里
 * （防止有人用通配符顺带把整块 cli 前缀豁免）。</p>
 */
class CliEndpointNotAnonymousTest {

    private static final String REGISTRY_REL =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/security/"
                    + "anonymous/AnonymousEndpointRegistry.java";

    private static Path repoRoot() throws IOException {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("ecos_backend"))) {
            Path parent = p.getParent();
            if (parent == null || parent.equals(p)) {
                break;
            }
            p = parent;
        }
        assertNotNull(p, "cwd 未锚定 ECOS 仓库根（找不到 ecos_backend/）");
        assertTrue(Files.isDirectory(p.resolve("ecos_backend")), "cwd 未锚定 ECOS 仓库根");
        return p;
    }

    private static List<String> regLines(Path root) throws IOException {
        Path file = root.resolve(REGISTRY_REL);
        assertTrue(Files.isRegularFile(file), "匿名白名单单源文件不存在: " + file);
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    /** 只看 reg("...") 实参行，注释里提到 /cli 不算违规（文档说明是允许的）。 */
    private static final Pattern REG_CALL = Pattern.compile("^\\s*reg\\(\\s*\"([^\"]+)\"");

    @Test
    @DisplayName("AnonymousEndpointRegistry 不得登记任何 /cli 路径（ANONYMOUS 与 CLEARANCE_EXEMPT 都不行）")
    void noCliPatternInAnonymousRegistry() throws IOException {
        List<String> offending = regLines(repoRoot()).stream()
                .map(line -> REG_CALL.matcher(line))
                .filter(java.util.regex.Matcher::find)
                .map(m -> m.group(1))
                .filter(pattern -> pattern.toLowerCase(java.util.Locale.ROOT).contains("cli"))
                .toList();
        assertTrue(offending.isEmpty(),
                "计划 §八 红线：CLI 端点不得进匿名/豁免白名单，发现登记项 " + offending);
    }

    @Test
    @DisplayName("preflight 与 L3 写命令路径都不得被通配豁免（防止 /api/v1/** 之类顺带豁免）")
    void noWildcardSwallowsCliPaths() throws IOException {
        List<String> patterns = regLines(repoRoot()).stream()
                .map(line -> REG_CALL.matcher(line))
                .filter(java.util.regex.Matcher::find)
                .map(m -> m.group(1))
                .toList();
        List<String> cliPaths = List.of(
                "/api/v1/cli/preflight",
                "/api/v1/wagent/candidates/{candidateId}/publish",
                "/api/v1/wagent/actions/{actionId}/commit");
        for (String path : cliPaths) {
            boolean swallowed = patterns.stream().anyMatch(p -> matches(path, p));
            assertFalse(swallowed, path + " 被匿名/豁免规则吞掉了（命中规则见 " + patterns + "）");
        }
    }

    /** 只实现注册表实际用到的两种通配：`**` 后缀与单段 `*`。故意不追求完备 —— 宁可漏判也不误判。 */
    private static boolean matches(String path, String pattern) {
        if (pattern.endsWith("/**")) {
            return path.startsWith(pattern.substring(0, pattern.length() - 3));
        }
        String regex = "^" + Pattern.quote(pattern).replace("*", "\\E[^/]*\\Q") + "$";
        return path.matches(regex);
    }

    @Test
    @DisplayName("检测逻辑可被触发（合成夹具）")
    void detectorIsNotVacuous() throws IOException {
        // 用真实文件构造「若被加了条目」的形态，证明上面的断言不是恒真
        List<String> synthetic = List.of(
                "        reg(\"/api/v1/cli/**\",     Category.ANONYMOUS, true,",
                "                \"假想豁免\", \"用户\", \"2026-11-11\");");
        List<String> caught = synthetic.stream()
                .map(line -> REG_CALL.matcher(line))
                .filter(java.util.regex.Matcher::find)
                .map(m -> m.group(1))
                .filter(p -> p.toLowerCase(java.util.Locale.ROOT).contains("cli"))
                .toList();
        assertTrue(caught.size() == 1, "扫描器对 reg(\"/api/v1/cli/**\") 失明: " + caught);
        assertTrue(matches("/api/v1/cli/preflight", "/api/v1/cli/**"),
                "通配判定漏了 ** 形态");
    }

    @Test
    @DisplayName("CLI Controller 源码树不得出现 permitAll/匿名注解（双保险）")
    void cliControllersCarryNoAnonymousMarkers() throws IOException {
        Path root = repoRoot();
        Path ctrl = root.resolve("ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/"
                + "controller/CliPreflightController.java");
        assertTrue(Files.isRegularFile(ctrl), "预检 Controller 不存在: " + ctrl);
        String src = Files.readString(ctrl, StandardCharsets.UTF_8);
        for (String marker : List.of("permitAll", "@PreAuthorize(\"permitAll", "AnonymousEndpointRegistry.reg")) {
            assertFalse(src.contains(marker), "预检 Controller 出现匿名标记: " + marker);
        }
        try (Stream<Path> walk = Files.walk(root.resolve("ecos_backend/gateway/src/main/java"))) {
            List<Path> cliSources = walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return Files.readString(p, StandardCharsets.UTF_8).contains("/api/v1/cli");
                        } catch (IOException e) {
                            return false;
                        }
                    }).toList();
            assertFalse(cliSources.isEmpty(), "未找到任何挂 /api/v1/cli 的网关源码（扫描器失效）");
        }
    }
}
