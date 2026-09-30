package com.chinacreator.gzcm.engine.data.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F02-10 / W62 架构守卫（源码扫描，无 DB）：
 * data-engine-impl {@code src/main/**} 必须<b>不</b>出现 Spring
 * {@code @Scheduled} — DQ 告警升级 / 巡检等周期任务统一由 runtime-task 驱动
 * （铁律 §2.5 #4：data-engine 禁自调度；F02-10 设计文档 D.10 明确规定调度方
 * 由 runtime-task 注册的 dq-alert-escalation 周期任务 trigger
 * {@code DqAlertEscalationService.executeEscalationScan}）。
 *
 * <p>命中违规 = FAIL；本测试沿用仓内 {@link com.chinacreator.gzcm.engine.data.NoDriverManagerArchTest}
 * 的 source-scan 用典（{@code user.dir} 上溯 .git；命中行按 trim + 前缀忽略注释）。</p>
 *
 * @author ECOS Data Engine Team / F02-10
 */
@DisplayName("F02-10/W62 架构守卫：data-engine-impl 主源码禁 @Scheduled（非 runtime-task 严禁自调度）")
class EscalationSchedulerRegisteredInRuntimeTaskTest {

    private static final String MODULE_REL =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main";

    /** 匹配 Spring @Scheduled 注解 (紧挨方法/字段，字符串不含注解头的承认不算；
     *    简化判据：一行内出现 @Scheduled 即是违规，误命中注释行由下方 trim 判断过滤)。 */
    private static final Pattern SCHEDULED = Pattern.compile("@Scheduled\\b");

    @Test
    @DisplayName("data-engine-impl 主源码 0 处 @Scheduled（升级/巡检等周期任务统一走 runtime-task）")
    void noScheduledInDataEngineImplMain() throws IOException {
        Path root = findRepoRoot();
        Path srcMain = root.resolve(MODULE_REL);
        assertTrue(Files.isDirectory(srcMain), "缺 data-engine-impl/src/main: " + srcMain);

        List<String> violations = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(srcMain)) {
            for (Path f : (List<Path>) walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                int lin = 0;
                for (String raw : lines) {
                    lin++;
                    String t = raw.trim();
                    if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) {
                        continue;
                    }
                    if (SCHEDULED.matcher(t).find()) {
                        violations.add(f.getFileName() + ":" + lin + ": " + t);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "F02-10/W62：data-engine-impl 主源码禁 @Scheduled（收 runtime-task 调度）\n"
                        + String.join("\n", violations));
    }

    @Test
    @DisplayName("入口确认：DqAlertEscalationService 暴露 executeEscalationScan 且由 runtime-task 触发")
    void escalationEntryPointDocumentsRuntimeTaskRegistration() throws IOException {
        Path repo = findRepoRoot();
        Path svc = repo.resolve(
                "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/quality/service/DqAlertEscalationService.java");
        assertTrue(Files.isRegularFile(svc), "缺 DqAlertEscalationService 入口源码: " + svc);
        String src = Files.readString(svc, StandardCharsets.UTF_8);
        // 本用例的验收拍合：入口方法可见 + Javadoc 明确 runtime-task 驱动
        assertTrue(src.contains("executeEscalationScan"),
                "DqAlertEscalationService 必须暴露 executeEscalationScan 供 runtime-task 触发");
        assertTrue(src.contains("runtime-task"),
                "入口类 Javadoc 应明确由 runtime-task 周期任务 trigger（F02-10 规格）");
    }

    /** 从 user.dir 逐级上溯找仓根（.git 目录或文件 / pom.xml 祖先），
     *  沿用 {@code NoYamlLineageScanArchTest} 与 {@code NoDriverManagerArchTest} 安全模式。 */
    static Path findRepoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path cursor = dir;
        while (cursor != null) {
            Path git = cursor.resolve(".git");
            if (Files.isDirectory(git) || Files.isRegularFile(git)) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        cursor = dir;
        while (cursor != null) {
            if (Files.exists(cursor.resolve("pom.xml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("无法从 user.dir=" + dir + " 定位仓根（.git/pom.xml 均缺失）");
    }
}
