package com.chinacreator.gzcm.workspace.scenario;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V07 F07-08/09 隔离红线等价护栏（对应设计 §8.1 line 1320
 * 「SandboxWriteGuardArchTest — ArchUnit 谓词」 + §7.3 C154「SANDBOX 写白名单 ArchUnit 谓词
 * (X-34) 依赖 F07-24/P-2 底座」的**离线半**）。
 *
 * 为什么不用 ArchUnit 依赖：
 * - workspace-impl 未有 archunit 依赖（禁加新 Maven 依赖以免破坏
 *   check-legacy-modules.ps1 基线 12 + 三档 Maven profile 裁剪）；
 * - 用 regex 源码扫描等价同一红线（同 R9 「X-34 双写禁令·一门意一门落」原则）：
 *   同等守护"workspace 主包绝对零写入库 D 层五事实表 + outcome 表"。
 *
 * 判定：workspace-impl 主包内（含 JdbcTemplate 内嵌 SQL 字符串）
 *   出现 `INSERT INTO ecos_biz_*` / `UPDATE ecos_biz_*`
 *            / `DELETE FROM ecos_biz_*` / `TRUNCATE ecos_biz_*`
 *   任一命中 → 红线破 → 红。
 *
 * X-34 单源纪律：如日后要额外拦 `ecos_scenario_*` 域，本类**就地扩**，
 * 禁在别的类重复造一条 ArchUnit 版本。
 */
class SandboxWriteGuardArchTest {

    /** 写动词 + `ecos_biz_` 家族（最保守正则，避免过窄漏检）。 */
    private static final Pattern WRITE_VERB_ON_BIZ = Pattern.compile(
            "(?i)\\b(INSERT\\s+INTO|UPDATE|DELETE\\s+FROM|TRUNCATE(?:\\s+TABLE)?)\\s+(?:[A-Za-z0-9_]+\\.)?ecos_biz_");

    @Test
    void noDirectWriteToBusinessFactsTablesAnywhere() throws IOException {
        List<String> hits = scanMainSources(WRITE_VERB_ON_BIZ).entrySet().stream()
                .map(e -> e.getKey() + " ↷ " + String.join(" | ", e.getValue()))
                .toList();
        assertTrue(hits.isEmpty(),
                "workspace 主包内发现对 D 层 `ecos_biz_*` 的写动词（本线要求写通道走"
                        + " runtime-access/data-engine 门面，禁 workspace 直接 JdbcTemplate 写）：\n  "
                        + String.join("\n  ", hits));
    }

    /** 由上：sb = 稀疏名反锚点，另锁 outcome 子线。 */
    @Test
    void noForbiddenFactTableWriteInWorkspace() throws IOException {
        Pattern outcome = Pattern.compile(
                "(?i)(INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\s+(?:[A-Za-z0-9_]+\\.)?ecos_biz_action_outcome\\b");
        List<String> hits = scanMainSources(outcome).entrySet().stream()
                .map(e -> e.getKey() + " ↷ " + String.join(" | ", e.getValue()))
                .toList();
        assertTrue(hits.isEmpty(),
                "对 `ecos_biz_action_outcome` 的直接写动作应在数据域经门面生效，本条零容忍：\n  "
                        + String.join("\n  ", hits));
    }

    // helpers

    static java.util.Map<Path, List<String>> scanMainSources(Pattern p) throws IOException {
        java.util.Map<Path, List<String>> out = new java.util.LinkedHashMap<>();
        Path impl = monorepoRoot().resolve("ecos_backend/workspace/workspace-impl/src/main");
        Path svc = monorepoRoot().resolve("ecos_backend/workspace/workspace-service/src/main");
        collect(impl, p, out);
        collect(svc, p, out);
        return out;
    }

    private static void collect(Path rootDir, Pattern p, java.util.Map<Path, List<String>> out) throws IOException {
        if (!Files.isDirectory(rootDir)) return;
        Files.walk(rootDir)
                .filter(Files::isRegularFile)
                .filter(f -> f.toString().endsWith(".java"))
                .forEach(f -> {
                    try {
                        List<String> lines = Files.readAllLines(f);
                        List<String> hits = new ArrayList<>();
                        for (int i = 0; i < lines.size(); i++) {
                            if (p.matcher(lines.get(i)).find()) {
                                hits.add((i + 1) + ": " + lines.get(i).trim());
                            }
                        }
                        if (!hits.isEmpty()) {
                            out.put(f, hits);
                        }
                    } catch (IOException ignored) {
                    }
                });
    }

    private static Path monorepoRoot() {
        String override = System.getenv("ECOS_ROOT");
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        return Paths.get("").toAbsolutePath().getParent().getParent().getParent();
    }
}
