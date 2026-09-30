package com.chinacreator.gzcm.engine.data.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F02-08 / W49 / ST07 / MC06 架构守卫《QualifiedTableNameArchTest》—
 * 数据域写入的表名必须限定到引擎 schema，禁止裸表名 / 禁止落 public 桶。
 *
 * <p>设计 §E.1（R-1 a 已批准）+ §E.2：数据域新表一律落引擎 schema
 * （控制域 {@code ecos_data}，业务域五层 {@code ecos_dw}）。W49 / D-7 实证：
 * 同名表在 {@code public} + 引擎 schema 双镜像、写全落 public。R-1 a 裁决：
 * 「新写落引擎 schema，public 只停写」—— 故源码/迁移单源内数据域表名<b>必须</b>带
 * schema 限定名（限定名来自配置 MC06），裸 {@code public.} = FAIL（§四 附则 1）。</p>
 *
 * <p>守住三条真实不变量（当前代码已合规，本测试永久守卫）：</p>
 * <ol>
 *   <li>Mapper XML：任一数据域表 token（{@code ecos_biz_*} / {@code ecos_data_lineage*} /
 *       {@code ecos_forecast_*}）前必须紧跟 {@code <schema>}. 限定（如
 *       {@code ecos_dw.ecos_biz_project_attribution}）。</li>
 *   <li>Mapper XML：禁任何 {@code public.} 前缀（数据域读/写禁落 public 桶）。</li>
 *   <li>迁移单源：新建数据域表（{@code CREATE TABLE ... ecos_biz_*} / {@code ecos_forecast_*}）
 *       必须带 schema 限定；全目录禁数据域表落 {@code public.ecos_(biz|dq|data|dw)_}。</li>
 * </ol>
 */
@DisplayName("F02-08/W49/ST07/MC06：数据域表名限定到引擎 schema（禁裸名/禁 public）")
class QualifiedTableNameArchTest {

    /** 数据域业务表 token（不含 schema 前缀）。匹配命中后要求其被 {@code <schema>}. 限定。 */
    private static final Pattern DATA_DOMAIN_TABLE = Pattern.compile(
            "\\b(?:ecos_biz_[a-z0-9_]+|ecos_data_lineage[a-z0-9_]*|ecos_forecast_[a-z0-9_]+)\\b");

    /** 数据域表落 public 桶 = W49 违规。 */
    private static final Pattern PUBLIC_BUCKET_WRITE = Pattern.compile(
            "public\\s*\\.\\s*(?:ecos_(?:biz|dq|data|dw)_|td_dw_write_channel)\\b",
            Pattern.CASE_INSENSITIVE);

    /** 迁移单源：CREATE TABLE 直连数据域生态表名（无 schema 限定）= 违规。 */
    private static final Pattern UNQUALIFIED_CREATE = Pattern.compile(
            "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?"
                    + "(ecos_biz_[a-z0-9_]+|ecos_forecast_[a-z0-9_]+)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final String MAPPER_REL =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/resources/mapper";
    private static final String MIGRATION_REL =
            "ecos_backend/gateway/src/main/resources/db/migration";

    @Test
    @DisplayName("Mapper XML 数据域表名必须 schema 限定 + 禁 public 桶")
    void mapperDataDomainTablesAreSchemaQualified() throws IOException {
        Path mapperDir = findRepoRoot().resolve(MAPPER_REL);
        assertTrue(Files.isDirectory(mapperDir), "缺 mapper 目录: " + mapperDir);
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(mapperDir)) {
            for (Path f : (List<Path>) files.filter(p -> p.toString().endsWith(".xml")).toList()) {
                List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                int lin = 0;
                for (String raw : lines) {
                    lin++;
                    String t = raw.trim();
                    if (t.startsWith("--") || t.startsWith("<!--")) {
                        continue;
                    }
                    if (PUBLIC_BUCKET_WRITE.matcher(raw).find()) {
                        violations.add(f.getFileName() + ":" + lin + ": public 桶写入: " + t);
                    }
                    // 数据域表 token 前必须是 <schema>. 限定（点号），否则裸表名违规
                    Matcher m = DATA_DOMAIN_TABLE.matcher(raw);
                    while (m.find()) {
                        int start = m.start();
                        if (start == 0 || raw.charAt(start - 1) != '.') {
                            violations.add(f.getFileName() + ":" + lin
                                    + ": 裸数据域表名(无 schema 限定): " + m.group());
                        }
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "F02-08/W49/MC06：数据域 Mapper 表名必须限定到引擎 schema"
                        + "（ecos_dw.*/ecos_data.*），禁 public 桶：\n" + String.join("\n", violations));
    }

    @Test
    @DisplayName("迁移单源 CREATE TABLE 数据域表必须 schema 限定 + 禁 public 桶")
    void migrationDataDomainDdlIsSchemaQualified() throws IOException {
        Path migrationDir = findRepoRoot().resolve(MIGRATION_REL);
        assertTrue(Files.isDirectory(migrationDir), "缺迁移目录: " + migrationDir);
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.list(migrationDir)) {
            for (Path f : (List<Path>) files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                int lin = 0;
                for (String raw : lines) {
                    lin++;
                    String t = raw.trim();
                    if (t.startsWith("--")) {
                        continue;
                    }
                    if (PUBLIC_BUCKET_WRITE.matcher(raw).find()) {
                        violations.add(f.getFileName() + ":" + lin + ": public 桶落数据域表: " + t);
                    }
                    if (UNQUALIFIED_CREATE.matcher(raw).find()) {
                        violations.add(f.getFileName() + ":" + lin
                                + ": CREATE TABLE 裸数据域表名(无 schema 限定): " + t);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "F02-08/W49/MC06：数据域表 DDL 必须限定到引擎 schema，禁裸名/禁 public 桶：\n"
                        + String.join("\n", violations));
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
