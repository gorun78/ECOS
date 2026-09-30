package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-00 F00-15 — 配置/字典单源门禁（sysman 门面唯一出口）。
 *
 * <p>守卫目标：引擎不得<b>直连</b> {@code sys_config} 表（读/写/JC），
 * 一律经 sysman 面（{@code SysConfigController} {@code /api/v1/system/config}
 * 或 {@code SysConfigService} API 门面）。当前仓库实测有 4 处<b>脏读/bypass</b>
 * 直连（cognitive 配置自造 ORM / data-engine 的 DQ 定时任务 + Git 归档），
 * 需按 F00-15 的收口口径切断。
 *
 * <p>断言语义（<b>no-new-violation</b> 不阻断本次收口分批）：
 * <ol>
 *   <li>全仓源扫所有 {@code *.java} 中 {@code FROM|JOIN|UPDATE|INSERT INTO sys_config}
 *       是否落在 <b>基线登记集</b> B 之外 → 新增 FAIL，存量只减不增；</li>
 *   <li>基线 B 反证自己：基线里每个文件仍存在且命中一次 {@code sys_config}
 *       （防止基线膨胀：若有人修掉就删基线行，若新增就报警）。</li>
 * </ol>
 *
 * <p>验收：{@code mvn -pl gateway -am test -Dtest='ConfigSingleSourceArchTest'}</p>
 */
class ConfigSingleSourceArchTest {

    /** 相对 ecos_backend 的路径，按当前 HEAD 实测存在且含 {@code sys_config}。只减不增。 */
    private static final Set<String> KNOWN_VIOLATION_BASELINE = new TreeSet<>(Set.of(
            "engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/CognitiveConfigQueryService.java",
            "engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/scheduler/DqScheduledTask.java",
            "engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/metadata/MetadataCollectGitArchive.java",
            "workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/knowledge/service/KnowledgeConfigAggregator.java"
    ));

    /** 白名单：允许触达 sys_config 表的应用层（面自身的实现）。 */
    private static final Set<String> LEGITIMATE_ACCESSORS = new TreeSet<>(Set.of(
            // sysman 面本体
            "services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/config/service/impl/SysConfigService.java",
            "services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/controller/SysConfigController.java",
            "services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/service/SysConfigQueryService.java",
            "services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/controller/UserController.java",
            // gateway 内置面板的 sysman-facade 直通（组织/认证鉴权面，非引擎）
            "gateway/src/main/java/com/chinacreator/gzcm/gateway/controller/SysConfigController.java",
            "gateway/src/main/java/com/chinacreator/gzcm/gateway/service/GatewaySysConfigService.java"
    ));

    private static final Pattern SYS_CONFIG_SQL =
            Pattern.compile("(FROM|JOIN|UPDATE|INSERT\\s+INTO)\\s+([A-Z0-9_$]*\\.)?sys_config",
                    Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("新增 FROM/JOIN/UPDATE/INSERT sys_config 直连 → FAIL（基线外）")
    void noNewDirectSysConfigSql() throws IOException {
        Path root = resolveBackendRoot();
        Set<String> offenders = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".java"))
                .forEach(p -> {
                    String s = p.toString().replace('\\', '/');
                    if (s.contains("/target/")) return;
                    if (s.contains("/src/test/")) return;
                    if (!SYS_CONFIG_SQL.matcher(read(p)).find()) return;
                    String rel = root.relativize(p).toString().replace('\\', '/');
                    if (LEGITIMATE_ACCESSORS.contains(rel)      // 面本体
                            || KNOWN_VIOLATION_BASELINE.contains(rel)) {  // 存量基线
                        return;
                    }
                    offenders.add(rel);
                });
        }
        assertTrue(offenders.isEmpty(),
                "F00-15 违规：以下文件新增/躺外直连 sys_config（应收口到 SysConfigService 或 sysman REST 面）: "
                        + String.join("\n  ", offenders));
    }

    @Test
    @DisplayName("基线反证：每条基线文件仍存在且仍命中 sys_config（防止过度声明/已收口未删基线）")
    void baselineEntriesStillViolate() throws IOException {
        Path root = resolveBackendRoot();
        Set<String> stale = new TreeSet<>();
        for (String rel : KNOWN_VIOLATION_BASELINE) {
            Path p = root.resolve(rel);
            if (!Files.exists(p)) {
                stale.add(rel + "（文件已删除 → 基线可删）");
                continue;
            }
            if (!SYS_CONFIG_SQL.matcher(read(p)).find()) {
                stale.add(rel + "（已修复 → 基线应删）");
            }
        }
        assertTrue(stale.isEmpty(),
                "基线登记已过时（须同步减基线）→ " + String.join("\n  ", stale));
    }

    private static Path resolveBackendRoot() {
        Path cur = Paths.get("").toAbsolutePath();
        while (cur != null) {
            if (Files.exists(cur.resolve("pom.xml")) && Files.exists(cur.resolve("gateway"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        return Paths.get("..").toAbsolutePath().normalize();
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            return "";
        }
    }
}
