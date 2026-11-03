package com.chinacreator.gzcm.gateway.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Secret Scanning 门（§2.9.9 G6「无 Secret Scanning + 明文口令在仓」收口；#147AG-G6）。
 *
 * <p><b>定位</b>：只读扫描 + 「存量登记、禁新增」。不引入外部扫描器（那属新依赖/新工具链，须单独裁决），
 * 而是把「代码/脚本/种子/配置里不得出现凭据材料」变成构建期可判红断言。</p>
 *
 * <p><b>扫描面</b>：{@code ecos_backend}（源码与资源）、{@code ecos_frontend/src}、{@code ecos-tests}、
 * {@code ecos-docker}、{@code _win_tasks}、{@code docs/50-db_script}、{@code tools} 及仓根单层文件。
 * 跳过 {@code target/}、{@code node_modules/}、{@code dist/}、{@code .git/}。</p>
 *
 * <p><b>豁免（存量登记，改动须同批留证）</b>：① 文档（{@code docs} 下全部 Markdown、仓根 Markdown、
 * 技能说明）——引用 dev 凭据是文档事实，不是可用凭据；② 凭据规范 {@code .trae/rules/开发环境登录凭据.md}
 * 属红线文件（任何 Agent 不得修改）；③ {@link #REGISTERED_PATHS} 中逐条登记的 dev 种子/压测脚本/探针体
 * ——它们是本地开发装置，含 dev 口令，禁令只针对**新增**。</p>
 *
 * <p><b>未覆盖（如实声明）</b>：提交前钩子与发布前扫描未接（本门只在构建/合并期跑）；
 * 二进制与压缩包不扫描；不校验已登记口令的轮换。</p>
 *
 * @author ECOS Gateway #147G6
 */
class SecretScanGateTest {

    /** 高危凭据形态（命中即视为凭据材料，不打印命中值）。 */
    private static final List<Pattern> PATTERNS = List.of(
            // 拼接构造，避免本文件自身命中（自扫）
            Pattern.compile("admin" + "123"),
            // PEM 头 + 真实密钥体：只匹配正则/常量里的头（如 JwtTokenProvider 的解析器）不算凭据材料
            Pattern.compile("-----BEGIN (?:RSA |OPENSSH |EC |DSA )?PRIVATE KEY-----[\\s\\S]{0,40}[A-Za-z0-9+/=]{64,}"),
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            Pattern.compile("\\bsk-[A-Za-z0-9]{20,}\\b"),
            Pattern.compile("\\bghp_[A-Za-z0-9]{30,}\\b"));

    /** 存量登记（dev 装置与红线文件）；新增登记必须写明理由并入册。 */
    private static final Set<String> REGISTERED_PATHS = new LinkedHashSet<>(List.of(
            ".trae/rules/开发环境登录凭据.md",
            "AGENTS.md",
            "login_body.json",
            "ecos_backend/audit_script.py",
            "ecos-docker/k6-72h.js",
            "ecos-docker/seed-2h.sh",
            "ecos-docker/soak-72h.sh",
            "docs/50-db_script/05-service/sysman/seed.sql",
            "docs/50-db_script/08-database-util/seed.sql",
            "docs/50-db_script/02-flyway-migration/gateway/V16__ecos_role_password_fix.sql",
            ".qoder/skills/pm-auto-deploy/scripts/verify_api.py",
            ".trae/skills/pm-auto-deploy/scripts/verify_api.py",
            // 扫描器自身：本文件承载全部匹配模式字面量与合成反证样例，必然自命中
            "ecos_backend/gateway/src/test/java/com/chinacreator/gzcm/gateway/arch/SecretScanGateTest.java"));

    /** 扫描面下限（防路径漂移把门变空转）。 */
    private static final int MIN_SCANNED_FILES = 300;

    @Test
    @DisplayName("代码/脚本/种子/配置零新增凭据材料（存量按登记豁免）")
    void noNewSecretMaterialOutsideRegistry() throws IOException {
        Path root = repoRoot();
        Set<String> unregistered = new LinkedHashSet<>();
        int scanned = 0;

        for (Path dir : roots(root)) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> s = Files.walk(dir)) {
                for (Path file : s.filter(Files::isRegularFile).toList()) {
                    if (check(root, file, unregistered)) {
                        scanned++;
                    }
                }
            }
        }
        // 仓根单层文件（探针体/根脚本等）
        try (Stream<Path> s = Files.list(root)) {
            for (Path file : s.filter(Files::isRegularFile).toList()) {
                if (check(root, file, unregistered)) {
                    scanned++;
                }
            }
        }

        assertTrue(scanned >= MIN_SCANNED_FILES,
                "扫描面退化：只扫到 " + scanned + " 个文件（基线 " + MIN_SCANNED_FILES + "）——路径漂移会让本门空转");
        assertTrue(unregistered.isEmpty(),
                "发现未登记的凭据材料（禁新增；确属 dev 装置请连同理由登记到 REGISTERED_PATHS）：\n  "
                        + String.join("\n  ", unregistered));
    }

    @Test
    @DisplayName("反证：判据能变红（合成样例），文档豁免不放行代码文件")
    void predicateCatchesSecretsAndDocsExemptionIsNarrow() {
        assertTrue(detectsForTest("password = \"admin" + "123\";"));
        assertTrue(detectsForTest("-----BEGIN OPENSSH PRIVATE KEY-----\n"
                + "b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW\n"), "含真实密钥体必须命中");
        assertTrue(!detectsForTest("Pattern.compile(\"-----BEGIN\\\\s+PRIVATE\\\\s+KEY-----\")"),
                "仅解析 PEM 头的正则不是凭据材料，不得误报");
        assertTrue(detectsForTest("key AKIAABCDEFGHIJKLMNOP"));
        assertTrue(!detectsForTest("password = System.getenv(\"DB_PASSWORD\");"),
                "读环境变量不是凭据材料，不得误报");

        assertTrue(isDocumentation("docs/30-设计/某设计.md"));
        assertTrue(!isDocumentation("ecos_backend/gateway/src/main/java/A.java"),
                "代码文件不得因“像文档”而豁免");
        assertTrue(!isDocumentation("ecos-docker/seed-2h.sh"));
    }

    // ── 工具 ──────────────────────────────────────────────────────────

    /** 检查单个文件；返回 true 表示计入扫描面（跳过的不计）。 */
    private static boolean check(Path root, Path file, Set<String> unregistered) {
        if (skip(file)) {
            return false;
        }
        String rel = root.relativize(file).toString().replace('\\', '/');
        if (isDocumentation(rel) || REGISTERED_PATHS.contains(rel)) {
            return true;
        }
        String text = readQuietly(file);
        if (text == null) {
            return true;
        }
        for (Pattern p : PATTERNS) {
            if (p.matcher(text).find()) {
                unregistered.add(rel + "  [" + p.pattern() + "]");
                break;
            }
        }
        return true;
    }

    private static boolean detectsForTest(String text) {
        return PATTERNS.stream().anyMatch(p -> p.matcher(text).find());
    }

    private static boolean isDocumentation(String rel) {
        return rel.endsWith(".md") && (rel.startsWith("docs/") || rel.startsWith(".qoder/")
                || rel.startsWith(".trae/") || !rel.contains("/"));
    }

    private static List<Path> roots(Path root) {
        return List.of(
                root.resolve("ecos_backend"),
                root.resolve("ecos_frontend").resolve("src"),
                root.resolve("ecos-tests"),
                root.resolve("ecos-docker"),
                root.resolve("_win_tasks"),
                root.resolve("docs").resolve("50-db_script"),
                root.resolve("tools"));
    }

    private static boolean skip(Path file) {
        String p = file.toString().replace('\\', '/');
        String name = file.getFileName().toString();
        return p.contains("/target/") || p.contains("/node_modules/") || p.contains("/dist/")
                || p.contains("/.git/") || p.endsWith(".jar") || p.endsWith(".zip") || p.endsWith(".png")
                || p.endsWith(".jpg") || p.endsWith(".lock")
                // 本地 .env（含 dev 凭据）不入库、不属提交面；入库模板仍是 .env.example，照常扫描
                || ".env".equals(name);
    }

    private static String readQuietly(Path file) {
        try {
            long size = Files.size(file);
            if (size > 2_000_000L) {
                return null;
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static Path repoRoot() {
        Path cur = Paths.get("").toAbsolutePath().normalize();
        for (int i = 0; i < 8 && cur != null; i++) {
            if (Files.isDirectory(cur.resolve("ecos_backend")) && Files.isDirectory(cur.resolve("ecos-docker"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("未找到仓库根，CWD=" + Paths.get("").toAbsolutePath());
    }
}
