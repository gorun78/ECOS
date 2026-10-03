package com.chinacreator.gzcm.gateway.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F00-15（docs/30-设计/详细设计-00-平台入口与横切底座-2026-09-28.md §F00-15 行 / 铁律 v2.0 §4，M1）
 * — 引擎取配置只经 sysman api 门面（单表分组 {@code sys.config_group} / {@code submodule}）；
 * 禁引擎自建配置/字典表。
 *
 * <p><b>范围界定（关键）：</b>sysman = 配置单源所有者（ST07）；
 * {@code sys_config} / {@code sys_dict} 表 DDL 只允许出现在 sysman。
 * gateway 迁移 V12/V13 是 sysman-scoped 表的 gateway 分身（历史聚合部署时代产物，
 * 铁律 §3.1 单目录归 gateway 迁移目录但不改表属主）。
 * 因此本规则<b>只扫描引擎（engine/）子树</b> — 引擎侧 DDL 里出现
 * {@code CREATE TABLE <*>_config / <*>_dict / <*>_parameter / <*>_settings} 即 FAIL。
 *
 * <p>运行：{@code mvn -pl gateway -am test -Dtest='ConfigSingleSourceArchTest'}
 */
class ConfigSingleSourceArchTest {

    /** 只匹配引擎侧自建配置/字典/参数表的 DDL（大小写不敏感） */
    private static final Pattern ENGINE_CONFIG_TABLE_DDL =
            Pattern.compile(
                    "(?i)create\\s+table[^;]*\\b([a-z0-9_]*_(config|dict|parameter|settings?|sysparam)[a-z0-9_]*)\\b",
                    Pattern.DOTALL
            );

    private static Path backendRoot() {
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path guess = cwd;
        while (guess != null) {
            if (Files.isDirectory(guess.resolve("gateway/src/main/java"))
                    && Files.isDirectory(guess.resolve("engine/data-engine"))) {
                return guess;
            }
            guess = guess.getParent();
        }
        return cwd;
    }

    @Test
    @DisplayName("F00-15：引擎侧 DDL 禁 CREATE TABLE <*>_config/<*>_dict/<*>_parameter/<*>_settings（单源=sysman sys_config）")
    void noEngineOwnConfigTableDdl() {
        Path root = backendRoot();
        Path engineRoot = root.resolve("engine");
        if (!Files.isDirectory(engineRoot)) {
            return; // 极端情况：无 engine 子树（在 CI 拆分构建时），扫描跳过
        }
        List<String> offenders = new ArrayList<>();
        try (var walk = Files.walk(engineRoot)) {
            walk.filter(p -> p.toString().endsWith(".sql") || p.toString().endsWith(".java"))
                .filter(p -> !p.toString().contains("target"))
                .filter(p -> !p.toString().contains("/test/"))
                .filter(p -> !p.toString().contains("archive"))
                .forEach(p -> {
                    try {
                        String content = Files.readString(p, StandardCharsets.UTF_8);
                        Matcher m = ENGINE_CONFIG_TABLE_DDL.matcher(content);
                        while (m.find()) {
                            offenders.add(root.relativize(p).toString().replace('\\', '/')
                                    + " :: CREATE " + m.group(1));
                        }
                    } catch (IOException ignored) { }
                });
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("walk 失败 " + engineRoot, e);
        }
        assertTrue(offenders.isEmpty(),
                "F00-15 红线：引擎 DDL 自建配置/字典/参数表（配置单源=sysman sys_config，config_group/submodule 分组）：\n  "
                        + String.join("\n  ", offenders));
    }
}
