package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分册09 §七 W220/C202 · 迁移脚本单源目录（铁律 v2.0 §3.1：DDL 单源 =
 * {@code gateway/src/main/resources/db/migration/}，禁第二 DDL 源分域另立）。
 *
 * <p>验收标识：{@code mvn -Dtest=MigrationSingleSourceGateTest#fcScriptsOnlyInGatewayDir}。</p>
 *
 * <p>断言：全仓（除 target）任何位置出现的 {@code fc_*} 预测域 DDL 脚本，其落点<b>必须</b>在
 * gateway 单源目录。若有人在 data-engine / runtime-access / 其他引擎目录另立 fc 迁移脚本
 * （PRD-09 §九 曾错误指向 {@code runtime/runtime-access/.../db/migration/}），本守卫 fail-loud。</p>
 *
 * <p><b>不触库、不联网</b>：纯文件扫描。</p>
 */
@DisplayName("C202 fc 迁移脚本单源目录守门（禁第二 DDL 源）")
class MigrationSingleSourceGateTest {

    /** fc 预测域脚本 / 表名特征。 */
    private static final Pattern FC_SQL_NAME = Pattern.compile("(?i)^V\\d+.*__.*fc_.*\\.sql$");

    @Test
    @DisplayName("C202/§3.1 · fc 迁移脚本只允许落在 gateway 单源目录")
    void fcScriptsOnlyInGatewayDir() {
        Path ecos = repoRoot();
        Path gatewayMigration = ecos.resolve("gateway/src/main/resources/db/migration");
        assertTrue(Files.isDirectory(gatewayMigration), "gateway 单源迁移目录缺失: " + gatewayMigration);

        List<String> strays = new ArrayList<>();
        List<Path> all;
        try (Stream<Path> walk = Files.walk(ecos)) {
            all = walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .filter(p -> !inBuildOutput(p))
                    .toList();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        for (Path p : all) {
            String fn = p.getFileName().toString();
            if (!FC_SQL_NAME.matcher(fn).matches()) continue;
            if (!p.startsWith(gatewayMigration)) {
                strays.add(ecos.relativize(p).toString());
            }
        }
        assertTrue(strays.isEmpty(),
                "C202/§3.1 违规：fc 迁移脚本落在 gateway 单源目录之外（属第二 DDL 源）:\n  "
                        + String.join("\n  ", strays));
    }

    /** 排除 Maven 构建产物（target/classes 里的 resources 副本），不算"第二 DDL 源"。 */
    private static boolean inBuildOutput(Path p) {
        for (int i = 0; i < p.getNameCount(); i++) {
            if (p.getName(i).toString().equals("target")) {
                return true;
            }
        }
        return false;
    }

    private static Path repoRoot() {
        Path cur = Path.of("").toAbsolutePath();
        while (cur != null && !Files.exists(cur.resolve("ecos_backend"))) {
            cur = cur.getParent();
        }
        Path ecos = cur.resolve("ecos_backend");
        return ecos;
    }
}
