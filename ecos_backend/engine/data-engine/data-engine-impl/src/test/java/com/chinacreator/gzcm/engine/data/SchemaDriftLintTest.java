package com.chinacreator.gzcm.engine.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W48 / M0《SchemaDriftLintTest》— 控制域/DQ/业务事实表的 DDL ⇔ 迁移单源 一致性（声明 ⇔ 脚本存在）。
 *
 * <p>设计 §E.2 / §E.3 / §E.4 / §E.5：DDL 单源目录 =
 * {@code gateway/src/main/resources/db/migration/}（铁律 v2.0 §3.1，禁分域另立目录）。
 * W48/D-6c：{@code dq_score_asset} 曾在 V112 声明但库内缺失（DDL 漂移）；本测试反转为守卫——
 * 对被声明的每张控制面/DQ/业务事实表（及 td_data_resource 新增列），断言其
 * <b>必</b>在至少一个 {@code V*.sql} 单源脚本中出现。缺脚本 = 声明与落地漂移 = FAIL。</p>
 *
 * <p>覆盖（与设计 E.2/E.3/E.4 对齐）：</p>
 * <ul>
 *   <li>业务事实五表：{@code ecos_biz_project_attribution / ecos_biz_stage_fact /
 *       ecos_biz_resource_fact / ecos_biz_cost_fact / ecos_forecast_input_snapshot}（E.2/V167）</li>
 *   <li>DQ 评分资产表：{@code dq_score_asset}（E.3/V165.1 补表）</li>
 *   <li>五层载体登记新列：{@code layer_bucket / carrier_ref / storage_kind}（E.4/V169）</li>
 *   <li>载体写通道幂等表：{@code td_dw_write_channel_idempotency}（F02-05/V260）</li>
 * </ul>
 */
@DisplayName("W48/M0：DDL 单源 ⇔ 声明表 一致性（缺脚本即 FAIL）")
class SchemaDriftLintTest {

    private static final String MIGRATION_REL =
            "docs/50-db_script/02-flyway-migration/gateway";
    /** V1~V349 历史脚本已归档（2026-11-18）；扫描面 = 活单源 ∪ 归档，覆盖面不缩减。 */
    private static final String LEGACY_MIGRATION_REL =
            "docs/90-归档/50-db_script/02-flyway-migration-legacy/gateway";

    /** 每张「已声明」表须出现在至少一个 V*.sql 单源脚本里。 */
    private static final List<String> EXPECTED_TABLES = List.of(
            // E.2 业务事实五表（V167__biz_fact_model.sql）
            "ecos_biz_project_attribution",
            "ecos_biz_stage_fact",
            "ecos_biz_resource_fact",
            "ecos_biz_cost_fact",
            "ecos_forecast_input_snapshot",
            // E.3 DQ 评分资产（V165.1__dq_score_asset_repair.sql）
            "dq_score_asset",
            // E.4 五层载体登记新列（V169__layer_carrier_registry.sql）
            "layer_bucket",
            "carrier_ref",
            "storage_kind",
            // F02-05 载体写通道幂等表（V260__f02_05_layer_carrier_write_channel.sql）
            "td_dw_write_channel_idempotency"
    );

    @Test
    @DisplayName("每张已声明的控制面/DQ/业务事实表在迁移单源目录至少一个 V*.sql 中出现")
    void declaredTablesHaveSingleSourceScript() throws IOException {
        Path migrationDir = findRepoRoot().resolve(MIGRATION_REL);
        assertTrue(Files.isDirectory(migrationDir), "缺迁移单源目录: " + migrationDir);

        List<String> allSql = new ArrayList<>();
        // 扫描面 = 活单源 ∪ 归档（V1~V349 已归档），文件集合与搬迁前一致
        try (Stream<Path> live = Files.list(migrationDir);
             Stream<Path> archived = legacyScripts()) {
            for (Path f : (List<Path>) Stream.concat(live, archived)
                    .filter(p -> p.getFileName().toString().startsWith("V")
                            && p.toString().endsWith(".sql")).toList()) {
                allSql.add(Files.readString(f, StandardCharsets.UTF_8));
            }
        }
        assertFalse(allSql.isEmpty(), "迁移单源目录无任何 V*.sql 脚本: " + migrationDir);
        String corpus = String.join("\n", allSql);

        List<String> missing = new ArrayList<>();
        for (String table : EXPECTED_TABLES) {
            if (!corpus.contains(table)) {
                missing.add(table);
            }
        }
        assertTrue(missing.isEmpty(),
                "W48/M0：以下已声明的控制面/DQ/业务事实表/新列在迁移单源目录无对应 V*.sql"
                        + "（DDL 声明 ⇔ 单源脚本漂移，W48 反证）：\n" + String.join("\n", missing));
    }

    /** 归档目录脚本流；目录不存在则空流（与 DdlShapeScanner 同口径）。 */
    private static Stream<Path> legacyScripts() throws IOException {
        Path legacy = findRepoRoot().resolve(LEGACY_MIGRATION_REL);
        return Files.isDirectory(legacy) ? Files.list(legacy) : Stream.empty();
    }

    static Path findRepoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path cursor = dir;
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve(".git"))
                    || Files.exists(cursor.resolve(".git"))) {
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
        throw new IllegalStateException("无法从 user.dir=" + dir + " 定位仓库根（.git/pom.xml 均缺失）");
    }
}
